package me.reil.voidrift.zone;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventType;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Spawns mobs in waves for active events.
 */
public final class WaveSpawner {

    private final VoidRiftPlugin plugin;
    private final ZoneManager zoneManager;
    private final Random random = new Random();
    // eventId -> list of spawned entities
    private final Map<String, List<Entity>> spawnedEntities = new LinkedHashMap<String, List<Entity>>();
    private int tickCounter = 0;

    public WaveSpawner(VoidRiftPlugin plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    public void tick(ActiveEvent event) {
        if (event.isFinished()) return;
        if (event.getDefinition().getType() != EventType.WAVE_SURVIVAL && event.getDefinition().getType() != EventType.BOSS_FIGHT) return;

        tickCounter++;
        // First tick — spawn immediately; then every 10 seconds
        if (tickCounter > 1 && tickCounter % 10 != 0) return;

        ZoneDefinition zone = zoneManager.getZone(event.getDefinition().getZoneId());
        if (zone == null) {
            if (tickCounter % 30 == 0) {
                plugin.getLogger().warning("WaveSpawner: zone '" + event.getDefinition().getZoneId() + "' not found for event " + event.getDefinition().getId());
            }
            return;
        }

        if (zone.getSpawnPoints().isEmpty()) {
            if (tickCounter % 30 == 0) {
                plugin.getLogger().warning("WaveSpawner: zone '" + zone.getId() + "' has no spawn points!");
            }
            return;
        }

        if (zone.getMobPools().isEmpty()) {
            if (tickCounter % 30 == 0) {
                plugin.getLogger().warning("WaveSpawner: zone '" + zone.getId() + "' has no mob pools!");
            }
            return;
        }

        // Count alive mobs
        List<Entity> entities = spawnedEntities.get(event.getDefinition().getId());
        if (entities == null) {
            entities = new ArrayList<Entity>();
            spawnedEntities.put(event.getDefinition().getId(), entities);
        }

        // Remove dead entities
        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            if (e.isDead() || !e.isValid()) it.remove();
        }

        // Spawn if under limit
        int alive = entities.size();
        if (alive >= zone.getMaxMobs()) return;

        int toSpawn = Math.min(3, zone.getMaxMobs() - alive);
        // Difficulty scaling: more players = more mobs
        if (plugin.getConfig().getBoolean("scaling.enabled", true)) {
            int participants = event.getParticipants().size();
            if (participants > 1) {
                double multiplier = plugin.getConfig().getDouble("scaling.per-player-multiplier", 0.5);
                toSpawn = (int) Math.ceil(toSpawn * (1.0 + (participants - 1) * multiplier));
                toSpawn = Math.min(toSpawn, zone.getMaxMobs() - alive);
            }
        }
        for (int i = 0; i < toSpawn; i++) {
            Entity spawned = spawnMob(zone, event.getCurrentWave());
            if (spawned != null) entities.add(spawned);
        }

        // Check wave completion (all mobs dead after spawn)
        if (alive == 0 && tickCounter > 30) {
            event.nextWave();
            // Spawn bonus wave mobs if configured
            spawnBonusWave(event, zone);
        }
    }

    public void cleanup(ActiveEvent event) {
        List<Entity> entities = spawnedEntities.remove(event.getDefinition().getId());
        if (entities != null) {
            for (Entity e : entities) {
                if (!e.isDead()) e.remove();
            }
        }
    }

    public void shutdown() {
        for (List<Entity> entities : spawnedEntities.values()) {
            for (Entity e : entities) {
                if (!e.isDead()) e.remove();
            }
        }
        spawnedEntities.clear();
    }

    private Entity spawnMob(ZoneDefinition zone, int wave) {
        if (zone.getSpawnPoints().isEmpty() || zone.getMobPools().isEmpty()) return null;

        // Pick random spawn point
        ZoneDefinition.SpawnPoint sp = zone.getSpawnPoints().get(random.nextInt(zone.getSpawnPoints().size()));
        Location loc = sp.getLocation();
        if (loc == null || loc.getWorld() == null) return null;

        // Pick mob from pool (weighted)
        ZoneDefinition.MobPool pool = pickMob(zone.getMobPools(), wave);
        if (pool == null) return null;

        return spawnFromPool(pool, loc);
    }

    /**
     * Spawn bonus wave mobs when a new wave starts.
     */
    private void spawnBonusWave(ActiveEvent event, ZoneDefinition zone) {
        int wave = event.getCurrentWave();
        List<ZoneDefinition.MobPool> bonusMobs = zone.getBonusWaves().get(wave);
        if (bonusMobs == null || bonusMobs.isEmpty()) return;
        if (zone.getSpawnPoints().isEmpty()) return;

        List<Entity> entities = spawnedEntities.get(event.getDefinition().getId());
        if (entities == null) {
            entities = new ArrayList<Entity>();
            spawnedEntities.put(event.getDefinition().getId(), entities);
        }

        for (ZoneDefinition.MobPool pool : bonusMobs) {
            int count = pool.getWeight(); // weight field stores count for bonus waves
            for (int i = 0; i < count; i++) {
                ZoneDefinition.SpawnPoint sp = zone.getSpawnPoints().get(random.nextInt(zone.getSpawnPoints().size()));
                Location loc = sp.getLocation();
                if (loc == null || loc.getWorld() == null) continue;
                Entity spawned = spawnFromPool(pool, loc);
                if (spawned != null) entities.add(spawned);
            }
        }

        plugin.getLogger().info("Bonus wave " + wave + " spawned for event " + event.getDefinition().getId());
    }

    /**
     * Spawn a mob from a pool entry at a location.
     */
    private Entity spawnFromPool(ZoneDefinition.MobPool pool, Location loc) {
        if ("ELITEMOBS".equalsIgnoreCase(pool.getMobType())) {
            // Try EliteMobs integration
            if (plugin.getEliteMobsHook().isAvailable()) {
                return plugin.getEliteMobsHook().spawnBoss(pool.getMobId(), loc);
            }
            // Fallback to vanilla zombie if EliteMobs not available
            return loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        } else if ("MYTHICMOBS".equalsIgnoreCase(pool.getMobType())) {
            // Try MythicMobs integration
            if (plugin.getMythicMobsHook() != null && plugin.getMythicMobsHook().isAvailable()) {
                return plugin.getMythicMobsHook().spawnMob(pool.getMobId(), loc);
            }
            // Fallback to vanilla zombie if MythicMobs not available
            return loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        } else {
            // Vanilla mob
            EntityType type = parseEntityType(pool.getMobId());
            if (type == null) type = EntityType.ZOMBIE;
            Entity entity = loc.getWorld().spawnEntity(loc, type);

            // Apply FMM model to vanilla mob if configured
            if (pool.getModelId() != null && !pool.getModelId().isEmpty()
                    && plugin.getFmmHook().isAvailable()) {
                plugin.getFmmHook().applyModel(entity, pool.getModelId());
            }
            return entity;
        }
    }

    private ZoneDefinition.MobPool pickMob(List<ZoneDefinition.MobPool> pools, int wave) {
        List<ZoneDefinition.MobPool> eligible = new ArrayList<ZoneDefinition.MobPool>();
        int totalWeight = 0;
        for (ZoneDefinition.MobPool pool : pools) {
            if (pool.getWave() == 0 || pool.getWave() == wave) {
                eligible.add(pool);
                totalWeight += pool.getWeight();
            }
        }
        if (eligible.isEmpty() || totalWeight == 0) {
            // Use all pools
            eligible = pools;
            for (ZoneDefinition.MobPool p : pools) totalWeight += p.getWeight();
        }
        if (totalWeight == 0) return null;

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (ZoneDefinition.MobPool pool : eligible) {
            cumulative += pool.getWeight();
            if (roll < cumulative) return pool;
        }
        return eligible.get(0);
    }

    private EntityType parseEntityType(String name) {
        try { return EntityType.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}

