package com.thornily.skills.models;

import java.util.Map;
import java.util.UUID;

public class PlayerSkills {
  private final UUID playerId;
  private final Map<Skill, SkillData> skills;

  public PlayerSkills(UUID playerId) {
    this.playerId = playerId;
    this.skills = null;
  }

  public UUID getPlayerId() { return playerId; }

  public SkillData getSkillData(Skill skill) { return null; }

  public int getLevel(Skill skill) { return 0; }

  public double getExperience(Skill skill) { return 0.0; }

  public void addExperience(Skill skill, double amount) {}

  public void setLevel(Skill skill, int level) {}

  public void setExperience(Skill skill, double experience) {}

  public void reset(Skill skill) {}

  public void resetAll() {}

  public Map<Skill, SkillData> getAllSkills() { return null; }

  public int getTotalLevel() { return 0; }
}
