package me.reil.voidrift.portal;

/**
 * Types of portals in VoidRift.
 */
public enum PortalType {

    /** Entry portal — teleports player INTO the event zone. Static location. */
    ENTRY,

    /** Exit/Return portal — teleports player OUT of the event zone back to origin. */
    EXIT,

    /** Intermediate portal — teleports between different areas within the event. */
    INTERMEDIATE,

    /** Dynamic entry — appears at a random location from a list of coordinates. */
    DYNAMIC
}
