package me.reil.voidrift.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Optional EliteMobs integration.
 * Spawns EliteMobs custom bosses in event zones via reflection.
 *
 * Supports:
 * - Spawning bosses by config file name
 * - Tracking spawned bosses per event
 * - Removing bosses on event end
 * - Boss death detection (for score tracking)
 */
public final class EliteMobsHook {

    private final JavaPlugin plugin;
    private boolean available;
    private Class<?> customBossClass;
    private Method createMethod;
    private Method spawnMethod;
    private Method getLivingEntityMethod;
    private Method removeMethod;

    public EliteMobsHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("EliteMobs");
        if (available) {
            try {
                customBossClass = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
                createMethod = customBossClass.getMethod("createCustomBossEntity", String.class);
                spawnMethod = customBossClass.getMethod("spawn", Location.class, boolean.class);
                getLivingEntityMethod = customBossClass.getMethod("getLivingEntity");
                // remove() is optional — may not exist in all versions
                try {
                    removeMethod = customBossClass.getMethod("remove");
                } catch (NoSuchMethodException ignored) {}
                plugin.getLogger().info("EliteMobs detected - boss spawning enabled.");
            } catch (Exception e) {
                plugin.getLogger().warning("EliteMobs found but API not compatible: " + e.getMessage());
                available = false;
            }
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * Spawn an EliteMobs boss at a location.
     * @param bossFileName the boss config file name (e.g., "skybound_void_reaver.yml")
     * @param location spawn location
     * @return the spawned entity, or null if failed
     */
    public Entity spawnBoss(String bossFileName, Location location) {
        if (!available) return null;
        try {
            Object bossEntity = createMethod.invoke(null, bossFileName);
            if (bossEntity == null) {
                plugin.getLogger().warning("EliteMobs returned null for boss: " + bossFileName);
                return null;
            }
            spawnMethod.invoke(bossEntity, location, false);
            Object living = getLivingEntityMethod.invoke(bossEntity);
            if (living instanceof Entity) {
                return (Entity) living;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn EliteMobs boss '" + bossFileName + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Remove an EliteMobs boss entity cleanly (despawn + cleanup).
     * Falls back to regular entity removal if EM API fails.
     */
    public void removeBoss(Entity entity) {
        if (!available || entity == null) return;
        try {
            // Try to find the CustomBossEntity for this living entity and remove it properly
            Class<?> managerClass = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
            Method getCustomBoss = managerClass.getMethod("getCustomBossEntity", UUID.class);
            Object boss = getCustomBoss.invoke(null, entity.getUniqueId());
            if (boss != null) {
                removeMethod.invoke(boss);
                return;
            }
        } catch (Exception ignored) {}
        // Fallback
        if (!entity.isDead()) entity.remove();
    }

    /**
     * Check if an entity is an EliteMobs boss.
     */
    public boolean isEliteMob(Entity entity) {
        if (!available || entity == null) return false;
        try {
            Class<?> managerClass = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
            Method getCustomBoss = managerClass.getMethod("getCustomBossEntity", UUID.class);
            Object boss = getCustomBoss.invoke(null, entity.getUniqueId());
            return boss != null;
        } catch (Exception e) {
            return false;
        }
    }
}
