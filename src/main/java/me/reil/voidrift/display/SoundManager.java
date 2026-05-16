package me.reil.voidrift.display;

import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plays configurable sounds at key event moments.
 * All sounds are configured in config.yml under "sounds:" section.
 */
public final class SoundManager {

    private final VoidRiftPlugin plugin;

    public SoundManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Play a configured sound to a specific player.
     * @param player the player to play sound for
     * @param key the sound key from config (e.g. "event-start", "wave-clear")
     */
    public void playSound(Player player, String key) {
        if (player == null || !player.isOnline()) return;
        String soundName = plugin.getConfig().getString("sounds." + key + ".sound", null);
        if (soundName == null || soundName.isEmpty()) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        float volume = (float) plugin.getConfig().getDouble("sounds." + key + ".volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("sounds." + key + ".pitch", 1.0);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Play a configured sound to all online players.
     * @param key the sound key from config
     */
    public void playSoundAll(String key) {
        String soundName = plugin.getConfig().getString("sounds." + key + ".sound", null);
        if (soundName == null || soundName.isEmpty()) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        float volume = (float) plugin.getConfig().getDouble("sounds." + key + ".volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("sounds." + key + ".pitch", 1.0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
