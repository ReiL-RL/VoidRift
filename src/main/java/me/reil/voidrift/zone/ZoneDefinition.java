package me.reil.voidrift.zone;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Defines an event zone with boundaries, spawn points, and mob pools.
 */
public final class ZoneDefinition {

    private final String id;
    private final String world;
    private Location pos1;
    private Location pos2;
    private final List<Area> areas;
    private final List<Location> boundaryPoints;
    private final List<SpawnPoint> spawnPoints;
    private final List<MobPool> mobPools;
    private final Map<Integer, List<MobPool>> bonusWaves;
    private final int maxMobs;

    public ZoneDefinition(String id, String world, Location pos1, Location pos2, List<Area> areas, List<Location> boundaryPoints,
                          List<SpawnPoint> spawnPoints, List<MobPool> mobPools,
                          Map<Integer, List<MobPool>> bonusWaves, int maxMobs) {
        this.id = id;
        this.world = world;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.areas = areas != null ? areas : Collections.<Area>emptyList();
        this.boundaryPoints = boundaryPoints != null ? boundaryPoints : Collections.<Location>emptyList();
        this.spawnPoints = spawnPoints;
        this.mobPools = mobPools;
        this.bonusWaves = bonusWaves != null ? bonusWaves : Collections.<Integer, List<MobPool>>emptyMap();
        this.maxMobs = maxMobs;
    }

    public String getId() { return id; }
    public String getWorld() { return world; }
    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public List<Area> getAreas() { return areas; }
    public List<Location> getBoundaryPoints() { return boundaryPoints; }
    public List<SpawnPoint> getSpawnPoints() { return spawnPoints; }
    public List<MobPool> getMobPools() { return mobPools; }
    public Map<Integer, List<MobPool>> getBonusWaves() { return bonusWaves; }
    public int getMaxMobs() { return maxMobs; }

    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        if (!areas.isEmpty()) {
            for (Area area : areas) {
                if (area.isInside(loc)) return true;
            }
            return false;
        }
        if (pos1 == null || pos2 == null) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        if (y < minY || y > maxY) return false;
        if (boundaryPoints.size() >= 3) {
            return isInsidePolygon(x, z);
        }
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public Location getCenter() {
        if (!areas.isEmpty()) {
            return areas.get(0).getCenter();
        }
        if (boundaryPoints.size() >= 3) {
            double sx = 0;
            double sz = 0;
            for (Location point : boundaryPoints) {
                sx += point.getX();
                sz += point.getZ();
            }
            double cy = pos1 != null && pos2 != null ? (pos1.getY() + pos2.getY()) / 2 : 0;
            return new Location(pos1 != null ? pos1.getWorld() : null, sx / boundaryPoints.size(), cy, sz / boundaryPoints.size());
        }
        double cx = (pos1.getX() + pos2.getX()) / 2;
        double cy = (pos1.getY() + pos2.getY()) / 2;
        double cz = (pos1.getZ() + pos2.getZ()) / 2;
        return new Location(pos1.getWorld(), cx, cy, cz);
    }

    public static final class Area {
        private final String id;
        private final Location pos1;
        private final Location pos2;

        public Area(String id, Location pos1, Location pos2) {
            this.id = id;
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public String getId() { return id; }
        public Location getPos1() { return pos1; }
        public Location getPos2() { return pos2; }

        public boolean isInside(Location loc) {
            if (loc == null || loc.getWorld() == null || pos1 == null || pos2 == null || pos1.getWorld() == null) return false;
            if (!loc.getWorld().equals(pos1.getWorld())) return false;
            double x = loc.getX(), y = loc.getY(), z = loc.getZ();
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public Location getCenter() {
            return new Location(pos1.getWorld(),
                    (pos1.getX() + pos2.getX()) / 2,
                    (pos1.getY() + pos2.getY()) / 2,
                    (pos1.getZ() + pos2.getZ()) / 2);
        }
    }

    private boolean isInsidePolygon(double x, double z) {
        boolean inside = false;
        int count = boundaryPoints.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Location pi = boundaryPoints.get(i);
            Location pj = boundaryPoints.get(j);
            double xi = pi.getX();
            double zi = pi.getZ();
            double xj = pj.getX();
            double zj = pj.getZ();
            boolean intersects = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / ((zj - zi) == 0 ? 0.000001 : (zj - zi)) + xi);
            if (intersects) inside = !inside;
        }
        return inside;
    }

    public static final class SpawnPoint {
        private final String name;
        private final Location location;

        public SpawnPoint(String name, Location location) {
            this.name = name;
            this.location = location;
        }

        public String getName() { return name; }
        public Location getLocation() { return location; }
    }

    public static final class MobPool {
        private final String mobId;
        private final String mobType; // VANILLA or ELITEMOBS
        private final String modelId; // FMM model id (optional)
        private final int weight;
        private final int wave; // 0 = all waves

        public MobPool(String mobId, String mobType, String modelId, int weight, int wave) {
            this.mobId = mobId;
            this.mobType = mobType;
            this.modelId = modelId;
            this.weight = weight;
            this.wave = wave;
        }

        public String getMobId() { return mobId; }
        public String getMobType() { return mobType; }
        public String getModelId() { return modelId; }
        public int getWeight() { return weight; }
        public int getWave() { return wave; }
    }
}

