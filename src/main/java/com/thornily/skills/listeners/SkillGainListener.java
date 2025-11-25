package com.thornily.skills.listeners;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.models.Skill;
import com.thornily.skills.utils.NBTKeys;
import com.thornily.skills.utils.SkillUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class SkillGainListener implements Listener {
  private final SkillsPlugin plugin;
  private final DatabaseManager dbmanager;
  private final NBTKeys keys;
  private static final Map<UUID, Map<UUID, Double>> damageTracker = new HashMap<>();
  private static final Map<UUID, Map<UUID, String>> skillTracker = new HashMap<>();
  private static final Map<UUID, ArmorStand> healthDisplays = new HashMap<>();
  private static final Set<UUID> applyingCustomDamage = new HashSet<>();

  public SkillGainListener(SkillsPlugin plugin) {
    this.plugin = plugin;
    this.dbmanager = plugin.getDatabase();
    this.keys = plugin.getNBTKeys();
  }

  /**
   * Apply custom magic damage with skill bonuses and armor bonuses
   * Used by Volatile armor passive attacks and other magic sources
   */
  public void applyCustomMagicDamage(Player player, LivingEntity target, double baseDamage) {
    if (applyingCustomDamage.contains(target.getUniqueId()))
      return;

    try {
      applyingCustomDamage.add(target.getUniqueId());

      double damage = baseDamage;

      ResultSet rs = dbmanager.executeQuery(
          "SELECT level FROM player_skills WHERE uuid = '" +
          player.getUniqueId() + "' AND skill_name = 'MAGIC'");
      if (rs != null && rs.next()) {
        int skillLevel = rs.getInt("level");
        double skillBonus = skillLevel * 0.05;
        double armorBonus = getWandDamageBonus(player);

        damage *= (1.0 + skillBonus + armorBonus);
        damage = Math.floor(damage);
        rs.close();
      }

      target.damage(damage, player);
      spawnDamageIndicator(target, damage);

      damageTracker.putIfAbsent(target.getUniqueId(), new HashMap<>());
      Map<UUID, Double> playerDamage = damageTracker.get(target.getUniqueId());
      playerDamage.put(player.getUniqueId(),
                       playerDamage.getOrDefault(player.getUniqueId(), 0.0) +
                           damage);

      skillTracker.putIfAbsent(target.getUniqueId(), new HashMap<>());
      Map<UUID, String> playerSkills = skillTracker.get(target.getUniqueId());
      playerSkills.put(player.getUniqueId(), "magic");

    } catch (SQLException e) {
      plugin.getLogger().log(Level.SEVERE,
                             "Failed to get magic level", e);
    } finally {
      updateHealthDisplay(target);
      applyingCustomDamage.remove(target.getUniqueId());
    }
  }

  /**
   * Apply direct damage without recalculating bonuses
   * Used by multi-hit mechanics where damage is already calculated
   */
  public void applyDirectDamage(Player player, LivingEntity target, double finalDamage, String skillType) {
    try {
      applyingCustomDamage.add(target.getUniqueId());

      spawnDamageIndicator(target, finalDamage);

      damageTracker.putIfAbsent(target.getUniqueId(), new HashMap<>());
      Map<UUID, Double> playerDamage = damageTracker.get(target.getUniqueId());
      playerDamage.put(player.getUniqueId(),
                       playerDamage.getOrDefault(player.getUniqueId(), 0.0) +
                           finalDamage);

      skillTracker.putIfAbsent(target.getUniqueId(), new HashMap<>());
      Map<UUID, String> playerSkills = skillTracker.get(target.getUniqueId());
      playerSkills.put(player.getUniqueId(), skillType);

      target.damage(finalDamage, player);

    } finally {
      applyingCustomDamage.remove(target.getUniqueId());
      updateHealthDisplay(target);
    }
  }

  @EventHandler
  public void onEntityDamage(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target))
      return;

    if (applyingCustomDamage.contains(target.getUniqueId())) {
      updateHealthDisplay(target);
      return;
    }

    if (target instanceof ArmorStand && event.getCause() == EntityDamageEvent.DamageCause.MAGIC) {
      event.setCancelled(true);
      return;
    }

    ItemStack weapon = player.getInventory().getItemInMainHand();
    ItemMeta itemMeta = weapon.getItemMeta();
    if (itemMeta == null)
      return;

    if (!itemMeta.getPersistentDataContainer().has(keys.damageCustom, PersistentDataType.DOUBLE)) {
      plugin.getLogger().info("No custom damage on weapon: " + weapon.getType());
      return;
    }

    Double customDamage = itemMeta.getPersistentDataContainer().get(keys.damageCustom, PersistentDataType.DOUBLE);
    if (customDamage == null)
      return;

    boolean noMelee = itemMeta.getPersistentDataContainer().has(keys.noMelee, PersistentDataType.BYTE);

    if (noMelee && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      event.setCancelled(true);
      return;
    }

    String skillType = itemMeta.getPersistentDataContainer().get(keys.damageSkill, PersistentDataType.STRING);
    if (skillType == null)
      skillType = "attack";

    if (skillType.equalsIgnoreCase("combat"))
      skillType = "attack";

    plugin.getLogger().info("Applying custom damage: " + customDamage + " with skill: " + skillType);

    try {
      applyingCustomDamage.add(target.getUniqueId());
      event.setCancelled(true);

      double damage = customDamage;

      ResultSet rs = dbmanager.executeQuery(
          "SELECT level FROM player_skills WHERE uuid = '" +
          player.getUniqueId() + "' AND skill_name = '" +
          skillType.toUpperCase() + "'");
      if (rs != null && rs.next()) {
        int skillLevel = rs.getInt("level");
        double skillBonus = skillLevel * 0.05;
        double armorBonus = 0.0;

        if (skillType.equalsIgnoreCase("magic")) {
          armorBonus = getWandDamageBonus(player);
        } else if (skillType.equalsIgnoreCase("combat") || skillType.equalsIgnoreCase("attack")) {
          armorBonus = getMeleeDamageBonus(player);
        }

        damage *= (1.0 + skillBonus + armorBonus);
        damage = Math.floor(damage);
        rs.close();

        plugin.getLogger().info("Final damage for FIRST HIT: " + damage + " (skill: " + skillLevel + ", skillBonus: " + skillBonus + ", armorBonus: " + armorBonus + ")");
      }

      target.damage(damage, player);
      spawnDamageIndicator(target, damage);

      damageTracker.putIfAbsent(target.getUniqueId(), new HashMap<>());
      Map<UUID, Double> playerDamage = damageTracker.get(target.getUniqueId());
      playerDamage.put(player.getUniqueId(),
                       playerDamage.getOrDefault(player.getUniqueId(), 0.0) +
                           damage);

      skillTracker.putIfAbsent(target.getUniqueId(), new HashMap<>());
      Map<UUID, String> playerSkills = skillTracker.get(target.getUniqueId());
      playerSkills.put(player.getUniqueId(), skillType);

    } catch (SQLException e) {
      plugin.getLogger().log(Level.SEVERE,
                             "Failed to get " + skillType + " level", e);
    } finally {
      updateHealthDisplay(target);
      applyingCustomDamage.remove(target.getUniqueId());
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    ResultSet resultSet = dbmanager.executeQuery(
        "SELECT COUNT(*) as count FROM player_skills WHERE uuid = '" + uuid +
        "'");

    if (resultSet == null) {
      plugin.getLogger().severe("Failed to query player skills for " +
                                event.getPlayer().getName());
      return;
    }

    try {
      if (resultSet.next()) {
        int count = resultSet.getInt("count");
        resultSet.close();

        if (count == 0) {
          for (Skill skill : Skill.values()) {
            dbmanager.executeUpdate(
                "INSERT INTO player_skills (uuid, skill_name) VALUES ('" +
                uuid + "', '" + skill.name() + "')");
            plugin.getLogger().log(Level.INFO, "Adding skill " + skill.name() +
                                                   " for player " +
                                                   event.getPlayer().getName());
          }
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().log(Level.SEVERE,
                             "Could not get resultSet from database.", e);
    }
  }

  @EventHandler
  public void onEntitySpawn(EntitySpawnEvent event) {
    if (!(event.getEntity() instanceof LivingEntity) ||
        event.getEntity() instanceof Player ||
        event.getEntity() instanceof ArmorStand) {
      return;
    }

    updateHealthDisplay((LivingEntity)event.getEntity());
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerDamaged(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;

    double originalDamage = event.getDamage();
    double defenseBonus = getDefenseBonus(player);

    if (defenseBonus > 0) {
      double reducedDamage = originalDamage * (1.0 - defenseBonus);
      double damageBlocked = originalDamage - reducedDamage;

      event.setDamage(reducedDamage);

      // Award Defense XP based on damage blocked (async to prevent lag)
      double finalDamageBlocked = damageBlocked;
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        awardDefenseXP(player, finalDamageBlocked, originalDamage);
      });
    } else {
      // Even with 0% defense, award small XP for taking damage
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        awardDefenseXP(player, 0, originalDamage);
      });
    }
  }

  @EventHandler
  public void onEntityDeath(EntityDeathEvent event) {
    event.setDroppedExp(0);

    UUID entityId = event.getEntity().getUniqueId();

    ArmorStand healthDisplay = healthDisplays.remove(entityId);
    if (healthDisplay != null && !healthDisplay.isDead()) {
      healthDisplay.remove();
    }

    Map<UUID, Double> playerDamage = damageTracker.remove(entityId);
    Map<UUID, String> playerSkills = skillTracker.remove(entityId);

    if (playerDamage == null || playerDamage.isEmpty()) {
      return;
    }

    String entityType = event.getEntityType().name();
    double baseXP = plugin.getConfig().getDouble(
        "combat_xp." + entityType,
        plugin.getConfig().getDouble("combat_xp.DEFAULT", 5.0));

    double totalDamage =
        playerDamage.values().stream().mapToDouble(Double::doubleValue).sum();

    for (Map.Entry<UUID, Double> entry : playerDamage.entrySet()) {
      UUID playerId = entry.getKey();
      double damageDealt = entry.getValue();
      double damagePercentage = damageDealt / totalDamage;
      double xpAmount = baseXP * damagePercentage;

      Player player = plugin.getServer().getPlayer(playerId);
      if (player == null || !player.isOnline()) {
        continue;
      }

      // Get the skill type for this player's damage
      String skillName = playerSkills.getOrDefault(playerId, "ATTACK");

      ResultSet rs = dbmanager.executeQuery(
          "SELECT level, experience FROM player_skills WHERE uuid = '" +
          playerId + "' AND skill_name = '" + skillName.toUpperCase() + "'");

      try {
        if (rs != null && rs.next()) {
          int currentLevel = rs.getInt("level");
          double currentXP = rs.getDouble("experience");
          rs.close();

          double newXP = currentXP + xpAmount;
          int newLevel = currentLevel;

          double requiredXP = SkillUtils.getRequiredXP(currentLevel);
          while (newXP >= requiredXP && newLevel < 99) {
            newXP -= requiredXP;
            newLevel++;
            requiredXP = SkillUtils.getRequiredXP(newLevel);

            String levelUpMsg =
                skillName.equalsIgnoreCase("MAGIC")
                    ? "§d§l✦ Magic Level Up! §7(" + (newLevel - 1) + " → " +
                          newLevel + ")"
                    : "§6§l✦ Combat Level Up! §7(" + (newLevel - 1) + " → " +
                          newLevel + ")";
            player.sendMessage(levelUpMsg);
            player.playSound(player.getLocation(),
                             org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f,
                             1.0f);
          }

          dbmanager.executeUpdate(
              "UPDATE player_skills SET level = " + newLevel +
              ", experience = " + newXP + " WHERE uuid = '" + playerId +
              "' AND skill_name = '" + skillName.toUpperCase() + "'");



          String skillColor = skillName.equalsIgnoreCase("MAGIC") ? "§d" : "§a";
          String skillNameDisplay =
              skillName.equalsIgnoreCase("MAGIC") ? "Magic" : "Combat";

          String xpMsg = skillColor + "+" + String.format("%.1f", xpAmount) +
                         " " + skillNameDisplay + " XP §7(" +
                         String.format("%.0f%%", damagePercentage * 100) + ")";
          player.sendActionBar(xpMsg);
        }
      } catch (SQLException e) {
        plugin.getLogger().log(
            Level.SEVERE, "Failed to add combat XP for " + player.getName(), e);
      }
    }
  }

  private void spawnDamageIndicator(LivingEntity entity, double damage) {
    Location loc = entity.getLocation().add((Math.random() - 0.5) * 1.5,
                                            entity.getHeight() * 0.5,
                                            (Math.random() - 0.5) * 1.5);

    ArmorStand hologram =
        (ArmorStand)entity.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
    hologram.setVisible(false);
    hologram.setGravity(false);
    hologram.setMarker(true);
    hologram.setCustomNameVisible(true);
    hologram.setCustomName("§c" + String.format("%.1f", damage));

    new BukkitRunnable() {
      int ticks = 0;

      @Override
      public void run() {
        if (ticks >= 20 || hologram.isDead()) {
          hologram.remove();
          cancel();
          return;
        }

        Location currentLoc = hologram.getLocation();
        currentLoc.add(0, 0.05, 0);
        hologram.teleport(currentLoc);
        ticks++;
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  private void updateHealthDisplay(LivingEntity entity) {
    UUID entityId = entity.getUniqueId();
    ArmorStand healthDisplay = healthDisplays.get(entityId);

    String entityName = entity.getCustomName() != null
                            ? entity.getCustomName()
                            : entity.getType().name();
    double health = Math.max(0, entity.getHealth());
    double maxHealth = entity.getMaxHealth();

    plugin.getLogger().info("Health Display: " + entityName + " - Health: " + health + "/" + maxHealth);

    String healthBar =
        String.format("§e%s §c❤ %.0f/%.0f", entityName, health, maxHealth);

    if (healthDisplay == null || healthDisplay.isDead()) {
      Location loc = entity.getLocation().add(0, entity.getHeight() + 0.5, 0);
      healthDisplay = (ArmorStand)entity.getWorld().spawnEntity(
          loc, EntityType.ARMOR_STAND);
      healthDisplay.setVisible(false);
      healthDisplay.setGravity(false);
      healthDisplay.setMarker(true);
      healthDisplay.setCustomNameVisible(true);
      healthDisplays.put(entityId, healthDisplay);

      ArmorStand finalHealthDisplay = healthDisplay;
      new BukkitRunnable() {
        @Override
        public void run() {
          if (entity.isDead() || finalHealthDisplay.isDead()) {
            finalHealthDisplay.remove();
            healthDisplays.remove(entityId);
            cancel();
            return;
          }

          Location newLoc =
              entity.getLocation().add(0, entity.getHeight() + 0.5, 0);
          finalHealthDisplay.teleport(newLoc);
        }
      }.runTaskTimer(plugin, 0L, 1L);
    }

    healthDisplay.setCustomName(healthBar);
  }

  private double getWandDamageBonus(Player player) {
    double totalBonus = 0.0;

    ItemStack[] armor = player.getInventory().getArmorContents();
    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;

      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(keys.wandDamageBonus, PersistentDataType.DOUBLE)) {
        Double bonus = meta.getPersistentDataContainer().get(keys.wandDamageBonus, PersistentDataType.DOUBLE);
        if (bonus != null) {
          totalBonus += bonus;
        }
      }
    }

    String tierName = SkillUtils.getFullSetTier(player, keys);
    if (tierName != null) {
      totalBonus += SkillUtils.getTierSetBonus(tierName, true);
    }

    double totalDefenseBonus = getRawDefenseBonus(player);
    double conversionPenalty = getDefenseConversionPenalty(player);
    double convertedWandBonus = totalDefenseBonus * conversionPenalty * 2.5;
    totalBonus += convertedWandBonus;

    return totalBonus / 100.0;
  }

  private double getMeleeDamageBonus(Player player) {
    double totalBonus = 0.0;

    ItemStack[] armor = player.getInventory().getArmorContents();
    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;

      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(keys.meleeDamageBonus, PersistentDataType.DOUBLE)) {
        Double bonus = meta.getPersistentDataContainer().get(keys.meleeDamageBonus, PersistentDataType.DOUBLE);
        if (bonus != null) {
          totalBonus += bonus;
        }
      }
    }

    String warriorTierName = getFullWarriorSetTier(player);
    if (warriorTierName != null) {
      totalBonus += getWarriorSetBonus(warriorTierName);
    }

    double totalDefenseBonus = getRawDefenseBonus(player);
    double conversionPenalty = getDefenseConversionPenalty(player);
    double convertedMeleeBonus = totalDefenseBonus * conversionPenalty * 3.0;
    totalBonus += convertedMeleeBonus;

    return totalBonus / 100.0;
  }

  private String getFullWarriorSetTier(Player player) {
    ItemStack[] armor = player.getInventory().getArmorContents();
    if (armor.length != 4) return null;

    String tierName = null;
    String path = null;

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) return null;

      ItemMeta meta = piece.getItemMeta();
      Byte isWarrior = meta.getPersistentDataContainer().get(keys.warriorArmor, PersistentDataType.BYTE);
      if (isWarrior == null || isWarrior != 1) return null;

      String piecePath = meta.getPersistentDataContainer().get(keys.armorPath, PersistentDataType.STRING);
      String pieceTier = meta.getPersistentDataContainer().get(keys.tierName, PersistentDataType.STRING);

      if (piecePath == null || pieceTier == null) return null;

      if (path == null) {
        path = piecePath;
      } else if (!path.equals(piecePath)) {
        return null;
      }

      if (tierName == null) {
        tierName = pieceTier;
      } else if (!tierName.equals(pieceTier)) {
        return null;
      }
    }

    return tierName;
  }

  private double getWarriorSetBonus(String tierName) {
    return switch (tierName.toUpperCase()) {
      case "BRAWLER" -> 6.0;
      case "WARRIOR" -> 10.0;
      case "CHAMPION" -> 16.0;
      case "WARLORD" -> 22.0;
      case "BERSERKER" -> 30.0;
      case "TITAN" -> 40.0;
      case "RAGEFUL" -> 0.0;
      default -> 0.0;
    };
  }

  private double getDefenseBonus(Player player) {
    double armorDefense = getRawDefenseBonus(player);
    double conversionPenalty = getDefenseConversionPenalty(player);
    double finalArmorDefense = armorDefense * (1.0 - conversionPenalty);

    double skillDefense = SkillUtils.getSkillLevel(player, "DEFENSE", dbmanager, plugin.getLogger()) * 1.0;

    double totalDefense = finalArmorDefense + skillDefense;

    return applyDiminishingReturns(totalDefense / 100.0);
  }

  private double applyDiminishingReturns(double defenseDecimal) {
    double defensePercentage = defenseDecimal * 100.0;
    final double K = 100.0;
    final double MAX_CAP = 0.75;

    double reduction = (defensePercentage / (defensePercentage + K)) * MAX_CAP;
    return Math.min(reduction, MAX_CAP);
  }

  private void awardDefenseXP(Player player, double damageBlocked, double originalDamage) {
    try {
      double blockedXP = damageBlocked * 2.0;
      double minimumXP = originalDamage * 0.5;
      double xpAmount = Math.max(blockedXP, minimumXP);
      xpAmount = Math.min(xpAmount, 50.0);

      ResultSet rs = dbmanager.executeQuery(
          "SELECT level, experience FROM player_skills WHERE uuid = '" +
          player.getUniqueId() + "' AND skill_name = 'DEFENSE'");

      if (rs != null && rs.next()) {
        int currentLevel = rs.getInt("level");
        double currentXP = rs.getDouble("experience");
        rs.close();

        double newXP = currentXP + xpAmount;
        int newLevel = currentLevel;

        double requiredXP = SkillUtils.getRequiredXP(newLevel);
        while (newXP >= requiredXP && newLevel < 99) {
          newXP -= requiredXP;
          newLevel++;
          requiredXP = SkillUtils.getRequiredXP(newLevel);

          int finalLevel = newLevel;
          Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage("§a§l✦ Defense Level Up! §7(" +
                (finalLevel - 1) + " → " + finalLevel + ")");
            player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
          });
        }

        dbmanager.executeUpdate(
            "UPDATE player_skills SET level = " + newLevel +
            ", experience = " + newXP + " WHERE uuid = '" +
            player.getUniqueId() + "' AND skill_name = 'DEFENSE'");

        String xpMsg = "§a+" + String.format("%.1f", xpAmount) +
            " Defense XP §7(Blocked: " + String.format("%.1f", damageBlocked) + ")";
        Bukkit.getScheduler().runTask(plugin, () -> {
          player.sendActionBar(xpMsg);
        });
      }
    } catch (SQLException e) {
      plugin.getLogger().log(Level.SEVERE,
          "Failed to add Defense XP for " + player.getName(), e);
    }
  }

  private double getRawDefenseBonus(Player player) {
    double totalBonus = 0.0;

    ItemStack[] armor = player.getInventory().getArmorContents();
    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;

      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(keys.defenseBonus, PersistentDataType.DOUBLE)) {
        Double bonus = meta.getPersistentDataContainer().get(keys.defenseBonus, PersistentDataType.DOUBLE);
        if (bonus != null) {
          totalBonus += bonus;
        }
      }
    }

    String tierName = SkillUtils.getFullSetTier(player, keys);
    if (tierName != null) {
      totalBonus += SkillUtils.getTierSetBonus(tierName, false);
    }

    return totalBonus;
  }

  private double getDefenseConversionPenalty(Player player) {
    double totalPenalty = 0.0;
    ItemStack[] armor = player.getInventory().getArmorContents();

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;

      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(keys.defenseConversionPenalty, PersistentDataType.DOUBLE)) {
        Double penalty = meta.getPersistentDataContainer().get(keys.defenseConversionPenalty, PersistentDataType.DOUBLE);
        if (penalty != null) {
          totalPenalty += penalty;
        }
      }
    }

    return totalPenalty / 100.0;
  }
}
