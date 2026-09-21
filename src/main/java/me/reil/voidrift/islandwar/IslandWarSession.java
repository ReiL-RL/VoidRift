package me.reil.voidrift.islandwar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds runtime state of one Island War event session.
 * Tracks which islands participate and the state of their hearts.
 */
public final class IslandWarSession {

    private final String eventId;
    private final long startedAt;
    /** islandId -> heart */
    private final Map<String, IslandHeart> hearts = new LinkedHashMap<String, IslandHeart>();
    /** islandId -> islands destroyed by this island */
    private final Map<String, Integer> kills = new LinkedHashMap<String, Integer>();
    private boolean finished;

    public IslandWarSession(String eventId) {
        this.eventId = eventId;
        this.startedAt = System.currentTimeMillis();
        this.finished = false;
    }

    public String getEventId() { return eventId; }
    public long getStartedAt() { return startedAt; }
    public boolean isFinished() { return finished; }
    public void finish() { this.finished = true; }

    public void registerHeart(IslandHeart heart) {
        hearts.put(heart.getIslandId(), heart);
    }

    public IslandHeart getHeart(String islandId) {
        return hearts.get(islandId);
    }

    public Collection<IslandHeart> getAllHearts() {
        return Collections.unmodifiableCollection(hearts.values());
    }

    /** Number of islands still alive (heart not destroyed). */
    public int getAliveCount() {
        int count = 0;
        for (IslandHeart h : hearts.values()) {
            if (!h.isDestroyed()) count++;
        }
        return count;
    }

    /** Get last surviving island id, or null if more than one remains. */
    public String getLastSurvivor() {
        String last = null;
        int alive = 0;
        for (IslandHeart h : hearts.values()) {
            if (!h.isDestroyed()) {
                alive++;
                last = h.getIslandId();
            }
        }
        return alive == 1 ? last : null;
    }

    public void addKill(String attackerIslandId) {
        Integer cur = kills.get(attackerIslandId);
        kills.put(attackerIslandId, cur == null ? 1 : cur + 1);
    }

    public int getKills(String islandId) {
        Integer k = kills.get(islandId);
        return k == null ? 0 : k;
    }

    public Map<String, Integer> getAllKills() {
        return Collections.unmodifiableMap(kills);
    }
}
