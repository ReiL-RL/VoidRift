package me.reil.voidrift.lang;

import me.reil.voidrift.VoidRiftPlugin;
import net.enelson.sopli.lib.SopLib;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Manages localized messages from lang.yml.
 * Uses SopLib TextUtils for color processing (hex, MiniMessage support).
 * All text output passes through PlaceholderAPI if available.
 */
public final class LangManager {

    private final VoidRiftPlugin plugin;
    private FileConfiguration langConfig;
    private boolean papiAvailable;
    private Method setPlaceholdersMethod;

    public LangManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
        load();
        initPapi();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(file);
    }

    private void initPapi() {
        papiAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papiAvailable) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholdersMethod = papiClass.getMethod("setPlaceholders",
                        org.bukkit.OfflinePlayer.class, String.class);
            } catch (Exception e) {
                papiAvailable = false;
            }
        }
    }

    /**
     * Colorize text using SopLib (supports hex colors, MiniMessage on newer versions).
     * Falls back to ChatColor if SopLib not ready.
     */
    public String color(String text) {
        if (text == null) return "";
        SopLib sopLib = SopLib.getInstance();
        if (sopLib != null) {
            try {
                return sopLib.getTextUtils().color(text);
            } catch (Throwable ignored) {}
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Get a message by key with colors applied.
     */
    public String msg(String key) {
        String raw = langConfig.getString(key);
        if (raw == null) return ChatColor.RED + "[Missing: " + key + "]";
        return color(raw);
    }

    /**
     * Get a message by key with placeholders replaced and colors applied.
     */
    public String msg(String key, Map<String, String> placeholders) {
        String raw = langConfig.getString(key);
        if (raw == null) return ChatColor.RED + "[Missing: " + key + "]";
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return color(raw);
    }

    /**
     * Get a message for a specific player — applies PlaceholderAPI + colors.
     * Use this for all player-facing messages.
     */
    public String msgFor(Player player, String key) {
        return render(player, msg(key));
    }

    /**
     * Get a message for a specific player with custom placeholders + PAPI + colors.
     */
    public String msgFor(Player player, String key, Map<String, String> placeholders) {
        return render(player, msg(key, placeholders));
    }

    /**
     * Full render pipeline: PlaceholderAPI + SopLib colors.
     * Call this on any text shown to a player.
     */
    public String render(Player player, String text) {
        return color(applyPapi(player, text));
    }

    /**
     * Apply PlaceholderAPI to text for a player.
     */
    public String applyPapi(Player player, String text) {
        if (!papiAvailable || player == null || text == null) return text;
        try {
            Object result = setPlaceholdersMethod.invoke(null, player, text);
            return result != null ? result.toString() : text;
        } catch (Exception e) {
            return text;
        }
    }

    public void reload() {
        load();
        initPapi();
    }
}
