package me.reil.voidrift.event;

/**
 * Types of events.
 */
public enum EventType {

    /** Wave-based mob survival. */
    WAVE_SURVIVAL,

    /** Kill a boss mob. */
    BOSS_FIGHT,

    /** Collect resources in a zone. */
    RESOURCE_RACE,

    /** PvP arena. */
    PVP_ARENA,

    /** Timed challenge (break blocks, etc). */
    TIMED_CHALLENGE,

    /** Custom (scripted by commands). */
    CUSTOM
}

