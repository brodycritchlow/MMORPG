package com.thornily.skills;

import com.thornily.skills.commands.*;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.listeners.SkillGainListener;
import com.thornily.skills.listeners.SkillsGUIListener;
import com.thornily.skills.listeners.WandListener;
import com.thornily.skills.managers.SkillManager;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class SkillsPlugin extends JavaPlugin {
  private DatabaseManager database;
  private SkillManager skillManager;

  @Override
  public void onEnable() {
    getLogger().info("Initializing SkillsPlugin...");

    saveDefaultConfig();

    database = new DatabaseManager(this);
    database.connect();
    database.createTables();

    skillManager = new SkillManager(this, database);

    registerCommands();
    registerListeners();

    getLogger().info("SkillsPlugin enabled successfully!");
  }

  @Override
  public void onDisable() {
    getLogger().info("Disabling SkillsPlugin...");

    if (database != null) {
      database.disconnect();
    }

    getLogger().info("SkillsPlugin disabled!");
  }

  private void registerCommands() {
    Objects.requireNonNull(getCommand("skills"))
        .setExecutor(new SkillsCommand(this));
    Objects.requireNonNull(getCommand("skillset"))
        .setExecutor(new SkillSetCommand(this));
    Objects.requireNonNull(getCommand("skillreset"))
        .setExecutor(new SkillResetCommand(this));
    Objects.requireNonNull(getCommand("query"))
        .setExecutor(new QueryCommand(this));
    Objects.requireNonNull(getCommand("sword"))
        .setExecutor(new SwordCommand(this));
    Objects.requireNonNull(getCommand("wand"))
        .setExecutor(new WandCommand(this));
  }

  private void registerListeners() {
    getServer().getPluginManager().registerEvents(new SkillGainListener(this),
                                                  this);
    getServer().getPluginManager().registerEvents(new SkillsGUIListener(),
                                                  this);
    getServer().getPluginManager().registerEvents(new WandListener(this), this);
  }

  public SkillManager getSkillManager() { return skillManager; }

  public DatabaseManager getDatabase() { return database; }
}
