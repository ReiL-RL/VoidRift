package me.reil.voidrift.listener;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventManager;
import me.reil.voidrift.loot.LootTable;
import me.reil.voidrift.objective.ObjectiveTracker;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens to Bukkit events and feeds them into ObjectiveTracker.
 */
public final class EventListener implements Listener {

    private final VoidRiftPlugin plugin;
    private final EventManager eventManager;

    public EventListener(VoidRiftPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(killer.getUniqueId())) {
                String eventId = active.getDefinition().getId();
                String mobType = entity.getType().name();
                // Check if it's an EliteMobs boss
                String bossFile = null;
                if (plugin.getEliteMobsHook().isAvailable() && plugin.getEliteMobsHook().isEliteMob(entity)) {
                    bossFile = getBossFileName(entity);
                }
                eventManager.onMobKillDetailed(killer.getUniqueId(), eventId, mobType, bossFile);

                // Boss kill — spawn boss loot chest
                if (bossFile != null) {
                    plugin.getLootChestManager().onBossKill(active, entity.getLocation(), killer);
                }

                // Roll loot table
                LootTable lootTable = eventManager.getLootTable(eventId);
                if (lootTable != null) {
                    lootTable.rollAndDrop(entity.getLocation());
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                String eventId = active.getDefinition().getId();
                plugin.getObjectiveTracker().onPlayerDeath(eventId, player.getUniqueId());
                // Record death stat
                if (plugin.getStatsManager() != null) {
                    plugin.getStatsManager().addDeath(player.getUniqueId(), eventId);
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                plugin.getObjectiveTracker().onDamageDealt(active.getDefinition().getId(), player.getUniqueId(), event.getFinalDamage());
                break;
            }
        }
        // Damage taken
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            for (ActiveEvent active : eventManager.getActiveEvents()) {
                if (active.isParticipant(victim.getUniqueId())) {
                    plugin.getObjectiveTracker().onDamageTaken(active.getDefinition().getId(), victim.getUniqueId(), event.getFinalDamage());
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                plugin.getObjectiveTracker().onBlockBreak(active.getDefinition().getId(), player.getUniqueId(), event.getBlock().getType().name());
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                plugin.getObjectiveTracker().onBlockPlace(active.getDefinition().getId(), player.getUniqueId(), event.getBlock().getType().name());
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                plugin.getObjectiveTracker().onEatFood(active.getDefinition().getId(), player.getUniqueId(), event.getItem().getType().name());
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                ItemStack result = event.getRecipe().getResult();
                plugin.getObjectiveTracker().onCraftItem(active.getDefinition().getId(), player.getUniqueId(), result.getType().name());
                break;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        for (ActiveEvent active : eventManager.getActiveEvents()) {
            if (active.isParticipant(player.getUniqueId())) {
                plugin.getObjectiveTracker().onItemCollect(active.getDefinition().getId(), player.getUniqueId(), event.getItem().getItemStack().getType().name());
                break;
            }
        }
    }

    private String getBossFileName(LivingEntity entity) {
        try {
            Class<?> cls = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
            Object boss = cls.getMethod("getCustomBossEntity", java.util.UUID.class).invoke(null, entity.getUniqueId());
            if (boss != null) {
                Object filename = boss.getClass().getMethod("getFilename").invoke(boss);
                return filename != null ? filename.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CHEST) return;

        Player player = event.getPlayer();
        if (!plugin.getPortalManager().isPlayerInEvent(player.getUniqueId())) return;

        boolean handled = plugin.getLootChestManager().handleChestOpen(player, event.getClickedBlock().getLocation());
        if (handled) {
            event.setCancelled(true);
        }
    }
}
