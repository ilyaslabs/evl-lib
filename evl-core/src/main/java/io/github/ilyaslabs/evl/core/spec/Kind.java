package io.github.ilyaslabs.evl.core.spec;

import static io.github.ilyaslabs.evl.core.spec.FieldSpec.bool;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.enumeration;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.list;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.objects;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.ref;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.refs;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.source;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.steps;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.str;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.strOrObject;
import static io.github.ilyaslabs.evl.core.spec.FieldSpec.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every EVL element kind with its document section key and allowed fields. This table is the single
 * source of truth for document validation, the published JSON Schema and the agent reference docs.
 */
public enum Kind {
    APPLICATION("application", "application", "Application",
        str("domain", "Business domain.")),
    MODULE("module", "modules", "Module",
        str("domain", "Business domain."),
        list("responsibilities", "What the module is responsible for."),
        str("owner", "Owning team or person."),
        ref("parent", "Parent module.", "module"),
        list("packages", "Java package globs the module covers, e.g. com.example.order.**")),
    COMPONENT("component", "components", "Component",
        ref("module", "Owning module.", "module"),
        list("responsibilities", "What the component is responsible for."),
        enumeration("layer", "Architectural layer.", "PRESENTATION", "APPLICATION", "DOMAIN", "INFRASTRUCTURE",
            "INTEGRATION", "CUSTOM")),
    DEPENDENCY("dependency", "dependencies", "Dependency",
        ref("from", "Depending element.").mandatory(),
        ref("to", "Element depended on.").mandatory(),
        enumeration("type", "Dependency type.", "DIRECT", "API", "EVENT", "DATABASE", "CACHE", "EXTERNAL", "ASYNC",
            "CONFIG", "CUSTOM"),
        str("customType", "Type name when type is CUSTOM."),
        text("reason", "Why the dependency exists."),
        enumeration("direction", "Direction relative to from.", "OUTBOUND", "INBOUND", "BIDIRECTIONAL"),
        bool("optional", "Whether the dependency is optional.")),
    INTERFACE("interface", "interfaces", "Interface",
        ref("module", "Owning module.", "module"),
        list("responsibilities", "Contract responsibilities."),
        enumeration("stability", "Contract stability.", "STABLE", "EVOLVING", "EXPERIMENTAL", "DEPRECATED")),
    IMPLEMENTATION("implementation", "implementations", "Implementation",
        ref("of", "Implemented interface.", "interface").mandatory(),
        str("variant", "Variant name, e.g. Stripe.")),
    ENDPOINT("endpoint", "endpoints", "Endpoint",
        enumeration("protocol", "Protocol.", "HTTP", "GRPC", "GRAPHQL", "WEBSOCKET", "MESSAGING", "RMI", "CLI",
            "CUSTOM"),
        str("method", "HTTP method or operation verb."),
        str("path", "Path or operation name."),
        ref("request", "Request contract.", "request", "data-model"),
        ref("response", "Response contract.", "response", "data-model"),
        refs("errors", "Error responses.", "error"),
        text("auth", "Authentication requirements."),
        ref("flow", "Related flow.", "flow"),
        ref("component", "Owning component.", "component")),
    REQUEST("request", "requests", "Request",
        refs("endpoint", "Endpoints using this request.", "endpoint"),
        text("validation", "Validation rules."),
        objects("fields", "Documented fields.")),
    RESPONSE("response", "responses", "Response",
        refs("endpoint", "Endpoints returning this response.", "endpoint"),
        objects("fields", "Documented fields.")),
    ERROR("error", "errors", "Error response",
        str("code", "Error code, e.g. 409 or NOT_FOUND."),
        text("condition", "When the error happens."),
        bool("retryable", "Whether the caller may retry.")),
    DATA_MODEL("data-model", "dataModels", "Data model",
        ref("module", "Owning module.", "module"),
        ref("owner", "Owning module or component.", "module", "component"),
        str("storage", "Storage location."),
        strOrObject("lifecycle", "Lifecycle description or state machine."),
        str("retention", "Retention policy."),
        bool("pii", "Holds personally identifiable information."),
        objects("fields", "Documented fields."),
        objects("relationships", "Relationships to other data models."),
        objects("indexes", "Important indexes.")),
    REPOSITORY("repository", "repositories", "Repository",
        refs("models", "Data models persisted.", "data-model"),
        str("storage", "Storage location.")),
    EVENT("event", "events", "Event",
        str("channel", "Topic or queue name."),
        enumeration("delivery", "Delivery guarantee.", "AT_MOST_ONCE", "AT_LEAST_ONCE", "EXACTLY_ONCE"),
        text("ordering", "Ordering expectations."),
        str("schema", "Payload schema."),
        ref("broker", "Broker.", "external-system", "infrastructure"),
        refs("producers", "Producing elements."),
        objects("consumers", "Consumers: ref, retry, onFailure, idempotent, flow.")),
    EXTERNAL_SYSTEM("external-system", "externalSystems", "External system",
        enumeration("systemType", "System type.", "API", "DATABASE", "BROKER", "STORAGE", "SEARCH", "IDENTITY",
            "PAYMENT", "EMAIL", "CUSTOM"),
        str("url", "Documentation URL (never credentials)."),
        str("owner", "Owner.")),
    CACHE("cache", "caches", "Cache",
        str("store", "Cache store."),
        str("ttl", "Time to live."),
        str("key", "Key structure."),
        text("invalidation", "Invalidation strategy.")),
    INFRASTRUCTURE("infrastructure", "infrastructure", "Infrastructure",
        str("resourceType", "Resource type, e.g. S3 bucket."),
        list("environments", "Environments.")),
    CONFIGURATION("configuration", "configurations", "Configuration",
        str("prefix", "Property prefix."),
        ref("component", "Owning component.", "component"),
        objects("properties", "Config properties in this group.")),
    CONFIG_PROPERTY("config-property", "configProperties", "Config property",
        str("key", "Property key.").mandatory(),
        str("defaultValue", "Default value (never for sensitive properties)."),
        bool("required", "Whether the property is required."),
        bool("sensitive", "Whether the value is a secret."),
        list("environments", "Environments."),
        refs("affects", "Affected elements.")),
    FLOW("flow", "flows", "Flow",
        enumeration("category", "Workflow category.", "BUSINESS", "APPLICATION", "INTEGRATION", "BACKGROUND",
            "SCHEDULED", "TECHNICAL", "CUSTOM"),
        str("domain", "Business domain."),
        ref("module", "Owning module.", "module"),
        refs("actors", "Actors.", "actor"),
        text("trigger", "What starts the flow."),
        source("entryPoint", "Entry point in code."),
        list("preconditions", "Preconditions."),
        list("postconditions", "Postconditions."),
        objects("inputs", "Inputs: name, type, description, required."),
        objects("outputs", "Outputs: name, type, description."),
        objects("context", "Context values: name, description."),
        refs("implementedBy", "Technical flows implementing this flow.", "flow"),
        steps("steps", "Step sequence."),
        steps("onError", "Flow-level error handling steps.")),
    STEP("step", null, "Step"),
    DECISION("decision", "decisions", "Decision",
        enumeration("status", "Decision status.", "PROPOSED", "ACCEPTED", "SUPERSEDED", "DEPRECATED"),
        text("context", "Context."),
        text("decision", "The decision."),
        text("reasoning", "Reasoning."),
        list("alternatives", "Alternatives considered."),
        text("consequences", "Consequences and tradeoffs."),
        ref("supersedes", "Superseded decision.", "decision"),
        str("date", "ISO date."),
        refs("relates", "Related elements.")),
    REQUIREMENT("requirement", "requirements", "Requirement",
        enumeration("kind", "Requirement kind.", "BUSINESS", "FUNCTIONAL", "NON_FUNCTIONAL", "SECURITY",
            "COMPLIANCE"),
        text("statement", "Requirement statement."),
        str("ticket", "Ticket URL or text."),
        enumeration("priority", "Priority.", "MUST", "SHOULD", "COULD"),
        refs("relates", "Related elements.")),
    ACTOR("actor", "actors", "Actor",
        enumeration("actorType", "Actor type.", "HUMAN", "SYSTEM", "SCHEDULER"));

    /** Fields every element kind accepts. */
    public static final List<FieldSpec> COMMON = List.copyOf(COMMON_INIT());

    private final String keyword;
    private final String sectionKey;
    private final String displayName;
    private final Map<String, FieldSpec> fields;

    Kind(String keyword, String sectionKey, String displayName, FieldSpec... specific) {
        this.keyword = keyword;
        this.sectionKey = sectionKey;
        this.displayName = displayName;
        Map<String, FieldSpec> map = new LinkedHashMap<>();
        for (FieldSpec f : COMMON_INIT()) {
            map.put(f.name(), f);
        }
        for (FieldSpec f : specific) {
            map.put(f.name(), f);
        }
        this.fields = Collections.unmodifiableMap(map);
    }

    // Enum constants are initialised before static fields, so COMMON cannot be used in the constructor.
    private static List<FieldSpec> COMMON_INIT() {
        List<FieldSpec> l = new ArrayList<>();
        l.add(str("id", "Stable identifier (dotted lowercase)."));
        l.add(str("name", "Display name."));
        l.add(text("description", "Markdown description."));
        l.add(list("tags", "Tags."));
        l.add(text("doc", "Longer Markdown documentation."));
        l.add(list("see", "Links (URLs or evl: references)."));
        l.add(refs("requirements", "Related requirements.", "requirement"));
        l.add(refs("decisions", "Related decisions.", "decision"));
        l.add(source("source", "Where the element lives in code."));
        l.add(text("concurrency", "Concurrency considerations."));
        l.add(text("performance", "Performance considerations."));
        l.add(text("security", "Security considerations."));
        l.add(text("failure", "Failure scenarios."));
        return l;
    }

    /** Lowercase keyword used in references, e.g. {@code flow:order.placement}. */
    public String keyword() {
        return keyword;
    }

    /** Top-level document key, e.g. {@code flows}; {@code null} for kinds not declared at top level. */
    public String sectionKey() {
        return sectionKey;
    }

    public String displayName() {
        return displayName;
    }

    public Map<String, FieldSpec> fields() {
        return fields;
    }

    public Optional<FieldSpec> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    /** Whether elements of this kind must have an id in documents. */
    public boolean requiresId() {
        return this != DEPENDENCY;
    }

    public static Optional<Kind> byKeyword(String keyword) {
        for (Kind k : values()) {
            if (k.keyword.equals(keyword)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }

    public static Optional<Kind> bySection(String section) {
        for (Kind k : values()) {
            if (section.equals(k.sectionKey)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
