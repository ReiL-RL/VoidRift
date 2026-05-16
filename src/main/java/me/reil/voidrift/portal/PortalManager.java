package me.reil.voidrift.portal;

import me.reil.voidrift.config.EventsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Portal system with rift particle visuals.
 *
 * Logic:
 * 1. Entry portal (static or dynamic) — walking into it teleports to event location
 * 2. Exit portal — on event location, only way to leave
 * 3. Return point — where players go when event ends or they exit
 * 4. Commands blocked inside event zone (without permission)
 */
public final class PortalManager {

    private final JavaPlugin plugin;
    private final EventsConfig config;
    private final Map<String, List<PortalDefinition>> eventPortals = new LinkedHashMap<String, List<PortalDefinition>>();
    private final Set<String> activeEventIds = new HashSet<String>();
    private final Map<UUID, Location> returnLocations = new LinkedHashMap<UUID, Location>();
    private final Set<UUID> playersInEvent = new HashSet<UUID>();
    private BukkitTask ambientTask;

    private static final long COOLDOWN_MS = 2000L;
    private final Map<UUID, Long> teleportCooldowns = new LinkedHashMap<UUID, Long>();

    public PortalManager(JavaPlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
        loadPortals();
        startAmbientTask();
    }

    // === Lifecycle ===

    public void buildPortal(String eventId) {
        activeEventIds.add(eventId);
        List<PortalDefinition> portals = eventPortals.get(eventId);
        if (portals == null || portals.isEmpty()) {
            plugin.getLogger().warning("No portals configured for event: " + eventId);
            return;
        }

        for (PortalDefinition portal : portals) {
            if (portal.getType() == PortalType.DYNAMIC) {
                portal.rollDynamicLocation();
            }
            Location loc = portal.getActiveLocation();
            if (loc != null && loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.5f);
                plugin.getLogger().info("Portal " + portal.getId() + " activated at " + (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ());
            } else {
                plugin.getLogger().warning("Portal " + portal.getId() + " has no location set!");
            }
        }
    }

    public void destroyPortal(String eventId) {
        activeEventIds.remove(eventId);
        List<PortalDefinition> portals = eventPortals.get(eventId);
        if (portals != null) {
            for (PortalDefinition portal : portals) {
                Location loc = portal.getActiveLocation();
                if (loc != null && loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.5f);
                }
                if (portal.getType() == PortalType.DYNAMIC) {
                    portal.clearActiveLocation();
                }
            }
        }
        returnAllPlayers(eventId);
    }

    // === Teleport logic ===

    /**
     * Check if player walks into any portal and teleport.
     * Called from PlayerMoveEvent.
     */
    public boolean handlePlayerMove(Player player) {
        if (isOnCooldown(player.getUniqueId())) return false;

        for (String eventId : activeEventIds) {
            List<PortalDefinition> portals = eventPortals.get(eventId);
            if (portals == null) continue;

            for (PortalDefinition portal : portals) {
                if (!portal.isInTriggerArea(player.getLocation())) continue;

                switch (portal.getType()) {
                    case ENTRY:
                    case DYNAMIC:
                        // Teleport INTO event + auto-join
                        Location dest = portal.getDestination();
                        if (dest == null || dest.getWorld() == null) continue;
                        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
                        playersInEvent.add(player.getUniqueId());
                        player.teleport(dest);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        // Use lang message
                        try {
                            me.reil.voidrift.VoidRiftPlugin vr = (me.reil.voidrift.VoidRiftPlugin) plugin;
                            player.sendMessage(vr.getLang().msg("portal.enter"));
                            if (vr.getSoundManager() != null) vr.getSoundManager().playSound(player, "portal-enter");
                        } catch (Exception e2) {
                            player.sendMessage("\u00a7d\u2726 \u0422\u044b \u0432\u043e\u0448\u0451\u043b \u0432 \u0441\u043e\u0431\u044b\u0442\u0438\u0435!");
                        }
                        // Auto-join event as participant
                        try {
                            me.reil.voidrift.VoidRiftPlugin vr = (me.reil.voidrift.VoidRiftPlugin) plugin;
                            vr.getEventManager().joinEvent(player, eventId);
                        } catch (Exception ignored) {}
                        setCooldown(player.getUniqueId());
                        return true;

                    case EXIT:
                        // Teleport OUT of event
                        Location returnLoc = returnLocations.remove(player.getUniqueId());
                        if (returnLoc == null) returnLoc = portal.getDestination();
                        if (returnLoc == null) returnLoc = player.getWorld().getSpawnLocation();
                        playersInEvent.remove(player.getUniqueId());
                        player.teleport(returnLoc);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                        // Use lang message
                        try {
                            me.reil.voidrift.VoidRiftPlugin vr = (me.reil.voidrift.VoidRiftPlugin) plugin;
                            player.sendMessage(vr.getLang().msg("portal.exit"));
                            if (vr.getSoundManager() != null) vr.getSoundManager().playSound(player, "portal-exit");
                        } catch (Exception e2) {
                            player.sendMessage("\u00a7e\u2726 \u0422\u044b \u043f\u043e\u043a\u0438\u043d\u0443\u043b \u0441\u043e\u0431\u044b\u0442\u0438\u0435.");
                        }
                        setCooldown(player.getUniqueId());
                        return true;

                    case INTERMEDIATE:
                        // Teleport between zones
                        Location intDest = portal.getDestination();
                        if (intDest == null || intDest.getWorld() == null) continue;
                        player.teleport(intDest);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
                        setCooldown(player.getUniqueId());
                        return true;
                }
            }
        }
        return false;
    }

    // === Player state ===

    public boolean isPlayerInEvent(UUID playerId) {
        return playersInEvent.contains(playerId);
    }

    public void returnAllPlayers(String eventId) {
        for (UUID playerId : new ArrayList<UUID>(playersInEvent)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                playersInEvent.remove(playerId);
                returnLocations.remove(playerId);
                continue;
            }
            Location returnLoc = returnLocations.remove(playerId);
            if (returnLoc == null) returnLoc = player.getWorld().getSpawnLocation();
            playersInEvent.remove(playerId);
            player.teleport(returnLoc);
            player.sendMessage("\u00a7e\u2726 \u0421\u043e\u0431\u044b\u0442\u0438\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u043e. \u0422\u044b \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0451\u043d.");
        }
    }

    // === Admin methods (save to file after each change) ===

    public void setPortalLocation(String eventId, String portalId, Location location) {
        List<PortalDefinition> portals = eventPortals.get(eventId);
        if (portals == null) return;
        for (PortalDefinition portal : portals) {
            if (portal.getId().equals(portalId)) { portal.setLocation(location); break; }
        }
        savePortals();
    }

    public void setPortalDestination(String eventId, String portalId, Location location) {
        List<PortalDefinition> portals = eventPortals.get(eventId);
        if (portals == null) return;
        for (PortalDefinition portal : portals) {
            if (portal.getId().equals(portalId)) { portal.setDestination(location); break; }
        }
        savePortals();
    }

    public void addDynamicLocation(String eventId, String portalId, Location location) {
        List<PortalDefinition> portals = eventPortals.get(eventId);
        if (portals == null) return;
        for (PortalDefinition portal : portals) {
            if (portal.getId().equals(portalId)) { portal.getDynamicLocations().add(location.clone()); break; }
        }
        savePortals();
    }

    /**
     * Create a new portal definition for an event (or update type if exists).
     */
    public void createPortal(String eventId, String portalId, PortalType type) {
        List<PortalDefinition> portals = eventPortals.get(eventId);
        if (portals == null) {
            portals = new ArrayList<PortalDefinition>();
            eventPortals.put(eventId, portals);
        }
        // If exists, update type
        for (PortalDefinition p : portals) {
            if (p.getId().equals(portalId)) {
                p.setType(type);
                savePortals();
                return;
            }
        }
        portals.add(new PortalDefinition(portalId, eventId, type));
        savePortals();
    }

    /**
     * Save all portal data to portals.yml.
     */
    public void savePortals() {
        File file = new File(plugin.getDataFolder(), "portals.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<String, List<PortalDefinition>> entry : eventPortals.entrySet()) {
            String eventId = entry.getKey();
            for (PortalDefinition portal : entry.getValue()) {
                String path = "portals." + eventId + "." + portal.getId();
                cfg.set(path + ".type", portal.getType().name());

                Location loc = portal.getLocation();
                if (loc != null) {
                    cfg.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
                    cfg.set(path + ".location.x", loc.getX());
                    cfg.set(path + ".location.y", loc.getY());
                    cfg.set(path + ".location.z", loc.getZ());
                    cfg.set(path + ".location.yaw", (double) loc.getYaw());
                    cfg.set(path + ".location.pitch", (double) loc.getPitch());
                }

                Location dest = portal.getDestination();
                if (dest != null) {
                    cfg.set(path + ".destination.world", dest.getWorld() != null ? dest.getWorld().getName() : "world");
                    cfg.set(path + ".destination.x", dest.getX());
                    cfg.set(path + ".destination.y", dest.getY());
                    cfg.set(path + ".destination.z", dest.getZ());
                }

                cfg.set(path + ".trigger-radius", portal.getTriggerRadius());
                cfg.set(path + ".particle", portal.getAmbientParticle().name());
                cfg.set(path + ".preview-seconds", portal.getPreviewSeconds());
                cfg.set(path + ".preview-title", portal.getPreviewTitle());
                cfg.set(path + ".preview-subtitle", portal.getPreviewSubtitle());
                cfg.set(path + ".preview-sound", portal.getPreviewSound().name());
                cfg.set(path + ".preview-sound-volume", (double) portal.getPreviewSoundVolume());
                cfg.set(path + ".preview-sound-pitch", (double) portal.getPreviewSoundPitch());

                // Dynamic locations
                if (!portal.getDynamicLocations().isEmpty()) {
                    List<Map<String, Object>> dynList = new ArrayList<Map<String, Object>>();
                    for (Location dl : portal.getDynamicLocations()) {
                        Map<String, Object> m = new LinkedHashMap<String, Object>();
                        m.put("x", dl.getX());
                        m.put("y", dl.getY());
                        m.put("z", dl.getZ());
                        dynList.add(m);
                    }
                    cfg.set(path + ".dynamic-locations", dynList);
                }
            }
        }

        try {
            cfg.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save portals.yml: " + e.getMessage());
        }
    }

    public boolean isEventActive(String eventId) { return activeEventIds.contains(eventId); }

    /**
     * Reload portals from file.
     */
    public void reloadPortals() {
        eventPortals.clear();
        loadPortals();
    }
    public Collection<PortalDefinition> getPortals(String eventId) {
        List<PortalDefinition> p = eventPortals.get(eventId);
        return p != null ? Collections.unmodifiableList(p) : Collections.<PortalDefinition>emptyList();
    }

    public Set<String> getAllEventIds() {
        return Collections.unmodifiableSet(eventPortals.keySet());
    }

    public void shutdown() {
        if (ambientTask != null) ambientTask.cancel();
        // Return everyone
        for (UUID playerId : new ArrayList<UUID>(playersInEvent)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Location ret = returnLocations.remove(playerId);
                if (ret != null) player.teleport(ret);
            }
        }
        playersInEvent.clear();
        activeEventIds.clear();
    }

    // === Ambient particles (rift effect) ===

    private void startAmbientTask() {
        this.ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;
            @Override
            public void run() {
                tick++;
                boolean detailed = tick % 2 == 0;
                for (String eventId : activeEventIds) {
                    List<PortalDefinition> portals = eventPortals.get(eventId);
                    if (portals == null) continue;
                    for (PortalDefinition portal : portals) {
                        Location loc = portal.getActiveLocation();
                        if (loc == null || loc.getWorld() == null) continue;
                        Particle particle = getConfiguredParticle(portal.getType());
                        if (portal.getType() == PortalType.ENTRY || portal.getType() == PortalType.DYNAMIC) {
                            RiftParticles.spawnFull(loc, particle, detailed);
                        } else {
                            RiftParticles.spawnSmall(loc, particle, detailed);
                        }
                    }
                }
            }
        }, 20L, 3L); // Every 0.15 seconds for smooth rift
    }

    /**
     * Get configured particle type for a portal type from config.
     * Falls back to PORTAL if not configured.
     */
    public Particle getConfiguredParticle(PortalType type) {
        String configKey;
        switch (type) {
            case ENTRY: configKey = "portal.particles.entry"; break;
            case EXIT: configKey = "portal.particles.exit"; break;
            case INTERMEDIATE: configKey = "portal.particles.intermediate"; break;
            case DYNAMIC: configKey = "portal.particles.dynamic"; break;
            default: configKey = "portal.particles.entry"; break;
        }
        String particleName = plugin.getConfig().getString(configKey, "PORTAL");
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.PORTAL;
        }
    }

    // === Cooldown ===

    private boolean isOnCooldown(UUID playerId) {
        Long last = teleportCooldowns.get(playerId);
        return last != null && (System.currentTimeMillis() - last) < COOLDOWN_MS;
    }

    private void setCooldown(UUID playerId) {
        teleportCooldowns.put(playerId, System.currentTimeMillis());
    }

    // === Config loading ===

    private void loadPortals() {
        File file = new File(plugin.getDataFolder(), "portals.yml");
        if (!file.exists()) { plugin.saveResource("portals.yml", false); }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("portals");
        if (section == null) return;

        for (String eventId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(eventId);
            if (eventSection == null) continue;

            List<PortalDefinition> portals = new ArrayList<PortalDefinition>();
            for (String portalId : eventSection.getKeys(false)) {
                ConfigurationSection ps = eventSection.getConfigurationSection(portalId);
                if (ps == null) continue;

                PortalType type = parseType(ps.getString("type", "ENTRY"));
                PortalDefinition portal = new PortalDefinition(portalId, eventId, type);

                String worldName = ps.getString("world", "world");
                World world = Bukkit.getWorld(worldName);

                if (ps.contains("location")) {
                    ConfigurationSection ls = ps.getConfigurationSection("location");
                    if (ls != null) portal.setLocation(new Location(world, ls.getDouble("x"), ls.getDouble("y"), ls.getDouble("z"), (float) ls.getDouble("yaw", 0), (float) ls.getDouble("pitch", 0)));
                }
                if (ps.contains("destination")) {
                    ConfigurationSection ds = ps.getConfigurationSection("destination");
                    if (ds != null) {
                        String dw = ds.getString("world", worldName);
                        portal.setDestination(new Location(Bukkit.getWorld(dw), ds.getDouble("x"), ds.getDouble("y"), ds.getDouble("z")));
                    }
                }
                if (ps.contains("dynamic-locations")) {
                    List<Map<?, ?>> dynList = ps.getMapList("dynamic-locations");
                    for (Map<?, ?> m : dynList) {
                        portal.getDynamicLocations().add(new Location(world,
                                Double.parseDouble(String.valueOf(m.get("x"))),
                                Double.parseDouble(String.valueOf(m.get("y"))),
                                Double.parseDouble(String.valueOf(m.get("z")))));
                    }
                }
                portal.setTriggerRadius(ps.getDouble("trigger-radius", 2.0));
                String particleName = ps.getString("particle", "PORTAL");
                try { portal.setAmbientParticle(Particle.valueOf(particleName)); } catch (IllegalArgumentException ignored) {}

                // Preview settings
                portal.setPreviewSeconds(ps.getInt("preview-seconds", 10));
                if (ps.contains("preview-title")) portal.setPreviewTitle(ps.getString("preview-title"));
                if (ps.contains("preview-subtitle")) portal.setPreviewSubtitle(ps.getString("preview-subtitle"));
                String prevSound = ps.getString("preview-sound", "BLOCK_NOTE_BLOCK_PLING");
                try { portal.setPreviewSound(Sound.valueOf(prevSound)); } catch (IllegalArgumentException ignored) {}
                portal.setPreviewSoundVolume((float) ps.getDouble("preview-sound-volume", 1.0));
                portal.setPreviewSoundPitch((float) ps.getDouble("preview-sound-pitch", 1.0));

                portals.add(portal);
            }
            if (!portals.isEmpty()) eventPortals.put(eventId, portals);
        }
        plugin.getLogger().info("Loaded portals for " + eventPortals.size() + " events.");
    }

    private PortalType parseType(String str) {
        try { return PortalType.valueOf(str.toUpperCase()); }
        catch (IllegalArgumentException e) { return PortalType.ENTRY; }
    }
}
