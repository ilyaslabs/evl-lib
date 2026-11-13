package io.github.ilyaslabs.evl.processor;

import java.util.Map;

/** One extracted annotation (same shape as evl-core's AnnotationRecord). */
public final class Record {

    public final String annotation;
    public final String element;
    public final String elementKind;
    public final String packageName;
    public final Map<String, Object> attributes;
    public final String file;
    public final int line;
    public final int column;

    public Record(String annotation, String element, String elementKind, String packageName,
                  Map<String, Object> attributes, String file, int line, int column) {
        this.annotation = annotation;
        this.element = element;
        this.elementKind = elementKind;
        this.packageName = packageName;
        this.attributes = attributes;
        this.file = file;
        this.line = line;
        this.column = column;
    }
}
