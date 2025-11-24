package com.thornily.skills.commands;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.models.Skill;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SkillsCommand implements CommandExecutor {
  private final SkillsPlugin plugin;

  public SkillsCommand(SkillsPlugin plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label,
                           String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage("§cOnly players can use this command.");
      return true;
    }

    Player player = (Player)sender;
    openSkillsGUI(player);
    return true;
  }

  private void openSkillsGUI(Player player) {
    Inventory gui = Bukkit.createInventory(null, 54, "§6§lYour Skills");
    UUID uuid = player.getUniqueId();

    ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta glassMeta = glassPane.getItemMeta();
    if (glassMeta != null) {
      glassMeta.setDisplayName(" ");
      glassPane.setItemMeta(glassMeta);
    }

    for (int i = 0; i < 54; i++) {
      gui.setItem(i, glassPane);
    }

    int[] skillSlots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33};
    int skillIndex = 0;

    for (Skill skill : Skill.values()) {
      if (skillIndex >= skillSlots.length)
        break;

      ResultSet rs = plugin.getDatabase().executeQuery(
          "SELECT level, experience FROM player_skills WHERE uuid = '" + uuid +
          "' AND skill_name = '" + skill.name() + "'");

      try {
        if (rs != null && rs.next()) {
          int level = rs.getInt("level");
          double experience = rs.getDouble("experience");
          rs.close();

          ItemStack item = createSkillItem(skill, level, experience);
          gui.setItem(skillSlots[skillIndex], item);
          skillIndex++;
        }
      } catch (SQLException e) {
        plugin.getLogger().severe("Failed to load skill data for " +
                                  skill.name());
      }
    }

    player.openInventory(gui);
  }

  private ItemStack createSkillItem(Skill skill, int level, double experience) {
    Material material = getSkillMaterial(skill);
    ItemStack item = new ItemStack(material);

    ItemMeta meta = item.getItemMeta();

    if (meta != null) {
      meta.setDisplayName("§e§l" + formatSkillName(skill.name()));

      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

      List<String> lore = new ArrayList<>();
      lore.add("§7Level: §a" + level);
      lore.add("§7Experience: §b" + String.format("%.1f", experience) +
               "§7/§b" + String.format("%.0f", getRequiredXP(level)));
      lore.add("");
      lore.add("§7Progress to next level:");
      lore.add(createProgressBar(experience, getRequiredXP(level)));

      meta.setLore(lore);
      item.setItemMeta(meta);
    }

    return item;
  }

  private Material getSkillMaterial(Skill skill) {
    switch (skill) {
    case MINING:
      return Material.DIAMOND_PICKAXE;
    case WOODCUTTING:
      return Material.IRON_AXE;
    case FARMING:
      return Material.DIAMOND_HOE;
    case FISHING:
      return Material.FISHING_ROD;
    case ATTACK:
      return Material.DIAMOND_SWORD;
    case DEFENSE:
      return Material.SHIELD;
    case MAGIC:
      return Material.ENCHANTED_BOOK;
    case ARCHERY:
      return Material.BOW;
    case HUNTING:
      return Material.CROSSBOW;
    case EXPLORATION:
      return Material.COMPASS;
    case COOKING:
      return Material.COOKED_BEEF;
    case ALCHEMY:
      return Material.BREWING_STAND;
    default:
      return Material.PAPER;
    }
  }

  private String formatSkillName(String name) {
    return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
  }

  private double getRequiredXP(int level) { return Math.pow(level, 2) * 45; }

  private String createProgressBar(double current, double required) {
    int bars = 20;
    double percentage = Math.min(current / required, 1.0);
    int filled = (int)(bars * percentage);

    StringBuilder bar = new StringBuilder("§a");
    for (int i = 0; i < bars; i++) {
      if (i < filled) {
        bar.append("█");
      } else if (i == filled) {
        bar.append("§7");
      }
      if (i >= filled) {
        bar.append("█");
      }
    }

    bar.append(" §e").append(String.format("%.1f%%", percentage * 100));
    return bar.toString();
  }
}
