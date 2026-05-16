package me.reil.voidrift.objective;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-player progress on objectives within an active event.
 */
public final class PlayerProgress {

    private final UUID playerId;
    // objectiveIndex -> current progress
    private final Map<Integer, Integer> progress = new LinkedHashMap<Integer, Integer>();
    // Track which objectives have already played their completion sound
    private final Set<Integer> notifiedObjectives = new HashSet<Integer>();

    public PlayerProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }

    public int getProgress(int objectiveIndex) {
        Integer val = progress.get(objectiveIndex);
        return val == null ? 0 : val;
    }

    public int addProgress(int objectiveIndex, int amount) {
        int current = getProgress(objectiveIndex);
        int newVal = current + amount;
        progress.put(objectiveIndex, newVal);
        return newVal;
    }

    public void setProgress(int objectiveIndex, int value) {
        progress.put(objectiveIndex, value);
    }

    public boolean isObjectiveNotified(int objectiveIndex) {
        return notifiedObjectives.contains(objectiveIndex);
    }

    public void setObjectiveNotified(int objectiveIndex) {
        notifiedObjectives.add(objectiveIndex);
    }
}
