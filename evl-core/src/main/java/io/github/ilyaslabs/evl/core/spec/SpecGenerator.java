package io.github.ilyaslabs.evl.core.spec;

import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.parse.Versions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Generates the published machine-readable spec files from the {@link Kind}, {@link StepType} and
 * {@link Rule} tables: JSON Schemas and {@code rules.yaml}. Run with the spec directory as the
 * argument ({@code ./gradlew generateSpec}); a test fails when the checked-in files are stale.
 */
public final class SpecGenerator {

    public static final String BASE = "https://ilyaslabs.github.io/evl-lib/schema/" + Versions.SPEC + "/";
    private static final String DRAFT = "https://json-schema.org/draft/2020-12/schema";

    private SpecGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path spec = Path.of(args.length > 0 ? args[0] : "spec");
        for (Map.Entry<String, String> e : files().entrySet()) {
            Path p = spec.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    /** Relative path → content of every generated file. */
    public static Map<String, String> files() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("schema/evl-document.schema.json", Json.write(document()));
        out.put("schema/evl-manifest.schema.json", Json.write(manifest()));
        out.put("schema/evl-index.schema.json", Json.write(index()));
        out.put("schema/evl-diagnostics.schema.json", Json.write(diagnostics()));
        out.put("schema/evl-view.schema.json", Json.write(view()));
        out.put("rules.yaml", rulesYaml());
        return out;
    }

    // ------------------------------------------------------------------ document

    static Map<String, Object> document() {
        Map<String, Object> s = header("evl-document.schema.json", "EVL document (*.evl.yaml)");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evl", m("type", "string", "pattern", "^\\d+\\.\\d+$", "description", "Spec version, e.g. \"1.0\"."));
        Map<String, Object> defs = new LinkedHashMap<>();
        for (Kind k : Kind.values()) {
            if (k.sectionKey() == null || k == Kind.APPLICATION) {
                continue;
            }
            defs.put(k.keyword(), element(k));
            props.put(k.sectionKey(), m("type", "array", "description", k.displayName() + " declarations.",
                "items", m("$ref", "#/$defs/" + k.keyword())));
        }
        defs.put("step", step());
        defs.put("steps", m("type", "array", "items", m("$ref", "#/$defs/step")));
        defs.put("source", m("description", "Source reference: type[#member[(params)]], package:, config: or file:.",
            "oneOf", List.of(m("type", "string"), m("type", "object", "required", List.of("ref"),
                "properties", m("ref", m("type", "string")), "additionalProperties", false))));
        defs.put("id", m("type", "string", "maxLength", 128, "pattern", "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*$"));
        defs.put("ref", m("type", "string", "description", "Element reference: [app-id:][kind:]id[/local]."));
        s.put("type", "object");
        s.put("properties", props);
        s.put("patternProperties", m("^x-", m()));
        s.put("additionalProperties", false);
        s.put("$defs", defs);
        return s;
    }

    static Map<String, Object> element(Kind k) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (k.requiresId()) {
            required.add("id");
        }
        for (FieldSpec f : k.fields().values()) {
            Map<String, Object> p = field(f);
            if (f.name().equals("id")) {
                p = m("$ref", "#/$defs/id");
            }
            props.put(f.name(), p);
            if (f.required()) {
                required.add(f.name());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("title", k.displayName());
        m.put("required", required);
        m.put("properties", props);
        m.put("patternProperties", m("^x-", m()));
        m.put("additionalProperties", false);
        return m;
    }

    static Map<String, Object> field(FieldSpec f) {
        Map<String, Object> p = new LinkedHashMap<>();
        switch (f.type()) {
            case STRING, TEXT -> p.put("type", "string");
            case INT -> p.put("type", "integer");
            case BOOL -> p.put("type", "boolean");
            case STRING_LIST -> p.put("type", "array");
            case ENUM -> {
                p.put("type", "string");
                p.put("enum", f.values());
            }
            case REF -> p.put("$ref", "#/$defs/ref");
            case REF_LIST -> {
                p.put("type", "array");
                p.put("items", m("$ref", "#/$defs/ref"));
            }
            case SOURCE -> p.put("$ref", "#/$defs/source");
            case OBJECT_LIST -> {
                p.put("type", "array");
                p.put("items", m("type", "object"));
            }
            case STRING_OR_OBJECT -> p.put("type", List.of("string", "object"));
            case STEPS -> p.put("$ref", "#/$defs/steps");
            default -> {
            }
        }
        if (f.type() == FieldType.STRING_LIST) {
            p.put("items", m("type", "string"));
        }
        if (!f.refKinds().isEmpty()) {
            p.put("x-evl-ref-kinds", new TreeSet<>(f.refKinds()));
        }
        p.put("description", f.description());
        return p;
    }

    static Map<String, Object> step() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", m("type", "string", "pattern", "^[a-z][a-z0-9-]*$"));
        List<String> types = new ArrayList<>();
        for (StepType t : StepType.values()) {
            types.add(t.name());
        }
        props.put("type", m("type", "string", "enum", types, "default", "ACTION"));
        for (String f : List.of("name", "description", "doc", "question", "for", "timeout", "reason", "outcome", "target",
            "when", "text", "error")) {
            if (!props.containsKey(f)) {
                props.put(f, m("type", "string"));
            }
        }
        props.put("for", m("type", List.of("string", "array")));
        props.put("source", m("$ref", "#/$defs/source"));
        props.put("component", m("$ref", "#/$defs/ref"));
        props.put("flow", m("$ref", "#/$defs/ref"));
        props.put("event", m("$ref", "#/$defs/ref"));
        props.put("direction", m("type", "string", "enum", List.of("PUBLISH", "CONSUME")));
        props.put("join", m("type", "string", "enum", List.of("ALL", "ANY", "NONE")));
        props.put("async", m("type", "boolean"));
        props.put("inputs", m("type", "object"));
        props.put("outputs", m("type", "object"));
        props.put("policy", m("type", "object", "properties", m("maxAttempts", m("type", "integer"),
            "backoff", m("type", "string"), "retryOn", m("type", "array"))));
        for (String f : List.of("tags", "requirements", "decisions", "realizedBy")) {
            props.put(f, m("type", "array", "items", m("type", "string")));
        }
        for (String f : List.of("concurrency", "performance", "security", "failure")) {
            props.put(f, m("type", "string"));
        }
        props.put("steps", m("$ref", "#/$defs/steps"));
        props.put("otherwise", m("$ref", "#/$defs/steps"));
        props.put("onTimeout", m("$ref", "#/$defs/steps"));
        props.put("branches", m("type", "array", "items", m("type", "object", "properties",
            m("when", m("type", "string"), "name", m("type", "string"), "steps", m("$ref", "#/$defs/steps")))));
        props.put("errors", m("type", "array", "items", m("type", "object", "required", List.of("on"), "properties",
            m("on", m("type", "string"), "then", m("$ref", "#/$defs/steps")))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("title", "Step");
        m.put("required", List.of("id"));
        m.put("properties", props);
        m.put("patternProperties", m("^x-", m()));
        m.put("additionalProperties", false);
        return m;
    }

    // ------------------------------------------------------------------ other schemas

    static Map<String, Object> manifest() {
        Map<String, Object> s = header("evl-manifest.schema.json", "EVL manifest (evl.yaml)");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evl", m("type", "string", "pattern", "^\\d+\\.\\d+$"));
        props.put("id", m("type", "string", "pattern", "^[a-z][a-z0-9]*(-[a-z0-9]+)*$", "maxLength", 128));
        props.put("name", m("type", "string"));
        props.put("description", m("type", "string"));
        props.put("kind", m("type", "string", "enum", List.of("APPLICATION", "LIBRARY")));
        for (String f : List.of("documents", "sources", "exclude", "dependencies", "tags", "see", "requirements", "decisions")) {
            props.put(f, m("type", "array", "items", m("type", "string")));
        }
        props.put("settings", m("type", "object", "properties", m("inferred", m("type", "boolean"))));
        props.put("rules", m("type", "object", "description", "Rule code → level (error, warning, weak_warning, info, off).",
            "additionalProperties", m("type", "string", "enum", List.of("error", "warning", "weak_warning", "info", "off"))));
        props.put("artifact", m("type", List.of("string", "object")));
        for (String f : List.of("domain", "doc", "concurrency", "performance", "security", "failure")) {
            props.put(f, m("type", "string"));
        }
        props.put("source", m("type", List.of("string", "object")));
        s.put("type", "object");
        s.put("required", List.of("evl", "id", "name"));
        s.put("properties", props);
        s.put("patternProperties", m("^x-", m()));
        s.put("additionalProperties", false);
        return s;
    }

    static Map<String, Object> index() {
        Map<String, Object> s = header("evl-index.schema.json", "EVL processor index (META-INF/evl/index.json)");
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", "object");
        rec.put("required", List.of("annotation", "element", "elementKind"));
        Map<String, Object> rp = new LinkedHashMap<>();
        rp.put("annotation", m("type", "string", "description", "Annotation name inside EVL, e.g. Component."));
        rp.put("element", m("type", "string", "description", "Annotated declaration as a source reference."));
        rp.put("elementKind", m("type", "string", "enum", List.of("PACKAGE", "TYPE", "METHOD", "FIELD", "CONSTRUCTOR",
            "PARAMETER")));
        rp.put("package", m("type", "string"));
        rp.put("file", m("type", "string"));
        rp.put("line", m("type", "integer"));
        rp.put("column", m("type", "integer"));
        rp.put("attributes", m("type", "object", "description", "Explicitly set attributes (strings, booleans, string arrays)."));
        rec.put("properties", rp);
        s.put("type", "object");
        s.put("required", List.of("evl", "format", "annotations"));
        s.put("properties", m("evl", m("type", "string"), "format", m("const", 1),
            "generator", m("type", "string"), "annotations", m("type", "array", "items", rec)));
        return s;
    }

    static Map<String, Object> diagnostics() {
        Map<String, Object> s = header("evl-diagnostics.schema.json", "EVL diagnostics (evl validate --format json)");
        Map<String, Object> loc = m("type", "object", "properties", m("file", m("type", "string"),
            "line", m("type", "integer"), "column", m("type", "integer")));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", "object");
        d.put("required", List.of("code", "level", "message", "location"));
        Map<String, Object> dp = new LinkedHashMap<>();
        dp.put("code", m("type", "string", "pattern", "^EVL-[EWI]\\d{3}$"));
        dp.put("level", m("type", "string", "enum", List.of("ERROR", "WARNING", "WEAK_WARNING", "INFO")));
        dp.put("message", m("type", "string"));
        dp.put("location", loc);
        dp.put("element", m("type", "string"));
        dp.put("related", m("type", "array", "items", loc));
        dp.put("fixes", m("type", "array", "items", m("type", "object", "required", List.of("action", "title"),
            "properties", m("action", m("type", "string"), "title", m("type", "string"),
                "args", m("type", "object")))));
        d.put("properties", dp);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evl", m("type", "string"));
        props.put("format", m("const", 1));
        props.put("application", m("type", "string"));
        props.put("summary", m("type", "object"));
        props.put("diagnostics", m("type", "array", "items", d));
        s.put("type", "object");
        s.put("required", List.of("evl", "format", "diagnostics"));
        s.put("properties", props);
        return s;
    }

    static Map<String, Object> view() {
        Map<String, Object> s = header("evl-view.schema.json", "Shared EVL view (.evl/views/*.evl-view.yaml)");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evl", m("type", "string"));
        props.put("name", m("type", "string"));
        props.put("description", m("type", "string"));
        props.put("location", m("type", "object", "required", List.of("root"), "properties", m(
            "root", m("type", "string", "description", "Element reference the view opens on."),
            "path", m("type", "array", "items", m("type", "string"), "description", "Expanded path of element ids."),
            "perspective", m("type", "string", "enum", List.of("FLOW", "ARCHITECTURE", "DATA", "EVENTS"))),
            "additionalProperties", false));
        props.put("pins", m("type", "array", "items", m("type", "string")));
        props.put("positions", m("type", "object", "description", "Element key → {x, y} layout hints.",
            "additionalProperties", m("type", "object", "properties", m("x", m("type", "number"),
                "y", m("type", "number")))));
        s.put("type", "object");
        s.put("required", List.of("name", "location"));
        s.put("properties", props);
        s.put("additionalProperties", false);
        return s;
    }

    /** An insertion-ordered map, so generated files are byte-for-byte stable. */
    static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], kv[i + 1]);
        }
        return out;
    }

    private static Map<String, Object> header(String file, String title) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("$schema", DRAFT);
        s.put("$id", BASE + file);
        s.put("title", title);
        s.put("description", "Generated from the evl-core spec tables. Do not edit by hand; run ./gradlew generateSpec.");
        return s;
    }

    // ------------------------------------------------------------------ rules.yaml

    static String rulesYaml() {
        StringBuilder sb = new StringBuilder();
        sb.append("# EVL ").append(Versions.SPEC).append(" validation rules.\n");
        sb.append("# Generated from io.github.ilyaslabs.evl.core.spec.Rule; run ./gradlew generateSpec after changing it.\n");
        sb.append("# Examples for each rule live in spec/conformance/<code>.yaml.\n");
        sb.append("evl: \"").append(Versions.SPEC).append("\"\nrules:\n");
        for (Rule r : Rule.values()) {
            sb.append("  - code: ").append(r.code()).append('\n');
            sb.append("    level: ").append(r.level().name()).append('\n');
            sb.append("    title: ").append(quote(r.title())).append('\n');
            sb.append("    description: ").append(quote(r.description())).append('\n');
            sb.append("    configurable: ").append(!r.isLocked()).append('\n');
            sb.append("    conformance: conformance/").append(r.code()).append(".yaml\n");
        }
        return sb.toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        Json.string(sb, s);
        return sb.toString();
    }
}
