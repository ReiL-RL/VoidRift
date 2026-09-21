package me.reil.voidrift.command;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventDefinition;
import me.reil.voidrift.leaderboard.LeaderboardEntry;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().msg("messages.players-only"));
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            plugin.getEventGui().open(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join":
                if (args.length < 2) {
                    p.sendMessage(plugin.getLang().msgFor(p, "messages.admin.usage-join"));
                    return true;
                }
                boolean joined = plugin.getEventManager().joinEvent(p, args[1]);
                p.sendMessage(joined
                        ? plugin.getLang().msgFor(p, "messages.event.join-success")
                        : plugin.getLang().msgFor(p, "messages.event.join-fail"));
                break;
            case "leave":
                if (plugin.getPortalManager().isPlayerInEvent(p.getUniqueId())) {
                    p.sendMessage(plugin.getLang().msgFor(p, "messages.event.exit-through-portal"));
                    break;
                }
                String eventId = findPlayerEvent(p);
                if (eventId != null) {
                    plugin.getEventManager().leaveEvent(p, eventId);
                    p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leave-success"));
                } else {
                    p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leave-not-in"));
                }
                break;
            case "list":
                p.sendMessage(plugin.getLang().msgFor(p, "messages.event.list-header"));
                for (EventDefinition def : plugin.getEventManager().getDefinitions()) {
                    boolean active = plugin.getEventManager().getActiveEvent(def.getId()) != null;
                    p.sendMessage((active ? ChatColor.GREEN + "● " : ChatColor.GRAY + "○ ")
                            + ChatColor.YELLOW + def.getDisplayName()
                            + ChatColor.GRAY + " [" + def.getId() + "]");
                }
                break;
            case "top":
                handleTop(p, args);
                break;
            case "gui":
                plugin.getEventGui().open(p);
                break;
            case "attack":
                plugin.getIslandWarMenu().open(p);
                break;
            case "heart":
                handleHeart(p);
                break;
            case "status":
                handleStatus(p);
                break;
            default:
                p.sendMessage(plugin.getLang().msgFor(p, "messages.admin.usage-event"));
                break;
        }
        return true;
    }

    private void handleHeart(Player p) {
        if (!plugin.getSkyBoundHook().isAvailable()) {
            p.sendMessage(plugin.getLang().msgFor(p, "messages.island-war.skybound-unavailable"));
            return;
        }
        me.reil.voidrift.islandwar.IslandHeart heart = plugin.getIslandWarManager().getOwnHeart(p);
        if (heart == null) {
            p.sendMessage(plugin.getLang().msgFor(p, "messages.island-war.no-own-heart"));
            return;
        }
        p.teleport(heart.getLocation().clone().add(0.5, 1, 0.5));
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("hp", String.valueOf(heart.getHp()));
        vars.put("max", String.valueOf(heart.getMaxHp()));
        p.sendMessage(plugin.getLang().msgFor(p, "messages.island-war.own-heart-location", vars));
    }

    private void handleStatus(Player p) {
        p.sendMessage(plugin.getLang().msgFor(p, "messages.status.header"));
        p.sendMessage(plugin.getLang().msgFor(p, plugin.getSkyBoundHook().isAvailable()
                ? "messages.status.mode-addon"
                : "messages.status.mode-standalone"));
        p.sendMessage(plugin.getLang().msgFor(p, plugin.getEliteMobsHook().isAvailable()
                ? "messages.status.elitemobs-on"
                : "messages.status.elitemobs-off"));
        p.sendMessage(plugin.getLang().msgFor(p, plugin.getFmmHook().isAvailable()
                ? "messages.status.fmm-on"
                : "messages.status.fmm-off"));
        p.sendMessage(plugin.getLang().msgFor(p, plugin.getMythicMobsHook().isAvailable()
                ? "messages.status.mythicmobs-on"
                : "messages.status.mythicmobs-off"));
        p.sendMessage(plugin.getLang().msgFor(p, plugin.getCitizensHook().isAvailable()
                ? "messages.status.citizens-on"
                : "messages.status.citizens-off"));
        p.sendMessage(plugin.getLang().msgFor(p, plugin.getSopCustomBlocksHook().isAvailable()
                ? "messages.status.sopblocks-on"
                : "messages.status.sopblocks-off"));
    }

    private void handleTop(Player p, String[] args) {
        String eventId;
        if (args.length >= 2) {
            eventId = args[1];
        } else {
            ActiveEvent playerEvent = getPlayerActiveEvent(p);
            eventId = playerEvent != null ? playerEvent.getDefinition().getId() : plugin.getLeaderboard().getFirstEventId();
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

        List<LeaderboardEntry> top = plugin.getLeaderboard().getTop(eventId);
        if (top.isEmpty()) {
            ActiveEvent active = plugin.getEventManager().getActiveEvent(eventId);
            if (active != null) {
                showActiveEventTop(p, active);
            } else {
                p.sendMessage(plugin.getLang().msgFor(p, "messages.event.no-leaderboard-for", vars("id", eventId)));
            }
            return;
        }

        EventDefinition def = plugin.getEventManager().getDefinition(eventId);
        String title = def != null ? def.getDisplayName() : eventId;
        Map<String, String> headerVars = vars("event", title);
        p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-header", headerVars));
        int rank = 1;
        for (LeaderboardEntry entry : top) {
            Map<String, String> entryVars = vars("rank", String.valueOf(rank));
            entryVars.put("player", entry.getPlayerName());
            entryVars.put("score", String.valueOf(entry.getScore()));
            p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-entry", entryVars));
            rank++;
        }
    }

    private void showActiveEventTop(Player p, ActiveEvent event) {
        List<Map.Entry<UUID, Integer>> top = event.getTopPlayers(10);
        Map<String, String> headerVars = vars("event", event.getDefinition().getDisplayName());
        p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-header", headerVars));
        if (top.isEmpty()) {
            p.sendMessage(plugin.getLang().msgFor(p, "messages.event.no-scores-yet"));
            return;
        }

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : top) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString().substring(0, 8);
            Map<String, String> entryVars = vars("rank", String.valueOf(rank));
            entryVars.put("player", name);
            entryVars.put("score", String.valueOf(entry.getValue()));
            p.sendMessage(plugin.getLang().msgFor(p, "messages.event.leaderboard-entry", entryVars));
            rank++;
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
        if (args.length == 1) {
            return filter(Arrays.asList("join", "leave", "list", "top", "gui", "attack", "heart", "status"), args[0]);
        }
        if (args.length == 2 && ("join".equalsIgnoreCase(args[0]) || "top".equalsIgnoreCase(args[0]))) {
            List<String> ids = new ArrayList<String>();
            for (ActiveEvent e : plugin.getEventManager().getActiveEvents()) ids.add(e.getDefinition().getId());
            return filter(ids, args[1]);
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

    private Map<String, String> vars(String key, String value) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(key, value);
        return vars;
    }
}
