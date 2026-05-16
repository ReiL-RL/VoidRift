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

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2726 \u041c\u0430\u0441\u0442\u0435\u0440 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0437\u043e\u043d\u044b: " + ChatColor.YELLOW + zoneId);
        player.sendMessage(ChatColor.GRAY + "\u0428\u0430\u0433 1: \u0412\u0441\u0442\u0430\u043d\u044c \u043d\u0430 \u0443\u0433\u043e\u043b 1 \u0437\u043e\u043d\u044b \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c \u0437\u0435\u043b\u0451\u043d\u044b\u0439.");
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
                player.sendMessage(ChatColor.GREEN + "\u2714 \u041c\u0430\u043a\u0441. \u043c\u043e\u0431\u043e\u0432: " + max);
                session.setAwaitingChatInput(false);
                finishWizard(player, session);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "\u0412\u0432\u0435\u0434\u0438 \u0447\u0438\u0441\u043b\u043e!");
            }
            return true;
        }

        // Mob pool entry state machine
        switch (session.getMobEntryState()) {
            case AWAITING_TYPE:
                if ("v".equalsIgnoreCase(msg) || "vanilla".equalsIgnoreCase(msg)) {
                    session.setPendingMobType("VANILLA");
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_ID);
                    player.sendMessage(ChatColor.YELLOW + "\u0412\u0432\u0435\u0434\u0438 mob-id (ZOMBIE, SKELETON, CREEPER...):");
                } else if ("e".equalsIgnoreCase(msg) || "em".equalsIgnoreCase(msg) || "elitemobs".equalsIgnoreCase(msg)) {
                    session.setPendingMobType("ELITEMOBS");
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_ID);
                    player.sendMessage(ChatColor.YELLOW + "\u0412\u0432\u0435\u0434\u0438 \u0438\u043c\u044f \u0444\u0430\u0439\u043b\u0430 \u0431\u043e\u0441\u0441\u0430 (skybound_void_reaver.yml):");
                } else {
                    player.sendMessage(ChatColor.RED + "\u0412\u0432\u0435\u0434\u0438 'v' (\u0432\u0430\u043d\u0438\u043b\u044c) \u0438\u043b\u0438 'e' (EliteMobs):");
                }
                return true;

            case AWAITING_ID:
                session.setPendingMobId(msg);
                session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_MODEL);
                if (plugin.getFmmHook().isAvailable()) {
                    player.sendMessage(ChatColor.YELLOW + "\u0412\u0432\u0435\u0434\u0438 FMM \u043c\u043e\u0434\u0435\u043b\u044c (\u0438\u043b\u0438 '-' \u0435\u0441\u043b\u0438 \u043d\u0435 \u043d\u0443\u0436\u043d\u0430):");
                } else {
                    // FMM not available — skip model
                    session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WEIGHT);
                    player.sendMessage(ChatColor.YELLOW + "\u0412\u0432\u0435\u0434\u0438 \u0432\u0435\u0441 (1-10, \u0447\u0435\u043c \u0431\u043e\u043b\u044c\u0448\u0435 = \u0447\u0430\u0449\u0435 \u0441\u043f\u0430\u0432\u043d\u0438\u0442\u0441\u044f):");
                }
                return true;

            case AWAITING_MODEL:
                String modelId = "-".equals(msg) || "none".equalsIgnoreCase(msg) ? null : msg;
                session.setMobEntryState(ZoneWizardSession.MobEntryState.AWAITING_WEIGHT);
                // Store model temporarily in pendingMobId field trick — use a holder
                // Actually let's just finish the entry here with default weight prompt
                session.getMobEntries().add(new ZoneWizardSession.MobEntry(
                        session.getPendingMobId(), session.getPendingMobType(), modelId, 0));
                player.sendMessage(ChatColor.YELLOW + "\u0412\u0432\u0435\u0434\u0438 \u0432\u0435\u0441 (1-10):");
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
                    player.sendMessage(ChatColor.GREEN + "\u2714 \u041c\u043e\u0431 \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d! \u0415\u0449\u0451 \u0438\u043b\u0438 \u0436\u0451\u043b\u0442\u044b\u0439 = \u0434\u0430\u043b\u044c\u0448\u0435.");
                    refreshStep(player, session);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "\u0412\u0432\u0435\u0434\u0438 \u0447\u0438\u0441\u043b\u043e!");
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
        player.sendMessage(ChatColor.RED + "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0437\u043e\u043d\u044b \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430.");
    }

    // ===== Step handlers =====

    private boolean handleSetPos1(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setPos1(player.getLocation().clone());
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0423\u0433\u043e\u043b 1 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d!");
            session.setStep(ZoneWizardStep.SET_POS2);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 2: \u0412\u0441\u0442\u0430\u043d\u044c \u043d\u0430 \u043f\u0440\u043e\u0442\u0438\u0432\u043e\u043f\u043e\u043b\u043e\u0436\u043d\u044b\u0439 \u0443\u0433\u043e\u043b \u0437\u043e\u043d\u044b \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c.");
        }
        return true;
    }

    private boolean handleSetPos2(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.setPos2(player.getLocation().clone());
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0423\u0433\u043e\u043b 2 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d!");
            session.setStep(ZoneWizardStep.ADD_SPAWN_POINTS);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 3: \u0414\u043e\u0431\u0430\u0432\u044c \u0442\u043e\u0447\u043a\u0438 \u0441\u043f\u0430\u0432\u043d\u0430 \u043c\u043e\u0431\u043e\u0432.");
            player.sendMessage(ChatColor.GRAY + "  \u0417\u0435\u043b\u0451\u043d\u044b\u0439 = \u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c, \u0416\u0451\u043b\u0442\u044b\u0439 = \u0434\u0430\u043b\u044c\u0448\u0435");
        }
        return true;
    }

    private boolean handleAddSpawnPoints(Player player, ZoneWizardSession session, int slot) {
        if (slot == 0) {
            session.getSpawnPoints().add(player.getLocation().clone());
            session.incrementSpawnPointCount();
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0422\u043e\u0447\u043a\u0430 \u0441\u043f\u0430\u0432\u043d\u0430 #" + session.getSpawnPointCount() + " \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u0430.");
        } else if (slot == 1) {
            // Next — go to mob pool
            session.setStep(ZoneWizardStep.ADD_MOB_POOL);
            refreshStep(player, session);
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 4: \u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043c\u043e\u0431\u043e\u0432.");
            player.sendMessage(ChatColor.GRAY + "  \u0417\u0435\u043b\u0451\u043d\u044b\u0439 = \u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043c\u043e\u0431\u0430 (\u0447\u0435\u0440\u0435\u0437 \u0447\u0430\u0442)");
            player.sendMessage(ChatColor.GRAY + "  \u0416\u0451\u043b\u0442\u044b\u0439 = \u0434\u0430\u043b\u044c\u0448\u0435");
            if (plugin.getEliteMobsHook().isAvailable()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "  EliteMobs: \u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d");
            }
            if (plugin.getFmmHook().isAvailable()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "  FMM \u043c\u043e\u0434\u0435\u043b\u0438: \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u044b");
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
            player.sendMessage(ChatColor.AQUA + "\u0412\u0432\u0435\u0434\u0438 \u0442\u0438\u043f \u043c\u043e\u0431\u0430 \u0432 \u0447\u0430\u0442:");
            player.sendMessage(ChatColor.GRAY + "  'v' = Vanilla (ZOMBIE, SKELETON...)");
            if (plugin.getEliteMobsHook().isAvailable()) {
                player.sendMessage(ChatColor.GRAY + "  'e' = EliteMobs (\u0444\u0430\u0439\u043b \u0431\u043e\u0441\u0441\u0430.yml)");
            }
        } else if (slot == 1) {
            // Next — go to max mobs
            session.setStep(ZoneWizardStep.SET_MAX_MOBS);
            session.setAwaitingChatInput(true);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 5: \u0412\u0432\u0435\u0434\u0438 \u043c\u0430\u043a\u0441. \u043a\u043e\u043b-\u0432\u043e \u043c\u043e\u0431\u043e\u0432 \u0432 \u0447\u0430\u0442 (\u043d\u0430\u043f\u0440. 15):");
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
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0423\u0433\u043e\u043b 1 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d.");
                return;
            case SET_POS2:
                session.setStep(ZoneWizardStep.ADD_SPAWN_POINTS);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0423\u0433\u043e\u043b 2 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d.");
                return;
            case ADD_SPAWN_POINTS:
                session.setStep(ZoneWizardStep.ADD_MOB_POOL);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0422\u043e\u0447\u043a\u0438 \u0441\u043f\u0430\u0432\u043d\u0430 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d\u044b.");
                return;
            case ADD_MOB_POOL:
                session.setStep(ZoneWizardStep.SET_MAX_MOBS);
                session.setAwaitingChatInput(true);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u041c\u043e\u0431\u044b \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d\u044b. \u0412\u0432\u0435\u0434\u0438 \u043c\u0430\u043a\u0441. \u043c\u043e\u0431\u043e\u0432:");
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
            player.sendMessage(ChatColor.RED + "\u041d\u0435\u043a\u0443\u0434\u0430 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0430\u0442\u044c\u0441\u044f.");
            return;
        }
        session.goBack();
        refreshStep(player, session);
        player.sendMessage(ChatColor.YELLOW + "\u25C0 \u0412\u043e\u0437\u0432\u0440\u0430\u0442: " + stepName(session.getStep()));
    }

    private void finishWizard(Player player, ZoneWizardSession session) {
        session.setStep(ZoneWizardStep.DONE);
        clearHotbar(player);
        sessions.remove(player.getUniqueId());

        // Save zone
        saveZone(session);

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "\u2726 \u0417\u043e\u043d\u0430 '" + session.getZoneId() + "' \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0430!");
        player.sendMessage(ChatColor.GRAY + "\u0422\u043e\u0447\u0435\u043a \u0441\u043f\u0430\u0432\u043d\u0430: " + session.getSpawnPointCount());
        player.sendMessage(ChatColor.GRAY + "\u041c\u043e\u0431\u043e\u0432 \u0432 \u043f\u0443\u043b\u0435: " + session.getMobEntries().size());
        player.sendMessage(ChatColor.GRAY + "\u041c\u0430\u043a\u0441. \u043c\u043e\u0431\u043e\u0432: " + session.getMaxMobs());
        player.sendMessage(ChatColor.GRAY + "\u0422\u0435\u043f\u0435\u0440\u044c \u0443\u043a\u0430\u0436\u0438 \u044d\u0442\u0443 \u0437\u043e\u043d\u0443 \u0432 events.yml: zone: \"" + session.getZoneId() + "\"");
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
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "\u2714 \u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c \u043f\u043e\u0437\u0438\u0446\u0438\u044e"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, ChatColor.GRAY + "\u25C0 \u041d\u0430\u0437\u0430\u0434"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
                break;
            case ADD_SPAWN_POINTS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "+ \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 \u0441\u043f\u0430\u0432\u043d\u0430"));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, ChatColor.YELLOW + "\u25B6 \u0414\u0430\u043b\u044c\u0448\u0435"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, ChatColor.GRAY + "\u25C0 \u041d\u0430\u0437\u0430\u0434"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
                break;
            case ADD_MOB_POOL:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "+ \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043c\u043e\u0431\u0430"));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, ChatColor.YELLOW + "\u25B6 \u0414\u0430\u043b\u044c\u0448\u0435"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, ChatColor.GRAY + "\u25C0 \u041d\u0430\u0437\u0430\u0434"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
                break;
            case SET_MAX_MOBS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "\u2714 \u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e (15)"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, ChatColor.GRAY + "\u25C0 \u041d\u0430\u0437\u0430\u0434"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
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
            case SET_POS1: return "\u0423\u0433\u043e\u043b 1";
            case SET_POS2: return "\u0423\u0433\u043e\u043b 2";
            case ADD_SPAWN_POINTS: return "\u0422\u043e\u0447\u043a\u0438 \u0441\u043f\u0430\u0432\u043d\u0430";
            case ADD_MOB_POOL: return "\u041c\u043e\u0431\u044b";
            case SET_MAX_MOBS: return "\u041c\u0430\u043a\u0441. \u043c\u043e\u0431\u043e\u0432";
            default: return step.name();
        }
    }
}
