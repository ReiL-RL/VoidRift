package me.reil.voidrift.modifier;

import org.bukkit.potion.PotionEffectType;

/**
 * Random event modifiers (buffs/debuffs) applied during events.
 */
public enum EventModifier {

    DOUBLE_DAMAGE("double-damage", PotionEffectType.INCREASE_DAMAGE, 1),
    HALF_HEALTH("half-health", null, 0),
    SPEED_BOOST("speed-boost", PotionEffectType.SPEED, 1),
    NO_REGEN("no-regen", null, 0),
    DOUBLE_LOOT("double-loot", null, 0),
    EXTRA_MOBS("extra-mobs", null, 0),
    DARKNESS("darkness", PotionEffectType.BLINDNESS, 0),
    SLOW_MOBS("slow-mobs", PotionEffectType.SLOW, 1);

    private final String configKey;
    private final PotionEffectType potionEffect;
    private final int amplifier;

    EventModifier(String configKey, PotionEffectType potionEffect, int amplifier) {
        this.configKey = configKey;
        this.potionEffect = potionEffect;
        this.amplifier = amplifier;
    }

    public String getConfigKey() { return configKey; }
    public PotionEffectType getPotionEffect() { return potionEffect; }
    public int getAmplifier() { return amplifier; }

    /**
     * Whether this modifier applies a potion effect to players.
     */
    public boolean hasPotionEffect() {
        return potionEffect != null;
    }
}
