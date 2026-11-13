package io.github.ilyaslabs.evl.cli.out;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.GraphExport;
import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Level;
import io.github.ilyaslabs.evl.core.parse.Versions;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders diagnostics as text, JSON ({@code evl-diagnostics.schema.json}) or SARIF 2.1.0. */
public final class Printer {

    /** Output format. */
    public enum Format { TEXT, JSON, SARIF }

    private Printer() {
    }

    public static void diagnostics(EvlResult r, Format format, boolean color, String version, PrintWriter out) {
        switch (format) {
            case JSON -> out.print(Json.write(json(r)));
            case SARIF -> out.print(Json.write(sarif(r.diagnostics(), version)));
            default -> text(r, color, out);
        }
        out.flush();
    }

    static void text(EvlResult r, boolean color, PrintWriter out) {
        for (Diagnostic d : r.diagnostics()) {
            String level = switch (d.level()) {
                case ERROR -> paint("error", "31;1", color);
                case WARNING -> paint("warning", "33;1", color);
                case WEAK_WARNING -> paint("weak warning", "33", color);
                default -> paint("info", "36", color);
            };
            out.println(d.location() + ": " + level + " " + d.code() + ": " + d.message());
        }
        long e = r.count(Level.ERROR);
        long w = r.count(Level.WARNING) + r.count(Level.WEAK_WARNING);
        long i = r.count(Level.INFO);
        String summary = r.graph().elementMap().size() + " elements, " + e + " error(s), " + w + " warning(s), " + i + " info";
        out.println(e > 0 ? paint(summary, "31", color) : paint(summary, "32", color));
    }

    private static String paint(String s, String code, boolean color) {
        return color ? "\u001b[" + code + "m" + s + "\u001b[0m" : s;
    }

    public static Map<String, Object> json(EvlResult r) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evl", Versions.SPEC);
        root.put("format", 1);
        root.put("application", r.graph().appId());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("elements", r.graph().elementMap().size());
        summary.put("errors", r.count(Level.ERROR));
        summary.put("warnings", r.count(Level.WARNING));
        summary.put("weakWarnings", r.count(Level.WEAK_WARNING));
        summary.put("infos", r.count(Level.INFO));
        root.put("summary", summary);
        root.put("diagnostics", GraphExport.diagnostics(r.diagnostics()));
        return root;
    }

    public static Map<String, Object> sarif(List<Diagnostic> diagnostics, String version) {
        Set<String> used = new LinkedHashSet<>();
        diagnostics.forEach(d -> used.add(d.code()));
        List<Object> rules = new ArrayList<>();
        Map<String, Integer> ruleIndex = new LinkedHashMap<>();
        for (Rule rule : Rule.values()) {
            if (!used.contains(rule.code())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rule.code());
            m.put("name", rule.title().replaceAll("[^A-Za-z0-9]", ""));
            m.put("shortDescription", Map.of("text", rule.title()));
            m.put("fullDescription", Map.of("text", rule.description()));
            m.put("helpUri", "https://ilyaslabs.github.io/evl-lib/rules/#" + rule.code().toLowerCase());
            m.put("defaultConfiguration", Map.of("level", level(rule.level())));
            ruleIndex.put(rule.code(), rules.size());
            rules.add(m);
        }
        List<Object> results = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", d.code());
            if (ruleIndex.containsKey(d.code())) {
                m.put("ruleIndex", ruleIndex.get(d.code()));
            }
            m.put("level", level(d.level()));
            m.put("message", Map.of("text", d.message()));
            if (d.location().isKnown()) {
                Map<String, Object> region = new LinkedHashMap<>();
                region.put("startLine", Math.max(1, d.location().line()));
                if (d.location().column() > 0) {
                    region.put("startColumn", d.location().column());
                }
                Map<String, Object> phys = new LinkedHashMap<>();
                phys.put("artifactLocation", Map.of("uri", d.location().file()));
                phys.put("region", region);
                m.put("locations", List.of(Map.of("physicalLocation", phys)));
            }
            results.add(m);
        }
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", "evl");
        driver.put("version", version);
        driver.put("informationUri", "https://ilyaslabs.github.io/evl-lib/");
        driver.put("rules", rules);
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tool", Map.of("driver", driver));
        run.put("results", results);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.put("version", "2.1.0");
        root.put("runs", List.of(run));
        return root;
    }

    private static String level(Level l) {
        return switch (l) {
            case ERROR -> "error";
            case WARNING, WEAK_WARNING -> "warning";
            default -> "note";
        };
    }
}
