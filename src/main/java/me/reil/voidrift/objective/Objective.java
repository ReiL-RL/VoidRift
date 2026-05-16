package me.reil.voidrift.objective;

/**
 * A single objective/condition for event completion.
 * Loaded from events.yml objectives section.
 */
public final class Objective {

    private final ObjectiveType type;
    private final int amount;         // target amount (kills, score, seconds, wave)
    private final String target;      // boss filename, item material, mob type, etc.

    public Objective(ObjectiveType type, int amount, String target) {
        this.type = type;
        this.amount = amount;
        this.target = target;
    }

    public ObjectiveType getType() { return type; }
    public int getAmount() { return amount; }
    public String getTarget() { return target; }
}
