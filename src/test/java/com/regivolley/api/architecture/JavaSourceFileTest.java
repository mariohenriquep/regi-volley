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
}
