package io.github.ilyaslabs.evl.core.spec;

import java.util.List;
import java.util.Set;

/**
 * One allowed field of an element kind. For {@link FieldType#REF} and {@link FieldType#REF_LIST},
 * {@code refKinds} lists the allowed target kind keywords (empty = any kind). For
 * {@link FieldType#ENUM}, {@code values} lists the allowed values.
 */
public record FieldSpec(String name, FieldType type, boolean required, Set<String> refKinds, List<String> values,
                        String description) {

    public static FieldSpec str(String name, String description) {
        return new FieldSpec(name, FieldType.STRING, false, Set.of(), List.of(), description);
    }

    public static FieldSpec text(String name, String description) {
        return new FieldSpec(name, FieldType.TEXT, false, Set.of(), List.of(), description);
    }

    public static FieldSpec bool(String name, String description) {
        return new FieldSpec(name, FieldType.BOOL, false, Set.of(), List.of(), description);
    }

    public static FieldSpec list(String name, String description) {
        return new FieldSpec(name, FieldType.STRING_LIST, false, Set.of(), List.of(), description);
    }

    public static FieldSpec enumeration(String name, String description, String... values) {
        return new FieldSpec(name, FieldType.ENUM, false, Set.of(), List.of(values), description);
    }

    public static FieldSpec ref(String name, String description, String... kinds) {
        return new FieldSpec(name, FieldType.REF, false, Set.of(kinds), List.of(), description);
    }

    public static FieldSpec refs(String name, String description, String... kinds) {
        return new FieldSpec(name, FieldType.REF_LIST, false, Set.of(kinds), List.of(), description);
    }

    public static FieldSpec source(String name, String description) {
        return new FieldSpec(name, FieldType.SOURCE, false, Set.of(), List.of(), description);
    }

    public static FieldSpec objects(String name, String description) {
        return new FieldSpec(name, FieldType.OBJECT_LIST, false, Set.of(), List.of(), description);
    }

    public static FieldSpec strOrObject(String name, String description) {
        return new FieldSpec(name, FieldType.STRING_OR_OBJECT, false, Set.of(), List.of(), description);
    }

    public static FieldSpec steps(String name, String description) {
        return new FieldSpec(name, FieldType.STEPS, false, Set.of(), List.of(), description);
    }

    public FieldSpec mandatory() {
        return new FieldSpec(name, type, true, refKinds, values, description);
    }

    public boolean isReference() {
        return type == FieldType.REF || type == FieldType.REF_LIST;
    }
}
