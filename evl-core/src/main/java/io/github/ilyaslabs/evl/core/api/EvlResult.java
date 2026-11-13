package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Level;
import java.util.List;

/** Result of loading or updating a workspace: an immutable graph and sorted diagnostics. */
public record EvlResult(EvlGraph graph, List<Diagnostic> diagnostics) {

    public EvlResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.level() == Level.ERROR);
    }

    public long count(Level level) {
        return diagnostics.stream().filter(d -> d.level() == level).count();
    }

    public List<Diagnostic> diagnosticsFor(String elementKey) {
        return diagnostics.stream().filter(d -> elementKey.equals(d.elementId())).toList();
    }

    public EvlQueries queries() {
        return new EvlQueries(graph);
    }
}
