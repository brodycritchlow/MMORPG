package com.thornily.skills.commands;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminUtilsCommand implements CommandExecutor, TabCompleter {

  private final JavaPlugin plugin;

  public AdminUtilsCommand(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("§cOnly players can use this command.");
      return true;
    }

    if (args.length < 2) {
      player.sendMessage("§cUsage: /admin-utils spawn <item> [damage]");
      player.sendMessage("§7Items: wand, sword, <tier>-helmet, <tier>-chestplate, <tier>-leggings, <tier>-boots, <tier>-set");
      player.sendMessage("§7Tiers: apprentice, adept, mystic, sorcerer, archmage, celestial");
      return true;
    }

    String subcommand = args[0].toLowerCase();
    if (!subcommand.equals("spawn")) {
      player.sendMessage("§cUnknown subcommand. Use: spawn");
      return true;
    }

    String item = args[1].toLowerCase();
    double damage = 10.0;

    if (args.length >= 3) {
      try {
        damage = Double.parseDouble(args[2]);
      } catch (NumberFormatException e) {
        player.sendMessage("§cInvalid damage value. Using default: 10");
      }
    }

    // Handle wand and sword
    if (item.equals("wand")) {
      giveWand(player, damage);
      return true;
    }
    if (item.equals("sword")) {
      giveSword(player, damage);
      return true;
    }

    // Parse tier-piece format (e.g., "archmage-helmet" or "apprentice-set")
    String[] parts = item.split("-", 2);
    if (parts.length < 2) {
      player.sendMessage("§cInvalid item format. Use: <tier>-<piece> or <path>-<tier>-<piece>");
      player.sendMessage("§7Example: archmage-helmet, berserker-warlord-set");
      return true;
    }

    String firstPart = parts[0];
    String remaining = parts[1];

    // Check if it's warrior armor (berserker or bladedancer path)
    if (firstPart.equalsIgnoreCase("berserker") || firstPart.equalsIgnoreCase("bladedancer")) {
      String[] pathParts = remaining.split("-", 2);
      if (pathParts.length != 2) {
        player.sendMessage("§cInvalid warrior armor format. Use: <path>-<tier>-<piece>");
        player.sendMessage("§7Example: berserker-warlord-helmet, bladedancer-phantom-set");
        return true;
      }

      String path = firstPart;
      String tierName = pathParts[0];
      String pieceName = pathParts[1];

      if (path.equalsIgnoreCase("berserker")) {
        BerserkerTier bTier;
        try {
          bTier = BerserkerTier.valueOf(tierName.toUpperCase());
        } catch (IllegalArgumentException e) {
          player.sendMessage("§cUnknown berserker tier: " + tierName);
          player.sendMessage("§7Tiers: brawler, warrior, champion, warlord, berserker, titan, rageful");
          return true;
        }

        if (pieceName.equals("set")) {
          giveWarriorArmor(player, "BERSERKER", bTier, "helmet");
          giveWarriorArmor(player, "BERSERKER", bTier, "chestplate");
          giveWarriorArmor(player, "BERSERKER", bTier, "leggings");
          giveWarriorArmor(player, "BERSERKER", bTier, "boots");
          player.sendMessage("§aGiven full " + bTier.displayName + " armor set!");
          return true;
        }

        if (!pieceName.equals("helmet") && !pieceName.equals("chestplate") &&
            !pieceName.equals("leggings") && !pieceName.equals("boots")) {
          player.sendMessage("§cUnknown piece: " + pieceName);
          player.sendMessage("§7Pieces: helmet, chestplate, leggings, boots, set");
          return true;
        }

        giveWarriorArmor(player, "BERSERKER", bTier, pieceName);
        return true;
      } else {
        BladedancerTier bdTier;
        try {
          bdTier = BladedancerTier.valueOf(tierName.toUpperCase());
        } catch (IllegalArgumentException e) {
          player.sendMessage("§cUnknown bladedancer tier: " + tierName);
          player.sendMessage("§7Tiers: scout, duelist, skirmisher, shadowblade, bladedancer, tempest, phantom");
          return true;
        }

        if (pieceName.equals("set")) {
          giveWarriorArmor(player, "BLADEDANCER", bdTier, "helmet");
          giveWarriorArmor(player, "BLADEDANCER", bdTier, "chestplate");
          giveWarriorArmor(player, "BLADEDANCER", bdTier, "leggings");
          giveWarriorArmor(player, "BLADEDANCER", bdTier, "boots");
          player.sendMessage("§aGiven full " + bdTier.displayName + " armor set!");
          return true;
        }

        if (!pieceName.equals("helmet") && !pieceName.equals("chestplate") &&
            !pieceName.equals("leggings") && !pieceName.equals("boots")) {
          player.sendMessage("§cUnknown piece: " + pieceName);
          player.sendMessage("§7Pieces: helmet, chestplate, leggings, boots, set");
          return true;
        }

        giveWarriorArmor(player, "BLADEDANCER", bdTier, pieceName);
        return true;
      }
    }

    // Otherwise it's mage armor (original logic)
    String tierName = firstPart;
    String pieceName = remaining;

    // Parse tier
    ArmorTier tier;
    try {
      tier = ArmorTier.valueOf(tierName.toUpperCase());
    } catch (IllegalArgumentException e) {
      player.sendMessage("§cUnknown tier: " + tierName);
      player.sendMessage("§7Tiers: apprentice, adept, mystic, sorcerer, archmage, celestial");
      return true;
    }

    // Handle set or individual piece
    if (pieceName.equals("set")) {
      giveArmor(player, tier, "helmet");
      giveArmor(player, tier, "chestplate");
      giveArmor(player, tier, "leggings");
      giveArmor(player, tier, "boots");
      player.sendMessage("§aGiven full " + tier.displayName + " armor set!");
      return true;
    }

    // Validate piece name
    if (!pieceName.equals("helmet") && !pieceName.equals("chestplate") &&
        !pieceName.equals("leggings") && !pieceName.equals("boots")) {
      player.sendMessage("§cUnknown piece: " + pieceName);
      player.sendMessage("§7Pieces: helmet, chestplate, leggings, boots, set");
      return true;
    }

    giveArmor(player, tier, pieceName);

    return true;
  }

  private void giveWand(Player player, double damage) {
    ItemStack wand = new ItemStack(Material.STICK);
    ItemMeta meta = wand.getItemMeta();

    if (meta != null) {
      meta.setDisplayName("§6§lFire Wand");

      List<String> lore = new ArrayList<>();
      lore.add("§7Damage: §c+" + String.format("%.0f", damage));
      lore.add("");
      lore.add("§6Item Ability: Spiral Beam §eLEFT CLICK");
      lore.add("§7Shoot a spiraling beam of fire");
      lore.add("§7at your enemies!");
      lore.add("");
      lore.add("§7Damage scales with your §dMagic");
      lore.add("§7skill level §8(+5% per level)");
      lore.add("");
      lore.add("§6§lLEGENDARY MAGIC WAND");

      meta.setLore(lore);

      NamespacedKey wandKey = new NamespacedKey(plugin, "fire_wand");
      meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte)1);

      NamespacedKey damageKey = new NamespacedKey(plugin, "damage_custom");
      meta.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, damage);

      NamespacedKey noMeleeKey = new NamespacedKey(plugin, "no_melee");
      meta.getPersistentDataContainer().set(noMeleeKey, PersistentDataType.BYTE, (byte)1);

      NamespacedKey skillKey = new NamespacedKey(plugin, "damage_skill");
      meta.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, "magic");

      wand.setItemMeta(meta);
    }

    player.getInventory().addItem(wand);
    player.sendMessage("§aGiven Fire Wand with " + String.format("%.0f", damage) + " damage!");
  }

  private void giveSword(Player player, double damage) {
    ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
    ItemMeta meta = sword.getItemMeta();

    if (meta != null) {
      meta.setDisplayName("§c§lCustom Sword");

      NamespacedKey damageKey = new NamespacedKey(plugin, "damage_custom");
      meta.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, damage);

      NamespacedKey skillKey = new NamespacedKey(plugin, "damage_skill");
      meta.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, "attack");

      sword.setItemMeta(meta);
    }

    player.getInventory().addItem(sword);
    player.sendMessage("§aGiven Custom Sword with " + String.format("%.0f", damage) + " damage!");
  }

  private void giveArmor(Player player, ArmorTier tier, String piece) {
    ArmorStats stats = tier.getStats(piece);

    Material material = getMaterial(piece);
    ItemStack armor = new ItemStack(material);
    ItemMeta meta = armor.getItemMeta();

    if (meta != null) {
      meta.setDisplayName(tier.rarity.color + getArmorPieceName(tier, piece));

      List<String> lore = new ArrayList<>();
      lore.add("§7Wand Damage: §c+" + String.format("%.0f", stats.wandBonus) + "%");

      // Volatile armor shows no defense stat
      if (tier != ArmorTier.VOLATILE) {
        lore.add("§7Defense: §a+" + String.format("%.0f", stats.defenseBonus) + "%");
      }

      lore.add("");

      // Add Volatile-specific armor ability
      if (tier == ArmorTier.VOLATILE) {
        lore.add("§6Armor Ability: §cVolatile Conversion");
        lore.add("§7Takes away defense and converts");
        lore.add("§7it into additional attack damage");
      }

      if (tier != ArmorTier.VOLATILE) {
        lore.add("§6Full Set Bonus: §e" + tier.displayName + " Power §7(0/4)");
        lore.add("§7Grants §c+" + String.format("%.0f", tier.setBonusWand) + "% wand damage");
        lore.add("§7and §a+" + String.format("%.0f", tier.setBonusDefense) + "% defense §7when wearing");
        lore.add("§7all 4 armor pieces.");
      }
      lore.add("");
      lore.add("§7Requires §dMagic Level " + stats.magicReq);
      lore.add("§7and §aDefense Level " + stats.defenseReq);
      lore.add("");
      lore.add(tier.rarity.displayText + " MAGE ARMOR");

      meta.setLore(lore);

      NamespacedKey armorKey = new NamespacedKey(plugin, "mage_armor");
      meta.getPersistentDataContainer().set(armorKey, PersistentDataType.BYTE, (byte)1);

      NamespacedKey pieceKey = new NamespacedKey(plugin, "armor_piece");
      meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece);

      NamespacedKey wandKey = new NamespacedKey(plugin, "wand_damage_bonus");
      meta.getPersistentDataContainer().set(wandKey, PersistentDataType.DOUBLE, stats.wandBonus);

      NamespacedKey defenseKey = new NamespacedKey(plugin, "defense_bonus");
      meta.getPersistentDataContainer().set(defenseKey, PersistentDataType.DOUBLE, stats.defenseBonus);

      NamespacedKey magicReqKey = new NamespacedKey(plugin, "magic_requirement");
      meta.getPersistentDataContainer().set(magicReqKey, PersistentDataType.INTEGER, stats.magicReq);

      NamespacedKey defenseReqKey = new NamespacedKey(plugin, "defense_requirement");
      meta.getPersistentDataContainer().set(defenseReqKey, PersistentDataType.INTEGER, stats.defenseReq);

      // Store tier name for set bonus calculation
      NamespacedKey tierKey = new NamespacedKey(plugin, "tier_name");
      meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());

      // Add defense conversion penalty for Volatile armor
      if (tier == ArmorTier.VOLATILE) {
        NamespacedKey conversionKey = new NamespacedKey(plugin, "defense_conversion_penalty");
        double conversionPenalty = getConversionPenalty(piece);
        meta.getPersistentDataContainer().set(conversionKey, PersistentDataType.DOUBLE, conversionPenalty);
      }

      // Hide default tooltips
      meta.addItemFlags(ItemFlag.HIDE_DYE);
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

      if (meta instanceof LeatherArmorMeta leatherMeta) {
        Color armorColor = getArmorColor(tier, piece);
        leatherMeta.setColor(armorColor);
      }

      armor.setItemMeta(meta);
    }

    player.getInventory().addItem(armor);
    player.sendMessage("§aGiven " + tier.displayName + "'s " + capitalize(piece) + "!");
  }

  private void giveWarriorArmor(Player player, String path, Object tierObj, String piece) {
    Material material = getMaterial(piece);
    ItemStack armor = new ItemStack(material);
    ItemMeta meta = armor.getItemMeta();

    if (meta == null) return;

    NamespacedKey warriorKey = new NamespacedKey(plugin, "warrior_armor");
    NamespacedKey pathKey = new NamespacedKey(plugin, "armor_path");
    NamespacedKey pieceKey = new NamespacedKey(plugin, "armor_piece");
    NamespacedKey defenseKey = new NamespacedKey(plugin, "defense_bonus");
    NamespacedKey attackReqKey = new NamespacedKey(plugin, "attack_requirement");
    NamespacedKey defenseReqKey = new NamespacedKey(plugin, "defense_requirement");
    NamespacedKey tierKey = new NamespacedKey(plugin, "tier_name");
    NamespacedKey rarityKey = new NamespacedKey(plugin, "rarity");

    if (path.equals("BERSERKER")) {
      BerserkerTier tier = (BerserkerTier) tierObj;
      WarriorArmorStats stats = tier.getStats(piece);

      meta.setDisplayName(tier.rarity.color + tier.displayName + "'s " + capitalize(piece));

      List<String> lore = new ArrayList<>();
      lore.add("§7Melee Damage: §c+" + String.format("%.0f", stats.damageBonus) + "%");

      if (tier != BerserkerTier.RAGEFUL) {
        lore.add("§7Defense: §a+" + String.format("%.0f", stats.defenseBonus) + "%");
      }

      lore.add("");

      if (tier == BerserkerTier.RAGEFUL) {
        lore.add("§6Armor Ability: §cRageful Conversion");
        lore.add("§7Converts defense into raw attack");
        lore.add("§7power with a 3x multiplier");
      }

      if (tier != BerserkerTier.RAGEFUL) {
        lore.add("§6Full Set Bonus: §e" + tier.displayName + " Might §7(0/4)");
        lore.add("§7Grants §c+" + String.format("%.0f", tier.setBonusDamage) + "% melee damage");
        lore.add("§7and §a+" + String.format("%.0f", tier.setBonusDefense) + "% defense §7when wearing");
        lore.add("§7all 4 armor pieces.");
      }
      lore.add("");
      lore.add("§7Requires §cAttack Level " + stats.attackReq);
      lore.add("§7and §aDefense Level " + stats.defenseReq);
      lore.add("");
      lore.add(tier.rarity.displayText + " WARRIOR ARMOR");

      meta.setLore(lore);

      meta.getPersistentDataContainer().set(warriorKey, PersistentDataType.BYTE, (byte) 1);
      meta.getPersistentDataContainer().set(pathKey, PersistentDataType.STRING, "BERSERKER");
      meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece);

      NamespacedKey meleeDamageKey = new NamespacedKey(plugin, "melee_damage_bonus");
      meta.getPersistentDataContainer().set(meleeDamageKey, PersistentDataType.DOUBLE, stats.damageBonus);

      meta.getPersistentDataContainer().set(defenseKey, PersistentDataType.DOUBLE, stats.defenseBonus);
      meta.getPersistentDataContainer().set(attackReqKey, PersistentDataType.INTEGER, stats.attackReq);
      meta.getPersistentDataContainer().set(defenseReqKey, PersistentDataType.INTEGER, stats.defenseReq);
      meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());
      meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, tier.rarity.name());

      if (tier == BerserkerTier.RAGEFUL) {
        NamespacedKey conversionKey = new NamespacedKey(plugin, "defense_conversion_penalty");
        double conversionPenalty = getConversionPenalty(piece);
        meta.getPersistentDataContainer().set(conversionKey, PersistentDataType.DOUBLE, conversionPenalty);
      }

      meta.addItemFlags(ItemFlag.HIDE_DYE);
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

      if (meta instanceof LeatherArmorMeta leatherMeta) {
        Color armorColor = getWarriorArmorColor(tier, null, piece);
        leatherMeta.setColor(armorColor);
      }

      armor.setItemMeta(meta);
      player.getInventory().addItem(armor);
      player.sendMessage("§aGiven " + tier.displayName + "'s " + capitalize(piece) + "!");

    } else if (path.equals("BLADEDANCER")) {
      BladedancerTier tier = (BladedancerTier) tierObj;
      BladedancerArmorStats stats = tier.getStats(piece);

      meta.setDisplayName(tier.rarity.color + tier.displayName + "'s " + capitalize(piece));

      List<String> lore = new ArrayList<>();
      lore.add("§7Multi-Hit Chance: §b+" + String.format("%.0f", stats.multiHitChance) + "%");
      lore.add("§7Defense: §a+" + String.format("%.0f", stats.defenseBonus) + "%");
      lore.add("");

      if (tier == BladedancerTier.PHANTOM) {
        lore.add("§6Armor Ability: §dPhantom Cascade");
        lore.add("§7Unlocks triple-hit combos with");
        lore.add("§7cascading chances: 90% → 60% → 30%");
      }

      lore.add("§6Full Set Bonus: §e" + tier.displayName + " Flurry §7(0/4)");
      lore.add("§7Grants §b+" + String.format("%.0f", tier.setBonusHitChance) + "% multi-hit chance");
      lore.add("§7and §a+" + String.format("%.0f", tier.setBonusDefense) + "% defense §7when wearing");
      lore.add("§7all 4 armor pieces.");
      lore.add("");
      lore.add("§7Requires §cAttack Level " + stats.attackReq);
      lore.add("§7and §aDefense Level " + stats.defenseReq);
      lore.add("");
      lore.add(tier.rarity.displayText + " WARRIOR ARMOR");

      meta.setLore(lore);

      meta.getPersistentDataContainer().set(warriorKey, PersistentDataType.BYTE, (byte) 1);
      meta.getPersistentDataContainer().set(pathKey, PersistentDataType.STRING, "BLADEDANCER");
      meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece);

      NamespacedKey multiHitChanceKey = new NamespacedKey(plugin, "multi_hit_chance");
      meta.getPersistentDataContainer().set(multiHitChanceKey, PersistentDataType.DOUBLE, stats.multiHitChance);

      NamespacedKey multiHitCountKey = new NamespacedKey(plugin, "multi_hit_count");
      int multiHitCount = (tier == BladedancerTier.PHANTOM) ? 3 : 1;
      meta.getPersistentDataContainer().set(multiHitCountKey, PersistentDataType.INTEGER, multiHitCount);

      meta.getPersistentDataContainer().set(defenseKey, PersistentDataType.DOUBLE, stats.defenseBonus);
      meta.getPersistentDataContainer().set(attackReqKey, PersistentDataType.INTEGER, stats.attackReq);
      meta.getPersistentDataContainer().set(defenseReqKey, PersistentDataType.INTEGER, stats.defenseReq);
      meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());
      meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, tier.rarity.name());

      meta.addItemFlags(ItemFlag.HIDE_DYE);
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

      if (meta instanceof LeatherArmorMeta leatherMeta) {
        Color armorColor = getWarriorArmorColor(null, tier, piece);
        leatherMeta.setColor(armorColor);
      }

      armor.setItemMeta(meta);
      player.getInventory().addItem(armor);
      player.sendMessage("§aGiven " + tier.displayName + "'s " + capitalize(piece) + "!");
    }
  }

  private Material getMaterial(String piece) {
    return switch (piece.toLowerCase()) {
      case "helmet" -> Material.LEATHER_HELMET;
      case "chestplate" -> Material.LEATHER_CHESTPLATE;
      case "leggings" -> Material.LEATHER_LEGGINGS;
      case "boots" -> Material.LEATHER_BOOTS;
      default -> Material.LEATHER_CHESTPLATE;
    };
  }

  private Color getArmorColor(ArmorTier tier, String piece) {
    Color[] colors = tier.getColors();
    return switch (piece.toLowerCase()) {
      case "helmet" -> colors[0];
      case "chestplate" -> colors[1];
      case "leggings" -> colors[2];
      case "boots" -> colors[3];
      default -> colors[1]; // default to chestplate color
    };
  }

  private Color getWarriorArmorColor(BerserkerTier berserkerTier, BladedancerTier bladedancerTier, String piece) {
    Color[] colors;
    if (berserkerTier != null) {
      colors = berserkerTier.getColors();
    } else {
      colors = bladedancerTier.getColors();
    }

    return switch (piece.toLowerCase()) {
      case "helmet" -> colors[0];
      case "chestplate" -> colors[1];
      case "leggings" -> colors[2];
      case "boots" -> colors[3];
      default -> colors[1];
    };
  }

  private String capitalize(String str) {
    if (str == null || str.isEmpty()) return str;
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  private String getArmorPieceName(ArmorTier tier, String piece) {
    return tier.displayName + "'s " + capitalize(piece);
  }

  private double getConversionPenalty(String piece) {
    return switch (piece.toLowerCase()) {
      case "helmet" -> 40.0;      // 40% conversion
      case "chestplate" -> 30.0;  // 30% conversion
      case "leggings" -> 20.0;    // 20% conversion
      case "boots" -> 10.0;       // 10% conversion
      default -> 0.0;
    };
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      completions.add("spawn");
    } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
      // Add wand and sword
      completions.add("wand");
      completions.add("sword");

      // Add all mage tier-piece combinations
      String[] mageTiers = {"apprentice", "adept", "mystic", "sorcerer", "archmage", "celestial", "volatile"};
      String[] pieces = {"helmet", "chestplate", "leggings", "boots", "set"};

      for (String tier : mageTiers) {
        for (String piece : pieces) {
          completions.add(tier + "-" + piece);
        }
      }

      // Add all berserker tier-piece combinations
      String[] berserkerTiers = {"brawler", "warrior", "champion", "warlord", "berserker", "titan", "rageful"};
      for (String tier : berserkerTiers) {
        for (String piece : pieces) {
          completions.add("berserker-" + tier + "-" + piece);
        }
      }

      // Add all bladedancer tier-piece combinations
      String[] bladedancerTiers = {"scout", "duelist", "skirmisher", "shadowblade", "bladedancer", "tempest", "phantom"};
      for (String tier : bladedancerTiers) {
        for (String piece : pieces) {
          completions.add("bladedancer-" + tier + "-" + piece);
        }
      }
    } else if (args.length == 3) {
      // Only show damage values for wand and sword
      String item = args[1].toLowerCase();
      if (item.equals("wand") || item.equals("sword")) {
        completions.add("10");
        completions.add("25");
        completions.add("50");
        completions.add("100");
      }
    }

    return completions;
  }

  enum Rarity {
    COMMON("§f§lCOMMON", "§f"),
    UNCOMMON("§a§lUNCOMMON", "§a"),
    RARE("§9§lRARE", "§9"),
    EPIC("§5§lEPIC", "§5"),
    LEGENDARY("§6§lLEGENDARY", "§6"),
    MYTHIC("§d§lMYTHIC", "§d");

    final String displayText;
    final String color;

    Rarity(String displayText, String color) {
      this.displayText = displayText;
      this.color = color;
    }
  }

  enum ArmorTier {
    APPRENTICE("Apprentice", Rarity.COMMON,
        new ArmorStats(5, 3, 1, 1),    // helmet
        new ArmorStats(8, 5, 1, 1),    // chestplate
        new ArmorStats(6, 4, 1, 1),    // leggings
        new ArmorStats(4, 2, 1, 1),    // boots
        5, 3  // set bonus
    ),
    ADEPT("Adept", Rarity.UNCOMMON,
        new ArmorStats(8, 6, 10, 8),   // helmet
        new ArmorStats(12, 10, 10, 8), // chestplate
        new ArmorStats(10, 8, 10, 8),  // leggings
        new ArmorStats(6, 5, 10, 8),   // boots
        8, 6  // set bonus
    ),
    MYSTIC("Mystic", Rarity.RARE,
        new ArmorStats(12, 9, 20, 16),  // helmet
        new ArmorStats(18, 14, 20, 16), // chestplate
        new ArmorStats(15, 12, 20, 16), // leggings
        new ArmorStats(10, 8, 20, 16),  // boots
        12, 9  // set bonus
    ),
    SORCERER("Sorcerer", Rarity.EPIC,
        new ArmorStats(16, 12, 30, 24), // helmet
        new ArmorStats(24, 18, 30, 24), // chestplate
        new ArmorStats(20, 15, 30, 24), // leggings
        new ArmorStats(13, 10, 30, 24), // boots
        16, 12  // set bonus
    ),
    ARCHMAGE("Archmage", Rarity.LEGENDARY,
        new ArmorStats(20, 15, 35, 30), // helmet
        new ArmorStats(30, 25, 40, 35), // chestplate
        new ArmorStats(25, 20, 38, 33), // leggings
        new ArmorStats(15, 12, 33, 28), // boots
        20, 15  // set bonus
    ),
    CELESTIAL("Celestial", Rarity.MYTHIC,
        new ArmorStats(25, 20, 65, 55), // helmet
        new ArmorStats(38, 30, 65, 55), // chestplate
        new ArmorStats(32, 25, 65, 55), // leggings
        new ArmorStats(20, 15, 65, 55), // boots
        25, 20  // set bonus
    ),
    VOLATILE("Volatile", Rarity.MYTHIC,
        new ArmorStats(25, 15, 60, 1), // helmet - increased wand damage
        new ArmorStats(35, 25, 60, 1), // chestplate - increased wand damage
        new ArmorStats(30, 20, 60, 1), // leggings - increased wand damage
        new ArmorStats(20, 12, 60, 1), // boots - increased wand damage
        0, 18  // set bonus - only defense (for conversion), no wand bonus shown
    );

    final String displayName;
    final Rarity rarity;
    final ArmorStats helmet;
    final ArmorStats chestplate;
    final ArmorStats leggings;
    final ArmorStats boots;
    final double setBonusWand;
    final double setBonusDefense;

    ArmorTier(String displayName, Rarity rarity, ArmorStats helmet, ArmorStats chestplate,
              ArmorStats leggings, ArmorStats boots, double setBonusWand, double setBonusDefense) {
      this.displayName = displayName;
      this.rarity = rarity;
      this.helmet = helmet;
      this.chestplate = chestplate;
      this.leggings = leggings;
      this.boots = boots;
      this.setBonusWand = setBonusWand;
      this.setBonusDefense = setBonusDefense;
    }

    ArmorStats getStats(String piece) {
      return switch (piece.toLowerCase()) {
        case "helmet" -> helmet;
        case "chestplate" -> chestplate;
        case "leggings" -> leggings;
        case "boots" -> boots;
        default -> helmet;
      };
    }

    Color[] getColors() {
      return switch (this) {
        case APPRENTICE -> new Color[] {
          Color.fromRGB(100, 200, 255),  // helmet - light blue
          Color.fromRGB(90, 190, 245),   // chestplate
          Color.fromRGB(80, 180, 235),   // leggings
          Color.fromRGB(70, 170, 225)    // boots
        };
        case ADEPT -> new Color[] {
          Color.fromRGB(64, 224, 208),   // helmet - teal
          Color.fromRGB(56, 214, 198),   // chestplate
          Color.fromRGB(48, 204, 188),   // leggings
          Color.fromRGB(40, 194, 178)    // boots
        };
        case MYSTIC -> new Color[] {
          Color.fromRGB(65, 105, 225),   // helmet - royal blue
          Color.fromRGB(60, 95, 215),    // chestplate
          Color.fromRGB(55, 85, 205),    // leggings
          Color.fromRGB(50, 75, 195)     // boots
        };
        case SORCERER -> new Color[] {
          Color.fromRGB(90, 20, 150),    // helmet - indigo
          Color.fromRGB(85, 15, 140),    // chestplate
          Color.fromRGB(80, 10, 135),    // leggings
          Color.fromRGB(75, 5, 130)      // boots
        };
        case ARCHMAGE -> new Color[] {
          Color.fromRGB(148, 0, 211),    // helmet - dark violet
          Color.fromRGB(138, 43, 226),   // chestplate - blue violet
          Color.fromRGB(148, 0, 211),    // leggings - dark violet
          Color.fromRGB(128, 0, 128)     // boots - purple
        };
        case CELESTIAL -> new Color[] {
          Color.fromRGB(255, 255, 255),  // helmet - white
          Color.fromRGB(255, 215, 0),    // chestplate - gold
          Color.fromRGB(255, 195, 0),    // leggings - darker gold
          Color.fromRGB(255, 175, 0)     // boots - deep gold
        };
        case VOLATILE -> new Color[] {
          Color.fromRGB(255, 60, 0),     // helmet - bright red-orange
          Color.fromRGB(235, 50, 10),    // chestplate
          Color.fromRGB(215, 40, 20),    // leggings
          Color.fromRGB(195, 30, 30)     // boots - deep red
        };
      };
    }
  }

  record ArmorStats(double wandBonus, double defenseBonus, int magicReq, int defenseReq) {}

  enum BerserkerTier {
    BRAWLER("Brawler", Rarity.COMMON,
        new WarriorArmorStats(8, 3, 1, 1),
        new WarriorArmorStats(12, 5, 1, 1),
        new WarriorArmorStats(10, 4, 1, 1),
        new WarriorArmorStats(6, 2, 1, 1),
        6, 3
    ),
    WARRIOR("Warrior", Rarity.UNCOMMON,
        new WarriorArmorStats(15, 6, 10, 8),
        new WarriorArmorStats(22, 10, 10, 8),
        new WarriorArmorStats(18, 8, 10, 8),
        new WarriorArmorStats(10, 5, 10, 8),
        10, 6
    ),
    CHAMPION("Champion", Rarity.RARE,
        new WarriorArmorStats(24, 9, 20, 16),
        new WarriorArmorStats(35, 14, 20, 16),
        new WarriorArmorStats(30, 12, 20, 16),
        new WarriorArmorStats(16, 8, 20, 16),
        16, 9
    ),
    WARLORD("Warlord", Rarity.EPIC,
        new WarriorArmorStats(35, 12, 30, 24),
        new WarriorArmorStats(50, 18, 30, 24),
        new WarriorArmorStats(42, 15, 30, 24),
        new WarriorArmorStats(23, 10, 30, 24),
        22, 12
    ),
    BERSERKER("Berserker", Rarity.LEGENDARY,
        new WarriorArmorStats(48, 15, 40, 35),
        new WarriorArmorStats(70, 25, 40, 35),
        new WarriorArmorStats(58, 20, 40, 35),
        new WarriorArmorStats(32, 12, 40, 35),
        30, 15
    ),
    TITAN("Titan", Rarity.MYTHIC,
        new WarriorArmorStats(65, 20, 65, 55),
        new WarriorArmorStats(95, 30, 65, 55),
        new WarriorArmorStats(80, 25, 65, 55),
        new WarriorArmorStats(45, 15, 65, 55),
        40, 20
    ),
    RAGEFUL("Rageful", Rarity.MYTHIC,
        new WarriorArmorStats(85, 15, 60, 1),
        new WarriorArmorStats(120, 25, 60, 1),
        new WarriorArmorStats(100, 20, 60, 1),
        new WarriorArmorStats(55, 12, 60, 1),
        0, 18
    );

    final String displayName;
    final Rarity rarity;
    final WarriorArmorStats helmet;
    final WarriorArmorStats chestplate;
    final WarriorArmorStats leggings;
    final WarriorArmorStats boots;
    final double setBonusDamage;
    final double setBonusDefense;

    BerserkerTier(String displayName, Rarity rarity, WarriorArmorStats helmet, WarriorArmorStats chestplate,
                  WarriorArmorStats leggings, WarriorArmorStats boots, double setBonusDamage, double setBonusDefense) {
      this.displayName = displayName;
      this.rarity = rarity;
      this.helmet = helmet;
      this.chestplate = chestplate;
      this.leggings = leggings;
      this.boots = boots;
      this.setBonusDamage = setBonusDamage;
      this.setBonusDefense = setBonusDefense;
    }

    WarriorArmorStats getStats(String piece) {
      return switch (piece.toLowerCase()) {
        case "helmet" -> helmet;
        case "chestplate" -> chestplate;
        case "leggings" -> leggings;
        case "boots" -> boots;
        default -> helmet;
      };
    }

    Color[] getColors() {
      return switch (this) {
        case BRAWLER -> new Color[] {
          Color.fromRGB(180, 100, 80),
          Color.fromRGB(170, 90, 70),
          Color.fromRGB(160, 80, 60),
          Color.fromRGB(150, 70, 50)
        };
        case WARRIOR -> new Color[] {
          Color.fromRGB(200, 80, 60),
          Color.fromRGB(190, 70, 50),
          Color.fromRGB(180, 60, 40),
          Color.fromRGB(170, 50, 30)
        };
        case CHAMPION -> new Color[] {
          Color.fromRGB(220, 60, 40),
          Color.fromRGB(210, 50, 30),
          Color.fromRGB(200, 40, 20),
          Color.fromRGB(190, 30, 10)
        };
        case WARLORD -> new Color[] {
          Color.fromRGB(160, 40, 120),
          Color.fromRGB(150, 35, 110),
          Color.fromRGB(140, 30, 100),
          Color.fromRGB(130, 25, 90)
        };
        case BERSERKER -> new Color[] {
          Color.fromRGB(255, 100, 0),
          Color.fromRGB(245, 90, 10),
          Color.fromRGB(235, 80, 20),
          Color.fromRGB(225, 70, 30)
        };
        case TITAN -> new Color[] {
          Color.fromRGB(140, 140, 140),
          Color.fromRGB(160, 160, 160),
          Color.fromRGB(150, 150, 150),
          Color.fromRGB(130, 130, 130)
        };
        case RAGEFUL -> new Color[] {
          Color.fromRGB(200, 0, 0),
          Color.fromRGB(180, 0, 0),
          Color.fromRGB(160, 0, 0),
          Color.fromRGB(140, 0, 0)
        };
      };
    }
  }

  enum BladedancerTier {
    SCOUT("Scout", Rarity.COMMON,
        new BladedancerArmorStats(12, 3, 1, 1),
        new BladedancerArmorStats(18, 5, 1, 1),
        new BladedancerArmorStats(15, 4, 1, 1),
        new BladedancerArmorStats(9, 2, 1, 1),
        8, 3
    ),
    DUELIST("Duelist", Rarity.UNCOMMON,
        new BladedancerArmorStats(20, 6, 10, 8),
        new BladedancerArmorStats(30, 10, 10, 8),
        new BladedancerArmorStats(25, 8, 10, 8),
        new BladedancerArmorStats(15, 5, 10, 8),
        12, 6
    ),
    SKIRMISHER("Skirmisher", Rarity.RARE,
        new BladedancerArmorStats(30, 9, 20, 16),
        new BladedancerArmorStats(45, 14, 20, 16),
        new BladedancerArmorStats(38, 12, 20, 16),
        new BladedancerArmorStats(22, 8, 20, 16),
        18, 9
    ),
    SHADOWBLADE("Shadowblade", Rarity.EPIC,
        new BladedancerArmorStats(42, 12, 30, 24),
        new BladedancerArmorStats(63, 18, 30, 24),
        new BladedancerArmorStats(53, 15, 30, 24),
        new BladedancerArmorStats(30, 10, 30, 24),
        25, 12
    ),
    BLADEDANCER("Bladedancer", Rarity.LEGENDARY,
        new BladedancerArmorStats(55, 15, 40, 35),
        new BladedancerArmorStats(83, 25, 40, 35),
        new BladedancerArmorStats(68, 20, 40, 35),
        new BladedancerArmorStats(40, 12, 40, 35),
        35, 15
    ),
    TEMPEST("Tempest", Rarity.MYTHIC,
        new BladedancerArmorStats(70, 20, 65, 55),
        new BladedancerArmorStats(105, 30, 65, 55),
        new BladedancerArmorStats(88, 25, 65, 55),
        new BladedancerArmorStats(50, 15, 65, 55),
        45, 20
    ),
    PHANTOM("Phantom", Rarity.MYTHIC,
        new BladedancerArmorStats(90, 15, 60, 1),
        new BladedancerArmorStats(90, 25, 60, 1),
        new BladedancerArmorStats(90, 20, 60, 1),
        new BladedancerArmorStats(90, 12, 60, 1),
        60, 18
    );

    final String displayName;
    final Rarity rarity;
    final BladedancerArmorStats helmet;
    final BladedancerArmorStats chestplate;
    final BladedancerArmorStats leggings;
    final BladedancerArmorStats boots;
    final double setBonusHitChance;
    final double setBonusDefense;

    BladedancerTier(String displayName, Rarity rarity, BladedancerArmorStats helmet, BladedancerArmorStats chestplate,
                    BladedancerArmorStats leggings, BladedancerArmorStats boots, double setBonusHitChance, double setBonusDefense) {
      this.displayName = displayName;
      this.rarity = rarity;
      this.helmet = helmet;
      this.chestplate = chestplate;
      this.leggings = leggings;
      this.boots = boots;
      this.setBonusHitChance = setBonusHitChance;
      this.setBonusDefense = setBonusDefense;
    }

    BladedancerArmorStats getStats(String piece) {
      return switch (piece.toLowerCase()) {
        case "helmet" -> helmet;
        case "chestplate" -> chestplate;
        case "leggings" -> leggings;
        case "boots" -> boots;
        default -> helmet;
      };
    }

    Color[] getColors() {
      return switch (this) {
        case SCOUT -> new Color[] {
          Color.fromRGB(150, 200, 220),
          Color.fromRGB(140, 190, 210),
          Color.fromRGB(130, 180, 200),
          Color.fromRGB(120, 170, 190)
        };
        case DUELIST -> new Color[] {
          Color.fromRGB(100, 180, 200),
          Color.fromRGB(90, 170, 190),
          Color.fromRGB(80, 160, 180),
          Color.fromRGB(70, 150, 170)
        };
        case SKIRMISHER -> new Color[] {
          Color.fromRGB(60, 140, 180),
          Color.fromRGB(50, 130, 170),
          Color.fromRGB(40, 120, 160),
          Color.fromRGB(30, 110, 150)
        };
        case SHADOWBLADE -> new Color[] {
          Color.fromRGB(80, 60, 140),
          Color.fromRGB(70, 50, 130),
          Color.fromRGB(60, 40, 120),
          Color.fromRGB(50, 30, 110)
        };
        case BLADEDANCER -> new Color[] {
          Color.fromRGB(0, 200, 255),
          Color.fromRGB(10, 190, 245),
          Color.fromRGB(20, 180, 235),
          Color.fromRGB(30, 170, 225)
        };
        case TEMPEST -> new Color[] {
          Color.fromRGB(200, 255, 255),
          Color.fromRGB(180, 245, 255),
          Color.fromRGB(160, 235, 245),
          Color.fromRGB(140, 225, 235)
        };
        case PHANTOM -> new Color[] {
          Color.fromRGB(140, 100, 200),
          Color.fromRGB(130, 90, 190),
          Color.fromRGB(120, 80, 180),
          Color.fromRGB(110, 70, 170)
        };
      };
    }
  }

  record WarriorArmorStats(double damageBonus, double defenseBonus, int attackReq, int defenseReq) {}
  record BladedancerArmorStats(double multiHitChance, double defenseBonus, int attackReq, int defenseReq) {}
}
