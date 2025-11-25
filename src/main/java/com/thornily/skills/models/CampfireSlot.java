package com.thornily.skills.models;

import org.bukkit.Material;

import java.util.UUID;

public class CampfireSlot {
    private final UUID playerUUID;
    private final Material rawMaterial;
    private final long placedTime;

    public CampfireSlot(UUID playerUUID, Material rawMaterial) {
        this.playerUUID = playerUUID;
        this.rawMaterial = rawMaterial;
        this.placedTime = System.currentTimeMillis();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Material getRawMaterial() {
        return rawMaterial;
    }

    public long getPlacedTime() {
        return placedTime;
    }

    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - placedTime > timeoutMillis;
    }
}
