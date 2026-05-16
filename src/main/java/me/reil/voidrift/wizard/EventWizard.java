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

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Мастер создания события: " + ChatColor.YELLOW + eventId);
        player.sendMessage(ChatColor.GRAY + "Шаг 1/6: Введи отображаемое имя события:");
        player.sendMessage(ChatColor.DARK_GRAY + "(Поддерживает цветовые коды &)");
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
                player.sendMessage(ChatColor.GREEN + "✔ Имя: " + ChatColor.translateAlternateColorCodes('&', msg));
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "Шаг 2/6: Введи тип события:");
                player.sendMessage(ChatColor.GRAY + "  WAVE_SURVIVAL, BOSS_FIGHT, RESOURCE_RACE, CUSTOM");
                break;

            case 2: // Type
                EventType type;
                try { type = EventType.valueOf(msg.toUpperCase()); }
                catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Неизвестный тип! Доступные: WAVE_SURVIVAL, BOSS_FIGHT, RESOURCE_RACE, CUSTOM");
                    return true;
                }
                session.setType(type);
                session.nextStep();
                player.sendMessage(ChatColor.GREEN + "✔ Тип: " + type.name());
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "Шаг 3/6: Введи ID зоны:");
                // Show available zones
                Collection<ZoneDefinition> zones = plugin.getZoneManager().getZones();
                if (zones.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "  (нет зон — создай через /riftadmin setupzone)");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (ZoneDefinition z : zones) {
                        sb.append(z.getId()).append(", ");
                    }
                    player.sendMessage(ChatColor.GRAY + "  Доступные: " + sb.toString());
                }
                break;

            case 3: // Zone id
                session.setZoneId(msg);
                session.nextStep();
                player.sendMessage(ChatColor.GREEN + "✔ Зона: " + msg);
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "Шаг 4/6: Введи длительность в секундах (напр. 300):");
                break;

            case 4: // Duration
                try {
                    int duration = Integer.parseInt(msg);
                    if (duration < 10) { player.sendMessage(ChatColor.RED + "Минимум 10 секунд!"); return true; }
                    session.setDuration(duration);
                    session.nextStep();
                    player.sendMessage(ChatColor.GREEN + "✔ Длительность: " + duration + "с");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "Шаг 5/6: Введи макс. игроков (напр. 10):");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Введи число!");
                }
                break;

            case 5: // Max players
                try {
                    int maxPlayers = Integer.parseInt(msg);
                    if (maxPlayers < 1) { player.sendMessage(ChatColor.RED + "Минимум 1!"); return true; }
                    session.setMaxPlayers(maxPlayers);
                    session.nextStep();
                    player.sendMessage(ChatColor.GREEN + "✔ Макс. игроков: " + maxPlayers);
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "Шаг 6/6: Введи награду (деньги, напр. 5000):");
                    player.sendMessage(ChatColor.GRAY + "  (0 = без денежной награды)");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Введи число!");
                }
                break;

            case 6: // Reward money
                try {
                    double money = Double.parseDouble(msg);
                    session.setRewardMoney(money);
                    session.nextStep();
                    player.sendMessage(ChatColor.GREEN + "✔ Награда: " + money);
                    // Finish
                    finishWizard(player, session);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Введи число!");
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
        player.sendMessage(ChatColor.RED + "Создание события отменено.");
    }

    private void finishWizard(Player player, EventWizardSession session) {
        sessions.remove(player.getUniqueId());

        // Save to events.yml
        saveEvent(session);

        // Reload events
        plugin.getEventManager().reloadEvents();

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✦ Событие '" + session.getEventId() + "' создано!");
        player.sendMessage(ChatColor.GRAY + "Имя: " + ChatColor.translateAlternateColorCodes('&', session.getDisplayName()));
        player.sendMessage(ChatColor.GRAY + "Тип: " + session.getType().name());
        player.sendMessage(ChatColor.GRAY + "Зона: " + session.getZoneId());
        player.sendMessage(ChatColor.GRAY + "Длительность: " + session.getDuration() + "с");
        player.sendMessage(ChatColor.GRAY + "Макс. игроков: " + session.getMaxPlayers());
        player.sendMessage(ChatColor.GRAY + "Награда: " + session.getRewardMoney());
        player.sendMessage(ChatColor.GRAY + "Настрой порталы: /riftadmin setup " + session.getEventId());
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
