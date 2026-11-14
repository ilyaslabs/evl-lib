package io.github.ilyaslabs.evl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A database, storage, queue, cache, external system... Classes with the same kind and name merge. */
public final class Resource {
    public final String id;
    public final String kind;
    public final String name;
    public String subtitle = "";
    public Code code;
    public final List<String> members = new ArrayList<>();

    Resource(String kind, String name) {
        this.id = kind + ":" + name;
        this.kind = kind;
        this.name = name;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("kind", kind);
        m.put("name", name);
        m.put("subtitle", subtitle);
        m.put("members", members);
        m.put("code", code == null ? null : code.toJson());
        return m;
    }
}
