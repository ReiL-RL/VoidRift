package me.reil.voidrift.portal;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

/**
 * Rift portal particle effects.
 * Creates an oval rift-like portal made entirely of particles.
 * Includes: contour, halo, veins, swirl, core.
 */
public final class RiftParticles {

    private RiftParticles() {}

    /**
     * Spawn the full rift portal effect at a location.
     * @param base the base location (bottom center of portal)
     * @param particle the particle type
     * @param detailed whether to show extra detail (every other tick)
     */
    public static void spawnFull(Location base, Particle particle, boolean detailed) {
        if (base == null || base.getWorld() == null) return;

        double widthRadius = 1.45;
        double heightRadius = 2.4;
        long tick = System.currentTimeMillis() / 75L;
        double phase = (tick % 360L) * (Math.PI / 180.0);

        spawnContour(base, particle, 62, widthRadius, heightRadius, 3);
        spawnHalo(base, particle, 16, widthRadius + 0.18, heightRadius + 0.18);
        spawnVeins(base, particle, 20, widthRadius * 0.8, heightRadius * 0.8);

        if (detailed) {
            spawnSwirl(base, particle, 42, widthRadius * 0.76, 1.88, 2.8, phase, 0.11);
            spawnCore(base, particle, 38, 0.88, 1.58);
        }
    }

    /**
     * Spawn a smaller version (for exit/intermediate portals).
     */
    public static void spawnSmall(Location base, Particle particle, boolean detailed) {
        if (base == null || base.getWorld() == null) return;

        double widthRadius = 0.95;
        double heightRadius = 1.95;
        long tick = System.currentTimeMillis() / 75L;
        double phase = (tick % 360L) * (Math.PI / 180.0);

        spawnContour(base, particle, 34, widthRadius, heightRadius, 2);
        if (detailed) {
            spawnSwirl(base, particle, 22, widthRadius * 0.56, 1.35, 2.1, phase * 0.5, 0.08);
        }
    }

    private static void spawnContour(Location base, Particle particle, int points, double widthRadius, double heightRadius, int layers) {
        Location center = portalCenter(base);
        Vector right = portalRight(base);
        Vector normal = portalNormal(base);

        for (int layer = 0; layer < Math.max(1, layers); layer++) {
            double width = widthRadius + (layer * 0.08);
            double height = heightRadius + (layer * 0.06);
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0 * i) / points;
                double ripple = 1.0 + (0.035 * Math.sin((angle * 8.0) + layer));
                double x = Math.cos(angle) * width * ripple;
                double y = Math.sin(angle) * height * ripple;
                double z = Math.sin((angle * 4.0) + layer) * 0.11;
                Vector offset = right.clone().multiply(x).add(new Vector(0, y, 0)).add(normal.clone().multiply(z));
                base.getWorld().spawnParticle(particle, center.clone().add(offset), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void spawnHalo(Location base, Particle particle, int points, double widthRadius, double heightRadius) {
        Location center = portalCenter(base);
        Vector right = portalRight(base);
        Vector normal = portalNormal(base);

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / Math.max(1, points);
            double pulse = 0.88 + (0.28 * Math.abs(Math.sin(angle * 6.0)));
            double x = Math.cos(angle) * widthRadius * pulse;
            double y = Math.sin(angle) * heightRadius * pulse;
            double z = Math.cos(angle * 5.0) * 0.2;
            Vector offset = right.clone().multiply(x).add(new Vector(0, y, 0)).add(normal.clone().multiply(z));
            base.getWorld().spawnParticle(particle, center.clone().add(offset), 1, 0, 0, 0, 0);
        }
    }

    private static void spawnVeins(Location base, Particle particle, int strands, double widthRadius, double heightRadius) {
        Location center = portalCenter(base);
        Vector right = portalRight(base);
        Vector normal = portalNormal(base);

        for (int strand = 0; strand < strands; strand++) {
            double angle = (Math.PI * 2.0 * strand) / Math.max(1, strands);
            double edgeX = Math.cos(angle) * widthRadius;
            double edgeY = Math.sin(angle) * heightRadius;
            for (int step = 0; step <= 12; step++) {
                double progress = step / 12.0;
                double curve = Math.sin(progress * Math.PI) * 0.28 * (strand % 2 == 0 ? 1.0 : -1.0);
                double x = edgeX * progress;
                double y = edgeY * progress * (0.88 + (0.12 * Math.cos(angle * 2.0)));
                double depth = Math.sin((progress * Math.PI * 2.0) + angle) * 0.12;
                Vector offset = right.clone().multiply(x + curve).add(new Vector(0, y, 0)).add(normal.clone().multiply(depth));
                base.getWorld().spawnParticle(particle, center.clone().add(offset), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void spawnSwirl(Location base, Particle particle, int points, double widthRadius, double heightRadius, double turns, double phaseOffset, double depthScale) {
        Location center = portalCenter(base);
        Vector right = portalRight(base);
        Vector normal = portalNormal(base);

        for (int i = 0; i < points; i++) {
            double progress = i / (double) Math.max(1, points - 1);
            double radiusScale = Math.pow(1.0 - progress, 0.45);
            double angle = phaseOffset + (turns * Math.PI * 2.0 * progress);
            double x = Math.cos(angle) * widthRadius * radiusScale;
            double y = Math.sin(angle) * heightRadius * radiusScale;
            double depth = Math.sin((progress * Math.PI * 8.0) + angle) * depthScale;
            Vector offset = right.clone().multiply(x).add(new Vector(0, y, 0)).add(normal.clone().multiply(depth));
            base.getWorld().spawnParticle(particle, center.clone().add(offset), 1, 0, 0, 0, 0);
        }
    }

    private static void spawnCore(Location base, Particle particle, int points, double widthRadius, double heightRadius) {
        Location center = portalCenter(base);
        Vector right = portalRight(base);
        Vector normal = portalNormal(base);

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / Math.max(1, points);
            double wave = 0.18 + (0.82 * Math.abs(Math.sin(angle * 3.0)));
            double x = Math.cos(angle) * widthRadius * wave;
            double y = Math.sin(angle) * heightRadius * 0.62;
            double z = Math.cos(angle * 5.0) * 0.08;
            Vector offset = right.clone().multiply(x).add(new Vector(0, y, 0)).add(normal.clone().multiply(z));
            base.getWorld().spawnParticle(particle, center.clone().add(offset), 1, 0, 0, 0, 0);
        }
    }

    private static Location portalCenter(Location base) {
        return base.clone().add(0, 1.15, 0);
    }

    private static Vector portalRight(Location base) {
        double yawRadians = Math.toRadians(base.getYaw());
        return new Vector(-Math.cos(yawRadians), 0, Math.sin(yawRadians)).normalize();
    }

    private static Vector portalNormal(Location base) {
        double yawRadians = Math.toRadians(base.getYaw());
        return new Vector(Math.sin(yawRadians), 0, Math.cos(yawRadians)).normalize();
    }
}
