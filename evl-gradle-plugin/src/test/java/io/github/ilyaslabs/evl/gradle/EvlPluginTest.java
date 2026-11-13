package io.github.ilyaslabs.evl.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvlPluginTest {

    private static void project(Path dir, String doc) throws IOException {
        Files.writeString(dir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(dir.resolve("build.gradle"), """
            plugins {
                id 'java'
                id 'io.github.ilyaslabs.evl'
            }
            group = 'com.example'
            version = '1.2.3'
            evl {
                addDependencies = false
            }
            """);
        Files.writeString(dir.resolve("evl.yaml"), "evl: \"1.0\"\nid: demo\nname: Demo\n");
        Files.createDirectories(dir.resolve("docs"));
        Files.writeString(dir.resolve("docs/a.evl.yaml"), doc);
    }

    private static GradleRunner runner(Path dir, String... args) {
        return GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
            .withArguments(withWarnings(args)).forwardOutput();
    }

    private static java.util.List<String> withWarnings(String... args) {
        java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of(args));
        l.add("--warning-mode=fail");
        return l;
    }

    @Test
    void validatesPackagesAndIndexes(@TempDir Path dir) throws IOException {
        project(dir, "modules:\n  - { id: m, name: M }\n");
        BuildResult r = runner(dir, "check", "evlIndex", "jar", "--configuration-cache").build();
        assertThat(r.task(":evlValidate").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(dir.resolve("build/evl/evl-index.json"))).contains("\"id\": \"m\"");
        assertThat(Files.readString(dir.resolve("build/resources/main/META-INF/evl/manifest.json")))
            .contains("com.example:demo:1.2.3");
        assertThat(dir.resolve("build/resources/main/META-INF/evl/documents/docs/a.evl.yaml")).exists();
        BuildResult again = runner(dir, "check", "--configuration-cache").build();
        assertThat(again.task(":evlValidate").getOutcome()).isIn(TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE);
    }

    @Test
    void failsOnErrors(@TempDir Path dir) throws IOException {
        project(dir, "components:\n  - { id: c, name: C, module: missing }\n");
        BuildResult r = runner(dir, "evlValidate").buildAndFail();
        assertThat(r.getOutput()).contains("EVL-E025").contains("EVL validation failed");
    }
}
