package com.regivolley.api.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A production source file reduced to what the architecture rules need: the package it lives in
 * and every package-qualified name it refers to - through imports (plain, static, wildcard) or
 * fully qualified names written inline in the code.
 *
 * <p>Comments, string literals and the {@code package} declaration are stripped before scanning,
 * so a Javadoc mentioning {@code org.springframework} is not a dependency, while an inline
 * {@code new com.regivolley.api.infrastructure.Foo()} is.
 */
record JavaSourceFile(String name, String packageName, Set<String> references) {

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    // Only the roots the rules care about; a qualified name is any run of dotted identifiers.
    private static final Pattern QUALIFIED_REFERENCE =
            Pattern.compile("\\b((?:com\\.regivolley|org\\.springframework|jakarta)(?:\\.\\w+)+)");

    private static final Pattern COMMENTS_AND_STRINGS = Pattern.compile(
            "//[^\\n]*|/\\*.*?\\*/|\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",
            Pattern.DOTALL);

    static JavaSourceFile parse(String name, String content) {
        String code = COMMENTS_AND_STRINGS.matcher(content).replaceAll(" ");

        Matcher declaration = PACKAGE_DECLARATION.matcher(code);
        String packageName = declaration.find() ? declaration.group(1) : "";
        String body = declaration.replaceFirst(" ");

        Set<String> references = new TreeSet<>();
        Matcher reference = QUALIFIED_REFERENCE.matcher(body);
        while (reference.find()) {
            references.add(reference.group(1));
        }
        return new JavaSourceFile(name, packageName, references);
    }

    /** Every {@code .java} file under {@code root}, parsed. */
    static List<JavaSourceFile> scan(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> parse(root.relativize(path).toString(), read(path)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean residesIn(String packagePrefix) {
        return isInside(packageName, packagePrefix);
    }

    /** References that point into any of {@code forbiddenPrefixes}, for violation messages. */
    List<String> referencesInto(Set<String> forbiddenPrefixes) {
        return references.stream()
                .filter(reference -> forbiddenPrefixes.stream().anyMatch(prefix -> isInside(reference, prefix)))
                .toList();
    }

    private static boolean isInside(String qualifiedName, String packagePrefix) {
        return qualifiedName.equals(packagePrefix) || qualifiedName.startsWith(packagePrefix + ".");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
