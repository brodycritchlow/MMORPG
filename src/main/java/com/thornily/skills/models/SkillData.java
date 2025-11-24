package com.thornily.skills.models;

import com.thornily.skills.database.DatabaseManager;

public class SkillData {
  private final Skill skill;
  private int level;
  private double experience;
  DatabaseManager dbmanager;

  public SkillData(Skill skill) { this.skill = skill; }

  public Skill getSkill() { return skill; }

  public int getLevel() { return level; }

  public void setLevel(int level) {}

  public double getExperience() { return experience; }

  public void setExperience(double experience) {}

  public void addExperience(double amount) {}

  public double getRequiredXP() { return 0.0; }

  public double getProgress() { return 0.0; }

  public int getProgressBar(int length) { return 0; }
}
