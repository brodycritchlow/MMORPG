package com.thornily.skills.managers;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.models.PlayerSkills;
import com.thornily.skills.models.Skill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public class SkillManager {
  private final SkillsPlugin plugin;
  private final DatabaseManager database;
  private final Map<UUID, PlayerSkills> playerSkillsMap;

  public SkillManager(SkillsPlugin plugin, DatabaseManager database) {
    this.plugin = plugin;
    this.database = database;
    this.playerSkillsMap = new HashMap<>();
  }

  public PlayerSkills getPlayerSkills(UUID playerId) { return null; }

  public PlayerSkills getPlayerSkills(Player player) { return null; }

  public void loadPlayerData(UUID playerId) {}

  public void savePlayerData(UUID playerId) {}

  public void saveAllPlayerData() {}

  public void addExperience(Player player, Skill skill, double amount) {}

  public void resetPlayerSkills(UUID playerId) {}

  public void resetPlayerSkill(UUID playerId, Skill skill) {}
}
