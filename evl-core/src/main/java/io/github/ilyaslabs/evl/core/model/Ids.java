package io.github.ilyaslabs.evl.core.model;

import java.util.regex.Pattern;

/** Identifier grammar (spec 3.2). */
public final class Ids {

    public static final int MAX_LENGTH = 128;
    private static final Pattern ELEMENT_ID = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*");
    private static final Pattern APP_ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9-]*");

    private Ids() {
    }

    public static boolean isElementId(String s) {
        return s != null && s.length() <= MAX_LENGTH && ELEMENT_ID.matcher(s).matches();
    }

    public static boolean isAppId(String s) {
        return s != null && s.length() <= MAX_LENGTH && APP_ID.matcher(s).matches();
    }

    public static boolean isLocalId(String s) {
        return s != null && s.length() <= MAX_LENGTH && LOCAL_ID.matcher(s).matches();
    }

    /**
     * Derives an id from a Java simple name: {@code OrderService} becomes {@code order-service},
     * prefixed with {@code prefix.} when a prefix is given.
     */
    public static String derive(String prefix, String simpleName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && (Character.isLowerCase(simpleName.charAt(i - 1))
                    || (i + 1 < simpleName.length() && Character.isLowerCase(simpleName.charAt(i + 1))))) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('-');
            }
        }
        String local = sb.toString().replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (local.isEmpty() || !Character.isLetter(local.charAt(0))) {
            local = "x" + local;
        }
        return prefix == null || prefix.isEmpty() ? local : prefix + "." + local;
    }
}
