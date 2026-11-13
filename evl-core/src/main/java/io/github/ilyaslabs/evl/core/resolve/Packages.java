package io.github.ilyaslabs.evl.core.resolve;

import io.github.ilyaslabs.evl.core.model.SourceRef;

/** Package helpers for module {@code packages} globs. */
public final class Packages {

    private Packages() {
    }

    /** Package of a code source reference, or {@code null}. Assumes types start with an upper-case letter. */
    public static String packageOf(SourceRef ref) {
        if (ref == null || !ref.isCode()) {
            return null;
        }
        if (ref.form() == SourceRef.Form.PACKAGE) {
            return ref.type();
        }
        String[] parts = ref.type().split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && Character.isUpperCase(p.charAt(0))) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /** {@code a.b.**} matches {@code a.b} and sub-packages; {@code a.b.*} matches direct children; otherwise exact. */
    public static boolean matches(String glob, String pkg) {
        if (pkg == null) {
            return false;
        }
        if (glob.endsWith(".**")) {
            String base = glob.substring(0, glob.length() - 3);
            return pkg.equals(base) || pkg.startsWith(base + ".");
        }
        if (glob.endsWith(".*")) {
            String base = glob.substring(0, glob.length() - 2);
            return pkg.startsWith(base + ".") && pkg.indexOf('.', base.length() + 1) < 0;
        }
        return glob.equals(pkg);
    }

    /** Specificity of a glob match, for picking the nearest module. */
    public static int specificity(String glob) {
        return glob.replace(".**", "").replace(".*", "").length();
    }
}
