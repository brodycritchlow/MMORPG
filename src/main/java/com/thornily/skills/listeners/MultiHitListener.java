package com.thornily.skills.listeners;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.utils.NBTKeys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MultiHitListener implements Listener {

  private final SkillsPlugin plugin;
  private final NBTKeys keys;
  private final Random random;
  private final Set<UUID> processingMultiHit = new HashSet<>();

  public MultiHitListener(SkillsPlugin plugin) {
    this.plugin = plugin;
    this.keys = plugin.getNBTKeys();
    this.random = new Random();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityDamage(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player)) return;
    if (!(event.getEntity() instanceof LivingEntity target)) return;

    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

    if (processingMultiHit.contains(player.getUniqueId())) return;

    ItemStack weapon = player.getInventory().getItemInMainHand();
    if (weapon == null || !weapon.hasItemMeta()) return;

    ItemMeta weaponMeta = weapon.getItemMeta();
    String skillType = weaponMeta.getPersistentDataContainer().get(keys.damageSkill, PersistentDataType.STRING);

    if (skillType == null) return;
    if (!skillType.equalsIgnoreCase("combat") && !skillType.equalsIgnoreCase("attack")) return;

    double multiHitChance = getTotalMultiHitChance(player);
    if (multiHitChance <= 0) return;

    int maxHits = getMaxMultiHits(player);
    boolean isPhantom = maxHits == 3;

    double roll = random.nextDouble() * 100.0;
    if (roll > multiHitChance) return;

    processingMultiHit.add(player.getUniqueId());

    double finalDamage = event.getFinalDamage();

    if (isPhantom) {
      executePhantomCascade(player, target, finalDamage);
    } else {
      executeSingleMultiHit(player, target, finalDamage);
    }
  }

  private void executeSingleMultiHit(Player player, LivingEntity target, double finalDamage) {
    int delay = 2 + random.nextInt(3);

    new BukkitRunnable() {
      @Override
      public void run() {
        if (!target.isValid() || target.isDead() || !player.isOnline()) {
          processingMultiHit.remove(player.getUniqueId());
          cancel();
          return;
        }

        plugin.getSkillGainListener().applyDirectDamage(player, target, finalDamage, "attack");

        spawnMultiHitParticles(target.getEyeLocation(), Color.fromRGB(0, 200, 255));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.5f);
        player.sendActionBar("§b§l⚡ MULTI-HIT!");

        processingMultiHit.remove(player.getUniqueId());
      }
    }.runTaskLater(plugin, delay);
  }

  private void executePhantomCascade(Player player, LivingEntity target, double finalDamage) {
    double[] cascadeChances = {90.0, 60.0, 30.0};
    int[] delays = {3, 6, 9};

    for (int i = 0; i < cascadeChances.length; i++) {
      final int hitNumber = i + 1;
      final double chance = cascadeChances[i];
      final int delay = delays[i];

      double roll = random.nextDouble() * 100.0;
      if (roll > chance) {
        if (hitNumber == 1) {
          processingMultiHit.remove(player.getUniqueId());
        }
        break;
      }

      new BukkitRunnable() {
        @Override
        public void run() {
          if (!target.isValid() || target.isDead() || !player.isOnline()) {
            processingMultiHit.remove(player.getUniqueId());
            cancel();
            return;
          }

          plugin.getSkillGainListener().applyDirectDamage(player, target, finalDamage, "attack");

          Color particleColor = switch (hitNumber) {
            case 1 -> Color.fromRGB(100, 220, 255);
            case 2 -> Color.fromRGB(140, 100, 200);
            case 3 -> Color.fromRGB(200, 100, 255);
            default -> Color.fromRGB(255, 255, 255);
          };

          spawnMultiHitParticles(target.getEyeLocation(), particleColor);
          player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.3f + (hitNumber * 0.2f));

          String message = switch (hitNumber) {
            case 1 -> "§b§l⚡ PHANTOM HIT I";
            case 2 -> "§d§l⚡ PHANTOM HIT II";
            case 3 -> "§5§l⚡ PHANTOM HIT III";
            default -> "§b§l⚡ MULTI-HIT!";
          };
          player.sendActionBar(message);

          if (hitNumber == 3) {
            processingMultiHit.remove(player.getUniqueId());
          }
        }
      }.runTaskLater(plugin, delay);
    }
  }

  private double getTotalMultiHitChance(Player player) {
    double totalChance = 0.0;

    ItemStack[] armor = player.getInventory().getArmorContents();
    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;

      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(keys.multiHitChance, PersistentDataType.DOUBLE)) {
        Double chance = meta.getPersistentDataContainer().get(keys.multiHitChance, PersistentDataType.DOUBLE);
        if (chance != null) {
          totalChance += chance;
        }
      }
    }

    String warriorTierName = getFullBladedancerSetTier(player);
    if (warriorTierName != null) {
      totalChance += getBladedancerSetBonus(warriorTierName);
    }

    return totalChance;
  }

  private int getMaxMultiHits(Player player) {
    ItemStack[] armor = player.getInventory().getArmorContents();

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) continue;

      ItemMeta meta = piece.getItemMeta();
      if (meta.getPersistentDataContainer().has(keys.multiHitCount, PersistentDataType.INTEGER)) {
        Integer count = meta.getPersistentDataContainer().get(keys.multiHitCount, PersistentDataType.INTEGER);
        if (count != null && count == 3) {
          return 3;
        }
      }
    }

    return 1;
  }

  private String getFullBladedancerSetTier(Player player) {
    ItemStack[] armor = player.getInventory().getArmorContents();
    if (armor.length != 4) return null;

    String tierName = null;

    for (ItemStack piece : armor) {
      if (piece == null || !piece.hasItemMeta()) return null;

      ItemMeta meta = piece.getItemMeta();
      Byte isWarrior = meta.getPersistentDataContainer().get(keys.warriorArmor, PersistentDataType.BYTE);
      if (isWarrior == null || isWarrior != 1) return null;

      String piecePath = meta.getPersistentDataContainer().get(keys.armorPath, PersistentDataType.STRING);
      String pieceTier = meta.getPersistentDataContainer().get(keys.tierName, PersistentDataType.STRING);

      if (piecePath == null || !piecePath.equals("BLADEDANCER")) return null;
      if (pieceTier == null) return null;

      if (tierName == null) {
        tierName = pieceTier;
      } else if (!tierName.equals(pieceTier)) {
        return null;
      }
    }

    return tierName;
  }

  private double getBladedancerSetBonus(String tierName) {
    return switch (tierName.toUpperCase()) {
      case "SCOUT" -> 8.0;
      case "DUELIST" -> 12.0;
      case "SKIRMISHER" -> 18.0;
      case "SHADOWBLADE" -> 25.0;
      case "BLADEDANCER" -> 35.0;
      case "TEMPEST" -> 45.0;
      case "PHANTOM" -> 60.0;
      default -> 0.0;
    };
  }

  private void spawnMultiHitParticles(Location location, Color color) {
    Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);

    for (int i = 0; i < 15; i++) {
      double offsetX = (random.nextDouble() - 0.5) * 0.6;
      double offsetY = (random.nextDouble() - 0.5) * 0.6;
      double offsetZ = (random.nextDouble() - 0.5) * 0.6;

      Location particleLoc = location.clone().add(offsetX, offsetY, offsetZ);
      location.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
    }

    location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 1, 0.3, 0.3, 0.3, 0);
  }
}
