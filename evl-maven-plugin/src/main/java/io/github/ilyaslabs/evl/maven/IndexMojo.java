package io.github.ilyaslabs.evl.maven;

import io.github.ilyaslabs.evl.core.api.GraphExport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;

/** {@code evl:index}: writes target/evl/evl-index.json, the resolved index for AI agents. */
public class IndexMojo extends AbstractEvlMojo {

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }
        try {
            Path out = buildDirectory.toPath().resolve("evl/evl-index.json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, GraphExport.json(load()));
            getLog().info("EVL: wrote " + out);
        } catch (IOException e) {
            throw new MojoExecutionException("EVL index failed", e);
        }
    }
}
