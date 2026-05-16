package me.reil.voidrift.gui;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI menu showing all event definitions.
 * Active events = ENDER_EYE, inactive = ENDER_PEARL.
 * Info only — join via portal.
 */
public final class EventGui implements Listener {

    private final VoidRiftPlugin plugin;
    private String guiTitle;

    public EventGui(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        this.guiTitle = plugin.getLang().msg("messages.gui.title");
    }

    public void open(Player player) {
        Collection<EventDefinition> defs = plugin.getEventManager().getDefinitions();
        int size = Math.max(9, ((defs.size() / 9) + 1) * 9);
        if (size > 54) size = 54;

        Inventory inv = Bukkit.createInventory(null, size, guiTitle);

        int slot = 0;
        for (EventDefinition def : defs) {
            if (slot >= size) break;
            ActiveEvent active = plugin.getEventManager().getActiveEvent(def.getId());
            boolean isActive = active != null && !active.isFinished();

            Material mat = isActive ? Material.ENDER_EYE : Material.ENDER_PEARL;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', def.getDisplayName()));
                List<String> lore = new ArrayList<String>();
                lore.add(ChatColor.GRAY + def.getDescription());
                lore.add("");
                if (isActive) {
                    lore.add(plugin.getLang().msg("messages.gui.status-active"));
                    Map<String, String> pv = new HashMap<String, String>();
                    pv.put("count", active.getParticipants().size() + "/" + def.getMaxPlayers());
                    lore.add(plugin.getLang().msg("messages.gui.players", pv));
                    Map<String, String> tv = new HashMap<String, String>();
                    tv.put("time", formatTime(active.getRemainingSeconds()));
                    lore.add(plugin.getLang().msg("messages.gui.time-remaining", tv));
                } else {
                    lore.add(plugin.getLang().msg("messages.gui.status-inactive"));
                }
                lore.add("");
                lore.add(plugin.getLang().msg("messages.gui.enter-via-portal"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slot++;
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(guiTitle)) {
            event.setCancelled(true);
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
