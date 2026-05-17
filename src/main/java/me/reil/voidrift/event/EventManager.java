package me.reil.voidrift.event;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.config.EventsConfig;
import me.reil.voidrift.loot.LootEntry;
import me.reil.voidrift.loot.LootTable;
import me.reil.voidrift.modifier.EventModifier;
import me.reil.voidrift.objective.Objective;
import me.reil.voidrift.objective.ObjectiveType;
import me.reil.voidrift.objective.QuestStage;
import me.reil.voidrift.portal.PortalManager;
import me.reil.voidrift.reward.RewardManager;
import me.reil.voidrift.zone.WaveSpawner;
import me.reil.voidrift.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core event manager. Loads events from config, starts/stops them, manages lifecycle.
 */
public final class EventManager {

    private final VoidRiftPlugin plugin;
    private final EventsConfig config;
    private final ZoneManager zoneManager;
    private final WaveSpawner waveSpawner;
    private final PortalManager portalManager;
    private final RewardManager rewardManager;
    private final Map<String, EventDefinition> definitions = new LinkedHashMap<String, EventDefinition>();
    private final Map<String, ActiveEvent> activeEvents = new LinkedHashMap<String, ActiveEvent>();
    private final Map<String, Long> nextStartTimes = new LinkedHashMap<String, Long>();
    private final Map<String, LootTable> lootTables = new LinkedHashMap<String, LootTable>();
    // Track last scheduled start date to avoid double-starting
    private final Map<String, String> lastScheduledStart = new LinkedHashMap<String, String>();

    public EventManager(VoidRiftPlugin plugin, EventsConfig config, ZoneManager zoneManager,
                        WaveSpawner waveSpawner, PortalManager portalManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.config = config;
        this.zoneManager = zoneManager;
        this.waveSpawner = waveSpawner;
        this.portalManager = portalManager;
        this.rewardManager = rewardManager;
        loadEvents();
    }

    public void tick() {
        long now = System.currentTimeMillis();

        // Check for expired events — delayed stop (10 sec grace period with announcement)
        List<String> toStop = new ArrayList<String>();
        for (Map.Entry<String, ActiveEvent> entry : activeEvents.entrySet()) {
            ActiveEvent event = entry.getValue();
            if (event.isExpired() && !event.isFinished()) {
                // Mark as finishing, announce, stop after 10 seconds
                long overTime = now - event.getEndsAt();
                if (overTime < 1000L) {
                    // First tick after expiry — announce ending with title
                    event.finish();
                    Map<String, String> vars = new HashMap<String, String>();
                    vars.put("event", event.getDefinition().getDisplayName());
                    vars.put("id", event.getDefinition().getId());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(
                                plugin.getLang().color("&c✦ " + event.getDefinition().getDisplayName()),
                                plugin.getLang().msg("messages.event.ending-subtitle"),
                                10, 40, 10);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                    Bukkit.broadcastMessage(plugin.getLang().msg("messages.event.ending", vars));
                    final String eid = entry.getKey();
                    Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                        @Override
                        public void run() {
                            finalizeStop(eid);
                        }
                    }, 200L);
                }
            }
        }

        // Check scheduled events (DAILY/WEEKLY)
        checkScheduledEvents(now);

        // Auto-start events with 10 second preview
        if (config.isAutoStartEnabled()) {
            for (EventDefinition def : definitions.values()) {
                // Skip scheduled events (they use their own scheduling)
                if (!"INTERVAL".equalsIgnoreCase(def.getSchedule())) continue;
                if (activeEvents.containsKey(def.getId())) continue;
                Long nextStart = nextStartTimes.get(def.getId());
                if (nextStart == null) {
                    nextStartTimes.put(def.getId(), now + (def.getIntervalSeconds() * 1000L));
                    continue;
                }
                // Preview 10 seconds before start
                long timeUntilStart = nextStart - now;
                if (timeUntilStart <= 10000L && timeUntilStart > 9000L && activeEvents.size() < config.getMaxActiveEvents()) {
                    // Announce preview
                    Map<String, String> soonVars = new HashMap<String, String>();
                    soonVars.put("event", def.getDisplayName());
                    soonVars.put("id", def.getId());
                    Bukkit.broadcastMessage(plugin.getLang().msg("messages.event.starting-soon", soonVars));
                }
                if (now >= nextStart && activeEvents.size() < config.getMaxActiveEvents()) {
                    previewAndStart(def.getId());
                }
            }
        }

        // Tick wave spawner for active events
        for (ActiveEvent event : activeEvents.values()) {
            if (!event.isFinished()) {
                waveSpawner.tick(event);
                plugin.getObjectiveTracker().tick(event);
            }
        }
    }

    /**
     * Actually stop the event (called 10 seconds after expiry announcement).
     */
    private void finalizeStop(String eventId) {
        ActiveEvent event = activeEvents.remove(eventId);
        if (event == null) return;

        // Update leaderboard
        plugin.getLeaderboard().onEventEnd(event);

        // Record stats
        long timePlayed = (int) ((System.currentTimeMillis() - event.getStartedAt()) / 1000L);
        for (UUID playerId : event.getParticipants()) {
            if (plugin.getStatsManager() != null) {
                plugin.getStatsManager().addCompletion(playerId, eventId, event.getScore(playerId), (int) timePlayed);
            }
        }

        // Give rewards
        for (UUID playerId : event.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                rewardManager.giveRewards(player, event);
            }
        }

        // Sound effect
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().playSoundAll("event-end");
        }

        // Destroy portal (particles disappear, players returned)
        portalManager.destroyPortal(eventId);

        // Despawn mobs
        waveSpawner.cleanup(event);

        // Schedule next
        EventDefinition def = definitions.get(eventId);
        if (def != null && "INTERVAL".equalsIgnoreCase(def.getSchedule())) {
            nextStartTimes.put(eventId, System.currentTimeMillis() + (def.getIntervalSeconds() * 1000L));
        }

        Map<String, String> endVars = new HashMap<String, String>();
        endVars.put("event", event.getDefinition().getDisplayName());
        endVars.put("id", event.getDefinition().getId());
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.event.ended-portals", endVars));
    }

    public boolean startEvent(String eventId) {
        EventDefinition def = definitions.get(eventId);
        if (def == null || activeEvents.containsKey(eventId)) return false;

        ActiveEvent event = new ActiveEvent(def);

        // Roll modifiers
        if (plugin.getModifierManager() != null) {
            List<EventModifier> mods = plugin.getModifierManager().rollModifiers();
            event.setModifiers(mods);
        }

        activeEvents.put(eventId, event);

        // Init objective tracking
        plugin.getObjectiveTracker().onEventStart(eventId);

        // Spawn random loot chests
        plugin.getLootChestManager().spawnRandomChests(event);

        // Build portal (particles appear)
        portalManager.buildPortal(eventId);

        // Sound effect
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().playSoundAll("event-start");
        }

        // Broadcast start
        Map<String, String> startVars = new HashMap<String, String>();
        startVars.put("event", def.getDisplayName());
        startVars.put("id", eventId);
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.event.started", startVars));

        // Title to all players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    plugin.getLang().color("&d✦ " + def.getDisplayName()),
                    plugin.getLang().msg("messages.event.portal-open-subtitle"),
                    10, 40, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 1.5f);
        }

        plugin.getLogger().info("Event started: " + eventId);
        return true;
    }

    /**
     * Start event with preview countdown (title + subtitle + sound every second).
     * Like the old SkyBound plugin.
     */
    public boolean previewAndStart(final String eventId) {
        final EventDefinition def = definitions.get(eventId);
        if (def == null || activeEvents.containsKey(eventId)) return false;

        // Get preview settings from first ENTRY portal
        int previewSeconds = 10;
        String previewTitle = "&d✦ Событие начинается";
        String previewSubtitle = "&eЧерез {seconds} сек...";
        Sound previewSound = Sound.BLOCK_NOTE_BLOCK_PLING;
        float previewVolume = 1.0f;
        float previewPitch = 1.0f;

        for (me.reil.voidrift.portal.PortalDefinition portal : portalManager.getPortals(eventId)) {
            if (portal.getType() == me.reil.voidrift.portal.PortalType.ENTRY || portal.getType() == me.reil.voidrift.portal.PortalType.DYNAMIC) {
                previewSeconds = portal.getPreviewSeconds();
                previewTitle = portal.getPreviewTitle();
                previewSubtitle = portal.getPreviewSubtitle();
                previewSound = portal.getPreviewSound();
                previewVolume = portal.getPreviewSoundVolume();
                previewPitch = portal.getPreviewSoundPitch();
                break;
            }
        }

        if (previewSeconds <= 0) {
            return startEvent(eventId);
        }

        // Countdown with title/subtitle/sound each second
        final String fTitle = previewTitle;
        final String fSubtitle = previewSubtitle;
        final Sound fSound = previewSound;
        final float fVol = previewVolume;
        final float fPitch = previewPitch;

        for (int i = previewSeconds; i >= 1; i--) {
            final int countdown = i;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    String title = ChatColor.translateAlternateColorCodes('&', fTitle);
                    String subtitle = ChatColor.translateAlternateColorCodes('&', fSubtitle.replace("{seconds}", String.valueOf(countdown)));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(title, subtitle, 0, 25, 0);
                        p.playSound(p.getLocation(), fSound, fVol, fPitch);
                    }
                }
            }, (long) (previewSeconds - i) * 20L);
        }

        // Start after countdown
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                startEvent(eventId);
            }
        }, (long) previewSeconds * 20L);

        return true;
    }

    public boolean stopEvent(String eventId) {
        ActiveEvent event = activeEvents.remove(eventId);
        if (event == null) return false;

        event.finish();

        // Update leaderboard
        plugin.getLeaderboard().onEventEnd(event);

        // Give rewards to participants
        for (UUID playerId : event.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                rewardManager.giveRewards(player, event);
            }
        }

        // Destroy portal (particles disappear, players returned)
        portalManager.destroyPortal(eventId);

        // Despawn mobs
        waveSpawner.cleanup(event);

        // Schedule next
        EventDefinition def = definitions.get(eventId);
        if (def != null) {
            nextStartTimes.put(eventId, System.currentTimeMillis() + (def.getIntervalSeconds() * 1000L));
        }

        Map<String, String> stopVars = new HashMap<String, String>();
        stopVars.put("event", event.getDefinition().getDisplayName());
        stopVars.put("id", eventId);
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.event.ended", stopVars));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    plugin.getLang().color("&6✦ " + event.getDefinition().getDisplayName()),
                    plugin.getLang().msg("messages.event.completed-subtitle"),
                    10, 40, 10);
        }
        return true;
    }

    /**
     * Stop event with 10 second countdown (title + broadcast).
     */
    public void previewAndStop(final String eventId) {
        final ActiveEvent event = activeEvents.get(eventId);
        if (event == null) return;

        // Announce
        Map<String, String> psVars = new HashMap<String, String>();
        psVars.put("event", event.getDefinition().getDisplayName());
        psVars.put("id", eventId);
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.event.ending", psVars));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    plugin.getLang().color("&c✦ " + event.getDefinition().getDisplayName()),
                    plugin.getLang().msg("messages.event.ending-subtitle"),
                    10, 40, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        // Stop after 10 seconds
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                stopEvent(eventId);
            }
        }, 200L);
    }

    public boolean joinEvent(Player player, String eventId) {
        ActiveEvent event = activeEvents.get(eventId);
        if (event == null || event.isFinished()) return false;
        if (event.getParticipants().size() >= event.getDefinition().getMaxPlayers()) return false;
        event.addParticipant(player.getUniqueId());
        plugin.getObjectiveTracker().onPlayerJoin(eventId, player.getUniqueId());
        // Apply modifiers to player
        if (plugin.getModifierManager() != null) {
            plugin.getModifierManager().applyToPlayer(player, event);
        }
        // Fire FlexAchievements event
        fireFlexEvent(player, "VOIDRIFT_EVENT_JOIN", eventId);
        return true;
    }

    public boolean leaveEvent(Player player, String eventId) {
        ActiveEvent event = activeEvents.get(eventId);
        if (event == null) return false;
        event.removeParticipant(player.getUniqueId());
        return true;
    }

    public ActiveEvent getActiveEvent(String eventId) {
        return activeEvents.get(eventId);
    }

    public Collection<ActiveEvent> getActiveEvents() {
        return Collections.unmodifiableCollection(activeEvents.values());
    }

    public Collection<EventDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public EventDefinition getDefinition(String eventId) {
        return definitions.get(eventId);
    }

    public LootTable getLootTable(String eventId) {
        return lootTables.get(eventId);
    }

    /**
     * Reload event definitions from file (hot reload).
     */
    public void reloadEvents() {
        definitions.clear();
        lootTables.clear();
        loadEvents();
    }

    public void shutdown() {
        for (String id : new ArrayList<String>(activeEvents.keySet())) {
            stopEvent(id);
        }
    }

    public void onMobKill(UUID playerId, String eventId) {
        ActiveEvent event = activeEvents.get(eventId);
        if (event != null && event.isParticipant(playerId)) {
            event.addScore(playerId, 1);
            // Update objective tracker
            plugin.getObjectiveTracker().onScoreUpdate(eventId, playerId, event.getScore(playerId));
            // Record kill stat
            if (plugin.getStatsManager() != null) {
                plugin.getStatsManager().addKill(playerId, eventId);
            }
        }
    }

    /**
     * Called when a mob is killed — with details for objective tracking.
     */
    public void onMobKillDetailed(UUID playerId, String eventId, String mobType, String bossFile) {
        ActiveEvent event = activeEvents.get(eventId);
        if (event != null && event.isParticipant(playerId)) {
            event.addScore(playerId, 1);
            plugin.getObjectiveTracker().onMobKill(eventId, playerId, mobType, bossFile);
            // Record kill stat
            if (plugin.getStatsManager() != null) {
                plugin.getStatsManager().addKill(playerId, eventId);
            }
            // Fire FlexAchievements boss kill event
            if (bossFile != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    fireFlexEvent(player, "VOIDRIFT_BOSS_KILL", eventId);
                }
            }
        }
    }

    /**
     * Fire a FlexAchievements event via reflection (if FlexAchievements is present).
     */
    private void fireFlexEvent(Player player, String eventType, String eventId) {
        try {
            Class<?> pluginClass = Class.forName("ru.flexachievements.FlexAchievementsPlugin");
            Object instance = pluginClass.getMethod("getInstance").invoke(null);
            if (instance == null) return;
            java.lang.reflect.Method processMethod = instance.getClass().getMethod("process", Player.class, String.class, java.util.Map.class);
            java.util.Map<String, Object> context = new java.util.LinkedHashMap<String, Object>();
            context.put("event_id", eventId);
            processMethod.invoke(instance, player, eventType, context);
        } catch (Exception ignored) {}
    }

    private void loadEvents() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) {
            plugin.saveResource("events.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("events");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection es = section.getConfigurationSection(id);
            if (es == null) continue;

            String displayName = es.getString("display-name", id);
            String description = es.getString("description", "");
            EventType type = parseType(es.getString("type", "WAVE_SURVIVAL"));
            String zoneId = es.getString("zone", "");
            long duration = es.getLong("duration-seconds", 300);
            long interval = es.getLong("interval-seconds", 1800);
            int minPlayers = es.getInt("min-players", 1);
            int maxPlayers = es.getInt("max-players", 10);
            List<String> rewardCmds = es.getStringList("rewards.commands");
            double rewardMoney = es.getDouble("rewards.money", 0.0);
            long rewardXp = es.getLong("rewards.island-xp", 0L);

            // Objectives
            List<me.reil.voidrift.objective.Objective> objectives = new ArrayList<me.reil.voidrift.objective.Objective>();
            List<Map<?, ?>> objList = es.getMapList("objectives");
            for (Map<?, ?> objMap : objList) {
                objectives.add(parseObjective(objMap));
            }
            String completeOn = es.getString("complete-on", "ANY");
            boolean completeOnAll = "ALL".equalsIgnoreCase(completeOn);
            String flexAchievement = es.getString("rewards.flex-achievement", null);
            int scaleMobsPerPlayer = es.getInt("scale-mobs-per-player", 2);

            EventDefinition def = new EventDefinition(id, displayName, description, type, zoneId,
                    duration, interval, minPlayers, maxPlayers, rewardCmds, rewardMoney, rewardXp,
                    objectives, completeOnAll, flexAchievement, scaleMobsPerPlayer);

            // Schedule
            String schedule = es.getString("schedule", "INTERVAL");
            def.setSchedule(schedule);
            def.setScheduleTime(es.getString("schedule-time", null));
            def.setScheduleDay(es.getString("schedule-day", null));

            // Quest chain
            List<Map<?, ?>> questChainList = es.getMapList("quest-chain");
            if (!questChainList.isEmpty()) {
                List<QuestStage> stages = new ArrayList<QuestStage>();
                for (Map<?, ?> stageMap : questChainList) {
                    String stageMessage = stageMap.containsKey("message") ? String.valueOf(stageMap.get("message")) : "";
                    List<Objective> stageObjectives = new ArrayList<Objective>();
                    Object objsRaw = stageMap.get("objectives");
                    if (objsRaw instanceof List) {
                        List<?> objsList = (List<?>) objsRaw;
                        for (Object o : objsList) {
                            if (o instanceof Map) {
                                stageObjectives.add(parseObjective((Map<?, ?>) o));
                            }
                        }
                    }
                    stages.add(new QuestStage(stageObjectives, stageMessage));
                }
                def.setQuestChain(stages);
            }

            definitions.put(id, def);

            // Load loot table
            List<Map<?, ?>> lootList = es.getMapList("loot-table");
            if (lootList.isEmpty()) {
                lootList = es.getMapList("loot");
            }
            if (!lootList.isEmpty()) {
                List<LootEntry> lootEntries = new ArrayList<LootEntry>();
                for (Map<?, ?> lootMap : lootList) {
                    String matStr = "DIAMOND";
                    if (lootMap.containsKey("item")) {
                        matStr = String.valueOf(lootMap.get("item"));
                    } else if (lootMap.containsKey("material")) {
                        matStr = String.valueOf(lootMap.get("material"));
                    }

                    // Check for sop: prefix (SopItemsCreator integration)
                    if (matStr.startsWith("sop:")) {
                        // Will be handled at drop time; store as-is with BARRIER placeholder
                        int lootAmount = lootMap.containsKey("amount") ? Integer.parseInt(String.valueOf(lootMap.get("amount"))) : 1;
                        double chance = lootMap.containsKey("chance") ? Double.parseDouble(String.valueOf(lootMap.get("chance"))) : 0.5;
                        String lootDisplayName = lootMap.containsKey("display-name") ? String.valueOf(lootMap.get("display-name")) : matStr;
                        lootEntries.add(new LootEntry(Material.BARRIER, lootAmount, chance, matStr));
                    } else {
                        Material mat;
                        try { mat = Material.valueOf(matStr.toUpperCase()); }
                        catch (IllegalArgumentException e) { mat = Material.DIAMOND; }
                        int lootAmount = lootMap.containsKey("amount") ? Integer.parseInt(String.valueOf(lootMap.get("amount"))) : 1;
                        double chance = lootMap.containsKey("chance") ? Double.parseDouble(String.valueOf(lootMap.get("chance"))) : 0.5;
                        String lootDisplayName = lootMap.containsKey("display-name") ? String.valueOf(lootMap.get("display-name")) : null;
                        lootEntries.add(new LootEntry(mat, lootAmount, chance, lootDisplayName));
                    }
                }
                lootTables.put(id, new LootTable(lootEntries));
            }
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " event definitions.");
    }

    private Objective parseObjective(Map<?, ?> objMap) {
        String typeStr = objMap.containsKey("type") ? String.valueOf(objMap.get("type")) : "KILL_MOBS";
        ObjectiveType objType;
        try { objType = ObjectiveType.valueOf(typeStr.toUpperCase()); }
        catch (IllegalArgumentException e) { objType = ObjectiveType.KILL_MOBS; }
        int objAmount = objMap.containsKey("amount") ? Integer.parseInt(String.valueOf(objMap.get("amount"))) : 1;
        String objTarget = objMap.containsKey("target") ? String.valueOf(objMap.get("target")) : null;
        if (objTarget == null && objMap.containsKey("boss")) objTarget = String.valueOf(objMap.get("boss"));
        if (objTarget == null && objMap.containsKey("item")) objTarget = String.valueOf(objMap.get("item"));
        if (objTarget == null && objMap.containsKey("mob")) objTarget = String.valueOf(objMap.get("mob"));
        return new Objective(objType, objAmount, objTarget);
    }

    /**
     * Check for DAILY/WEEKLY scheduled events and start them at the configured time.
     */
    private void checkScheduledEvents(long now) {
        Calendar cal = Calendar.getInstance();
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        int currentMinute = cal.get(Calendar.MINUTE);
        int currentSecond = cal.get(Calendar.SECOND);
        int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sunday, 2=Monday, ...
        String todayKey = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);

        for (EventDefinition def : definitions.values()) {
            String schedule = def.getSchedule();
            if ("INTERVAL".equalsIgnoreCase(schedule)) continue;
            if (activeEvents.containsKey(def.getId())) continue;
            if (activeEvents.size() >= config.getMaxActiveEvents()) continue;

            String scheduleTime = def.getScheduleTime();
            if (scheduleTime == null || scheduleTime.isEmpty()) continue;

            // Parse time "HH:mm"
            String[] parts = scheduleTime.split(":");
            if (parts.length != 2) continue;
            int targetHour, targetMinute;
            try {
                targetHour = Integer.parseInt(parts[0]);
                targetMinute = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) { continue; }

            // Check if it's time to start (within 1 second window)
            if (currentHour != targetHour || currentMinute != targetMinute || currentSecond > 1) continue;

            if ("DAILY".equalsIgnoreCase(schedule)) {
                String key = def.getId() + "_" + todayKey;
                if (lastScheduledStart.containsKey(key)) continue;
                lastScheduledStart.put(key, todayKey);
                previewAndStart(def.getId());
            } else if ("WEEKLY".equalsIgnoreCase(schedule)) {
                String scheduleDay = def.getScheduleDay();
                if (scheduleDay == null) continue;
                int targetDay = parseDayOfWeek(scheduleDay);
                if (targetDay != currentDayOfWeek) continue;
                String key = def.getId() + "_" + todayKey;
                if (lastScheduledStart.containsKey(key)) continue;
                lastScheduledStart.put(key, todayKey);
                previewAndStart(def.getId());
            }
        }
    }

    private int parseDayOfWeek(String day) {
        switch (day.toUpperCase()) {
            case "SUNDAY": return Calendar.SUNDAY;
            case "MONDAY": return Calendar.MONDAY;
            case "TUESDAY": return Calendar.TUESDAY;
            case "WEDNESDAY": return Calendar.WEDNESDAY;
            case "THURSDAY": return Calendar.THURSDAY;
            case "FRIDAY": return Calendar.FRIDAY;
            case "SATURDAY": return Calendar.SATURDAY;
            default: return -1;
        }
    }

    private EventType parseType(String str) {
        try { return EventType.valueOf(str.toUpperCase()); }
        catch (IllegalArgumentException e) { return EventType.CUSTOM; }
    }
}

