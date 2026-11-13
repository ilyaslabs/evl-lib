package io.github.ilyaslabs.evl.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The step tree of a flow: its main sequence and its flow-level error handler. */
public record FlowStructure(String flowKey, List<StepNode> steps, List<StepNode> onError) {

    public static FlowStructure empty(String flowKey) {
        return new FlowStructure(flowKey, List.of(), List.of());
    }

    public FlowStructure {
        steps = List.copyOf(steps);
        onError = List.copyOf(onError);
    }

    /** Visits every step depth-first in document order. */
    public void forEach(Consumer<StepNode> visitor) {
        walk(steps, visitor);
        walk(onError, visitor);
    }

    public List<StepNode> all() {
        List<StepNode> out = new ArrayList<>();
        forEach(out::add);
        return out;
    }

    private static void walk(List<StepNode> nodes, Consumer<StepNode> visitor) {
        for (StepNode n : nodes) {
            visitor.accept(n);
            for (StepNode.Block b : n.blocks()) {
                walk(b.steps(), visitor);
            }
        }
    }
}
