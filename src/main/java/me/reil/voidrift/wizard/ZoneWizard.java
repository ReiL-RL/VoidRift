package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hotbar-based arena/zone setup wizard.
 */
public final class ZoneWizard {

    private final VoidRiftPlugin plugin;
    private final Map<UUID, ZoneWizardSession> sessions = new LinkedHashMap<UUID, ZoneWizardSession>();

    public ZoneWizard(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, String zoneId) {
        ZoneWizardSession session = new ZoneWizardSession(zoneId, player.getWorld().getName());
        sessions.put(player.getUniqueId(), session);

        refreshStep(player, session);

        Map<String, String> vars = vars("zone", zoneId);
        player.sendMessage("");
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.title", vars));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step1"));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.hotbar-hint"));
        player.sendMessage("");
    }

    public boolean handleInteract(Player player, int slot) {
        ZoneWizardSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        if (session.isAwaitingChatInput()) return true;

        if (slot == 8) { cancel(player); return true; }
        if (slot == 7) { handleBack(player, session); return true; }
        if (slot == 6) { handleSkip(player, session); return true; }

        switch (session.getStep()) {
            case SET_POS1: return handleSetPos1(player, session, slot);
            case SET_POS2: return handleSetPos2(player, session, slot);
            case ADD_AREAS: return handleAddBoundaryPoints(player, session, slot);
            case ADD_SPAWN_POINTS: return handleAddSpawnPoints(player, session, slot);
            case ADD_MOB_POOL: return handleAddMobPool(player, session, slot);
            case SET_MAX_MOBS: return handleSetMaxMobs(player, session, slot);
            default: return false;
        }
    }

    public boolean handleChat(Player player, String message) {
        ZoneWizardSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAwaitingChatInput()) return false;

        String msg = message.trim();

        if (session.getStep() == ZoneWizardStep.SET_MAX_MOBS) {
            try {
                int max = Math.max(0, Integer.parseInt(msg));
                session.setMaxMobs(max);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.maxmobs-set",
                        vars("count", String.valueOf(max))));
                session.setAwaitingChatInput(false);
                finishWizard(player, session);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-number-error"));
            }
            return true;
        }

        switch (session.getMobEntryState()) {
            case AWAITING_ID:
                session.setPendingMobId(msg);
                if (plugin.getFmmHook().isAvailable() || "ELITEMOBS".equalsIgnoreCase(session.getPendingMobType())) {
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_MODEL);
                    player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-model"));
                } else {
                    session.setPendingModelId(null);
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WAVE);
                    player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-wave"));
                }
                return true;

            case AWAITING_MODEL:
                session.setPendingModelId("-".equals(msg) || "none".equalsIgnoreCase(msg) ? null : msg);
                session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WAVE);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-wave"));
                return true;

            case AWAITING_WAVE:
                try {
                    int wave = Math.max(0, Integer.parseInt(msg));
                    session.setPendingWave(wave);
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WEIGHT);
                    player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-weight"));
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-number-error"));
                }
                return true;

            case AWAITING_WEIGHT:
                try {
                    int weight = Math.max(1, Integer.parseInt(msg));
                    session.getMobEntries().add(new ZoneWizardSession.MobEntry(
                            session.getPendingMobId(),
                            session.getPendingMobType(),
                            session.getPendingModelId(),
                            session.getPendingWave(),
                            weight));
                    session.setAwaitingChatInput(false);
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.NONE);
                    player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.mob-added"));
                    refreshStep(player, session);
                    showMobStepHelp(player);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.chat-number-error"));
                }
                return true;

            default:
                return false;
        }
    }

    public boolean isInWizard(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isAwaitingChat(UUID playerId) {
        ZoneWizardSession session = sessions.get(playerId);
        return session != null && session.isAwaitingChatInput();
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
        clearHotbar(player);
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.cancelled"));
    }

    private boolean handleSetPos1(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setPos1(player.getLocation().clone());
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.pos1-set"));
            session.setStep(ZoneWizardStep.SET_POS2);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step2"));
        }
        return true;
    }

    private boolean handleSetPos2(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setPos2(player.getLocation().clone());
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.pos2-set"));
            session.setStep(ZoneWizardStep.ADD_AREAS);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step-areas"));
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step-areas-hint"));
        }
        return true;
    }

    private boolean handleAddBoundaryPoints(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            if (session.getPendingAreaPos1() == null) {
                session.setPendingAreaPos1(player.getLocation().clone());
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.area-pos1-set"));
            } else {
                Location pos1 = session.getPendingAreaPos1();
                Location pos2 = player.getLocation().clone();
                session.getAreas().add(new ZoneWizardSession.AreaEntry(pos1, pos2));
                session.setPendingAreaPos1(null);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.area-added",
                        vars("count", String.valueOf(session.getAreas().size() + 1))));
            }
        } else if (slot == 1) {
            session.setPendingAreaPos1(null);
            session.setStep(ZoneWizardStep.ADD_SPAWN_POINTS);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step3"));
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step3-hint"));
        }
        return true;
    }

    private boolean handleAddSpawnPoints(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.getSpawnPoints().add(player.getLocation().clone());
            session.incrementSpawnPointCount();
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.spawn-added",
                    vars("count", String.valueOf(session.getSpawnPointCount()))));
        } else if (slot == 1) {
            session.setStep(ZoneWizardStep.ADD_MOB_POOL);
            refreshStep(player, session);
            showMobStepHelp(player);
        }
        return true;
    }

    private boolean handleAddMobPool(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            startMobEntry(player, session, "VANILLA");
        } else if (slot == 1) {
            startMobEntry(player, session, "ELITEMOBS");
        } else if (slot == 2) {
            session.setStep(ZoneWizardStep.SET_MAX_MOBS);
            session.setAwaitingChatInput(true);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step5-maxmobs"));
        }
        return true;
    }

    private boolean handleSetMaxMobs(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setAwaitingChatInput(false);
            finishWizard(player, session);
        }
        return true;
    }

    private void startMobEntry(Player player, ZoneWizardSession session, String type) {
        if ("ELITEMOBS".equalsIgnoreCase(type) && !plugin.getEliteMobsHook().isAvailable()) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.em-required"));
            return;
        }
        session.setPendingMobType(type);
        session.setPendingMobId(null);
        session.setPendingModelId(null);
        session.setPendingWave(0);
        session.setAwaitingChatInput(true);
        session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_ID);

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msgFor(player, "ELITEMOBS".equalsIgnoreCase(type)
                ? "messages.zone-wizard.chat-id-em"
                : "messages.zone-wizard.chat-id-vanilla"));
    }

    private void handleSkip(Player player, ZoneWizardSession session) {
        switch (session.getStep()) {
            case SET_POS1:
                session.setStep(ZoneWizardStep.SET_POS2);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.skip-pos1"));
                return;
            case SET_POS2:
                session.setStep(ZoneWizardStep.ADD_AREAS);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.skip-pos2"));
                return;
            case ADD_AREAS:
                session.setStep(ZoneWizardStep.ADD_SPAWN_POINTS);
                refreshStep(player, session);
                session.setPendingAreaPos1(null);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.skip-areas"));
                return;
            case ADD_SPAWN_POINTS:
                session.setStep(ZoneWizardStep.ADD_MOB_POOL);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.skip-spawns"));
                showMobStepHelp(player);
                return;
            case ADD_MOB_POOL:
                session.setStep(ZoneWizardStep.SET_MAX_MOBS);
                session.setAwaitingChatInput(true);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.skip-mobs"));
                return;
            case SET_MAX_MOBS:
                session.setAwaitingChatInput(false);
                finishWizard(player, session);
                return;
            default:
                break;
        }
    }

    private void handleBack(Player player, ZoneWizardSession session) {
        if (session.isAwaitingChatInput()) {
            session.setAwaitingChatInput(false);
            session.setMobEntryState(ZoneWizardSession.MobEntryState.NONE);
        }
        if (!session.goBack()) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.back-nowhere"));
            return;
        }
        refreshStep(player, session);
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.back-to",
                vars("step", stepName(session.getStep()))));
    }

    private void finishWizard(Player player, ZoneWizardSession session) {
        session.setStep(ZoneWizardStep.DONE);
        clearHotbar(player);
        sessions.remove(player.getUniqueId());
        saveZone(session);
        plugin.getZoneManager().reloadZones();

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.finished",
                vars("zone", session.getZoneId())));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.finished-spawns",
                vars("count", String.valueOf(session.getSpawnPointCount()))));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.finished-mobs",
                vars("count", String.valueOf(session.getMobEntries().size()))));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.finished-maxmobs",
                vars("count", String.valueOf(session.getMaxMobs()))));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.finished-hint",
                vars("zone", session.getZoneId())));
        player.sendMessage("");
    }

    private void saveZone(ZoneWizardSession session) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "zones.yml");
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        String path = "zones." + session.getZoneId();
        cfg.set(path + ".world", session.getWorldName());

        if (session.getPos1() != null) {
            cfg.set(path + ".pos1.x", session.getPos1().getX());
            cfg.set(path + ".pos1.y", session.getPos1().getY());
            cfg.set(path + ".pos1.z", session.getPos1().getZ());
        }
        if (session.getPos2() != null) {
            cfg.set(path + ".pos2.x", session.getPos2().getX());
            cfg.set(path + ".pos2.y", session.getPos2().getY());
            cfg.set(path + ".pos2.z", session.getPos2().getZ());
        }

        cfg.set(path + ".areas.area1.pos1.x", session.getPos1().getX());
        cfg.set(path + ".areas.area1.pos1.y", session.getPos1().getY());
        cfg.set(path + ".areas.area1.pos1.z", session.getPos1().getZ());
        cfg.set(path + ".areas.area1.pos2.x", session.getPos2().getX());
        cfg.set(path + ".areas.area1.pos2.y", session.getPos2().getY());
        cfg.set(path + ".areas.area1.pos2.z", session.getPos2().getZ());
        int areaIdx = 2;
        for (ZoneWizardSession.AreaEntry area : session.getAreas()) {
            String areaPath = path + ".areas.area" + areaIdx;
            cfg.set(areaPath + ".pos1.x", area.getPos1().getX());
            cfg.set(areaPath + ".pos1.y", area.getPos1().getY());
            cfg.set(areaPath + ".pos1.z", area.getPos1().getZ());
            cfg.set(areaPath + ".pos2.x", area.getPos2().getX());
            cfg.set(areaPath + ".pos2.y", area.getPos2().getY());
            cfg.set(areaPath + ".pos2.z", area.getPos2().getZ());
            areaIdx++;
        }

        cfg.set(path + ".max-mobs", session.getMaxMobs());

        int spIdx = 1;
        for (Location loc : session.getSpawnPoints()) {
            String spPath = path + ".spawn-points.sp" + spIdx;
            cfg.set(spPath + ".x", loc.getX());
            cfg.set(spPath + ".y", loc.getY());
            cfg.set(spPath + ".z", loc.getZ());
            spIdx++;
        }

        List<Map<String, Object>> poolList = new ArrayList<Map<String, Object>>();
        for (ZoneWizardSession.MobEntry entry : session.getMobEntries()) {
            Map<String, Object> mob = new LinkedHashMap<String, Object>();
            mob.put("mob-id", entry.getMobId());
            mob.put("mob-type", entry.getMobType());
            if (entry.getModelId() != null && !entry.getModelId().isEmpty()) {
                mob.put("model", entry.getModelId());
            }
            mob.put("weight", entry.getWeight());
            mob.put("wave", entry.getWave());
            poolList.add(mob);
        }
        cfg.set(path + ".mob-pools", poolList);

        try {
            cfg.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save zones.yml: " + e.getMessage());
        }
    }

    private void showMobStepHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step4"));
        player.sendMessage(plugin.getLang().msgFor(player, "messages.zone-wizard.step4-hotbar"));
        player.sendMessage(plugin.getLang().msgFor(player, plugin.getEliteMobsHook().isAvailable()
                ? "messages.zone-wizard.step4-em-available"
                : "messages.zone-wizard.step4-em-missing"));
        player.sendMessage(plugin.getLang().msgFor(player, plugin.getFmmHook().isAvailable()
                ? "messages.zone-wizard.step4-fmm-available"
                : "messages.zone-wizard.step4-fmm-missing"));
    }

    private void refreshStep(Player player, ZoneWizardSession session) {
        clearHotbar(player);
        giveStepItems(player, session);
    }

    private void giveStepItems(Player player, ZoneWizardSession session) {
        switch (session.getStep()) {
            case SET_POS1:
            case SET_POS2:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-confirm")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-cancel")));
                break;
            case ADD_AREAS:
                player.getInventory().setItem(0, makeItem(Material.BLUE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-add-area-point")));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-next")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-cancel")));
                break;
            case ADD_SPAWN_POINTS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-add-spawn")));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-next")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-cancel")));
                break;
            case ADD_MOB_POOL:
                player.getInventory().setItem(0, makeItem(Material.ZOMBIE_HEAD, plugin.getLang().msg("messages.zone-wizard.btn-add-vanilla")));
                player.getInventory().setItem(1, makeItem(Material.WITHER_SKELETON_SKULL, plugin.getLang().msg("messages.zone-wizard.btn-add-elitemob")));
                player.getInventory().setItem(2, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-next")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-cancel")));
                break;
            case SET_MAX_MOBS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-default-15")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-cancel")));
                break;
            default:
                break;
        }
    }

    private void clearHotbar(Player player) {
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, null);
        }
    }

    private ItemStack makeItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String stepName(ZoneWizardStep step) {
        switch (step) {
            case SET_POS1: return plugin.getLang().msg("messages.zone-wizard.step-pos1");
            case SET_POS2: return plugin.getLang().msg("messages.zone-wizard.step-pos2");
            case ADD_AREAS: return plugin.getLang().msg("messages.zone-wizard.step-areas-name");
            case ADD_SPAWN_POINTS: return plugin.getLang().msg("messages.zone-wizard.step-spawns");
            case ADD_MOB_POOL: return plugin.getLang().msg("messages.zone-wizard.step-mobs");
            case SET_MAX_MOBS: return plugin.getLang().msg("messages.zone-wizard.step-maxmobs");
            default: return step.name();
        }
    }

    private Map<String, String> vars(String key, String value) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(key, value);
        return vars;
    }
}
