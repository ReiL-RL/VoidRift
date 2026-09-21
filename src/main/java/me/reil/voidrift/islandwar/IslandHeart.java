package me.reil.voidrift.islandwar;

import org.bukkit.Location;

/**
 * Represents an island's heart block during an Island War event.
 */
public final class IslandHeart {

    private final String islandId;
    private final Location location;
    private final int maxHp;
    private int hp;
    private boolean destroyed;
    private long lastHitAt;

    public IslandHeart(String islandId, Location location, int maxHp) {
        this.islandId = islandId;
        this.location = location;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.destroyed = false;
        this.lastHitAt = 0L;
    }

    public String getIslandId() { return islandId; }
    public Location getLocation() { return location; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isDestroyed() { return destroyed; }
    public long getLastHitAt() { return lastHitAt; }

    /** Damage the heart, returns true if destroyed by this hit. */
    public boolean damage(int amount) {
        if (destroyed) return false;
        this.hp = Math.max(0, this.hp - amount);
        this.lastHitAt = System.currentTimeMillis();
        if (this.hp <= 0) {
            this.destroyed = true;
            return true;
        }
        return false;
    }

    public double getHpPercent() {
        return maxHp == 0 ? 0.0 : (double) hp / (double) maxHp;
    }
}
