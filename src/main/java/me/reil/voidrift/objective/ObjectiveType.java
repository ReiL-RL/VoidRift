package me.reil.voidrift.objective;

/**
 * All possible objective types for event completion.
 * Designed to be very flexible — covers combat, collection, exploration, survival, etc.
 */
public enum ObjectiveType {

    // === Combat ===
    KILL_MOBS,           // Kill N mobs (any or specific type/boss)
    KILL_BOSS,           // Kill a specific EliteMobs boss
    KILL_ELITE,          // Kill any EliteMobs elite mob
    DEAL_DAMAGE,         // Deal N total damage
    TAKE_DAMAGE,         // Take N total damage (tank objective)
    NO_DEATH,            // Don't die during event (amount=1 means must survive)
    KILL_STREAK,         // Kill N mobs without dying
    LAST_HIT_BOSS,       // Land the killing blow on a boss

    // === Waves & Survival ===
    REACH_WAVE,          // Reach wave N
    SURVIVE_TIME,        // Survive N seconds
    ALL_MOBS_DEAD,       // All mobs in zone are dead (wave clear)
    CLEAR_WAVES,         // Clear N waves completely

    // === Collection ===
    COLLECT_ITEM,        // Pick up / have item (material or custom name)
    COLLECT_FROM_MOB,    // Get item drop from mob kill
    COLLECT_FROM_CHEST,  // Loot item from chest/container
    MINE_BLOCK,          // Break N blocks (specific type)
    PLACE_BLOCK,         // Place N blocks

    // === Score & Progress ===
    SCORE_POINTS,        // Reach N score points
    SCORE_TOP,           // Be #1 in score at end

    // === Movement & Exploration ===
    ENTER_ZONE,          // Enter a specific zone/area
    REACH_LOCATION,      // Reach specific coordinates (within radius)
    USE_PORTAL,          // Use an intermediate portal
    TRAVEL_DISTANCE,     // Travel N blocks total

    // === Interaction ===
    USE_ITEM,            // Right-click with specific item
    EAT_FOOD,            // Eat N food items
    CRAFT_ITEM,          // Craft specific item
    ENCHANT_ITEM,        // Enchant an item

    // === Team/Social ===
    PLAYERS_IN_EVENT,    // Have N players in event simultaneously
    REVIVE_PLAYER,       // Help/revive another player (custom mechanic)

    // === Time-based ===
    COMPLETE_BEFORE,     // Complete event within N seconds
    SPEED_KILL,          // Kill boss within N seconds of spawn

    // === Custom ===
    CUSTOM               // Custom condition checked via API/command
}
