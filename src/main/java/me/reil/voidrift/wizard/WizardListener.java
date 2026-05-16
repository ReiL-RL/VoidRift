package me.reil.voidrift.wizard;

import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/**
 * Listens for right-click on wizard items and chat input for zone wizard.
 */
public final class WizardListener implements Listener {

    private final VoidRiftPlugin plugin;

    public WizardListener(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();

        // Portal setup wizard
        if (plugin.getSetupWizard().isInWizard(player.getUniqueId())) {
            boolean handled = plugin.getSetupWizard().handleInteract(player, slot);
            if (handled) event.setCancelled(true);
            return;
        }

        // Zone setup wizard
        if (plugin.getZoneWizard().isInWizard(player.getUniqueId())) {
            boolean handled = plugin.getZoneWizard().handleInteract(player, slot);
            if (handled) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Event wizard chat input
        if (plugin.getEventWizard().isInWizard(player.getUniqueId())) {
            event.setCancelled(true);
            final String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    plugin.getEventWizard().handleChat(player, message);
                }
            });
            return;
        }

        // Zone wizard chat input
        if (plugin.getZoneWizard().isAwaitingChat(player.getUniqueId())) {
            event.setCancelled(true);
            final String message = event.getMessage();
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    plugin.getZoneWizard().handleChat(player, message);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSetupWizard().isInWizard(player.getUniqueId())) {
            plugin.getSetupWizard().cancel(player);
        }
        if (plugin.getZoneWizard().isInWizard(player.getUniqueId())) {
            plugin.getZoneWizard().cancel(player);
        }
        if (plugin.getEventWizard().isInWizard(player.getUniqueId())) {
            plugin.getEventWizard().cancel(player);
        }
    }
}
