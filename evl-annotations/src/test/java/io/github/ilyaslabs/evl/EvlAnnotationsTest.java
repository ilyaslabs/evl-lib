package io.github.ilyaslabs.evl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EvlAnnotationsTest {

    private static List<Class<?>> annotations() {
        return Arrays.stream(EVL.class.getDeclaredClasses())
            .filter(Class::isAnnotation)
            .collect(Collectors.toList());
    }

    @Test
    void catalogueIsComplete() {
        assertThat(annotations().stream().map(Class::getSimpleName)).containsExactlyInAnyOrder(
            "Application", "Module", "Component", "Dependency", "Interface", "Implementation",
            "Endpoint", "Request", "Response", "ErrorResponse",
            "DataModel", "Field", "Relationship", "Index", "Repository",
            "Flow", "Step", "Call",
            "Event", "Producer", "Consumer",
            "ExternalSystem", "Cache", "Infrastructure",
            "Configuration", "ConfigProperty",
            "Doc", "Decision", "Requirement");
    }

    @Test
    void everyAnnotationHasClassRetention() {
        for (Class<?> a : annotations()) {
            assertThat(a.getAnnotation(Retention.class).value()).as(a.getSimpleName()).isEqualTo(RetentionPolicy.CLASS);
        }
    }

    @Test
    void repeatableAnnotationsHaveContainers() {
        List<String> repeatable = annotations().stream()
            .filter(a -> a.isAnnotationPresent(Repeatable.class))
            .map(Class::getSimpleName).collect(Collectors.toList());
        assertThat(repeatable).containsExactlyInAnyOrder("Dependency", "ErrorResponse", "Relationship", "Index",
            "Step", "Call", "Producer", "Consumer", "ConfigProperty", "Decision", "Requirement");
        for (Class<?> a : annotations()) {
            Repeatable r = a.getAnnotation(Repeatable.class);
            if (r != null) {
                assertThat(r.value().getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.CLASS);
                assertThat(r.value().getAnnotation(Target.class).value()).containsExactlyInAnyOrder(a.getAnnotation(Target.class).value());
            }
        }
    }

    @Test
    void targetsMatchCatalogue() {
        assertThat(EVL.Application.class.getAnnotation(Target.class).value()).containsExactly(ElementType.PACKAGE);
        assertThat(EVL.Module.class.getAnnotation(Target.class).value()).containsExactlyInAnyOrder(ElementType.PACKAGE, ElementType.TYPE);
        assertThat(EVL.Component.class.getAnnotation(Target.class).value()).containsExactly(ElementType.TYPE);
        assertThat(EVL.Flow.class.getAnnotation(Target.class).value()).containsExactlyInAnyOrder(ElementType.METHOD, ElementType.TYPE);
        assertThat(EVL.Step.class.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD);
        assertThat(EVL.Field.class.getAnnotation(Target.class).value()).containsExactlyInAnyOrder(ElementType.FIELD, ElementType.METHOD);
    }

    @EVL.Component(id = "test.component", name = "Test", module = "test")
    @EVL.Dependency(target = "a", type = EVL.DependencyType.API)
    @EVL.Dependency(target = "b")
    static class Usage {
        @EVL.Flow(id = "test.flow", name = "Flow", category = EVL.Category.BUSINESS)
        @EVL.Step(flow = "test.flow", step = "one")
        @EVL.Doc("Explains it")
        void run() {
        }
    }

    @Test
    void annotationsAreInvisibleAtRuntime() {
        assertThat(Usage.class.getAnnotations()).isEmpty();
    }
}
