package me.reil.voidrift.display;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a BossBar (PURPLE) with event name + time remaining.
 * Progress = time remaining / total duration.
 */
public final class BossBarDisplay {

    private final VoidRiftPlugin plugin;
    // eventId -> BossBar
    private final Map<String, BossBar> bossBars = new LinkedHashMap<String, BossBar>();
    private BukkitTask task;

    public BossBarDisplay(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("display.bossbar", true)) return;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                update();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }

    private void update() {
        // Remove bars for events that ended
        for (String eventId : new java.util.ArrayList<String>(bossBars.keySet())) {
            ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
            if (event == null || event.isFinished()) {
                BossBar bar = bossBars.remove(eventId);
                if (bar != null) bar.removeAll();
            }
        }

        // Update/create bars for active events
        for (ActiveEvent event : plugin.getEventManager().getActiveEvents()) {
            if (event.isFinished()) continue;
            String eventId = event.getDefinition().getId();

            BossBar bar = bossBars.get(eventId);
            if (bar == null) {
                String title = ChatColor.translateAlternateColorCodes('&',
                        "&d\u2726 " + event.getDefinition().getDisplayName() + " &7| " + formatTime(event.getRemainingSeconds()));
                bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SOLID);
                bossBars.put(eventId, bar);
            }

            // Update title and progress
            String title = ChatColor.translateAlternateColorCodes('&',
                    "&d\u2726 " + event.getDefinition().getDisplayName() + " &7| " + formatTime(event.getRemainingSeconds()));
            bar.setTitle(title);

            double totalDuration = event.getDefinition().getDurationSeconds();
            double remaining = event.getRemainingSeconds();
            double progress = totalDuration > 0 ? remaining / totalDuration : 0.0;
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

            // Add participants who don't have the bar, remove non-participants
            for (UUID playerId : event.getParticipants()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    if (!bar.getPlayers().contains(player)) {
                        bar.addPlayer(player);
                    }
                }
            }
            // Remove players who left
            for (Player player : new java.util.ArrayList<Player>(bar.getPlayers())) {
                if (!event.isParticipant(player.getUniqueId())) {
                    bar.removePlayer(player);
                }
            }
        }
    }

    /**
     * Add a player to the bossbar for their event.
     */
    public void addPlayer(Player player, String eventId) {
        BossBar bar = bossBars.get(eventId);
        if (bar != null) {
            bar.addPlayer(player);
        }
    }

    /**
     * Remove a player from all bossbars.
     */
    public void removePlayer(Player player) {
        for (BossBar bar : bossBars.values()) {
            bar.removePlayer(player);
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
