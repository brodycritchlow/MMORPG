package com.thornily.skills.listeners;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.models.CampfireSlot;
import com.thornily.skills.utils.NBTKeys;
import com.thornily.skills.utils.SkillUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CookingListener implements Listener {
    private final SkillsPlugin plugin;
    private final NBTKeys keys;
    private final DatabaseManager dbmanager;

    private final Map<Location, Map<Integer, CampfireSlot>> campfireTracking = new ConcurrentHashMap<>();
    private final Map<Location, UUID> recentInteractions = new ConcurrentHashMap<>();

    private static final long SLOT_TIMEOUT_MS = 30 * 60 * 1000;

    public CookingListener(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.dbmanager = plugin.getDatabase();
        this.keys = plugin.getNBTKeys();
        startCleanupTask();
    }

    @EventHandler
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CAMPFIRE) return;

        ItemStack item = event.getItem();
        if (item == null || !isCookable(item.getType())) return;

        Location loc = event.getClickedBlock().getLocation();
        UUID playerUUID = event.getPlayer().getUniqueId();

        recentInteractions.put(loc, playerUUID);
        plugin.getLogger().info("[Cooking] " + event.getPlayer().getName() + " placed " +
            item.getType() + " on campfire at " + formatLocation(loc));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            recentInteractions.remove(loc, playerUUID);
        }, 40L);
    }

    @EventHandler
    public void onCampfireStart(CampfireStartEvent event) {
        Location loc = event.getBlock().getLocation();
        Material rawMaterial = event.getSource().getType();

        UUID placer = recentInteractions.get(loc);
        if (placer == null) {
            plugin.getLogger().warning("[Cooking] No recent interaction found for campfire at " + formatLocation(loc));
            return;
        }

        Player player = Bukkit.getPlayer(placer);
        if (player != null) {
            double level = SkillUtils.getSkillLevel(player, "COOKING", dbmanager, plugin.getLogger());
            int cookTime = calculateCookTime(level);
            event.setTotalCookTime(cookTime);
        }

        // Delay slot detection by 1 tick to allow campfire state to update
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Campfire campfire = (Campfire) event.getBlock().getState();
            int targetSlot = findNewlyOccupiedSlot(campfire, rawMaterial);

            if (targetSlot == -1) {
                plugin.getLogger().warning("[Cooking] Could not find slot for " + rawMaterial + " at " + formatLocation(loc));
                return;
            }

            campfireTracking.putIfAbsent(loc, new ConcurrentHashMap<>());
            campfireTracking.get(loc).put(targetSlot, new CampfireSlot(placer, rawMaterial));

            if (player != null) {
                plugin.getLogger().info("[Cooking] Tracking slot " + targetSlot + " for " + player.getName() +
                    " cooking " + rawMaterial + " (cook time: " + (player != null ? calculateCookTime(
                        SkillUtils.getSkillLevel(player, "COOKING", dbmanager, plugin.getLogger())) : "unknown") +
                    " ticks, level: " + (player != null ? (int)SkillUtils.getSkillLevel(player, "COOKING", dbmanager, plugin.getLogger()) : "?") + ")");
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCook(BlockCookEvent event) {
        Location loc = event.getBlock().getLocation();
        Material cookedType = event.getResult().getType();

        Map<Integer, CampfireSlot> slots = campfireTracking.get(loc);
        if (slots == null || slots.isEmpty()) {
            // No tracking data - this might be a duplicate event after we already processed it
            // Cancel it to prevent item from dropping
            event.setCancelled(true);
            return;
        }

        Material rawMaterial = getRawMaterialFor(cookedType);
        if (rawMaterial == null) {
            plugin.getLogger().warning("[Cooking] Unknown cooked material: " + cookedType);
            return;
        }

        Integer matchingSlot = null;
        for (Map.Entry<Integer, CampfireSlot> entry : slots.entrySet()) {
            if (entry.getValue().getRawMaterial() == rawMaterial) {
                matchingSlot = entry.getKey();
                break;
            }
        }

        if (matchingSlot == null) {
            plugin.getLogger().warning("[Cooking] Could not match " + cookedType + " to any slot at " +
                formatLocation(loc) + ". Tracked materials: " +
                slots.values().stream().map(s -> s.getRawMaterial().toString()).toList());
            return;
        }

        CampfireSlot slot = slots.remove(matchingSlot);
        UUID cookerUUID = slot.getPlayerUUID();
        Player player = Bukkit.getPlayer(cookerUUID);

        if (player == null) {
            plugin.getLogger().warning("[Cooking] Player offline for cooked " + cookedType + " in slot " + matchingSlot);
            if (slots.isEmpty()) campfireTracking.remove(loc);
            return;
        }

        // Cancel the event to prevent vanilla drop behavior
        event.setCancelled(true);

        // Manually remove the item from the campfire slot
        Campfire campfire = (Campfire) event.getBlock().getState();
        campfire.setItem(matchingSlot, null);
        campfire.update();

        double cookingLevel = SkillUtils.getSkillLevel(player, "COOKING", dbmanager, plugin.getLogger());
        double burnChance = 0.65 * Math.exp(-0.045 * (cookingLevel - 1));

        if (Math.random() < burnChance) {
            // Burned - give coal to player
            giveItemToPlayer(player, new ItemStack(Material.COAL));
            player.sendActionBar("§c§l✖ Cooking Failed!");
            plugin.getLogger().info("[Cooking] " + player.getName() + "'s " + cookedType + " BURNED (slot " +
                matchingSlot + ", burn chance: " + String.format("%.1f%%", burnChance * 100) + ")");
        } else {
            // Successfully cooked - give item to player
            ItemStack cookedItem = event.getResult().clone();
            giveItemToPlayer(player, cookedItem);
            giveCookingXP(player, cookedItem);
            plugin.getLogger().info("[Cooking] " + player.getName() + " successfully cooked " + cookedType +
                " (slot " + matchingSlot + ")");
        }

        if (slots.isEmpty()) {
            campfireTracking.remove(loc);
            plugin.getLogger().info("[Cooking] Removed campfire tracking at " + formatLocation(loc) + " (all slots empty)");
        }
    }

    @EventHandler
    public void onCampfireBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CAMPFIRE && block.getType() != Material.SOUL_CAMPFIRE) return;

        Location loc = block.getLocation();
        Map<Integer, CampfireSlot> slots = campfireTracking.remove(loc);
        recentInteractions.remove(loc);

        if (slots != null && !slots.isEmpty()) {
            plugin.getLogger().info("[Cooking] Campfire broken at " + formatLocation(loc) +
                " by " + event.getPlayer().getName() + " with " + slots.size() + " items still cooking");
        }
    }

    private int findNewlyOccupiedSlot(Campfire campfire, Material rawMaterial) {
        Map<Integer, CampfireSlot> existing = campfireTracking.get(campfire.getLocation());

        for (int i = 0; i < 4; i++) {
            ItemStack slotItem = campfire.getItem(i);
            if (slotItem != null && slotItem.getType() == rawMaterial) {
                // If no existing tracking OR this slot isn't tracked yet
                if (existing == null || !existing.containsKey(i)) {
                    return i;
                }
            }
        }

        // If we can't find a newly occupied slot, check if campfire just got an item
        // This handles the case where the campfire state hasn't updated yet
        if (existing == null || existing.isEmpty()) {
            // Campfire is starting fresh, assume slot 0
            return 0;
        }

        return -1;
    }

    private Material getRawMaterialFor(Material cookedType) {
        return switch (cookedType) {
            case COOKED_BEEF -> Material.BEEF;
            case COOKED_PORKCHOP -> Material.PORKCHOP;
            case COOKED_MUTTON -> Material.MUTTON;
            case COOKED_CHICKEN -> Material.CHICKEN;
            case COOKED_RABBIT -> Material.RABBIT;
            case COOKED_COD -> Material.COD;
            case COOKED_SALMON -> Material.SALMON;
            case BAKED_POTATO -> Material.POTATO;
            case DRIED_KELP -> Material.KELP;
            default -> null;
        };
    }

    private boolean isCookable(Material material) {
        return switch (material) {
            case BEEF, PORKCHOP, MUTTON, CHICKEN,
                 RABBIT, COD, SALMON, POTATO, KELP -> true;
            default -> false;
        };
    }

    private int calculateCookTime(double cookingLevel) {
        int baseCookTime = plugin.getConfig().getInt("cooking.cook_time_base", 600);
        int reductionPerLevel = plugin.getConfig().getInt("cooking.cook_time_reduction_per_level", 5);
        int cookTime = baseCookTime - (int)(cookingLevel * reductionPerLevel);
        return Math.max(cookTime, 100);
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int removedSlots = 0;
            int removedCampfires = 0;

            Iterator<Map.Entry<Location, Map<Integer, CampfireSlot>>> campfireIter =
                campfireTracking.entrySet().iterator();

            while (campfireIter.hasNext()) {
                Map.Entry<Location, Map<Integer, CampfireSlot>> entry = campfireIter.next();
                Map<Integer, CampfireSlot> slots = entry.getValue();

                Iterator<Map.Entry<Integer, CampfireSlot>> slotIter =
                    slots.entrySet().iterator();
                while (slotIter.hasNext()) {
                    CampfireSlot slot = slotIter.next().getValue();
                    if (slot.isExpired(SLOT_TIMEOUT_MS)) {
                        slotIter.remove();
                        removedSlots++;
                    }
                }

                if (slots.isEmpty()) {
                    campfireIter.remove();
                    removedCampfires++;
                }
            }

            if (removedSlots > 0 || removedCampfires > 0) {
                plugin.getLogger().info("Cleanup: " + removedSlots +
                    " expired slots, " + removedCampfires + " empty campfires");
            }
        }, 6000L, 6000L);
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        // Try to add to inventory
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

        // If inventory is full, drop at player's location
        if (!leftover.isEmpty()) {
            for (ItemStack remaining : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
            player.sendMessage("§e§l⚠ §7Inventory full! Item dropped at your feet.");
        }
    }

    private void giveCookingXP(Player player, ItemStack cookedItem) {
        Material cookedType = cookedItem.getType();
        String itemName = cookedType.name();

        double baseXP = plugin.getConfig().getDouble("cooking_xp." + itemName,
                        plugin.getConfig().getDouble("cooking_xp.DEFAULT", 5.0));

        try {
            UUID playerId = player.getUniqueId();
            ResultSet rs = dbmanager.executeQuery(
                "SELECT level, experience FROM player_skills WHERE uuid = '" +
                playerId + "' AND skill_name = 'COOKING'");

            if (rs != null && rs.next()) {
                int currentLevel = rs.getInt("level");
                double currentXP = rs.getDouble("experience");
                rs.close();

                double newXP = currentXP + baseXP;
                int newLevel = currentLevel;

                double requiredXP = SkillUtils.getRequiredXP(currentLevel);
                while (newXP >= requiredXP && newLevel < 99) {
                    newXP -= requiredXP;
                    newLevel++;
                    requiredXP = SkillUtils.getRequiredXP(newLevel);

                    String levelUpMsg = "§e§l✦ Cooking Level Up! §7(" +
                                       (newLevel - 1) + " → " + newLevel + ")";
                    player.sendMessage(levelUpMsg);
                    player.playSound(player.getLocation(),
                                   org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }

                dbmanager.executeUpdate(
                    "UPDATE player_skills SET level = " + newLevel +
                    ", experience = " + newXP + " WHERE uuid = '" + playerId +
                    "' AND skill_name = 'COOKING'");

                String xpMsg = "§e+" + String.format("%.1f", baseXP) + " Cooking XP";
                player.sendActionBar(xpMsg);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                                  "Failed to add cooking XP for " + player.getName(), e);
        }
    }
}
