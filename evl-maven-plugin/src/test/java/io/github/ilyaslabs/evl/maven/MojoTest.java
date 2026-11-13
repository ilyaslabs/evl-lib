package io.github.ilyaslabs.evl.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class MojoTest {

    static <T extends AbstractEvlMojo> T setup(T m, Path dir) {
        m.basedir = dir.toFile();
        m.outputDirectory = dir.resolve("target/classes").toFile();
        m.buildDirectory = dir.resolve("target").toFile();
        m.classpathElements = List.of();
        return m;
    }

    @Test
    void packageValidateIndex(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("evl.yaml"), "evl: \"1.0\"\nid: demo\nname: Demo\n");
        Files.writeString(dir.resolve("a.evl.yaml"), "modules:\n  - { id: m, name: M }\n");
        PackageMojo pkg = setup(new PackageMojo(), dir);
        pkg.groupId = "com.example";
        pkg.artifactId = "demo";
        pkg.version = "1.0";
        pkg.execute();
        assertThat(Files.readString(dir.resolve("target/classes/META-INF/evl/manifest.json"))).contains("com.example:demo:1.0");
        assertThat(dir.resolve("target/classes/META-INF/evl/documents/a.evl.yaml")).exists();
        setup(new ValidateMojo(), dir).execute();
        assertThat(dir.resolve("target/evl/evl.txt")).exists();
        setup(new IndexMojo(), dir).execute();
        assertThat(Files.readString(dir.resolve("target/evl/evl-index.json"))).contains("\"id\": \"m\"");
    }

    @Test
    void failsOnErrors(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("evl.yaml"), "evl: \"1.0\"\nid: demo\nname: Demo\n");
        Files.writeString(dir.resolve("a.evl.yaml"), "components:\n  - { id: c, name: C, module: nope }\n");
        ValidateMojo v = setup(new ValidateMojo(), dir);
        v.format = "sarif";
        assertThatThrownBy(v::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("1 error");
        assertThat(Files.readString(dir.resolve("target/evl/evl.sarif"))).contains("EVL-E025");
    }

    @Test
    void descriptorMatchesMojos() throws Exception {
        Document doc;
        try (InputStream in = MojoTest.class.getResourceAsStream("/META-INF/maven/plugin.xml")) {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        NodeList impls = doc.getElementsByTagName("implementation");
        assertThat(impls.getLength()).isEqualTo(3);
        for (int i = 0; i < impls.getLength(); i++) {
            Class<?> c = Class.forName(impls.item(i).getTextContent());
            assertThat(AbstractEvlMojo.class).isAssignableFrom(c);
        }
        NodeList names = doc.getElementsByTagName("name");
        for (int i = 1; i < names.getLength(); i++) { // skip the plugin name
            String field = names.item(i).getTextContent();
            boolean found = false;
            for (Class<?> c : List.of(PackageMojo.class, ValidateMojo.class, IndexMojo.class)) {
                for (Class<?> k = c; k != Object.class; k = k.getSuperclass()) {
                    try {
                        k.getDeclaredField(field);
                        found = true;
                    } catch (NoSuchFieldException ignored) {
                        // keep looking
                    }
                }
            }
            assertThat(found).as(field).isTrue();
        }
        assertThat(doc.getElementsByTagName("version").item(0).getTextContent()).doesNotContain("${");
    }
}
