package me.reil.voidrift.event;

import me.reil.voidrift.modifier.EventModifier;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a currently running event instance.
 */
public final class ActiveEvent {

    private final EventDefinition definition;
    private final long startedAt;
    private final long endsAt;
    private final Set<UUID> participants = new LinkedHashSet<UUID>();
    private final Map<UUID, Integer> scores = new LinkedHashMap<UUID, Integer>();
    private List<EventModifier> modifiers = new ArrayList<EventModifier>();
    private int currentWave;
    private int currentQuestStage;
    private boolean finished;

    public ActiveEvent(EventDefinition definition) {
        this.definition = definition;
        this.startedAt = System.currentTimeMillis();
        this.endsAt = startedAt + (definition.getDurationSeconds() * 1000L);
        this.currentWave = 0;
        this.currentQuestStage = 0;
        this.finished = false;
    }

    public EventDefinition getDefinition() { return definition; }
    public long getStartedAt() { return startedAt; }
    public long getEndsAt() { return endsAt; }
    public Set<UUID> getParticipants() { return Collections.unmodifiableSet(participants); }
    public int getCurrentWave() { return currentWave; }
    public boolean isFinished() { return finished; }

    public long getRemainingSeconds() {
        return Math.max(0L, (endsAt - System.currentTimeMillis()) / 1000L);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endsAt;
    }

    public void addParticipant(UUID playerId) {
        participants.add(playerId);
    }

    public void removeParticipant(UUID playerId) {
        participants.remove(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public void addScore(UUID playerId, int amount) {
        Integer current = scores.get(playerId);
        scores.put(playerId, (current == null ? 0 : current) + amount);
    }

    public int getScore(UUID playerId) {
        Integer score = scores.get(playerId);
        return score == null ? 0 : score;
    }

    public Map<UUID, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    /**
     * Get top players sorted by score descending.
     */
    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<Map.Entry<UUID, Integer>>(scores.entrySet());
        Collections.sort(sorted, new java.util.Comparator<Map.Entry<UUID, Integer>>() {
            @Override
            public int compare(Map.Entry<UUID, Integer> a, Map.Entry<UUID, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        if (sorted.size() > limit) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }

    public void nextWave() {
        currentWave++;
    }

    public void finish() {
        this.finished = true;
    }

    public List<EventModifier> getModifiers() { return modifiers; }
    public void setModifiers(List<EventModifier> modifiers) { this.modifiers = modifiers != null ? modifiers : new ArrayList<EventModifier>(); }

    public int getCurrentQuestStage() { return currentQuestStage; }
    public void setCurrentQuestStage(int stage) { this.currentQuestStage = stage; }
    public void advanceQuestStage() { this.currentQuestStage++; }
}

