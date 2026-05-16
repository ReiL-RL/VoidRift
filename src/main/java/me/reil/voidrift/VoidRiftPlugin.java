package me.reil.voidrift;

import me.reil.voidrift.command.EventAdminCommand;
import me.reil.voidrift.command.EventCommand;
import me.reil.voidrift.config.EventsConfig;
import me.reil.voidrift.display.BossBarDisplay;
import me.reil.voidrift.display.SoundManager;
import me.reil.voidrift.event.EventManager;
import me.reil.voidrift.gui.EventGui;
import me.reil.voidrift.integration.CitizensHook;
import me.reil.voidrift.integration.EliteMobsHook;
import me.reil.voidrift.integration.FreeMinecraftModelsHook;
import me.reil.voidrift.integration.MythicMobsHook;
import me.reil.voidrift.integration.SkyBoundHook;
import me.reil.voidrift.integration.SopItemsHook;
import me.reil.voidrift.lang.LangManager;
import me.reil.voidrift.leaderboard.Leaderboard;
import me.reil.voidrift.listener.EventListener;
import me.reil.voidrift.listener.PortalListener;
import me.reil.voidrift.loot.LootManager;
import me.reil.voidrift.modifier.ModifierManager;
import me.reil.voidrift.portal.PortalManager;
import me.reil.voidrift.reward.RewardManager;
import me.reil.voidrift.stats.StatsManager;
import me.reil.voidrift.wizard.EventWizard;
import me.reil.voidrift.wizard.SetupWizard;
import me.reil.voidrift.wizard.WizardListener;
import me.reil.voidrift.zone.ZoneManager;
import me.reil.voidrift.zone.WaveSpawner;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * VoidRift Plugin.
 *
 * Modes:
 * - Standalone: works independently, rewards via Vault/commands
 * - Addon: integrates with SkyBound Core for island XP, missions, bank rewards
 *
 * Optional integrations:
 * - EliteMobs: spawn EM bosses in event zones
 * - FreeMinecraftModels: custom mob models in zones
 * - MythicMobs: spawn MM mobs in event zones
 * - Citizens: NPC support for event info
 */
public final class VoidRiftPlugin extends JavaPlugin {

    private EventsConfig eventsConfig;
    private EventManager eventManager;
    private ZoneManager zoneManager;
    private WaveSpawner waveSpawner;
    private PortalManager portalManager;
    private RewardManager rewardManager;
    private SkyBoundHook skyBoundHook;
    private EliteMobsHook eliteMobsHook;
    private FreeMinecraftModelsHook fmmHook;
    private MythicMobsHook mythicMobsHook;
    private CitizensHook citizensHook;
    private SopItemsHook sopItemsHook;
    private me.reil.voidrift.integration.SopCustomBlocksHook sopCustomBlocksHook;
    private SetupWizard setupWizard;
    private me.reil.voidrift.wizard.ZoneWizard zoneWizard;
    private EventWizard eventWizard;
    private me.reil.voidrift.objective.ObjectiveTracker objectiveTracker;
    private me.reil.voidrift.display.EventDisplay eventDisplay;
    private me.reil.voidrift.integration.PlaceholderHook placeholderHook;
    private BossBarDisplay bossBarDisplay;
    private EventGui eventGui;
    private LootManager lootManager;
    private me.reil.voidrift.loot.LootChestManager lootChestManager;
    private Leaderboard leaderboard;
    private LangManager langManager;
    private SoundManager soundManager;
    private ModifierManager modifierManager;
    private StatsManager statsManager;
    private BukkitTask schedulerTask;

    @Override
    public void onEnable() {
        // Config
        saveDefaultConfig();
        this.eventsConfig = new EventsConfig(this);
        this.eventsConfig.load();

        // Lang
        this.langManager = new LangManager(this);

        // Managers
        this.soundManager = new SoundManager(this);
        this.modifierManager = new ModifierManager(this);
        this.statsManager = new StatsManager(this);
        this.rewardManager = new RewardManager(this);
        this.zoneManager = new ZoneManager(this, eventsConfig);
        this.waveSpawner = new WaveSpawner(this, zoneManager);
        this.portalManager = new PortalManager(this, eventsConfig);
        this.lootManager = new LootManager(this);
        this.lootChestManager = new me.reil.voidrift.loot.LootChestManager(this);
        this.eventManager = new EventManager(this, eventsConfig, zoneManager, waveSpawner, portalManager, rewardManager);

        // Integrations
        this.skyBoundHook = new SkyBoundHook(this);
        this.eliteMobsHook = new EliteMobsHook(this);
        this.fmmHook = new FreeMinecraftModelsHook(this);
        this.mythicMobsHook = new MythicMobsHook(this);
        this.citizensHook = new CitizensHook(this);
        this.sopItemsHook = new SopItemsHook(this);
        this.sopCustomBlocksHook = new me.reil.voidrift.integration.SopCustomBlocksHook(this);
        this.setupWizard = new SetupWizard(this);
        this.zoneWizard = new me.reil.voidrift.wizard.ZoneWizard(this);
        this.eventWizard = new EventWizard(this);
        this.objectiveTracker = new me.reil.voidrift.objective.ObjectiveTracker(this);
        this.eventDisplay = new me.reil.voidrift.display.EventDisplay(this);
        this.placeholderHook = new me.reil.voidrift.integration.PlaceholderHook(this);
        this.bossBarDisplay = new BossBarDisplay(this);
        this.eventGui = new EventGui(this);
        this.leaderboard = new Leaderboard(this);

        // Init Vault economy cache
        this.rewardManager.initVault();

        // Start display updaters
        this.eventDisplay.start();
        this.bossBarDisplay.start();

        // Register PlaceholderAPI
        this.placeholderHook.register();

        // Commands
        PluginCommand evCmd = getCommand("event");
        if (evCmd != null) {
            EventCommand exec = new EventCommand(this);
            evCmd.setExecutor(exec);
            evCmd.setTabCompleter(exec);
        }
        PluginCommand adminCmd = getCommand("eventadmin");
        if (adminCmd != null) {
            EventAdminCommand exec = new EventAdminCommand(this);
            adminCmd.setExecutor(exec);
            adminCmd.setTabCompleter(exec);
        }

        // Listeners
        Bukkit.getPluginManager().registerEvents(new EventListener(this, eventManager), this);
        Bukkit.getPluginManager().registerEvents(new PortalListener(this, portalManager, eventManager), this);
        Bukkit.getPluginManager().registerEvents(new WizardListener(this), this);
        Bukkit.getPluginManager().registerEvents(eventGui, this);

        // Scheduler (event tick every second)
        this.schedulerTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                eventManager.tick();
            }
        }, 20L, 20L);

        getLogger().info("VoidRift v" + getDescription().getVersion() + " enabled.");
        getLogger().info("Mode: " + (skyBoundHook.isAvailable() ? "SkyBound Addon" : "Standalone"));
        if (eliteMobsHook.isAvailable()) getLogger().info("EliteMobs integration: enabled");
        if (fmmHook.isAvailable()) getLogger().info("FreeMinecraftModels integration: enabled");
        if (mythicMobsHook.isAvailable()) getLogger().info("MythicMobs integration: enabled");
        if (citizensHook.isAvailable()) getLogger().info("Citizens integration: enabled");
    }

    @Override
    public void onDisable() {
        if (schedulerTask != null) schedulerTask.cancel();
        if (eventDisplay != null) eventDisplay.stop();
        if (bossBarDisplay != null) bossBarDisplay.stop();
        if (lootChestManager != null) lootChestManager.shutdown();
        if (eventManager != null) eventManager.shutdown();
        if (waveSpawner != null) waveSpawner.shutdown();
        if (statsManager != null) statsManager.close();
        getLogger().info("VoidRift disabled.");
    }

    public EventsConfig getEventsConfig() { return eventsConfig; }
    public EventManager getEventManager() { return eventManager; }
    public ZoneManager getZoneManager() { return zoneManager; }
    public WaveSpawner getWaveSpawner() { return waveSpawner; }
    public PortalManager getPortalManager() { return portalManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public SkyBoundHook getSkyBoundHook() { return skyBoundHook; }
    public EliteMobsHook getEliteMobsHook() { return eliteMobsHook; }
    public FreeMinecraftModelsHook getFmmHook() { return fmmHook; }
    public MythicMobsHook getMythicMobsHook() { return mythicMobsHook; }
    public CitizensHook getCitizensHook() { return citizensHook; }
    public SetupWizard getSetupWizard() { return setupWizard; }
    public me.reil.voidrift.wizard.ZoneWizard getZoneWizard() { return zoneWizard; }
    public EventWizard getEventWizard() { return eventWizard; }
    public me.reil.voidrift.objective.ObjectiveTracker getObjectiveTracker() { return objectiveTracker; }
    public me.reil.voidrift.display.EventDisplay getEventDisplay() { return eventDisplay; }
    public me.reil.voidrift.integration.PlaceholderHook getPlaceholderHook() { return placeholderHook; }
    public BossBarDisplay getBossBarDisplay() { return bossBarDisplay; }
    public EventGui getEventGui() { return eventGui; }
    public LootManager getLootManager() { return lootManager; }
    public Leaderboard getLeaderboard() { return leaderboard; }
    public LangManager getLang() { return langManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public ModifierManager getModifierManager() { return modifierManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public SopItemsHook getSopItemsHook() { return sopItemsHook; }
    public me.reil.voidrift.integration.SopCustomBlocksHook getSopCustomBlocksHook() { return sopCustomBlocksHook; }
    public me.reil.voidrift.loot.LootChestManager getLootChestManager() { return lootChestManager; }
}
