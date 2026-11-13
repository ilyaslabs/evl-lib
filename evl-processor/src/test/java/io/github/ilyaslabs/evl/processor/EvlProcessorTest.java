package io.github.ilyaslabs.evl.processor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.Level;
import io.github.ilyaslabs.evl.core.parse.IndexFile;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvlProcessorTest {

    static final Path EXAMPLES = Path.of(System.getProperty("evl.examples", "../examples"));

    /** Compiles an example with the processor into {@code out} and returns compiler output. */
    static String compile(Path project, Path out, String... options) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null);
        List<Path> sources;
        try (Stream<Path> s = Files.walk(project.resolve("src/main/java"))) {
            sources = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(sources);
        List<String> opts = new ArrayList<>(List.of("-d", out.toString(), "-classpath", System.getProperty("java.class.path"),
            "-processor", EvlProcessor.class.getName(), "-Aevl.projectDir=" + project.toAbsolutePath(),
            "-Aevl.manifest=" + project.resolve("evl.yaml").toAbsolutePath()));
        opts.addAll(List.of(options));
        StringWriter log = new StringWriter();
        boolean ok = javac.getTask(log, fm, null, opts, null, units).call();
        assertThat(ok).as(log.toString()).isTrue();
        return log.toString();
    }

    static void copyDocuments(Path project, Path classes) throws IOException {
        Path docs = project.resolve("docs/evl");
        Path target = classes.resolve("META-INF/evl/documents");
        try (Stream<Path> s = Files.walk(docs)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path t = target.resolve(docs.relativize(p).toString());
                Files.createDirectories(t.getParent());
                Files.copy(p, t);
            }
        }
    }

    @Test
    void writesIndexAndManifest(@TempDir Path out) throws IOException {
        compile(EXAMPLES.resolve("shop"), out);
        String index = Files.readString(out.resolve("META-INF/evl/index.json"));
        IndexFile.Result r = IndexFile.read("index.json", index);
        assertThat(r.diagnostics()).isEmpty();
        assertThat(r.records()).extracting(x -> x.annotation()).contains("Module", "Component", "Endpoint", "Flow",
            "Step", "Dependency", "Field", "Index", "ConfigProperty", "Producer", "Consumer", "Doc", "ErrorResponse");
        var flow = r.records().stream().filter(x -> x.annotation().equals("Flow") && x.string("id").equals("order.placement"))
            .findFirst().orElseThrow();
        assertThat(flow.element()).isEqualTo("com.example.shop.order.OrderService#placeOrder(CreateOrderRequest)");
        assertThat(flow.location().file()).isEqualTo("src/main/java/com/example/shop/order/OrderService.java");
        assertThat(flow.location().line()).isEqualTo(17);
        var endpoint = r.records().stream().filter(x -> x.annotation().equals("Endpoint")).findFirst().orElseThrow();
        assertThat(endpoint.attributes().get("requestType")).isEqualTo("com.example.shop.order.CreateOrderRequest");
        assertThat(Files.readString(out.resolve("META-INF/evl/manifest.json"))).contains("\"id\": \"shop\"");
        // Deterministic output.
        Path again = out.resolveSibling(out.getFileName() + "-2");
        Files.createDirectories(again);
        compile(EXAMPLES.resolve("shop"), again);
        assertThat(Files.readString(again.resolve("META-INF/evl/index.json"))).isEqualTo(index);
    }

    @Test
    void shopFixtureLoadsCleanWithLibrary(@TempDir Path tmp) throws IOException {
        Path lib = tmp.resolve("payments");
        Path app = tmp.resolve("shop");
        Files.createDirectories(lib);
        Files.createDirectories(app);
        compile(EXAMPLES.resolve("payments-sdk"), lib, "-Aevl.artifact=com.example:payments-sdk:1.0.0");
        copyDocuments(EXAMPLES.resolve("payments-sdk"), lib);
        compile(EXAMPLES.resolve("shop"), app);
        EvlResult r = EvlWorkspace.builder().discover(EXAMPLES.resolve("shop"))
            .addIndex(app.resolve("META-INF/evl/index.json")).addLibrary(lib).build().load();
        List<Diagnostic> problems = r.diagnostics().stream().filter(d -> d.level() == Level.ERROR || d.level() == Level.WARNING)
            .toList();
        assertThat(problems).isEmpty();
        var q = r.queries();
        assertThat(q.steps("order.placement")).extracting(Element::id).startsWith("order.placement/validate",
            "order.placement/reserve");
        assertThat(r.graph().element("order.placement/validate").orElseThrow().source().raw())
            .isEqualTo("com.example.shop.order.OrderService#validate(CreateOrderRequest)");
        assertThat(q.callees("order.placement")).extracting(Element::key).contains("inventory.reserve",
            "payments-sdk:payment.authorize", "inventory.release");
        assertThat(q.callers("order.placement")).extracting(Element::id).contains("order.create");
        assertThat(r.graph().element("order.create").orElseThrow().string("request")).contains("order.create-request");
        assertThat(r.graph().element("order.create").orElseThrow().strings("errors")).contains("order.out-of-stock");
        assertThat(q.parents("order.service")).extracting(Element::id).containsExactly("order");
        assertThat(q.parents("config.shop.payments.timeout")).extracting(Element::id).containsExactly("config.payments");
        assertThat(r.graph().incoming("order.placed", EdgeType.PRODUCES)).isNotEmpty();
        assertThat(r.graph().libraries()).singleElement().satisfies(l -> assertThat(l.artifact())
            .isEqualTo("com.example:payments-sdk:1.0.0"));
        assertThat(q.steps("payments-sdk:payment.authorize")).hasSize(4);
    }

    @Test
    void localChecksWarnByDefault(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("p/src/main/java/a");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Bad.java"), """
            package a;
            import io.github.ilyaslabs.evl.EVL;
            @EVL.Component(id = "Bad_Id")
            public class Bad {
                @EVL.ConfigProperty(key = "a.b", sensitive = true, defaultValue = "x")
                String secret;
            }
            """);
        Files.writeString(tmp.resolve("p/evl.yaml"), "evl: \"1.0\"\nid: p\nname: P\n");
        Path out = tmp.resolve("out");
        Files.createDirectories(out);
        String log = compile(tmp.resolve("p"), out);
        assertThat(log).contains("EVL-E011").contains("EVL-E070");
    }
}
