package com.regivolley.api.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the dependency direction the whole codebase is built around: dependencies only ever
 * point inward, towards the domain (docs/architecture.md §1, §2, §3, §11). Plain JUnit 5 over the
 * production sources - no architecture library. If one of these tests fails, the layering has
 * been violated and the fix is architectural, not cosmetic.
 */
class OnionArchitectureTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");

    private static final String BASE = "com.regivolley.api";
    private static final String DOMAIN = BASE + ".domain";
    private static final String APPLICATION = BASE + ".application";
    private static final String INFRASTRUCTURE = BASE + ".infrastructure";
    private static final String PERSISTENCE = INFRASTRUCTURE + ".persistence";
    private static final String JPA_ENTITIES = PERSISTENCE + ".entity";

    private static List<JavaSourceFile> sources;

    @BeforeAll
    static void scanProductionSources() {
        sources = JavaSourceFile.scan(PRODUCTION_SOURCES);
    }

    @Test
    void productionSourcesAreFound() {
        // Arrange
        String bootstrapClass = "RegiVolleyApplication.java";

        // Act
        List<String> names = sources.stream().map(JavaSourceFile::name).toList();

        // Assert
        assertThat(names)
                .as("the scanner must see the production sources, or every rule below passes vacuously")
                .anyMatch(name -> name.endsWith(bootstrapClass));
    }

    @Test
    void domainDependsOnNoOtherLayer() {
        // Arrange
        Set<String> forbidden = Set.of(APPLICATION, INFRASTRUCTURE);

        // Act
        List<String> violations = violations(DOMAIN, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void applicationDependsOnlyOnDomain() {
        // Arrange
        Set<String> forbidden = Set.of(INFRASTRUCTURE);

        // Act
        List<String> violations = violations(APPLICATION, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        // Arrange
        Set<String> forbidden = Set.of("org.springframework", "jakarta.persistence", "jakarta.validation");

        // Act
        List<String> violations = violations(DOMAIN, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void jpaEntitiesStayInsideThePersistencePackage() {
        // Arrange
        Set<String> forbidden = Set.of(JPA_ENTITIES);

        // Act
        List<String> violations = sources.stream()
                .filter(source -> !source.residesIn(PERSISTENCE))
                .flatMap(source -> describe(source, forbidden).stream())
                .toList();

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void authenticationStaysOutOfDomainAndApplication() {
        // Arrange
        Set<String> forbidden = Set.of("org.springframework.security");

        // Act
        List<String> violations = new ArrayList<>(violations(DOMAIN, forbidden));
        violations.addAll(violations(APPLICATION, forbidden));

        // Assert
        assertThat(violations).isEmpty();
    }

    private static List<String> violations(String layer, Set<String> forbidden) {
        return sources.stream()
                .filter(source -> source.residesIn(layer))
                .flatMap(source -> describe(source, forbidden).stream())
                .toList();
    }

    private static List<String> describe(JavaSourceFile source, Set<String> forbidden) {
        return source.referencesInto(forbidden).stream()
                .map(reference -> source.name() + " -> " + reference)
                .toList();
    }
}
