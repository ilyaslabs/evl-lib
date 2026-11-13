package io.github.ilyaslabs.evl.core.model;

/**
 * Where a declaration (or one field of it) was written. For annotations, {@code javaElement} is the
 * annotated declaration as a source reference string (for example {@code com.example.Foo#bar(String)}).
 */
public record Origin(OriginKind kind, Location location, String javaElement, String app) {

    public static Origin document(Location location) {
        return new Origin(OriginKind.DOCUMENT, location, null, null);
    }

    public static Origin annotation(Location location, String javaElement) {
        return new Origin(OriginKind.ANNOTATION, location, javaElement, null);
    }

    public Origin inLibrary(String libraryApp) {
        return new Origin(kind, location, javaElement, libraryApp);
    }

    public boolean isLibrary() {
        return app != null;
    }
}
