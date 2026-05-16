package me.reil.voidrift.wizard;

import me.reil.voidrift.portal.PortalType;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public final class WizardSession {

    private final String eventId;
    private WizardStep step;
    private WizardStep previousStep;
    private PortalType entryType;
    private int dynamicCount;
    private int intermediateCount;

    /** Stores pairs: [locA, locB, locA, locB, ...] for intermediate portals */
    private final List<Location> intermediatePairs;

    public WizardSession(String eventId, WizardStep step) {
        this.eventId = eventId;
        this.step = step;
        this.previousStep = null;
        this.entryType = PortalType.ENTRY;
        this.dynamicCount = 0;
        this.intermediateCount = 0;
        this.intermediatePairs = new ArrayList<Location>();
    }

    public String getEventId() { return eventId; }
    public WizardStep getStep() { return step; }

    public void setStep(WizardStep newStep) {
        this.previousStep = this.step;
        this.step = newStep;
    }

    public WizardStep getPreviousStep() { return previousStep; }

    public PortalType getEntryType() { return entryType; }
    public void setEntryType(PortalType entryType) { this.entryType = entryType; }

    public int getDynamicCount() { return dynamicCount; }
    public void incrementDynamicCount() { this.dynamicCount++; }

    public int getIntermediateCount() { return intermediateCount; }
    public void incrementIntermediateCount() { this.intermediateCount++; }

    public List<Location> getIntermediatePairs() { return intermediatePairs; }

    /**
     * Go back to the previous step. Returns true if back was possible.
     */
    public boolean goBack() {
        if (previousStep == null || previousStep == WizardStep.CHOOSE_TYPE) return false;
        this.step = this.previousStep;
        this.previousStep = null;
        return true;
    }
}
