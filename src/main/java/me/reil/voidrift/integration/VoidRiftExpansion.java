package me.reil.voidrift.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion class.
 * Prefix: voidrift
 */
public final class VoidRiftExpansion extends PlaceholderExpansion {

    private final VoidRiftPlugin plugin;

    public VoidRiftExpansion(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "voidrift"; }

    @Override
    public @NotNull String getAuthor() { return "Reil"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        return plugin.getPlaceholderHook().resolve(player, identifier);
    }
}
