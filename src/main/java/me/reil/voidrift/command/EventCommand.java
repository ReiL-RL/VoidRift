package me.reil.voidrift.command;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventCommand implements CommandExecutor, TabCompleter {

    private final VoidRiftPlugin plugin;

    public EventCommand(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only players."); return true; }
        Player p = (Player) sender;

        if (args.length == 0) {
            // Open GUI
            plugin.getEventGui().open(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join":
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "/event join <id>"); return true; }
                boolean joined = plugin.getEventManager().joinEvent(p, args[1]);
                p.sendMessage(joined ? ChatColor.GREEN + "\u0422\u044b \u043f\u0440\u0438\u0441\u043e\u0435\u0434\u0438\u043d\u0438\u043b\u0441\u044f \u043a \u0441\u043e\u0431\u044b\u0442\u0438\u044e!" : ChatColor.RED + "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u0440\u0438\u0441\u043e\u0435\u0434\u0438\u043d\u0438\u0442\u044c\u0441\u044f.");
                break;
            case "leave":
                String eventId = findPlayerEvent(p);
                if (eventId != null) {
                    plugin.getEventManager().leaveEvent(p, eventId);
                    p.sendMessage(ChatColor.YELLOW + "\u0422\u044b \u043f\u043e\u043a\u0438\u043d\u0443\u043b \u0441\u043e\u0431\u044b\u0442\u0438\u0435.");
                } else {
                    p.sendMessage(ChatColor.RED + "\u0422\u044b \u043d\u0435 \u0443\u0447\u0430\u0441\u0442\u0432\u0443\u0435\u0448\u044c \u0432 \u0441\u043e\u0431\u044b\u0442\u0438\u0438.");
                }
                break;
            case "list":
                p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2726 \u0412\u0441\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u044f:");
                for (EventDefinition def : plugin.getEventManager().getDefinitions()) {
                    boolean active = plugin.getEventManager().getActiveEvent(def.getId()) != null;
                    p.sendMessage((active ? ChatColor.GREEN + "\u25CF " : ChatColor.GRAY + "\u25CB ") + ChatColor.YELLOW + def.getDisplayName() + ChatColor.GRAY + " [" + def.getId() + "]");
                }
                break;
            case "top":
                handleTop(p, args);
                break;
            case "gui":
                plugin.getEventGui().open(p);
                break;
            default:
                p.sendMessage(ChatColor.RED + "/event [join|leave|list|top|gui]");
                break;
        }
        return true;
    }

    private void handleTop(Player p, String[] args) {
        // Show leaderboard from persistent storage
        String eventId = null;
        if (args.length >= 2) {
            eventId = args[1];
        } else {
            // Try player's current event first
            ActiveEvent playerEvent = getPlayerActiveEvent(p);
            if (playerEvent != null) {
                eventId = playerEvent.getDefinition().getId();
            } else {
                // Try first event with leaderboard data
                eventId = plugin.getLeaderboard().getFirstEventId();
            }
        }

        if (eventId == null) {
            // Fallback: show active event scores
            ActiveEvent event = null;
            for (ActiveEvent ae : plugin.getEventManager().getActiveEvents()) {
                event = ae;
                break;
            }
            if (event == null) {
                p.sendMessage(ChatColor.RED + "\u041d\u0435\u0442 \u0434\u0430\u043d\u043d\u044b\u0445 \u043b\u0438\u0434\u0435\u0440\u0431\u043e\u0440\u0434\u0430.");
                return;
            }
            showActiveEventTop(p, event);
            return;
        }

        // Show persistent leaderboard
        java.util.List<me.reil.voidrift.leaderboard.LeaderboardEntry> top = plugin.getLeaderboard().getTop(eventId);
        if (top.isEmpty()) {
            // Fallback to active event scores
            ActiveEvent active = plugin.getEventManager().getActiveEvent(eventId);
            if (active != null) {
                showActiveEventTop(p, active);
            } else {
                p.sendMessage(ChatColor.RED + "\u041d\u0435\u0442 \u0434\u0430\u043d\u043d\u044b\u0445 \u043b\u0438\u0434\u0435\u0440\u0431\u043e\u0440\u0434\u0430 \u0434\u043b\u044f: " + eventId);
            }
            return;
        }

        EventDefinition def = plugin.getEventManager().getDefinition(eventId);
        String title = def != null ? def.getDisplayName() : eventId;
        p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2726 \u0422\u043e\u043f-10: " + ChatColor.translateAlternateColorCodes('&', title));
        int rank = 1;
        for (me.reil.voidrift.leaderboard.LeaderboardEntry entry : top) {
            p.sendMessage(ChatColor.YELLOW + "  #" + rank + " " + ChatColor.WHITE + entry.getPlayerName() + ChatColor.GRAY + " \u2014 " + ChatColor.GREEN + entry.getScore() + " \u043e\u0447\u043a\u043e\u0432");
            rank++;
        }
    }

    private void showActiveEventTop(Player p, ActiveEvent event) {
        List<Map.Entry<UUID, Integer>> top = event.getTopPlayers(10);
        p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2726 \u0422\u043e\u043f-10: " + event.getDefinition().getDisplayName());
        if (top.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "  \u041f\u043e\u043a\u0430 \u043d\u0435\u0442 \u043e\u0447\u043a\u043e\u0432.");
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = entry.getKey().toString().substring(0, 8);
                p.sendMessage(ChatColor.YELLOW + "  #" + rank + " " + ChatColor.WHITE + name + ChatColor.GRAY + " \u2014 " + ChatColor.GREEN + entry.getValue() + " \u043e\u0447\u043a\u043e\u0432");
                rank++;
            }
        }
    }

    private ActiveEvent getPlayerActiveEvent(Player p) {
        for (ActiveEvent event : plugin.getEventManager().getActiveEvents()) {
            if (event.isParticipant(p.getUniqueId())) return event;
        }
        return null;
    }

    private String findPlayerEvent(Player p) {
        for (ActiveEvent event : plugin.getEventManager().getActiveEvents()) {
            if (event.isParticipant(p.getUniqueId())) return event.getDefinition().getId();
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("join", "leave", "list", "top", "gui"), args[0]);
        if (args.length == 2 && "join".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (ActiveEvent e : plugin.getEventManager().getActiveEvents()) ids.add(e.getDefinition().getId());
            return ids;
        }
        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (ActiveEvent e : plugin.getEventManager().getActiveEvents()) ids.add(e.getDefinition().getId());
            return ids;
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<String>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }
}
