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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventAdminCommand implements CommandExecutor, TabCompleter {

    private final VoidRiftPlugin plugin;

    public EventAdminCommand(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidrift.admin")) {
            sender.sendMessage(plugin.getLang().msg("messages.admin.no-permission"));
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start":
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-start")); return true; }
                plugin.getEventManager().previewAndStart(args[1]);
                sender.sendMessage(plugin.getLang().msg("messages.admin.start-countdown"));
                break;
            case "startnow":
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-startnow")); return true; }
                boolean ok = plugin.getEventManager().startEvent(args[1]);
                sender.sendMessage(ok ? plugin.getLang().msg("messages.admin.started") : plugin.getLang().msg("messages.admin.error"));
                break;
            case "stop":
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-stop")); return true; }
                plugin.getEventManager().previewAndStop(args[1]);
                sender.sendMessage(plugin.getLang().msg("messages.admin.stop-countdown"));
                break;
            case "stopnow":
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-stopnow")); return true; }
                boolean stopped = plugin.getEventManager().stopEvent(args[1]);
                sender.sendMessage(stopped ? plugin.getLang().msg("messages.admin.stopped") : plugin.getLang().msg("messages.admin.not-found"));
                break;
            case "reload":
                plugin.getEventsConfig().load();
                plugin.getZoneManager().reloadZones();
                plugin.getPortalManager().reloadPortals();
                plugin.getEventManager().reloadEvents();
                sender.sendMessage(plugin.getLang().msg("messages.admin.reloaded"));
                break;
            case "portal":
                handlePortal(sender, args);
                break;
            case "createevent":
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().msg("messages.admin.players-only")); break; }
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-createevent")); break; }
                plugin.getEventWizard().start((Player) sender, args[1]);
                break;
            case "setup":
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().msg("messages.admin.players-only")); break; }
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-setup")); break; }
                plugin.getSetupWizard().start((Player) sender, args[1]);
                break;
            case "setupzone":
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().msg("messages.admin.players-only")); break; }
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-setupzone")); break; }
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
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().msg("messages.admin.players-only")); return; }
        Player p = (Player) sender;

        if (args.length < 3) { sendPortalHelp(p); return; }

        String action = args[1].toLowerCase();
        String eventId = args[2];
        Location loc = p.getLocation();

        switch (action) {
            case "entry":
                plugin.getPortalManager().createPortal(eventId, "entry", PortalType.ENTRY);
                plugin.getPortalManager().setPortalLocation(eventId, "entry", loc);
                Map<String, String> entryVars = new HashMap<String, String>();
                entryVars.put("event", eventId);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.entry-set", entryVars));
                break;

            case "exit":
                plugin.getPortalManager().createPortal(eventId, "exit", PortalType.EXIT);
                plugin.getPortalManager().setPortalLocation(eventId, "exit", loc);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.exit-set"));
                break;

            case "dynamic":
                plugin.getPortalManager().createPortal(eventId, "entry", PortalType.DYNAMIC);
                plugin.getPortalManager().setPortalDestination(eventId, "entry", loc);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.dynamic-created"));
                Map<String, String> dynVars = new HashMap<String, String>();
                dynVars.put("event", eventId);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.dynamic-hint", dynVars));
                break;

            case "addpos":
                plugin.getPortalManager().addDynamicLocation(eventId, "entry", loc);
                Map<String, String> posVars = new HashMap<String, String>();
                posVars.put("x", String.valueOf((int) loc.getX()));
                posVars.put("y", String.valueOf((int) loc.getY()));
                posVars.put("z", String.valueOf((int) loc.getZ()));
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.addpos-done", posVars));
                break;

            case "dest":
                plugin.getPortalManager().setPortalDestination(eventId, "entry", loc);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.dest-set"));
                break;

            case "return":
                plugin.getPortalManager().setPortalDestination(eventId, "exit", loc);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.return-set"));
                break;

            case "next":
                if (args.length < 4) { p.sendMessage(plugin.getLang().msg("messages.admin-portal.usage-next")); return; }
                String nextId = "next_" + args[3];
                plugin.getPortalManager().createPortal(eventId, nextId, PortalType.INTERMEDIATE);
                plugin.getPortalManager().setPortalLocation(eventId, nextId, loc);
                Map<String, String> nextVars = new HashMap<String, String>();
                nextVars.put("id", args[3]);
                nextVars.put("event", eventId);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.next-set", nextVars));
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.next-hint", nextVars));
                break;

            case "nextdest":
                if (args.length < 4) { p.sendMessage(plugin.getLang().msg("messages.admin-portal.usage-nextdest")); return; }
                String nextDestId = "next_" + args[3];
                plugin.getPortalManager().setPortalDestination(eventId, nextDestId, loc);
                Map<String, String> ndVars = new HashMap<String, String>();
                ndVars.put("id", args[3]);
                p.sendMessage(plugin.getLang().msg("messages.admin-portal.nextdest-set", ndVars));
                break;

            default:
                sendPortalHelp(p);
                break;
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().msg("messages.admin.info-events-header"));
            for (me.reil.voidrift.event.EventDefinition def : plugin.getEventManager().getDefinitions()) {
                boolean active = plugin.getEventManager().getActiveEvent(def.getId()) != null;
                sender.sendMessage((active ? ChatColor.GREEN + "● " : ChatColor.GRAY + "○ ") + ChatColor.YELLOW + def.getId() + ChatColor.GRAY + " - " + def.getDisplayName());
            }
            sender.sendMessage(plugin.getLang().msg("messages.admin.info-portals-header"));
            for (String eventId : plugin.getPortalManager().getAllEventIds()) {
                Collection<PortalDefinition> portals = plugin.getPortalManager().getPortals(eventId);
                for (PortalDefinition portal : portals) {
                    Location loc = portal.getActiveLocation();
                    String locStr = loc != null ? (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ() : plugin.getLang().msg("messages.admin.info-loc-not-set");
                    sender.sendMessage(ChatColor.GRAY + "  " + eventId + "." + portal.getId() + " [" + portal.getType().name() + "] @ " + locStr);
                }
            }
            return;
        }
        String eventId = args[1];
        me.reil.voidrift.event.ActiveEvent active = plugin.getEventManager().getActiveEvent(eventId);
        me.reil.voidrift.event.EventDefinition def = plugin.getEventManager().getDefinition(eventId);
        if (def == null) {
            Map<String, String> vars = new HashMap<String, String>();
            vars.put("id", eventId);
            sender.sendMessage(plugin.getLang().msg("messages.admin.not-found-id", vars));
            return;
        }
        Map<String, String> v = new HashMap<String, String>();
        v.put("name", def.getDisplayName());
        v.put("id", def.getId());
        v.put("type", def.getType().name());
        v.put("duration", String.valueOf(def.getDurationSeconds()));
        v.put("interval", String.valueOf(def.getIntervalSeconds()));
        sender.sendMessage(plugin.getLang().msg("messages.admin.info-header", v));
        sender.sendMessage(plugin.getLang().msg("messages.admin.info-id-type", v));
        sender.sendMessage(plugin.getLang().msg("messages.admin.info-duration-interval", v));
        if (active != null) {
            Map<String, String> av = new HashMap<String, String>();
            av.put("remaining", String.valueOf(active.getRemainingSeconds()));
            av.put("players", String.valueOf(active.getParticipants().size()));
            sender.sendMessage(plugin.getLang().msg("messages.admin.info-active", av));
        }
        String noStr = plugin.getLang().msg("messages.admin.info-no");
        Collection<PortalDefinition> portals = plugin.getPortalManager().getPortals(eventId);
        for (PortalDefinition portal : portals) {
            Location loc = portal.getActiveLocation();
            Location dest = portal.getDestination();
            String locStr = loc != null ? (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ() : noStr;
            String destStr = dest != null ? (int)dest.getX() + "," + (int)dest.getY() + "," + (int)dest.getZ() : noStr;
            sender.sendMessage(ChatColor.GRAY + "  " + portal.getId() + " [" + portal.getType().name() + "] pos=" + locStr + " dest=" + destStr);
        }
    }

    private void sendPortalHelp(Player p) {
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-header"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-entry"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-dynamic"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-addpos"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-dest"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-exit"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-return"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-next"));
        p.sendMessage(plugin.getLang().msg("messages.admin-portal.help-nextdest"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.header"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.start"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.startnow"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.stop"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.stopnow"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.portal"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.setup"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.setupzone"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.createevent"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.info"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.reload"));
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
