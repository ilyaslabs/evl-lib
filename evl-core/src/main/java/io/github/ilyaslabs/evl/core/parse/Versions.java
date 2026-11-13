package io.github.ilyaslabs.evl.core.parse;

/** Spec version helpers (spec 3.6). */
public final class Versions {

    /** The spec version implemented by this library. */
    public static final String SPEC = "1.0";
    public static final int MAJOR = 1;
    public static final int MINOR = 0;

    private Versions() {
    }

    public static boolean isValid(String v) {
        return v != null && v.matches("\\d+\\.\\d+");
    }

    public static int major(String v) {
        return isValid(v) ? Integer.parseInt(v.substring(0, v.indexOf('.'))) : -1;
    }

    public static int minor(String v) {
        return isValid(v) ? Integer.parseInt(v.substring(v.indexOf('.') + 1)) : -1;
    }
}
