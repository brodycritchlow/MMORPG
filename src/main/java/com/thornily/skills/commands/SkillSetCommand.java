package com.thornily.skills.commands;

import com.thornily.skills.SkillsPlugin;
import com.thornily.skills.models.Skill;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkillSetCommand implements CommandExecutor {
  private final SkillsPlugin plugin;

  public SkillSetCommand(SkillsPlugin plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label,
                           String[] args) {
    return false;
  }
}
