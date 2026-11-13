package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.spec.Rule;
import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

/**
 * Safe YAML 1.2 reader producing position-preserving {@link YNode} trees. No Java objects are ever
 * constructed from YAML; custom tags, duplicate keys and oversized input are rejected as diagnostics.
 * JSON is a subset of YAML 1.2, so the same reader also loads {@code index.json} files.
 */
public final class YamlReader {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_ALIASES = 10_000;

    private YamlReader() {
    }

    /** Result of reading one file: the root node (if any) and problems found. */
    public record Result(Optional<YNode> root, List<Diagnostic> diagnostics) {
    }

    public static Result read(String file, String content) {
        List<Diagnostic> diags = new ArrayList<>();
        if (content.length() > MAX_BYTES) {
            diags.add(Diagnostics.of(Rule.E105, "Document is larger than 5 MB", Location.of(file)));
            return new Result(Optional.empty(), diags);
        }
        LoadSettings settings = LoadSettings.builder()
            .setLabel(file)
            .setAllowDuplicateKeys(true) // detected ourselves to report all of them with positions
            .setMaxAliasesForCollections(MAX_ALIASES)
            .setAllowRecursiveKeys(false)
            .setCodePointLimit(MAX_BYTES)
            .build();
        try {
            Composer composer = new Composer(settings, new ParserImpl(settings, new StreamReader(settings, content)));
            Optional<Node> node = composer.getSingleNode();
            if (node.isEmpty()) {
                return new Result(Optional.empty(), diags);
            }
            YNode root = convert(file, node.get(), 0, diags);
            return new Result(Optional.ofNullable(root), diags);
        } catch (MarkedYamlEngineException e) {
            Location loc = e.getProblemMark().map(m -> loc(file, m)).orElse(Location.of(file));
            diags.add(Diagnostics.of(Rule.E104, "YAML syntax error: " + firstLine(e.getProblem()), loc));
        } catch (YamlEngineException e) {
            Rule r = e.getMessage() != null && e.getMessage().contains("aliases") ? Rule.E105 : Rule.E104;
            diags.add(Diagnostics.of(r, "YAML error: " + firstLine(e.getMessage()), Location.of(file)));
        } catch (DepthExceeded e) {
            diags.add(Diagnostics.of(Rule.E105, "Document nesting is deeper than " + MAX_DEPTH, e.location));
        }
        return new Result(Optional.empty(), diags);
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "unknown problem";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    private static Location loc(String file, Mark m) {
        return new Location(file, m.getLine() + 1, m.getColumn() + 1);
    }

    private static Location loc(String file, Node n) {
        return n.getStartMark().map(m -> loc(file, m)).orElse(Location.of(file));
    }

    private static final class DepthExceeded extends RuntimeException {
        final Location location;

        DepthExceeded(Location location) {
            super(null, null, false, false);
            this.location = location;
        }
    }

    private static YNode convert(String file, Node node, int depth, List<Diagnostic> diags) {
        Location location = loc(file, node);
        if (depth > MAX_DEPTH) {
            throw new DepthExceeded(location);
        }
        Tag tag = node.getTag();
        if (!isStandardTag(tag)) {
            diags.add(Diagnostics.of(Rule.E100, "YAML tag " + tag.getValue() + " is not allowed", location));
        }
        if (node instanceof ScalarNode s) {
            YNode.ScalarType type;
            if (Tag.BOOL.equals(tag)) {
                type = YNode.ScalarType.BOOL;
            } else if (Tag.INT.equals(tag)) {
                type = YNode.ScalarType.INT;
            } else if (Tag.FLOAT.equals(tag)) {
                type = YNode.ScalarType.FLOAT;
            } else if (Tag.NULL.equals(tag)) {
                type = YNode.ScalarType.NULL;
            } else {
                type = YNode.ScalarType.STRING;
            }
            return new YNode.Scalar(s.getValue(), type, location);
        }
        if (node instanceof SequenceNode seq) {
            List<YNode> items = new ArrayList<>();
            for (Node child : seq.getValue()) {
                items.add(convert(file, child, depth + 1, diags));
            }
            return new YNode.Sequence(items, location);
        }
        MappingNode map = (MappingNode) node;
        Map<String, YNode> entries = new LinkedHashMap<>();
        Map<String, Location> keys = new LinkedHashMap<>();
        for (NodeTuple t : map.getValue()) {
            Location keyLoc = loc(file, t.getKeyNode());
            String key = t.getKeyNode() instanceof ScalarNode ks ? ks.getValue() : null;
            if (key == null) {
                diags.add(Diagnostics.of(Rule.E103, "Mapping keys must be plain strings", keyLoc));
                continue;
            }
            if (entries.containsKey(key)) {
                diags.add(Diagnostics.of(Rule.E101, "Duplicate key '" + key + "'", keyLoc));
                continue;
            }
            entries.put(key, convert(file, t.getValueNode(), depth + 1, diags));
            keys.put(key, keyLoc);
        }
        return new YNode.Mapping(entries, keys, location);
    }

    private static boolean isStandardTag(Tag tag) {
        return tag == null || Tag.STR.equals(tag) || Tag.INT.equals(tag) || Tag.BOOL.equals(tag)
            || Tag.FLOAT.equals(tag) || Tag.NULL.equals(tag) || Tag.SEQ.equals(tag) || Tag.MAP.equals(tag);
    }
}
