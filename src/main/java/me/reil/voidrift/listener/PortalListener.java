package me.reil.voidrift.listener;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.EventManager;
import me.reil.voidrift.portal.PortalManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles portal teleportation on move and blocks commands in event zones.
 */
public final class PortalListener implements Listener {

    private final VoidRiftPlugin plugin;
    private final PortalManager portalManager;
    private final EventManager eventManager;

    // Commands blocked inside event zone (without voidrift.bypass permission)
    private static final Set<String> BLOCKED_COMMANDS = new HashSet<String>(Arrays.asList(
            "spawn", "home", "tp", "tpa", "tpaccept", "back", "warp",
            "is home", "is go", "island home", "sethome", "delhome"
    ));

    public PortalListener(VoidRiftPlugin plugin, PortalManager portalManager, EventManager eventManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        // Only check if actually moved a block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        portalManager.handlePlayerMove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!portalManager.isPlayerInEvent(player.getUniqueId())) return;
        if (player.hasPermission("voidrift.bypass")) return;

        String message = event.getMessage().toLowerCase().substring(1); // Remove /
        for (String blocked : BLOCKED_COMMANDS) {
            if (message.startsWith(blocked)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLang().msg("messages.portal.blocked-cmd"));
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // If player quits while in event, save their return location
        // They'll be returned on next login (handled elsewhere or just clear state)
        if (portalManager.isPlayerInEvent(event.getPlayer().getUniqueId())) {
            // Just clear — they'll spawn at default on rejoin
            // Could save to file for persistence, but for now just clear
        }
    }
}
