package me.reil.voidrift.display;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.objective.Objective;
import me.reil.voidrift.objective.PlayerProgress;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.UUID;

/**
 * Displays event progress via ActionBar and Sidebar scoreboard.
 * Both can be toggled in config.yml.
 */
public final class EventDisplay {

    private final VoidRiftPlugin plugin;
    private BukkitTask task;

    public EventDisplay(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void update() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ActiveEvent event = getPlayerEvent(player.getUniqueId());
            if (event == null) {
                clearSidebar(player);
                continue;
            }
            if (plugin.getConfig().getBoolean("display.actionbar", true)) {
                sendActionBar(player, event);
            }
            if (plugin.getConfig().getBoolean("display.sidebar", true)) {
                updateSidebar(player, event);
            }
        }
    }

    private void sendActionBar(Player player, ActiveEvent event) {
        long remaining = event.getRemainingSeconds();
        int score = event.getScore(player.getUniqueId());
        String msg = ChatColor.GOLD + "✦ " + ChatColor.YELLOW + event.getDefinition().getDisplayName()
                + ChatColor.GRAY + " | " + ChatColor.WHITE + formatTime(remaining)
                + ChatColor.GRAY + " | " + ChatColor.GREEN + "★ " + score;

        List<Objective> objectives = event.getDefinition().getObjectives();
        if (!objectives.isEmpty()) {
            PlayerProgress progress = getProgress(event, player.getUniqueId());
            if (progress != null) {
                Objective first = objectives.get(0);
                int current = progress.getProgress(0);
                msg += ChatColor.GRAY + " | " + ChatColor.AQUA + current + "/" + first.getAmount();
            }
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    private void updateSidebar(Player player, ActiveEvent event) {
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        org.bukkit.scoreboard.Objective obj = board.getObjective("voidrift");
        if (obj != null) obj.unregister();

        obj = board.registerNewObjective("voidrift", "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + "✦ " + event.getDefinition().getDisplayName());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15;

        obj.getScore(ChatColor.YELLOW + "⏱ " + formatTime(event.getRemainingSeconds())).setScore(line--);
        obj.getScore(ChatColor.GRAY + "---").setScore(line--);

        // Score
        String scoreLabel = plugin.getLang().msg("messages.display.score-label");
        obj.getScore(ChatColor.GREEN + scoreLabel + event.getScore(player.getUniqueId())).setScore(line--);

        // Objectives
        List<Objective> objectives = event.getDefinition().getObjectives();
        PlayerProgress progress = getProgress(event, player.getUniqueId());
        if (!objectives.isEmpty() && progress != null) {
            obj.getScore(ChatColor.GRAY + "----").setScore(line--);
            String objectivesLabel = plugin.getLang().msg("messages.display.objectives-label");
            obj.getScore(ChatColor.AQUA + objectivesLabel).setScore(line--);
            for (int i = 0; i < Math.min(objectives.size(), 5); i++) {
                Objective o = objectives.get(i);
                int cur = progress.getProgress(i);
                boolean done = cur >= o.getAmount();
                String status = done ? (ChatColor.GREEN + "✔ ") : (ChatColor.WHITE + "○ ");
                String text = status + objectiveName(o) + ChatColor.GRAY + " " + cur + "/" + o.getAmount();
                obj.getScore(text).setScore(line--);
            }
        }

        // Players
        obj.getScore(ChatColor.GRAY + "-----").setScore(line--);
        String playersLabel = plugin.getLang().msg("messages.display.players-label");
        obj.getScore(ChatColor.LIGHT_PURPLE + playersLabel + event.getParticipants().size()).setScore(line--);

        // Modifiers
        if (event.getModifiers() != null && !event.getModifiers().isEmpty()) {
            obj.getScore(ChatColor.GRAY + "------").setScore(line--);
            String modDisplay = plugin.getModifierManager() != null ? plugin.getModifierManager().getModifierDisplay(event) : "";
            if (modDisplay.length() > 30) modDisplay = modDisplay.substring(0, 30) + "...";
            obj.getScore(ChatColor.DARK_PURPLE + "✦ " + modDisplay).setScore(line--);
        }
    }

    private void clearSidebar(Player player) {
        Scoreboard board = player.getScoreboard();
        org.bukkit.scoreboard.Objective obj = board.getObjective("voidrift");
        if (obj != null) obj.unregister();
    }

    private PlayerProgress getProgress(ActiveEvent event, UUID playerId) {
        return plugin.getObjectiveTracker().getPlayerProgress(event.getDefinition().getId(), playerId);
    }

    private ActiveEvent getPlayerEvent(UUID playerId) {
        for (ActiveEvent event : plugin.getEventManager().getActiveEvents()) {
            if (event.isParticipant(playerId)) return event;
        }
        return null;
    }

    private String objectiveName(Objective obj) {
        switch (obj.getType()) {
            case KILL_MOBS: return plugin.getLang().msg("messages.objective-names.kill-mobs");
            case KILL_BOSS: return plugin.getLang().msg("messages.objective-names.kill-boss");
            case KILL_ELITE: return plugin.getLang().msg("messages.objective-names.kill-elite");
            case SURVIVE_TIME: return plugin.getLang().msg("messages.objective-names.survive-time");
            case REACH_WAVE: return plugin.getLang().msg("messages.objective-names.reach-wave");
            case COLLECT_ITEM: return plugin.getLang().msg("messages.objective-names.collect-item");
            case MINE_BLOCK: return plugin.getLang().msg("messages.objective-names.mine-block");
            case SCORE_POINTS: return plugin.getLang().msg("messages.objective-names.score-points");
            case NO_DEATH: return plugin.getLang().msg("messages.objective-names.no-death");
            case DEAL_DAMAGE: return plugin.getLang().msg("messages.objective-names.deal-damage");
            default: return obj.getType().name();
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
