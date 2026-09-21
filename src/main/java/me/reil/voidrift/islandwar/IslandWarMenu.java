package me.reil.voidrift.islandwar;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.integration.SkyBoundIsland;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu showing all attack targets in an active Island War.
 * Click an island icon to teleport to its border.
 */
public final class IslandWarMenu implements Listener {

    private final VoidRiftPlugin plugin;
    private final IslandWarManager warManager;
    /** Player UUID -> island ids displayed in their currently open menu (slot index -> islandId). */
    private final Map<UUID, Map<Integer, String>> openMenus = new HashMap<UUID, Map<Integer, String>>();

    public IslandWarMenu(VoidRiftPlugin plugin, IslandWarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    public void open(Player viewer) {
        if (!plugin.getSkyBoundHook().isAvailable()) {
            viewer.sendMessage(plugin.getLang().msgFor(viewer, "messages.island-war.skybound-unavailable"));
            return;
        }
        if (!warManager.isWarActive()) {
            viewer.sendMessage(plugin.getLang().msgFor(viewer, "messages.island-war.no-active-war"));
            return;
        }

        List<IslandHeart> targets = warManager.getAttackTargets(viewer);
        int rows = Math.max(1, (int) Math.ceil(targets.size() / 9.0));
        int size = Math.min(54, rows * 9);
        if (size < 9) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, title());

        Map<Integer, String> slotMap = new HashMap<Integer, String>();
        int slot = 0;
        for (IslandHeart heart : targets) {
            if (slot >= size) break;

            SkyBoundIsland target = plugin.getSkyBoundHook().getIsland(heart.getIslandId());
            if (target == null) continue;

            ItemStack icon = new ItemStack(Material.BEACON);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String name = target.getName() != null ? target.getName() : heart.getIslandId();
                meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + name);

                List<String> lore = new ArrayList<String>();
                lore.add(plugin.getLang().msgFor(viewer, "messages.island-war.menu.level",
                        vars("level", String.valueOf(target.getLevel()))));
                lore.add(plugin.getLang().msgFor(viewer, "messages.island-war.menu.members",
                        vars("members", String.valueOf(target.getMembers().size()))));
                Map<String, String> hpVars = vars("hp", String.valueOf(heart.getHp()));
                hpVars.put("max", String.valueOf(heart.getMaxHp()));
                lore.add(plugin.getLang().msgFor(viewer, "messages.island-war.menu.heart-hp", hpVars));
                lore.add("");
                lore.add(plugin.getLang().msgFor(viewer, "messages.island-war.menu.attack-hint"));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot, icon);
            slotMap.put(slot, heart.getIslandId());
            slot++;
        }

        openMenus.put(viewer.getUniqueId(), slotMap);
        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!title().equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player p = (Player) event.getWhoClicked();
        Map<Integer, String> slotMap = openMenus.get(p.getUniqueId());
        if (slotMap == null) return;

        String islandId = slotMap.get(event.getRawSlot());
        if (islandId == null) return;

        teleportToAttack(p, islandId);
        p.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    private void teleportToAttack(Player attacker, String targetIslandId) {
        SkyBoundIsland target = plugin.getSkyBoundHook().getIsland(targetIslandId);
        if (target == null) {
            attacker.sendMessage(plugin.getLang().msgFor(attacker, "messages.island-war.target-unavailable"));
            return;
        }
        IslandWarSession session = warManager.findSessionByIsland(targetIslandId);
        IslandHeart heart = session != null ? session.getHeart(targetIslandId) : null;
        if (heart == null || heart.isDestroyed()) {
            attacker.sendMessage(plugin.getLang().msgFor(attacker, "messages.island-war.heart-destroyed"));
            return;
        }

        Location center = target.getCenter();
        if (center == null || center.getWorld() == null) return;

        int distance = plugin.getConfig().getInt("island-war.teleport.spawn-distance", 25);
        Location dest = center.clone().add(distance, 0, 0);
        int worldMax = dest.getWorld().getMaxHeight();
        for (int y = worldMax - 1; y > 0; y--) {
            if (!dest.getWorld().getBlockAt(dest.getBlockX(), y, dest.getBlockZ()).getType().isAir()) {
                dest.setY(y + 1);
                break;
            }
        }
        dest.setX(dest.getBlockX() + 0.5);
        dest.setZ(dest.getBlockZ() + 0.5);

        attacker.teleport(dest);
        attacker.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        attacker.sendTitle(
                plugin.getLang().msgFor(attacker, "messages.island-war.attack-title"),
                plugin.getLang().msgFor(attacker, "messages.island-war.attack-subtitle"),
                10, 50, 20);
    }

    private String title() {
        return plugin.getLang().msg("messages.island-war.menu.title");
    }

    private Map<String, String> vars(String key, String value) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(key, value);
        return vars;
    }
}
