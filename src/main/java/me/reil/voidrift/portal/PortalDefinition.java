package me.reil.voidrift.portal;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Defines a particle-based portal.
 * Supports static location or dynamic (random from list).
 */
public final class PortalDefinition {

    private static final Random RANDOM = new Random();

    private final String id;
    private final String eventId;
    private PortalType type;
    private Location location;           // Static location (or null for DYNAMIC)
    private Location activeLocation;     // Currently active location (for DYNAMIC portals)
    private Location destination;
    private final List<Location> dynamicLocations; // Pool of possible locations for DYNAMIC type
    private double triggerRadius;
    private Particle ambientParticle;
    private Sound activateSound;
    private float activateSoundVolume;
    private float activateSoundPitch;
    private Sound previewSound;
    private float previewSoundVolume;
    private float previewSoundPitch;
    private String previewTitle;
    private String previewSubtitle;
    private int previewSeconds;

    public PortalDefinition(String id, String eventId, PortalType type) {
        this.id = id;
        this.eventId = eventId;
        this.type = type;
        this.dynamicLocations = new ArrayList<Location>();
        this.triggerRadius = 2.0;
        this.ambientParticle = Particle.PORTAL;
        this.activateSound = Sound.ENTITY_ENDERMAN_TELEPORT;
        this.activateSoundVolume = 1.0f;
        this.activateSoundPitch = 1.0f;
        this.previewSound = Sound.BLOCK_NOTE_BLOCK_PLING;
        this.previewSoundVolume = 1.0f;
        this.previewSoundPitch = 1.0f;
        this.previewTitle = "&d\u2726 \u0421\u043e\u0431\u044b\u0442\u0438\u0435 \u043d\u0430\u0447\u0438\u043d\u0430\u0435\u0442\u0441\u044f";
        this.previewSubtitle = "&e\u0427\u0435\u0440\u0435\u0437 {seconds} \u0441\u0435\u043a...";
        this.previewSeconds = 10;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public PortalType getType() { return type; }
    public void setType(PortalType type) { this.type = type; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public Location getDestination() { return destination; }
    public void setDestination(Location destination) { this.destination = destination; }
    public double getTriggerRadius() { return triggerRadius; }
    public void setTriggerRadius(double triggerRadius) { this.triggerRadius = triggerRadius; }
    public Particle getAmbientParticle() { return ambientParticle; }
    public void setAmbientParticle(Particle ambientParticle) { this.ambientParticle = ambientParticle; }
    public Sound getActivateSound() { return activateSound; }
    public float getActivateSoundVolume() { return activateSoundVolume; }
    public float getActivateSoundPitch() { return activateSoundPitch; }
    public Sound getPreviewSound() { return previewSound; }
    public float getPreviewSoundVolume() { return previewSoundVolume; }
    public float getPreviewSoundPitch() { return previewSoundPitch; }
    public String getPreviewTitle() { return previewTitle; }
    public void setPreviewTitle(String previewTitle) { this.previewTitle = previewTitle; }
    public String getPreviewSubtitle() { return previewSubtitle; }
    public void setPreviewSubtitle(String previewSubtitle) { this.previewSubtitle = previewSubtitle; }
    public int getPreviewSeconds() { return previewSeconds; }
    public void setPreviewSeconds(int previewSeconds) { this.previewSeconds = previewSeconds; }
    public void setPreviewSound(Sound sound) { this.previewSound = sound; }
    public void setPreviewSoundVolume(float vol) { this.previewSoundVolume = vol; }
    public void setPreviewSoundPitch(float pitch) { this.previewSoundPitch = pitch; }
    public List<Location> getDynamicLocations() { return dynamicLocations; }

    /**
     * Get the currently active location.
     * For DYNAMIC portals, this is the randomly chosen location.
     * For others, this is the static location.
     */
    public Location getActiveLocation() {
        if (type == PortalType.DYNAMIC) {
            return activeLocation;
        }
        return location;
    }

    /**
     * Pick a random location from the dynamic pool and set it as active.
     */
    public void rollDynamicLocation() {
        if (dynamicLocations.isEmpty()) {
            activeLocation = location; // fallback
            return;
        }
        activeLocation = dynamicLocations.get(RANDOM.nextInt(dynamicLocations.size()));
    }

    /**
     * Clear the active dynamic location (portal disappears).
     */
    public void clearActiveLocation() {
        activeLocation = null;
    }

    /**
     * Check if a location is within the trigger radius of the active portal.
     */
    public boolean isInTriggerArea(Location loc) {
        Location active = getActiveLocation();
        if (active == null || loc == null) return false;
        if (active.getWorld() == null || loc.getWorld() == null) return false;
        if (!active.getWorld().equals(loc.getWorld())) return false;
        return active.distanceSquared(loc) <= (triggerRadius * triggerRadius);
    }
}
