package me.reil.voidrift.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventsConfig {

    private final JavaPlugin plugin;
    private String language;
    private int maxActiveEvents;
    private int portalPreviewSeconds;
    private boolean autoStartEnabled;

    public EventsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.language = cfg.getString("language", "ru");
        this.maxActiveEvents = cfg.getInt("max-active-events", 3);
        this.portalPreviewSeconds = cfg.getInt("portal.preview-seconds", 10);
        this.autoStartEnabled = cfg.getBoolean("auto-start.enabled", true);
    }

    public String getLanguage() { return language; }
    public int getMaxActiveEvents() { return maxActiveEvents; }
    public int getPortalPreviewSeconds() { return portalPreviewSeconds; }
    public boolean isAutoStartEnabled() { return autoStartEnabled; }
}

