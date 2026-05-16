package me.reil.voidrift.objective;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Tracks objective progress for all players in active events.
 * Checks completion and triggers rewards + FlexAchievements.
 */
public final class ObjectiveTracker {

    private final VoidRiftPlugin plugin;
    private final Map<String, Map<UUID, PlayerProgress>> eventProgress = new LinkedHashMap<String, Map<UUID, PlayerProgress>>();
    // Track players who already completed (don't reward twice)
    private final Map<String, Set<UUID>> completedPlayers = new LinkedHashMap<String, Set<UUID>>();

    public ObjectiveTracker(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    // === Lifecycle ===

    public void onEventStart(String eventId) {
        eventProgress.put(eventId, new LinkedHashMap<UUID, PlayerProgress>());
        completedPlayers.put(eventId, new HashSet<UUID>());
    }

    public void onEventEnd(String eventId) {
        eventProgress.remove(eventId);
        completedPlayers.remove(eventId);
    }

    public void onPlayerJoin(String eventId, UUID playerId) {
        Map<UUID, PlayerProgress> map = eventProgress.get(eventId);
        if (map != null && !map.containsKey(playerId)) {
            map.put(playerId, new PlayerProgress(playerId));
        }
    }

    public PlayerProgress getPlayerProgress(String eventId, UUID playerId) {
        Map<UUID, PlayerProgress> map = eventProgress.get(eventId);
        if (map == null) return null;
        return map.get(playerId);
    }

    // === Progress reporters ===

    public void onMobKill(String eventId, UUID playerId, String mobType, String bossFile) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);

        for (int i = 0; i < objectives.size(); i++) {
            Objective obj = objectives.get(i);
            switch (obj.getType()) {
                case KILL_MOBS:
                    if (matchesTarget(obj, mobType, bossFile)) progress.addProgress(i, 1);
                    break;
                case KILL_BOSS:
                    if (bossFile != null && matchesTarget(obj, null, bossFile)) progress.addProgress(i, 1);
                    break;
                case KILL_ELITE:
                    if (bossFile != null) progress.addProgress(i, 1);
                    break;
                case KILL_STREAK:
                    progress.addProgress(i, 1);
                    break;
                case LAST_HIT_BOSS:
                    if (bossFile != null && matchesTarget(obj, null, bossFile)) progress.setProgress(i, 1);
                    break;
                default: break;
            }
        }
        checkCompletion(eventId, playerId, event);
    }

    public void onPlayerDeath(String eventId, UUID playerId) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);

        for (int i = 0; i < objectives.size(); i++) {
            Objective obj = objectives.get(i);
            switch (obj.getType()) {
                case NO_DEATH:
                    progress.setProgress(i, -1); // Failed
                    break;
                case KILL_STREAK:
                    progress.setProgress(i, 0); // Reset streak
                    break;
                default: break;
            }
        }
    }

    public void onDamageDealt(String eventId, UUID playerId, double damage) {
        updateNumeric(eventId, playerId, ObjectiveType.DEAL_DAMAGE, (int) damage);
    }

    public void onDamageTaken(String eventId, UUID playerId, double damage) {
        updateNumeric(eventId, playerId, ObjectiveType.TAKE_DAMAGE, (int) damage);
    }

    public void onItemCollect(String eventId, UUID playerId, String material) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);
        for (int i = 0; i < objectives.size(); i++) {
            Objective obj = objectives.get(i);
            if (obj.getType() == ObjectiveType.COLLECT_ITEM || obj.getType() == ObjectiveType.COLLECT_FROM_MOB
                    || obj.getType() == ObjectiveType.COLLECT_FROM_CHEST) {
                if (obj.getTarget() == null || obj.getTarget().equalsIgnoreCase(material)) {
                    progress.addProgress(i, 1);
                }
            }
        }
        checkCompletion(eventId, playerId, event);
    }

    public void onBlockBreak(String eventId, UUID playerId, String blockType) {
        updateTargeted(eventId, playerId, ObjectiveType.MINE_BLOCK, blockType);
    }

    public void onBlockPlace(String eventId, UUID playerId, String blockType) {
        updateTargeted(eventId, playerId, ObjectiveType.PLACE_BLOCK, blockType);
    }

    public void onWaveReached(String eventId, int wave) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        Map<UUID, PlayerProgress> map = eventProgress.get(eventId);
        if (map == null) return;

        for (Map.Entry<UUID, PlayerProgress> entry : map.entrySet()) {
            PlayerProgress progress = entry.getValue();
            for (int i = 0; i < objectives.size(); i++) {
                Objective obj = objectives.get(i);
                if (obj.getType() == ObjectiveType.REACH_WAVE) progress.setProgress(i, wave);
                if (obj.getType() == ObjectiveType.CLEAR_WAVES) progress.addProgress(i, 1);
                if (obj.getType() == ObjectiveType.ALL_MOBS_DEAD) progress.setProgress(i, 1);
            }
            checkCompletion(eventId, entry.getKey(), event);
        }
    }

    public void onScoreUpdate(String eventId, UUID playerId, int totalScore) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i).getType() == ObjectiveType.SCORE_POINTS) {
                progress.setProgress(i, totalScore);
            }
        }
        checkCompletion(eventId, playerId, event);
    }

    public void onPortalUse(String eventId, UUID playerId, String portalId) {
        updateTargeted(eventId, playerId, ObjectiveType.USE_PORTAL, portalId);
    }

    public void onEnterZone(String eventId, UUID playerId, String zoneId) {
        updateTargeted(eventId, playerId, ObjectiveType.ENTER_ZONE, zoneId);
    }

    public void onDistanceTraveled(String eventId, UUID playerId, double distance) {
        updateNumeric(eventId, playerId, ObjectiveType.TRAVEL_DISTANCE, (int) distance);
    }

    public void onEatFood(String eventId, UUID playerId, String food) {
        updateTargeted(eventId, playerId, ObjectiveType.EAT_FOOD, food);
    }

    public void onCraftItem(String eventId, UUID playerId, String item) {
        updateTargeted(eventId, playerId, ObjectiveType.CRAFT_ITEM, item);
    }

    public void onUseItem(String eventId, UUID playerId, String item) {
        updateTargeted(eventId, playerId, ObjectiveType.USE_ITEM, item);
    }

    public void onCustom(String eventId, UUID playerId, String customId) {
        updateTargeted(eventId, playerId, ObjectiveType.CUSTOM, customId);
    }

    /**
     * Called every second — checks SURVIVE_TIME and COMPLETE_BEFORE.
     */
    public void tick(ActiveEvent event) {
        if (event.isFinished()) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        String eventId = event.getDefinition().getId();
        Map<UUID, PlayerProgress> map = eventProgress.get(eventId);
        if (map == null) return;

        long elapsed = (System.currentTimeMillis() - event.getStartedAt()) / 1000L;

        for (Map.Entry<UUID, PlayerProgress> entry : new ArrayList<Map.Entry<UUID, PlayerProgress>>(map.entrySet())) {
            PlayerProgress progress = entry.getValue();
            for (int i = 0; i < objectives.size(); i++) {
                Objective obj = objectives.get(i);
                if (obj.getType() == ObjectiveType.SURVIVE_TIME) {
                    progress.setProgress(i, (int) elapsed);
                }
                if (obj.getType() == ObjectiveType.COMPLETE_BEFORE) {
                    // This is checked at completion time — elapsed must be < amount
                    progress.setProgress(i, (int) elapsed);
                }
                if (obj.getType() == ObjectiveType.NO_DEATH && progress.getProgress(i) >= 0) {
                    progress.setProgress(i, 1); // Still alive = progress
                }
            }
            checkCompletion(eventId, entry.getKey(), event);
        }
    }

    // === Completion ===

    private void checkCompletion(String eventId, UUID playerId, ActiveEvent event) {
        Set<UUID> completed = completedPlayers.get(eventId);
        if (completed != null && completed.contains(playerId)) return; // Already rewarded

        EventDefinition def = event.getDefinition();

        // Quest chain mode
        if (def.hasQuestChain()) {
            checkQuestChainCompletion(eventId, playerId, event);
            return;
        }

        List<Objective> objectives = def.getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);

        // Check individual objective completion for sound effects
        Player soundPlayer = Bukkit.getPlayer(playerId);
        if (soundPlayer != null && soundPlayer.isOnline()) {
            for (int i = 0; i < objectives.size(); i++) {
                if (!progress.isObjectiveNotified(i) && isObjectiveMet(objectives.get(i), progress.getProgress(i))) {
                    progress.setObjectiveNotified(i);
                    soundPlayer.playSound(soundPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
        }

        boolean done;

        if (def.isCompleteOnAll()) {
            done = true;
            for (int i = 0; i < objectives.size(); i++) {
                if (!isObjectiveMet(objectives.get(i), progress.getProgress(i))) {
                    done = false;
                    break;
                }
            }
        } else {
            done = false;
            for (int i = 0; i < objectives.size(); i++) {
                if (isObjectiveMet(objectives.get(i), progress.getProgress(i))) {
                    done = true;
                    break;
                }
            }
        }

        if (done) {
            if (completed != null) completed.add(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                plugin.getRewardManager().giveRewards(player, event);
                fireFlexAchievement(player, def);
                player.sendMessage(plugin.getLang().msg("objective.complete"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                if (plugin.getSoundManager() != null) {
                    plugin.getSoundManager().playSound(player, "objective-complete");
                }
                // Send title with objective summary
                String completedObjective = getCompletedObjectiveSummary(objectives);
                player.sendTitle(
                        plugin.getLang().msg("messages.objective.complete-title"),
                        ChatColor.YELLOW + completedObjective,
                        10, 60, 20
                );
            }
        }
    }

    /**
     * Check quest chain stage completion for a player.
     */
    private void checkQuestChainCompletion(String eventId, UUID playerId, ActiveEvent event) {
        EventDefinition def = event.getDefinition();
        List<me.reil.voidrift.objective.QuestStage> chain = def.getQuestChain();
        int currentStage = event.getCurrentQuestStage();
        if (currentStage >= chain.size()) return; // All stages done

        me.reil.voidrift.objective.QuestStage stage = chain.get(currentStage);
        List<Objective> stageObjectives = stage.getObjectives();
        if (stageObjectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);

        // Check if all objectives in current stage are met
        boolean stageDone = true;
        for (int i = 0; i < stageObjectives.size(); i++) {
            // Use offset index for quest chain stages
            int globalIdx = getQuestChainObjectiveOffset(chain, currentStage) + i;
            if (!isObjectiveMet(stageObjectives.get(i), progress.getProgress(globalIdx))) {
                stageDone = false;
                break;
            }
        }

        if (stageDone) {
            event.advanceQuestStage();
            int nextStage = event.getCurrentQuestStage();

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                if (nextStage >= chain.size()) {
                    // All stages complete!
                    Set<UUID> completed = completedPlayers.get(eventId);
                    if (completed != null) completed.add(playerId);
                    plugin.getRewardManager().giveRewards(player, event);
                    fireFlexAchievement(player, def);
                    player.sendMessage(plugin.getLang().msg("objective.complete"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    if (plugin.getSoundManager() != null) {
                        plugin.getSoundManager().playSound(player, "objective-complete");
                    }
                } else {
                    // Advance to next stage
                    me.reil.voidrift.objective.QuestStage nextQuestStage = chain.get(nextStage);
                    player.sendMessage(plugin.getLang().msg("objective.stage-complete"));
                    if (nextQuestStage.getMessage() != null && !nextQuestStage.getMessage().isEmpty()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', nextQuestStage.getMessage()));
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                }
            }
        }
    }

    /**
     * Get the global objective index offset for a quest chain stage.
     */
    private int getQuestChainObjectiveOffset(List<me.reil.voidrift.objective.QuestStage> chain, int stageIndex) {
        int offset = 0;
        for (int i = 0; i < stageIndex; i++) {
            offset += chain.get(i).getObjectives().size();
        }
        return offset;
    }

    /**
     * Get the current stage objectives for quest chain tracking.
     * Used by ObjectiveTracker to only track current stage.
     */
    public List<Objective> getCurrentObjectives(ActiveEvent event) {
        EventDefinition def = event.getDefinition();
        if (def.hasQuestChain()) {
            int stage = event.getCurrentQuestStage();
            List<me.reil.voidrift.objective.QuestStage> chain = def.getQuestChain();
            if (stage < chain.size()) {
                return chain.get(stage).getObjectives();
            }
            return java.util.Collections.emptyList();
        }
        return def.getObjectives();
    }

    private boolean isObjectiveMet(Objective obj, int progress) {
        switch (obj.getType()) {
            case NO_DEATH:
                return progress >= 0 && progress >= obj.getAmount(); // -1 = died
            case COMPLETE_BEFORE:
                return progress > 0 && progress <= obj.getAmount(); // Must complete within N seconds
            case SCORE_TOP:
                return progress >= 1; // Checked separately at event end
            default:
                return progress >= obj.getAmount();
        }
    }

    private void fireFlexAchievement(Player player, EventDefinition def) {
        String achievementId = def.getFlexAchievement();
        if (achievementId == null || achievementId.isEmpty()) return;
        try {
            Class<?> pluginClass = Class.forName("ru.flexachievements.FlexAchievementsPlugin");
            Object api = pluginClass.getMethod("getApi").invoke(null);
            if (api != null) {
                Map<String, Object> context = new LinkedHashMap<String, Object>();
                context.put("event_id", def.getId());
                context.put("custom_event", achievementId);
                api.getClass().getMethod("fireCustomEvent", Player.class, String.class, Map.class)
                        .invoke(api, player, achievementId, context);
            }
        } catch (Exception ignored) {}
    }

    // === Helpers ===

    private void updateNumeric(String eventId, UUID playerId, ObjectiveType type, int amount) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i).getType() == type) {
                progress.addProgress(i, amount);
            }
        }
        checkCompletion(eventId, playerId, event);
    }

    private void updateTargeted(String eventId, UUID playerId, ObjectiveType type, String target) {
        ActiveEvent event = plugin.getEventManager().getActiveEvent(eventId);
        if (event == null) return;
        List<Objective> objectives = event.getDefinition().getObjectives();
        if (objectives.isEmpty()) return;

        PlayerProgress progress = getOrCreate(eventId, playerId);
        for (int i = 0; i < objectives.size(); i++) {
            Objective obj = objectives.get(i);
            if (obj.getType() == type) {
                if (obj.getTarget() == null || obj.getTarget().isEmpty()
                        || obj.getTarget().equalsIgnoreCase(target)) {
                    progress.addProgress(i, 1);
                }
            }
        }
        checkCompletion(eventId, playerId, event);
    }

    private boolean matchesTarget(Objective obj, String mobType, String bossFile) {
        if (obj.getTarget() == null || obj.getTarget().isEmpty()) return true;
        if (bossFile != null && obj.getTarget().equalsIgnoreCase(bossFile)) return true;
        if (mobType != null && obj.getTarget().equalsIgnoreCase(mobType)) return true;
        return false;
    }

    private String getCompletedObjectiveSummary(List<Objective> objectives) {
        if (objectives.isEmpty()) return "";
        if (objectives.size() == 1) return objectiveName(objectives.get(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(objectives.size(), 3); i++) {
            if (i > 0) sb.append(", ");
            sb.append(objectiveName(objectives.get(i)));
        }
        return sb.toString();
    }

    private String objectiveName(Objective obj) {
        switch (obj.getType()) {
            case KILL_MOBS: return plugin.getLang().msg("messages.objective-names.kill-mobs");
            case KILL_BOSS: return plugin.getLang().msg("messages.objective-names.kill-boss");
            case KILL_ELITE: return plugin.getLang().msg("messages.objective-names.kill-elite");
            case SURVIVE_TIME: return plugin.getLang().msg("messages.objective-names.survive-time");
            case REACH_WAVE: return plugin.getLang().msg("messages.objective-names.reach-wave");
            case COLLECT_ITEM: return plugin.getLang().msg("messages.objective-names.collect-item");
            case MINE_BLOCK: return plugin.getLang().msg("messages.objective-names.mine-block");
            case SCORE_POINTS: return plugin.getLang().msg("messages.objective-names.score-points");
            case NO_DEATH: return plugin.getLang().msg("messages.objective-names.no-death");
            case DEAL_DAMAGE: return plugin.getLang().msg("messages.objective-names.deal-damage");
            default: return obj.getType().name();
        }
    }

    private PlayerProgress getOrCreate(String eventId, UUID playerId) {
        Map<UUID, PlayerProgress> map = eventProgress.get(eventId);
        if (map == null) {
            map = new LinkedHashMap<UUID, PlayerProgress>();
            eventProgress.put(eventId, map);
        }
        PlayerProgress p = map.get(playerId);
        if (p == null) {
            p = new PlayerProgress(playerId);
            map.put(playerId, p);
        }
        return p;
    }
}
