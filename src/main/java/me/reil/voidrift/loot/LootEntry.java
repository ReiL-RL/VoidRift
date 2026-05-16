package me.reil.voidrift.loot;

import org.bukkit.Material;

/**
 * A single loot entry with material, amount, chance, and optional display name.
 */
public final class LootEntry {

    private final Material material;
    private final int amount;
    private final double chance;
    private final String displayName;

    public LootEntry(Material material, int amount, double chance, String displayName) {
        this.material = material;
        this.amount = amount;
        this.chance = chance;
        this.displayName = displayName;
    }

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public double getChance() { return chance; }
    public String getDisplayName() { return displayName; }
}
