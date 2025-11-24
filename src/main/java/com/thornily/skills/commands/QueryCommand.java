package com.thornily.skills.commands;

import com.thornily.skills.SkillsPlugin;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class QueryCommand implements CommandExecutor {
  private final SkillsPlugin plugin;

  public QueryCommand(SkillsPlugin plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(@NotNull CommandSender sender,
                           @NotNull Command command, @NotNull String label,
                           @NotNull String[] args) {
    if (!sender.isOp()) {
      sender.sendMessage("§cYou must be an operator to use this command.");
      return true;
    }

    if (args.length == 0) {
      sender.sendMessage("§cUsage: /query <sql>");
      return true;
    }

    String query = String.join(" ", args);

    if (query.toLowerCase().startsWith("select")) {
      executeSelectQuery(sender, query);
    } else {
      executeUpdateQuery(sender, query);
    }

    return true;
  }

  private void executeSelectQuery(CommandSender sender, String query) {
    ResultSet resultSet = plugin.getDatabase().executeQuery(query);

    if (resultSet == null) {
      sender.sendMessage("§cQuery failed. Check console for errors.");
      return;
    }

    try {
      ResultSetMetaData metaData = resultSet.getMetaData();
      int columnCount = metaData.getColumnCount();

      StringBuilder header = new StringBuilder("§e");
      for (int i = 1; i <= columnCount; i++) {
        header.append(metaData.getColumnName(i));
        if (i < columnCount)
          header.append(" | ");
      }
      sender.sendMessage(header.toString());

      int rowCount = 0;
      while (resultSet.next()) {
        StringBuilder row = new StringBuilder("§f");
        for (int i = 1; i <= columnCount; i++) {
          row.append(resultSet.getString(i));
          if (i < columnCount)
            row.append(" | ");
        }
        sender.sendMessage(row.toString());
        rowCount++;
      }

      sender.sendMessage("§a" + rowCount + " rows returned.");
    } catch (SQLException e) {
      sender.sendMessage("§cError reading results: " + e.getMessage());
      plugin.getLogger().severe("Query error: " + e.getMessage());
    }
  }

  private void executeUpdateQuery(CommandSender sender, String query) {
    int rowsAffected = plugin.getDatabase().executeUpdate(query);

    if (rowsAffected > 0) {
      sender.sendMessage("§aQuery executed successfully. " + rowsAffected +
                         " rows affected.");
    } else {
      sender.sendMessage("§cQuery executed but no rows affected, or query " +
                         "failed. Check console.");
    }
  }
}
