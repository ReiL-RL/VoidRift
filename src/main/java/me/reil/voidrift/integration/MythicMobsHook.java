package me.reil.voidrift.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Optional MythicMobs integration.
 * Spawns MythicMobs mobs in event zones via reflection.
 */
public final class MythicMobsHook {

    private final JavaPlugin plugin;
    private boolean available;
    private Object mythicMobsInstance;
    private Method getMobManagerMethod;
    private Method spawnMobMethod;

    public MythicMobsHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        if (available) {
            try {
                // MythicMobs 5.x API via reflection
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Method instMethod = mythicBukkitClass.getMethod("inst");
                mythicMobsInstance = instMethod.invoke(null);

                getMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");

                // MobManager.spawnMob(String mobName, Location location)
                Object mobManager = getMobManagerMethod.invoke(mythicMobsInstance);
                Class<?> mobManagerClass = mobManager.getClass();
                // Try to find spawnMob method
                for (Method m : mobManagerClass.getMethods()) {
                    if ("spawnMob".equals(m.getName()) && m.getParameterTypes().length == 2) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params[0] == String.class && params[1] == Location.class) {
                            spawnMobMethod = m;
                            break;
                        }
                    }
                }

                if (spawnMobMethod == null) {
                    // Try alternative: MythicMobs 4.x API
                    try {
                        Class<?> mm4Class = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
                        Method mm4Inst = mm4Class.getMethod("inst");
                        mythicMobsInstance = mm4Inst.invoke(null);
                        Method apiHelper = mm4Class.getMethod("getAPIHelper");
                        Object helper = apiHelper.invoke(mythicMobsInstance);
                        for (Method m : helper.getClass().getMethods()) {
                            if ("spawnMythicMob".equals(m.getName())) {
                                spawnMobMethod = m;
                                mythicMobsInstance = helper;
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (spawnMobMethod != null) {
                    plugin.getLogger().info("MythicMobs detected - mob spawning enabled.");
                } else {
                    plugin.getLogger().warning("MythicMobs found but spawn API not compatible.");
                    available = false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("MythicMobs found but API not compatible: " + e.getMessage());
                available = false;
            }
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * Spawn a MythicMobs mob at a location.
     * @param mobName the internal MythicMobs mob name
     * @param location spawn location
     * @return the spawned entity, or null if failed
     */
    public Entity spawnMob(String mobName, Location location) {
        if (!available || spawnMobMethod == null) return null;
        try {
            Object result;
            if (spawnMobMethod.getParameterTypes().length == 2) {
                result = spawnMobMethod.invoke(mythicMobsInstance, mobName, location);
            } else {
                // 4.x API: spawnMythicMob(String, Location, int level)
                result = spawnMobMethod.invoke(mythicMobsInstance, mobName, location, 1);
            }
            if (result instanceof Entity) {
                return (Entity) result;
            }
            // MythicMobs 5.x returns ActiveMob, try to get bukkit entity
            if (result != null) {
                try {
                    Method getEntity = result.getClass().getMethod("getEntity");
                    Object bukkitEntity = getEntity.invoke(result);
                    if (bukkitEntity != null) {
                        Method getBukkitEntity = bukkitEntity.getClass().getMethod("getBukkitEntity");
                        Object entity = getBukkitEntity.invoke(bukkitEntity);
                        if (entity instanceof Entity) return (Entity) entity;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn MythicMobs mob '" + mobName + "': " + e.getMessage());
        }
        return null;
    }
}
