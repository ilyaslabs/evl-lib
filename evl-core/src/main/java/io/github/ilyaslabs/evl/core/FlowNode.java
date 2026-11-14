package io.github.ilyaslabs.evl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One path in the merged flow tree, for example {@code image-upload>s3}. */
public final class FlowNode {
    public final String path;
    public String kind = "step";
    public String name;
    public String subtitle = "";
    public String caller = "";
    public String after = "";
    public String resource;
    public Code code;
    public boolean async;
    public int seq;
    public boolean inline;
    public final Set<String> uses = new LinkedHashSet<>();
    public final List<FlowNode> children = new ArrayList<>();
    public final Map<String, String> attrs = new LinkedHashMap<>();

    FlowNode(String path) {
        this.path = path;
        this.name = segment();
    }

    public String segment() {
        return path.substring(path.lastIndexOf('>') + 1);
    }

    public String parent() {
        int i = path.lastIndexOf('>');
        return i < 0 ? "" : path.substring(0, i);
    }

    public boolean virtual() {
        return code == null;
    }

    /** Resources used anywhere in this subtree, including resource steps. */
    public Set<String> allUses() {
        Set<String> all = new LinkedHashSet<>(uses);
        if (resource != null) all.add(resource);
        for (FlowNode c : children) all.addAll(c.allUses());
        return all;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", path);
        m.put("segment", segment());
        m.put("kind", kind);
        m.put("name", name);
        m.put("subtitle", subtitle);
        if (!caller.isEmpty()) m.put("caller", caller);
        if (!after.isEmpty()) m.put("after", after);
        if (resource != null) m.put("resource", resource);
        m.put("async", async);
        m.put("seq", seq);
        m.put("inline", inline);
        m.put("attrs", attrs);
        m.put("uses", new ArrayList<>(uses));
        m.put("allUses", new ArrayList<>(allUses()));
        m.put("code", code == null ? null : code.toJson());
        List<Object> kids = new ArrayList<>();
        for (FlowNode c : children) kids.add(c.toJson());
        m.put("children", kids);
        return m;
    }
}
