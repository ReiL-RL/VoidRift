package me.reil.voidrift.objective;

import java.util.Collections;
import java.util.List;

/**
 * A single stage in a quest chain.
 * Contains a list of objectives that must be completed to advance to the next stage.
 */
public final class QuestStage {

    private final List<Objective> objectives;
    private final String message;

    public QuestStage(List<Objective> objectives, String message) {
        this.objectives = objectives != null ? objectives : Collections.<Objective>emptyList();
        this.message = message;
    }

    public List<Objective> getObjectives() { return objectives; }
    public String getMessage() { return message; }
}
