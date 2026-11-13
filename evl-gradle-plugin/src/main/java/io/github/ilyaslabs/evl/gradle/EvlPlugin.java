package io.github.ilyaslabs.evl.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.tasks.ProcessResources;

/**
 * Plugin {@code io.github.ilyaslabs.evl} (spec 7.6). With the {@code java} plugin it adds the EVL
 * annotations and processor, packages {@code META-INF/evl/} into the jar, and registers
 * {@code evlValidate} (wired into {@code check}) and {@code evlIndex}. Configuration-cache and
 * build-cache compatible.
 */
public class EvlPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        EvlExtension ext = project.getExtensions().create("evl", EvlExtension.class);
        ext.getVersion().convention(pluginVersion());
        ext.getAddDependencies().convention(true);
        ext.getFailOnWarning().convention(false);
        ext.getFailOnError().convention(true);
        ext.getFormat().convention("text");
        ext.getManifest().convention(project.getLayout().getProjectDirectory().file("evl.yaml"));

        project.getPlugins().withId("java", p -> configureJava(project, ext));
        if (!project.getPlugins().hasPlugin("java")) {
            registerTasks(project, ext, null);
        }
    }

    private void configureJava(Project project, EvlExtension ext) {
        project.afterEvaluate(p -> {
            if (ext.getAddDependencies().get()) {
                String v = ext.getVersion().get();
                p.getDependencies().add("compileOnly", "io.github.ilyaslabs:evl-annotations:" + v);
                p.getDependencies().add("annotationProcessor", "io.github.ilyaslabs:evl-processor:" + v);
            }
        });
        String dir = project.getProjectDir().getAbsolutePath();
        project.getTasks().withType(JavaCompile.class).configureEach(t -> t.getOptions().getCompilerArgs()
            .add("-Aevl.projectDir=" + dir));
        SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        registerTasks(project, ext, main);
    }

    private void registerTasks(Project project, EvlExtension ext, SourceSet main) {
        ConfigurableFileTree docs = project.fileTree(project.getProjectDir(), t -> {
            t.include("evl.yaml", "**/*.evl.yaml");
            t.exclude("build/**", ".gradle/**", "target/**", "node_modules/**");
        });
        org.gradle.api.provider.Provider<String> artifact = project.provider(() ->
            project.getGroup() + ":" + project.getName() + ":" + project.getVersion());
        TaskProvider<EvlPackageTask> pkg = project.getTasks().register("evlPackage", EvlPackageTask.class, t -> {
            t.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            t.setDescription("Writes META-INF/evl/manifest.json and documents for the jar.");
            t.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            t.getSources().from(docs);
            t.getArtifact().set(artifact);
            t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("evl/resources"));
        });
        TaskProvider<EvlValidateTask> validate = project.getTasks().register("evlValidate", EvlValidateTask.class, t -> {
            t.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            t.setDescription("Validates EVL documentation against the compiled code.");
            t.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            t.getSources().from(docs);
            t.getFailOnWarning().set(ext.getFailOnWarning());
            t.getFailOnError().set(ext.getFailOnError());
            t.getFormat().set(ext.getFormat());
            t.getReport().set(project.getLayout().getBuildDirectory().file(ext.getFormat().map(f ->
                "reports/evl/evl." + ("sarif".equalsIgnoreCase(f) ? "sarif" : "txt"))));
            if (main != null) {
                t.getClasspath().from(main.getOutput(), main.getRuntimeClasspath());
            }
        });
        project.getTasks().register("evlIndex", EvlIndexTask.class, t -> {
            t.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            t.setDescription("Writes the resolved EVL index for AI agents to build/evl/evl-index.json.");
            t.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            t.getSources().from(docs);
            t.getOutputFile().set(project.getLayout().getBuildDirectory().file("evl/evl-index.json"));
            if (main != null) {
                t.getClasspath().from(main.getOutput(), main.getRuntimeClasspath());
            }
        });
        if (main != null) {
            project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class, t -> t.from(pkg));
        }
        project.getPlugins().withId("base", b -> project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME,
            c -> c.dependsOn(validate)));
    }

    static String pluginVersion() {
        try (InputStream in = EvlPlugin.class.getResourceAsStream("evl-gradle.properties")) {
            Properties p = new Properties();
            if (in != null) {
                p.load(in);
            }
            return p.getProperty("version", "1.0.0");
        } catch (IOException e) {
            return "1.0.0";
        }
    }
}
