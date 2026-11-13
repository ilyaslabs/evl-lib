package io.github.ilyaslabs.evl.maven;

import io.github.ilyaslabs.evl.cli.LibraryPackager;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;

/** {@code evl:package} (process-classes): writes META-INF/evl/manifest.json and documents into target/classes. */
public class PackageMojo extends AbstractEvlMojo {

    String groupId;
    String artifactId;
    String version;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }
        try {
            String artifact = groupId == null ? null : groupId + ":" + artifactId + ":" + version;
            int n = LibraryPackager.write(basedir.toPath(), outputDirectory.toPath(), artifact).size();
            getLog().info("EVL: packaged " + n + " file(s) into META-INF/evl");
        } catch (IOException e) {
            throw new MojoExecutionException("EVL packaging failed", e);
        }
    }
}
