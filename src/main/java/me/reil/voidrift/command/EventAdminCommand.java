package me.reil.voidrift.command;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.portal.PortalDefinition;
import me.reil.voidrift.portal.PortalType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * /riftadmin (alias: /evadmin)
 *
 * Simplified portal commands:
 *   /riftadmin portal entry <event>                — set static entry portal at your pos
 *   /riftadmin portal exit <event>                 — set exit portal at your pos
 *   /riftadmin portal dynamic <event>              — set dynamic entry (dest = your pos)
 *   /riftadmin portal addpos <event>               — add dynamic spawn position
 *   /riftadmin portal next <event> <id>            — set intermediate entry at your pos
 *   /riftadmin portal nextdest <event> <id>        — set intermediate destination at your pos
 *   /riftadmin portal dest <event>                 — set where exit returns players
 *
 * Event commands:
 *   /riftadmin start <event>       — start with countdown
 *   /riftadmin startnow <event>    — start instantly
 *   /riftadmin stop <event>        — stop with countdown
 *   /riftadmin stopnow <event>     — stop instantly
 *   /riftadmin info [event]        — info
 *   /riftadmin reload              — reload configs
 */
public final class EventAdminCommand implements CommandExecutor, TabCompleter {

    private final VoidRiftPlugin plugin;

    public EventAdminCommand(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidrift.admin")) {
            sender.sendMessage(ChatColor.RED + "\u041d\u0435\u0442 \u043f\u0440\u0430\u0432.");
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start":
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin start <event>"); return true; }
                plugin.getEventManager().previewAndStart(args[1]);
                sender.sendMessage(ChatColor.GREEN + "\u0417\u0430\u043f\u0443\u0441\u043a \u0441 \u043e\u0442\u0441\u0447\u0451\u0442\u043e\u043c...");
                break;
            case "startnow":
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin startnow <event>"); return true; }
                boolean ok = plugin.getEventManager().startEvent(args[1]);
                sender.sendMessage(ok ? ChatColor.GREEN + "\u0417\u0430\u043f\u0443\u0449\u0435\u043d\u043e." : ChatColor.RED + "\u041e\u0448\u0438\u0431\u043a\u0430.");
                break;
            case "stop":
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin stop <event>"); return true; }
                plugin.getEventManager().previewAndStop(args[1]);
                sender.sendMessage(ChatColor.GREEN + "\u041e\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0430 \u0447\u0435\u0440\u0435\u0437 10\u0441...");
                break;
            case "stopnow":
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin stopnow <event>"); return true; }
                boolean stopped = plugin.getEventManager().stopEvent(args[1]);
                sender.sendMessage(stopped ? ChatColor.GREEN + "\u041e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e." : ChatColor.RED + "\u041d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e.");
                break;
            case "reload":
                plugin.getEventsConfig().load();
                plugin.getZoneManager().reloadZones();
                plugin.getPortalManager().reloadPortals();
                plugin.getEventManager().reloadEvents();
                sender.sendMessage(ChatColor.GREEN + "\u041f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u043e.");
                break;
            case "portal":
                handlePortal(sender, args);
                break;
            case "createevent":
                if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "\u0422\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a\u0438."); break; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin createevent <id>"); break; }
                plugin.getEventWizard().start((Player) sender, args[1]);
                break;
            case "setup":
                if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "\u0422\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a\u0438."); break; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin setup <event>"); break; }
                plugin.getSetupWizard().start((Player) sender, args[1]);
                break;
            case "setupzone":
                if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "\u0422\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a\u0438."); break; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "/riftadmin setupzone <zone-id>"); break; }
                plugin.getZoneWizard().start((Player) sender, args[1]);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handlePortal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "\u0422\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a\u0438."); return; }
        Player p = (Player) sender;

        if (args.length < 3) { sendPortalHelp(p); return; }

        String action = args[1].toLowerCase();
        String eventId = args[2];
        Location loc = p.getLocation();

        switch (action) {
            case "entry":
                // Set static entry portal at player pos, dest = player pos (will be overridden by dest command)
                plugin.getPortalManager().createPortal(eventId, "entry", PortalType.ENTRY);
                plugin.getPortalManager().setPortalLocation(eventId, "entry", loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u0412\u0445\u043e\u0434 \u0432 \u0435\u0432\u0435\u043d\u0442 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d. \u0422\u0435\u043f\u0435\u0440\u044c \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435: /riftadmin portal dest " + eventId);
                break;

            case "exit":
                // Set exit portal at player pos
                plugin.getPortalManager().createPortal(eventId, "exit", PortalType.EXIT);
                plugin.getPortalManager().setPortalLocation(eventId, "exit", loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u0412\u044b\u0445\u043e\u0434 \u0438\u0437 \u0435\u0432\u0435\u043d\u0442\u0430 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d.");
                break;

            case "dynamic":
                // Set dynamic entry, destination = player pos (where players teleport TO)
                plugin.getPortalManager().createPortal(eventId, "entry", PortalType.DYNAMIC);
                plugin.getPortalManager().setPortalDestination(eventId, "entry", loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u0414\u0438\u043d\u0430\u043c\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0432\u0445\u043e\u0434 \u0441\u043e\u0437\u0434\u0430\u043d. \u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 = \u0442\u0432\u043e\u044f \u043f\u043e\u0437\u0438\u0446\u0438\u044f.");
                p.sendMessage(ChatColor.YELLOW + "\u0414\u043e\u0431\u0430\u0432\u044c \u0442\u043e\u0447\u043a\u0438 \u043f\u043e\u044f\u0432\u043b\u0435\u043d\u0438\u044f: /riftadmin portal addpos " + eventId);
                break;

            case "addpos":
                // Add dynamic position for entry portal
                plugin.getPortalManager().addDynamicLocation(eventId, "entry", loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u0422\u043e\u0447\u043a\u0430 \u043f\u043e\u044f\u0432\u043b\u0435\u043d\u0438\u044f \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u0430 (" + (int)loc.getX() + ", " + (int)loc.getY() + ", " + (int)loc.getZ() + ")");
                break;

            case "dest":
                // Set destination for entry portal (where players teleport to = event location)
                plugin.getPortalManager().setPortalDestination(eventId, "entry", loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u0432\u0445\u043e\u0434\u0430 = \u0442\u0432\u043e\u044f \u043f\u043e\u0437\u0438\u0446\u0438\u044f (\u0435\u0432\u0435\u043d\u0442-\u043b\u043e\u043a\u0430\u0446\u0438\u044f).");
                break;

            case "return":
                // Set where exit portal returns players (override default)
                plugin.getPortalManager().setPortalDestination(eventId, "exit", loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u0422\u043e\u0447\u043a\u0430 \u0432\u043e\u0437\u0432\u0440\u0430\u0442\u0430 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0430.");
                break;

            case "next":
                // Intermediate portal entry point
                if (args.length < 4) { p.sendMessage(ChatColor.RED + "/riftadmin portal next <event> <id>"); return; }
                String nextId = "next_" + args[3];
                plugin.getPortalManager().createPortal(eventId, nextId, PortalType.INTERMEDIATE);
                plugin.getPortalManager().setPortalLocation(eventId, nextId, loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u041f\u0440\u043e\u043c\u0435\u0436\u0443\u0442\u043e\u0447\u043d\u044b\u0439 \u043f\u043e\u0440\u0442\u0430\u043b " + args[3] + " \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d.");
                p.sendMessage(ChatColor.YELLOW + "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435: /riftadmin portal nextdest " + eventId + " " + args[3]);
                break;

            case "nextdest":
                // Intermediate portal destination
                if (args.length < 4) { p.sendMessage(ChatColor.RED + "/riftadmin portal nextdest <event> <id>"); return; }
                String nextDestId = "next_" + args[3];
                plugin.getPortalManager().setPortalDestination(eventId, nextDestId, loc);
                p.sendMessage(ChatColor.GREEN + "\u2714 \u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 " + args[3] + " \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e.");
                break;

            default:
                sendPortalHelp(p);
                break;
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "\u0421\u043e\u0431\u044b\u0442\u0438\u044f:");
            for (me.reil.voidrift.event.EventDefinition def : plugin.getEventManager().getDefinitions()) {
                boolean active = plugin.getEventManager().getActiveEvent(def.getId()) != null;
                sender.sendMessage((active ? ChatColor.GREEN + "\u25CF " : ChatColor.GRAY + "\u25CB ") + ChatColor.YELLOW + def.getId() + ChatColor.GRAY + " - " + def.getDisplayName());
            }
            sender.sendMessage(ChatColor.GRAY + "\u041f\u043e\u0440\u0442\u0430\u043b\u044b:");
            for (String eventId : plugin.getPortalManager().getAllEventIds()) {
                Collection<PortalDefinition> portals = plugin.getPortalManager().getPortals(eventId);
                for (PortalDefinition portal : portals) {
                    Location loc = portal.getActiveLocation();
                    String locStr = loc != null ? (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ() : "\u043d\u0435 \u0443\u0441\u0442.";
                    sender.sendMessage(ChatColor.GRAY + "  " + eventId + "." + portal.getId() + " [" + portal.getType().name() + "] @ " + locStr);
                }
            }
            return;
        }
        String eventId = args[1];
        me.reil.voidrift.event.ActiveEvent active = plugin.getEventManager().getActiveEvent(eventId);
        me.reil.voidrift.event.EventDefinition def = plugin.getEventManager().getDefinition(eventId);
        if (def == null) { sender.sendMessage(ChatColor.RED + "\u041d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e: " + eventId); return; }
        sender.sendMessage(ChatColor.GOLD + "=== " + def.getDisplayName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "ID: " + def.getId() + ", \u0422\u0438\u043f: " + def.getType().name());
        sender.sendMessage(ChatColor.GRAY + "\u0414\u043b\u0438\u0442.: " + def.getDurationSeconds() + "\u0441, \u0418\u043d\u0442\u0435\u0440\u0432\u0430\u043b: " + def.getIntervalSeconds() + "\u0441");
        if (active != null) {
            sender.sendMessage(ChatColor.GREEN + "\u0410\u043a\u0442\u0438\u0432\u0435\u043d! \u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c: " + active.getRemainingSeconds() + "\u0441, \u0438\u0433\u0440\u043e\u043a\u043e\u0432: " + active.getParticipants().size());
        }
        Collection<PortalDefinition> portals = plugin.getPortalManager().getPortals(eventId);
        for (PortalDefinition portal : portals) {
            Location loc = portal.getActiveLocation();
            Location dest = portal.getDestination();
            String locStr = loc != null ? (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ() : "\u043d\u0435\u0442";
            String destStr = dest != null ? (int)dest.getX() + "," + (int)dest.getY() + "," + (int)dest.getZ() : "\u043d\u0435\u0442";
            sender.sendMessage(ChatColor.GRAY + "  " + portal.getId() + " [" + portal.getType().name() + "] pos=" + locStr + " dest=" + destStr);
        }
    }

    private void sendPortalHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043f\u043e\u0440\u0442\u0430\u043b\u043e\u0432:");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal entry <event>" + ChatColor.GRAY + " \u2014 \u0441\u0442\u0430\u0442\u0438\u0447\u043d\u044b\u0439 \u0432\u0445\u043e\u0434");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal dynamic <event>" + ChatColor.GRAY + " \u2014 \u0434\u0438\u043d\u0430\u043c\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0432\u0445\u043e\u0434");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal addpos <event>" + ChatColor.GRAY + " \u2014 \u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 \u043f\u043e\u044f\u0432\u043b\u0435\u043d\u0438\u044f");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal dest <event>" + ChatColor.GRAY + " \u2014 \u043a\u0443\u0434\u0430 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0438\u0440\u0443\u0435\u0442 \u0432\u0445\u043e\u0434");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal exit <event>" + ChatColor.GRAY + " \u2014 \u043f\u043e\u0440\u0442\u0430\u043b \u0432\u044b\u0445\u043e\u0434\u0430");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal return <event>" + ChatColor.GRAY + " \u2014 \u0442\u043e\u0447\u043a\u0430 \u0432\u043e\u0437\u0432\u0440\u0430\u0442\u0430");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal next <event> <id>" + ChatColor.GRAY + " \u2014 \u043f\u0440\u043e\u043c\u0435\u0436\u0443\u0442\u043e\u0447\u043d\u044b\u0439");
        p.sendMessage(ChatColor.YELLOW + "  /riftadmin portal nextdest <event> <id>" + ChatColor.GRAY + " \u2014 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u043f\u0440\u043e\u043c.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "VoidRift Admin:");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin start <event>" + ChatColor.GRAY + " \u2014 \u0441 \u043e\u0442\u0441\u0447\u0451\u0442\u043e\u043c");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin startnow <event>" + ChatColor.GRAY + " \u2014 \u043c\u0433\u043d\u043e\u0432\u0435\u043d\u043d\u043e");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin stop <event>" + ChatColor.GRAY + " \u2014 \u0441 \u043e\u0442\u0441\u0447\u0451\u0442\u043e\u043c");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin stopnow <event>" + ChatColor.GRAY + " \u2014 \u043c\u0433\u043d\u043e\u0432\u0435\u043d\u043d\u043e");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin portal ..." + ChatColor.GRAY + " \u2014 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043f\u043e\u0440\u0442\u0430\u043b\u043e\u0432");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin setup <event>" + ChatColor.GRAY + " \u2014 \u0432\u0438\u0437\u0430\u0440\u0434 \u043f\u043e\u0440\u0442\u0430\u043b\u043e\u0432");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin setupzone <zone>" + ChatColor.GRAY + " \u2014 \u0432\u0438\u0437\u0430\u0440\u0434 \u0437\u043e\u043d\u044b");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin createevent <id>" + ChatColor.GRAY + " \u2014 \u0441\u043e\u0437\u0434\u0430\u0442\u044c \u0441\u043e\u0431\u044b\u0442\u0438\u0435");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin info [event]" + ChatColor.GRAY + " \u2014 \u0438\u043d\u0444\u043e");
        sender.sendMessage(ChatColor.YELLOW + "  /riftadmin reload" + ChatColor.GRAY + " \u2014 \u043f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("start", "startnow", "stop", "stopnow", "reload", "info", "portal", "setup", "setupzone", "createevent"), args[0]);
        if (args.length == 2) {
            if ("portal".equals(args[0])) return filter(Arrays.asList("entry", "exit", "dynamic", "addpos", "dest", "return", "next", "nextdest"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> r = new ArrayList<String>();
        for (String s : options) if (s.toLowerCase().startsWith(prefix.toLowerCase())) r.add(s);
        return r;
    }
}
