package com.regivolley.api.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A production source file reduced to what the architecture rules need: the package it lives in,
 * every package-qualified name it refers to - through imports (plain, static, wildcard) or
 * fully qualified names written inline in the code - and the shape of its top-level type (name,
 * kind, modifiers and the simple names of the interfaces it implements, or extends for an
 * interface).
 *
 * <p>Comments, string literals and the {@code package} declaration are stripped before scanning,
 * so a Javadoc mentioning {@code org.springframework} is not a dependency, while an inline
 * {@code new com.regivolley.api.infrastructure.Foo()} is.
 */
record JavaSourceFile(String name, String packageName, Set<String> references,
                      TypeKind kind, String typeName, Set<String> modifiers, Set<String> implementedInterfaces) {

    /** The kind of the top-level type a file declares; {@code NONE} for e.g. {@code package-info}. */
    enum TypeKind { CLASS, RECORD, ENUM, INTERFACE, NONE }

    // The first type declaration in the file is the top-level one: nested types come after it.
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]*((?:(?:@\\w+(?:\\([^)]*\\))?|public|protected|private|abstract|final|sealed|non-sealed|static|strictfp)\\s+)*)"
                    + "(class|record|enum|interface|@interface)\\s+(\\w+)");


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
        return withTypeShape(name, packageName, references, body);
    }

    private static JavaSourceFile withTypeShape(String name, String packageName, Set<String> references, String body) {
        Matcher type = TYPE_DECLARATION.matcher(body);
        if (!type.find()) {
            return new JavaSourceFile(name, packageName, references, TypeKind.NONE, "", Set.of(), Set.of());
        }
        Set<String> modifiers = new LinkedHashSet<>();
        for (String word : type.group(1).trim().split("\\s+")) {
            if (!word.isEmpty() && !word.startsWith("@")) {
                modifiers.add(word);
            }
        }
        String keyword = type.group(2);
        TypeKind kind = switch (keyword) {
            case "record" -> TypeKind.RECORD;
            case "enum" -> TypeKind.ENUM;
            case "class" -> TypeKind.CLASS;
            default -> TypeKind.INTERFACE;
        };
        String header = header(body, type.end());
        String clause = kind == TypeKind.INTERFACE ? "extends" : "implements";
        return new JavaSourceFile(name, packageName, references, kind, type.group(3), modifiers,
                simpleNames(clause, header));
    }

    /** The text between the type name and its opening brace, ignoring braces inside parentheses. */
    private static String header(String body, int from) {
        int depth = 0;
        for (int i = from; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '{' && depth == 0) return body.substring(from, i);
        }
        return body.substring(from);
    }

    /** Simple names listed after {@code keyword} in {@code header}, generics and packages removed. */
    private static Set<String> simpleNames(String keyword, String header) {
        Matcher clause = Pattern.compile("\\b" + keyword + "\\s+(.*?)(?=\\bpermits\\b|$)", Pattern.DOTALL)
                .matcher(withoutParenthesesAndGenerics(header));
        Set<String> names = new TreeSet<>();
        if (clause.find()) {
            Arrays.stream(clause.group(1).split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .map(entry -> entry.substring(entry.lastIndexOf('.') + 1))
                    .forEach(names::add);
        }
        return names;
    }

    private static String withoutParenthesesAndGenerics(String header) {
        StringBuilder out = new StringBuilder();
        int parens = 0;
        int angles = 0;
        for (char c : header.toCharArray()) {
            if (c == '(') parens++;
            else if (c == ')') parens--;
            else if (c == '<') angles++;
            else if (c == '>') angles--;
            else if (parens == 0 && angles == 0) out.append(c);
        }
        return out.toString();
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

    boolean implementsAnyOf(Set<String> interfaceNames) {
        return implementedInterfaces.stream().anyMatch(interfaceNames::contains);
    }

    boolean isFinal() {
        return modifiers.contains("final");
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
