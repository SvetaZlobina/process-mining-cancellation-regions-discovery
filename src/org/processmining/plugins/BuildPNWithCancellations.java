package org.processmining.plugins;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.framework.plugin.events.Logger;
import org.processmining.framework.util.Pair;
import org.processmining.framework.util.search.MultiThreadedSearcher;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.connections.transitionsystem.GeneralizedExitationRegionConnection;
import org.processmining.models.connections.transitionsystem.MinimalRegionConnection;
import org.processmining.models.connections.transitionsystem.RegionConnection;
import org.processmining.models.connections.transitionsystem.TransitionSystemConnection;
import org.processmining.models.graphbased.directed.petrinet.ResetNet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemFactory;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;
import org.processmining.models.graphbased.directed.transitionsystem.regions.GeneralizedExitationRegions;
import org.processmining.models.graphbased.directed.transitionsystem.regions.Region;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionSet;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.aux.CancellationState;
import org.processmining.plugins.aux.MisMatch;
import org.processmining.plugins.aux.MisMatchExpander;
import org.processmining.plugins.aux.SplitObject;
import org.processmining.plugins.ui.TSDecomposeByCancellationsUI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Plugin(name = "Build RWF net from transition system with cancellations",
        parameterLabels = {"Transition system"}, returnLabels = {"RWF Net"},
        returnTypes = {ResetNet.class}, userAccessible = true,
        help = "Build RWF net from transition system with cancellations")
public class BuildPNWithCancellations {

    @UITopiaVariant(affiliation = "HSE", author = "S. Zlobina", email = "zlobinasv@edu.hse.ru")
    @PluginVariant(variantLabel = "Build RWF net from transition system with cancellations", requiredParameterLabels = {
            0})
    public ResetNet constructTS(UIPluginContext context, TransitionSystem initialTs)
            throws ConnectionCannotBeObtained, InterruptedException, ExecutionException {
        List<Object> cancellations = new TSDecomposeByCancellationsUI(context).decompose(initialTs);

        // step 1 - Construct a TS' from TS by deleting the cancellation state and its incident arcs
        TransitionSystem tsWithoutCancellations = excludeCancellationStatesFromTs(initialTs, cancellations);

        StartStateSet initialStates = context.tryToFindOrConstructFirstObject(StartStateSet.class,
                TransitionSystemConnection.class, TransitionSystemConnection.STARTIDS, tsWithoutCancellations);

        AcceptStateSet acceptStates = context.tryToFindOrConstructFirstObject(AcceptStateSet.class,
                TransitionSystemConnection.class, TransitionSystemConnection.ACCEPTIDS, tsWithoutCancellations);

        // find self-loops
        List<org.processmining.models.graphbased.directed.transitionsystem.Transition> selfLoops =
                new ArrayList<>();
        for (org.processmining.models.graphbased.directed.transitionsystem.Transition t : tsWithoutCancellations
                .getEdges()) {
            if (t.getSource() == t.getTarget()) {
                selfLoops.add(t);
            }
        }
        Map<Object, Object> toFuse = new HashMap<Object, Object>();
        if (!selfLoops.isEmpty()) {
            TransitionSystem newTS = TransitionSystemFactory.newTransitionSystem(tsWithoutCancellations.getLabel());

            for (State s : tsWithoutCancellations.getNodes()) {
                newTS.addState(s.getIdentifier());
            }

            int i = 0;
            for (org.processmining.models.graphbased.directed.transitionsystem.Transition t : tsWithoutCancellations
                    .getEdges()) {
                if (t.getSource() == t.getTarget()) {
                    final int newState = i++;
                    Object endIdentifier = new Object() {
                        public String toString() {
                            return "fresh " + newState;
                        }
                    };
                    newTS.addState(endIdentifier);
                    SplitObject sl1 = new SplitObject(t.getIdentifier(), SplitObject.SL1);
                    newTS.addTransition(t.getSource().getIdentifier(), endIdentifier, sl1);
                    SplitObject sl2 = new SplitObject(t.getIdentifier(), SplitObject.SL2);
                    newTS.addTransition(endIdentifier, t.getTarget().getIdentifier(), sl2);

                    toFuse.put(sl1, sl2);
                } else {
                    newTS
                            .addTransition(t.getSource().getIdentifier(), t.getTarget().getIdentifier(), t
                                    .getIdentifier());
                }
            }
        }

        //TODO добавить обработку кейса, когда сет пустой
        Set<CancellationState> cancellationStates = extractCancellationStates(initialTs, cancellations);

        Pair<ResetNet, Marking> result =
                convertToPetrinet(context, tsWithoutCancellations.getLabel(), tsWithoutCancellations, initialStates,
                        acceptStates,
                        1, toFuse, cancellationStates);

        return result.getFirst();
    }

    private Pair<ResetNet, Marking> convertToPetrinet(PluginContext context, String label, TransitionSystem ts,
                                                      StartStateSet initial, AcceptStateSet accept, int split,
                                                      Map<Object, Object> toFuse,
                                                      Set<CancellationState> cancellationStates)
            throws ConnectionCannotBeObtained, InterruptedException, ExecutionException {


        RegionSet regions = context.tryToFindOrConstructFirstObject(RegionSet.class, MinimalRegionConnection.class,
                RegionConnection.REGIONS, ts);
        context.log("Minimal Regions identified.", Logger.MessageLevel.DEBUG);

        if (context.getProgress().isCancelled()) {
            return null;
        }

        GeneralizedExitationRegions gers = context.tryToFindOrConstructFirstObject(GeneralizedExitationRegions.class,
                GeneralizedExitationRegionConnection.class, GeneralizedExitationRegionConnection.GERS, ts);

        if (context.getProgress().isCancelled()) {
            return null;
        }

        Set<Object> cancellationSetFromIds = new HashSet<>();
        Set<State> cancellationSet = cancellationStates.stream().flatMap(state -> state.getCancellationSet().stream())
                .collect(Collectors.toSet());
        for (Transition transition : ts.getEdges()) {
            if (cancellationSet.contains(transition.getSource())) {
                cancellationSetFromIds.add(transition.getIdentifier());
            }
        }

        MisMatch toResolve = checkForwardClosure(ts, gers, regions, context, cancellationSetFromIds);

        if (context.getProgress().isCancelled()) {
            return null;
        }

        if (toResolve != null) {

            context.log("This transition system does not meet the forward-closure property.",
                    Logger.MessageLevel.DEBUG);
            context.log("    Splitting transitions: " + toResolve.getToSplit(), Logger.MessageLevel.DEBUG);
            //			context.log("    Making a region of: " + toResolve.getStates(), MessageLevel.DEBUG);

            TransitionSystem newTS = TransitionSystemFactory.newTransitionSystem(label + " Split: " + split);

            for (State s : ts.getNodes()) {
                newTS.addState(s.getIdentifier());
                if (context.getProgress().isCancelled()) {
                    return null;
                }
            }

            for (org.processmining.models.graphbased.directed.transitionsystem.Transition transition : ts.getEdges()) {
                if (context.getProgress().isCancelled()) {
                    return null;
                }
                Object identifier;
                identifier = transition.getIdentifier();
                if (toResolve.getToSplit().contains(identifier)) {

                    boolean sc = toResolve.getStates().contains(transition.getSource());
                    boolean tc = toResolve.getStates().contains(transition.getTarget());

                    SplitObject newIdentifier;
                    if (sc == tc) {
                        // not crossing
                        newIdentifier = new SplitObject(identifier, SplitObject.NOTCROSS);
                    } else if (sc) {
                        newIdentifier = new SplitObject(identifier, SplitObject.EXIT);
                    } else {
                        newIdentifier = new SplitObject(identifier, SplitObject.ENTER);
                    }
                    // Check if identifier is part of a toFuse map.
                    Iterator<Map.Entry<Object, Object>> it = toFuse.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Object, Object> entry = it.next();
                        if (entry.getKey().equals(identifier)) {
                            // add the new entry,
                            toFuse.put(newIdentifier, new SplitObject(entry.getValue(), newIdentifier.getOpposed()));
                            break;
                        } else if (entry.getValue().equals(identifier)) {
                            // add the new entry,
                            toFuse.put(new SplitObject(entry.getKey(), newIdentifier.getOpposed()), newIdentifier);
                            break;
                        }
                    }
                    identifier = newIdentifier;
                }
                newTS.addTransition(transition.getSource().getIdentifier(), transition.getTarget().getIdentifier(),
                        identifier);
            }
            toFuse.keySet().removeAll(toResolve.getToSplit());
            context.getProvidedObjectManager().createProvidedObject(newTS.getLabel(), newTS, context);
            context.getProvidedObjectManager().createProvidedObject("Initial states of " + ts.getLabel(), initial,
                    context);
            context.getProvidedObjectManager().createProvidedObject("Accept states of " + ts.getLabel(), accept,
                    context);
            context.getConnectionManager().addConnection(new TransitionSystemConnection(newTS, initial, accept));
            ts = null;
            return convertToPetrinet(context, label, newTS, initial, accept, split + 1, toFuse, cancellationStates);
        }

        context.log("This transition system satifies the forward-closure property.", Logger.MessageLevel.DEBUG);

        // step 2 - Verify that cancellation sets are minimal regions
        // TODO: переписать попроще
//        cancellationStates.stream().map(CancellationState::getCancellationSet).forEach(
//                set -> {
//                    Set<Object> cancellationSetIds = set.stream().map(State::getIdentifier).collect(Collectors.toSet());
//
//                    if (regions.stream()
//                            .map(region -> region.stream().map(State::getIdentifier).collect(Collectors.toSet()))
//                            .noneMatch(regionIds -> regionIds.equals(cancellationSetIds))) {
//                        throw new RuntimeException("Cancellation set is not a minimal region");
//                    }
//                }
//        );

        ResetNet resetNet = PetrinetFactory.newResetNet(ts.getLabel());
        Marking marking = new Marking();

        // Add transitions
        Map<Object, org.processmining.models.graphbased.directed.petrinet.elements.Transition> id2trans =
                new HashMap<>();

        Set<Object> identifiers = new HashSet<>(ts.getTransitions());

        // First handle the self loops.
        for (Map.Entry<Object, Object> fuse : toFuse.entrySet()) {
            org.processmining.models.graphbased.directed.petrinet.elements.Transition t =
                    resetNet.addTransition(fuse.getKey().toString());
            id2trans.put(fuse.getKey(), t);
            identifiers.remove(fuse.getKey());
            id2trans.put(fuse.getValue(), t);
            identifiers.remove(fuse.getValue());
        }

        for (Object identifier : identifiers) {

            id2trans.put(identifier, resetNet.addTransition(identifier.toString()));
        }

        // Add Places
        Set<Place> toRemove = new HashSet<Place>();
        Map<Region, Place> reg2place = new HashMap<Region, Place>();
        for (Region r : regions) {
            Place p = resetNet.addPlace(r.toString());
            reg2place.put(r, p);
            // now check for initial state

            for (State s : r) {
                if (initial.contains(s.getIdentifier())) {
                    marking.add(p);
                }
            }

            for (Object identifier : ts.getTransitions()) {
                org.processmining.models.graphbased.directed.transitionsystem.Transition t = ts.getEdges(identifier)
                        .iterator().next();
                if (!r.contains(t.getSource()) && r.contains(t.getTarget())) {
                    // entering
                    if (toFuse.containsKey(identifier)) {
                        toRemove.add(p);
                    } else {
                        resetNet.addArc(id2trans.get(identifier), p);
                    }
                } else if (r.contains(t.getSource()) && !r.contains(t.getTarget())) {
                    // exiting
                    if (toFuse.containsValue(identifier)) {
                        toRemove.add(p);
                    } else {
                        resetNet.addArc(p, id2trans.get(identifier));
                    }
                }
            }

        }
        for (Place p : toRemove) {
            resetNet.removePlace(p);
        }

//        for (Set<State> cancellationSet : cancellationSets) {
//            Set<Object> cancellationSetIds = cancellationSet.stream()
//                    .map(State::getIdentifier)
//                    .collect(Collectors.toSet());
//
//            //TODO: separate failure events and cancellation sets (1 cancellationState - 1 cancellationSet - M failure events)
//
//            Object failureEvent = failureEventsTransitionsByEventName.entrySet().stream().findFirst().;
//            id2trans.put(value, resetNet.addTransition(key.toString())
//        }
//        failureEventsTransitionsByEventName
//                .forEach((key, value) -> id2trans.put(value, resetNet.addTransition(key.toString())));
//
//
//        regions.forEach(region -> {
//            Set<Object> regionIds = region.stream().map(State::getIdentifier).collect(Collectors.toSet());
//            if (regionIds.equals(cancellationSetIds)) {
//                resetNet.addArc(reg2place.get(region), id2trans.get(failureEventsTransitionsByEventName.get(0)))
//            }
//        });

        for (CancellationState cancellationState : cancellationStates) {
            Set<State> cancellationSet1 = cancellationState.getCancellationSet();

            Set<Object> cancellationSetIds = cancellationSet1.stream()
                    .map(State::getIdentifier)
                    .collect(Collectors.toSet());

            Set<Object> failureEvents = cancellationState.getFailureEvents();

            Place cancellationPlace = resetNet.addPlace(cancellationState.getState().getLabel());

            for (Object event : failureEvents) {
                org.processmining.models.graphbased.directed.petrinet.elements.Transition resetNetFailureTransition =
                        resetNet.addTransition(event.toString());
//                id2trans.put(event, resetNetFailureTransition);

                for (Region minimalRegion : regions) {
                    Set<Object> regionIds =
                            minimalRegion.stream().map(State::getIdentifier).collect(Collectors.toSet());

                    if (regionIds.equals(cancellationSetIds)) {
                        resetNet.addArc(reg2place.get(minimalRegion), resetNetFailureTransition);
                    } else if (regionIds.stream().anyMatch(cancellationSetIds::contains)) {
                        resetNet.addResetArc(reg2place.get(minimalRegion), resetNetFailureTransition);
                    }
                }

                resetNet.addArc(resetNetFailureTransition, cancellationPlace);
            }

            org.processmining.models.graphbased.directed.petrinet.elements.Transition catchingTransition =
                    resetNet.addTransition(cancellationState.getCatchingEvent().getIdentifier().toString());
            resetNet.addArc(cancellationPlace, catchingTransition);

            //TODO: может быть, сделать set<catchingEvents>?
            Set<Object> stateAfterCatchingEvent =
                    Collections.singleton(cancellationState.getCatchingEvent().getTarget().getIdentifier());
            regions.forEach(region -> {
                Set<Object> regionIds = region.stream().map(State::getIdentifier).collect(Collectors.toSet());
                if (regionIds.equals(stateAfterCatchingEvent)) {
                    resetNet.addArc(catchingTransition, reg2place.get(region));
                }
            });

        }


        context.getConnectionManager().addConnection(new InitialMarkingConnection(resetNet, marking));

//        context.getFutureResult(0).setLabel("Petrinet Synthesized from " + label);
//        context.getFutureResult(1).setLabel("Initial marking of " + context.getFutureResult(0).getLabel());

        return new Pair<>(resetNet, marking);
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

//    private Map<Object, List<Transition>> getFailureEvents(TransitionSystem transitionSystem,
//                                                           List<Object> cancellationStates) {
//        Set<Transition> allTransitions = transitionSystem.getEdges();
//        Map<Object, List<Transition>> cancellation
//        for (Object cancellationState : cancellationStates) {
//
//        }
//        return .stream()
//                .filter(transition -> cancellationStates.contains(transition.getTarget()))
//                .collect(Collectors.groupingBy(Transition::getIdentifier));
//
//    }

    private Set<CancellationState> extractCancellationStates(TransitionSystem transitionSystem,
                                                             List<Object> cancellations) {
        Set<CancellationState> cancellationStates = new HashSet<>();
        Set<Transition> allTransitions = transitionSystem.getEdges();

        for (Object cancellation : cancellations) {
            Map<State, List<Transition>> failureTransitions = allTransitions.stream()
                    .filter(transition -> transition.getTarget().equals(cancellation))
                    .collect(Collectors.groupingBy(Transition::getSource));
            Set<Transition> catchingTransitions = allTransitions.stream()
                    .filter(transition -> transition.getSource().equals(cancellation))
                    .collect(Collectors.toSet());
//            if (catchingTransitions.size() > 1) {
//                throw new RuntimeException("More than 1 catching event for cancellation state");
//            }
            Transition catchingTransition = catchingTransitions.stream().findFirst().orElse(null);
//            Set<Object> statesAfterCatchingTransition = allTransitions.stream()
//                    .filter(transition -> transition.getSource().equals(catchingTransition))
//                    .collect(Collectors.toSet())
            cancellationStates.add(new CancellationState(
                    (State) cancellation,
                    failureTransitions,
                    catchingTransition
            ));
        }

        return cancellationStates;
    }

    private MisMatch checkForwardClosure(TransitionSystem ts, GeneralizedExitationRegions gers, RegionSet regions,
                                         PluginContext context, Set<Object> cancellationSetFromIds)
            throws InterruptedException, ExecutionException {

        MisMatch mismatchFound = null;
        for (Object identifier : ts.getTransitions()) {
            // get the ger
            Set<State> ger = new LinkedHashSet<State>(gers.get(identifier));

            // construct the intersection of all pre-regions.
            Set<State> inter = null;
            List<Region> pre = new ArrayList<Region>();
            for (Region r : regions) {
                if (r.getExiting().contains(identifier)) {
                    pre.add(r);
                    if (inter == null) {
                        inter = new LinkedHashSet<State>(r);
                    } else {
                        inter.retainAll(r);
                    }
                }
            }
            if (ger.equals(inter)) {
                // intersection equals ger
                continue;
            }
            if (inter == null) {
                inter = new LinkedHashSet<State>();
            }
            context.log("For transition " + identifier
                    + " the GER is not equal to the intersection of pre-regions: ger size=" + ger.size()
                    + " intersection size=" + inter.size(), Logger.MessageLevel.DEBUG);

            // check all proper subsets of inter that include ger
            int maxSize = inter.size() - 1;
            inter.removeAll(ger);

            MisMatch mismatch = new MisMatch(new LinkedHashSet<State>(ger), ts, cancellationSetFromIds);
            if ((mismatchFound == null) || (mismatch.compareTo(mismatchFound) < 0)) {
                mismatchFound = mismatch;
            }

            //			extendMisMatches(ger, new ArrayList<State>(inter), 0, maxSize, mismatchFound, ts);

            MisMatchExpander expander = new MisMatchExpander(maxSize, new ArrayList<State>(inter), ts, mismatchFound,
                    cancellationSetFromIds);
            MultiThreadedSearcher<Pair<Set<State>, Integer>> searcher =
                    new MultiThreadedSearcher<Pair<Set<State>, Integer>>(
                            expander, MultiThreadedSearcher.BREADTHFIRST);
            searcher.addInitialNodes(new Pair<Set<State>, Integer>(mismatchFound.getStates(), 0));
            searcher.startSearch(context.getExecutor(), context.getProgress(), Collections
                    .<Pair<Set<State>, Integer>>emptyList());

            mismatchFound = expander.getSelectedMisMatch();

            context.log("All proper subsets of the intersection of the pre-regions of " + identifier
                    + " have been investigated", Logger.MessageLevel.DEBUG);

        }

        return mismatchFound;
    }

}




