package me.reil.voidrift.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional bridge into SkyBound Core.
 *
 * Uses reflection so VoidRift can run as a standalone plugin when SkyBound is
 * not installed. Feature code should use this bridge instead of importing
 * SkyBound API types directly.
 */
public final class SkyBoundHook {

    private final JavaPlugin plugin;
    private boolean available;
    private Class<?> apiClass;

    public SkyBoundHook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        available = false;
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("SkyBound")) return;
            apiClass = Class.forName("me.reil.skybound.api.SkyBoundAPI");
            Object isAvailable = apiClass.getMethod("isAvailable").invoke(null);
            available = Boolean.TRUE.equals(isAvailable);
            if (available) {
                plugin.getLogger().info("SkyBound Core detected - addon mode active.");
            }
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean depositMoney(Player player, double amount) {
        if (!available || player == null || amount <= 0) return false;
        try {
            Object economy = provider("getEconomyProvider");
            economy.getClass().getMethod("deposit", UUID.class, double.class)
                    .invoke(economy, player.getUniqueId(), amount);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean addIslandXp(Player player, long xp) {
        SkyBoundIsland island = getPlayerIsland(player);
        if (island == null || xp <= 0) return false;
        island.addExperience(xp);
        return true;
    }

    /**
     * Reward-style island bank deposit. This avoids BankProvider.deposit()
     * because that method belongs to player deposit flow and may withdraw money
     * from the player.
     */
    public boolean depositToBank(Player player, double amount) {
        SkyBoundIsland island = getPlayerIsland(player);
        if (island == null || amount <= 0) return false;
        island.setBankBalance(island.getBankBalance() + amount);
        return true;
    }

    /**
     * Reward-style booster activation. Prefers forceActivate() to avoid
     * charging the player; purchase() is a last fallback for old providers.
     */
    public boolean activateBooster(Player player, String boosterId, int durationMinutes) {
        SkyBoundIsland island = getPlayerIsland(player);
        if (island == null || boosterId == null || boosterId.isEmpty()) return false;
        try {
            Object boosterProvider = provider("getBoosterProvider");
            Method isActive = findCompatibleMethod(boosterProvider.getClass(), "isActive", 2);
            if (isActive != null && Boolean.TRUE.equals(isActive.invoke(boosterProvider, island.handle(), boosterId))) {
                return false;
            }

            Method force = findCompatibleMethod(boosterProvider.getClass(), "forceActivate", 2);
            if (force != null && Boolean.TRUE.equals(force.invoke(boosterProvider, island.handle(), boosterId))) {
                return true;
            }

            Method purchase = findCompatibleMethod(boosterProvider.getClass(), "purchase", 3);
            return purchase != null && Boolean.TRUE.equals(purchase.invoke(boosterProvider, player, island.handle(), boosterId));
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void trackMission(Player player, String missionType, String target, int amount) {
        if (!available || player == null || target == null || amount <= 0) return;
        try {
            Object missionProvider = provider("getMissionProvider");
            Class<?> missionTypeClass = Class.forName("me.reil.skybound.api.mission.MissionType");
            Object type;
            try {
                type = Enum.valueOf((Class<Enum>) missionTypeClass.asSubclass(Enum.class), missionType.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = Enum.valueOf((Class<Enum>) missionTypeClass.asSubclass(Enum.class), "CUSTOM");
            }
            missionProvider.getClass()
                    .getMethod("trackAction", UUID.class, missionTypeClass, String.class, int.class)
                    .invoke(missionProvider, player.getUniqueId(), type, target, amount);
        } catch (Throwable ignored) {}
    }

    public SkyBoundIsland getPlayerIsland(Player player) {
        if (!available || player == null) return null;
        try {
            Object islandProvider = provider("getIslandProvider");
            Object island = islandProvider.getClass().getMethod("getPlayerIsland", UUID.class)
                    .invoke(islandProvider, player.getUniqueId());
            return island != null ? new SkyBoundIsland(island) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public SkyBoundIsland getIsland(String islandId) {
        if (!available || islandId == null) return null;
        try {
            Object islandProvider = provider("getIslandProvider");
            Object island = islandProvider.getClass().getMethod("getIsland", String.class)
                    .invoke(islandProvider, islandId);
            return island != null ? new SkyBoundIsland(island) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isOwnIsland(Player player, String islandId) {
        SkyBoundIsland island = getPlayerIsland(player);
        return island != null && island.getId() != null && island.getId().equals(islandId);
    }

    public boolean transferPrestige(String victimIslandId, String attackerIslandId, int amount) {
        if (!available || victimIslandId == null || attackerIslandId == null || amount <= 0) return false;
        try {
            Object prestige = provider("getPrestigeProvider");
            int victimPrestige = ((Number) prestige.getClass()
                    .getMethod("getPrestigeLevel", String.class)
                    .invoke(prestige, victimIslandId)).intValue();
            if (victimPrestige <= 0) return false;

            int transfer = Math.min(amount, victimPrestige);
            int attackerPrestige = ((Number) prestige.getClass()
                    .getMethod("getPrestigeLevel", String.class)
                    .invoke(prestige, attackerIslandId)).intValue();
            int maxPrestige = ((Number) prestige.getClass()
                    .getMethod("getMaxPrestige")
                    .invoke(prestige)).intValue();

            prestige.getClass().getMethod("setPrestigeLevel", String.class, int.class)
                    .invoke(prestige, victimIslandId, victimPrestige - transfer);
            prestige.getClass().getMethod("setPrestigeLevel", String.class, int.class)
                    .invoke(prestige, attackerIslandId, Math.min(maxPrestige, attackerPrestige + transfer));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object api() throws Exception {
        return apiClass.getMethod("get").invoke(null);
    }

    private Object provider(String getter) throws Exception {
        Object api = api();
        return api.getClass().getMethod(getter).invoke(api);
    }

    private Method findCompatibleMethod(Class<?> type, String name, int argCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == argCount) {
                return method;
            }
        }
        return null;
    }
}
