package me.reil.voidrift.command;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.objective.Objective;
import me.reil.voidrift.objective.ObjectiveType;
import me.reil.voidrift.portal.PortalDefinition;
import me.reil.voidrift.portal.PortalType;
import me.reil.voidrift.zone.ZoneDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
                if (!validateBeforeStart(sender, args[1])) return true;
                plugin.getEventManager().previewAndStart(args[1]);
                sender.sendMessage(plugin.getLang().msg("messages.admin.start-countdown"));
                break;
            case "startnow":
                if (args.length < 2) { sender.sendMessage(plugin.getLang().msg("messages.admin.usage-startnow")); return true; }
                if (!validateBeforeStart(sender, args[1])) return true;
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
            case "doctor":
                handleDoctor(sender);
                break;
            case "validate":
            case "valid":
            case "val":
            case "check":
            case "проверить":
            case "валидате":
                handleValidate(sender, args);
                break;
            case "template":
                handleTemplate(sender, args);
                break;
            case "zonetemplate":
                handleZoneTemplate(sender, args);
                break;
            case "quickstart":
                handleQuickStart(sender);
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

    private boolean validateBeforeStart(CommandSender sender, String eventId) {
        me.reil.voidrift.event.EventDefinition def = plugin.getEventManager().getDefinition(eventId);
        if (def == null) {
            sender.sendMessage(ChatColor.RED + "Event not found: " + eventId);
            return false;
        }
        List<String> issues = collectEventIssues(def, true);
        if (issues.isEmpty()) return true;
        sender.sendMessage(ChatColor.RED + "Cannot start event '" + eventId + "':");
        for (String issue : issues) {
            sender.sendMessage(ChatColor.RED + "  - " + issue);
        }
        return false;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length >= 2 && "zone".equalsIgnoreCase(args[1])) {
            handleZoneInfo(sender, args);
            return;
        }
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

    private void handleZoneInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "/riftadmin info zone <zone-id>");
            sender.sendMessage(ChatColor.GRAY + "Zones:");
            for (ZoneDefinition zone : plugin.getZoneManager().getZones()) {
                sender.sendMessage(ChatColor.YELLOW + "  " + zone.getId());
            }
            return;
        }

        ZoneDefinition zone = plugin.getZoneManager().getZone(args[2]);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "Zone not found: " + args[2]);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Zone: " + zone.getId() + " ===");
        sender.sendMessage(ChatColor.GRAY + "World: " + ChatColor.YELLOW + zone.getWorld());
        sender.sendMessage(ChatColor.GRAY + "Areas: " + ChatColor.YELLOW + zone.getAreas().size());
        int idx = 1;
        for (ZoneDefinition.Area area : zone.getAreas()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  area" + idx
                    + ChatColor.GRAY + " pos1=" + loc(area.getPos1())
                    + " pos2=" + loc(area.getPos2()));
            idx++;
        }
        sender.sendMessage(ChatColor.GRAY + "Spawn points: " + ChatColor.YELLOW + zone.getSpawnPoints().size());
        for (ZoneDefinition.SpawnPoint sp : zone.getSpawnPoints()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  " + sp.getName() + ChatColor.GRAY + " @ " + loc(sp.getLocation()));
        }
        sender.sendMessage(ChatColor.GRAY + "Mob pool: " + ChatColor.YELLOW + zone.getMobPools().size());
        for (ZoneDefinition.MobPool mob : zone.getMobPools()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  " + mob.getMobType()
                    + ChatColor.GRAY + " id=" + mob.getMobId()
                    + " model=" + (mob.getModelId() == null ? "-" : mob.getModelId())
                    + " wave=" + mob.getWave()
                    + " weight=" + mob.getWeight());
        }
        sender.sendMessage(ChatColor.GRAY + "Max mobs: " + ChatColor.YELLOW + zone.getMaxMobs());

        List<String> issues = plugin.getEventManager().validateZone(zone, true);
        issues.addAll(validateZoneMobs(zone, true));
        if (issues.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Status: OK");
        } else {
            sender.sendMessage(ChatColor.RED + "Issues:");
            for (String issue : issues) {
                sender.sendMessage(ChatColor.RED + "  - " + issue);
            }
        }
    }

    private String loc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "not set";
        return loc.getWorld().getName() + " "
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void handleDoctor(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== VoidRift Doctor ===");
        sender.sendMessage(ChatColor.GRAY + "Mode: " + (plugin.getSkyBoundHook().isAvailable()
                ? ChatColor.GREEN + "SkyBound addon"
                : ChatColor.YELLOW + "Standalone"));

        boolean ok = true;
        ok &= dependency(sender, "SopLib", true, org.bukkit.Bukkit.getPluginManager().isPluginEnabled("SopLib"));
        ok &= dependency(sender, "EliteMobs", true, plugin.getEliteMobsHook().isAvailable());
        ok &= dependency(sender, "FreeMinecraftModels", true, plugin.getFmmHook().isAvailable());
        dependency(sender, "SkyBound", false, plugin.getSkyBoundHook().isAvailable());
        dependency(sender, "Vault", false, org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Vault"));
        dependency(sender, "PlaceholderAPI", false, org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        dependency(sender, "MythicMobs", false, plugin.getMythicMobsHook().isAvailable());
        dependency(sender, "Citizens", false, plugin.getCitizensHook().isAvailable());
        dependency(sender, "SopCustomBlocks", false, plugin.getSopCustomBlocksHook().isAvailable());

        List<String> runtimeIssues = validateRuntimeSetup();
        if (!runtimeIssues.isEmpty()) {
            ok = false;
            sender.sendMessage(ChatColor.RED + "Runtime/config issues:");
            for (String issue : runtimeIssues) sender.sendMessage(ChatColor.RED + "  - " + issue);
        }

        int events = plugin.getEventManager().getDefinitions().size();
        int zones = plugin.getZoneManager().getZones().size();
        int portalEvents = plugin.getPortalManager().getAllEventIds().size();
        sender.sendMessage(ChatColor.GRAY + "Events: " + colorCount(events));
        sender.sendMessage(ChatColor.GRAY + "Zones: " + colorCount(zones));
        sender.sendMessage(ChatColor.GRAY + "Portal configs: " + colorCount(portalEvents));

        if (events == 0) {
            ok = false;
            sender.sendMessage(ChatColor.RED + "  - Нет событий. Создай: /riftadmin createevent <id>");
        }
        if (zones == 0) {
            sender.sendMessage(ChatColor.YELLOW + "  - Нет зон. Для обычных событий создай: /riftadmin setupzone <id>");
        }

        int brokenEvents = 0;
        for (me.reil.voidrift.event.EventDefinition def : plugin.getEventManager().getDefinitions()) {
            List<String> issues = collectEventIssues(def, true);
            if (!issues.isEmpty()) {
                brokenEvents++;
                sender.sendMessage(ChatColor.RED + "Event " + def.getId() + " has issues:");
                for (String issue : issues) sender.sendMessage(ChatColor.RED + "  - " + issue);
            }
            List<String> warnings = collectEventWarnings(def);
            if (!warnings.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Event " + def.getId() + " warnings:");
                for (String warning : warnings) sender.sendMessage(ChatColor.YELLOW + "  - " + warning);
            }
        }

        int brokenZones = 0;
        for (ZoneDefinition zone : plugin.getZoneManager().getZones()) {
            List<String> issues = plugin.getEventManager().validateZone(zone, false);
            issues.addAll(validateZoneMobs(zone, false));
            if (!issues.isEmpty()) {
                brokenZones++;
                sender.sendMessage(ChatColor.RED + "Zone " + zone.getId() + " has issues:");
                for (String issue : issues) sender.sendMessage(ChatColor.RED + "  - " + issue);
            }
        }

        if (brokenEvents > 0 || brokenZones > 0) ok = false;
        sender.sendMessage(ok
                ? ChatColor.GREEN + "Doctor result: OK. Можно переходить к тестам."
                : ChatColor.YELLOW + "Doctor result: есть проблемы. Исправь список выше и проверь /riftadmin validate all.");
    }

    private boolean dependency(CommandSender sender, String name, boolean required, boolean available) {
        ChatColor color = available ? ChatColor.GREEN : (required ? ChatColor.RED : ChatColor.YELLOW);
        String status = available ? "OK" : (required ? "MISSING" : "optional/off");
        sender.sendMessage(ChatColor.GRAY + "- " + name + ": " + color + status);
        return available || !required;
    }

    private String colorCount(int value) {
        return (value > 0 ? ChatColor.GREEN : ChatColor.YELLOW).toString() + value;
    }

    private List<String> validateRuntimeSetup() {
        List<String> issues = new ArrayList<String>();

        checkResourceFile(issues, "config.yml");
        checkResourceFile(issues, "events.yml");
        checkResourceFile(issues, "zones.yml");
        checkResourceFile(issues, "portals.yml");
        checkResourceFile(issues, "lang.yml");

        checkCommand(issues, "event");
        checkCommand(issues, "eventadmin");

        checkPermission(issues, "voidrift.admin");
        checkPermission(issues, "voidrift.join");
        checkPermission(issues, "voidrift.build");

        checkSection(issues, "arena-protection");
        checkSection(issues, "auto-start");
        checkSection(issues, "portal");
        checkSection(issues, "portal.particles");
        checkSection(issues, "sounds");
        checkSection(issues, "modifiers");
        checkSection(issues, "display");
        checkSection(issues, "rewards");
        checkSection(issues, "scaling");
        checkSection(issues, "loot-chests");
        checkSection(issues, "integrations");
        checkLangKeys(issues);

        if (plugin.getConfig().getInt("max-active-events", 1) < 1) {
            issues.add("config.yml max-active-events must be at least 1");
        }
        if (plugin.getConfig().getLong("rewards.cooldown-seconds", 0L) < 0L) {
            issues.add("config.yml rewards.cooldown-seconds cannot be negative");
        }
        if (plugin.getConfig().getInt("modifiers.max-count", 0) < 0) {
            issues.add("config.yml modifiers.max-count cannot be negative");
        }
        if (plugin.getConfig().getInt("loot-chests.random.count", 0) < 0) {
            issues.add("config.yml loot-chests.random.count cannot be negative");
        }

        validateParticle(issues, "portal.particles.entry");
        validateParticle(issues, "portal.particles.exit");
        validateParticle(issues, "portal.particles.intermediate");
        validateParticle(issues, "portal.particles.dynamic");
        validateParticle(issues, "loot-chests.random.particle");
        validateParticle(issues, "loot-chests.boss.particle");

        validateSound(issues, "sounds.event-start.sound");
        validateSound(issues, "sounds.event-end.sound");
        validateSound(issues, "sounds.wave-clear.sound");
        validateSound(issues, "sounds.boss-spawn.sound");
        validateSound(issues, "sounds.objective-complete.sound");
        validateSound(issues, "sounds.portal-enter.sound");
        validateSound(issues, "sounds.portal-exit.sound");

        return issues;
    }

    private void checkResourceFile(List<String> issues, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            issues.add(name + " is missing in plugin data folder");
        } else if (!file.isFile()) {
            issues.add(name + " exists but is not a file");
        } else if (!file.canRead()) {
            issues.add(name + " cannot be read");
        }
    }

    private void checkCommand(List<String> issues, String name) {
        if (plugin.getCommand(name) == null) {
            issues.add("plugin.yml command is missing or not registered: " + name);
        }
    }

    private void checkPermission(List<String> issues, String name) {
        boolean found = false;
        for (org.bukkit.permissions.Permission permission : plugin.getDescription().getPermissions()) {
            if (permission.getName().equalsIgnoreCase(name)) {
                found = true;
                break;
            }
        }
        if (!found) {
            issues.add("plugin.yml permission is missing: " + name);
        }
    }

    private void checkSection(List<String> issues, String path) {
        if (!plugin.getConfig().isConfigurationSection(path)) {
            issues.add("config.yml section is missing: " + path);
        }
    }

    private void checkLangKeys(List<String> issues) {
        String[] keys = new String[] {
                "messages.players-only",
                "messages.portal.enter",
                "messages.portal.exit",
                "messages.portal.blocked-cmd",
                "messages.event.started",
                "messages.event.ending",
                "messages.event.ended",
                "messages.event.join-success",
                "messages.event.join-fail",
                "messages.event.exit-through-portal",
                "messages.objective.complete-title",
                "messages.reward.title",
                "messages.reward.money",
                "messages.reward.score",
                "messages.reward.cooldown",
                "messages.loot.boss-chest-spawned",
                "messages.loot.boss-loot-received",
                "messages.loot.already-looted",
                "messages.display.objectives-label",
                "messages.display.score-label",
                "messages.display.players-label",
                "messages.admin.no-permission",
                "messages.admin.usage-start",
                "messages.admin.usage-startnow",
                "messages.admin.usage-stop",
                "messages.admin.usage-stopnow",
                "messages.admin.usage-setup",
                "messages.admin.usage-setupzone",
                "messages.admin.usage-createevent",
                "messages.admin.reloaded",
                "messages.admin-help.header",
                "messages.admin-help.template",
                "messages.admin-help.zonetemplate",
                "messages.admin-help.quickstart",
                "messages.admin-help.doctor",
                "messages.admin-help.validate",
                "messages.admin-portal.entry-set",
                "messages.admin-portal.exit-set",
                "messages.admin-portal.dest-set",
                "messages.admin-portal.return-set",
                "messages.admin-quickstart.header",
                "messages.admin-quickstart.step1",
                "messages.admin-quickstart.cmd1",
                "messages.admin-quickstart.step2",
                "messages.admin-quickstart.cmd2",
                "messages.admin-quickstart.step3",
                "messages.admin-quickstart.cmd3",
                "messages.admin-quickstart.step4",
                "messages.admin-quickstart.cmd4",
                "messages.admin-quickstart.step5",
                "messages.admin-quickstart.cmd5",
                "messages.admin-quickstart.doctor",
                "messages.admin-quickstart.doctor-cmd",
                "messages.admin-template.usage",
                "messages.admin-template.example",
                "messages.admin-template.unknown",
                "messages.admin-template.available",
                "messages.admin-template.invalid-id",
                "messages.admin-template.exists",
                "messages.admin-template.save-failed",
                "messages.admin-template.created",
                "messages.admin-template.type",
                "messages.admin-template.zone",
                "messages.admin-template.no-zone",
                "messages.admin-template.next-zone",
                "messages.admin-template.next-setup",
                "messages.admin-template.check",
                "messages.admin-zone-template.usage",
                "messages.admin-zone-template.example",
                "messages.admin-zone-template.unknown",
                "messages.admin-zone-template.available",
                "messages.admin-zone-template.invalid-id",
                "messages.admin-zone-template.exists",
                "messages.admin-zone-template.radius-number",
                "messages.admin-zone-template.radius-range",
                "messages.admin-zone-template.save-failed",
                "messages.admin-zone-template.created",
                "messages.admin-zone-template.type",
                "messages.admin-zone-template.world",
                "messages.admin-zone-template.radius",
                "messages.admin-zone-template.check",
                "messages.admin-zone-template.create-event",
                "messages.wizard.title",
                "messages.wizard.step-entry",
                "messages.wizard.step-dest",
                "messages.wizard.step-exit",
                "messages.wizard.finished",
                "messages.zone-wizard.title",
                "messages.zone-wizard.step-pos1",
                "messages.zone-wizard.step-pos2",
                "messages.zone-wizard.step-spawns",
                "messages.zone-wizard.step-mobs",
                "messages.zone-wizard.step-maxmobs",
                "messages.event-wizard.title",
                "messages.event-wizard.finished",
                "messages.arena-protection.no-build"
        };
        for (String key : keys) {
            if (!plugin.getLang().has(key)) {
                issues.add("lang.yml key is missing: " + key);
            }
        }
    }

    private void validateParticle(List<String> issues, String path) {
        String value = plugin.getConfig().getString(path, "");
        if (value == null || value.trim().isEmpty()) {
            issues.add("config.yml " + path + " is empty");
            return;
        }
        try {
            org.bukkit.Particle.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            issues.add("config.yml " + path + " has unknown particle: " + value);
        }
    }

    private void validateSound(List<String> issues, String path) {
        String value = plugin.getConfig().getString(path, "");
        if (value == null || value.trim().isEmpty()) {
            issues.add("config.yml " + path + " is empty");
            return;
        }
        try {
            org.bukkit.Sound.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            issues.add("config.yml " + path + " has unknown sound: " + value);
        }
    }

    private void handleValidate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "/riftadmin validate <event-id|all>");
            return;
        }

        if ("all".equalsIgnoreCase(args[1])) {
            int checked = 0;
            int broken = 0;
            sender.sendMessage(ChatColor.GOLD + "=== VoidRift Validate All ===");
            for (me.reil.voidrift.event.EventDefinition def : plugin.getEventManager().getDefinitions()) {
                checked++;
                List<String> issues = collectEventIssues(def, true);
                if (issues.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "✔ " + def.getId());
                } else {
                    broken++;
                    sender.sendMessage(ChatColor.RED + "✖ " + def.getId());
                    for (String issue : issues) sender.sendMessage(ChatColor.RED + "  - " + issue);
                }
                List<String> warnings = collectEventWarnings(def);
                if (!warnings.isEmpty()) {
                    for (String warning : warnings) sender.sendMessage(ChatColor.YELLOW + "  ! " + warning);
                }
            }
            sender.sendMessage((broken == 0 ? ChatColor.GREEN : ChatColor.YELLOW)
                    + "Checked " + checked + " events, issues: " + broken);
            return;
        }

        me.reil.voidrift.event.EventDefinition def = plugin.getEventManager().getDefinition(args[1]);
        if (def == null) {
            sender.sendMessage(ChatColor.RED + "Event not found: " + args[1]);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Validate: " + def.getId() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + def.getType().name());
        sender.sendMessage(ChatColor.GRAY + "Zone: " + ChatColor.YELLOW + (def.getZoneId() == null || def.getZoneId().isEmpty() ? "-" : def.getZoneId()));
        sender.sendMessage(ChatColor.GRAY + "Players: " + ChatColor.YELLOW + def.getMinPlayers() + "-" + def.getMaxPlayers());
        sender.sendMessage(ChatColor.GRAY + "Duration: " + ChatColor.YELLOW + def.getDurationSeconds() + "s");

        List<String> issues = collectEventIssues(def, true);
        if (issues.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Status: OK. Event can start.");
        } else {
            sender.sendMessage(ChatColor.RED + "Issues:");
            for (String issue : issues) sender.sendMessage(ChatColor.RED + "  - " + issue);
        }
        List<String> warnings = collectEventWarnings(def);
        if (!warnings.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Warnings:");
            for (String warning : warnings) sender.sendMessage(ChatColor.YELLOW + "  - " + warning);
        }
    }

    private List<String> collectEventIssues(me.reil.voidrift.event.EventDefinition def, boolean includePortals) {
        List<String> issues = new ArrayList<String>();
        if (!def.isEnabled()) {
            issues.add("event is disabled (events.yml enabled: false)");
        }
        issues.addAll(plugin.getEventManager().validateEvent(def));
        issues.addAll(validateObjectives(def));
        issues.addAll(validateRewards(def));
        ZoneDefinition zone = def.getZoneId() == null ? null : plugin.getZoneManager().getZone(def.getZoneId());
        if (zone != null) {
            issues.addAll(validateZoneMobs(zone, def.getType() == me.reil.voidrift.event.EventType.WAVE_SURVIVAL
                    || def.getType() == me.reil.voidrift.event.EventType.BOSS_FIGHT));
        }

        if (includePortals) {
            Collection<PortalDefinition> portals = plugin.getPortalManager().getPortals(def.getId());
            boolean hasEntry = false;
            boolean hasExit = false;
            for (PortalDefinition portal : portals) {
                if ("entry".equalsIgnoreCase(portal.getId())) {
                    hasEntry = true;
                    if (portal.getType() == me.reil.voidrift.portal.PortalType.DYNAMIC && portal.getDynamicLocations().isEmpty()) {
                        issues.add("entry portal is dynamic but has no dynamic locations");
                    }
                    if (portal.getLocation() == null && portal.getDynamicLocations().isEmpty()) {
                        issues.add("entry portal location is not set");
                    }
                    if (portal.getDestination() == null) {
                        issues.add("entry portal destination is not set");
                    }
                }
                if ("exit".equalsIgnoreCase(portal.getId())) {
                    hasExit = true;
                    if (portal.getLocation() == null) {
                        issues.add("exit portal location is not set");
                    }
                    if (portal.getDestination() == null) {
                        issues.add("exit portal return destination is not set");
                    }
                }
            }
            if (def.getType() != me.reil.voidrift.event.EventType.ISLAND_WAR) {
                if (!hasEntry) issues.add("entry portal is missing (/riftadmin setup " + def.getId() + ")");
                if (!hasExit) issues.add("exit portal is missing (/riftadmin setup " + def.getId() + ")");
            }
        }

        return issues;
    }

    private List<String> collectEventWarnings(me.reil.voidrift.event.EventDefinition def) {
        List<String> warnings = new ArrayList<String>();
        warnings.addAll(rewardWarnings(def));
        return warnings;
    }

    private List<String> validateRewards(me.reil.voidrift.event.EventDefinition def) {
        List<String> issues = new ArrayList<String>();
        if (def.getRewardMoney() < 0) {
            issues.add("reward money cannot be negative");
        }
        if (def.getRewardIslandXp() < 0) {
            issues.add("reward island-xp cannot be negative");
        }
        int index = 1;
        for (String command : def.getRewardCommands()) {
            if (command == null || command.trim().isEmpty()) {
                issues.add("reward command[" + index + "] is empty");
            }
            index++;
        }
        return issues;
    }

    private List<String> rewardWarnings(me.reil.voidrift.event.EventDefinition def) {
        List<String> warnings = new ArrayList<String>();
        if (def.getRewardMoney() > 0 && !plugin.getRewardManager().isEconomyAvailable()) {
            warnings.add("money reward is configured but no SkyBound/Vault economy provider is available; money will be skipped");
        }
        if (def.getRewardIslandXp() > 0 && !plugin.getSkyBoundHook().isAvailable()) {
            warnings.add("island-xp reward is configured but SkyBound is unavailable; XP will be skipped");
        }
        return warnings;
    }

    private List<String> validateObjectives(me.reil.voidrift.event.EventDefinition def) {
        List<String> issues = new ArrayList<String>();
        if (def.getObjectives().isEmpty() && def.getType() != me.reil.voidrift.event.EventType.ISLAND_WAR) {
            issues.add("no objectives configured");
            return issues;
        }

        int index = 1;
        for (Objective objective : def.getObjectives()) {
            if (objective.getAmount() <= 0 && objective.getType() != ObjectiveType.NO_DEATH) {
                issues.add("objective[" + index + "] amount must be greater than 0");
            }
            if (!isTrackedObjective(objective.getType())) {
                issues.add("objective[" + index + "] " + objective.getType().name() + " is not tracked by current listeners yet");
            }
            if (objective.getType() == ObjectiveType.COMPLETE_BEFORE) {
                issues.add("objective[" + index + "] COMPLETE_BEFORE should be combined with a real task; alone it may complete too easily");
            }
            index++;
        }
        return issues;
    }

    private boolean isTrackedObjective(ObjectiveType type) {
        switch (type) {
            case KILL_MOBS:
            case KILL_BOSS:
            case KILL_ELITE:
            case DEAL_DAMAGE:
            case TAKE_DAMAGE:
            case NO_DEATH:
            case KILL_STREAK:
            case LAST_HIT_BOSS:
            case REACH_WAVE:
            case SURVIVE_TIME:
            case ALL_MOBS_DEAD:
            case CLEAR_WAVES:
            case COLLECT_ITEM:
            case COLLECT_FROM_MOB:
            case COLLECT_FROM_CHEST:
            case MINE_BLOCK:
            case PLACE_BLOCK:
            case SCORE_POINTS:
            case USE_PORTAL:
            case USE_ITEM:
            case EAT_FOOD:
            case CRAFT_ITEM:
            case COMPLETE_BEFORE:
            case CUSTOM:
                return true;
            default:
                return false;
        }
    }

    private List<String> validateZoneMobs(ZoneDefinition zone, boolean requireMobs) {
        List<String> issues = new ArrayList<String>();
        if (zone == null) return issues;

        if (requireMobs && zone.getMobPools().isEmpty()) {
            issues.add("mob pool is empty");
        }

        int index = 1;
        for (ZoneDefinition.MobPool mob : zone.getMobPools()) {
            issues.addAll(validateMobPoolEntry(zone.getId(), "mob-pool[" + index + "]", mob));
            index++;
        }

        for (Map.Entry<Integer, List<ZoneDefinition.MobPool>> entry : zone.getBonusWaves().entrySet()) {
            if (entry.getKey() == null || entry.getKey().intValue() <= 0) {
                issues.add("bonus wave key must be greater than 0");
            }
            List<ZoneDefinition.MobPool> mobs = entry.getValue();
            if (mobs == null || mobs.isEmpty()) {
                issues.add("bonus-wave " + entry.getKey() + " has no mobs");
                continue;
            }
            int bonusIndex = 1;
            for (ZoneDefinition.MobPool mob : mobs) {
                issues.addAll(validateMobPoolEntry(zone.getId(), "bonus-wave[" + entry.getKey() + "][" + bonusIndex + "]", mob));
                bonusIndex++;
            }
        }

        return issues;
    }

    private List<String> validateMobPoolEntry(String zoneId, String label, ZoneDefinition.MobPool mob) {
        List<String> issues = new ArrayList<String>();
        if (mob == null) {
            issues.add(zoneId + " " + label + ": mob entry is null");
            return issues;
        }

        String type = mob.getMobType() == null ? "" : mob.getMobType().trim().toUpperCase();
        String id = mob.getMobId() == null ? "" : mob.getMobId().trim();
        String model = mob.getModelId() == null ? "" : mob.getModelId().trim();
        String prefix = zoneId + " " + label + ": ";

        if (type.isEmpty()) {
            issues.add(prefix + "mob type is empty");
        }
        if (id.isEmpty()) {
            issues.add(prefix + "mob id is empty");
        }
        if (mob.getWeight() <= 0) {
            issues.add(prefix + "weight must be greater than 0");
        }
        if (mob.getWave() < 0) {
            issues.add(prefix + "wave cannot be negative");
        }

        if ("ELITEMOBS".equals(type)) {
            if (!plugin.getEliteMobsHook().isAvailable()) {
                issues.add(prefix + "EliteMobs mob configured but EliteMobs hook is unavailable");
            } else if (!id.isEmpty() && !plugin.getEliteMobsHook().canCreateBoss(id)) {
                issues.add(prefix + "EliteMobs boss not found or cannot be created: " + id);
            }
            if (!model.isEmpty()) {
                issues.add(prefix + "model-id is ignored for EliteMobs; put customModel in the EliteMobs boss config");
            }
            return issues;
        }

        if ("MYTHICMOBS".equals(type)) {
            if (plugin.getMythicMobsHook() == null || !plugin.getMythicMobsHook().isAvailable()) {
                issues.add(prefix + "MythicMobs mob configured but MythicMobs hook is unavailable");
            }
            if (!model.isEmpty() && !plugin.getFmmHook().isAvailable()) {
                issues.add(prefix + "FMM model configured but FreeMinecraftModels hook is unavailable: " + model);
            }
            return issues;
        }

        if ("VANILLA".equals(type) || type.isEmpty()) {
            if (!id.isEmpty()) {
                try {
                    org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(id.toUpperCase());
                    if (!entityType.isAlive()) {
                        issues.add(prefix + "vanilla entity is not a living mob: " + id);
                    }
                } catch (IllegalArgumentException e) {
                    issues.add(prefix + "unknown vanilla EntityType: " + id);
                }
            }
            if (!model.isEmpty() && !plugin.getFmmHook().isAvailable()) {
                issues.add(prefix + "FMM model configured but FreeMinecraftModels hook is unavailable: " + model);
            }
            return issues;
        }

        issues.add(prefix + "unsupported mob type '" + type + "'; supported: VANILLA, ELITEMOBS, MYTHICMOBS");
        return issues;
    }

    private void handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.usage"));
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.example"));
            return;
        }

        String template = normalizeTemplate(args[1]);
        if (template == null) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.unknown", vars("type", args[1])));
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.available"));
            return;
        }

        String eventId = args[2].toLowerCase();
        if (!eventId.matches("[a-zA-Z0-9_-]+")) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.invalid-id"));
            return;
        }
        if (plugin.getEventManager().getDefinition(eventId) != null) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.exists", vars("id", eventId)));
            return;
        }

        String zoneId = args.length >= 4 ? args[3] : "";
        if (!"islandwar".equals(template) && zoneId.isEmpty()) {
            ZoneDefinition first = firstZone();
            if (first != null) {
                zoneId = first.getId();
            }
        }

        boolean saved = saveTemplateEvent(template, eventId, zoneId);
        if (!saved) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.save-failed"));
            return;
        }

        plugin.getEventManager().reloadEvents();
        Map<String, String> out = vars("id", eventId, "type", template);
        out.put("zone", zoneId.isEmpty() ? "-" : zoneId);
        out.put("zone_or_placeholder", zoneId.isEmpty() ? "<zone-id>" : zoneId);
        sender.sendMessage(plugin.getLang().msg("messages.admin-template.created", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-template.type", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-template.zone", out));
        if (!"islandwar".equals(template) && zoneId.isEmpty()) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.no-zone", out));
        }
        if (!"islandwar".equals(template)) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.next-zone", out));
            sender.sendMessage(plugin.getLang().msg("messages.admin-template.next-setup", out));
        }
        sender.sendMessage(plugin.getLang().msg("messages.admin-template.check", out));
    }

    private String normalizeTemplate(String input) {
        String t = input.toLowerCase();
        if ("wave".equals(t) || "waves".equals(t) || "survival".equals(t)) return "waves";
        if ("boss".equals(t) || "bossfight".equals(t)) return "boss";
        if ("resource".equals(t) || "resources".equals(t) || "race".equals(t)) return "resource";
        if ("pvp".equals(t) || "arena".equals(t)) return "pvp";
        if ("timed".equals(t) || "time".equals(t) || "challenge".equals(t)) return "timed";
        if ("islandwar".equals(t) || "war".equals(t) || "island_war".equals(t)) return "islandwar";
        return null;
    }

    private ZoneDefinition firstZone() {
        for (ZoneDefinition zone : plugin.getZoneManager().getZones()) return zone;
        return null;
    }

    private boolean saveTemplateEvent(String template, String eventId, String zoneId) {
        File file = new File(plugin.getDataFolder(), "events.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "events." + eventId;

        cfg.set(path + ".display-name", templateDisplayName(template));
        cfg.set(path + ".description", templateDescription(template));
        cfg.set(path + ".enabled", true);
        cfg.set(path + ".type", templateType(template));
        cfg.set(path + ".zone", "islandwar".equals(template) ? "" : zoneId);
        cfg.set(path + ".duration-seconds", templateDuration(template));
        cfg.set(path + ".interval-seconds", templateInterval(template));
        cfg.set(path + ".min-players", templateMinPlayers(template));
        cfg.set(path + ".max-players", templateMaxPlayers(template));
        cfg.set(path + ".scale-mobs-per-player", templateScaleMobs(template));
        cfg.set(path + ".schedule", "INTERVAL");
        cfg.set(path + ".announcements.enabled", true);
        cfg.set(path + ".announcements.warning-seconds", Arrays.asList(Integer.valueOf(300), Integer.valueOf(60), Integer.valueOf(10)));
        cfg.set(path + ".preview.enabled", true);
        cfg.set(path + ".preview.seconds", 10);
        cfg.set(path + ".complete-on", templateCompleteOn(template));
        cfg.set(path + ".objectives", templateObjectives(template));
        cfg.set(path + ".rewards.money", templateMoney(template));
        cfg.set(path + ".rewards.island-xp", templateIslandXp(template));
        cfg.set(path + ".rewards.commands", templateCommands(template, eventId));
        cfg.set(path + ".loot-table", templateLoot(template));

        try {
            cfg.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save events.yml template '" + eventId + "': " + e.getMessage());
            return false;
        }
    }

    private String templateDisplayName(String template) {
        if ("waves".equals(template)) return "&5✦ Разлом Бездны";
        if ("boss".equals(template)) return "&c✦ Повелитель Разлома";
        if ("resource".equals(template)) return "&e✦ Гонка Собирателей";
        if ("pvp".equals(template)) return "&6✦ Арена Разлома";
        if ("timed".equals(template)) return "&b✦ Испытание Скорости";
        if ("islandwar".equals(template)) return "&5&l✦ Война островов";
        return "&d✦ VoidRift Event";
    }

    private String templateDescription(String template) {
        if ("waves".equals(template)) return "Выживи против волн мобов и зачисти разлом.";
        if ("boss".equals(template)) return "Победи босса до закрытия портала.";
        if ("resource".equals(template)) return "Набери больше очков за сбор ресурсов.";
        if ("pvp".equals(template)) return "Сражайся с игроками и набери победные очки.";
        if ("timed".equals(template)) return "Выполни задачу быстрее, чем закончится время.";
        if ("islandwar".equals(template)) return "Уничтожай сердца чужих островов и защищай своё.";
        return "";
    }

    private String templateType(String template) {
        if ("waves".equals(template)) return "WAVE_SURVIVAL";
        if ("boss".equals(template)) return "BOSS_FIGHT";
        if ("resource".equals(template)) return "RESOURCE_RACE";
        if ("pvp".equals(template)) return "PVP_ARENA";
        if ("timed".equals(template)) return "TIMED_CHALLENGE";
        if ("islandwar".equals(template)) return "ISLAND_WAR";
        return "CUSTOM";
    }

    private int templateDuration(String template) {
        if ("boss".equals(template)) return 240;
        if ("resource".equals(template)) return 180;
        if ("pvp".equals(template)) return 300;
        if ("timed".equals(template)) return 180;
        if ("islandwar".equals(template)) return 1800;
        return 300;
    }

    private int templateInterval(String template) {
        if ("boss".equals(template) || "pvp".equals(template)) return 2400;
        if ("islandwar".equals(template)) return 7200;
        return 1800;
    }

    private int templateMinPlayers(String template) {
        if ("pvp".equals(template) || "islandwar".equals(template)) return 2;
        return 1;
    }

    private int templateMaxPlayers(String template) {
        if ("boss".equals(template)) return 8;
        if ("resource".equals(template)) return 12;
        if ("pvp".equals(template)) return 16;
        if ("islandwar".equals(template)) return 100;
        return 10;
    }

    private int templateScaleMobs(String template) {
        if ("waves".equals(template)) return 2;
        if ("boss".equals(template)) return 1;
        return 0;
    }

    private String templateCompleteOn(String template) {
        if ("waves".equals(template) || "islandwar".equals(template)) return "ANY";
        return "ALL";
    }

    private double templateMoney(String template) {
        if ("boss".equals(template)) return 8000.0;
        if ("resource".equals(template)) return 4000.0;
        if ("pvp".equals(template)) return 6000.0;
        if ("timed".equals(template)) return 4500.0;
        if ("islandwar".equals(template)) return 15000.0;
        return 5000.0;
    }

    private int templateIslandXp(String template) {
        if ("boss".equals(template)) return 250;
        if ("resource".equals(template)) return 100;
        if ("pvp".equals(template)) return 120;
        if ("timed".equals(template)) return 120;
        if ("islandwar".equals(template)) return 500;
        return 150;
    }

    private List<String> templateCommands(String template, String eventId) {
        List<String> commands = new ArrayList<String>();
        commands.add("say {player} завершил событие " + eventId + "! Очки: {score}");
        return commands;
    }

    private List<Map<String, Object>> templateObjectives(String template) {
        List<Map<String, Object>> objectives = new ArrayList<Map<String, Object>>();
        if ("waves".equals(template)) {
            objectives.add(objective("CLEAR_WAVES", 5, ""));
            objectives.add(objective("SURVIVE_TIME", 300, ""));
        } else if ("boss".equals(template)) {
            objectives.add(objective("KILL_BOSS", 1, ""));
        } else if ("resource".equals(template)) {
            objectives.add(objective("SCORE_POINTS", 100, ""));
        } else if ("pvp".equals(template)) {
            objectives.add(objective("SCORE_POINTS", 10, ""));
        } else if ("timed".equals(template)) {
            objectives.add(objective("SURVIVE_TIME", 180, ""));
        }
        return objectives;
    }

    private Map<String, Object> objective(String type, int amount, String target) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("type", type);
        map.put("amount", amount);
        map.put("target", target);
        return map;
    }

    private List<Map<String, Object>> templateLoot(String template) {
        List<Map<String, Object>> loot = new ArrayList<Map<String, Object>>();
        if ("islandwar".equals(template) || "pvp".equals(template)) return loot;
        loot.add(loot("DIAMOND", 1, 0.10, "&b✦ Осколок Разлома"));
        loot.add(loot("IRON_INGOT", 5, 0.45, ""));
        loot.add(loot("GOLD_INGOT", 3, 0.25, ""));
        if ("boss".equals(template)) {
            loot.add(loot("NETHER_STAR", 1, 0.03, "&d✦ Ядро Босса"));
        }
        return loot;
    }

    private Map<String, Object> loot(String item, int amount, double chance, String displayName) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("item", item);
        map.put("amount", amount);
        map.put("chance", chance);
        if (displayName != null && !displayName.isEmpty()) {
            map.put("display-name", displayName);
        }
        return map;
    }

    private void handleZoneTemplate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().msg("messages.admin.players-only"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.usage"));
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.example"));
            return;
        }

        Player player = (Player) sender;
        String template = normalizeZoneTemplate(args[1]);
        if (template == null) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.unknown", vars("type", args[1])));
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.available"));
            return;
        }

        String zoneId = args[2].toLowerCase();
        if (!zoneId.matches("[a-zA-Z0-9_-]+")) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.invalid-id"));
            return;
        }
        if (plugin.getZoneManager().getZone(zoneId) != null) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.exists", vars("id", zoneId)));
            return;
        }

        int radius = defaultZoneRadius(template);
        if (args.length >= 4) {
            try {
                radius = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.radius-number"));
                return;
            }
            if (radius < 5 || radius > 200) {
                sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.radius-range"));
                return;
            }
        }

        if (!saveZoneTemplate(player, template, zoneId, radius)) {
            sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.save-failed"));
            return;
        }

        plugin.getZoneManager().reloadZones();
        Map<String, String> out = vars("id", zoneId, "type", template);
        out.put("world", player.getWorld().getName());
        out.put("radius", String.valueOf(radius));
        sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.created", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.type", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.world", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.radius", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.check", out));
        sender.sendMessage(plugin.getLang().msg("messages.admin-zone-template.create-event", out));
    }

    private String normalizeZoneTemplate(String input) {
        String t = input.toLowerCase();
        if ("wave".equals(t) || "waves".equals(t) || "survival".equals(t)) return "waves";
        if ("boss".equals(t) || "bossfight".equals(t)) return "boss";
        if ("resource".equals(t) || "resources".equals(t) || "race".equals(t)) return "resource";
        if ("pvp".equals(t) || "arena".equals(t)) return "pvp";
        if ("timed".equals(t) || "time".equals(t) || "challenge".equals(t)) return "timed";
        return null;
    }

    private int defaultZoneRadius(String template) {
        if ("boss".equals(template)) return 18;
        if ("resource".equals(template)) return 30;
        if ("pvp".equals(template)) return 24;
        if ("timed".equals(template)) return 20;
        return 25;
    }

    private boolean saveZoneTemplate(Player player, String template, String zoneId, int radius) {
        File file = new File(plugin.getDataFolder(), "zones.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Location center = player.getLocation();
        String path = "zones." + zoneId;
        int minY = Math.max(1, center.getBlockY() - 5);
        int maxY = Math.min(player.getWorld().getMaxHeight() - 1, center.getBlockY() + 18);

        cfg.set(path + ".world", player.getWorld().getName());
        setLoc(cfg, path + ".pos1", center.getBlockX() - radius, minY, center.getBlockZ() - radius);
        setLoc(cfg, path + ".pos2", center.getBlockX() + radius, maxY, center.getBlockZ() + radius);
        setLoc(cfg, path + ".areas.area1.pos1", center.getBlockX() - radius, minY, center.getBlockZ() - radius);
        setLoc(cfg, path + ".areas.area1.pos2", center.getBlockX() + radius, maxY, center.getBlockZ() + radius);
        cfg.set(path + ".max-mobs", zoneMaxMobs(template));

        cfg.set(path + ".spawn-points", null);
        setLoc(cfg, path + ".spawn-points.center", center.getBlockX(), center.getBlockY(), center.getBlockZ());
        if ("waves".equals(template)) {
            setLoc(cfg, path + ".spawn-points.north", center.getBlockX(), center.getBlockY(), center.getBlockZ() - Math.max(4, radius / 2));
            setLoc(cfg, path + ".spawn-points.south", center.getBlockX(), center.getBlockY(), center.getBlockZ() + Math.max(4, radius / 2));
            setLoc(cfg, path + ".spawn-points.east", center.getBlockX() + Math.max(4, radius / 2), center.getBlockY(), center.getBlockZ());
            setLoc(cfg, path + ".spawn-points.west", center.getBlockX() - Math.max(4, radius / 2), center.getBlockY(), center.getBlockZ());
        }

        cfg.set(path + ".mob-pools", zoneMobPools(template));
        cfg.set(path + ".bonus-waves", null);
        if ("waves".equals(template)) {
            cfg.set(path + ".bonus-waves.5", zoneBonusWave("WITCH", "VANILLA", 1));
            cfg.set(path + ".bonus-waves.10", zoneBonusWave("RAVAGER", "VANILLA", 1));
        }

        try {
            cfg.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save zones.yml template '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }

    private int zoneMaxMobs(String template) {
        if ("boss".equals(template)) return 1;
        if ("resource".equals(template) || "pvp".equals(template) || "timed".equals(template)) return 0;
        return 15;
    }

    private List<Map<String, Object>> zoneMobPools(String template) {
        List<Map<String, Object>> mobs = new ArrayList<Map<String, Object>>();
        if ("waves".equals(template)) {
            mobs.add(mob("ZOMBIE", "VANILLA", null, 5, 0));
            mobs.add(mob("SKELETON", "VANILLA", null, 3, 0));
            mobs.add(mob("CREEPER", "VANILLA", null, 2, 2));
        } else if ("boss".equals(template)) {
            mobs.add(mob("ZOMBIE", "VANILLA", null, 1, 0));
        }
        return mobs;
    }

    private List<Map<String, Object>> zoneBonusWave(String mobId, String mobType, int count) {
        List<Map<String, Object>> mobs = new ArrayList<Map<String, Object>>();
        Map<String, Object> mob = new LinkedHashMap<String, Object>();
        mob.put("mob-id", mobId);
        mob.put("mob-type", mobType);
        mob.put("count", count);
        mobs.add(mob);
        return mobs;
    }

    private Map<String, Object> mob(String mobId, String mobType, String model, int weight, int wave) {
        Map<String, Object> mob = new LinkedHashMap<String, Object>();
        mob.put("mob-id", mobId);
        mob.put("mob-type", mobType);
        if (model != null && !model.isEmpty()) mob.put("model", model);
        mob.put("weight", weight);
        mob.put("wave", wave);
        return mob;
    }

    private void setLoc(YamlConfiguration cfg, String path, int x, int y, int z) {
        cfg.set(path + ".x", x);
        cfg.set(path + ".y", y);
        cfg.set(path + ".z", z);
    }

    private void handleQuickStart(CommandSender sender) {
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.header"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.step1"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.cmd1"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.step2"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.cmd2"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.step3"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.cmd3"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.step4"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.cmd4"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.step5"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.cmd5"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.doctor"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-quickstart.doctor-cmd"));
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
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.template"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.zonetemplate"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.quickstart"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.doctor"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.validate"));
        sender.sendMessage(plugin.getLang().msg("messages.admin-help.reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("start", "startnow", "stop", "stopnow", "reload", "info", "portal", "setup", "setupzone", "createevent", "doctor", "validate", "val", "check", "template", "zonetemplate", "quickstart"), args[0]);
        if (args.length == 2) {
            if ("portal".equals(args[0])) return filter(Arrays.asList("entry", "exit", "dynamic", "addpos", "dest", "return", "next", "nextdest"), args[1]);
            if ("template".equals(args[0])) return filter(Arrays.asList("waves", "boss", "resource", "pvp", "timed", "islandwar"), args[1]);
            if ("zonetemplate".equals(args[0])) return filter(Arrays.asList("waves", "boss", "resource", "pvp", "timed"), args[1]);
            if ("validate".equals(args[0]) || "valid".equals(args[0]) || "val".equals(args[0]) || "check".equals(args[0])) {
                List<String> options = eventIds();
                options.add("all");
                return filter(options, args[1]);
            }
            if ("info".equals(args[0])) {
                List<String> options = eventIds();
                options.add("zone");
                return filter(options, args[1]);
            }
            if ("start".equals(args[0]) || "startnow".equals(args[0]) || "stop".equals(args[0])
                    || "stopnow".equals(args[0]) || "setup".equals(args[0])) {
                return filter(eventIds(), args[1]);
            }
        }
        if (args.length == 3 && "portal".equals(args[0])) {
            return filter(eventIds(), args[2]);
        }
        if (args.length == 3 && "info".equals(args[0]) && "zone".equals(args[1])) {
            List<String> ids = new ArrayList<String>();
            for (ZoneDefinition zone : plugin.getZoneManager().getZones()) ids.add(zone.getId());
            return filter(ids, args[2]);
        }
        if (args.length == 4 && "template".equals(args[0]) && !"islandwar".equalsIgnoreCase(args[1])) {
            List<String> ids = new ArrayList<String>();
            for (ZoneDefinition zone : plugin.getZoneManager().getZones()) ids.add(zone.getId());
            return filter(ids, args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> r = new ArrayList<String>();
        for (String s : options) if (s.toLowerCase().startsWith(prefix.toLowerCase())) r.add(s);
        return r;
    }

    private List<String> eventIds() {
        List<String> ids = new ArrayList<String>();
        for (me.reil.voidrift.event.EventDefinition def : plugin.getEventManager().getDefinitions()) {
            ids.add(def.getId());
        }
        return ids;
    }

    private Map<String, String> vars(String k1, String v1) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(k1, v1);
        return vars;
    }

    private Map<String, String> vars(String k1, String v1, String k2, String v2) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(k1, v1);
        vars.put(k2, v2);
        return vars;
    }
}
