package org.processmining.plugins.aux;

import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CancellationState {
    private final State state;
    private final Map<State, List<Transition>> failureEventsByStartState;
    private final Transition catchingEvent;

    public CancellationState(
            State state, Map<State,
            List<Transition>> failureEventsByStartState,
            Transition catchingEvent) {

        this.state = state;
        this.failureEventsByStartState = failureEventsByStartState;
        this.catchingEvent = catchingEvent;
    }

    public Set<State> getCancellationSet() {
        return failureEventsByStartState.keySet();
    }

    public State getState() {
        return this.state;
    }

    public Set<Object> getFailureEvents() {
        return failureEventsByStartState.values().stream()
                .flatMap(List::stream)
                .map(Transition::getIdentifier)
                .collect(Collectors.toSet());
    }

    public Transition getCatchingEvent() {
        return this.catchingEvent;
    }
}
