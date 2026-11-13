package io.github.ilyaslabs.evl.core.model;

import io.github.ilyaslabs.evl.core.spec.StepType;
import java.util.List;

/**
 * Structure of one step inside a block-structured flow. Nested sequences (decision branches,
 * parallel branches, retry bodies, error handlers, ...) are {@link Block}s.
 *
 * @param key  graph key of the step element ({@code flowId/stepId})
 */
public record StepNode(String key, StepType type, List<Block> blocks) {

    public StepNode {
        blocks = List.copyOf(blocks);
    }

    /** Role of a nested block. */
    public enum Role { BRANCH, OTHERWISE, BODY, ON_TIMEOUT, ERROR }

    /** A nested step sequence with an optional label (branch condition, error name, ...). */
    public record Block(Role role, String label, List<StepNode> steps) {
        public Block {
            steps = List.copyOf(steps);
        }
    }
}
