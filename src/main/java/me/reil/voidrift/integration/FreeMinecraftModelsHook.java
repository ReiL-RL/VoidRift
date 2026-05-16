package me.reil.voidrift.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Optional FreeMinecraftModels integration.
 * Applies custom models to entities via DynamicEntity.create(entityID, livingEntity).
 *
 * FMM 2.5 API:
 *   DynamicEntity.create(String entityID, LivingEntity livingEntity) -> DynamicEntity
 *
 * Note: EliteMobs bosses with 'customModel' in their yml get models automatically
 * via FMM's own EliteMobsEntityEnricher. This hook is for vanilla mobs.
 */
public final class FreeMinecraftModelsHook {

    private final JavaPlugin plugin;
    private boolean available;
    private Method createMethod;

    public FreeMinecraftModelsHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("FreeMinecraftModels");
        if (available) {
            try {
                Class<?> dynamicEntityClass = Class.forName(
                        "com.magmaguy.freeminecraftmodels.customentity.DynamicEntity");
                createMethod = dynamicEntityClass.getMethod("create",
                        String.class, LivingEntity.class);
                plugin.getLogger().info("FreeMinecraftModels detected - custom models enabled.");
            } catch (Exception e) {
                plugin.getLogger().warning("FreeMinecraftModels found but API not compatible: " + e.getMessage());
                available = false;
            }
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * Apply a custom model to a living entity.
     * The model must exist in FMM (imported from .bbmodel).
     *
     * @param entity the entity to apply the model to
     * @param modelId the model id (filename without extension, e.g., "skybound_void_reaver")
     * @return true if model was applied successfully
     */
    public boolean applyModel(Entity entity, String modelId) {
        if (!available || entity == null || modelId == null || modelId.isEmpty()) return false;
        if (!(entity instanceof LivingEntity)) return false;
        try {
            Object result = createMethod.invoke(null, modelId, (LivingEntity) entity);
            return result != null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply FMM model '" + modelId + "': " + e.getMessage());
            return false;
        }
    }
}
