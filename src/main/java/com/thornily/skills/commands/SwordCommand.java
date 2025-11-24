package com.thornily.skills.commands;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class SwordCommand implements CommandExecutor {

  private final JavaPlugin plugin;

  public SwordCommand(JavaPlugin plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label,
                           String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this.");
      return true;
    }

    if (args.length != 1) {
      player.sendMessage("Usage: /sword <damage>");
      return true;
    }

    double customDamage;
    try {
      customDamage = Double.parseDouble(args[0]);
    } catch (Exception e) {
      player.sendMessage("Damage must be a number.");
      return true;
    }

    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
    ItemMeta meta = sword.getItemMeta();

    // Set custom damage
    NamespacedKey damageKey = new NamespacedKey(plugin, "damage_custom");
    meta.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE,
                                          customDamage);

    // Set damage properties
    NamespacedKey skillKey = new NamespacedKey(plugin, "damage_skill");
    meta.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING,
                                          "combat");

    sword.setItemMeta(meta);
    player.getInventory().addItem(sword);
    player.sendMessage("§aGiven sword with custom damage: " + customDamage);

    return true;
  }
}
