package com.thornily.skills.listeners;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.utils.NBTKeys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VolatileArmorListener {

  private final SkillsPlugin plugin;
  private final NBTKeys keys;
  private int particleTick = 0;
  private final Map<UUID, Integer> lastAttackTick = new HashMap<>();
  private static final int ATTACK_COOLDOWN_TICKS = 200;
  private static final double ATTACK_DAMAGE = 5.0;
  private static final double ATTACK_RANGE = 8.0;

  public VolatileArmorListener(SkillsPlugin plugin) {
    this.plugin = plugin;
    this.keys = plugin.getNBTKeys();
    startParticleTask();
  }

  private void startParticleTask() {
    new BukkitRunnable() {
      @Override
      public void run() {
        particleTick++;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
          if (hasVolatileArmor(player)) {
            spawnVolatileParticles(player);

            UUID playerId = player.getUniqueId();
            int lastAttack = lastAttackTick.getOrDefault(playerId, -ATTACK_COOLDOWN_TICKS);

            if (particleTick - lastAttack >= ATTACK_COOLDOWN_TICKS) {
              performPassiveAttack(player);
              lastAttackTick.put(playerId, particleTick);
            }
          }
        }
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  private boolean hasVolatileArmor(Player player) {
    ItemStack[] armor = player.getInventory().getArmorContents();

    if (armor.length != 4) return false;

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) return false;

      ItemMeta meta = piece.getItemMeta();
      String tierName = meta.getPersistentDataContainer().get(keys.tierName, PersistentDataType.STRING);

      if (!"VOLATILE".equals(tierName)) {
        return false;
      }
    }

    return true;
  }

  private void spawnVolatileParticles(Player player) {
    Location loc = player.getLocation().clone();
    double centerY = loc.getY() + 1.0;

    double intensity = (Math.sin(particleTick * 0.1) + 1.0) / 2.0;

    int red = 255;
    int green = (int) (intensity * 165);
    int blue = 0;

    double baseRadius = 1.2;
    double radius = baseRadius + (intensity * 0.3);
    int particles = 12;

    for (int i = 0; i < particles; i++) {
      double angle = (2 * Math.PI * i / particles) + (particleTick * 0.05);
      double x = loc.getX() + radius * Math.cos(angle);
      double z = loc.getZ() + radius * Math.sin(angle);
      double y = centerY + (Math.sin(particleTick * 0.08 + i) * 0.3);

      Location particleLoc = new Location(loc.getWorld(), x, y, z);

      Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(red, green, blue), 1.2f);
      player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
    }
  }

  private void performPassiveAttack(Player player) {
    Location playerLoc = player.getLocation();

    LivingEntity target = null;
    double minDistance = ATTACK_RANGE;

    for (Entity entity : player.getNearbyEntities(ATTACK_RANGE, ATTACK_RANGE, ATTACK_RANGE)) {
      if (entity instanceof LivingEntity && !(entity instanceof Player)) {
        LivingEntity livingEntity = (LivingEntity) entity;
        double distance = playerLoc.distance(livingEntity.getLocation());

        if (distance < minDistance) {
          minDistance = distance;
          target = livingEntity;
        }
      }
    }

    if (target == null) return;

    Location start = playerLoc.clone().add(0, 1.0, 0);
    Location end = target.getEyeLocation();

    spawnParticleProjectile(start, end, target, player);
  }

  private void spawnParticleProjectile(Location start, Location end, LivingEntity target, Player player) {
    final double speed = 0.4;

    new BukkitRunnable() {
      Location current = start.clone();

      @Override
      public void run() {
        if (!target.isValid() || target.isDead()) {
          cancel();
          return;
        }

        Location targetLoc = target.getEyeLocation();
        Vector direction = targetLoc.toVector().subtract(current.toVector()).normalize();
        double distanceToTarget = current.distance(targetLoc);

        if (distanceToTarget < 0.5) {
          plugin.getSkillGainListener().applyCustomMagicDamage(player, target, ATTACK_DAMAGE);

          Particle.DustOptions explosionDust = new Particle.DustOptions(Color.fromRGB(255, 60, 0), 2.0f);
          targetLoc.getWorld().spawnParticle(Particle.DUST, targetLoc, 30, 0.3, 0.3, 0.3, 0, explosionDust);

          cancel();
          return;
        }

        current.add(direction.multiply(speed));

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.5f);
        current.getWorld().spawnParticle(Particle.DUST, current, 2, 0.05, 0.05, 0.05, 0, dust);
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }
}
