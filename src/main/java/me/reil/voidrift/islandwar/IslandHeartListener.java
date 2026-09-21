package me.reil.voidrift.islandwar;

import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Handles block break attempts on Island Hearts during a war.
 * - Members of the heart's island cannot break it
 * - Outsiders damage the heart through HP
 * - Explosions never destroy hearts
 */
public final class IslandHeartListener implements Listener {

    private final VoidRiftPlugin plugin;
    private final IslandWarManager warManager;

    public IslandHeartListener(VoidRiftPlugin plugin, IslandWarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        IslandHeart heart = warManager.findHeartAt(loc);
        if (heart == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!plugin.getSkyBoundHook().isAvailable()) return;

        if (plugin.getSkyBoundHook().isOwnIsland(player, heart.getIslandId())) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.island-war.own-heart"));
            return;
        }

        long now = System.currentTimeMillis();
        long since = now - heart.getLastHitAt();
        if (since < 200L) return;

        warManager.damageHeart(heart, player, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> warManager.findHeartAt(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> warManager.findHeartAt(b.getLocation()) != null);
    }
}
