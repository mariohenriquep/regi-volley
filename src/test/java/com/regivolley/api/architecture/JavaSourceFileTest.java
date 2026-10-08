package com.regivolley.api.architecture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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

    @Nested
    class SecurityRelevantShape {

        @Test
        void nimbusReferencesAreTracked() {
            // Arrange
            String content = "package a;\nimport com.nimbusds.jose.jwk.ECKey;\nclass A {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.referencesInto(Set.of("com.nimbusds"))).containsExactly("com.nimbusds.jose.jwk.ECKey");
        }

        @Test
        void detectsAConstructorCall() {
            // Arrange
            String content = "package a;\nclass A { Object o = new Actor(a, b); }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.instantiates("Actor")).isTrue();
        }

        @Test
        void detectsAQualifiedConstructorCall() {
            // Arrange
            String content = "package a;\nclass A { Object o = new com.regivolley.api.application.command.Actor (a, b); }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.instantiates("Actor")).isTrue();
        }

        @Test
        void detectsAConstructorReference() {
            // Arrange
            String content = "package a;\nclass A { java.util.function.BiFunction<X, Y, Actor> f = Actor::new; }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.instantiates("Actor")).isTrue();
        }

        @Test
        void aMentionInACommentOrStringOrAFieldIsNotAnInstantiation() {
            // Arrange
            String content = "package a;\n/** new Actor(x) */\nclass A { // new Actor(y)\n Actor field; String s = \"new Actor(\"; }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.instantiates("Actor")).isFalse();
        }

        @Test
        void aDifferentTypeWithTheSameSuffixIsNotTheActor() {
            // Arrange
            String content = "package a;\nclass A { Object o = new AuthenticatedActor(a); Object p = new Actors(); Object q = AuthenticatedActor::new; }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.instantiates("Actor")).isFalse();
        }

        @Test
        void readsTheComponentNamesOfARecordWithAnnotatedComponents() {
            // Arrange
            String content = "package a;\nrecord CreateRequest(@NotBlank @Size(max = 5) String name,\n"
                    + "  List<String> levelNames, UUID associationId) {}\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("CreateRequest.java", content);

            // Assert
            assertThat(source.recordComponentNames()).containsExactly("name", "levelNames", "associationId");
        }

        @Test
        void readsNestedRecordsToo() {
            // Arrange
            String content = "package a;\nrecord OrderRequest(String name, List<Line> lines) {\n"
                    + "  record Line(int quantity, java.util.UUID tenantId) {}\n}\n";

            // Act
            Map<String, List<String>> records = JavaSourceFile.parse("OrderRequest.java", content).recordComponents();

            // Assert
            assertThat(records).containsOnlyKeys("OrderRequest", "Line");
            assertThat(records.get("OrderRequest")).containsExactly("name", "lines");
            assertThat(records.get("Line")).containsExactly("quantity", "tenantId");
        }

        @Test
        void aTypeThatIsNotARecordHasNoComponents() {
            // Arrange
            String content = "package a;\nclass A { void f(String x) {} }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.recordComponentNames()).isEmpty();
            assertThat(source.recordComponents()).isEmpty();
        }
    }

    @Nested
    class StaticMembers {

        @Test
        void readsTheReturnTypeAndNameOfEveryStaticMethod() {
            // Arrange
            String content = """
                    package a;
                    public final class Plan {
                        private static final java.util.Map<String, java.util.Set<String>> ALLOWED = java.util.Map.of();
                        private static final String NAME = compute("x");
                        public static Plan create(String name) { return null; }
                        static <T> java.util.List<T> listOf(T t) { return null; }
                        public static java.util.Optional<Plan> holding(java.util.Collection<Plan> all) { return null; }
                        public Plan edit(String name) { return null; }
                        private static void helper(int x) {}
                        static class Nested {}
                    }
                    """;

            // Act
            List<JavaSourceFile.StaticMethod> methods = JavaSourceFile.parse("Plan.java", content).staticMethods();

            // Assert - fields, instance methods and nested types are not static methods
            assertThat(methods).extracting(JavaSourceFile.StaticMethod::name)
                    .containsExactly("create", "listOf", "holding", "helper");
            assertThat(methods).extracting(JavaSourceFile.StaticMethod::returnType)
                    .containsExactly("Plan", "java.util.List<T>", "java.util.Optional<Plan>", "void");
            assertThat(methods).extracting(JavaSourceFile.StaticMethod::isPrivate)
                    .containsExactly(false, false, false, true);
        }

        @Test
        void aStaticMethodMentionedInACommentOrStringIsNotDeclared() {
            // Arrange
            String content = "package a;\n/** static Plan create(String n) */\nclass A { String s = \"static Plan make(\"; }\n";

            // Act
            List<JavaSourceFile.StaticMethod> methods = JavaSourceFile.parse("A.java", content).staticMethods();

            // Assert
            assertThat(methods).isEmpty();
        }

        @Test
        void detectsACallOfAStaticMethodOnAType() {
            // Arrange
            String content = "package a;\nclass A { Object o = Session.create(a, b); Object p = com.x.Session . reconstruct(c); }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.callsStatic("Session", "create|reconstruct")).isTrue();
            assertThat(source.callsStatic("Session", "reconstruct")).isTrue();
            assertThat(source.callsStatic("Session", "register")).isFalse();
        }

        @Test
        void aCallOnAnotherTypeOrInACommentIsNotACallOnThisType() {
            // Arrange
            String content = "package a;\n/** Session.create(x) */\nclass A { Object o = SessionFactory.create(a); Object p = MySession.create(b); }\n";

            // Act
            JavaSourceFile source = JavaSourceFile.parse("A.java", content);

            // Assert
            assertThat(source.callsStatic("Session", "create")).isFalse();
        }
    }

    @Nested
    class CallsAndMembers {

        @Test
        void listsTheTypeQualifyingEveryCallToAMethod() {
            // Arrange
            String content = "package a;\n/** PlanId.generate() */\nclass A { Object a = PlanId.generate(); Object b = BookingId . generate();"
                    + " Object c = other.generate(1); Object d = PlanId.of(); String s = \"MemberId.generate()\"; }\n";

            // Act
            List<String> qualifiers = JavaSourceFile.parse("A.java", content).qualifiersOfCallsTo("generate");

            // Assert
            assertThat(qualifiers).containsExactly("PlanId", "BookingId", "other");
        }

        @Test
        void detectsReflectionAndMethodHandlesInImportsAndInlineUses() {
            // Arrange
            String imported = "package a;\nimport java.lang.reflect.Constructor;\nclass A {}\n";
            String inline = "package a;\nclass A { Object o = Plan.class.getDeclaredConstructor().newInstance();"
                    + " Object p = java.lang.invoke.MethodHandles.lookup(); Object q = Class . forName(n); void r(Object x) { x.setAccessible(true); } }\n";
            String clean = "package a;\n/** newInstance getDeclaredConstructor */\nclass A { String s = \"newInstance\"; Object o = newInstanceCount; Object p = instance(); }\n";

            // Act
            List<String> importedUses = JavaSourceFile.parse("A.java", imported).reflectionUses();
            List<String> inlineUses = JavaSourceFile.parse("A.java", inline).reflectionUses();
            List<String> cleanUses = JavaSourceFile.parse("A.java", clean).reflectionUses();

            // Assert
            assertThat(importedUses).containsExactly("java.lang.reflect");
            assertThat(inlineUses).contains("getDeclaredConstructor", "newInstance", "java.lang.invoke", "MethodHandles",
                    "Class.forName", "setAccessible");
            assertThat(cleanUses).isEmpty();
        }

        @Test
        void aUtilityClassWithOnlyStaticMembersAndAPrivateConstructorHasNoInstanceMembers() {
            // Arrange
            String content = """
                    package a;
                    public final class PlanFactory {
                        private static final java.util.Map<String, String> CACHE = new java.util.HashMap<>() {{ put("a", "b"); }};
                        private static int counter = compute(1);
                        static { counter = 2; }
                        private PlanFactory() {
                        }
                        public static Plan create(String name) { return new Plan(name); }
                        static <T> T generic(T t) { return t; }
                        private record Helper(int x) { int twice() { return x * 2; } }
                    }
                    """;

            // Act
            List<String> members = JavaSourceFile.parse("PlanFactory.java", content).nonStaticMembers();

            // Assert
            assertThat(members).isEmpty();
        }

        @Test
        void reportsInstanceFieldsInstanceMethodsAndNonPrivateConstructors() {
            // Arrange
            String content = """
                    package a;
                    public final class PlanFactory {
                        private final Clock clock;
                        private int created = 0;
                        public PlanFactory(Clock clock) { this.clock = clock; }
                        public Plan create(String name) { return new Plan(name); }
                        private void helper() {}
                        public static Plan fine() { return null; }
                    }
                    """;

            // Act
            List<String> members = JavaSourceFile.parse("PlanFactory.java", content).nonStaticMembers();

            // Assert
            assertThat(members).containsExactly("field clock", "field created", "constructor", "method create", "method helper");
        }
    }
}
