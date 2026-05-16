package me.reil.voidrift.modifier;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages random event modifiers (buffs/debuffs).
 * On event start, randomly picks 0-N modifiers and applies them.
 */
public final class ModifierManager {

    private final VoidRiftPlugin plugin;
    private final Random random = new Random();

    public ModifierManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if modifiers are enabled in config.
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("modifiers.enabled", true);
    }

    /**
     * Get max modifier count from config.
     */
    public int getMaxCount() {
        return plugin.getConfig().getInt("modifiers.max-count", 2);
    }

    /**
     * Roll random modifiers for an event.
     * @return list of selected modifiers (0 to maxCount)
     */
    public List<EventModifier> rollModifiers() {
        if (!isEnabled()) return Collections.emptyList();

        int maxCount = getMaxCount();
        if (maxCount <= 0) return Collections.emptyList();

        List<EventModifier> all = new ArrayList<EventModifier>(Arrays.asList(EventModifier.values()));
        Collections.shuffle(all, random);

        int count = random.nextInt(maxCount + 1); // 0 to maxCount
        List<EventModifier> selected = new ArrayList<EventModifier>();
        for (int i = 0; i < Math.min(count, all.size()); i++) {
            selected.add(all.get(i));
        }
        return selected;
    }

    /**
     * Apply modifier effects to a player entering an event.
     */
    public void applyToPlayer(Player player, ActiveEvent event) {
        List<EventModifier> modifiers = event.getModifiers();
        if (modifiers == null || modifiers.isEmpty()) return;

        int duration = (int) (event.getRemainingSeconds() * 20L); // ticks
        if (duration <= 0) duration = 6000; // fallback 5 min

        for (EventModifier mod : modifiers) {
            switch (mod) {
                case HALF_HEALTH:
                    double maxHealth = player.getMaxHealth();
                    if (player.getHealth() > maxHealth / 2.0) {
                        player.setHealth(maxHealth / 2.0);
                    }
                    break;
                case DOUBLE_DAMAGE:
                case SPEED_BOOST:
                case DARKNESS:
                case SLOW_MOBS:
                    if (mod.hasPotionEffect()) {
                        player.addPotionEffect(new PotionEffect(mod.getPotionEffect(), duration, mod.getAmplifier(), true, false), true);
                    }
                    break;
                case NO_REGEN:
                    // Remove regen and prevent natural regen via potion
                    player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, duration, 0, true, false), true);
                    break;
                default:
                    // DOUBLE_LOOT, EXTRA_MOBS are checked as flags, not potion effects
                    break;
            }
        }
    }

    /**
     * Check if an active event has a specific modifier.
     */
    public boolean hasModifier(ActiveEvent event, EventModifier modifier) {
        List<EventModifier> modifiers = event.getModifiers();
        return modifiers != null && modifiers.contains(modifier);
    }

    /**
     * Get display string of active modifiers for sidebar.
     */
    public String getModifierDisplay(ActiveEvent event) {
        List<EventModifier> modifiers = event.getModifiers();
        if (modifiers == null || modifiers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modifiers.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(plugin.getLang().msg("modifier." + modifiers.get(i).getConfigKey()));
        }
        return sb.toString();
    }
}
