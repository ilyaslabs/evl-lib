package io.github.ilyaslabs.evl.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parsed source reference (spec 3.4).
 *
 * <ul>
 *   <li>{@code com.example.OrderService}: a type</li>
 *   <li>{@code com.example.OrderService#place}: a member by name</li>
 *   <li>{@code com.example.OrderService#place(CreateOrderRequest,int)}: a method by signature</li>
 *   <li>{@code package:com.example.order}, {@code config:shop.timeout}, {@code file:docs/a.md#L3}</li>
 * </ul>
 */
public record SourceRef(Form form, String type, String member, List<String> params, String raw) {

    /** Shape of a source reference. */
    public enum Form { TYPE, MEMBER, METHOD, PACKAGE, CONFIG, FILE }

    private static final Pattern QUALIFIED = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*|<init>");
    private static final Pattern PARAM = Pattern.compile("[A-Za-z_$][\\w$.]*(\\[\\])*(\\.\\.\\.)?");

    public SourceRef {
        params = params == null ? null : List.copyOf(params);
    }

    public static Optional<SourceRef> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String s = text.trim();
        if (s.startsWith("package:")) {
            String p = s.substring(8);
            return QUALIFIED.matcher(p).matches() ? Optional.of(new SourceRef(Form.PACKAGE, p, null, null, text)) : Optional.empty();
        }
        if (s.startsWith("config:")) {
            String k = s.substring(7);
            return k.isEmpty() ? Optional.empty() : Optional.of(new SourceRef(Form.CONFIG, k, null, null, text));
        }
        if (s.startsWith("file:")) {
            String f = s.substring(5);
            return f.isEmpty() ? Optional.empty() : Optional.of(new SourceRef(Form.FILE, f, null, null, text));
        }
        int hash = s.indexOf('#');
        String type = hash < 0 ? s : s.substring(0, hash);
        if (!QUALIFIED.matcher(type).matches()) {
            return Optional.empty();
        }
        if (hash < 0) {
            return Optional.of(new SourceRef(Form.TYPE, type, null, null, text));
        }
        String m = s.substring(hash + 1);
        int paren = m.indexOf('(');
        if (paren < 0) {
            return NAME.matcher(m).matches() ? Optional.of(new SourceRef(Form.MEMBER, type, m, null, text)) : Optional.empty();
        }
        if (!m.endsWith(")")) {
            return Optional.empty();
        }
        String name = m.substring(0, paren);
        if (!NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        String inner = m.substring(paren + 1, m.length() - 1).trim();
        List<String> params = new ArrayList<>();
        if (!inner.isEmpty()) {
            for (String p : inner.split(",")) {
                String t = p.trim().replaceAll("<.*>", "");
                if (!PARAM.matcher(t).matches()) {
                    return Optional.empty();
                }
                params.add(t);
            }
        }
        return Optional.of(new SourceRef(Form.METHOD, type, name, params, text));
    }

    /** Simple name of the referenced type. */
    public String simpleTypeName() {
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    public boolean isCode() {
        return form == Form.TYPE || form == Form.MEMBER || form == Form.METHOD || form == Form.PACKAGE;
    }

    @Override
    public String toString() {
        return raw;
    }
}
