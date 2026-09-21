package me.reil.voidrift.event;

import me.reil.voidrift.objective.Objective;
import me.reil.voidrift.objective.QuestStage;

import java.util.Collections;
import java.util.List;

/**
 * Defines an event loaded from config.
 */
public final class EventDefinition {

    private final String id;
    private final String displayName;
    private final String description;
    private final EventType type;
    private final String zoneId;
    private final long durationSeconds;
    private final long intervalSeconds;
    private final int minPlayers;
    private final int maxPlayers;
    private final List<String> rewardCommands;
    private final double rewardMoney;
    private final long rewardIslandXp;
    private final List<Objective> objectives;
    private final boolean completeOnAll;
    private final String flexAchievement;
    private final int scaleMobsPerPlayer;
    private boolean enabled;
    private boolean previewEnabled;
    private int previewSeconds;
    private boolean announcementsEnabled;
    private List<Integer> warningSeconds;
    private List<QuestStage> questChain;
    private String schedule;       // DAILY, WEEKLY, INTERVAL (default)
    private String scheduleTime;   // "18:00"
    private String scheduleDay;    // MONDAY, TUESDAY, etc.

    public EventDefinition(String id, String displayName, String description, EventType type,
                           String zoneId, long durationSeconds, long intervalSeconds,
                           int minPlayers, int maxPlayers, List<String> rewardCommands,
                           double rewardMoney, long rewardIslandXp,
                           List<Objective> objectives, boolean completeOnAll, String flexAchievement,
                           int scaleMobsPerPlayer) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.zoneId = zoneId;
        this.durationSeconds = durationSeconds;
        this.intervalSeconds = intervalSeconds;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.rewardCommands = rewardCommands;
        this.rewardMoney = rewardMoney;
        this.rewardIslandXp = rewardIslandXp;
        this.objectives = objectives != null ? objectives : Collections.<Objective>emptyList();
        this.completeOnAll = completeOnAll;
        this.flexAchievement = flexAchievement;
        this.scaleMobsPerPlayer = scaleMobsPerPlayer;
        this.enabled = true;
        this.previewEnabled = true;
        this.previewSeconds = -1;
        this.announcementsEnabled = true;
        this.warningSeconds = Collections.emptyList();
        this.questChain = Collections.emptyList();
        this.schedule = "INTERVAL";
        this.scheduleTime = null;
        this.scheduleDay = null;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public EventType getType() { return type; }
    public String getZoneId() { return zoneId; }
    public long getDurationSeconds() { return durationSeconds; }
    public long getIntervalSeconds() { return intervalSeconds; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public List<String> getRewardCommands() { return rewardCommands; }
    public double getRewardMoney() { return rewardMoney; }
    public long getRewardIslandXp() { return rewardIslandXp; }
    public List<Objective> getObjectives() { return objectives; }
    public boolean isCompleteOnAll() { return completeOnAll; }
    public String getFlexAchievement() { return flexAchievement; }
    public int getScaleMobsPerPlayer() { return scaleMobsPerPlayer; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isPreviewEnabled() { return previewEnabled; }
    public void setPreviewEnabled(boolean previewEnabled) { this.previewEnabled = previewEnabled; }
    public int getPreviewSeconds() { return previewSeconds; }
    public void setPreviewSeconds(int previewSeconds) { this.previewSeconds = previewSeconds; }
    public boolean isAnnouncementsEnabled() { return announcementsEnabled; }
    public void setAnnouncementsEnabled(boolean announcementsEnabled) { this.announcementsEnabled = announcementsEnabled; }
    public List<Integer> getWarningSeconds() { return warningSeconds; }
    public void setWarningSeconds(List<Integer> warningSeconds) {
        this.warningSeconds = warningSeconds != null ? warningSeconds : Collections.<Integer>emptyList();
    }
    public List<QuestStage> getQuestChain() { return questChain; }
    public void setQuestChain(List<QuestStage> questChain) { this.questChain = questChain != null ? questChain : Collections.<QuestStage>emptyList(); }
    public boolean hasQuestChain() { return questChain != null && !questChain.isEmpty(); }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }
    public String getScheduleDay() { return scheduleDay; }
    public void setScheduleDay(String scheduleDay) { this.scheduleDay = scheduleDay; }
}
