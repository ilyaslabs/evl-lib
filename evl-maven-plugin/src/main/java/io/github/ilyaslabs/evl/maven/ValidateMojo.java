package io.github.ilyaslabs.evl.maven;

import io.github.ilyaslabs.evl.cli.out.Printer;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Level;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/** {@code evl:validate} (verify): validates EVL documentation against the compiled classpath. */
public class ValidateMojo extends AbstractEvlMojo {

    boolean failOnWarning;
    boolean failOnError = true;
    String format = "text";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        EvlResult r;
        try {
            r = load();
        } catch (IOException e) {
            throw new MojoExecutionException("EVL validation could not read the project", e);
        }
        for (Diagnostic d : r.diagnostics()) {
            switch (d.level()) {
                case ERROR -> getLog().error(d.toString());
                case WARNING, WEAK_WARNING -> getLog().warn(d.toString());
                default -> getLog().info(d.toString());
            }
        }
        boolean sarif = "sarif".equals(format == null ? "" : format.toLowerCase(Locale.ROOT));
        Path report = buildDirectory.toPath().resolve("evl").resolve(sarif ? "evl.sarif" : "evl.txt");
        try {
            StringWriter sw = new StringWriter();
            Printer.diagnostics(r, sarif ? Printer.Format.SARIF : Printer.Format.TEXT, false, "maven", new PrintWriter(sw));
            Files.createDirectories(report.getParent());
            Files.writeString(report, sw.toString());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + report, e);
        }
        long errors = r.count(Level.ERROR);
        long warnings = r.count(Level.WARNING);
        if (failOnError && errors > 0) {
            throw new MojoFailureException("EVL validation failed with " + errors + " error(s). See " + report);
        }
        if (failOnWarning && warnings > 0) {
            throw new MojoFailureException("EVL validation found " + warnings + " warning(s) and failOnWarning is set");
        }
    }
}
