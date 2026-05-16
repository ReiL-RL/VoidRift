package me.reil.voidrift.zone;

import me.reil.voidrift.config.EventsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZoneManager {

    private final JavaPlugin plugin;
    private final EventsConfig config;
    private final Map<String, ZoneDefinition> zones = new LinkedHashMap<String, ZoneDefinition>();

    public ZoneManager(JavaPlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
        loadZones();
    }

    public ZoneDefinition getZone(String zoneId) { return zones.get(zoneId); }
    public Collection<ZoneDefinition> getZones() { return Collections.unmodifiableCollection(zones.values()); }

    public void reloadZones() {
        zones.clear();
        loadZones();
    }

    public void setPos1(String zoneId, Location location) {
        ZoneDefinition zone = zones.get(zoneId);
        if (zone != null) {
            zone.setPos1(location);
        }
    }

    public void setPos2(String zoneId, Location location) {
        ZoneDefinition zone = zones.get(zoneId);
        if (zone != null) {
            zone.setPos2(location);
        }
    }

    public void addSpawnPoint(String zoneId, String name, Location location) {
        ZoneDefinition zone = zones.get(zoneId);
        if (zone != null) {
            zone.getSpawnPoints().add(new ZoneDefinition.SpawnPoint(name, location));
        }
    }

    private void loadZones() {
        File file = new File(plugin.getDataFolder(), "zones.yml");
        if (!file.exists()) {
            plugin.saveResource("zones.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("zones");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection zs = section.getConfigurationSection(id);
            if (zs == null) continue;

            String world = zs.getString("world", "world");
            Location pos1 = deserializeLoc(world, zs.getConfigurationSection("pos1"));
            Location pos2 = deserializeLoc(world, zs.getConfigurationSection("pos2"));
            int maxMobs = zs.getInt("max-mobs", 20);

            List<ZoneDefinition.SpawnPoint> spawnPoints = new ArrayList<ZoneDefinition.SpawnPoint>();
            ConfigurationSection spSection = zs.getConfigurationSection("spawn-points");
            if (spSection != null) {
                for (String spName : spSection.getKeys(false)) {
                    Location loc = deserializeLoc(world, spSection.getConfigurationSection(spName));
                    if (loc != null) spawnPoints.add(new ZoneDefinition.SpawnPoint(spName, loc));
                }
            }

            List<ZoneDefinition.MobPool> mobPools = new ArrayList<ZoneDefinition.MobPool>();
            List<Map<?, ?>> poolList = zs.getMapList("mob-pools");
            for (Map<?, ?> poolMap : poolList) {
                String mobId = String.valueOf(poolMap.get("mob-id"));
                String mobType = poolMap.containsKey("mob-type") ? String.valueOf(poolMap.get("mob-type")) : "VANILLA";
                String modelId = poolMap.containsKey("model") ? String.valueOf(poolMap.get("model")) : null;
                int weight = poolMap.containsKey("weight") ? Integer.parseInt(String.valueOf(poolMap.get("weight"))) : 1;
                int wave = poolMap.containsKey("wave") ? Integer.parseInt(String.valueOf(poolMap.get("wave"))) : 0;
                mobPools.add(new ZoneDefinition.MobPool(mobId, mobType, modelId, weight, wave));
            }

            // Load bonus waves
            Map<Integer, List<ZoneDefinition.MobPool>> bonusWaves = new HashMap<Integer, List<ZoneDefinition.MobPool>>();
            ConfigurationSection bwSection = zs.getConfigurationSection("bonus-waves");
            if (bwSection != null) {
                for (String waveKey : bwSection.getKeys(false)) {
                    int waveNum;
                    try { waveNum = Integer.parseInt(waveKey); }
                    catch (NumberFormatException e) { continue; }
                    List<ZoneDefinition.MobPool> bonusMobs = new ArrayList<ZoneDefinition.MobPool>();
                    List<Map<?, ?>> bonusList = bwSection.getMapList(waveKey);
                    for (Map<?, ?> bm : bonusList) {
                        String bMobId = String.valueOf(bm.get("mob-id"));
                        String bMobType = bm.containsKey("mob-type") ? String.valueOf(bm.get("mob-type")) : "VANILLA";
                        String bModelId = bm.containsKey("model") ? String.valueOf(bm.get("model")) : null;
                        int bCount = bm.containsKey("count") ? Integer.parseInt(String.valueOf(bm.get("count"))) : 1;
                        // Use count as weight for bonus waves (count field determines how many to spawn)
                        bonusMobs.add(new ZoneDefinition.MobPool(bMobId, bMobType, bModelId, bCount, waveNum));
                    }
                    bonusWaves.put(waveNum, bonusMobs);
                }
            }

            zones.put(id, new ZoneDefinition(id, world, pos1, pos2, spawnPoints, mobPools, bonusWaves, maxMobs));
        }

        plugin.getLogger().info("Loaded " + zones.size() + " event zones.");
    }

    private Location deserializeLoc(String worldName, ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(worldName);
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0), (float) section.getDouble("pitch", 0));
    }
}

