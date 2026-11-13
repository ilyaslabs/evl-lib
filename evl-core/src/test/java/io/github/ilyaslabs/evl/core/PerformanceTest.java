package io.github.ilyaslabs.evl.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.ChangeSet;
import io.github.ilyaslabs.evl.core.api.EvlQueries;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.model.Level;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Budgets of 11.5 on the synthetic 10k-element fixture. Thresholds here are generous CI ceilings
 * (shared runners are slower than the baseline machines); the printed timings are the evidence.
 */
@Tag("performance")
class PerformanceTest {

    private static EvlWorkspace workspace(Map<String, String> docs) {
        EvlWorkspace.Builder b = EvlWorkspace.builder().manifest("evl.yaml", "evl: \"1.0\"\nid: big\nname: Big\n");
        docs.forEach(b::addDocument);
        return b.build();
    }

    @Test
    void loadsAndValidatesTenThousandElements() {
        Map<String, String> docs = SyntheticFixture.documents(10_000);
        EvlWorkspace ws = workspace(docs);
        ws.load(); // warm-up (class loading, JIT)
        long t0 = System.nanoTime();
        EvlResult r = workspace(docs).load();
        long loadMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(r.graph().elements().size()).isGreaterThanOrEqualTo(10_000);
        assertThat(r.diagnostics().stream().filter(d -> d.level() == Level.ERROR)).isEmpty();
        System.out.printf("load+validate %d elements: %d ms%n", r.graph().elements().size(), loadMs);
        assertThat(loadMs).as("EVL-CLI-005: validating 10k elements under 3 s").isLessThan(3_000);
    }

    @Test
    void incrementalUpdateOfOneDocumentIsFast() {
        Map<String, String> docs = SyntheticFixture.documents(10_000);
        EvlWorkspace ws = workspace(docs);
        ws.load();
        String file = "docs/evl/m3.evl.yaml";
        String changed = docs.get(file).replace("name: Component 3-1,", "name: Component 3-1 renamed,");
        long t0 = System.nanoTime();
        EvlResult r = ws.update(ChangeSet.builder().putDocument(file, changed).build());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(r.graph().element("m3.c1").orElseThrow().name()).isEqualTo("Component 3-1 renamed");
        System.out.printf("incremental update of one document: %d ms%n", ms);
        assertThat(ms).isLessThan(1_500);
    }

    @Test
    void searchOverFiveThousandFlowsIsFast() {
        EvlResult r = workspace(SyntheticFixture.documents(62_500)).load(); // 1,250 modules × 4 flows = 5,000 flows
        EvlQueries q = new EvlQueries(r.graph());
        q.search("Flow 7", EvlQueries.Filters.NONE, 50); // warm-up
        long t0 = System.nanoTime();
        var hits = q.search("Flow 7", EvlQueries.Filters.kinds(io.github.ilyaslabs.evl.core.spec.Kind.FLOW), 50);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(hits).isNotEmpty();
        System.out.printf("search over %d flows: %d ms%n", r.graph().elementsOf(io.github.ilyaslabs.evl.core.spec.Kind.FLOW).size(), ms);
        assertThat(ms).as("EVL-FEX-010 target 50 ms; CI ceiling").isLessThan(500);
    }
}
