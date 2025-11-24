package com.thornily.skills.commands;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.models.Skill;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkillResetCommand implements CommandExecutor {
  private final SkillsPlugin plugin;

  public SkillResetCommand(SkillsPlugin plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label,
                           String[] args) {
    return false;
  }
}
