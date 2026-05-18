package me.reil.voidrift.integration;

import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.bank.BankProvider;
import me.reil.skybound.api.booster.BoosterProvider;
import me.reil.skybound.api.economy.EconomyProvider;
import me.reil.skybound.api.island.Island;
import me.reil.skybound.api.island.IslandProvider;
import me.reil.skybound.api.mission.MissionProvider;
import me.reil.skybound.api.mission.MissionType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hook into SkyBound Core (optional).
 * When available, rewards go to island bank/XP, missions are tracked,
 * and boosters can be activated.
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

    /**
     * Deposit money to player's account via SkyBound economy.
     */
    public void depositMoney(Player player, double amount) {
        if (!available) return;
        try {
            SkyBoundAPI.get().getEconomyProvider().deposit(player.getUniqueId(), amount);
        } catch (Exception ignored) {}
    }

    /**
     * Add XP to the player's island.
     */
    public void addIslandXp(Player player, long xp) {
        if (!available) return;
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
            if (island != null) {
                island.addExperience(xp);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Deposit money to the player's island bank.
     */
    public void depositToBank(Player player, double amount) {
        if (!available) return;
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
            if (island != null) {
                SkyBoundAPI.get().getBankProvider().deposit(player, island, amount);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Activate a booster for the player's island.
     * Uses the purchase flow which handles cost and activation.
     *
     * @param player       the player activating the booster
     * @param boosterId    the booster type id (e.g. "xp_boost", "generator_boost")
     * @param durationMinutes ignored — duration is defined in booster config
     * @return true if the booster was activated successfully
     */
    public boolean activateBooster(Player player, String boosterId, int durationMinutes) {
        if (!available) return false;
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
            if (island == null) return false;
            return SkyBoundAPI.get().getBoosterProvider().purchase(player, island, boosterId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Track mission progress for the player in SkyBound's mission system.
     * This allows SkyBound missions like "Complete 3 events" to work.
     *
     * @param player      the player
     * @param missionType the mission type (e.g. "CUSTOM")
     * @param target      the action target (e.g. "EVENT_COMPLETE")
     * @param amount      progress amount
     */
    public void trackMission(Player player, String missionType, String target, int amount) {
        if (!available) return;
        try {
            // Use reflection to call MissionManager.trackAction since MissionProvider
            // doesn't expose trackAction directly — it's on the implementation.
            // We use addProgress as the API-level method.
            MissionType type;
            try {
                type = MissionType.valueOf(missionType.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = MissionType.CUSTOM;
            }

            // Access the MissionProvider and try to call trackAction via reflection
            // since the API interface only has addProgress(UUID, String, int)
            Object missionProvider = SkyBoundAPI.get().getMissionProvider();
            try {
                Method trackMethod = missionProvider.getClass().getMethod("trackAction",
                        UUID.class, MissionType.class, String.class, int.class);
                trackMethod.invoke(missionProvider, player.getUniqueId(), type, target, amount);
            } catch (NoSuchMethodException e) {
                // Fallback: not available in this version
                plugin.getLogger().fine("SkyBound trackAction not available, skipping mission tracking.");
            }
        } catch (Exception ignored) {}
    }
}
