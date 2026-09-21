package me.reil.voidrift.modifier;

import org.bukkit.potion.PotionEffectType;

/**
 * Random event modifiers (buffs/debuffs) applied during events.
 */
public enum EventModifier {

    DOUBLE_DAMAGE("double-damage", "INCREASE_DAMAGE", 1),
    HALF_HEALTH("half-health", null, 0),
    SPEED_BOOST("speed-boost", "SPEED", 1),
    NO_REGEN("no-regen", null, 0),
    DOUBLE_LOOT("double-loot", null, 0),
    EXTRA_MOBS("extra-mobs", null, 0),
    DARKNESS("darkness", "BLINDNESS", 0),
    SLOW_MOBS("slow-mobs", "SLOW", 1);

    private final String configKey;
    private final String potionEffectName;
    private final int amplifier;

    EventModifier(String configKey, String potionEffectName, int amplifier) {
        this.configKey = configKey;
        this.potionEffectName = potionEffectName;
        this.amplifier = amplifier;
    }

    public String getConfigKey() { return configKey; }
    public PotionEffectType getPotionEffect() {
        return potionEffectName == null ? null : PotionEffectType.getByName(potionEffectName);
    }
    public int getAmplifier() { return amplifier; }

    /**
     * Whether this modifier applies a potion effect to players.
     */
    public boolean hasPotionEffect() {
        return getPotionEffect() != null;
    }
}
