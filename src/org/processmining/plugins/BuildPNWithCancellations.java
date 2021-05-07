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
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;
import org.processmining.models.graphbased.directed.petrinet.impl.ResetNetImpl;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionSet;
import org.processmining.plugins.ts.ui.TSDecomposeUI;
import java.util.ArrayList;
import java.util.Collection;
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

        // step 1 - Construct a TS' from TS by deleting the cancellation state and its incident arcs
        TransitionSystem tsWithoutCancellations = excludeCancellationStatesFromTs(initialTs, cancellationStates);

        // step 2 -
        Map<Object, List<Transition>> failureEventsTransitionsByEventName =
                getFailureEvents(initialTs, cancellationStates);
        List<Set<State>> cancellationSets = getCancellationSets(failureEventsTransitionsByEventName);
        // TODO: verify, that all cancellation sets are minimal regions
        RegionSet regions = context.tryToFindOrConstructFirstObject(RegionSet.class, MinimalRegionConnection.class,
                RegionConnection.REGIONS, tsWithoutCancellations);

        // step 3 - Construct WF' from TS' using state-based region algorithm
        Petrinet petriNet2 = context.tryToFindOrConstructFirstObject(Petrinet.class, null, null,
                tsWithoutCancellations);

        return new ResetNetImpl(initialTs.getLabel());
//        return petriNet2;
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
