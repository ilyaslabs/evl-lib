package io.github.ilyaslabs.evl;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Symbol annotations: they only choose how a class or method is drawn. The flow itself comes from
 * {@link Flow}. EVL is framework-agnostic, so nothing is inferred from other annotations.
 */
public final class EVL {
    private EVL() {
    }

    /** An entry point called from outside: box with the method and path; {@code caller} adds a stick figure. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Endpoint {
        String value() default "";
        String method() default "";
        String path() default "";
        String caller() default "";
        String description() default "";
    }

    /** Scheduled or triggered work: clock. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Job {
        String value() default "";
        String schedule() default "";
        String trigger() default "";
        String description() default "";
    }

    /** A database: cylinder. Classes with the same name are drawn as one database. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Database {
        String value() default "";
        String table() default "";
        String description() default "";
    }

    /** Object or file storage: bucket. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Storage {
        String value() default "";
        String description() default "";
    }

    /** A queue, topic or event stream: pipe. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Queue {
        String value() default "";
        String description() default "";
    }

    /** A cache: lightning bolt. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Cache {
        String value() default "";
        String ttl() default "";
        String description() default "";
    }

    /** A third-party system or API: cloud. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface External {
        String value() default "";
        String description() default "";
    }

    /** A contract or port other code depends on: box with a lollipop. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Interface {
        String value() default "";
        String description() default "";
    }

    /** A service or component: plain box. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Service {
        String value() default "";
        String description() default "";
    }

    /** A person or client system, drawn as a stick figure. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Actor {
        String value() default "";
        String description() default "";
    }
}
