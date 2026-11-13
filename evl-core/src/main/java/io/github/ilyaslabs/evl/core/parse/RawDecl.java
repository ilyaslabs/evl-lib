package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.spec.Kind;

/**
 * One declaration of an element before merging: a YAML list item, a manifest, or an annotation
 * mapped to YAML field names.
 *
 * @param kind       element kind
 * @param id         declared or derived id ({@code null} when missing)
 * @param body       fields in YAML shape, with positions
 * @param origin     where it was declared
 * @param derivedId  whether {@code id} was derived from a Java name
 */
public record RawDecl(Kind kind, String id, YNode.Mapping body, Origin origin, boolean derivedId) {
}
