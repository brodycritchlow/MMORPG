package com.thornily.skills;

import com.thornily.skills.commands.*;
import com.thornily.skills.database.DatabaseManager;
import com.thornily.skills.listeners.*;
import com.thornily.skills.managers.SkillManager;
import com.thornily.skills.utils.NBTKeys;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class SkillsPlugin extends JavaPlugin {
  private DatabaseManager database;
  private SkillManager skillManager;
  private SkillGainListener skillGainListener;
  private NBTKeys nbtKeys;

  @Override
  public void onEnable() {
    getLogger().info("Initializing SkillsPlugin...");

    saveDefaultConfig();

    nbtKeys = new NBTKeys(this);
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
    Objects.requireNonNull(getCommand("stats"))
        .setExecutor(new StatsCommand(this));

    AdminUtilsCommand adminUtils = new AdminUtilsCommand(this);
    Objects.requireNonNull(getCommand("admin-utils"))
        .setExecutor(adminUtils);
    Objects.requireNonNull(getCommand("admin-utils"))
        .setTabCompleter(adminUtils);
  }

    private void registerListeners() {
        skillGainListener = new SkillGainListener(this);
        getServer().getPluginManager().registerEvents(skillGainListener, this);
        getServer().getPluginManager().registerEvents(new SkillsGUIListener(), this);
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new ArmorEquipListener(this), this);
        getServer().getPluginManager().registerEvents(new WarriorArmorEquipListener(this), this);
        getServer().getPluginManager().registerEvents(new CookingListener(this), this);

        // Start Volatile armor particle effects
        new VolatileArmorListener(this);
    }

  public SkillManager getSkillManager() { return skillManager; }

  public DatabaseManager getDatabase() { return database; }

  public SkillGainListener getSkillGainListener() { return skillGainListener; }

  public NBTKeys getNBTKeys() { return nbtKeys; }
}
