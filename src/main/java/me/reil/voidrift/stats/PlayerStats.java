package me.reil.voidrift.stats;

/**
 * Player statistics record for a specific event (or aggregated).
 */
public final class PlayerStats {

    private final String uuid;
    private final String eventId;
    private final int kills;
    private final int deaths;
    private final int score;
    private final int completions;
    private final int timePlayedSeconds;
    private final long lastPlayed;

    public PlayerStats(String uuid, String eventId, int kills, int deaths, int score,
                       int completions, int timePlayedSeconds, long lastPlayed) {
        this.uuid = uuid;
        this.eventId = eventId;
        this.kills = kills;
        this.deaths = deaths;
        this.score = score;
        this.completions = completions;
        this.timePlayedSeconds = timePlayedSeconds;
        this.lastPlayed = lastPlayed;
    }

    public String getUuid() { return uuid; }
    public String getEventId() { return eventId; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getScore() { return score; }
    public int getCompletions() { return completions; }
    public int getTimePlayedSeconds() { return timePlayedSeconds; }
    public long getLastPlayed() { return lastPlayed; }
}
