package com.thornily.skills.listeners;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.utils.NBTKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WandListener implements Listener {

  private final SkillsPlugin plugin;
  private final NBTKeys keys;

  public WandListener(SkillsPlugin plugin) {
    this.plugin = plugin;
    this.keys = plugin.getNBTKeys();
  }

  @EventHandler
  public void onWandUse(PlayerInteractEvent event) {
    if (!(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK))
      return;

    Player player = event.getPlayer();
    if (!player.getInventory().getItemInMainHand().hasItemMeta())
      return;

    Byte isWand = player.getInventory()
                      .getItemInMainHand()
                      .getItemMeta()
                      .getPersistentDataContainer()
                      .get(keys.fireWand, PersistentDataType.BYTE);
    if (isWand == null)
      return;

    shootSpiralBeam(player);
  }

  private void shootSpiralBeam(Player player) {
    Location start = player.getEyeLocation().clone().add(0, -0.5, 0);
    Vector direction = start.getDirection().normalize();
    double length = 25.0;
    double step = 0.8;
    double spiralRadius = 0.4;
    double rotationSpeed = Math.PI / 2;

    new BukkitRunnable() {
      double traveled = 0;
      double spiralAngle = 0;

      @Override
      public void run() {
        if (traveled >= length) {
          cancel();
          return;
        }

        Location center = start.clone().add(direction.clone().multiply(traveled));

        for (Entity e : center.getWorld().getNearbyEntities(center, 0.5, 1, 0.5)) {
          if (e instanceof LivingEntity le && le != player) {
            org.bukkit.event.entity.EntityDamageByEntityEvent damageEvent =
                new org.bukkit.event.entity.EntityDamageByEntityEvent(
                    player, le, org.bukkit.event.entity.EntityDamageEvent.DamageCause.MAGIC, 1.0);
            Bukkit.getPluginManager().callEvent(damageEvent);

            if (!damageEvent.isCancelled()) {
              le.damage(damageEvent.getDamage());
            }

            cancel();
            return;
          }
        }

        if (center.getBlock().getType().isSolid()) {
          cancel();
          return;
        }

        player.getWorld().spawnParticle(Particle.FLAME, center, 1, 0, 0, 0, 0);

        Vector right = getPerpendicularVector(direction);
        Vector up = direction.clone().crossProduct(right);

        double spiralOffsetX = spiralRadius * Math.cos(spiralAngle);
        double spiralOffsetZ = spiralRadius * Math.sin(spiralAngle);
        Vector spiralOffset = right.clone().multiply(spiralOffsetX).add(up.clone().multiply(spiralOffsetZ));
        Location spiralLoc = center.clone().add(spiralOffset);

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spiralLoc, 1, 0.1, 0.1, 0.1, 0);

        traveled += step;
        spiralAngle += rotationSpeed;
      }
    }.runTaskTimer(plugin, 0L, 0L);
  }

  private Vector getPerpendicularVector(Vector direction) {
    direction = direction.clone().normalize();
    Vector perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0));
    if (perpendicular.lengthSquared() < 0.01) {
      perpendicular = direction.clone().crossProduct(new Vector(1, 0, 0));
    }
    return perpendicular.normalize();
  }
}