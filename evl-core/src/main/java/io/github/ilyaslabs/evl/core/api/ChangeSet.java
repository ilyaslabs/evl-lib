package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A set of changes for {@link EvlWorkspace#update}. A {@code null} content or record list means the
 * file or unit was removed.
 */
public final class ChangeSet {

    private final Map<String, String> documents;
    private final Map<String, List<AnnotationRecord>> annotations;
    private final String manifest;
    private final boolean manifestChanged;

    private ChangeSet(Builder b) {
        this.documents = Collections.unmodifiableMap(new LinkedHashMap<>(b.documents));
        this.annotations = Collections.unmodifiableMap(new LinkedHashMap<>(b.annotations));
        this.manifest = b.manifest;
        this.manifestChanged = b.manifestChanged;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, String> documents() {
        return documents;
    }

    public Map<String, List<AnnotationRecord>> annotations() {
        return annotations;
    }

    /** New manifest content, if it changed ({@code Optional.empty()} inside means removed). */
    public Optional<Optional<String>> manifest() {
        return manifestChanged ? Optional.of(Optional.ofNullable(manifest)) : Optional.empty();
    }

    public boolean isEmpty() {
        return documents.isEmpty() && annotations.isEmpty() && !manifestChanged;
    }

    /** Builder. */
    public static final class Builder {
        private final Map<String, String> documents = new LinkedHashMap<>();
        private final Map<String, List<AnnotationRecord>> annotations = new LinkedHashMap<>();
        private String manifest;
        private boolean manifestChanged;

        public Builder putDocument(String file, String content) {
            documents.put(file, content);
            return this;
        }

        public Builder removeDocument(String file) {
            documents.put(file, null);
            return this;
        }

        /** Replaces all annotation records of one unit (a source file or an index file). */
        public Builder putAnnotations(String unit, List<AnnotationRecord> records) {
            annotations.put(unit, new ArrayList<>(records));
            return this;
        }

        public Builder removeAnnotations(String unit) {
            annotations.put(unit, null);
            return this;
        }

        public Builder manifest(String content) {
            manifest = content;
            manifestChanged = true;
            return this;
        }

        public ChangeSet build() {
            return new ChangeSet(this);
        }
    }
}
