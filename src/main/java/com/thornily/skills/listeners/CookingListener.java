package com.thornily.skills.listeners;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.utils.NBTKeys;
import com.thornily.skills.utils.SkillUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class CookingListener implements Listener {
    private final SkillsPlugin plugin;
    private final NBTKeys keys;
    private final DatabaseManager dbmanager;
    private final HashMap<Location, UUID> campfireCookers = new HashMap<Location, UUID>();

    public CookingListener(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.dbmanager = plugin.getDatabase();
        this.keys = plugin.getNBTKeys();
    }

    @EventHandler
    public void onCampfirePlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Material type = block.getType();

        if (type != Material.CAMPFIRE) return;

        Player player = event.getPlayer();

        campfireCookers.put(block.getLocation(), player.getUniqueId());
    }

    @EventHandler
    public void onCampfireStart(CampfireStartEvent event) {
        // speed up based on cooking level later on
        event.setTotalCookTime(5);
    }

    @EventHandler
    public void onCook(BlockCookEvent event) {
        Location loc = event.getBlock().getLocation();

        if (!campfireCookers.containsKey(loc)) return;

        UUID cookerId = campfireCookers.get(loc);
        Player player = Bukkit.getPlayer(cookerId);

        if (player == null) return;

        double cooking_level = SkillUtils.getSkillLevel(player,"COOKING", dbmanager, plugin.getLogger());

        Material type = event.getBlock().getType();
        if (type != Material.CAMPFIRE) {
            return;
        }

        double burnChance = 0.65 * Math.exp(-0.045 * (cooking_level - 1));
        plugin.getLogger().log(Level.INFO, "Burning " + burnChance);

        if (Math.random() < burnChance) {
            event.setResult(new ItemStack(Material.COAL));
        } else {
            giveCookingXP(player, event.getResult());
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
