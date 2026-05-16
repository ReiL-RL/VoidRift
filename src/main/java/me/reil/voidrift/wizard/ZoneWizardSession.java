package me.reil.voidrift.wizard;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public final class ZoneWizardSession {

    private final String zoneId;
    private final String worldName;
    private ZoneWizardStep step;
    private ZoneWizardStep previousStep;
    private Location pos1;
    private Location pos2;
    private int maxMobs = 15;
    private final List<Location> spawnPoints;
    private final List<MobEntry> mobEntries;
    private int spawnPointCount;

    /** Temporary state for mob entry via chat */
    private boolean awaitingChatInput;
    private MobEntryState mobEntryState;
    private String pendingMobId;
    private String pendingMobType;

    public ZoneWizardSession(String zoneId, String worldName) {
        this.zoneId = zoneId;
        this.worldName = worldName;
        this.step = ZoneWizardStep.SET_POS1;
        this.previousStep = null;
        this.spawnPoints = new ArrayList<Location>();
        this.mobEntries = new ArrayList<MobEntry>();
        this.spawnPointCount = 0;
        this.awaitingChatInput = false;
        this.mobEntryState = MobEntryState.NONE;
    }

    public String getZoneId() { return zoneId; }
    public String getWorldName() { return worldName; }
    public ZoneWizardStep getStep() { return step; }

    public void setStep(ZoneWizardStep newStep) {
        this.previousStep = this.step;
        this.step = newStep;
    }

    public ZoneWizardStep getPreviousStep() { return previousStep; }

    public boolean goBack() {
        if (previousStep == null) return false;
        this.step = this.previousStep;
        this.previousStep = null;
        return true;
    }

    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public int getMaxMobs() { return maxMobs; }
    public void setMaxMobs(int maxMobs) { this.maxMobs = maxMobs; }
    public List<Location> getSpawnPoints() { return spawnPoints; }
    public int getSpawnPointCount() { return spawnPointCount; }
    public void incrementSpawnPointCount() { this.spawnPointCount++; }
    public List<MobEntry> getMobEntries() { return mobEntries; }

    // Chat input state
    public boolean isAwaitingChatInput() { return awaitingChatInput; }
    public void setAwaitingChatInput(boolean awaiting) { this.awaitingChatInput = awaiting; }
    public MobEntryState getMobEntryState() { return mobEntryState; }
    public void setMobEntryState(MobEntryState state) { this.mobEntryState = state; }
    public String getPendingMobId() { return pendingMobId; }
    public void setPendingMobId(String id) { this.pendingMobId = id; }
    public String getPendingMobType() { return pendingMobType; }
    public void setPendingMobType(String type) { this.pendingMobType = type; }

    public enum MobEntryState {
        NONE,
        AWAITING_TYPE,
        AWAITING_ID,
        AWAITING_MODEL,
        AWAITING_WEIGHT
    }

    public static final class MobEntry {
        private final String mobId;
        private final String mobType;
        private final String modelId;
        private final int weight;

        public MobEntry(String mobId, String mobType, String modelId, int weight) {
            this.mobId = mobId;
            this.mobType = mobType;
            this.modelId = modelId;
            this.weight = weight;
        }

        public String getMobId() { return mobId; }
        public String getMobType() { return mobType; }
        public String getModelId() { return modelId; }
        public int getWeight() { return weight; }
    }
}
