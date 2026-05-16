package me.reil.voidrift.leaderboard;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores top scores per event. Keeps top 10 per event.
 * Saves/loads from leaderboard.yml.
 */
public final class Leaderboard {

    private static final int MAX_ENTRIES = 10;

    private final VoidRiftPlugin plugin;
    private final Map<String, List<LeaderboardEntry>> boards = new LinkedHashMap<String, List<LeaderboardEntry>>();

    public Leaderboard(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Update leaderboard when an event ends.
     */
    public void onEventEnd(ActiveEvent event) {
        String eventId = event.getDefinition().getId();
        List<LeaderboardEntry> board = boards.get(eventId);
        if (board == null) {
            board = new ArrayList<LeaderboardEntry>();
            boards.put(eventId, board);
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Integer> entry : event.getScores().entrySet()) {
            UUID uuid = entry.getKey();
            int score = entry.getValue();
            if (score <= 0) continue;

            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);

            board.add(new LeaderboardEntry(name, uuid, score, now));
        }

        // Sort descending by score, keep top 10
        Collections.sort(board, new Comparator<LeaderboardEntry>() {
            @Override
            public int compare(LeaderboardEntry a, LeaderboardEntry b) {
                return Integer.compare(b.getScore(), a.getScore());
            }
        });
        if (board.size() > MAX_ENTRIES) {
            boards.put(eventId, new ArrayList<LeaderboardEntry>(board.subList(0, MAX_ENTRIES)));
        }

        save();
    }

    /**
     * Get top entries for an event.
     */
    public List<LeaderboardEntry> getTop(String eventId) {
        List<LeaderboardEntry> board = boards.get(eventId);
        if (board == null) return Collections.emptyList();
        return Collections.unmodifiableList(board);
    }

    /**
     * Get a specific rank entry for an event (1-indexed).
     */
    public LeaderboardEntry getEntry(String eventId, int rank) {
        List<LeaderboardEntry> board = boards.get(eventId);
        if (board == null || rank < 1 || rank > board.size()) return null;
        return board.get(rank - 1);
    }

    /**
     * Get the first event id that has leaderboard data (for default display).
     */
    public String getFirstEventId() {
        for (Map.Entry<String, List<LeaderboardEntry>> entry : boards.entrySet()) {
            if (!entry.getValue().isEmpty()) return entry.getKey();
        }
        return null;
    }

    // ===== Persistence =====

    private void load() {
        File file = new File(plugin.getDataFolder(), "leaderboard.yml");
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("leaderboards");
        if (section == null) return;

        for (String eventId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(eventId);
            if (eventSection == null) continue;

            List<LeaderboardEntry> entries = new ArrayList<LeaderboardEntry>();
            for (String key : eventSection.getKeys(false)) {
                ConfigurationSection entrySection = eventSection.getConfigurationSection(key);
                if (entrySection == null) continue;

                String name = entrySection.getString("name", "Unknown");
                String uuidStr = entrySection.getString("uuid", "");
                int score = entrySection.getInt("score", 0);
                long timestamp = entrySection.getLong("timestamp", 0L);

                UUID uuid;
                try { uuid = UUID.fromString(uuidStr); }
                catch (IllegalArgumentException e) { continue; }

                entries.add(new LeaderboardEntry(name, uuid, score, timestamp));
            }

            if (!entries.isEmpty()) {
                boards.put(eventId, entries);
            }
        }

        plugin.getLogger().info("Loaded leaderboards for " + boards.size() + " events.");
    }

    private void save() {
        File file = new File(plugin.getDataFolder(), "leaderboard.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<String, List<LeaderboardEntry>> entry : boards.entrySet()) {
            String eventId = entry.getKey();
            List<LeaderboardEntry> entries = entry.getValue();
            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry le = entries.get(i);
                String path = "leaderboards." + eventId + "." + (i + 1);
                cfg.set(path + ".name", le.getPlayerName());
                cfg.set(path + ".uuid", le.getUuid().toString());
                cfg.set(path + ".score", le.getScore());
                cfg.set(path + ".timestamp", le.getTimestamp());
            }
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save leaderboard.yml: " + e.getMessage());
        }
    }
}
