package io.github.ilyaslabs.evl.core.model;

/**
 * A position in a file. {@code file} is a path or URI string as given by the producer; lines and
 * columns are 1-based, 0 when unknown.
 */
public record Location(String file, int line, int column) {

    public static final Location UNKNOWN = new Location("", 0, 0);

    public static Location of(String file) {
        return new Location(file, 0, 0);
    }

    public boolean isKnown() {
        return file != null && !file.isEmpty();
    }

    @Override
    public String toString() {
        if (!isKnown()) {
            return "<unknown>";
        }
        return line > 0 ? file + ":" + line + (column > 0 ? ":" + column : "") : file;
    }
}
