package org.processmining.plugins.aux;

import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.regions.Region;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionImpl;
import java.util.LinkedHashSet;
import java.util.Set;

public class MisMatch implements Comparable<MisMatch> {

    private final Set<State> states;

    private final LinkedHashSet<Object> toSplit;

    private int splitCount;

    private final Set<Object> cancellationSetFromIds;

    public MisMatch(Set<State> states, TransitionSystem ts, Set<Object> cancellationSetFromIds) {
        this.states = states;
        this.cancellationSetFromIds = cancellationSetFromIds;

        Region r = new RegionImpl();
        r.addAll(states);

        r.initialize(ts);

        toSplit = new LinkedHashSet<Object>();
        splitCount = 0;
        for (Object id : ts.getTransitions()) {
            boolean entering = r.getEntering().contains(id);
            boolean exiting = r.getExiting().contains(id);
            boolean notCross = r.getInternal().contains(id) || r.getExternal().contains(id);

            boolean split = false;
            if (entering && exiting && notCross) {
                splitCount += 3;
                split = true;
            } else if (entering && exiting) {
                splitCount += 2;
                split = true;
            } else if (entering && notCross) {
                splitCount += 2;
                split = true;
            } else if (exiting && notCross) {
                splitCount += 2;
                split = true;
            }
            if (split) {
                getToSplit().add(id);
            }

        }
    }

    public Set<State> getStates() {
        return states;
    }

    public Set<Object> getToSplit() {
        return toSplit;
    }

    public boolean equals(Object o) {
        if (o instanceof MisMatch) {
            return (((MisMatch) o).states.equals(states));
        } else {
            return false;
        }
    }

    public int compareTo(MisMatch m) {
        if (toSplit.size() != m.toSplit.size()) {
            return toSplit.size() - m.toSplit.size();
        }

        if (states.size() != m.states.size()) {
            return states.size() - m.states.size();
        }

        if (splitCount != m.splitCount) {
            return splitCount - m.splitCount;
        }

        if (equals(m)) {
            return 0;
        }

        return m.hashCode() - hashCode();
    }

    public String toString() {
        return "states = {" + states.toString() + "}";
    }
}
