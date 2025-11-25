package com.thornily.skills.utils;

import com.thornily.skills.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SkillUtils {

  public static int getSkillLevel(Player player, String skillName, DatabaseManager dbManager, Logger logger) {
    try {
      ResultSet rs = dbManager.executeQuery(
          "SELECT level FROM player_skills WHERE uuid = '" +
          player.getUniqueId() + "' AND skill_name = '" + skillName + "'");

      if (rs != null && rs.next()) {
        int level = rs.getInt("level");
        rs.close();
        return level;
      }
    } catch (SQLException e) {
      logger.log(Level.SEVERE, "Failed to get " + skillName + " level for " + player.getName(), e);
    }
    return 1;
  }

  public static double getRequiredXP(int level) {
    return Math.pow(level, 2) * 45;
  }

  public static boolean isMageArmor(ItemStack item, NBTKeys keys) {
    if (item == null || !item.hasItemMeta()) return false;
    ItemMeta meta = item.getItemMeta();
    return meta.getPersistentDataContainer().has(keys.mageArmor, PersistentDataType.BYTE);
  }

  public static String getFullSetTier(Player player, NBTKeys keys) {
    ItemStack[] armor = player.getInventory().getArmorContents();

    if (armor.length != 4) return null;

    String firstTier = null;

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) return null;

      ItemMeta meta = piece.getItemMeta();
      if (!meta.getPersistentDataContainer().has(keys.mageArmor, PersistentDataType.BYTE)) {
        return null;
      }

      String tierName = meta.getPersistentDataContainer().get(keys.tierName, PersistentDataType.STRING);
      if (tierName == null) return null;

      if (firstTier == null) {
        firstTier = tierName;
      } else if (!firstTier.equals(tierName)) {
        return null;
      }
    }

    return firstTier;
  }

  public static double getTierSetBonus(String tierName, boolean isWandBonus) {
    return switch (tierName.toUpperCase()) {
      case "APPRENTICE" -> isWandBonus ? 5.0 : 3.0;
      case "ADEPT" -> isWandBonus ? 8.0 : 6.0;
      case "MYSTIC" -> isWandBonus ? 12.0 : 9.0;
      case "SORCERER" -> isWandBonus ? 16.0 : 12.0;
      case "ARCHMAGE" -> isWandBonus ? 20.0 : 15.0;
      case "CELESTIAL" -> isWandBonus ? 25.0 : 20.0;
      case "VOLATILE" -> isWandBonus ? 20.0 : 15.0;
      default -> 0.0;
    };
  }
}
