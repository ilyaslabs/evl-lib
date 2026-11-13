package io.github.ilyaslabs.evl.cli.code;

import io.github.ilyaslabs.evl.core.api.SourceResolver;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.SourceRef;
import io.github.ilyaslabs.evl.core.model.SourceResolution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Resolves source references against a {@link CodeIndex}. References that the index cannot see
 * (Kotlin sources without classes, types from unknown jars) are UNCHECKED, never errors.
 */
public final class CodeResolver implements SourceResolver {

    private final CodeIndex index;
    private final Set<String> configKeys;

    public CodeResolver(CodeIndex index, Set<String> configKeys) {
        this.index = index;
        this.configKeys = configKeys;
    }

    @Override
    public SourceResolution resolve(SourceRef ref) {
        switch (ref.form()) {
            case PACKAGE:
                return index.packages.contains(ref.type()) ? SourceResolution.resolved(Location.UNKNOWN)
                    : unknown("package " + ref.type() + " not found");
            case CONFIG:
                if (configKeys == null) {
                    return SourceResolution.UNCHECKED;
                }
                return configKeys.contains(ref.type()) ? SourceResolution.resolved(Location.UNKNOWN)
                    : SourceResolution.unresolved("config key not found");
            case FILE:
                return SourceResolution.UNCHECKED;
            default:
                break;
        }
        CodeIndex.TypeInfo t = index.type(ref.type());
        if (t == null) {
            return unknown("type " + ref.type() + " not found");
        }
        Location typeLoc = new Location(t.file, t.line, 0);
        if (ref.form() == SourceRef.Form.TYPE) {
            return SourceResolution.resolved(typeLoc);
        }
        String member = ref.member();
        List<CodeIndex.Method> named = new ArrayList<>();
        for (CodeIndex.Method m : t.methods) {
            if (m.name().equals(member)) {
                named.add(m);
            }
        }
        if (ref.form() == SourceRef.Form.MEMBER) {
            if (t.fields.containsKey(member) && named.isEmpty()) {
                return SourceResolution.resolved(new Location(t.file, t.fields.get(member), 0));
            }
            if (named.size() == 1) {
                return SourceResolution.resolved(new Location(t.file, named.get(0).line(), 0));
            }
            if (named.size() > 1) {
                List<String> c = new ArrayList<>();
                for (CodeIndex.Method m : named) {
                    c.add(ref.type() + "#" + m.name() + "(" + String.join(",", m.params()) + ")");
                }
                return SourceResolution.ambiguous(c);
            }
            if (t.fields.containsKey(member)) {
                return SourceResolution.resolved(new Location(t.file, t.fields.get(member), 0));
            }
            return SourceResolution.unresolved("no member '" + member + "' in " + ref.type());
        }
        for (CodeIndex.Method m : named) {
            if (sameParams(m.params(), ref.params())) {
                return SourceResolution.resolved(new Location(t.file, m.line(), 0));
            }
        }
        return SourceResolution.unresolved("no method " + member + "(" + String.join(",", ref.params()) + ") in " + ref.type());
    }

    private SourceResolution unknown(String reason) {
        if (index.hasKotlinSources || index.isEmpty()) {
            return SourceResolution.UNCHECKED;
        }
        return SourceResolution.unresolved(reason);
    }

    private static boolean sameParams(List<String> actual, List<String> wanted) {
        if (actual.size() != wanted.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            String w = wanted.get(i);
            int dot = w.lastIndexOf('.', w.endsWith("...") ? w.length() - 4 : w.length());
            String ws = dot < 0 ? w : w.substring(dot + 1);
            if (!actual.get(i).equals(ws)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Optional<Boolean> references(SourceRef from, SourceRef to) {
        if (from.form() != SourceRef.Form.TYPE || to.form() != SourceRef.Form.TYPE) {
            return Optional.empty();
        }
        CodeIndex.TypeInfo f = index.type(from.type());
        if (f == null || index.type(to.type()) == null) {
            return Optional.empty();
        }
        String qn = to.type().replace('$', '.');
        return Optional.of(f.references.contains(qn) || f.references.contains(to.simpleTypeName()));
    }

    /** Keys from application*.properties / *.yml files under the given resource roots. */
    public static Set<String> configKeys(List<Path> roots) {
        Set<String> keys = new java.util.TreeSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(root, 3)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    String n = p.getFileName().toString();
                    if (!n.startsWith("application") && !n.startsWith("bootstrap")) {
                        continue;
                    }
                    if (n.endsWith(".properties")) {
                        Properties props = new Properties();
                        try (var in = Files.newInputStream(p)) {
                            props.load(in);
                        }
                        props.stringPropertyNames().forEach(keys::add);
                    } else if (n.endsWith(".yml") || n.endsWith(".yaml")) {
                        yamlKeys(Files.readString(p), keys);
                    }
                }
            } catch (java.io.IOException | RuntimeException ignored) {
                // unreadable config files are skipped
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static void yamlKeys(String content, Set<String> keys) {
        var load = new org.snakeyaml.engine.v2.api.Load(org.snakeyaml.engine.v2.api.LoadSettings.builder().build());
        for (Object doc : load.loadAllFromString(content)) {
            if (doc instanceof java.util.Map<?, ?> m) {
                flatten("", (java.util.Map<Object, Object>) m, keys);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, java.util.Map<Object, Object> m, Set<String> keys) {
        m.forEach((k, v) -> {
            String key = prefix.isEmpty() ? String.valueOf(k) : prefix + "." + k;
            if (v instanceof java.util.Map<?, ?> inner) {
                flatten(key, (java.util.Map<Object, Object>) inner, keys);
            } else {
                keys.add(key);
            }
        });
    }
}
