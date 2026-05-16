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
        String msg = ChatColor.GOLD + "\u2726 " + ChatColor.YELLOW + event.getDefinition().getDisplayName()
                + ChatColor.GRAY + " | " + ChatColor.WHITE + formatTime(remaining)
                + ChatColor.GRAY + " | " + ChatColor.GREEN + "\u2605 " + score;

        // Add first objective progress
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

        // Remove old objective
        org.bukkit.scoreboard.Objective obj = board.getObjective("voidrift");
        if (obj != null) obj.unregister();

        obj = board.registerNewObjective("voidrift", "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + "\u2726 " + event.getDefinition().getDisplayName());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15;

        // Time
        obj.getScore(ChatColor.YELLOW + "\u23F1 " + formatTime(event.getRemainingSeconds())).setScore(line--);
        obj.getScore(ChatColor.GRAY + "---").setScore(line--);

        // Score
        obj.getScore(ChatColor.GREEN + "\u2605 \u041e\u0447\u043a\u0438: " + event.getScore(player.getUniqueId())).setScore(line--);

        // Objectives
        List<Objective> objectives = event.getDefinition().getObjectives();
        PlayerProgress progress = getProgress(event, player.getUniqueId());
        if (!objectives.isEmpty() && progress != null) {
            obj.getScore(ChatColor.GRAY + "----").setScore(line--);
            obj.getScore(ChatColor.AQUA + "\u0426\u0435\u043b\u0438:").setScore(line--);
            for (int i = 0; i < Math.min(objectives.size(), 5); i++) {
                Objective o = objectives.get(i);
                int cur = progress.getProgress(i);
                boolean done = cur >= o.getAmount();
                String status = done ? (ChatColor.GREEN + "\u2714 ") : (ChatColor.WHITE + "\u25CB ");
                String text = status + objectiveName(o) + ChatColor.GRAY + " " + cur + "/" + o.getAmount();
                obj.getScore(text).setScore(line--);
            }
        }

        // Players
        obj.getScore(ChatColor.GRAY + "-----").setScore(line--);
        obj.getScore(ChatColor.LIGHT_PURPLE + "\u0418\u0433\u0440\u043e\u043a\u043e\u0432: " + event.getParticipants().size()).setScore(line--);

        // Modifiers
        if (event.getModifiers() != null && !event.getModifiers().isEmpty()) {
            obj.getScore(ChatColor.GRAY + "------").setScore(line--);
            String modDisplay = plugin.getModifierManager() != null ? plugin.getModifierManager().getModifierDisplay(event) : "";
            if (modDisplay.length() > 30) modDisplay = modDisplay.substring(0, 30) + "...";
            obj.getScore(ChatColor.DARK_PURPLE + "\u2726 " + modDisplay).setScore(line--);
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
            case KILL_MOBS: return "\u0423\u0431\u0438\u0442\u044c \u043c\u043e\u0431\u043e\u0432";
            case KILL_BOSS: return "\u0423\u0431\u0438\u0442\u044c \u0431\u043e\u0441\u0441\u0430";
            case KILL_ELITE: return "\u0423\u0431\u0438\u0442\u044c \u044d\u043b\u0438\u0442\u0443";
            case SURVIVE_TIME: return "\u0412\u044b\u0436\u0438\u0442\u044c";
            case REACH_WAVE: return "\u0414\u043e\u0439\u0442\u0438 \u0434\u043e \u0432\u043e\u043b\u043d\u044b";
            case COLLECT_ITEM: return "\u0421\u043e\u0431\u0440\u0430\u0442\u044c";
            case MINE_BLOCK: return "\u0421\u043b\u043e\u043c\u0430\u0442\u044c";
            case SCORE_POINTS: return "\u041d\u0430\u0431\u0440\u0430\u0442\u044c \u043e\u0447\u043a\u0438";
            case NO_DEATH: return "\u041d\u0435 \u0443\u043c\u0435\u0440\u0435\u0442\u044c";
            case DEAL_DAMAGE: return "\u041d\u0430\u043d\u0435\u0441\u0442\u0438 \u0443\u0440\u043e\u043d";
            default: return obj.getType().name();
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
