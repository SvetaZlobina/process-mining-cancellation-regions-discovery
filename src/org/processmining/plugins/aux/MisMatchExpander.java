package org.processmining.plugins.aux;

import org.processmining.framework.plugin.Progress;
import org.processmining.framework.util.Pair;
import org.processmining.framework.util.search.NodeExpander;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MisMatchExpander implements NodeExpander<Pair<Set<State>, Integer>> {

    private MisMatch mismatchFound;
    private final int maxSize;
    private final List<State> from;
    private final TransitionSystem ts;
    private final Set<Object> cancellationSetFromIds;

    public MisMatchExpander(int maxSize, List<State> from, TransitionSystem ts, MisMatch mismatchFound, Set<Object> cancellationSetFromIds) {
        this.maxSize = maxSize;
        this.from = from;
        this.ts = ts;
        this.mismatchFound = mismatchFound;
        this.cancellationSetFromIds = cancellationSetFromIds;
    }

    public Collection<Pair<Set<State>, Integer>> expandNode(Pair<Set<State>, Integer> toExpand, Progress progress,
                                                            Collection<Pair<Set<State>, Integer>> unmodifiableResultCollection) {

        Collection<Pair<Set<State>, Integer>> toExpandFurther = new ArrayList<Pair<Set<State>, Integer>>();

        Set<State> baseSet = toExpand.getFirst();
        int index = toExpand.getSecond();

        if (baseSet.size() >= maxSize) {
            return toExpandFurther;
        }

        State s = from.get(index);
        if (!baseSet.contains(s)) {
            LinkedHashSet<State> newSet = new LinkedHashSet<State>(baseSet);
            newSet.add(s);
            MisMatch mismatch = new MisMatch(newSet, ts, cancellationSetFromIds);
            synchronized (mismatchFound) {
                if (mismatch.compareTo(mismatchFound) < 0) {
                    mismatchFound = mismatch;
                }
            }
            if (index < from.size() - 1) {
                toExpandFurther.add(new Pair<Set<State>, Integer>(newSet, index + 1));
            }
        }
        if (index < from.size() - 1) {
            toExpandFurther.add(new Pair<Set<State>, Integer>(baseSet, index + 1));
        }

        return toExpandFurther;
    }

    public void processLeaf(Pair<Set<State>, Integer> leaf, Progress progress,
                            Collection<Pair<Set<State>, Integer>> resultCollection) {
        // do nothing.
    }

    public MisMatch getSelectedMisMatch() {
        return mismatchFound;
    }
}
