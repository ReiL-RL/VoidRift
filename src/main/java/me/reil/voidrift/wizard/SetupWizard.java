package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.portal.PortalType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2726 \u041c\u0430\u0441\u0442\u0435\u0440 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u043f\u043e\u0440\u0442\u0430\u043b\u043e\u0432: " + ChatColor.YELLOW + eventId);
        player.sendMessage(ChatColor.GRAY + "\u0428\u0430\u0433 1: \u0412\u044b\u0431\u0435\u0440\u0438 \u0442\u0438\u043f \u0432\u0445\u043e\u0434\u0430");
        player.sendMessage(ChatColor.GREEN + "  \u041f\u041a\u041c \u0437\u0435\u043b\u0451\u043d\u044b\u0439 \u0448\u0430\u0440 = \u0421\u0442\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439");
        player.sendMessage(ChatColor.AQUA + "  \u041f\u041a\u041c \u0433\u043e\u043b\u0443\u0431\u043e\u0439 \u0448\u0430\u0440 = \u0414\u0438\u043d\u0430\u043c\u0438\u0447\u0435\u0441\u043a\u0438\u0439");
        player.sendMessage(ChatColor.RED + "  \u041f\u041a\u041c \u043a\u0440\u0430\u0441\u043d\u044b\u0439 = \u041e\u0442\u043c\u0435\u043d\u0430");
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
        player.sendMessage(ChatColor.RED + "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430.");
    }

    // ===== Step handlers =====

    private boolean handleChooseType(Player player, WizardSession session, int slot) {
        if (slot == 0) { // Static
            session.setEntryType(PortalType.ENTRY);
            session.setStep(WizardStep.SET_ENTRY);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 2: \u0412\u0441\u0442\u0430\u043d\u044c \u0433\u0434\u0435 \u0431\u0443\u0434\u0435\u0442 \u043f\u043e\u0440\u0442\u0430\u043b \u0432\u0445\u043e\u0434\u0430 \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c \u043d\u0430 \u0437\u0435\u043b\u0451\u043d\u044b\u0439 \u0431\u043b\u043e\u043a.");
        } else if (slot == 1) { // Dynamic
            session.setEntryType(PortalType.DYNAMIC);
            session.setStep(WizardStep.SET_DEST);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 2: \u0412\u0441\u0442\u0430\u043d\u044c \u043d\u0430 \u0435\u0432\u0435\u043d\u0442-\u043b\u043e\u043a\u0430\u0446\u0438\u0438 (\u043a\u0443\u0434\u0430 \u0422\u041f) \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c.");
        }
        return true;
    }

    private boolean handleSetEntry(Player player, WizardSession session, int slot) {
        if (slot == 0) {
            plugin.getPortalManager().createPortal(session.getEventId(), "entry", session.getEntryType());
            plugin.getPortalManager().setPortalLocation(session.getEventId(), "entry", player.getLocation());
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0412\u0445\u043e\u0434 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d!");
            session.setStep(WizardStep.SET_DEST);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 3: \u0412\u0441\u0442\u0430\u043d\u044c \u043d\u0430 \u0435\u0432\u0435\u043d\u0442-\u043b\u043e\u043a\u0430\u0446\u0438\u0438 \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c.");
        }
        return true;
    }

    private boolean handleSetDest(Player player, WizardSession session, int slot) {
        if (slot == 0) {
            plugin.getPortalManager().createPortal(session.getEventId(), "entry", session.getEntryType());
            plugin.getPortalManager().setPortalDestination(session.getEventId(), "entry", player.getLocation());
            player.sendMessage(ChatColor.GREEN + "\u2714 \u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e!");
            if (session.getEntryType() == PortalType.DYNAMIC) {
                session.setStep(WizardStep.ADD_DYNAMIC_POS);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 3.5: \u0414\u043e\u0431\u0430\u0432\u044c \u0442\u043e\u0447\u043a\u0438 \u043f\u043e\u044f\u0432\u043b\u0435\u043d\u0438\u044f. \u041f\u041a\u041c \u0437\u0435\u043b\u0451\u043d\u044b\u0439 = \u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c, \u0436\u0451\u043b\u0442\u044b\u0439 = \u0434\u0430\u043b\u044c\u0448\u0435.");
            } else {
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 4: \u0412\u0441\u0442\u0430\u043d\u044c \u0433\u0434\u0435 \u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0435\u0432\u0435\u043d\u0442\u0430 \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c.");
            }
        }
        return true;
    }

    private boolean handleAddDynamic(Player player, WizardSession session, int slot) {
        if (slot == 0) { // Add position
            plugin.getPortalManager().addDynamicLocation(session.getEventId(), "entry", player.getLocation());
            session.incrementDynamicCount();
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0422\u043e\u0447\u043a\u0430 #" + session.getDynamicCount() + " \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u0430. \u0415\u0449\u0451 \u0438\u043b\u0438 \u0436\u0451\u043b\u0442\u044b\u0439 = \u0434\u0430\u043b\u044c\u0448\u0435.");
        } else if (slot == 1) { // Next step
            session.setStep(WizardStep.SET_EXIT);
            refreshStep(player, session);
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 4: \u0412\u0441\u0442\u0430\u043d\u044c \u0433\u0434\u0435 \u0432\u044b\u0445\u043e\u0434 \u0438 \u043d\u0430\u0436\u043c\u0438 \u041f\u041a\u041c.");
        }
        return true;
    }

    private boolean handleSetExit(Player player, WizardSession session, int slot) {
        if (slot == 0) {
            plugin.getPortalManager().createPortal(session.getEventId(), "exit", PortalType.EXIT);
            plugin.getPortalManager().setPortalLocation(session.getEventId(), "exit", player.getLocation());
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0412\u044b\u0445\u043e\u0434 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d!");
            // Move to intermediate portals step
            session.setStep(WizardStep.ADD_INTERMEDIATE);
            refreshStep(player, session);
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "\u0428\u0430\u0433 5: \u041f\u0440\u043e\u043c\u0435\u0436\u0443\u0442\u043e\u0447\u043d\u044b\u0435 \u043f\u043e\u0440\u0442\u0430\u043b\u044b (\u0434\u0432\u0443\u0441\u0442\u043e\u0440\u043e\u043d\u043d\u0438\u0435)");
            player.sendMessage(ChatColor.GRAY + "  \u0417\u0435\u043b\u0451\u043d\u044b\u0439 = \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 A");
            player.sendMessage(ChatColor.GRAY + "  \u0413\u043e\u043b\u0443\u0431\u043e\u0439 = \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 B (\u0441\u043e\u0437\u0434\u0430\u0441\u0442 \u043f\u0430\u0440\u0443)");
            player.sendMessage(ChatColor.GRAY + "  \u0416\u0451\u043b\u0442\u044b\u0439 = \u0413\u043e\u0442\u043e\u0432\u043e (\u0437\u0430\u0432\u0435\u0440\u0448\u0438\u0442\u044c)");
            player.sendMessage(ChatColor.GRAY + "  ID: " + session.getEventId() + "_loc1, _loc2...");
        }
        return true;
    }

    private boolean handleAddIntermediate(Player player, WizardSession session, int slot) {
        List<Location> pairs = session.getIntermediatePairs();

        if (slot == 0) {
            // Set point A — store temporarily
            pairs.add(player.getLocation().clone());
            player.sendMessage(ChatColor.GREEN + "\u2714 \u0422\u043e\u0447\u043a\u0430 A \u0437\u0430\u043f\u043e\u043c\u043d\u0435\u043d\u0430. \u0422\u0435\u043f\u0435\u0440\u044c \u0432\u0441\u0442\u0430\u043d\u044c \u043d\u0430 \u0442\u043e\u0447\u043a\u0443 B \u0438 \u043d\u0430\u0436\u043c\u0438 \u0433\u043e\u043b\u0443\u0431\u043e\u0439.");
            return true;
        }

        if (slot == 1) {
            // Set point B — create bidirectional pair
            if (pairs.size() % 2 == 0) {
                // No point A set yet
                player.sendMessage(ChatColor.RED + "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u0442\u043e\u0447\u043a\u0443 A (\u0437\u0435\u043b\u0451\u043d\u044b\u0439)!");
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

            player.sendMessage(ChatColor.GREEN + "\u2714 \u041f\u0430\u0440\u0430 #" + idx + " \u0441\u043e\u0437\u0434\u0430\u043d\u0430! (" + idA + " \u2194 " + idB + ")");
            player.sendMessage(ChatColor.GRAY + "  \u0414\u043e\u0431\u0430\u0432\u044c \u0435\u0449\u0451 \u0438\u043b\u0438 \u043d\u0430\u0436\u043c\u0438 \u0436\u0451\u043b\u0442\u044b\u0439 = \u0413\u043e\u0442\u043e\u0432\u043e.");
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
                // Skip type choice — jump straight to exit setup
                session.setEntryType(PortalType.ENTRY);
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0412\u044b\u0431\u043e\u0440 \u0442\u0438\u043f\u0430 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d. \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u0432\u044b\u0445\u043e\u0434 \u0438\u043b\u0438 \u043f\u0440\u043e\u043f\u0443\u0441\u0442\u0438 \u0434\u0430\u043b\u044c\u0448\u0435.");
                return;
            case SET_ENTRY:
                // Skip entry — go to dest
                session.setStep(WizardStep.SET_DEST);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0412\u0445\u043e\u0434 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d. \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435.");
                return;
            case SET_DEST:
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d\u043e. \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u0432\u044b\u0445\u043e\u0434.");
                return;
            case ADD_DYNAMIC_POS:
                session.setStep(WizardStep.SET_EXIT);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0414\u0438\u043d\u0430\u043c\u0438\u0447\u0435\u0441\u043a\u0438\u0435 \u0442\u043e\u0447\u043a\u0438 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d\u044b. \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438 \u0432\u044b\u0445\u043e\u0434.");
                return;
            case SET_EXIT:
                // Skip exit — go to intermediate
                session.setStep(WizardStep.ADD_INTERMEDIATE);
                refreshStep(player, session);
                player.sendMessage(ChatColor.YELLOW + "\u25B6 \u0412\u044b\u0445\u043e\u0434 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d. \u041f\u0440\u043e\u043c\u0435\u0436\u0443\u0442\u043e\u0447\u043d\u044b\u0435 \u043f\u043e\u0440\u0442\u0430\u043b\u044b.");
                return;
            case ADD_INTERMEDIATE:
                // Skip intermediate — finish
                finishWizard(player, session);
                return;
            default:
                break;
        }
    }

    private void handleBack(Player player, WizardSession session) {
        WizardStep prev = session.getPreviousStep();
        if (prev == null) {
            player.sendMessage(ChatColor.RED + "\u041d\u0435\u043a\u0443\u0434\u0430 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0430\u0442\u044c\u0441\u044f.");
            return;
        }
        session.goBack();
        refreshStep(player, session);
        player.sendMessage(ChatColor.YELLOW + "\u25C0 \u0412\u043e\u0437\u0432\u0440\u0430\u0442 \u043d\u0430 \u043f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0438\u0439 \u0448\u0430\u0433: " + stepName(session.getStep()));
    }

    private void finishWizard(Player player, WizardSession session) {
        session.setStep(WizardStep.DONE);
        clearHotbar(player);
        sessions.remove(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "\u2726 \u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430!");
        if (session.getIntermediateCount() > 0) {
            player.sendMessage(ChatColor.GRAY + "\u041f\u0440\u043e\u043c\u0435\u0436\u0443\u0442\u043e\u0447\u043d\u044b\u0445 \u043f\u043e\u0440\u0442\u0430\u043b\u043e\u0432: " + session.getIntermediateCount() + " \u043f\u0430\u0440(\u044b)");
        }
        player.sendMessage(ChatColor.GRAY + "\u0417\u0430\u043f\u0443\u0441\u0442\u0438: /riftadmin startnow " + session.getEventId());
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
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "\u0421\u0442\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0432\u0445\u043e\u0434"));
                player.getInventory().setItem(1, makeItem(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA + "\u0414\u0438\u043d\u0430\u043c\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0432\u0445\u043e\u0434"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
                break;
            case SET_ENTRY:
            case SET_DEST:
            case SET_EXIT:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "\u2714 \u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c \u043f\u043e\u0437\u0438\u0446\u0438\u044e"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, ChatColor.GRAY + "\u25C0 \u041d\u0430\u0437\u0430\u0434"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
                break;
            case ADD_DYNAMIC_POS:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "+ \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443"));
                player.getInventory().setItem(1, makeItem(Material.YELLOW_CONCRETE, ChatColor.YELLOW + "\u25B6 \u0414\u0430\u043b\u044c\u0448\u0435"));
                player.getInventory().setItem(6, makeItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "\u25B6 \u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c"));
                player.getInventory().setItem(7, makeItem(Material.GRAY_CONCRETE, ChatColor.GRAY + "\u25C0 \u041d\u0430\u0437\u0430\u0434"));
                player.getInventory().setItem(8, makeItem(Material.RED_CONCRETE, ChatColor.RED + "\u041e\u0442\u043c\u0435\u043d\u0430"));
                break;
            case ADD_INTERMEDIATE:
                player.getInventory().setItem(0, makeItem(Material.LIME_CONCRETE, ChatColor.GREEN + "A \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 A"));
                player.getInventory().setItem(1, makeItem(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA + "B \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 B"));
                player.getInventory().setItem(2, makeItem(Material.YELLOW_CONCRETE, ChatColor.YELLOW + "\u2714 \u0413\u043e\u0442\u043e\u0432\u043e"));
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

    private String stepName(WizardStep step) {
        switch (step) {
            case CHOOSE_TYPE: return "\u0412\u044b\u0431\u043e\u0440 \u0442\u0438\u043f\u0430";
            case SET_ENTRY: return "\u0412\u0445\u043e\u0434";
            case SET_DEST: return "\u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435";
            case ADD_DYNAMIC_POS: return "\u0414\u0438\u043d\u0430\u043c\u0438\u0447\u0435\u0441\u043a\u0438\u0435 \u0442\u043e\u0447\u043a\u0438";
            case SET_EXIT: return "\u0412\u044b\u0445\u043e\u0434";
            case ADD_INTERMEDIATE: return "\u041f\u0440\u043e\u043c\u0435\u0436\u0443\u0442\u043e\u0447\u043d\u044b\u0435";
            default: return step.name();
        }
    }
}
