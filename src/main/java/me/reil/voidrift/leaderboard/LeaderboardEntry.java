package me.reil.voidrift.leaderboard;

import java.util.UUID;

/**
 * A single leaderboard entry.
 */
public final class LeaderboardEntry {

    private final String playerName;
    private final UUID uuid;
    private final int score;
    private final long timestamp;

    public LeaderboardEntry(String playerName, UUID uuid, int score, long timestamp) {
        this.playerName = playerName;
        this.uuid = uuid;
        this.score = score;
        this.timestamp = timestamp;
    }

    public String getPlayerName() { return playerName; }
    public UUID getUuid() { return uuid; }
    public int getScore() { return score; }
    public long getTimestamp() { return timestamp; }
}
