package me.reil.voidrift.integration;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.objective.Objective;
import me.reil.voidrift.objective.PlayerProgress;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * PlaceholderAPI expansion for VoidRift.
 *
 * Placeholders:
 *   %voidrift_event%          - current event display name (or empty)
 *   %voidrift_event_id%       - current event id
 *   %voidrift_time%           - remaining time (m:ss)
 *   %voidrift_score%          - player score
 *   %voidrift_wave%           - current wave
 *   %voidrift_players%        - players in event
 *   %voidrift_in_event%       - true/false
 *   %voidrift_objective_1%    - first objective progress "3/10"
 *   %voidrift_objective_2%    - second objective progress
 *   %voidrift_active_count%   - number of active events
 *   %voidrift_kills%          - same as score (kill count)
 */
public final class PlaceholderHook {

    private final VoidRiftPlugin plugin;
    private boolean registered;

    public PlaceholderHook(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        this.registered = false;
    }

    public void register() {
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            new VoidRiftExpansion(plugin).register();
            registered = true;
            plugin.getLogger().info("PlaceholderAPI expansion registered.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
        }
    }

    public boolean isRegistered() { return registered; }

    /**
     * Resolve a placeholder for a player (called from expansion).
     */
    public String resolve(Player player, String identifier) {
        if (player == null) return "";

        ActiveEvent event = getPlayerEvent(player);

        switch (identifier) {
            case "event":
                return event != null ? event.getDefinition().getDisplayName() : "";
            case "event_id":
                return event != null ? event.getDefinition().getId() : "";
            case "time":
                return event != null ? formatTime(event.getRemainingSeconds()) : "";
            case "score":
            case "kills":
                return event != null ? String.valueOf(event.getScore(player.getUniqueId())) : "0";
            case "wave":
                return event != null ? String.valueOf(event.getCurrentWave()) : "0";
            case "players":
                return event != null ? String.valueOf(event.getParticipants().size()) : "0";
            case "in_event":
                return event != null ? "true" : "false";
            case "active_count":
                return String.valueOf(plugin.getEventManager().getActiveEvents().size());
            default:
                // top_N_name / top_N_score
                if (identifier.startsWith("top_")) {
                    return resolveTop(player, identifier);
                }
                // objective_N
                if (identifier.startsWith("objective_") && event != null) {
                    try {
                        int idx = Integer.parseInt(identifier.substring(10)) - 1;
                        List<Objective> objectives = event.getDefinition().getObjectives();
                        if (idx >= 0 && idx < objectives.size()) {
                            PlayerProgress progress = plugin.getObjectiveTracker().getPlayerProgress(
                                    event.getDefinition().getId(), player.getUniqueId());
                            int current = progress != null ? progress.getProgress(idx) : 0;
                            return current + "/" + objectives.get(idx).getAmount();
                        }
                    } catch (NumberFormatException ignored) {}
                }
                // Stats placeholders
                if (identifier.startsWith("stats_")) {
                    return resolveStats(player, identifier.substring(6));
                }
                return "";
        }
    }

    private String resolveStats(Player player, String field) {
        if (plugin.getStatsManager() == null) return "0";
        me.reil.voidrift.stats.PlayerStats stats = plugin.getStatsManager().getStats(player.getUniqueId());
        if (stats == null) return "0";
        switch (field) {
            case "kills": return String.valueOf(stats.getKills());
            case "deaths": return String.valueOf(stats.getDeaths());
            case "completions": return String.valueOf(stats.getCompletions());
            case "score": return String.valueOf(stats.getScore());
            case "time_played": return String.valueOf(stats.getTimePlayedSeconds());
            default: return "0";
        }
    }

    private String resolveTop(Player player, String identifier) {
        // Format: top_N_name or top_N_score
        // e.g. top_1_name, top_3_score
        String[] parts = identifier.split("_");
        if (parts.length < 3) return "";
        int rank;
        try { rank = Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) { return ""; }
        String field = parts[2]; // "name" or "score"

        // Try persistent leaderboard first
        // Use player's current event or first available
        ActiveEvent event = getPlayerEvent(player);
        String eventId = event != null ? event.getDefinition().getId() : null;
        if (eventId == null) {
            eventId = plugin.getLeaderboard().getFirstEventId();
        }

        if (eventId != null) {
            me.reil.voidrift.leaderboard.LeaderboardEntry lbEntry = plugin.getLeaderboard().getEntry(eventId, rank);
            if (lbEntry != null) {
                if ("name".equals(field)) return lbEntry.getPlayerName();
                if ("score".equals(field)) return String.valueOf(lbEntry.getScore());
            }
        }

        // Fallback to active event scores
        if (event == null) {
            for (ActiveEvent ae : plugin.getEventManager().getActiveEvents()) {
                event = ae;
                break;
            }
        }
        if (event == null) return "";

        java.util.List<java.util.Map.Entry<java.util.UUID, Integer>> top = event.getTopPlayers(rank);
        if (top.size() < rank) return "";
        java.util.Map.Entry<java.util.UUID, Integer> entry = top.get(rank - 1);

        if ("name".equals(field)) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey()).getName();
            return name != null ? name : "";
        } else if ("score".equals(field)) {
            return String.valueOf(entry.getValue());
        }
        return "";
    }

    private ActiveEvent getPlayerEvent(Player player) {
        for (ActiveEvent event : plugin.getEventManager().getActiveEvents()) {
            if (event.isParticipant(player.getUniqueId())) return event;
        }
        return null;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
