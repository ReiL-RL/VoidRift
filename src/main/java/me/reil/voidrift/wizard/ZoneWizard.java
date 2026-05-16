package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.zone.ZoneDefinition;
import org.bukkit.ChatColor;
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
 * Zone setup wizard — hotbar items + chat input for mob pools.
 *
 * Steps:
 * 1. Set pos1 (corner 1)
 * 2. Set pos2 (corner 2)
 * 3. Add spawn points (repeat until Done)
 * 4. Add mob pool entries via chat (type, id, model, weight)
 * 5. Set max mobs (chat input)
 * 6. Done — saves to zones.yml
 *
 * Hotbar:
 *  slot 0 = primary action
 *  slot 1 = secondary / next
 *  slot 2 = done (where applicable)
 *  slot 6 = Skip
 *  slot 7 = Back
 *  slot 8 = Cancel
 */
public final class ZoneWizard {

    private final VoidRiftPlugin plugin;
    private final Map<UUID, ZoneWizardSession> sessions = new LinkedHashMap<UUID, ZoneWizardSession>();

    public ZoneWizard(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== Public API =====

    public void start(Player player, String zoneId) {
        String worldName = player.getWorld().getName();
        ZoneWizardSession session = new ZoneWizardSession(zoneId, worldName);
        sessions.put(player.getUniqueId(), session);

        clearHotbar(player);
        giveStepItems(player, session);

        Map<String, String> v = new HashMap<String, String>();
        v.put("zone", zoneId);

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.title", v));
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step1"));
        player.sendMessage("");
    }

    public boolean handleInteract(Player player, int slot) {
        ZoneWizardSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        if (session.isAwaitingChatInput()) return true; // Block hotbar during chat input

        if (slot == 8) { cancel(player); return true; }
        if (slot == 7) { handleBack(player, session); return true; }
        if (slot == 6) { handleSkip(player, session); return true; }

        switch (session.getStep()) {
            case SET_POS1: return handleSetPos1(player, session, slot);
            case SET_POS2: return handleSetPos2(player, session, slot);
            case ADD_SPAWN_POINTS: return handleAddSpawnPoints(player, session, slot);
            case ADD_MOB_POOL: return handleAddMobPool(player, session, slot);
            case SET_MAX_MOBS: return handleSetMaxMobs(player, session, slot);
            default: return false;
        }
    }

    /**
     * Handle chat input for mob pool configuration.
     * @return true if consumed
     */
    public boolean handleChat(Player player, String message) {
        ZoneWizardSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAwaitingChatInput()) return false;

        String msg = message.trim();

        // Max mobs input
        if (session.getStep() == ZoneWizardStep.SET_MAX_MOBS) {
            try {
                int max = Integer.parseInt(msg);
                session.setMaxMobs(max);
                Map<String, String> v = new HashMap<String, String>();
                v.put("count", String.valueOf(max));
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.maxmobs-set", v));
                session.setAwaitingChatInput(false);
                finishWizard(player, session);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-number-error"));
            }
            return true;
        }

        // Mob pool entry state machine
        switch (session.getMobEntryState()) {
            case AWAITING_TYPE:
                if ("v".equalsIgnoreCase(msg) || "vanilla".equalsIgnoreCase(msg)) {
                    session.setPendingMobType("VANILLA");
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_ID);
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-id-vanilla"));
                } else if ("e".equalsIgnoreCase(msg) || "em".equalsIgnoreCase(msg) || "elitemobs".equalsIgnoreCase(msg)) {
                    session.setPendingMobType("ELITEMOBS");
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_ID);
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-id-em"));
                } else {
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-type-error"));
                }
                return true;

            case AWAITING_ID:
                session.setPendingMobId(msg);
                session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_MODEL);
                if (plugin.getFmmHook().isAvailable()) {
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-model"));
                } else {
                    // FMM not available — skip model
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WEIGHT);
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-weight"));
                }
                return true;

            case AWAITING_MODEL:
                String modelId = "-".equals(msg) || "none".equalsIgnoreCase(msg) ? null : msg;
                session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WEIGHT);
                session.getMobEntries().add(new ZoneWizardSession.MobEntry(
                        session.getPendingMobId(), session.getPendingMobType(), modelId, 0));
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-weight"));
                return true;

            case AWAITING_WEIGHT:
                try {
                    int weight = Integer.parseInt(msg);
                    if (weight < 1) weight = 1;
                    // Update last entry with correct weight
                    List<ZoneWizardSession.MobEntry> entries = session.getMobEntries();
                    if (!entries.isEmpty()) {
                        ZoneWizardSession.MobEntry last = entries.remove(entries.size() - 1);
                        entries.add(new ZoneWizardSession.MobEntry(last.getMobId(), last.getMobType(), last.getModelId(), weight));
                    }
                    session.setAwaitingChatInput(false);
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.NONE);
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.mob-added"));
                    refreshStep(player, session);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-number-error"));
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
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.cancelled"));
    }

    // ===== Step handlers =====

    private boolean handleSetPos1(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setPos1(player.getLocation().clone());
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.pos1-set"));
            session.setStep(ZoneWizardStep.SET_POS2);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step2"));
        }
        return true;
    }

    private boolean handleSetPos2(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setPos2(player.getLocation().clone());
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.pos2-set"));
            session.setStep(ZoneWizardStep.ADD_SPAWN_POINTS);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step3"));
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step3-hint"));
        }
        return true;
    }

    private boolean handleAddSpawnPoints(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.getSpawnPoints().add(player.getLocation().clone());
            session.incrementSpawnPointCount();
            Map<String, String> v = new HashMap<String, String>();
            v.put("count", String.valueOf(session.getSpawnPointCount()));
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.spawn-added", v));
        } else if (slot == 1) {
            // Next — go to mob pool
            session.setStep(ZoneWizardStep.ADD_MOB_POOL);
            refreshStep(player, session);
            player.sendMessage("");
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step4"));
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step4-hint-add"));
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step4-hint-next"));
            if (plugin.getEliteMobsHook().isAvailable()) {
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step4-em-available"));
            }
            if (plugin.getFmmHook().isAvailable()) {
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step4-fmm-available"));
            }
        }
        return true;
    }

    private boolean handleAddMobPool(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            // Start chat input for mob entry
            session.setAwaitingChatInput(true);
            session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_TYPE);
            player.sendMessage("");
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-type-prompt"));
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-type-vanilla"));
            if (plugin.getEliteMobsHook().isAvailable()) {
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.chat-type-em"));
            }
        } else if (slot == 1) {
            // Next — go to max mobs
            session.setStep(ZoneWizardStep.SET_MAX_MOBS);
            session.setAwaitingChatInput(true);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.step5-maxmobs"));
        }
        return true;
    }

    private boolean handleSetMaxMobs(Player player, ZoneWizardSession session, int slot) {
        // Max mobs is handled via chat, but if they click confirm use default
        if (slot == 0) {
            session.setAwaitingChatInput(false);
            finishWizard(player, session);
        }
        return true;
    }

    // ===== Skip & Back =====

    private void handleSkip(Player player, ZoneWizardSession session) {
        switch (session.getStep()) {
            case SET_POS1:
                session.setStep(ZoneWizardStep.SET_POS2);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.skip-pos1"));
                return;
            case SET_POS2:
                session.setStep(ZoneWizardStep.ADD_SPAWN_POINTS);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.skip-pos2"));
                return;
            case ADD_SPAWN_POINTS:
                session.setStep(ZoneWizardStep.ADD_MOB_POOL);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.skip-spawns"));
                return;
            case ADD_MOB_POOL:
                session.setStep(ZoneWizardStep.SET_MAX_MOBS);
                session.setAwaitingChatInput(true);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.zone-wizard.skip-mobs"));
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
        ZoneWizardStep prev = session.getPreviousStep();
        if (prev == null) {
            player.sendMessage(plugin.getLang().msg("messages.zone-wizard.back-nowhere"));
            return;
        }
        session.goBack();
        refreshStep(player, session);
        Map<String, String> v = new HashMap<String, String>();
        v.put("step", stepName(session.getStep()));
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.back-to", v));
    }

    private void finishWizard(Player player, ZoneWizardSession session) {
        session.setStep(ZoneWizardStep.DONE);
        clearHotbar(player);
        sessions.remove(player.getUniqueId());

        // Save zone
        saveZone(session);

        Map<String, String> v = new HashMap<String, String>();
        v.put("zone", session.getZoneId());
        v.put("count", String.valueOf(session.getSpawnPointCount()));

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.finished", v));

        Map<String, String> vs = new HashMap<String, String>();
        vs.put("count", String.valueOf(session.getSpawnPointCount()));
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.finished-spawns", vs));

        Map<String, String> vm = new HashMap<String, String>();
        vm.put("count", String.valueOf(session.getMobEntries().size()));
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.finished-mobs", vm));

        Map<String, String> vmx = new HashMap<String, String>();
        vmx.put("count", String.valueOf(session.getMaxMobs()));
        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.finished-maxmobs", vmx));

        player.sendMessage(plugin.getLang().msg("messages.zone-wizard.finished-hint", v));
        player.sendMessage("");
    }

    // ===== Save =====

    private void saveZone(ZoneWizardSession session) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "zones.yml");
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        String path = "zones." + session.getZoneId();
        String worldName = session.getWorldName();
        cfg.set(path + ".world", worldName);

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

        cfg.set(path + ".max-mobs", session.getMaxMobs());

        // Spawn points
        int spIdx = 1;
        for (Location loc : session.getSpawnPoints()) {
            String spPath = path + ".spawn-points.sp" + spIdx;
            cfg.set(spPath + ".x", loc.getX());
            cfg.set(spPath + ".y", loc.getY());
            cfg.set(spPath + ".z", loc.getZ());
            spIdx++;
        }

        // Mob pools
        List<java.util.Map<String, Object>> poolList = new ArrayList<java.util.Map<String, Object>>();
        for (ZoneWizardSession.MobEntry entry : session.getMobEntries()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("mob-id", entry.getMobId());
            m.put("mob-type", entry.getMobType());
            if (entry.getModelId() != null && !entry.getModelId().isEmpty()) {
                m.put("model", entry.getModelId());
            }
            m.put("weight", entry.getWeight());
            m.put("wave", 0);
            poolList.add(m);
        }
        cfg.set(path + ".mob-pools", poolList);

        try {
            cfg.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save zones.yml: " + e.getMessage());
        }
    }

    // ===== UI =====

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
            case ADD_SPAWN_POINTS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-add-spawn")));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-next")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-cancel")));
                break;
            case ADD_MOB_POOL:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-add-mob")));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.zone-wizard.btn-next")));
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
            case ADD_SPAWN_POINTS: return plugin.getLang().msg("messages.zone-wizard.step-spawns");
            case ADD_MOB_POOL: return plugin.getLang().msg("messages.zone-wizard.step-mobs");
            case SET_MAX_MOBS: return plugin.getLang().msg("messages.zone-wizard.step-maxmobs");
            default: return step.name();
        }
    }
}
