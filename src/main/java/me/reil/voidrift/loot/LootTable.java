package me.reil.voidrift.loot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Loot table for an event. Contains entries that are rolled on mob kill.
 */
public final class LootTable {

    private final List<LootEntry> entries;
    private final Random random = new Random();

    public LootTable(List<LootEntry> entries) {
        this.entries = entries != null ? entries : Collections.<LootEntry>emptyList();
    }

    public List<LootEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Roll loot and drop items at the given location.
     */
    public void rollAndDrop(Location location) {
        if (location == null || location.getWorld() == null) return;
        for (LootEntry entry : entries) {
            if (random.nextDouble() <= entry.getChance()) {
                ItemStack item = null;

                // Check for SopItemsCreator item (display name starts with "sop:")
                if (entry.getDisplayName() != null && entry.getDisplayName().startsWith("sop:")) {
                    String sopId = entry.getDisplayName().substring(4);
                    item = getSopItem(sopId);
                    if (item != null) {
                        item.setAmount(entry.getAmount());
                    }
                }

                if (item == null) {
                    item = new ItemStack(entry.getMaterial(), entry.getAmount());
                    if (entry.getDisplayName() != null && !entry.getDisplayName().isEmpty() && !entry.getDisplayName().startsWith("sop:")) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', entry.getDisplayName()));
                            item.setItemMeta(meta);
                        }
                    }
                }

                location.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    /**
     * Try to get a SopItemsCreator item via the plugin's hook.
     */
    private ItemStack getSopItem(String itemId) {
        try {
            Plugin voidRift = Bukkit.getPluginManager().getPlugin("VoidRift");
            if (voidRift != null) {
                Object hook = voidRift.getClass().getMethod("getSopItemsHook").invoke(voidRift);
                if (hook != null) {
                    Object available = hook.getClass().getMethod("isAvailable").invoke(hook);
                    if (Boolean.TRUE.equals(available)) {
                        Object result = hook.getClass().getMethod("getCustomItem", String.class).invoke(hook, itemId);
                        if (result instanceof ItemStack) {
                            return (ItemStack) result;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
