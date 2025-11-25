package com.thornily.skills.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class SkillsGUIListener implements Listener {

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    String title = event.getView().getTitle();

    if (title.contains("Your Skills") || title.contains("Combat Stats")) {
      event.setCancelled(true);
    }
  }
}
