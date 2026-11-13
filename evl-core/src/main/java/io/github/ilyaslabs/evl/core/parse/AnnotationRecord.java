package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Location;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One {@code @EVL.*} annotation as extracted from code by the annotation processor, KSP, the CLI's
 * source scanner or the IDE. Only attributes that were explicitly set to a non-default value are
 * present. Values are String, Boolean, List of String, or (for class literals) the qualified class
 * name as a String.
 *
 * @param annotation   simple annotation name inside {@code EVL}, e.g. {@code Component}
 * @param element      the annotated declaration as a source reference, e.g. {@code com.x.Foo#bar(String)}
 *                     or {@code package:com.x}
 * @param elementKind  PACKAGE, TYPE, METHOD, FIELD, CONSTRUCTOR or PARAMETER
 * @param packageName  package of the declaration
 * @param attributes   explicitly set attributes
 * @param location     file and line of the annotation
 */
public record AnnotationRecord(String annotation, String element, ElementKind elementKind, String packageName,
                               Map<String, Object> attributes, Location location) {

    /** Kind of the annotated Java declaration. */
    public enum ElementKind { PACKAGE, TYPE, METHOD, FIELD, CONSTRUCTOR, PARAMETER }

    public AnnotationRecord {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        packageName = packageName == null ? "" : packageName;
        location = location == null ? Location.UNKNOWN : location;
    }

    public String string(String attribute) {
        Object v = attributes.get(attribute);
        return v instanceof String s && !s.isEmpty() ? s : null;
    }

    /** Simple name of the annotated declaration (type, member or package last segment). */
    public String simpleName() {
        String e = element;
        if (e.startsWith("package:")) {
            e = e.substring(8);
            return e.substring(e.lastIndexOf('.') + 1);
        }
        int hash = e.indexOf('#');
        if (hash >= 0) {
            String m = e.substring(hash + 1);
            int p = m.indexOf('(');
            return p >= 0 ? m.substring(0, p) : m;
        }
        int dot = Math.max(e.lastIndexOf('.'), e.lastIndexOf('$'));
        return e.substring(dot + 1);
    }

    /** The type part of the element ({@code null} for packages). */
    public String typeName() {
        if (element.startsWith("package:")) {
            return null;
        }
        int hash = element.indexOf('#');
        return hash >= 0 ? element.substring(0, hash) : element;
    }
}
