package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.portal.PortalType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step-by-step portal setup wizard using hotbar items.
 *
 * Steps:
 * 1. Choose type (static / dynamic)
 * 2. Set entry position (if static)
 * 3. Set destination (event location)
 * 3.5. Add dynamic spawn points (if dynamic)
 * 4. Set exit position
 * 5. Add intermediate portals (bidirectional, auto-id)
 * 6. Done
 *
 * Hotbar layout per step:
 *  slot 0 = primary action (confirm / add)
 *  slot 1 = secondary action (next / type choice)
 *  slot 6 = Skip (skip current step)
 *  slot 7 = Back (return to previous step)
 *  slot 8 = Cancel
 */
public final class SetupWizard {

    private final VoidRiftPlugin plugin;
    private final Map<UUID, WizardSession> sessions = new LinkedHashMap<UUID, WizardSession>();

    public SetupWizard(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== Public API =====

    public void start(Player player, String eventId) {
        WizardSession session = new WizardSession(eventId, WizardStep.CHOOSE_TYPE);
        sessions.put(player.getUniqueId(), session);

        clearHotbar(player);
        giveStepItems(player, session);

        Map<String, String> v = new HashMap<String, String>();
        v.put("event", eventId);

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.wizard.title", v));
        player.sendMessage(plugin.getLang().msg("messages.wizard.step1-choose"));
        player.sendMessage(plugin.getLang().msg("messages.wizard.step1-static"));
        player.sendMessage(plugin.getLang().msg("messages.wizard.step1-dynamic"));
        player.sendMessage(plugin.getLang().msg("messages.wizard.step1-cancel"));
        player.sendMessage("");
    }

    /**
     * Handle right-click on wizard item.
     * @return true if handled
     */
    public boolean handleInteract(Player player, int slot) {
        WizardSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;

        // Universal buttons
        if (slot == 8) { cancel(player); return true; }
        if (slot == 7) { handleBack(player, session); return true; }
        if (slot == 6) { handleSkip(player, session); return true; }

        switch (session.getStep()) {
            case CHOOSE_TYPE:
                return handleChooseType(player, session, slot);
            case SET_ENTRY:
                return handleSetEntry(player, session, slot);
            case SET_DEST:
                return handleSetDest(player, session, slot);
            case ADD_DYNAMIC_POS:
                return handleAddDynamic(player, session, slot);
            case SET_EXIT:
                return handleSetExit(player, session, slot);
            case ADD_INTERMEDIATE:
                return handleAddIntermediate(player, session, slot);
            default:
                return false;
        }
    }

    public boolean isInWizard(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
        clearHotbar(player);
        player.sendMessage(plugin.getLang().msg("messages.wizard.cancelled"));
    }

    // ===== Step handlers =====

    private boolean handleChooseType(Player player, WizardSession session, int slot) {
        if (slot == 0) { // Static
            session.setEntryType(PortalType.ENTRY);
            session.setStep(WizardStep.SET_ENTRY);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.wizard.step2-entry"));
        } else if (slot == 1) { // Dynamic
            session.setEntryType(PortalType.DYNAMIC);
            session.setStep(WizardStep.SET_DEST);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.wizard.step2-dynamic"));
        }
        return true;
    }

    private boolean handleSetEntry(Player player, WizardSession session, int slot) {
        if (slot == 0) {
            plugin.getPortalManager().createPortal(session.getEventId(), "entry", session.getEntryType());
            plugin.getPortalManager().setPortalLocation(session.getEventId(), "entry", player.getLocation());
            player.sendMessage(plugin.getLang().msg("messages.wizard.entry-set"));
            session.setStep(WizardStep.SET_DEST);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.wizard.step3-dest"));
        }
        return true;
    }

    private boolean handleSetDest(Player player, WizardSession session, int slot) {
        if (slot == 0) {
            plugin.getPortalManager().createPortal(session.getEventId(), "entry", session.getEntryType());
            plugin.getPortalManager().setPortalDestination(session.getEventId(), "entry", player.getLocation());
            player.sendMessage(plugin.getLang().msg("messages.wizard.dest-set"));
            if (session.getEntryType() == PortalType.DYNAMIC) {
                session.setStep(WizardStep.ADD_DYNAMIC_POS);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.step35-dynamic"));
            } else {
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.step4-exit"));
            }
        }
        return true;
    }

    private boolean handleAddDynamic(Player player, WizardSession session, int slot) {
        if (slot == 0) { // Add position
            plugin.getPortalManager().addDynamicLocation(session.getEventId(), "entry", player.getLocation());
            session.incrementDynamicCount();
            Map<String, String> v = new HashMap<String, String>();
            v.put("count", String.valueOf(session.getDynamicCount()));
            player.sendMessage(plugin.getLang().msg("messages.wizard.dynamic-point-added", v));
        } else if (slot == 1) { // Next step
            session.setStep(WizardStep.SET_EXIT);
            refreshStep(player, session);
            player.sendMessage(plugin.getLang().msg("messages.wizard.step4-exit"));
        }
        return true;
    }

    private boolean handleSetExit(Player player, WizardSession session, int slot) {
        if (slot == 0) {
            plugin.getPortalManager().createPortal(session.getEventId(), "exit", PortalType.EXIT);
            plugin.getPortalManager().setPortalLocation(session.getEventId(), "exit", player.getLocation());
            player.sendMessage(plugin.getLang().msg("messages.wizard.exit-set"));
            // Move to intermediate portals step
            session.setStep(WizardStep.ADD_INTERMEDIATE);
            refreshStep(player, session);

            Map<String, String> v = new HashMap<String, String>();
            v.put("event", session.getEventId());

            player.sendMessage("");
            player.sendMessage(plugin.getLang().msg("messages.wizard.step5-intermediate"));
            player.sendMessage(plugin.getLang().msg("messages.wizard.step5-hint-a"));
            player.sendMessage(plugin.getLang().msg("messages.wizard.step5-hint-b"));
            player.sendMessage(plugin.getLang().msg("messages.wizard.step5-hint-done"));
            player.sendMessage(plugin.getLang().msg("messages.wizard.step5-ids", v));
        }
        return true;
    }

    private boolean handleAddIntermediate(Player player, WizardSession session, int slot) {
        List<Location> pairs = session.getIntermediatePairs();

        if (slot == 0) {
            // Set point A — store temporarily
            pairs.add(player.getLocation().clone());
            player.sendMessage(plugin.getLang().msg("messages.wizard.point-a-set"));
            return true;
        }

        if (slot == 1) {
            // Set point B — create bidirectional pair
            if (pairs.size() % 2 == 0) {
                // No point A set yet
                player.sendMessage(plugin.getLang().msg("messages.wizard.point-a-first"));
                return true;
            }
            pairs.add(player.getLocation().clone());
            session.incrementIntermediateCount();
            int idx = session.getIntermediateCount();

            // Auto-generate IDs: eventId_loc1, eventId_loc2...
            String idA = session.getEventId() + "_loc" + idx;
            String idB = session.getEventId() + "_loc" + idx + "_back";

            Location locA = pairs.get(pairs.size() - 2);
            Location locB = pairs.get(pairs.size() - 1);

            // Portal A->B
            plugin.getPortalManager().createPortal(session.getEventId(), idA, PortalType.INTERMEDIATE);
            plugin.getPortalManager().setPortalLocation(session.getEventId(), idA, locA);
            plugin.getPortalManager().setPortalDestination(session.getEventId(), idA, locB);

            // Portal B->A (reverse direction — bidirectional)
            plugin.getPortalManager().createPortal(session.getEventId(), idB, PortalType.INTERMEDIATE);
            plugin.getPortalManager().setPortalLocation(session.getEventId(), idB, locB);
            plugin.getPortalManager().setPortalDestination(session.getEventId(), idB, locA);

            Map<String, String> v = new HashMap<String, String>();
            v.put("idx", String.valueOf(idx));
            v.put("idA", idA);
            v.put("idB", idB);
            player.sendMessage(plugin.getLang().msg("messages.wizard.pair-created", v));
            player.sendMessage(plugin.getLang().msg("messages.wizard.pair-hint"));
            return true;
        }

        if (slot == 2) {
            // Done — finish wizard
            finishWizard(player, session);
            return true;
        }

        return true;
    }

    // ===== Skip & Back =====

    private void handleSkip(Player player, WizardSession session) {
        switch (session.getStep()) {
            case CHOOSE_TYPE:
                session.setEntryType(PortalType.ENTRY);
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.skip-type"));
                return;
            case SET_ENTRY:
                session.setStep(WizardStep.SET_DEST);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.skip-entry"));
                return;
            case SET_DEST:
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.skip-dest"));
                return;
            case ADD_DYNAMIC_POS:
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.skip-dynamic"));
                return;
            case SET_EXIT:
                session.setStep(WizardStep.ADD_INTERMEDIATE);
                refreshStep(player, session);
                player.sendMessage(plugin.getLang().msg("messages.wizard.skip-exit"));
                return;
            case ADD_INTERMEDIATE:
                finishWizard(player, session);
                return;
            default:
                break;
        }
    }

    private void handleBack(Player player, WizardSession session) {
        WizardStep prev = session.getPreviousStep();
        if (prev == null) {
            player.sendMessage(plugin.getLang().msg("messages.wizard.back-nowhere"));
            return;
        }
        session.goBack();
        refreshStep(player, session);
        Map<String, String> v = new HashMap<String, String>();
        v.put("step", stepName(session.getStep()));
        player.sendMessage(plugin.getLang().msg("messages.wizard.back-to", v));
    }

    private void finishWizard(Player player, WizardSession session) {
        session.setStep(WizardStep.DONE);
        clearHotbar(player);
        sessions.remove(player.getUniqueId());

        Map<String, String> v = new HashMap<String, String>();
        v.put("event", session.getEventId());
        v.put("count", String.valueOf(session.getIntermediateCount()));

        player.sendMessage("");
        player.sendMessage(plugin.getLang().msg("messages.wizard.finished"));
        if (session.getIntermediateCount() > 0) {
            player.sendMessage(plugin.getLang().msg("messages.wizard.finished-intermediate", v));
        }
        player.sendMessage(plugin.getLang().msg("messages.wizard.finished-hint", v));
        player.sendMessage("");
    }

    // ===== UI helpers =====

    private void refreshStep(Player player, WizardSession session) {
        clearHotbar(player);
        giveStepItems(player, session);
    }

    private void giveStepItems(Player player, WizardSession session) {
        switch (session.getStep()) {
            case CHOOSE_TYPE:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.wizard.btn-static")));
                player.getInventory().setItem(1, makeItem(Material.LIGHT_BLUE_CONCRETE, plugin.getLang().msg("messages.wizard.btn-dynamic")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.wizard.btn-skip")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.wizard.btn-cancel")));
                break;
            case SET_ENTRY:
            case SET_DEST:
            case SET_EXIT:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.wizard.btn-confirm")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.wizard.btn-cancel")));
                break;
            case ADD_DYNAMIC_POS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.wizard.btn-add-point")));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.wizard.btn-next")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.wizard.btn-cancel")));
                break;
            case ADD_INTERMEDIATE:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, plugin.getLang().msg("messages.wizard.btn-point-a")));
                player.getInventory().setItem(1, makeItem(Material.LIGHT_BLUE_CONCRETE, plugin.getLang().msg("messages.wizard.btn-point-b")));
                player.getInventory().setItem(2, makeItem(Material.YELLOW_CONCRETE, plugin.getLang().msg("messages.wizard.btn-done")));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, plugin.getLang().msg("messages.wizard.btn-skip")));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, plugin.getLang().msg("messages.wizard.btn-back")));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, plugin.getLang().msg("messages.wizard.btn-cancel")));
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

    private String stepName(WizardStep step) {
        switch (step) {
            case CHOOSE_TYPE: return plugin.getLang().msg("messages.wizard.step-choose-type");
            case SET_ENTRY: return plugin.getLang().msg("messages.wizard.step-entry");
            case SET_DEST: return plugin.getLang().msg("messages.wizard.step-dest");
            case ADD_DYNAMIC_POS: return plugin.getLang().msg("messages.wizard.step-dynamic-pos");
            case SET_EXIT: return plugin.getLang().msg("messages.wizard.step-exit");
            case ADD_INTERMEDIATE: return plugin.getLang().msg("messages.wizard.step-intermediate");
            default: return step.name();
        }
    }
}
