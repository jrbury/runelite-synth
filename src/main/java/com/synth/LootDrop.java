package com.synth;

import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

import java.util.Collection;

/**
 * Event published to the Synth API when a loot drop is recorded
 */
public class LootDrop extends LootReceived
{
    private String username;
    private Integer killCount;

    public LootDrop(String username, String bossName, int combatLevel, int killCount, LootRecordType type, Collection<ItemStack> items){
        super(bossName, combatLevel, type, items);
        this.username = username;
        this.killCount = killCount;
    }
}


