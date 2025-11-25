package com.thornily.skills.commands;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.listeners.SkillGainListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StatsCommand implements CommandExecutor {
  private final SkillsPlugin plugin;

  public StatsCommand(SkillsPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    Player target;

    // Determine target player
    if (args.length == 0) {
      if (!(sender instanceof Player)) {
        sender.sendMessage("§cYou must specify a player when running from console!");
        return true;
      }
      target = (Player) sender;
    } else {
      target = Bukkit.getPlayer(args[0]);
      if (target == null) {
        sender.sendMessage("§cPlayer '" + args[0] + "' not found!");
        return true;
      }
    }

    // Open stats GUI
    openStatsGUI((Player) (sender instanceof Player ? sender : target), target);
    return true;
  }

  private void openStatsGUI(Player viewer, Player target) {
    String title = target.equals(viewer)
        ? "§6§lYour Combat Stats"
        : "§6§l" + target.getName() + "'s Stats";

    Inventory gui = Bukkit.createInventory(null, 27, title);

    // Fill with gray glass panes
    ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta paneMeta = grayPane.getItemMeta();
    paneMeta.setDisplayName(" ");
    grayPane.setItemMeta(paneMeta);

    for (int i = 0; i < 27; i++) {
      gui.setItem(i, grayPane);
    }

    // Create attack stat item (slot 12)
    gui.setItem(12, createAttackItem(target));

    // Create defense stat item (slot 14)
    gui.setItem(14, createDefenseItem(target));

    viewer.openInventory(gui);
  }

  private ItemStack createAttackItem(Player player) {
    ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
    ItemMeta meta = item.getItemMeta();

    // Calculate total attack bonus percentage
    double attackBonus = calculateAttackBonus(player) * 100.0; // Convert to percentage

    meta.setDisplayName("§c§lAttack: +" + String.format("%.1f", attackBonus) + "%");

    List<String> lore = new ArrayList<>();
    lore.add("§7Total damage bonus from");
    lore.add("§7your skill level and armor");

    meta.setLore(lore);

    // Hide vanilla attribute modifiers and make unbreakable
    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
    meta.setUnbreakable(true);

    item.setItemMeta(meta);

    return item;
  }

  private ItemStack createDefenseItem(Player player) {
    ItemStack item = new ItemStack(Material.SHIELD);
    ItemMeta meta = item.getItemMeta();

    // Get defense skill level
    int defenseLevel = getSkillLevel(player, "defense");
    double skillBonus = defenseLevel * 1.0;

    // Get armor defense
    double armorDefense = getRawDefenseBonus(player);
    double conversionPenalty = getDefenseConversionPenalty(player);
    double finalArmorDefense = armorDefense * (1.0 - conversionPenalty);

    // Calculate total before diminishing returns
    double totalDefense = finalArmorDefense + skillBonus;

    // Apply diminishing returns
    double finalReduction = applyDiminishingReturns(totalDefense / 100.0) * 100.0;

    meta.setDisplayName("§a§lDefense: " + String.format("%.1f", finalReduction) + "% Reduction");

    List<String> lore = new ArrayList<>();
    lore.add("§7Total damage reduction from");
    lore.add("§7your skill level and armor");

    meta.setLore(lore);

    // Hide vanilla attribute modifiers and make unbreakable
    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
    meta.setUnbreakable(true);

    item.setItemMeta(meta);

    return item;
  }

  private double calculateAttackBonus(Player player) {
    // Get attack skill level
    int attackLevel = getSkillLevel(player, "attack");
    double skillBonus = attackLevel * 0.05; // 5% per level

    // Get armor bonus (includes wand damage bonus and Volatile conversion)
    double armorBonus = getWandDamageBonus(player);

    // Return total attack bonus as decimal (e.g., 0.50 for 50%)
    return skillBonus + armorBonus;
  }

  private int getSkillLevel(Player player, String skillType) {
    String skillName = skillType.equalsIgnoreCase("magic") ? "MAGIC" : "ATTACK";

    try {
      ResultSet rs = plugin.getDatabase().executeQuery(
          "SELECT level FROM player_skills WHERE uuid = '" +
          player.getUniqueId() + "' AND skill_name = '" + skillName + "'");

      if (rs != null && rs.next()) {
        int level = rs.getInt("level");
        rs.close();
        return level;
      }
    } catch (SQLException e) {
      plugin.getLogger().severe("Failed to get skill level: " + e.getMessage());
    }

    return 1; // Default level
  }

  private double getWandDamageBonus(Player player) {
    // Use the same method from SkillGainListener
    SkillGainListener listener = plugin.getSkillGainListener();
    if (listener != null) {
      // We need to make getWandDamageBonus public in SkillGainListener
      // For now, calculate it here directly
      return calculateWandDamageBonus(player);
    }
    return 0.0;
  }

  private double getDefenseBonus(Player player) {
    // Calculate defense bonus same way as SkillGainListener
    return calculateDefenseBonus(player);
  }

  // Copied from SkillGainListener to calculate wand damage bonus
  private double calculateWandDamageBonus(Player player) {
    double totalBonus = 0.0;
    ItemStack[] armor = player.getInventory().getArmorContents();
    NamespacedKey key = new NamespacedKey(plugin, "wand_damage_bonus");

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;
      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
        Double bonus = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        if (bonus != null) {
          totalBonus += bonus;
        }
      }
    }

    // Check for full set bonus
    String tierName = getFullSetTier(player);
    if (tierName != null) {
      double tierSetBonus = getTierSetBonus(tierName, true);
      totalBonus += tierSetBonus;
    }

    // Add converted defense from Volatile armor
    double totalDefenseBonus = getRawDefenseBonus(player);
    double conversionPenalty = getDefenseConversionPenalty(player);
    double convertedWandBonus = totalDefenseBonus * conversionPenalty * 2.5;
    totalBonus += convertedWandBonus;

    return totalBonus / 100.0;
  }

  // Copied from SkillGainListener to calculate defense bonus
  private double calculateDefenseBonus(Player player) {
    double totalBonus = getRawDefenseBonus(player);
    double conversionPenalty = getDefenseConversionPenalty(player);
    double finalDefense = totalBonus * (1.0 - conversionPenalty);
    return finalDefense / 100.0;
  }

  private double getRawDefenseBonus(Player player) {
    double totalBonus = 0.0;
    ItemStack[] armor = player.getInventory().getArmorContents();
    NamespacedKey key = new NamespacedKey(plugin, "defense_bonus");

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;
      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
        Double bonus = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        if (bonus != null) {
          totalBonus += bonus;
        }
      }
    }

    // Check for full set bonus
    String tierName = getFullSetTier(player);
    if (tierName != null) {
      double tierSetBonus = getTierSetBonus(tierName, false);
      totalBonus += tierSetBonus;
    }

    return totalBonus;
  }

  private String getFullSetTier(Player player) {
    ItemStack[] armor = player.getInventory().getArmorContents();
    if (armor.length != 4) return null;

    NamespacedKey armorKey = new NamespacedKey(plugin, "mage_armor");
    NamespacedKey tierKey = new NamespacedKey(plugin, "tier_name");
    String firstTier = null;

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) return null;
      ItemMeta meta = piece.getItemMeta();
      if (!meta.getPersistentDataContainer().has(armorKey, PersistentDataType.BYTE)) {
        return null;
      }
      String tierName = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
      if (tierName == null) return null;
      if (firstTier == null) {
        firstTier = tierName;
      } else if (!firstTier.equals(tierName)) {
        return null;
      }
    }
    return firstTier;
  }

  private double getTierSetBonus(String tierName, boolean isWandBonus) {
    return switch (tierName.toUpperCase()) {
      case "APPRENTICE" -> isWandBonus ? 5.0 : 3.0;
      case "ADEPT" -> isWandBonus ? 8.0 : 6.0;
      case "MYSTIC" -> isWandBonus ? 12.0 : 9.0;
      case "SORCERER" -> isWandBonus ? 16.0 : 12.0;
      case "ARCHMAGE" -> isWandBonus ? 20.0 : 15.0;
      case "CELESTIAL" -> isWandBonus ? 25.0 : 20.0;
      case "VOLATILE" -> isWandBonus ? 20.0 : 0.0;
      default -> 0.0;
    };
  }

  private double getDefenseConversionPenalty(Player player) {
    double totalPenalty = 0.0;
    ItemStack[] armor = player.getInventory().getArmorContents();
    NamespacedKey conversionKey = new NamespacedKey(plugin, "defense_conversion_penalty");

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;
      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(conversionKey, PersistentDataType.DOUBLE)) {
        Double penalty = meta.getPersistentDataContainer().get(conversionKey, PersistentDataType.DOUBLE);
        if (penalty != null) {
          totalPenalty += penalty;
        }
      }
    }
    return totalPenalty / 100.0;
  }

  private double applyDiminishingReturns(double defenseDecimal) {
    // Convert back to percentage for formula
    double defensePercentage = defenseDecimal * 100.0;

    // Formula: reduction = (defense / (defense + K)) × maxCap
    final double K = 100.0; // Diminishing returns constant
    final double MAX_CAP = 0.75; // 75% maximum reduction

    double reduction = (defensePercentage / (defensePercentage + K)) * MAX_CAP;
    return Math.min(reduction, MAX_CAP);
  }
}
