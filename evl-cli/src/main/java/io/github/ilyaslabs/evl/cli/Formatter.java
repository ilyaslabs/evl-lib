package io.github.ilyaslabs.evl.cli;

import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.StepType;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

/**
 * Canonical formatting of {@code .evl.yaml} files ({@code evl fmt}): stable key order and 2-space
 * indentation. Comments, scalar styles and inline (flow) collections are kept.
 */
public final class Formatter {

    private static final List<String> STRUCTURAL = List.of("branches", "otherwise", "steps", "onTimeout", "errors", "then");

    private Formatter() {
    }

    /** Returns the formatted text, or empty when the input is not valid YAML. */
    public static Optional<String> format(String content) {
        LoadSettings ls = LoadSettings.builder().setParseComments(true).setAllowDuplicateKeys(false).build();
        Optional<Node> root;
        try {
            root = new Compose(ls).composeString(content);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (root.isEmpty()) {
            return Optional.of(content);
        }
        Node n = root.get();
        if (n instanceof MappingNode m) {
            sortTop(m);
        }
        DumpSettings ds = DumpSettings.builder().setDumpComments(true).setIndent(2).setIndicatorIndent(2)
            .setIndentWithIndicator(true).setDefaultFlowStyle(FlowStyle.BLOCK).setWidth(120).setSplitLines(false).build();
        StringWriter sw = new StringWriter();
        new Dump(ds).dumpNode(n, new StreamDataWriter() {
            @Override
            public void write(String str) {
                sw.write(str);
            }

            @Override
            public void write(String str, int off, int len) {
                sw.write(str, off, len);
            }
        });
        return Optional.of(sw.toString());
    }

    private static void sortTop(MappingNode m) {
        List<String> order = new ArrayList<>(List.of("evl"));
        for (Kind k : Kind.values()) {
            if (k.sectionKey() != null) {
                order.add(k.sectionKey());
            }
        }
        sort(m, order);
        for (NodeTuple t : m.getValue()) {
            String key = key(t);
            Optional<Kind> kind = key == null ? Optional.empty() : Kind.bySection(key);
            if (kind.isPresent() && t.getValueNode() instanceof SequenceNode seq) {
                for (Node item : seq.getValue()) {
                    if (item instanceof MappingNode em) {
                        element(kind.get(), em);
                    }
                }
            }
        }
    }

    private static void element(Kind kind, MappingNode m) {
        List<String> order = new ArrayList<>(List.of("id", "name"));
        order.addAll(kind.fields().keySet());
        sort(m, order);
        if (kind == Kind.FLOW) {
            for (NodeTuple t : m.getValue()) {
                if (("steps".equals(key(t)) || "onError".equals(key(t))) && t.getValueNode() instanceof SequenceNode seq) {
                    steps(seq);
                }
            }
        }
    }

    private static void steps(SequenceNode seq) {
        for (Node n : seq.getValue()) {
            if (!(n instanceof MappingNode m)) {
                continue;
            }
            List<String> order = new ArrayList<>(List.of("id", "type", "name", "description"));
            StepType type = null;
            for (NodeTuple t : m.getValue()) {
                if ("type".equals(key(t)) && t.getValueNode() instanceof ScalarNode s) {
                    type = StepType.parse(s.getValue()).orElse(null);
                }
            }
            if (type != null) {
                type.specificFields().stream().filter(f -> !STRUCTURAL.contains(f)).sorted().forEach(order::add);
            }
            order.addAll(List.of("source", "realizedBy", "requirements", "decisions", "tags", "doc"));
            order.addAll(STRUCTURAL);
            sort(m, order);
            for (NodeTuple t : m.getValue()) {
                nested(t.getValueNode());
            }
        }
    }

    /** Recurses into branch/error/body sequences to sort nested steps. */
    private static void nested(Node value) {
        if (!(value instanceof SequenceNode seq)) {
            return;
        }
        boolean stepList = seq.getValue().stream().anyMatch(i -> i instanceof MappingNode im
            && im.getValue().stream().anyMatch(t -> "id".equals(key(t))));
        if (stepList) {
            steps(seq);
            return;
        }
        for (Node i : seq.getValue()) {
            if (i instanceof MappingNode im) {
                for (NodeTuple t : im.getValue()) {
                    if ("steps".equals(key(t)) || "then".equals(key(t))) {
                        nested(t.getValueNode());
                    }
                }
            }
        }
    }

    private static void sort(MappingNode m, List<String> order) {
        List<NodeTuple> tuples = new ArrayList<>(m.getValue());
        tuples.sort(Comparator.comparingInt((NodeTuple t) -> rank(key(t), order)));
        m.setValue(tuples);
    }

    private static int rank(String key, List<String> order) {
        if (key == null) {
            return Integer.MAX_VALUE;
        }
        int i = order.indexOf(key);
        if (i >= 0) {
            return i;
        }
        return key.startsWith("x-") ? 10_000 : 5_000;
    }

    private static String key(NodeTuple t) {
        return t.getKeyNode() instanceof ScalarNode s ? s.getValue() : null;
    }
}
