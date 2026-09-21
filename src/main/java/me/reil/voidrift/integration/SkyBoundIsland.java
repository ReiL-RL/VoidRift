package me.reil.voidrift.integration;

import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Reflection-backed wrapper around a SkyBound island.
 */
public final class SkyBoundIsland {

    private final Object handle;

    SkyBoundIsland(Object handle) {
        this.handle = handle;
    }

    Object handle() {
        return handle;
    }

    public String getId() { return string("getId"); }
    public String getName() { return string("getName"); }

    public UUID getOwner() {
        Object value = invoke("getOwner");
        return value instanceof UUID ? (UUID) value : null;
    }

    @SuppressWarnings("unchecked")
    public Set<UUID> getMembers() {
        Object value = invoke("getMembers");
        if (value instanceof Set) return (Set<UUID>) value;
        return Collections.emptySet();
    }

    public int getLevel() {
        Object value = invoke("getLevel");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public long getExperience() {
        Object value = invoke("getExperience");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public void addExperience(long amount) {
        invoke("addExperience", new Class<?>[] { long.class }, new Object[] { amount });
    }

    public double getBankBalance() {
        Object value = invoke("getBankBalance");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    public void setBankBalance(double amount) {
        invoke("setBankBalance", new Class<?>[] { double.class }, new Object[] { amount });
    }

    public Location getCenter() {
        Object value = invoke("getCenter");
        return value instanceof Location ? (Location) value : null;
    }

    private String string(String method) {
        Object value = invoke(method);
        return value != null ? value.toString() : null;
    }

    private Object invoke(String method) {
        return invoke(method, new Class<?>[0], new Object[0]);
    }

    private Object invoke(String method, Class<?>[] types, Object[] args) {
        try {
            Method m = handle.getClass().getMethod(method, types);
            return m.invoke(handle, args);
        } catch (Exception ignored) {
            return null;
        }
    }
}
