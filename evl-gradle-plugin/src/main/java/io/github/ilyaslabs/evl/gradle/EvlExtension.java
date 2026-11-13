package io.github.ilyaslabs.evl.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** The {@code evl { }} extension. */
public abstract class EvlExtension {

    /** Version of evl-processor and evl-annotations to add (default: the plugin's version). */
    public abstract Property<String> getVersion();

    /** Add evl-annotations (compileOnly) and evl-processor (annotationProcessor). Default true. */
    public abstract Property<Boolean> getAddDependencies();

    /** Fail evlValidate on warnings. Default false. */
    public abstract Property<Boolean> getFailOnWarning();

    /** Report format: text or sarif. Default text. */
    public abstract Property<String> getFormat();

    /** Fail the build on EVL errors. Default true. */
    public abstract Property<Boolean> getFailOnError();

    /** The manifest (default: evl.yaml in the project directory). */
    public abstract RegularFileProperty getManifest();
}
