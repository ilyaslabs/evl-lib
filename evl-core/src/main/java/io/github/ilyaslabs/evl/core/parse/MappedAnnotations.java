package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import java.util.List;
import java.util.Map;

/**
 * Annotation records mapped to EVL declarations.
 *
 * @param decls          element declarations
 * @param steps          {@code @EVL.Step} links
 * @param calls          {@code @EVL.Call} edges
 * @param contributions  values to append to list fields of elements declared elsewhere (producers, consumers)
 * @param diagnostics    local problems (derived ids, orphan docs, missing owners)
 */
public record MappedAnnotations(List<RawDecl> decls, List<Step> steps, List<Call> calls,
                                List<Contribution> contributions, List<Diagnostic> diagnostics) {

    public static final MappedAnnotations EMPTY = new MappedAnnotations(List.of(), List.of(), List.of(), List.of(), List.of());

    public MappedAnnotations {
        decls = List.copyOf(decls);
        steps = List.copyOf(steps);
        calls = List.copyOf(calls);
        contributions = List.copyOf(contributions);
        diagnostics = List.copyOf(diagnostics);
    }

    /** A step declared by {@code @EVL.Step} on a method. */
    public record Step(String flow, String step, String type, String name, String description, String source,
                       Location location, int order) {
    }

    /** A call declared by {@code @EVL.Call}; {@code from} is the id of the calling element. */
    public record Call(String from, String target, String kind, String reason, Location location) {
    }

    /** Appends {@code value} to list field {@code field} of element {@code target} (a reference). */
    public record Contribution(String target, String field, Object value, Location location, String javaElement) {
    }

    /** Convenience for tests: fields of the declaration with the given id. */
    public Map<String, Object> fieldsOf(String id) {
        for (RawDecl d : decls) {
            if (id.equals(d.id())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) d.body().toPlain();
                return m;
            }
        }
        return Map.of();
    }
}
