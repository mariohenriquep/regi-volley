package com.regivolley.api.architecture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the scanner behind {@link OnionArchitectureTest} actually detects dependencies - an
 * architecture rule built on a scanner that sees nothing would always pass.
 */
class JavaSourceFileTest {

    private static final Set<String> INFRASTRUCTURE = Set.of("com.regivolley.api.infrastructure");

    @Nested
    class Package {

        @Test
        void readsThePackageDeclaration() {
            // Arrange
            String content = "package com.regivolley.api.domain.model;\n\npublic class Booking {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Booking.java", content);

            // Assert
            assertThat(source.packageName()).isEqualTo("com.regivolley.api.domain.model");
            assertThat(source.residesIn("com.regivolley.api.domain")).isTrue();
        }

        @Test
        void packageDeclarationIsNotADependency() {
            // Arrange
            String content = "package com.regivolley.api.infrastructure.web;\nclass A {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.references()).isEmpty();
        }

        @Test
        void siblingPackageWithSamePrefixIsNotInside() {
            // Arrange
            String content = "package com.regivolley.api.domainextras;\nclass A {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.residesIn("com.regivolley.api.domain")).isFalse();
        }
    }

    @Nested
    class Detects {

        @Test
        void plainImport() {
            // Arrange
            String content = """
                    package com.regivolley.api.domain.model;
                    import com.regivolley.api.infrastructure.persistence.SessionJpaEntity;
                    class A {}
                    """;

            // Act
            List<String> found = JavaSourceFile.parse("A.java", content).referencesInto(INFRASTRUCTURE);

            // Assert
            assertThat(found).containsExactly("com.regivolley.api.infrastructure.persistence.SessionJpaEntity");
        }

        @Test
        void staticAndWildcardImports() {
            // Arrange
            String content = """
                    package com.regivolley.api.application.usecase;
                    import static com.regivolley.api.infrastructure.config.Defaults.WINDOW;
                    import com.regivolley.api.infrastructure.web.*;
                    class A {}
                    """;

            // Act
            List<String> found = JavaSourceFile.parse("A.java", content).referencesInto(INFRASTRUCTURE);

            // Assert
            assertThat(found).containsExactlyInAnyOrder(
                    "com.regivolley.api.infrastructure.config.Defaults.WINDOW",
                    "com.regivolley.api.infrastructure.web");
        }

        @Test
        void fullyQualifiedNameInCode() {
            // Arrange
            String content = """
                    package com.regivolley.api.domain.model;
                    class A {
                        Object x = new com.regivolley.api.infrastructure.web.Foo();
                        @org.springframework.stereotype.Component class B {}
                    }
                    """;

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.referencesInto(INFRASTRUCTURE))
                    .containsExactly("com.regivolley.api.infrastructure.web.Foo");
            assertThat(source.referencesInto(Set.of("org.springframework")))
                    .containsExactly("org.springframework.stereotype.Component");
        }
    }

    @Nested
    class Ignores {

        @Test
        void commentsAndStringLiterals() {
            // Arrange
            String content = """
                    package com.regivolley.api.domain.model;
                    // import org.springframework.stereotype.Service;
                    /** Unlike {@link org.springframework.context.ApplicationContext} ... */
                    class A {
                        String s = "org.springframework.web.Bad";
                        String t = \"""
                                jakarta.persistence.Entity
                                \""";
                    }
                    """;

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.references()).isEmpty();
        }

        @Test
        void allowedDependencies() {
            // Arrange
            String content = """
                    package com.regivolley.api.application.usecase;
                    import com.regivolley.api.domain.model.Booking;
                    import java.time.Clock;
                    class A {}
                    """;

            // Act
            List<String> found = JavaSourceFile.parse("A.java", content).referencesInto(INFRASTRUCTURE);

            // Assert
            assertThat(found).isEmpty();
        }
    }

    @Nested
    class TypeShape {

        @Test
        void detectsARecordAndItsInterfaces() {
            // Arrange
            String content = """
                    package com.regivolley.api.domain.model.valueobject;
                    import com.regivolley.api.domain.shared.ValueObject;
                    public record Money(long cents) implements ValueObject, Comparable<Money> {}
                    """;

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Money.java", content);

            // Assert
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.RECORD);
            assertThat(source.typeName()).isEqualTo("Money");
            assertThat(source.implementedInterfaces()).containsExactlyInAnyOrder("ValueObject", "Comparable");
            assertThat(source.implementsAnyOf(Set.of("ValueObject"))).isTrue();
        }

        @Test
        void detectsARecordWhoseComponentsSpanLinesAndCarryAnnotations() {
            // Arrange
            String content = """
                    package a;
                    public record Target(
                            @Deprecated(since = "1") String a,
                            Set<String> b) implements com.regivolley.api.domain.shared.ValueObject {}
                    """;

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Target.java", content);

            // Assert
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.RECORD);
            assertThat(source.implementedInterfaces()).containsExactly("ValueObject");
        }

        @Test
        void detectsAnEnum() {
            // Arrange
            String content = "package a;\npublic enum Status implements ValueObject { ACTIVE, INACTIVE }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Status.java", content);

            // Assert
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.ENUM);
            assertThat(source.implementedInterfaces()).containsExactly("ValueObject");
        }

        @Test
        void detectsAFinalClassWithSuperclassAndInterfaces() {
            // Arrange
            String content = """
                    package a;
                    @SuppressWarnings("all")
                    public final class Session extends Base<String>
                            implements AggregateRoot, java.io.Serializable {
                        static class Nested implements Entity {}
                    }
                    """;

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Session.java", content);

            // Assert
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.CLASS);
            assertThat(source.typeName()).isEqualTo("Session");
            assertThat(source.isFinal()).isTrue();
            assertThat(source.implementedInterfaces()).containsExactlyInAnyOrder("AggregateRoot", "Serializable");
        }

        @Test
        void aNonFinalClassIsNotFinal() {
            // Arrange
            String content = "package a;\npublic class Open {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Open.java", content);

            // Assert
            assertThat(source.isFinal()).isFalse();
            assertThat(source.implementedInterfaces()).isEmpty();
        }

        @Test
        void detectsAnInterfaceAndItsExtendedInterfaces() {
            // Arrange
            String content = "package a;\npublic interface UseCase<IN, OUT> extends Marker<IN>, Other {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("UseCase.java", content);

            // Assert
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.INTERFACE);
            assertThat(source.implementedInterfaces()).containsExactlyInAnyOrder("Marker", "Other");
        }

        @Test
        void ignoresKeywordsInCommentsAndStringsBeforeTheDeclaration() {
            // Arrange
            String content = """
                    package a;
                    /** A class implements Entity when ... */
                    // record Fake implements Entity
                    public record Real(int x) {}
                    """;

            // Act
            JavaSourceFile source = JavaSourceFile.parse("Real.java", content);

            // Assert
            assertThat(source.typeName()).isEqualTo("Real");
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.RECORD);
            assertThat(source.implementedInterfaces()).isEmpty();
        }

        @Test
        void aFileWithoutATypeHasNoKind() {
            // Arrange
            String content = "package a;\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("package-info.java", content);

            // Assert
            assertThat(source.kind()).isEqualTo(JavaSourceFile.TypeKind.NONE);
            assertThat(source.typeName()).isEmpty();
        }
    }
}
