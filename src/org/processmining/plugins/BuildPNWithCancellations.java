package org.processmining.plugins;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.transitionsystem.MinimalRegionConnection;
import org.processmining.models.connections.transitionsystem.RegionConnection;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.ResetNet;
import org.processmining.models.graphbased.directed.petrinet.impl.ResetNetImpl;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;
import org.processmining.models.graphbased.directed.transitionsystem.regions.Region;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionSet;
import org.processmining.plugins.ts.ui.TSDecomposeUI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Plugin(name = "Build RWF net from transition system with cancellations",
        parameterLabels = {"Transition system"}, returnLabels = {"RWF Net"},
        returnTypes = {ResetNet.class}, userAccessible = true,
        help = "Build RWF net from transition system with cancellations")
public class BuildPNWithCancellations {

    @UITopiaVariant(affiliation = "HSE", author = "S. Zlobina", email = "zlobinasv@edu.hse.ru")
    @PluginVariant(variantLabel = "Build RWF net from transition system with cancellations", requiredParameterLabels = {
            0})
    public ResetNet constructTS(UIPluginContext context, TransitionSystem initialTs) throws ConnectionCannotBeObtained {
        List<Object> cancellationStates = new TSDecomposeUI(context).decompose(initialTs);

        //step 0 - Prepare TS: merge the states with identical outflow

        // step 1 - Construct a TS' from TS by deleting the cancellation state and its incident arcs
        TransitionSystem tsWithoutCancellations = excludeCancellationStatesFromTs(initialTs, cancellationStates);

        // step 2 - Verify that cancellation sets are minimal regions
        Map<Object, List<Transition>> failureEventsTransitionsByEventName =
                getFailureEvents(initialTs, cancellationStates);
        List<Set<State>> cancellationSets = getCancellationSets(failureEventsTransitionsByEventName);

        RegionSet regions = context.tryToFindOrConstructFirstObject(RegionSet.class, MinimalRegionConnection.class,
                RegionConnection.REGIONS, tsWithoutCancellations);

        // TODO: is it the right way of verification?
        for (Region minimalRegion : regions) {
            for (Set<State> cancellationSet : cancellationSets) {
                if (cancellationSet.containsAll(minimalRegion) && cancellationSet.size() > minimalRegion.size()) {
                    throw new RuntimeException(
                            "RWF-net cannot be constructed, because cancellation set is not a minimal region");
                }
            }
        }

        // step 3 - Construct WF' from TS' using state-based region algorithm
        Petrinet petriNet = context.tryToFindOrConstructFirstObject(Petrinet.class, null, null,
                tsWithoutCancellations);

        // step 4 - Add to WF' transitions corresponding to failure and catching events
        // TODO

        return new ResetNetImpl(initialTs.getLabel());
    }

    private TransitionSystem excludeCancellationStatesFromTs(TransitionSystem transitionSystem,
                                                             List<Object> cancellationStates) {
        TransitionSystem regularTs = new TransitionSystemImpl("TS without cancellations");

        transitionSystem.getStates().forEach(state -> {
            if (!cancellationStates.contains(transitionSystem.getNode(state))) {
                regularTs.addState(state);
            }
        });

        transitionSystem.getEdges().forEach(transition -> {
            if ((!cancellationStates.contains(transition.getSource())) &&
                    !cancellationStates.contains(transition.getTarget())) {

                regularTs.addTransition(
                        transition.getSource().getIdentifier(),
                        transition.getTarget().getIdentifier(),
                        transition.getIdentifier()
                );
            }
        });

        return regularTs;
    }

    private Map<Object, List<Transition>> getFailureEvents(TransitionSystem transitionSystem,
                                                           List<Object> cancellationStates) {
        return transitionSystem.getEdges().stream()
                .filter(transition -> cancellationStates.contains(transition.getTarget()))
                .collect(Collectors.groupingBy(Transition::getIdentifier));

    }

    private List<Set<State>> getCancellationSets(Map<Object, List<Transition>> failureEvents) {
        return failureEvents.values().stream()
                .map(transitions -> transitions.stream()
                        .map(Transition::getSource)
                        .collect(Collectors.toSet()))
                .collect(Collectors.toList());
    }

}
