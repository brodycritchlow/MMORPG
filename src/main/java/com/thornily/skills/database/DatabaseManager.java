package com.thornily.skills.database;

import com.thornily.skills.SkillsPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private final SkillsPlugin plugin;
    private Connection connection;

    public DatabaseManager(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), "skills.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            this.connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connected to database successfully");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to database.", e);
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from database");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not disconnect from database.", e);
        }
    }

    public void createTables() {
        try {
            String createPlayerSkillsTable = "CREATE TABLE IF NOT EXISTS player_skills ("
                    + "uuid TEXT NOT NULL, "
                    + "skill_name TEXT NOT NULL, "
                    + "level INTEGER DEFAULT 1, "
                    + "experience REAL DEFAULT 0.0, "
                    + "PRIMARY KEY (uuid, skill_name)"
                    + ");";

            PreparedStatement statement = connection.prepareStatement(createPlayerSkillsTable);
            statement.execute();
            statement.close();

            plugin.getLogger().info("Database tables created successfully");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    public ResultSet executeQuery(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            return statement.executeQuery();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute query on database", e);
        }
        return null;
    }

    public int executeUpdate(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            int rowsAffected = statement.executeUpdate();
            statement.close();
            return rowsAffected;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute update on database", e);
        }
        return 0;
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check database connection", e);
        }
        return false;
    }
}
