package me.reil.voidrift.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional Citizens NPC integration.
 * Creates NPCs that show event info.
 *
 * Basic stub — logs availability. Citizens API is complex and
 * full implementation requires Citizens2 dependency.
 */
public final class CitizensHook {

    private final JavaPlugin plugin;
    private boolean available;

    public CitizensHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("Citizens");
        if (available) {
            plugin.getLogger().info("Citizens detected - NPC support available.");
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * Create an NPC at a location that shows event info.
     * Stub implementation — logs the action.
     *
     * @param eventId the event this NPC is associated with
     * @param location where to place the NPC
     * @param name display name for the NPC
     */
    public void createEventNPC(String eventId, Location location, String name) {
        if (!available) {
            plugin.getLogger().warning("Citizens not available — cannot create NPC for event " + eventId);
            return;
        }

        // Stub: log the creation request
        // Full implementation would use Citizens API:
        //   NPCRegistry registry = CitizensAPI.getNPCRegistry();
        //   NPC npc = registry.createNPC(EntityType.PLAYER, name);
        //   npc.spawn(location);
        //   npc.data().set("voidrift-event", eventId);
        plugin.getLogger().info("Citizens NPC stub: would create NPC '" + name + "' for event '" + eventId
                + "' at " + (int) location.getX() + "," + (int) location.getY() + "," + (int) location.getZ());
    }

    /**
     * Remove an event NPC.
     * Stub implementation.
     *
     * @param eventId the event whose NPC to remove
     */
    public void removeEventNPC(String eventId) {
        if (!available) return;
        plugin.getLogger().info("Citizens NPC stub: would remove NPC for event '" + eventId + "'");
    }
}
