package me.reil.voidrift.integration;

import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Integration with SopItemsCreator plugin.
 * Allows using custom items from SopItemsCreator in loot tables.
 * Item references use "sop:" prefix, e.g. "sop:void_sword"
 */
public final class SopItemsHook {

    private final VoidRiftPlugin plugin;
    private boolean available;
    private Object sopApi;

    public SopItemsHook(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        this.available = false;
        init();
    }

    private void init() {
        Plugin sopPlugin = Bukkit.getPluginManager().getPlugin("SopItemsCreator");
        if (sopPlugin == null) {
            sopPlugin = Bukkit.getPluginManager().getPlugin("SopLib");
        }
        if (sopPlugin != null && sopPlugin.isEnabled()) {
            try {
                // Try to get the API instance via reflection
                sopApi = sopPlugin.getClass().getMethod("getApi").invoke(sopPlugin);
                available = true;
                plugin.getLogger().info("SopItemsCreator integration: enabled");
            } catch (Exception e) {
                // Try alternative access method
                try {
                    Class<?> apiClass = Class.forName("net.enelson.sopli.lib.SopLibApi");
                    sopApi = apiClass.getMethod("getInstance").invoke(null);
                    available = true;
                    plugin.getLogger().info("SopItemsCreator integration: enabled (via SopLibApi)");
                } catch (Exception e2) {
                    plugin.getLogger().warning("SopItemsCreator found but API not accessible: " + e2.getMessage());
                }
            }
        }
    }

    /**
     * Check if SopItemsCreator is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Get a custom item by its ID from SopItemsCreator.
     * @param itemId the item ID (without "sop:" prefix)
     * @return the ItemStack, or null if not found
     */
    public ItemStack getCustomItem(String itemId) {
        if (!available || sopApi == null || itemId == null || itemId.isEmpty()) return null;

        try {
            // Try getItem(String) method
            Object result = sopApi.getClass().getMethod("getItem", String.class).invoke(sopApi, itemId);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception e) {
            // Try alternative method names
            try {
                Object result = sopApi.getClass().getMethod("getCustomItem", String.class).invoke(sopApi, itemId);
                if (result instanceof ItemStack) {
                    return (ItemStack) result;
                }
            } catch (Exception e2) {
                try {
                    Object result = sopApi.getClass().getMethod("createItem", String.class).invoke(sopApi, itemId);
                    if (result instanceof ItemStack) {
                        return (ItemStack) result;
                    }
                } catch (Exception e3) {
                    plugin.getLogger().warning("Failed to get SopItems item '" + itemId + "': " + e3.getMessage());
                }
            }
        }
        return null;
    }
}
