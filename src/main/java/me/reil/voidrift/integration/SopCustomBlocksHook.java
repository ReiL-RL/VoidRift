package me.reil.voidrift.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Optional SopCustomBlocks integration.
 * Allows placing/removing custom blocks in event zones (decorations, objectives, etc.)
 * and generating custom block items for loot.
 */
public final class SopCustomBlocksHook {

    private final JavaPlugin plugin;
    private boolean available;
    private Object blockManager;
    private Method getBlockMethod;
    private Method addBlockMethod;
    private Method breakBlockMethod;
    private Method generateItemMethod;

    public SopCustomBlocksHook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        available = false;
        blockManager = null;
        Plugin customBlocksPlugin = Bukkit.getPluginManager().getPlugin("SopCustomBlocks");
        if (customBlocksPlugin == null || !customBlocksPlugin.isEnabled()) return;

        try {
            Method getBlockManager = customBlocksPlugin.getClass().getMethod("getBlockManager");
            blockManager = getBlockManager.invoke(customBlocksPlugin);
            getBlockMethod = blockManager.getClass().getMethod("getBlock", Location.class);
            addBlockMethod = blockManager.getClass().getMethod("addBlock", String.class, Location.class, float.class, float.class);
            Class<?> customBlockClass = Class.forName("net.enelson.sopcustomblocks.managers.blocks.CustomBlock");
            breakBlockMethod = blockManager.getClass().getMethod("breakBlock", customBlockClass, org.bukkit.entity.Player.class);
            Class<?> utilsClass = Class.forName("net.enelson.sopcustomblocks.utils.Utils");
            generateItemMethod = utilsClass.getMethod("generateItem", String.class);
            available = true;
            plugin.getLogger().info("SopCustomBlocks detected - custom blocks enabled.");
        } catch (Exception e) {
            plugin.getLogger().warning("SopCustomBlocks found but API not compatible: " + e.getMessage());
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * Place a custom block at a location.
     */
    public boolean placeBlock(String blockId, Location location) {
        if (!available || blockId == null || location == null) return false;
        try {
            addBlockMethod.invoke(blockManager, blockId, location, 0.0f, 0.0f);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place custom block '" + blockId + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove a custom block at a location.
     */
    public boolean removeBlock(Location location) {
        if (!available || location == null) return false;
        try {
            Object customBlock = getBlockMethod.invoke(blockManager, location);
            if (customBlock == null) return false;
            breakBlockMethod.invoke(blockManager, customBlock, null);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove custom block: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a location has a custom block.
     */
    public boolean isCustomBlock(Location location) {
        if (!available || location == null) return false;
        try {
            Object customBlock = getBlockMethod.invoke(blockManager, location);
            return customBlock != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate a custom block item (for loot/rewards).
     */
    public ItemStack getItem(String blockId) {
        if (!available || generateItemMethod == null || blockId == null) return null;
        try {
            Object item = generateItemMethod.invoke(null, blockId);
            return item instanceof ItemStack ? ((ItemStack) item).clone() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
