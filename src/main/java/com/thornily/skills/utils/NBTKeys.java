package com.thornily.skills.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public class NBTKeys {
  private final Plugin plugin;

  public final NamespacedKey damageCustom;
  public final NamespacedKey damageSkill;
  public final NamespacedKey noMelee;
  public final NamespacedKey fireWand;
  public final NamespacedKey mageArmor;
  public final NamespacedKey tierName;
  public final NamespacedKey wandDamageBonus;
  public final NamespacedKey defenseBonus;
  public final NamespacedKey defenseConversionPenalty;
  public final NamespacedKey magicRequirement;
  public final NamespacedKey defenseRequirement;
  public final NamespacedKey rarity;
  public final NamespacedKey warriorArmor;
  public final NamespacedKey armorPath;
  public final NamespacedKey meleeDamageBonus;
  public final NamespacedKey multiHitChance;
  public final NamespacedKey multiHitCount;
  public final NamespacedKey attackRequirement;

  public NBTKeys(Plugin plugin) {
    this.plugin = plugin;
    this.damageCustom = new NamespacedKey(plugin, "damage_custom");
    this.damageSkill = new NamespacedKey(plugin, "damage_skill");
    this.noMelee = new NamespacedKey(plugin, "no_melee");
    this.fireWand = new NamespacedKey(plugin, "fire_wand");
    this.mageArmor = new NamespacedKey(plugin, "mage_armor");
    this.tierName = new NamespacedKey(plugin, "tier_name");
    this.wandDamageBonus = new NamespacedKey(plugin, "wand_damage_bonus");
    this.defenseBonus = new NamespacedKey(plugin, "defense_bonus");
    this.defenseConversionPenalty = new NamespacedKey(plugin, "defense_conversion_penalty");
    this.magicRequirement = new NamespacedKey(plugin, "magic_requirement");
    this.defenseRequirement = new NamespacedKey(plugin, "defense_requirement");
    this.rarity = new NamespacedKey(plugin, "rarity");
    this.warriorArmor = new NamespacedKey(plugin, "warrior_armor");
    this.armorPath = new NamespacedKey(plugin, "armor_path");
    this.meleeDamageBonus = new NamespacedKey(plugin, "melee_damage_bonus");
    this.multiHitChance = new NamespacedKey(plugin, "multi_hit_chance");
    this.multiHitCount = new NamespacedKey(plugin, "multi_hit_count");
    this.attackRequirement = new NamespacedKey(plugin, "attack_requirement");
  }
}
