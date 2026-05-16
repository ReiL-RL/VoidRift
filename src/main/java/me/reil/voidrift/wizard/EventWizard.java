package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.EventType;
import me.reil.voidrift.zone.ZoneDefinition;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event creation wizard via chat input.
 *
 * Steps:
 * 1. Display name
 * 2. Type (WAVE_SURVIVAL, BOSS_FIGHT, RESOURCE_RACE, CUSTOM)
 * 3. Zone id (from existing zones)
 * 4. Duration seconds
 * 5. Max players
 * 6. Reward money
 * 7. Done — saves to events.yml
 */
public final class EventWizard {

    private final VoidRiftPlugin plugin;
    private final Map<UUID, EventWizardSession> sessions = new LinkedHashMap<UUID, EventWizardSession>();

    public EventWizard(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, String eventId) {
        EventWizardSession session = new EventWizardSession(eventId);
        sessions.put(player.getUniqueId(), session);

        Map<String, String> v = new HashMap<String, String>();
        v.put("id", eventId);

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.title", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.step1"));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.step1-hint"));
        player.sendMessage("");
    }

    /**
     * Handle chat input for event wizard.
     * @return true if consumed
     */
    public boolean handleChat(Player player, String message) {
        EventWizardSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;

        String msg = message.trim();

        switch (session.getStep()) {
            case 1: // Display name
                session.setDisplayName(msg);
                session.nextStep();
                Map<String, String> v1 = new HashMap<String, String>();
                v1.put("value", ChatColor.translateAlternateColorCodes('&', msg));
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.ok-name", v1));
                player.sendMessage("");
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.step2"));
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.step2-options"));
                break;

            case 2: // Type
                EventType type;
                try { type = EventType.valueOf(msg.toUpperCase()); }
                catch (IllegalArgumentException e) {
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.step2-error"));
                    return true;
                }
                session.setType(type);
                session.nextStep();
                Map<String, String> v2 = new HashMap<String, String>();
                v2.put("value", type.name());
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.ok-type", v2));
                player.sendMessage("");
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.step3"));
                // Show available zones
                Collection<ZoneDefinition> zones = plugin.getZoneManager().getZones();
                if (zones.isEmpty()) {
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.step3-no-zones"));
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (ZoneDefinition z : zones) {
                        sb.append(z.getId()).append(", ");
                    }
                    Map<String, String> vz = new HashMap<String, String>();
                    vz.put("zones", sb.toString());
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.step3-available", vz));
                }
                break;

            case 3: // Zone id
                session.setZoneId(msg);
                session.nextStep();
                Map<String, String> v3 = new HashMap<String, String>();
                v3.put("value", msg);
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.ok-zone", v3));
                player.sendMessage("");
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.step4"));
                break;

            case 4: // Duration
                try {
                    int duration = Integer.parseInt(msg);
                    if (duration < 10) { player.sendMessage(plugin.getLang().msg("messages.event-wizard.step4-min")); return true; }
                    session.setDuration(duration);
                    session.nextStep();
                    Map<String, String> v4 = new HashMap<String, String>();
                    v4.put("value", String.valueOf(duration));
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.ok-duration", v4));
                    player.sendMessage("");
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.step5"));
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.number-error"));
                }
                break;

            case 5: // Max players
                try {
                    int maxPlayers = Integer.parseInt(msg);
                    if (maxPlayers < 1) { player.sendMessage(plugin.getLang().msg("messages.event-wizard.step5-min")); return true; }
                    session.setMaxPlayers(maxPlayers);
                    session.nextStep();
                    Map<String, String> v5 = new HashMap<String, String>();
                    v5.put("value", String.valueOf(maxPlayers));
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.ok-players", v5));
                    player.sendMessage("");
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.step6"));
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.step6-hint"));
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.number-error"));
                }
                break;

            case 6: // Reward money
                try {
                    double money = Double.parseDouble(msg);
                    session.setRewardMoney(money);
                    session.nextStep();
                    Map<String, String> v6 = new HashMap<String, String>();
                    v6.put("value", String.valueOf(money));
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.ok-reward", v6));
                    // Finish
                    finishWizard(player, session);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().msg("messages.event-wizard.number-error"));
                }
                break;

            default:
                sessions.remove(player.getUniqueId());
                break;
        }
        return true;
    }

    public boolean isInWizard(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.cancelled"));
    }

    private void finishWizard(Player player, EventWizardSession session) {
        sessions.remove(player.getUniqueId());

        // Save to events.yml
        saveEvent(session);

        // Reload events
        plugin.getEventManager().reloadEvents();

        Map<String, String> v = new HashMap<String, String>();
        v.put("id", session.getEventId());
        v.put("name", ChatColor.translateAlternateColorCodes('&', session.getDisplayName()));
        v.put("type", session.getType().name());
        v.put("zone", session.getZoneId());
        v.put("duration", String.valueOf(session.getDuration()));
        v.put("players", String.valueOf(session.getMaxPlayers()));
        v.put("reward", String.valueOf(session.getRewardMoney()));

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-name", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-type", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-zone", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-duration", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-players", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-reward", v));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-hint", v));
        player.sendMessage("");
    }

    private void saveEvent(EventWizardSession session) {
        File file = new File(plugin.getDataFolder(), "events.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String path = "events." + session.getEventId();
        cfg.set(path + ".display-name", session.getDisplayName());
        cfg.set(path + ".description", "");
        cfg.set(path + ".type", session.getType().name());
        cfg.set(path + ".zone", session.getZoneId());
        cfg.set(path + ".duration-seconds", session.getDuration());
        cfg.set(path + ".interval-seconds", 1800);
        cfg.set(path + ".min-players", 1);
        cfg.set(path + ".max-players", session.getMaxPlayers());
        cfg.set(path + ".scale-mobs-per-player", 2);
        cfg.set(path + ".rewards.money", session.getRewardMoney());
        cfg.set(path + ".rewards.island-xp", 0);
        cfg.set(path + ".rewards.commands", new java.util.ArrayList<String>());

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save events.yml: " + e.getMessage());
        }
    }

    // ===== Session inner class =====

    private static final class EventWizardSession {
        private final String eventId;
        private int step = 1;
        private String displayName;
        private EventType type;
        private String zoneId;
        private int duration;
        private int maxPlayers;
        private double rewardMoney;

        EventWizardSession(String eventId) {
            this.eventId = eventId;
        }

        String getEventId() { return eventId; }
        int getStep() { return step; }
        void nextStep() { step++; }

        String getDisplayName() { return displayName; }
        void setDisplayName(String displayName) { this.displayName = displayName; }
        EventType getType() { return type; }
        void setType(EventType type) { this.type = type; }
        String getZoneId() { return zoneId; }
        void setZoneId(String zoneId) { this.zoneId = zoneId; }
        int getDuration() { return duration; }
        void setDuration(int duration) { this.duration = duration; }
        int getMaxPlayers() { return maxPlayers; }
        void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
        double getRewardMoney() { return rewardMoney; }
        void setRewardMoney(double rewardMoney) { this.rewardMoney = rewardMoney; }
    }
}
