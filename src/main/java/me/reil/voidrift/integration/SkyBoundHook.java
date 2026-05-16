package me.reil.voidrift.integration;

import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.economy.EconomyProvider;
import me.reil.skybound.api.island.Island;
import me.reil.skybound.api.island.IslandProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hook into SkyBound Core (optional).
 * When available, rewards go to island bank/XP.
 */
public final class SkyBoundHook {

    private final JavaPlugin plugin;
    private boolean available;

    public SkyBoundHook(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            this.available = SkyBoundAPI.isAvailable();
        } catch (NoClassDefFoundError e) {
            this.available = false;
        }
        if (available) {
            plugin.getLogger().info("SkyBound Core detected - addon mode active.");
        }
    }

    public boolean isAvailable() { return available; }

    public void depositMoney(Player player, double amount) {
        if (!available) return;
        try {
            SkyBoundAPI.get().getEconomyProvider().deposit(player.getUniqueId(), amount);
        } catch (Exception ignored) {}
    }

    public void addIslandXp(Player player, long xp) {
        if (!available) return;
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
            if (island != null) {
                island.addExperience(xp);
            }
        } catch (Exception ignored) {}
    }
}

