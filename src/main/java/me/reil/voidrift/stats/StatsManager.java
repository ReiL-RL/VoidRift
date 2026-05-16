package me.reil.voidrift.stats;

import me.reil.voidrift.VoidRiftPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite-based player statistics storage.
 * Table: player_stats (uuid, event_id, kills, deaths, score, completions, time_played_seconds, last_played)
 */
public final class StatsManager {

    private final VoidRiftPlugin plugin;
    private Connection connection;

    public StatsManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "stats.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTable();
            plugin.getLogger().info("SQLite stats database initialized.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite stats: " + e.getMessage());
        }
    }

    private void createTable() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS player_stats (" +
            "uuid TEXT NOT NULL, " +
            "event_id TEXT NOT NULL, " +
            "kills INTEGER DEFAULT 0, " +
            "deaths INTEGER DEFAULT 0, " +
            "score INTEGER DEFAULT 0, " +
            "completions INTEGER DEFAULT 0, " +
            "time_played_seconds INTEGER DEFAULT 0, " +
            "last_played BIGINT DEFAULT 0, " +
            "PRIMARY KEY (uuid, event_id))"
        );
        stmt.close();
    }

    /**
     * Add a kill for a player in an event.
     */
    public void addKill(UUID uuid, String eventId) {
        ensureRow(uuid.toString(), eventId);
        executeUpdate("UPDATE player_stats SET kills = kills + 1, last_played = ? WHERE uuid = ? AND event_id = ?",
                System.currentTimeMillis(), uuid.toString(), eventId);
    }

    /**
     * Add a death for a player in an event.
     */
    public void addDeath(UUID uuid, String eventId) {
        ensureRow(uuid.toString(), eventId);
        executeUpdate("UPDATE player_stats SET deaths = deaths + 1, last_played = ? WHERE uuid = ? AND event_id = ?",
                System.currentTimeMillis(), uuid.toString(), eventId);
    }

    /**
     * Add a completion for a player in an event.
     */
    public void addCompletion(UUID uuid, String eventId, int score, int timePlayed) {
        ensureRow(uuid.toString(), eventId);
        executeUpdate(
            "UPDATE player_stats SET completions = completions + 1, score = score + ?, " +
            "time_played_seconds = time_played_seconds + ?, last_played = ? WHERE uuid = ? AND event_id = ?",
            score, timePlayed, System.currentTimeMillis(), uuid.toString(), eventId);
    }

    /**
     * Get stats for a player in a specific event.
     */
    public PlayerStats getStats(UUID uuid, String eventId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_stats WHERE uuid = ? AND event_id = ?");
            ps.setString(1, uuid.toString());
            ps.setString(2, eventId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerStats stats = fromResultSet(rs);
                rs.close();
                ps.close();
                return stats;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get stats: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get aggregated stats for a player across all events.
     */
    public PlayerStats getStats(UUID uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, 'all' as event_id, SUM(kills) as kills, SUM(deaths) as deaths, " +
                "SUM(score) as score, SUM(completions) as completions, " +
                "SUM(time_played_seconds) as time_played_seconds, MAX(last_played) as last_played " +
                "FROM player_stats WHERE uuid = ? GROUP BY uuid");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerStats stats = fromResultSet(rs);
                rs.close();
                ps.close();
                return stats;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get aggregated stats: " + e.getMessage());
        }
        return null;
    }

    /**
     * Close the database connection.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    private void ensureRow(String uuid, String eventId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_stats (uuid, event_id, kills, deaths, score, completions, time_played_seconds, last_played) " +
                "VALUES (?, ?, 0, 0, 0, 0, 0, 0)");
            ps.setString(1, uuid);
            ps.setString(2, eventId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to ensure stats row: " + e.getMessage());
        }
    }

    private void executeUpdate(String sql, Object... params) {
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof Integer) {
                    ps.setInt(i + 1, (Integer) param);
                } else if (param instanceof Long) {
                    ps.setLong(i + 1, (Long) param);
                } else {
                    ps.setString(i + 1, String.valueOf(param));
                }
            }
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to execute stats update: " + e.getMessage());
        }
    }

    private PlayerStats fromResultSet(ResultSet rs) throws SQLException {
        return new PlayerStats(
            rs.getString("uuid"),
            rs.getString("event_id"),
            rs.getInt("kills"),
            rs.getInt("deaths"),
            rs.getInt("score"),
            rs.getInt("completions"),
            rs.getInt("time_played_seconds"),
            rs.getLong("last_played")
        );
    }
}
