package com.thornily.skills.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WandListener implements Listener {

  private final Plugin plugin;

  public WandListener(Plugin plugin) { this.plugin = plugin; }

  @EventHandler
  public void onWandUse(PlayerInteractEvent event) {
    if (!(event.getAction() == Action.LEFT_CLICK_AIR ||
          event.getAction() == Action.LEFT_CLICK_BLOCK))
      return;
    Player player = event.getPlayer();
    if (!player.getInventory().getItemInMainHand().hasItemMeta())
      return;

    NamespacedKey key = new NamespacedKey(plugin, "fire_wand");
    Byte isWand = player.getInventory()
                      .getItemInMainHand()
                      .getItemMeta()
                      .getPersistentDataContainer()
                      .get(key, PersistentDataType.BYTE);
    if (isWand == null)
      return;

    shootSpiralBeam(player);
  }

  private void shootSpiralBeam(Player player) {
    Location start = player.getEyeLocation().clone().add(
        0, -0.5, 0); // start slightly below eyes
    Vector direction = start.getDirection().normalize();
    double length = 25.0;      // beam length (farther)
    double step = 0.8;         // movement per tick (much faster)
    double spiralRadius = 0.4; // spiral radius around the main beam (tighter)
    double rotationSpeed =
        Math.PI / 2; // radians per step for spiral rotation (much faster)

    new BukkitRunnable() {
      double traveled = 0;
      double spiralAngle = 0;

      @Override
      public void run() {
        if (traveled >= length) {
          cancel();
          return;
        }

        Location center =
            start.clone().add(direction.clone().multiply(traveled));

        // Check for collision with entities first
        for (Entity e :
             center.getWorld().getNearbyEntities(center, 0.5, 1, 0.5)) {
          if (e instanceof LivingEntity le && le != player) {
            // Trigger the damage system with a custom cause to bypass no_melee
            EntityDamageByEntityEvent damageEvent =
                new EntityDamageByEntityEvent(
                    player, le, EntityDamageEvent.DamageCause.MAGIC, 1.0);
            Bukkit.getPluginManager().callEvent(damageEvent);

            cancel();
            return;
          }
        }

        // Check for collision with blocks
        if (center.getBlock().getType().isSolid()) {
          cancel();
          return;
        }

        // First beam: straight particle line
        player.getWorld().spawnParticle(Particle.FLAME, center, 1, 0, 0, 0, 0);

        // Second beam: spiraling around the first beam
        // Calculate perpendicular vectors to the beam direction
        Vector right = getPerpendicularVector(direction);
        Vector up = direction.clone().crossProduct(right);

        // Create spiral offset using perpendicular vectors
        double spiralOffsetX = spiralRadius * Math.cos(spiralAngle);
        double spiralOffsetZ = spiralRadius * Math.sin(spiralAngle);
        Vector spiralOffset = right.clone()
                                  .multiply(spiralOffsetX)
                                  .add(up.clone().multiply(spiralOffsetZ));
        Location spiralLoc = center.clone().add(spiralOffset);

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spiralLoc, 1,
                                        0.1, 0.1, 0.1, 0);

        traveled += step;
        spiralAngle += rotationSpeed; // rotate spiral for next step
      }
    }.runTaskTimer(plugin, 0L, 0L); // runs every tick with no delay
  }

  private Vector getPerpendicularVector(Vector direction) {
    direction = direction.clone().normalize();
    // Cross with up vector (0, 1, 0) to get a perpendicular vector
    Vector perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0));
    if (perpendicular.lengthSquared() < 0.01) {
      // If direction is parallel to up vector, use a different reference
      perpendicular = direction.clone().crossProduct(new Vector(1, 0, 0));
    }
    return perpendicular.normalize();
  }
}
