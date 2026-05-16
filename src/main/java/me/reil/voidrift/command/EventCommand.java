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
import java.util.HashMap;
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
            plugin.getEventGui().open(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join":
                if (args.length < 2) { p.sendMessage(plugin.getLang().msg("messages.admin.usage-join")); return true; }
                boolean joined = plugin.getEventManager().joinEvent(p, args[1]);
                p.sendMessage(joined ? plugin.getLang().msgFor(p, "messages.event.join-success") : plugin.getLang().msgFor(p, "messages.event.join-fail"));
                break;
            case "leave":
                String eventId = findPlayerEvent(p);
                if (eventId != null) {
                    plugin.getEventManager().leaveEvent(p, eventId);
                    p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leave-success"));
                } else {
                    p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leave-not-in"));
                }
                break;
            case "list":
                p.sendMessage(plugin.getLang().msg("messages.event.list-header"));
                for (EventDefinition def : plugin.getEventManager().getDefinitions()) {
                    boolean active = plugin.getEventManager().getActiveEvent(def.getId()) != null;
                    p.sendMessage((active ? ChatColor.GREEN + "● " : ChatColor.GRAY + "○ ") + ChatColor.YELLOW + def.getDisplayName() + ChatColor.GRAY + " [" + def.getId() + "]");
                }
                break;
            case "top":
                handleTop(p, args);
                break;
            case "gui":
                plugin.getEventGui().open(p);
                break;
            default:
                p.sendMessage(plugin.getLang().msg("messages.admin.usage-event"));
                break;
        }
        return true;
    }

    private void handleTop(Player p, String[] args) {
        String eventId = null;
        if (args.length >= 2) {
            eventId = args[1];
        } else {
            ActiveEvent playerEvent = getPlayerActiveEvent(p);
            if (playerEvent != null) {
                eventId = playerEvent.getDefinition().getId();
            } else {
                eventId = plugin.getLeaderboard().getFirstEventId();
            }
        }

        if (eventId == null) {
            ActiveEvent event = null;
            for (ActiveEvent ae : plugin.getEventManager().getActiveEvents()) {
                event = ae;
                break;
            }
            if (event == null) {
                p.sendMessage(plugin.getLang().msgFor(p, "messages.event.no-leaderboard"));
                return;
            }
            showActiveEventTop(p, event);
            return;
        }

        java.util.List<me.reil.voidrift.leaderboard.LeaderboardEntry> top = plugin.getLeaderboard().getTop(eventId);
        if (top.isEmpty()) {
            ActiveEvent active = plugin.getEventManager().getActiveEvent(eventId);
            if (active != null) {
                showActiveEventTop(p, active);
            } else {
                Map<String, String> vars = new HashMap<String, String>();
                vars.put("id", eventId);
                p.sendMessage(plugin.getLang().msgFor(p, "messages.event.no-leaderboard-for", vars));
            }
            return;
        }

        EventDefinition def = plugin.getEventManager().getDefinition(eventId);
        String title = def != null ? def.getDisplayName() : eventId;
        Map<String, String> headerVars = new HashMap<String, String>();
        headerVars.put("event", title);
        p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-header", headerVars));
        int rank = 1;
        for (me.reil.voidrift.leaderboard.LeaderboardEntry entry : top) {
            Map<String, String> entryVars = new HashMap<String, String>();
            entryVars.put("rank", String.valueOf(rank));
            entryVars.put("player", entry.getPlayerName());
            entryVars.put("score", String.valueOf(entry.getScore()));
            p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-entry", entryVars));
            rank++;
        }
    }

    private void showActiveEventTop(Player p, ActiveEvent event) {
        List<Map.Entry<UUID, Integer>> top = event.getTopPlayers(10);
        Map<String, String> headerVars = new HashMap<String, String>();
        headerVars.put("event", event.getDefinition().getDisplayName());
        p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-header", headerVars));
        if (top.isEmpty()) {
            p.sendMessage(plugin.getLang().msgFor(p, "messages.event.no-scores-yet"));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = entry.getKey().toString().substring(0, 8);
                Map<String, String> entryVars = new HashMap<String, String>();
                entryVars.put("rank", String.valueOf(rank));
                entryVars.put("player", name);
                entryVars.put("score", String.valueOf(entry.getValue()));
                p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-entry", entryVars));
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
