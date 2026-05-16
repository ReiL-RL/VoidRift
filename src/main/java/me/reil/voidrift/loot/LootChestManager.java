package me.reil.voidrift.loot;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.zone.ZoneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Manages loot chests in events.
 *
 * Two types:
 * 1. RANDOM — spawn at random locations in zone, stay until looted
 * 2. BOSS — spawn at boss death location (chest or item drop, configurable)
 *
 * Config (events.yml per event):
 *   loot-chests:
 *     random:
 *       enabled: true
 *       count: 3              # how many random chests per event
 *       particle: VILLAGER_HAPPY
 *     boss:
 *       enabled: true
 *       mode: CHEST           # CHEST or DROP
 *       particle: FLAME
 */
public final class LootChestManager {

    private final VoidRiftPlugin plugin;
    private final Random random = new Random();
    private final List<ActiveLootChest> activeChests = new ArrayList<ActiveLootChest>();
    private BukkitTask particleTask;

    public LootChestManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        startParticleTask();
    }

    // === Random chests — spawn at event start, stay until looted ===

    /**
     * Spawn random loot chests in the zone when event starts.
     */
    public void spawnRandomChests(ActiveEvent event) {
        if (!plugin.getConfig().getBoolean("loot-chests.random.enabled", true)) return;

        ZoneDefinition zone = plugin.getZoneManager().getZone(event.getDefinition().getZoneId());
        if (zone == null || zone.getSpawnPoints().isEmpty()) return;

        int count = plugin.getConfig().getInt("loot-chests.random.count", 3);

        for (int i = 0; i < count; i++) {
            // Pick random location within zone bounds
            Location loc = getRandomLocationInZone(zone);
            if (loc == null) continue;

            Block block = loc.getBlock();
            Material originalType = block.getType();
            block.setType(Material.CHEST);

            ActiveLootChest chest = new ActiveLootChest(
                    event.getDefinition().getId(), loc, originalType, ChestType.RANDOM);
            fillChest(chest, event);
            activeChests.add(chest);
        }
    }

    // === Boss chests — spawn on boss kill ===

    /**
     * Called when a boss is killed. Handles loot based on config mode.
     * Modes: CHEST (place chest), DROP (items on ground), KILLER (to killer inventory), TOP_DAMAGE (to top damager)
     */
    public void onBossKill(ActiveEvent event, Location deathLocation, Player killer) {
        if (!plugin.getConfig().getBoolean("loot-chests.boss.enabled", true)) return;
        if (deathLocation == null || deathLocation.getWorld() == null) return;

        String mode = plugin.getConfig().getString("loot-chests.boss.mode", "CHEST").toUpperCase();

        switch (mode) {
            case "DROP":
                dropLootItems(event, deathLocation);
                break;
            case "KILLER":
                if (killer != null) {
                    giveLootToPlayer(event, killer);
                } else {
                    dropLootItems(event, deathLocation);
                }
                break;
            case "TOP_DAMAGE":
                Player topDamager = getTopDamager(event);
                if (topDamager != null) {
                    giveLootToPlayer(event, topDamager);
                } else if (killer != null) {
                    giveLootToPlayer(event, killer);
                } else {
                    dropLootItems(event, deathLocation);
                }
                break;
            default: // CHEST
                spawnBossChest(event, deathLocation);
                break;
        }
    }

    /**
     * Handle player right-clicking a chest. Returns true if it's a loot chest.
     */
    public boolean handleChestOpen(Player player, Location blockLoc) {
        for (ActiveLootChest chest : activeChests) {
            if (isSameBlock(chest.getLocation(), blockLoc)) {
                if (chest.isOnePerPlayer() && chest.hasOpened(player.getUniqueId())) {
                    player.sendMessage(plugin.getLang().msg("messages.loot.already-looted"));
                    return true;
                }

                chest.markOpened(player.getUniqueId());
                openLootInventory(player, chest);

                // Random chests disappear after first loot
                if (chest.getType() == ChestType.RANDOM) {
                    removeChest(chest);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Remove all chests for an event (on event end).
     */
    public void onEventEnd(String eventId) {
        Iterator<ActiveLootChest> it = activeChests.iterator();
        while (it.hasNext()) {
            ActiveLootChest chest = it.next();
            if (chest.getEventId().equals(eventId)) {
                restoreBlock(chest);
                it.remove();
            }
        }
    }

    public void shutdown() {
        if (particleTask != null) particleTask.cancel();
        for (ActiveLootChest chest : activeChests) {
            restoreBlock(chest);
        }
        activeChests.clear();
    }

    // === Private ===

    private void dropLootItems(ActiveEvent event, Location location) {
        LootTable table = plugin.getLootManager().getLootTable(event.getDefinition().getId());
        if (table == null) return;

        for (LootEntry entry : table.getEntries()) {
            if (random.nextDouble() <= entry.getChance()) {
                ItemStack item = new ItemStack(entry.getMaterial(), entry.getAmount());
                Item dropped = location.getWorld().dropItem(location, item);
                dropped.setVelocity(new Vector(
                        (random.nextDouble() - 0.5) * 0.3,
                        0.3,
                        (random.nextDouble() - 0.5) * 0.3));
            }
        }
    }

    private void spawnBossChest(ActiveEvent event, Location deathLocation) {
        Location chestLoc = deathLocation.clone();
        chestLoc.setX(Math.floor(chestLoc.getX()) + 0.5);
        chestLoc.setY(Math.floor(chestLoc.getY()));
        chestLoc.setZ(Math.floor(chestLoc.getZ()) + 0.5);

        Block block = chestLoc.getBlock();
        Material originalType = block.getType();
        block.setType(Material.CHEST);

        ActiveLootChest chest = new ActiveLootChest(
                event.getDefinition().getId(), chestLoc, originalType, ChestType.BOSS);
        fillChest(chest, event);
        activeChests.add(chest);

        for (UUID playerId : event.getParticipants()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                p.sendMessage(plugin.getLang().msg("messages.loot.boss-chest-spawned"));
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
            }
        }
    }

    private void giveLootToPlayer(ActiveEvent event, Player player) {
        LootTable table = plugin.getLootManager().getLootTable(event.getDefinition().getId());
        if (table == null) return;

        List<ItemStack> given = new ArrayList<ItemStack>();
        for (LootEntry entry : table.getEntries()) {
            if (random.nextDouble() <= entry.getChance()) {
                ItemStack item = new ItemStack(entry.getMaterial(), entry.getAmount());
                // Try to add to inventory, drop if full
                java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                if (!overflow.isEmpty()) {
                    for (ItemStack leftover : overflow.values()) {
                        player.getWorld().dropItem(player.getLocation(), leftover);
                    }
                }
                given.add(item);
            }
        }

        if (!given.isEmpty()) {
            player.sendMessage(plugin.getLang().msg("messages.loot.boss-loot-received"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
    }

    private Player getTopDamager(ActiveEvent event) {
        // Use scores as proxy for damage dealt (highest score = most kills/damage)
        UUID topId = null;
        int topScore = 0;
        for (java.util.Map.Entry<UUID, Integer> entry : event.getScores().entrySet()) {
            if (entry.getValue() > topScore) {
                topScore = entry.getValue();
                topId = entry.getKey();
            }
        }
        if (topId != null) {
            return Bukkit.getPlayer(topId);
        }
        return null;
    }

    private void fillChest(ActiveLootChest chest, ActiveEvent event) {
        LootTable table = plugin.getLootManager().getLootTable(event.getDefinition().getId());
        if (table == null) return;

        List<ItemStack> items = new ArrayList<ItemStack>();
        for (LootEntry entry : table.getEntries()) {
            if (random.nextDouble() <= entry.getChance()) {
                items.add(new ItemStack(entry.getMaterial(), entry.getAmount()));
            }
        }
        chest.setLootItems(items);
    }

    private void openLootInventory(Player player, ActiveLootChest chest) {
        String title = chest.getType() == ChestType.BOSS
                ? plugin.getLang().msg("messages.loot.chest-title-boss")
                : plugin.getLang().msg("messages.loot.chest-title-reward");
        Inventory inv = Bukkit.createInventory(null, 27, title);
        List<ItemStack> items = chest.getLootItems();
        if (items != null) {
            for (int i = 0; i < Math.min(items.size(), 27); i++) {
                inv.setItem(i, items.get(i));
            }
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    private void removeChest(ActiveLootChest chest) {
        activeChests.remove(chest);
        restoreBlock(chest);
    }

    private void restoreBlock(ActiveLootChest chest) {
        Location loc = chest.getLocation();
        if (loc.getWorld() != null) {
            Block block = loc.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(chest.getOriginalType());
            }
        }
    }

    private Location getRandomLocationInZone(ZoneDefinition zone) {
        if (zone.getPos1() == null || zone.getPos2() == null) return null;
        Location p1 = zone.getPos1();
        Location p2 = zone.getPos2();
        if (p1.getWorld() == null) return null;

        double x = Math.min(p1.getX(), p2.getX()) + random.nextDouble() * Math.abs(p2.getX() - p1.getX());
        double z = Math.min(p1.getZ(), p2.getZ()) + random.nextDouble() * Math.abs(p2.getZ() - p1.getZ());
        double y = Math.min(p1.getY(), p2.getY());

        Location loc = new Location(p1.getWorld(), Math.floor(x), y, Math.floor(z));
        // Find ground
        Block block = loc.getBlock();
        for (int i = 0; i < 20; i++) {
            if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                if (loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                    return loc;
                }
            }
            loc.add(0, 1, 0);
            block = loc.getBlock();
        }
        return loc;
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    private void startParticleTask() {
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                String randomParticle = plugin.getConfig().getString("loot-chests.random.particle", "VILLAGER_HAPPY");
                String bossParticle = plugin.getConfig().getString("loot-chests.boss.particle", "FLAME");
                for (ActiveLootChest chest : activeChests) {
                    Location loc = chest.getLocation();
                    if (loc.getWorld() == null) continue;
                    String pName = chest.getType() == ChestType.BOSS ? bossParticle : randomParticle;
                    Particle p;
                    try { p = Particle.valueOf(pName); } catch (IllegalArgumentException e) { p = Particle.VILLAGER_HAPPY; }
                    loc.getWorld().spawnParticle(p,
                            loc.getX() + 0.5, loc.getY() + 1.2, loc.getZ() + 0.5,
                            5, 0.3, 0.3, 0.3, 0);
                }
            }
        }, 20L, 5L);
    }

    // === Types ===

    private enum ChestType { RANDOM, BOSS }

    private static final class ActiveLootChest {
        private final String eventId;
        private final Location location;
        private final Material originalType;
        private final ChestType type;
        private final Set<UUID> openedBy = new HashSet<UUID>();
        private List<ItemStack> lootItems;

        ActiveLootChest(String eventId, Location location, Material originalType, ChestType type) {
            this.eventId = eventId;
            this.location = location;
            this.originalType = originalType;
            this.type = type;
        }

        String getEventId() { return eventId; }
        Location getLocation() { return location; }
        Material getOriginalType() { return originalType; }
        ChestType getType() { return type; }
        boolean isOnePerPlayer() { return type == ChestType.BOSS; } // Boss = each player can loot
        boolean hasOpened(UUID playerId) { return openedBy.contains(playerId); }
        void markOpened(UUID playerId) { openedBy.add(playerId); }
        List<ItemStack> getLootItems() { return lootItems; }
        void setLootItems(List<ItemStack> items) { this.lootItems = items; }
    }
}
