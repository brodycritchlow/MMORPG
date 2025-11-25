package com.thornily.skills.listeners;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.utils.NBTKeys;
import com.thornily.skills.utils.SkillUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class WarriorArmorEquipListener implements Listener {

  private final SkillsPlugin plugin;
  private final DatabaseManager dbmanager;
  private final NBTKeys keys;

  public WarriorArmorEquipListener(SkillsPlugin plugin) {
    this.plugin = plugin;
    this.dbmanager = plugin.getDatabase();
    this.keys = plugin.getNBTKeys();
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (event.getClickedInventory() == null) return;

    ItemStack clickedItem = event.getCurrentItem();
    ItemStack cursorItem = event.getCursor();

    if (event.isShiftClick() && clickedItem != null && clickedItem.hasItemMeta()) {
      if (isArmorPiece(clickedItem) && isWarriorArmor(clickedItem)) {
        if (!checkRequirements(player, clickedItem)) {
          event.setCancelled(true);
          return;
        }
        new BukkitRunnable() {
          @Override
          public void run() {
            updateArmorLore(player);
          }
        }.runTaskLater(plugin, 1L);
      }
    }

    if (event.getClickedInventory().getType() == InventoryType.PLAYER) {
      int slot = event.getSlot();
      if (slot >= 36 && slot <= 39) {
        ItemStack itemBeingPlaced = null;

        if (cursorItem != null && !cursorItem.getType().equals(Material.AIR)) {
          itemBeingPlaced = cursorItem;
        } else if (event.isShiftClick() && clickedItem != null) {
          itemBeingPlaced = clickedItem;
        }

        if (itemBeingPlaced != null && itemBeingPlaced.hasItemMeta()) {
          if (isWarriorArmor(itemBeingPlaced)) {
            if (!checkRequirements(player, itemBeingPlaced)) {
              event.setCancelled(true);
              return;
            }
          }
        }

        new BukkitRunnable() {
          @Override
          public void run() {
            updateArmorLore(player);
          }
        }.runTaskLater(plugin, 1L);
      }
    }
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();

    if (item == null || !item.hasItemMeta()) return;

    if (!event.getAction().name().contains("RIGHT")) return;

    if (isArmorPiece(item) && isWarriorArmor(item)) {
      if (!checkRequirements(player, item)) {
        event.setCancelled(true);
      }
    }
  }

  private boolean isArmorPiece(ItemStack item) {
    if (item == null) return false;

    Material type = item.getType();
    String typeName = type.name();
    return typeName.endsWith("_HELMET") ||
           typeName.endsWith("_CHESTPLATE") ||
           typeName.endsWith("_LEGGINGS") ||
           typeName.endsWith("_BOOTS");
  }

  private boolean isWarriorArmor(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return false;
    ItemMeta meta = item.getItemMeta();
    Byte isWarrior = meta.getPersistentDataContainer().get(keys.warriorArmor, PersistentDataType.BYTE);
    return isWarrior != null && isWarrior == 1;
  }

  private boolean checkRequirements(Player player, ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return true;

    Integer attackReq = meta.getPersistentDataContainer().get(keys.attackRequirement, PersistentDataType.INTEGER);
    Integer defenseReq = meta.getPersistentDataContainer().get(keys.defenseRequirement, PersistentDataType.INTEGER);

    if (attackReq == null || defenseReq == null) return true;

    int playerAttackLevel = SkillUtils.getSkillLevel(player, "ATTACK", dbmanager, plugin.getLogger());
    int playerDefenseLevel = SkillUtils.getSkillLevel(player, "DEFENSE", dbmanager, plugin.getLogger());

    if (playerAttackLevel < attackReq || playerDefenseLevel < defenseReq) {
      player.sendMessage("§c§lREQUIREMENT NOT MET!");
      player.sendMessage("§7You need §cAttack Level " + attackReq + " §7and §aDefense Level " + defenseReq);
      player.sendMessage("§7Your levels: §cAttack " + playerAttackLevel + " §7| §aDefense " + playerDefenseLevel);
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return false;
    }

    return true;
  }

  private void updateArmorLore(Player player) {
    ItemStack[] armor = player.getInventory().getArmorContents();
    int warriorArmorCount = 0;

    for (ItemStack piece : armor) {
      if (isWarriorArmor(piece)) {
        warriorArmorCount++;
      }
    }

    for (int i = 0; i < armor.length; i++) {
      ItemStack piece = armor[i];
      if (!isWarriorArmor(piece)) continue;

      ItemMeta meta = piece.getItemMeta();
      java.util.List<String> lore = meta.getLore();
      if (lore == null) continue;

      for (int j = 0; j < lore.size(); j++) {
        String line = lore.get(j);
        if (line.contains("Full Set Bonus:")) {
          String newLine = line.replaceAll("\\(\\d/4\\)", "(" + warriorArmorCount + "/4)");
          lore.set(j, newLine);
          break;
        }
      }

      meta.setLore(lore);
      piece.setItemMeta(meta);
      armor[i] = piece;
    }

    player.getInventory().setArmorContents(armor);
  }
}
