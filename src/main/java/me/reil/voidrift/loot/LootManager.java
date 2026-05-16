package me.reil.voidrift.loot;

import me.reil.voidrift.VoidRiftPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages loot tables loaded from events.yml per event.
 * Loot is rolled on mob kill and dropped at mob location.
 */
public final class LootManager {

    private final VoidRiftPlugin plugin;
    private final Map<String, LootTable> lootTables = new LinkedHashMap<String, LootTable>();

    public LootManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load loot tables from events.yml (called by EventManager).
     */
    public void loadFromConfig() {
        lootTables.clear();
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("events");
        if (section == null) return;

        for (String eventId : section.getKeys(false)) {
            ConfigurationSection es = section.getConfigurationSection(eventId);
            if (es == null) continue;

            // Support both "loot" and "loot-table" keys
            List<Map<?, ?>> lootList = es.getMapList("loot-table");
            if (lootList.isEmpty()) {
                lootList = es.getMapList("loot");
            }
            if (lootList.isEmpty()) continue;

            List<LootEntry> entries = new ArrayList<LootEntry>();
            for (Map<?, ?> lootMap : lootList) {
                String matStr = "DIAMOND";
                if (lootMap.containsKey("item")) {
                    matStr = String.valueOf(lootMap.get("item"));
                } else if (lootMap.containsKey("material")) {
                    matStr = String.valueOf(lootMap.get("material"));
                }
                Material mat;
                try { mat = Material.valueOf(matStr.toUpperCase()); }
                catch (IllegalArgumentException e) { mat = Material.DIAMOND; }

                int amount = lootMap.containsKey("amount") ? Integer.parseInt(String.valueOf(lootMap.get("amount"))) : 1;
                double chance = lootMap.containsKey("chance") ? Double.parseDouble(String.valueOf(lootMap.get("chance"))) : 0.5;
                String displayName = lootMap.containsKey("display-name") ? String.valueOf(lootMap.get("display-name")) : null;
                entries.add(new LootEntry(mat, amount, chance, displayName));
            }
            if (!entries.isEmpty()) {
                lootTables.put(eventId, new LootTable(entries));
            }
        }

        plugin.getLogger().info("Loaded " + lootTables.size() + " loot tables.");
    }

    public LootTable getLootTable(String eventId) {
        return lootTables.get(eventId);
    }

    public Map<String, LootTable> getAllLootTables() {
        return Collections.unmodifiableMap(lootTables);
    }
}
