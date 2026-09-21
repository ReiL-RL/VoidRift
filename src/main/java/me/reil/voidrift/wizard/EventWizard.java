package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.EventType;
import me.reil.voidrift.objective.ObjectiveType;
import me.reil.voidrift.zone.ZoneDefinition;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simple chat wizard for creating event definitions without editing events.yml.
 */
public final class EventWizard {

    private static final int STEP_NAME = 1;
    private static final int STEP_DESCRIPTION = 2;
    private static final int STEP_TYPE = 3;
    private static final int STEP_ZONE = 4;
    private static final int STEP_DURATION = 5;
    private static final int STEP_INTERVAL = 6;
    private static final int STEP_MIN_PLAYERS = 7;
    private static final int STEP_MAX_PLAYERS = 8;
    private static final int STEP_REWARD_MONEY = 9;
    private static final int STEP_REWARD_ISLAND_XP = 10;
    private static final int STEP_SCALE_MOBS = 11;
    private static final int STEP_CONFIRM = 12;

    private final VoidRiftPlugin plugin;
    private final Map<UUID, EventWizardSession> sessions = new LinkedHashMap<UUID, EventWizardSession>();

    public EventWizard(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, String eventId) {
        if (eventId == null || !eventId.matches("[a-zA-Z0-9_-]+")) {
            player.sendMessage(msg("messages.event-wizard.invalid-id"));
            return;
        }

        EventWizardSession session = new EventWizardSession(eventId.toLowerCase());
        sessions.put(player.getUniqueId(), session);

        Map<String, String> vars = new HashMap<String, String>();
        vars.put("id", session.eventId);

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.title", vars));
        player.sendMessage(msg("messages.event-wizard.commands"));
        prompt(player, session);
    }

    public boolean handleChat(Player player, String message) {
        EventWizardSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;

        String msg = message.trim();
        if (msg.isEmpty()) {
            prompt(player, session);
            return true;
        }

        if (isCancel(msg)) {
            cancel(player);
            return true;
        }
        if (isBack(msg)) {
            if (session.step > STEP_NAME) {
                session.step--;
                player.sendMessage(msg("messages.event-wizard.back"));
            } else {
                player.sendMessage(msg("messages.event-wizard.back-first"));
            }
            prompt(player, session);
            return true;
        }
        if (isList(msg)) {
            showList(player, session);
            return true;
        }

        switch (session.step) {
            case STEP_NAME:
                session.displayName = msg;
                ok(player, "Имя", ChatColor.translateAlternateColorCodes('&', msg));
                next(player, session);
                break;
            case STEP_DESCRIPTION:
                if (isDefault(msg) || isSkip(msg) || "-".equals(msg)) {
                    session.description = "";
                    ok(player, "Описание", "пусто");
                } else {
                    session.description = msg;
                    ok(player, "Описание", msg);
                }
                next(player, session);
                break;
            case STEP_TYPE:
                EventType type = parseType(msg);
                if (type == null) {
                    player.sendMessage(msg("messages.event-wizard.type-error"));
                    showTypes(player);
                    return true;
                }
                session.type = type;
                applyDefaults(session);
                ok(player, "Тип", friendlyType(type));
                next(player, session);
                break;
            case STEP_ZONE:
                if (isDefault(msg) || isSkip(msg) || "none".equalsIgnoreCase(msg) || "-".equals(msg)) {
                    if (session.type == EventType.ISLAND_WAR) {
                        session.zoneId = "";
                        ok(player, "Зона", "не нужна для войны островов");
                    } else {
                        ZoneDefinition first = firstZone();
                        if (first == null) {
                            player.sendMessage(msg("messages.event-wizard.no-zones-create"));
                            return true;
                        }
                        session.zoneId = first.getId();
                        ok(player, "Зона", session.zoneId);
                    }
                    next(player, session);
                    return true;
                }

                ZoneDefinition zone = parseZone(msg);
                if (zone == null) {
                    player.sendMessage(msg("messages.event-wizard.zone-not-found"));
                    showZones(player, session);
                    return true;
                }
                session.zoneId = zone.getId();
                ok(player, "Зона", session.zoneId);
                next(player, session);
                break;
            case STEP_DURATION:
                Integer duration = parseInt(player, msg, session.durationSeconds, 10, "длительность");
                if (duration == null) return true;
                session.durationSeconds = duration.intValue();
                ok(player, "Длительность", session.durationSeconds + "с");
                next(player, session);
                break;
            case STEP_INTERVAL:
                Integer interval = parseInt(player, msg, session.intervalSeconds, 30, "интервал");
                if (interval == null) return true;
                session.intervalSeconds = interval.intValue();
                ok(player, "Интервал", session.intervalSeconds + "с");
                next(player, session);
                break;
            case STEP_MIN_PLAYERS:
                Integer minPlayers = parseInt(player, msg, session.minPlayers, 1, "минимум игроков");
                if (minPlayers == null) return true;
                session.minPlayers = minPlayers.intValue();
                ok(player, "Мин. игроков", String.valueOf(session.minPlayers));
                next(player, session);
                break;
            case STEP_MAX_PLAYERS:
                Integer maxPlayers = parseInt(player, msg, session.maxPlayers, Math.max(1, session.minPlayers), "максимум игроков");
                if (maxPlayers == null) return true;
                if (maxPlayers.intValue() < session.minPlayers) {
                    player.sendMessage(msg("messages.event-wizard.max-less-min"));
                    return true;
                }
                session.maxPlayers = maxPlayers.intValue();
                ok(player, "Макс. игроков", String.valueOf(session.maxPlayers));
                next(player, session);
                break;
            case STEP_REWARD_MONEY:
                Double money = parseDouble(player, msg, session.rewardMoney, 0.0, "денежную награду");
                if (money == null) return true;
                session.rewardMoney = money.doubleValue();
                ok(player, "Деньги", String.valueOf(session.rewardMoney));
                next(player, session);
                break;
            case STEP_REWARD_ISLAND_XP:
                Integer xp = parseInt(player, msg, session.rewardIslandXp, 0, "XP острова");
                if (xp == null) return true;
                session.rewardIslandXp = xp.intValue();
                ok(player, "XP острова", String.valueOf(session.rewardIslandXp));
                next(player, session);
                break;
            case STEP_SCALE_MOBS:
                Integer scale = parseInt(player, msg, session.scaleMobsPerPlayer, 0, "скейл мобов");
                if (scale == null) return true;
                session.scaleMobsPerPlayer = scale.intValue();
                ok(player, "Мобов за игрока", String.valueOf(session.scaleMobsPerPlayer));
                next(player, session);
                break;
            case STEP_CONFIRM:
                if (isYes(msg)) {
                    finishWizard(player, session);
                } else if (isNo(msg)) {
                    player.sendMessage(msg("messages.event-wizard.confirm-reset"));
                    session.step = STEP_NAME;
                    prompt(player, session);
                } else {
                    player.sendMessage(msg("messages.event-wizard.confirm-error"));
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

    private void next(Player player, EventWizardSession session) {
        session.step++;
        prompt(player, session);
    }

    private void prompt(Player player, EventWizardSession session) {
        player.sendMessage("");
        switch (session.step) {
            case STEP_NAME:
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.step1"));
                player.sendMessage(plugin.getLang().msg("messages.event-wizard.step1-hint"));
                break;
            case STEP_DESCRIPTION:
                player.sendMessage(msg("messages.event-wizard.step2"));
                player.sendMessage(msg("messages.event-wizard.step2-hint"));
                break;
            case STEP_TYPE:
                player.sendMessage(msg("messages.event-wizard.step3"));
                showTypes(player);
                break;
            case STEP_ZONE:
                player.sendMessage(msg("messages.event-wizard.step4"));
                if (session.type == EventType.ISLAND_WAR) {
                    player.sendMessage(msg("messages.event-wizard.step4-island-war"));
                }
                showZones(player, session);
                break;
            case STEP_DURATION:
                player.sendMessage(msg("messages.event-wizard.step5"));
                player.sendMessage(msg("messages.event-wizard.default-hint", vars("value", String.valueOf(session.durationSeconds))));
                break;
            case STEP_INTERVAL:
                player.sendMessage(msg("messages.event-wizard.step6"));
                player.sendMessage(msg("messages.event-wizard.interval-hint", vars("value", String.valueOf(session.intervalSeconds))));
                break;
            case STEP_MIN_PLAYERS:
                player.sendMessage(msg("messages.event-wizard.step7"));
                player.sendMessage(msg("messages.event-wizard.default-hint", vars("value", String.valueOf(session.minPlayers))));
                break;
            case STEP_MAX_PLAYERS:
                player.sendMessage(msg("messages.event-wizard.step8"));
                player.sendMessage(msg("messages.event-wizard.default-hint", vars("value", String.valueOf(session.maxPlayers))));
                break;
            case STEP_REWARD_MONEY:
                player.sendMessage(msg("messages.event-wizard.step9"));
                player.sendMessage(msg("messages.event-wizard.money-hint", vars("value", String.valueOf(session.rewardMoney))));
                break;
            case STEP_REWARD_ISLAND_XP:
                player.sendMessage(msg("messages.event-wizard.step10"));
                player.sendMessage(msg("messages.event-wizard.xp-hint", vars("value", String.valueOf(session.rewardIslandXp))));
                break;
            case STEP_SCALE_MOBS:
                player.sendMessage(msg("messages.event-wizard.step11"));
                player.sendMessage(msg("messages.event-wizard.scale-hint", vars("value", String.valueOf(session.scaleMobsPerPlayer))));
                break;
            case STEP_CONFIRM:
                showSummary(player, session);
                player.sendMessage(msg("messages.event-wizard.step12"));
                break;
            default:
                break;
        }
    }

    private void showList(Player player, EventWizardSession session) {
        if (session.step == STEP_TYPE) {
            showTypes(player);
        } else if (session.step == STEP_ZONE) {
            showZones(player, session);
        } else {
            prompt(player, session);
        }
    }

    private void showTypes(Player player) {
        player.sendMessage(msg("messages.event-wizard.type-1"));
        player.sendMessage(msg("messages.event-wizard.type-2"));
        player.sendMessage(msg("messages.event-wizard.type-3"));
        player.sendMessage(msg("messages.event-wizard.type-4"));
        player.sendMessage(msg("messages.event-wizard.type-5"));
        player.sendMessage(msg("messages.event-wizard.type-6"));
        player.sendMessage(msg("messages.event-wizard.type-7"));
    }

    private void showZones(Player player, EventWizardSession session) {
        Collection<ZoneDefinition> zones = plugin.getZoneManager().getZones();
        if (zones.isEmpty()) {
            player.sendMessage(plugin.getLang().msg("messages.event-wizard.step3-no-zones"));
            return;
        }

        int index = 1;
        for (ZoneDefinition zone : zones) {
            player.sendMessage(color("&7" + index + ". &e" + zone.getId() + " &8(" + zone.getWorld() + ")"));
            index++;
        }
        player.sendMessage(msg("messages.event-wizard.zones-hint"));
        if (session.type == EventType.ISLAND_WAR) {
            player.sendMessage(msg("messages.event-wizard.zones-skip-hint"));
        }
    }

    private void showSummary(Player player, EventWizardSession session) {
        Map<String, String> vars = summaryVars(session);
        player.sendMessage(msg("messages.event-wizard.summary-title"));
        player.sendMessage(msg("messages.event-wizard.summary-id", vars));
        player.sendMessage(msg("messages.event-wizard.summary-name", vars));
        player.sendMessage(msg("messages.event-wizard.summary-description", vars));
        player.sendMessage(msg("messages.event-wizard.summary-type", vars));
        player.sendMessage(msg("messages.event-wizard.summary-zone", vars));
        player.sendMessage(msg("messages.event-wizard.summary-duration", vars));
        player.sendMessage(msg("messages.event-wizard.summary-interval", vars));
        player.sendMessage(msg("messages.event-wizard.summary-players", vars));
        player.sendMessage(msg("messages.event-wizard.summary-rewards", vars));
        player.sendMessage(msg("messages.event-wizard.summary-scale", vars));
        player.sendMessage(msg("messages.event-wizard.summary-objectives"));
    }

    private void finishWizard(Player player, EventWizardSession session) {
        List<String> issues = validateSession(session);
        if (!issues.isEmpty()) {
            player.sendMessage(msg("messages.event-wizard.cannot-save"));
            for (String issue : issues) {
                player.sendMessage(color("&7- &c" + issue));
            }
            prompt(player, session);
            return;
        }

        sessions.remove(player.getUniqueId());
        saveEvent(session);
        plugin.getEventManager().reloadEvents();

        Map<String, String> vars = new HashMap<String, String>();
        vars.put("id", session.eventId);
        vars.put("name", ChatColor.translateAlternateColorCodes('&', session.displayName));
        vars.put("type", session.type.name());
        vars.put("zone", session.zoneId == null || session.zoneId.isEmpty() ? "-" : session.zoneId);
        vars.put("duration", String.valueOf(session.durationSeconds));
        vars.put("players", String.valueOf(session.maxPlayers));
        vars.put("reward", String.valueOf(session.rewardMoney));

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-name", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-type", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-zone", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-duration", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-players", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-reward", vars));
        player.sendMessage(plugin.getLang().msg("messages.event-wizard.finished-hint", vars));
        player.sendMessage(msg("messages.event-wizard.finished-check", vars));
        player.sendMessage("");
    }

    private List<String> validateSession(EventWizardSession session) {
        List<String> issues = new ArrayList<String>();
        if (session.displayName == null || session.displayName.trim().isEmpty()) issues.add("не указано имя");
        if (session.type == null) issues.add("не указан тип");
        if (session.durationSeconds < 10) issues.add("duration-seconds меньше 10");
        if (session.intervalSeconds < 30) issues.add("interval-seconds меньше 30");
        if (session.minPlayers < 1) issues.add("min-players меньше 1");
        if (session.maxPlayers < session.minPlayers) issues.add("max-players меньше min-players");

        boolean zoneRequired = session.type != EventType.ISLAND_WAR;
        if (zoneRequired && (session.zoneId == null || plugin.getZoneManager().getZone(session.zoneId) == null)) {
            issues.add("зона не найдена: " + session.zoneId);
        }
        return issues;
    }

    private void saveEvent(EventWizardSession session) {
        File file = new File(plugin.getDataFolder(), "events.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String path = "events." + session.eventId;
        cfg.set(path + ".display-name", session.displayName);
        cfg.set(path + ".description", session.description);
        cfg.set(path + ".enabled", true);
        cfg.set(path + ".type", session.type.name());
        cfg.set(path + ".zone", session.zoneId == null ? "" : session.zoneId);
        cfg.set(path + ".duration-seconds", session.durationSeconds);
        cfg.set(path + ".interval-seconds", session.intervalSeconds);
        cfg.set(path + ".min-players", session.minPlayers);
        cfg.set(path + ".max-players", session.maxPlayers);
        cfg.set(path + ".scale-mobs-per-player", session.scaleMobsPerPlayer);
        cfg.set(path + ".schedule", "INTERVAL");
        cfg.set(path + ".announcements.enabled", true);
        cfg.set(path + ".announcements.warning-seconds", java.util.Arrays.asList(Integer.valueOf(300), Integer.valueOf(60), Integer.valueOf(10)));
        cfg.set(path + ".preview.enabled", true);
        cfg.set(path + ".preview.seconds", 10);
        cfg.set(path + ".complete-on", defaultCompleteOn(session.type));
        cfg.set(path + ".objectives", defaultObjectives(session));
        cfg.set(path + ".rewards.money", session.rewardMoney);
        cfg.set(path + ".rewards.island-xp", session.rewardIslandXp);
        cfg.set(path + ".rewards.commands", new ArrayList<String>());
        cfg.set(path + ".rewards.flex-achievement", null);

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save events.yml: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> defaultObjectives(EventWizardSession session) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        switch (session.type) {
            case WAVE_SURVIVAL:
                list.add(objective(ObjectiveType.CLEAR_WAVES.name(), 5, ""));
                list.add(objective(ObjectiveType.SURVIVE_TIME.name(), Math.min(session.durationSeconds, 300), ""));
                break;
            case BOSS_FIGHT:
                list.add(objective(ObjectiveType.KILL_BOSS.name(), 1, ""));
                break;
            case RESOURCE_RACE:
                list.add(objective(ObjectiveType.SCORE_POINTS.name(), 100, ""));
                break;
            case PVP_ARENA:
                list.add(objective(ObjectiveType.SCORE_POINTS.name(), 10, ""));
                break;
            case TIMED_CHALLENGE:
                list.add(objective(ObjectiveType.SURVIVE_TIME.name(), session.durationSeconds, ""));
                break;
            case ISLAND_WAR:
                break;
            case CUSTOM:
            default:
                list.add(objective(ObjectiveType.CUSTOM.name(), 1, session.eventId));
                break;
        }

        return list;
    }

    private Map<String, Object> objective(String type, int amount, String target) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("type", type);
        map.put("amount", amount);
        map.put("target", target);
        return map;
    }

    private String defaultCompleteOn(EventType type) {
        if (type == EventType.WAVE_SURVIVAL || type == EventType.ISLAND_WAR) return "ANY";
        return "ALL";
    }

    private void applyDefaults(EventWizardSession session) {
        switch (session.type) {
            case WAVE_SURVIVAL:
                session.durationSeconds = 300;
                session.intervalSeconds = 1800;
                session.minPlayers = 1;
                session.maxPlayers = 10;
                session.rewardMoney = 5000.0;
                session.rewardIslandXp = 150;
                session.scaleMobsPerPlayer = 2;
                break;
            case BOSS_FIGHT:
                session.durationSeconds = 240;
                session.intervalSeconds = 2400;
                session.minPlayers = 1;
                session.maxPlayers = 8;
                session.rewardMoney = 8000.0;
                session.rewardIslandXp = 250;
                session.scaleMobsPerPlayer = 1;
                break;
            case RESOURCE_RACE:
                session.durationSeconds = 180;
                session.intervalSeconds = 1800;
                session.minPlayers = 1;
                session.maxPlayers = 12;
                session.rewardMoney = 4000.0;
                session.rewardIslandXp = 100;
                session.scaleMobsPerPlayer = 0;
                break;
            case PVP_ARENA:
                session.durationSeconds = 300;
                session.intervalSeconds = 2400;
                session.minPlayers = 2;
                session.maxPlayers = 16;
                session.rewardMoney = 6000.0;
                session.rewardIslandXp = 120;
                session.scaleMobsPerPlayer = 0;
                break;
            case TIMED_CHALLENGE:
                session.durationSeconds = 180;
                session.intervalSeconds = 1800;
                session.minPlayers = 1;
                session.maxPlayers = 10;
                session.rewardMoney = 4500.0;
                session.rewardIslandXp = 120;
                session.scaleMobsPerPlayer = 0;
                break;
            case ISLAND_WAR:
                session.durationSeconds = 1800;
                session.intervalSeconds = 7200;
                session.minPlayers = 2;
                session.maxPlayers = 100;
                session.rewardMoney = 15000.0;
                session.rewardIslandXp = 500;
                session.scaleMobsPerPlayer = 0;
                break;
            case CUSTOM:
            default:
                session.durationSeconds = 300;
                session.intervalSeconds = 1800;
                session.minPlayers = 1;
                session.maxPlayers = 10;
                session.rewardMoney = 3000.0;
                session.rewardIslandXp = 100;
                session.scaleMobsPerPlayer = 0;
                break;
        }
    }

    private EventType parseType(String input) {
        String msg = input.trim();
        if ("1".equals(msg) || "wave".equalsIgnoreCase(msg) || "waves".equalsIgnoreCase(msg)) return EventType.WAVE_SURVIVAL;
        if ("2".equals(msg) || "boss".equalsIgnoreCase(msg)) return EventType.BOSS_FIGHT;
        if ("3".equals(msg) || "resource".equalsIgnoreCase(msg) || "race".equalsIgnoreCase(msg)) return EventType.RESOURCE_RACE;
        if ("4".equals(msg) || "pvp".equalsIgnoreCase(msg)) return EventType.PVP_ARENA;
        if ("5".equals(msg) || "timed".equalsIgnoreCase(msg) || "time".equalsIgnoreCase(msg)) return EventType.TIMED_CHALLENGE;
        if ("6".equals(msg) || "war".equalsIgnoreCase(msg) || "islandwar".equalsIgnoreCase(msg)) return EventType.ISLAND_WAR;
        if ("7".equals(msg) || "custom".equalsIgnoreCase(msg)) return EventType.CUSTOM;
        try {
            return EventType.valueOf(msg.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ZoneDefinition parseZone(String input) {
        Collection<ZoneDefinition> zones = plugin.getZoneManager().getZones();
        try {
            int index = Integer.parseInt(input.trim());
            if (index > 0 && index <= zones.size()) {
                int current = 1;
                for (ZoneDefinition zone : zones) {
                    if (current == index) return zone;
                    current++;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return plugin.getZoneManager().getZone(input.trim());
    }

    private ZoneDefinition firstZone() {
        Collection<ZoneDefinition> zones = plugin.getZoneManager().getZones();
        for (ZoneDefinition zone : zones) return zone;
        return null;
    }

    private Integer parseInt(Player player, String msg, int defaultValue, int min, String label) {
        if (isDefault(msg) || isSkip(msg)) return Integer.valueOf(defaultValue);
        try {
            int value = Integer.parseInt(msg);
            if (value < min) {
                player.sendMessage(msg("messages.event-wizard.min-error", vars("label", label, "min", String.valueOf(min))));
                return null;
            }
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLang().msg("messages.event-wizard.number-error"));
            return null;
        }
    }

    private Double parseDouble(Player player, String msg, double defaultValue, double min, String label) {
        if (isDefault(msg) || isSkip(msg)) return Double.valueOf(defaultValue);
        try {
            double value = Double.parseDouble(msg.replace(',', '.'));
            if (value < min) {
                player.sendMessage(msg("messages.event-wizard.min-error", vars("label", label, "min", String.valueOf(min))));
                return null;
            }
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLang().msg("messages.event-wizard.number-error"));
            return null;
        }
    }

    private void ok(Player player, String label, String value) {
        player.sendMessage(msg("messages.event-wizard.ok", vars("label", label, "value", value)));
    }

    private String friendlyType(EventType type) {
        switch (type) {
            case WAVE_SURVIVAL: return "Волны мобов";
            case BOSS_FIGHT: return "Босс";
            case RESOURCE_RACE: return "Сбор ресурсов";
            case PVP_ARENA: return "PvP арена";
            case TIMED_CHALLENGE: return "Испытание на время";
            case ISLAND_WAR: return "Война островов";
            case CUSTOM: return "Кастомное событие";
            default: return type.name();
        }
    }

    private boolean isCancel(String msg) {
        return "cancel".equalsIgnoreCase(msg) || "отмена".equalsIgnoreCase(msg);
    }

    private boolean isBack(String msg) {
        return "back".equalsIgnoreCase(msg) || "назад".equalsIgnoreCase(msg);
    }

    private boolean isDefault(String msg) {
        return "default".equalsIgnoreCase(msg) || "def".equalsIgnoreCase(msg) || "по умолчанию".equalsIgnoreCase(msg);
    }

    private boolean isSkip(String msg) {
        return "skip".equalsIgnoreCase(msg) || "пропустить".equalsIgnoreCase(msg);
    }

    private boolean isList(String msg) {
        return "list".equalsIgnoreCase(msg) || "список".equalsIgnoreCase(msg);
    }

    private boolean isYes(String msg) {
        return "yes".equalsIgnoreCase(msg) || "y".equalsIgnoreCase(msg) || "да".equalsIgnoreCase(msg) || "save".equalsIgnoreCase(msg);
    }

    private boolean isNo(String msg) {
        return "no".equalsIgnoreCase(msg) || "n".equalsIgnoreCase(msg) || "нет".equalsIgnoreCase(msg);
    }

    private String color(String text) {
        return plugin.getLang().color(text);
    }

    private String msg(String key) {
        return plugin.getLang().msg(key);
    }

    private String msg(String key, Map<String, String> vars) {
        return plugin.getLang().msg(key, vars);
    }

    private Map<String, String> vars(String k1, String v1) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(k1, v1);
        return vars;
    }

    private Map<String, String> vars(String k1, String v1, String k2, String v2) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(k1, v1);
        vars.put(k2, v2);
        return vars;
    }

    private Map<String, String> summaryVars(EventWizardSession session) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("id", session.eventId);
        vars.put("name", ChatColor.translateAlternateColorCodes('&', session.displayName));
        vars.put("description", session.description.isEmpty() ? "-" : session.description);
        vars.put("type", session.type.name());
        vars.put("zone", session.zoneId == null || session.zoneId.isEmpty() ? "-" : session.zoneId);
        vars.put("duration", String.valueOf(session.durationSeconds));
        vars.put("interval", String.valueOf(session.intervalSeconds));
        vars.put("min", String.valueOf(session.minPlayers));
        vars.put("max", String.valueOf(session.maxPlayers));
        vars.put("money", String.valueOf(session.rewardMoney));
        vars.put("xp", String.valueOf(session.rewardIslandXp));
        vars.put("scale", String.valueOf(session.scaleMobsPerPlayer));
        return vars;
    }

    private static final class EventWizardSession {
        private final String eventId;
        private int step = STEP_NAME;
        private String displayName;
        private String description = "";
        private EventType type;
        private String zoneId = "";
        private int durationSeconds = 300;
        private int intervalSeconds = 1800;
        private int minPlayers = 1;
        private int maxPlayers = 10;
        private double rewardMoney = 5000.0;
        private int rewardIslandXp = 100;
        private int scaleMobsPerPlayer = 2;

        private EventWizardSession(String eventId) {
            this.eventId = eventId;
        }
    }
}
