package me.reil.voidrift.reward;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles event rewards (money, commands, island XP via SkyBound hook).
 * Supports cooldown per player per event and Vault economy caching.
 */
public final class RewardManager {

    private final VoidRiftPlugin plugin;
    // playerId -> (eventId -> lastRewardTime)
    private final Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<UUID, Map<String, Long>>();
    // Cached Vault economy instance
    private Object vaultEconomy;
    private boolean vaultChecked;

    public RewardManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        this.vaultChecked = false;
        this.vaultEconomy = null;
    }

    /**
     * Initialize Vault economy cache. Called on plugin enable.
     */
    public void initVault() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp != null) {
                vaultEconomy = rsp.getClass().getMethod("getProvider").invoke(rsp);
            }
        } catch (Exception ignored) {}
        vaultChecked = true;
    }

    public void giveRewards(Player player, ActiveEvent event) {
        EventDefinition def = event.getDefinition();
        int score = event.getScore(player.getUniqueId());

        // Check cooldown
        long cooldownSeconds = plugin.getConfig().getLong("rewards.cooldown-seconds", 3600);
        if (cooldownSeconds > 0 && isOnCooldown(player.getUniqueId(), def.getId(), cooldownSeconds)) {
            long remaining = getRemainingCooldown(player.getUniqueId(), def.getId(), cooldownSeconds);
            long minutes = remaining / 60;
            if (minutes < 1) minutes = 1;
            java.util.Map<String, String> ph = new java.util.LinkedHashMap<String, String>();
            ph.put("minutes", String.valueOf(minutes));
            player.sendMessage(plugin.getLang().msg("cooldown.active", ph));
            return;
        }

        // Record cooldown
        setCooldown(player.getUniqueId(), def.getId());

        // Money (via Vault or SkyBound)
        if (def.getRewardMoney() > 0) {
            if (plugin.getSkyBoundHook().isAvailable()) {
                plugin.getSkyBoundHook().depositMoney(player, def.getRewardMoney());
            } else {
                giveMoneyVault(player, def.getRewardMoney());
            }
        }

        // Island XP (via SkyBound)
        if (def.getRewardIslandXp() > 0 && plugin.getSkyBoundHook().isAvailable()) {
            plugin.getSkyBoundHook().addIslandXp(player, def.getRewardIslandXp());
        }

        // Commands
        for (String cmd : def.getRewardCommands()) {
            String resolved = cmd.replace("{player}", player.getName())
                    .replace("{score}", String.valueOf(score))
                    .replace("{event}", def.getId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        // Track mission progress in SkyBound (allows "Complete X events" missions)
        if (plugin.getSkyBoundHook().isAvailable()) {
            plugin.getSkyBoundHook().trackMission(player, "CUSTOM", "EVENT_COMPLETE", 1);
            plugin.getSkyBoundHook().trackMission(player, "CUSTOM", "EVENT_" + def.getId().toUpperCase(), 1);
        }

        player.sendMessage(plugin.getLang().msg("reward.received", java.util.Collections.singletonMap("event_name", def.getDisplayName())));
        if (def.getRewardMoney() > 0) {
            java.util.Map<String, String> ph = new java.util.LinkedHashMap<String, String>();
            ph.put("amount", String.format("%.0f", def.getRewardMoney()));
            player.sendMessage(plugin.getLang().msg("reward.money", ph));
        }
        if (score > 0) {
            player.sendMessage(plugin.getLang().msg("reward.score", java.util.Collections.singletonMap("score", String.valueOf(score))));
        }
    }

    private boolean isOnCooldown(UUID playerId, String eventId, long cooldownSeconds) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;
        Long lastTime = playerCooldowns.get(eventId);
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) < (cooldownSeconds * 1000L);
    }

    /**
     * Check if a player is on cooldown for a specific event.
     */
    public boolean isOnCooldown(UUID playerId, String eventId) {
        long cooldownSeconds = plugin.getConfig().getLong("rewards.cooldown-seconds", 3600);
        return isOnCooldown(playerId, eventId, cooldownSeconds);
    }

    /**
     * Set cooldown for a player on a specific event (records current time).
     */
    public void setCooldown(UUID playerId, String eventId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            playerCooldowns = new LinkedHashMap<String, Long>();
            cooldowns.put(playerId, playerCooldowns);
        }
        playerCooldowns.put(eventId, System.currentTimeMillis());
    }

    private long getRemainingCooldown(UUID playerId, String eventId, long cooldownSeconds) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return 0;
        Long lastTime = playerCooldowns.get(eventId);
        if (lastTime == null) return 0;
        long elapsed = (System.currentTimeMillis() - lastTime) / 1000L;
        return Math.max(0, cooldownSeconds - elapsed);
    }

    private void giveMoneyVault(Player player, double amount) {
        if (!vaultChecked) {
            initVault();
        }
        if (vaultEconomy != null) {
            try {
                vaultEconomy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                        .invoke(vaultEconomy, player, amount);
            } catch (Exception ignored) {}
        }
    }
}
