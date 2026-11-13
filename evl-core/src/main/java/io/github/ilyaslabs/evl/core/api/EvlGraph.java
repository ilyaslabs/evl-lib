package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.ElementRef;
import io.github.ilyaslabs.evl.core.model.FlowStructure;
import io.github.ilyaslabs.evl.core.model.Level;
import io.github.ilyaslabs.evl.core.spec.Kind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The canonical EVL graph: an immutable, thread-safe snapshot (EVL-CORE-002). Element keys are
 * ids for the current application and {@code app:id} for library elements; step keys are
 * {@code flowKey/stepId}.
 */
public final class EvlGraph {

    private final String appId;
    private final String appName;
    private final List<LibraryInfo> libraries;
    private final Map<String, Element> elements;
    private final List<Edge> edges;
    private final Map<String, List<Edge>> outgoing;
    private final Map<String, List<Edge>> incoming;
    private final Map<String, FlowStructure> flows;
    private final Map<String, Level> validation;

    public EvlGraph(String appId, String appName, List<LibraryInfo> libraries, Map<String, Element> elements,
                    List<Edge> edges, Map<String, FlowStructure> flows, Map<String, Level> validation) {
        this.appId = appId;
        this.appName = appName;
        this.libraries = List.copyOf(libraries);
        this.elements = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        this.edges = List.copyOf(edges);
        this.flows = Collections.unmodifiableMap(new LinkedHashMap<>(flows));
        this.validation = Map.copyOf(validation);
        Map<String, List<Edge>> out = new HashMap<>();
        Map<String, List<Edge>> in = new HashMap<>();
        for (Edge e : this.edges) {
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
            in.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e);
        }
        out.replaceAll((k, v) -> List.copyOf(v));
        in.replaceAll((k, v) -> List.copyOf(v));
        this.outgoing = Map.copyOf(out);
        this.incoming = Map.copyOf(in);
    }

    public static EvlGraph empty(String appId) {
        return new EvlGraph(appId, appId, List.of(), Map.of(), List.of(), Map.of(), Map.of());
    }

    public String appId() {
        return appId;
    }

    public String appName() {
        return appName;
    }

    public List<LibraryInfo> libraries() {
        return libraries;
    }

    public Collection<Element> elements() {
        return elements.values();
    }

    public Map<String, Element> elementMap() {
        return elements;
    }

    public Optional<Element> element(String key) {
        return Optional.ofNullable(elements.get(key));
    }

    public List<Element> elementsOf(Kind kind) {
        return elements.values().stream().filter(e -> e.kind() == kind).toList();
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Edge> outgoing(String key) {
        return outgoing.getOrDefault(key, List.of());
    }

    public List<Edge> incoming(String key) {
        return incoming.getOrDefault(key, List.of());
    }

    public List<Edge> outgoing(String key, EdgeType type) {
        return outgoing(key).stream().filter(e -> e.type() == type).toList();
    }

    public List<Edge> incoming(String key, EdgeType type) {
        return incoming(key).stream().filter(e -> e.type() == type).toList();
    }

    public Optional<FlowStructure> flow(String flowKey) {
        return Optional.ofNullable(flows.get(flowKey));
    }

    public Map<String, FlowStructure> flows() {
        return flows;
    }

    /** Worst diagnostic level of an element, if it has any diagnostics. */
    public Optional<Level> validation(String key) {
        return Optional.ofNullable(validation.get(key));
    }

    /** The key of the application element. */
    public String applicationKey() {
        return appId;
    }

    /** Looks an element up by reference text ({@code flow:order.placement}, {@code lib:id}, ...). */
    public Optional<Element> resolve(String reference) {
        return ElementRef.parse(reference).flatMap(r -> {
            String key = r.app() == null || r.app().equals(appId)
                ? (r.local() == null ? r.id() : r.id() + "/" + r.local()) : r.key();
            Element e = elements.get(key);
            if (e == null || (r.kind() != null && r.kind() != e.kind())) {
                return Optional.empty();
            }
            return Optional.of(e);
        });
    }

    /** The reference text for an element as written from the current application. */
    public String referenceOf(Element e) {
        return e.key();
    }
}
