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
                player.sendMessage("\u00a7c\u2718 \u042d\u0442\u0430 \u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u0437\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d\u0430 \u0432\u043e \u0432\u0440\u0435\u043c\u044f \u0441\u043e\u0431\u044b\u0442\u0438\u044f. \u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u043f\u043e\u0440\u0442\u0430\u043b \u0432\u044b\u0445\u043e\u0434\u0430.");
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
