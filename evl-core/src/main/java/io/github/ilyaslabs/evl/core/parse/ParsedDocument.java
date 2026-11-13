package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import java.util.List;
import java.util.Map;

/**
 * A parsed {@code *.evl.yaml} document.
 *
 * @param file         file path as given
 * @param version      declared {@code evl:} version or {@code null}
 * @param versionAt    location of the version value
 * @param decls        declarations in document order
 * @param extensions   top-level {@code x-} keys
 * @param diagnostics  syntax and schema problems
 */
public record ParsedDocument(String file, String version, Location versionAt, List<RawDecl> decls,
                             Map<String, Object> extensions, List<Diagnostic> diagnostics) {

    public ParsedDocument {
        decls = List.copyOf(decls);
        extensions = Map.copyOf(extensions);
        diagnostics = List.copyOf(diagnostics);
    }
}
