package com.regivolley.api.infrastructure.web;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * springdoc publishes a record under its simple name, so two web records with the same simple name (two nested {@code EntryView}s,
 * a nested {@code SlotView} twice) collapse into one schema and the contract shows the wrong fields for one of them (issue #57).
 * Every record of the DTO package, nested ones included, must therefore have a name no other one has.
 */
public class WebDtoNamesTest {

    private static final String DTO_PACKAGE = ApiError.class.getPackageName();

    public static List<Class<?>> dtoRecords() throws IOException, URISyntaxException {
        Path directory = Path.of(ApiError.class.getResource(ApiError.class.getSimpleName() + ".class").toURI()).getParent();
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".class"))
                    .map(name -> DTO_PACKAGE + "." + name.substring(0, name.length() - ".class".length()))
                    .map(WebDtoNamesTest::load)
                    .filter(Class::isRecord)
                    .collect(Collectors.toList());
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, WebDtoNamesTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A class file of the DTO package cannot be loaded: " + name, e);
        }
    }

    @Test
    void noTwoWebRecordsShareASimpleName() throws Exception {
        // Arrange
        List<Class<?>> records = dtoRecords();

        // Act
        Map<String, List<String>> bySimpleName = new TreeMap<>();
        records.forEach(type -> bySimpleName.computeIfAbsent(type.getSimpleName(), key -> new java.util.ArrayList<>()).add(type.getName()));
        Map<String, List<String>> clashes = bySimpleName.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

        // Assert
        assertThat(clashes).as("web records that springdoc would publish as one schema").isEmpty();
    }

    @Test
    void theScanSeesNestedRecordsToo() throws Exception {
        // Arrange
        List<Class<?>> records = dtoRecords();

        // Act
        List<String> names = records.stream().map(Class::getSimpleName).toList();

        // Assert - non-vacuity: top-level and nested records are both found
        assertThat(records.size()).isGreaterThan(40);
        assertThat(names).contains("ApiError", "MemberHistoryResponse", "BookingView");
        assertThat(records).anyMatch(Class::isMemberClass);
    }
}
