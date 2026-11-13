package io.github.ilyaslabs.evl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.cli.scan.JavaSourceScanner;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import io.github.ilyaslabs.evl.core.parse.IndexFile;
import io.github.ilyaslabs.evl.processor.EvlProcessor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    static final Path EXAMPLES = Path.of(System.getProperty("evl.examples", "../examples"));
    static Path paymentsLib;

    record Run(int code, String out, String err) {
    }

    static Run evl(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = Main.run(args, new PrintWriter(out), new PrintWriter(err));
        return new Run(code, out.toString(), err.toString());
    }

    static void compile(Path project, Path out) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        var fm = javac.getStandardFileManager(null, null, null);
        List<Path> sources;
        try (Stream<Path> s = Files.walk(project.resolve("src/main/java"))) {
            sources = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        StringWriter log = new StringWriter();
        boolean ok = javac.getTask(log, fm, null, List.of("-d", out.toString(), "-classpath",
                System.getProperty("java.class.path"), "-processor", EvlProcessor.class.getName(),
                "-Aevl.projectDir=" + project.toAbsolutePath(), "-Aevl.manifest=" + project.resolve("evl.yaml").toAbsolutePath()),
            null, fm.getJavaFileObjectsFromPaths(sources)).call();
        assertThat(ok).as(log.toString()).isTrue();
    }

    @BeforeAll
    static void buildLibrary(@TempDir Path tmp) throws IOException {
        paymentsLib = tmp.resolve("payments");
        Files.createDirectories(paymentsLib);
        compile(EXAMPLES.resolve("payments-sdk"), paymentsLib);
        Path docs = EXAMPLES.resolve("payments-sdk/docs/evl");
        try (Stream<Path> s = Files.walk(docs)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path t = paymentsLib.resolve("META-INF/evl/documents").resolve(docs.relativize(p).toString());
                Files.createDirectories(t.getParent());
                Files.copy(p, t);
            }
        }
    }

    @Test
    void sourceScanMatchesProcessor(@TempDir Path out) throws IOException {
        Path shop = EXAMPLES.resolve("shop");
        compile(shop, out);
        List<AnnotationRecord> fromProcessor = IndexFile.read("i", Files.readString(out.resolve("META-INF/evl/index.json"))).records();
        List<AnnotationRecord> fromScan = new ArrayList<>();
        new JavaSourceScanner(shop).scan(List.of(shop.resolve("src/main/java"))).values().forEach(fromScan::addAll);
        assertThat(normalize(fromScan)).isEqualTo(normalize(fromProcessor));
    }

    private static List<String> normalize(List<AnnotationRecord> records) {
        return records.stream()
            .sorted(Comparator.comparing((AnnotationRecord r) -> r.location().file()).thenComparingInt(r -> r.location().line())
                .thenComparing(AnnotationRecord::annotation))
            .map(r -> r.annotation() + "|" + r.element() + "|" + r.elementKind() + "|" + r.packageName() + "|"
                + new java.util.TreeMap<>(r.attributes()) + "|" + r.location().file() + ":" + r.location().line())
            .toList();
    }

    @Test
    void validateShopIsClean() {
        Run r = evl("validate", EXAMPLES.resolve("shop").toString(), "--classpath", paymentsLib.toString(), "--min-level", "warning");
        assertThat(r.code()).as(r.out() + r.err()).isZero();
        assertThat(r.out()).contains("0 error(s), 0 warning(s)");
    }

    @Test
    void validateFormats() {
        Run json = evl("validate", EXAMPLES.resolve("shop").toString(), "--classpath", paymentsLib.toString(), "--format", "json");
        assertThat(json.out()).contains("\"summary\"").contains("\"errors\": 0");
        Run sarif = evl("validate", EXAMPLES.resolve("legacy-yaml-only").toString(), "--format", "sarif");
        assertThat(sarif.out()).contains("\"version\": \"2.1.0\"").contains("\"driver\"");
        assertThat(sarif.code()).isZero();
    }

    @Test
    void missingLibraryIsAnError() {
        Run r = evl("validate", EXAMPLES.resolve("shop").toString());
        assertThat(r.code()).isEqualTo(Main.ERRORS);
        assertThat(r.out()).contains("EVL-E022");
    }

    @Test
    void inspectListGraph() {
        String shop = EXAMPLES.resolve("shop").toString();
        Run inspect = evl("inspect", "flow:order.placement", "--dir", shop, "--classpath", paymentsLib.toString(), "--format", "json");
        assertThat(inspect.code()).isZero();
        assertThat(inspect.out()).contains("\"structure\"").contains("order.placement/stock-ok");
        Run text = evl("inspect", "order.placement", "--dir", shop, "--classpath", paymentsLib.toString());
        assertThat(text.out()).contains("DECISION stock-ok").contains("[branch reserved]");
        Run list = evl("list", shop, "--kind", "flow", "--classpath", paymentsLib.toString());
        assertThat(list.out()).contains("order.placement").contains("payments-sdk:payment.authorize");
        Run mermaid = evl("graph", shop, "--format", "mermaid", "--root", "order", "--classpath", paymentsLib.toString());
        assertThat(mermaid.out()).startsWith("flowchart LR").contains("Order Service");
        Run dot = evl("graph", shop, "--format", "dot", "--classpath", paymentsLib.toString());
        assertThat(dot.out()).startsWith("digraph evl");
        Run missing = evl("inspect", "order.placemnt", "--dir", shop);
        assertThat(missing.code()).isEqualTo(Main.ERRORS);
        assertThat(missing.err()).contains("Did you mean");
    }

    @Test
    void indexWritesAgentInterface(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("evl-index.json");
        Run r = evl("index", EXAMPLES.resolve("shop").toString(), "--classpath", paymentsLib.toString(), "--out", out.toString());
        assertThat(r.code()).isZero();
        String json = Files.readString(out);
        assertThat(json).contains("\"elements\"").contains("\"edges\"").contains("\"flows\"").contains("payments-sdk");
    }

    @Test
    void initThenValidate(@TempDir Path tmp) {
        Path dir = tmp.resolve("my-app");
        assertThat(evl("init", dir.toString()).code()).isZero();
        assertThat(Files.exists(dir.resolve("evl.yaml"))).isTrue();
        Run v = evl("validate", dir.toString());
        assertThat(v.code()).as(v.out()).isZero();
        assertThat(evl("init", dir.toString()).code()).isEqualTo(Main.ERRORS);
    }

    @Test
    void fmtIsCanonicalAndKeepsComments(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("evl.yaml"), "evl: \"1.0\"\nid: t\nname: T\n");
        Path doc = tmp.resolve("a.evl.yaml");
        Files.writeString(doc, """
            flows:
                - name: F   # display name
                  category: APPLICATION
                  id: f
                  steps:
                    - { name: A, id: a, type: ACTION }
            modules:
                - name: M
                  id: m
            """);
        assertThat(evl("fmt", tmp.toString(), "--check").code()).isEqualTo(Main.ERRORS);
        assertThat(evl("fmt", tmp.toString()).code()).isZero();
        String formatted = Files.readString(doc);
        assertThat(formatted).contains("# display name");
        assertThat(formatted.indexOf("modules:")).isLessThan(formatted.indexOf("flows:"));
        assertThat(formatted.indexOf("id: f")).isLessThan(formatted.indexOf("name: F"));
        assertThat(evl("fmt", tmp.toString(), "--check").code()).isZero();
        assertThat(evl("validate", tmp.toString()).code()).isZero();
    }

    @Test
    void rulesAndMisc() {
        assertThat(evl("rules").out()).contains("EVL-E025");
        assertThat(evl("compat").out()).contains("EVL spec");
        assertThat(evl("schema").out()).contains("evl-document.schema.json");
        assertThat(evl("--version").out()).contains("evl ");
        assertThat(evl("nonsense").code()).isEqualTo(Main.FAILURE);
    }
}
