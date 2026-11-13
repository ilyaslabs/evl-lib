/*
 * Copyright 2026 ilyaslabs
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.ilyaslabs.evl;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Holder for all EVL (Engineering Visualization Language) annotations.
 *
 * <p>EVL annotations declare engineering knowledge explicitly: modules, components, dependencies,
 * APIs, data models, events, workflows, configuration, decisions and requirements. Nothing is
 * inferred from code; only what is declared appears in the EVL model.
 *
 * <p>All annotations use {@link RetentionPolicy#CLASS} retention: they are recorded in class files
 * (so EVL documentation travels with library jars) but are invisible to runtime reflection and
 * cost nothing at runtime.
 *
 * <p>Java annotation attributes cannot be {@code null}. An empty string, an empty array or the
 * enum constant {@code UNSPECIFIED} means "not set".
 *
 * <pre>{@code
 * import io.github.ilyaslabs.evl.EVL;
 *
 * @EVL.Component(id = "order.service", name = "Order Service", module = "order")
 * public class OrderService { ... }
 * }</pre>
 *
 * @see <a href="https://ilyaslabs.github.io/evl-lib/spec/1.0/annotations">EVL annotation catalogue</a>
 */
public final class EVL {

    private EVL() {
    }

    // ------------------------------------------------------------------ enums

    /** Architectural layer of a component. */
    public enum Layer { UNSPECIFIED, PRESENTATION, APPLICATION, DOMAIN, INFRASTRUCTURE, INTEGRATION, CUSTOM }

    /** Kind of an engineering dependency. */
    public enum DependencyType { UNSPECIFIED, DIRECT, API, EVENT, DATABASE, CACHE, EXTERNAL, ASYNC, CONFIG, CUSTOM }

    /** Direction of a dependency relative to the declaring element. */
    public enum Direction { OUTBOUND, INBOUND, BIDIRECTIONAL }

    /** Stability of a contract. */
    public enum Stability { UNSPECIFIED, STABLE, EVOLVING, EXPERIMENTAL, DEPRECATED }

    /** Protocol of an endpoint. */
    public enum Protocol { UNSPECIFIED, HTTP, GRPC, GRAPHQL, WEBSOCKET, MESSAGING, RMI, CLI, CUSTOM }

    /** Cardinality of a data relationship. */
    public enum Cardinality { UNSPECIFIED, ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY }

    /** Ownership semantics of a data relationship. */
    public enum Ownership { UNSPECIFIED, COMPOSITION, AGGREGATION, REFERENCE }

    /** Workflow category. */
    public enum Category { UNSPECIFIED, BUSINESS, APPLICATION, INTEGRATION, BACKGROUND, SCHEDULED, TECHNICAL, CUSTOM }

    /** Workflow step type. Structural types (DECISION, PARALLEL, ...) are only meaningful in YAML. */
    public enum StepType { ACTION, DECISION, PARALLEL, CALL_FLOW, EVENT, WAIT, RETRY, COMPENSATE, FAIL, END, GOTO, NOTE }

    /** Kind of a declared call. */
    public enum CallKind { SYNC, ASYNC, EVENT, SCHEDULED }

    /** Delivery guarantee of an event. */
    public enum Delivery { UNSPECIFIED, AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE }

    /** Type of an external system. */
    public enum SystemType { UNSPECIFIED, API, DATABASE, BROKER, STORAGE, SEARCH, IDENTITY, PAYMENT, EMAIL, CUSTOM }

    /** Status of an engineering decision. */
    public enum DecisionStatus { UNSPECIFIED, PROPOSED, ACCEPTED, SUPERSEDED, DEPRECATED }

    /** Kind of a requirement. */
    public enum RequirementKind { UNSPECIFIED, BUSINESS, FUNCTIONAL, NON_FUNCTIONAL, SECURITY, COMPLIANCE }

    /** Priority of a requirement. */
    public enum Priority { UNSPECIFIED, MUST, SHOULD, COULD }

    // ------------------------------------------------------------ architecture

    /**
     * Adds metadata to the EVL application declared by the {@code evl.yaml} manifest.
     * At most one per application; the manifest wins on conflict.
     *
     * <pre>{@code
     * // package-info.java
     * @EVL.Application(name = "Shop", domain = "commerce")
     * package com.example.shop;
     * }</pre>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PACKAGE)
    public @interface Application {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String domain() default "";
    }

    /**
     * Declares a logical module. On {@code package-info.java} the module covers that package and
     * its sub-packages.
     *
     * <pre>{@code
     * @EVL.Module(id = "order", name = "Order", responsibilities = "Order lifecycle")
     * package com.example.order;
     * }</pre>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PACKAGE, ElementType.TYPE})
    public @interface Module {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String domain() default "";
        String[] responsibilities() default {};
        /** Owning team or person (free text). */
        String owner() default "";
        /** Reference to a parent module. */
        String parent() default "";
    }

    /**
     * Declares a documented component: an important unit of responsibility.
     *
     * <pre>{@code
     * @EVL.Component(id = "order.service", name = "Order Service", module = "order",
     *     responsibilities = {"Create orders", "Coordinate fulfilment"})
     * public class OrderService { }
     * }</pre>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Component {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        /** Owning module reference; inherited from the nearest {@link Module} package when empty. */
        String module() default "";
        String[] responsibilities() default {};
        Layer layer() default Layer.UNSPECIFIED;
    }

    /**
     * Declares an explicit engineering dependency from the annotated element to {@link #target()}.
     *
     * <pre>{@code
     * @EVL.Dependency(target = "inventory.reservations", type = EVL.DependencyType.API,
     *     reason = "Reserve stock before payment")
     * }</pre>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.PACKAGE})
    @Repeatable(Dependency.List.class)
    public @interface Dependency {
        String target();
        DependencyType type() default DependencyType.UNSPECIFIED;
        String customType() default "";
        String description() default "";
        String reason() default "";
        Direction direction() default Direction.OUTBOUND;
        boolean optional() default false;

        /** Container for repeated {@link Dependency} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE, ElementType.PACKAGE})
        @interface List {
            Dependency[] value();
        }
    }

    /** Declares an important interface or contract. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Interface {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String module() default "";
        String[] responsibilities() default {};
        Stability stability() default Stability.UNSPECIFIED;
    }

    /**
     * Declares that the annotated type implements the EVL interface {@link #of()}.
     * Implementations found only by class hierarchy are shown as inferred.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Implementation {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        /** Reference to the implemented {@link Interface}. */
        String of();
        /** Variant name, for example "Stripe". */
        String variant() default "";
    }

    // --------------------------------------------------------------------- API

    /**
     * Declares an API endpoint or externally exposed operation. For request and response, set either
     * the EVL reference ({@code requestRef}) or the type ({@code requestType}), not both.
     *
     * <pre>{@code
     * @EVL.Endpoint(id = "order.create", name = "Create Order", protocol = EVL.Protocol.HTTP,
     *     method = "POST", path = "/orders", requestType = CreateOrderRequest.class)
     * public Order createOrder(CreateOrderRequest request) { ... }
     * }</pre>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface Endpoint {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        Protocol protocol() default Protocol.UNSPECIFIED;
        /** HTTP method or operation verb, for example {@code POST}. */
        String method() default "";
        /** Path or operation name. */
        String path() default "";
        String requestRef() default "";
        Class<?> requestType() default void.class;
        String responseRef() default "";
        Class<?> responseType() default void.class;
        /** References to error responses. */
        String[] errors() default {};
        /** Authentication requirements (free text). */
        String auth() default "";
        /** Related flow reference. */
        String flow() default "";
        /** Owning component; defaults to the enclosing component. */
        String component() default "";
    }

    /** Documents a request structure. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Request {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String[] endpoint() default {};
        String validation() default "";
    }

    /** Documents a response structure. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Response {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String[] endpoint() default {};
    }

    /**
     * On a type, declares an error contract. On an endpoint method, declares that the endpoint
     * returns it.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Repeatable(ErrorResponse.List.class)
    public @interface ErrorResponse {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        /** Error code, for example {@code 409} or {@code NOT_FOUND}. */
        String code() default "";
        String condition() default "";
        boolean retryable() default false;

        /** Container for repeated {@link ErrorResponse} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE, ElementType.METHOD})
        @interface List {
            ErrorResponse[] value();
        }
    }

    // -------------------------------------------------------------------- data

    /** Declares an important data model, including its ownership and lifecycle. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface DataModel {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String module() default "";
        /** Reference to the owning module or component. */
        String owner() default "";
        /** Storage location, for example {@code postgres.orders}. */
        String storage() default "";
        String lifecycle() default "";
        String retention() default "";
        /** Whether the model holds personally identifiable information. */
        boolean pii() default false;
    }

    /** Documents a significant field of a data model. Only annotated fields appear in EVL. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface Field {
        String name() default "";
        String description() default "";
        String meaning() default "";
        boolean required() default false;
        String unit() default "";
        boolean sensitive() default false;
        String example() default "";
    }

    /** Declares a relationship to another data model. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Repeatable(Relationship.List.class)
    public @interface Relationship {
        String target();
        Cardinality cardinality() default Cardinality.UNSPECIFIED;
        Ownership ownership() default Ownership.UNSPECIFIED;
        String description() default "";

        /** Container for repeated {@link Relationship} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.FIELD, ElementType.TYPE})
        @interface List {
            Relationship[] value();
        }
    }

    /** Documents an important index or access path. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @Repeatable(Index.List.class)
    public @interface Index {
        String[] fields();
        boolean unique() default false;
        String reason() default "";

        /** Container for repeated {@link Index} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.TYPE)
        @interface List {
            Index[] value();
        }
    }

    /** Declares a repository or persistence boundary. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Repository {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String[] models() default {};
        String storage() default "";
    }

    // ---------------------------------------------------------------- workflow

    /**
     * Declares a flow whose entry point is the annotated method. Steps usually come from a
     * {@code .evl.yaml} flow with the same id.
     *
     * <pre>{@code
     * @EVL.Flow(id = "order.placement", name = "Order Placement", category = EVL.Category.BUSINESS)
     * public Order placeOrder(CreateOrderRequest request) { ... }
     * }</pre>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface Flow {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        Category category() default Category.UNSPECIFIED;
        String domain() default "";
        String module() default "";
        String trigger() default "";
    }

    /** Links the annotated method to step {@link #step()} of flow {@link #flow()}. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Repeatable(Step.List.class)
    public @interface Step {
        String flow();
        String step();
        String name() default "";
        String description() default "";
        StepType type() default StepType.ACTION;

        /** Container for repeated {@link Step} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.METHOD)
        @interface List {
            Step[] value();
        }
    }

    /** Declares that the annotated method calls another documented element. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Repeatable(Call.List.class)
    public @interface Call {
        String target();
        CallKind kind() default CallKind.SYNC;
        String reason() default "";

        /** Container for repeated {@link Call} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.METHOD)
        @interface List {
            Call[] value();
        }
    }

    // --------------------------------------------------------------- messaging

    /** Declares an event or message contract. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Event {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        /** Topic or queue name. */
        String channel() default "";
        Delivery delivery() default Delivery.UNSPECIFIED;
        String ordering() default "";
        String schema() default "";
        /** Reference to the broker (external system or infrastructure). */
        String broker() default "";
    }

    /** Documents that the annotated element produces {@link #event()}. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Repeatable(Producer.List.class)
    public @interface Producer {
        String event();
        String when() default "";

        /** Container for repeated {@link Producer} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.METHOD, ElementType.TYPE})
        @interface List {
            Producer[] value();
        }
    }

    /** Documents that the annotated element consumes {@link #event()}. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Repeatable(Consumer.List.class)
    public @interface Consumer {
        String event();
        String retry() default "";
        String onFailure() default "";
        boolean idempotent() default false;
        String flow() default "";

        /** Container for repeated {@link Consumer} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.METHOD, ElementType.TYPE})
        @interface List {
            Consumer[] value();
        }
    }

    // ---------------------------------------------------------- infrastructure

    /** Declares an external system, usually on its client or adapter class. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.PACKAGE})
    public @interface ExternalSystem {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        SystemType systemType() default SystemType.UNSPECIFIED;
        /** Documentation URL. Never put credentials here. */
        String url() default "";
        String owner() default "";
    }

    /** Documents a cache. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface Cache {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String store() default "";
        String ttl() default "";
        String key() default "";
        String invalidation() default "";
    }

    /** Documents an infrastructure resource. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.PACKAGE})
    public @interface Infrastructure {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String resourceType() default "";
        String[] environments() default {};
    }

    // ----------------------------------------------------------- configuration

    /** Declares a configuration group. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Configuration {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        String prefix() default "";
        String component() default "";
    }

    /**
     * Documents a configuration property. When {@link #sensitive()} is true, {@link #defaultValue()}
     * must be empty: secrets never belong in EVL.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
    @Repeatable(ConfigProperty.List.class)
    public @interface ConfigProperty {
        String id() default "";
        String key() default "";
        String description() default "";
        String defaultValue() default "";
        boolean required() default false;
        boolean sensitive() default false;
        String[] environments() default {};
        /** References to elements affected by this property. */
        String[] affects() default {};

        /** Container for repeated {@link ConfigProperty} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
        @interface List {
            ConfigProperty[] value();
        }
    }

    // --------------------------------------------------------------- knowledge

    /** Explains any declaration. {@link #value()} is Markdown. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PACKAGE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER})
    public @interface Doc {
        String value();
        String[] see() default {};
        String[] requirements() default {};
        String[] decisions() default {};
        String concurrency() default "";
        String performance() default "";
        String security() default "";
        String failure() default "";
    }

    /** Documents an engineering decision (ADR style). */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
    @Repeatable(Decision.List.class)
    public @interface Decision {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        DecisionStatus status() default DecisionStatus.UNSPECIFIED;
        String context() default "";
        String decision() default "";
        String reasoning() default "";
        String[] alternatives() default {};
        String consequences() default "";
        String supersedes() default "";
        /** ISO date, for example {@code 2026-11-13}. */
        String date() default "";

        /** Container for repeated {@link Decision} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
        @interface List {
            Decision[] value();
        }
    }

    /** Documents a business or technical requirement. */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
    @Repeatable(Requirement.List.class)
    public @interface Requirement {
        String id() default "";
        String name() default "";
        String description() default "";
        String[] tags() default {};
        RequirementKind kind() default RequirementKind.UNSPECIFIED;
        String statement() default "";
        /** Ticket URL or text. */
        String source() default "";
        Priority priority() default Priority.UNSPECIFIED;

        /** Container for repeated {@link Requirement} annotations. */
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
        @interface List {
            Requirement[] value();
        }
    }
}
