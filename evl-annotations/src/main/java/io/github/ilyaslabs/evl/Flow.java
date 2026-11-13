package io.github.ilyaslabs.evl;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Places a class or method on a flow path. Paths are {@code >}-separated ids that merge into one tree:
 * {@code @Flow("image-upload")} on the endpoint and {@code @Flow("image-upload>s3")} on the S3 call.
 *
 * <p>Inside a method body, use the comment marker {@code // @flow image-upload>s3>key: Build object key}.
 *
 * <p>Steps are ordered by the call graph; {@link #after()} places an asynchronous step after another path.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Repeatable(Flow.List.class)
public @interface Flow {
    /** The path, for example {@code "image-upload>s3"}. */
    String value();

    /** Display name; defaults to the last path segment. */
    String label() default "";

    /** Path this step follows asynchronously (a queue, an event), for example {@code "image-upload>queue"}. */
    String after() default "";

    /** One line shown under the name. */
    String description() default "";

    /** Holder for repeated {@code @Flow} annotations. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface List {
        Flow[] value();
    }
}
