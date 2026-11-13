package io.github.ilyaslabs.evl.core;

import io.github.ilyaslabs.evl.core.json.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The result of a scan: the flow tree, the resources and any problems. */
public final class FlowModel {
    public static final int VERSION = 2;
    public final List<FlowNode> flows = new ArrayList<>();
    public final List<Resource> resources = new ArrayList<>();
    public final List<Problem> problems = new ArrayList<>();

    public void walk(Consumer<FlowNode> visitor) {
        walk(flows, visitor);
    }

    private static void walk(List<FlowNode> nodes, Consumer<FlowNode> visitor) {
        for (FlowNode n : nodes) {
            visitor.accept(n);
            walk(n.children, visitor);
        }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", VERSION);
        List<Object> f = new ArrayList<>();
        for (FlowNode n : flows) f.add(n.toJson());
        m.put("flows", f);
        List<Object> r = new ArrayList<>();
        for (Resource x : resources) r.add(x.toJson());
        m.put("resources", r);
        List<Object> p = new ArrayList<>();
        for (Problem x : problems) p.add(x.toJson());
        m.put("problems", p);
        return m;
    }

    public String json() {
        return Json.compact(toJson());
    }
}
