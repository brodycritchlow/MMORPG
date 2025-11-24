package com.thornily.skills.listeners;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.models.Skill;
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
  DatabaseManager dbmanager;
  private static final Map<UUID, Map<UUID, Double>> damageTracker =
      new HashMap<>();
  private static final Map<UUID, Map<UUID, String>> skillTracker =
      new HashMap<>();
  private static final Map<UUID, ArmorStand> healthDisplays = new HashMap<>();
  private static final Set<UUID> applyingCustomDamage = new HashSet<>();

  public SkillGainListener(SkillsPlugin plugin) {
    this.plugin = plugin;
    this.dbmanager = plugin.getDatabase();
  }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target))
            return;

        if (applyingCustomDamage.contains(target.getUniqueId()))
            return;

        // Prevent magic damage to armor stands
        if (target instanceof ArmorStand && event.getCause() == EntityDamageEvent.DamageCause.MAGIC) {
            event.setCancelled(true);
            return;
        }

    if (applyingCustomDamage.contains(target.getUniqueId()))
      return;

    ItemMeta itemMeta = player.getInventory().getItemInMainHand().getItemMeta();
    if (itemMeta == null)
      return;

    // Check for custom damage
    NamespacedKey damageKey = new NamespacedKey(plugin, "damage_custom");
    if (!itemMeta.getPersistentDataContainer().has(damageKey,
                                                   PersistentDataType.DOUBLE))
      return;

    Double customDamage = itemMeta.getPersistentDataContainer().get(
        damageKey, PersistentDataType.DOUBLE);
    if (customDamage == null)
      return;

    // Check for no_melee flag
    NamespacedKey noMeleeKey = new NamespacedKey(plugin, "no_melee");
    boolean noMelee = itemMeta.getPersistentDataContainer().has(
        noMeleeKey, PersistentDataType.BYTE);

    // If this is a melee attack and the item has no_melee, cancel it
    if (noMelee &&
        event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      event.setCancelled(true);
      return;
    }

    // Get the skill type
    NamespacedKey skillKey = new NamespacedKey(plugin, "damage_skill");
    String skillType = itemMeta.getPersistentDataContainer().get(
        skillKey, PersistentDataType.STRING);
    if (skillType == null)
      skillType = "combat";

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
        damage *= (1.0 + (skillLevel * 0.05));
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

          double requiredXP = getRequiredXP(currentLevel);
          while (newXP >= requiredXP && newLevel < 99) {
            newXP -= requiredXP;
            newLevel++;
            requiredXP = getRequiredXP(newLevel);

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

  private double getRequiredXP(int level) { return Math.pow(level, 2) * 45; }

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

    private String createProgressBar(double current, double required) {
        int bars = 20;
        double percentage = Math.min(current / required, 1.0);
        int filled = (int) (bars * percentage);

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
