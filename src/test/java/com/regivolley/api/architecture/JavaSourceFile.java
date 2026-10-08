package com.regivolley.api.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
                      TypeKind kind, String typeName, Set<String> modifiers, Set<String> implementedInterfaces,
                      String code) {

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
            Pattern.compile("\\b((?:com\\.regivolley|com\\.nimbusds|org\\.springframework|jakarta)(?:\\.\\w+)+)");

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
            return new JavaSourceFile(name, packageName, references, TypeKind.NONE, "", Set.of(), Set.of(), body);
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
                simpleNames(clause, header), body);
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

    /**
     * Whether the code (comments and strings excluded) constructs {@code simpleTypeName}: {@code new Actor(...)}, the same
     * with a package prefix ({@code new a.b.Actor(...)}), or the constructor reference {@code Actor::new}.
     */
    boolean instantiates(String simpleTypeName) {
        String type = Pattern.quote(simpleTypeName);
        return Pattern.compile("\\bnew\\s+(?:[\\w.]+\\.)?" + type + "\\s*\\(").matcher(code).find()
                || Pattern.compile("\\b" + type + "\\s*::\\s*new\\b").matcher(code).find();
    }

    /**
     * A {@code static} method declared in the file (top-level or nested type): the return type as written, generics
     * included, its name and whether it is {@code private}.
     */
    record StaticMethod(String returnType, String name, boolean isPrivate) {
    }

    // modifiers, optional method type parameters, the return type (generics allowed), the name and the opening parenthesis;
    // a static field or a static nested type has no "(" right after its name, so it never matches
    private static final Pattern STATIC_METHOD = Pattern.compile(
            "((?:\\b(?:public|protected|private|final|synchronized)\\s+)*)\\bstatic\\s+(?:(?:final|synchronized)\\s+)*"
                    + "(?:<[^()=;{]*>\\s+)?([\\w.]+(?:<[^()=;{]*>)?(?:\\[\\])?)\\s+(\\w+)\\s*\\(");

    /** Every {@code static} method declared in the file, in order of appearance. */
    List<StaticMethod> staticMethods() {
        List<StaticMethod> methods = new java.util.ArrayList<>();
        Matcher matcher = STATIC_METHOD.matcher(code);
        while (matcher.find()) {
            methods.add(new StaticMethod(matcher.group(2), matcher.group(3), matcher.group(1).contains("private")));
        }
        return methods;
    }

    /**
     * Whether the code calls a static method of {@code simpleTypeName} whose name matches {@code methodNameRegex}:
     * {@code Session.create(...)}, with or without a package prefix.
     */
    boolean callsStatic(String simpleTypeName, String methodNameRegex) {
        return Pattern.compile("\\b" + Pattern.quote(simpleTypeName) + "\\s*\\.\\s*(?:" + methodNameRegex + ")\\s*\\(")
                .matcher(code).find();
    }

    /** The simple names written before {@code .methodName(} in the code: {@code Plan} for {@code Plan.of(x)}, {@code PlanId} for {@code PlanId.generate()}. */
    List<String> qualifiersOfCallsTo(String methodName) {
        List<String> qualifiers = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("\\b(\\w+)\\s*\\.\\s*" + Pattern.quote(methodName) + "\\s*\\(").matcher(code);
        while (matcher.find()) {
            qualifiers.add(matcher.group(1));
        }
        return qualifiers;
    }

    private static final Pattern REFLECTION = Pattern.compile(
            "\\bjava\\s*\\.\\s*lang\\s*\\.\\s*(?:reflect|invoke)\\b|\\bgetDeclaredConstructors?\\b|\\bgetConstructors?\\b"
                    + "|\\bnewInstance\\b|\\bMethodHandles?\\b|\\bsetAccessible\\b|\\bClass\\s*\\.\\s*forName\\b");

    /** What in the code reaches for reflection or method handles (imports and inline uses), comments and strings excluded. */
    List<String> reflectionUses() {
        List<String> uses = new java.util.ArrayList<>();
        Matcher matcher = REFLECTION.matcher(code);
        while (matcher.find()) {
            uses.add(matcher.group().replaceAll("\\s+", ""));
        }
        return uses;
    }

    /**
     * What the top-level class declares at its own level that is not static: {@code "field name"} for an instance field,
     * {@code "method name"} for an instance method and {@code "constructor"} for a constructor that is not private. A
     * private constructor (what keeps a utility class from being instantiated), static members and nested types are fine.
     */
    List<String> nonStaticMembers() {
        List<String> found = new java.util.ArrayList<>();
        Matcher type = TYPE_DECLARATION.matcher(code);
        if (!type.find()) {
            return found;
        }
        int open = code.indexOf('{', type.end());
        if (open < 0) {
            return found;
        }
        StringBuilder header = new StringBuilder();
        boolean initialiser = false;
        int depth = 1;
        for (int i = open + 1; i < code.length() && depth > 0; i++) {
            char c = code.charAt(i);
            if (depth > 1) {
                depth += c == '{' ? 1 : c == '}' ? -1 : 0;
                if (depth == 1 && !initialiser) {
                    classify(header.toString(), found);
                    header.setLength(0);
                }
                continue;
            }
            if (c == ';') {
                classify(header.toString(), found);
                header.setLength(0);
                initialiser = false;
            } else if (c == '{') {
                initialiser = initialiser || header.indexOf("=") >= 0;
                depth++;
            } else if (c == '}') {
                depth--;
            } else {
                header.append(c);
            }
        }
        return found;
    }

    private void classify(String rawHeader, List<String> found) {
        String header = rawHeader.replaceAll("@\\w+(?:\\([^)]*\\))?", " ");
        int equals = header.indexOf('=');
        String declaration = (equals >= 0 ? header.substring(0, equals) : header).trim();
        if (declaration.isEmpty() || declaration.matches("(?s).*\\b(class|record|enum|interface)\\b.*")) {
            return;
        }
        boolean isStatic = declaration.matches("(?s).*\\bstatic\\b.*");
        int paren = declaration.indexOf('(');
        if (paren < 0) {
            if (!isStatic) {
                found.add("field " + declaration.substring(declaration.lastIndexOf(' ') + 1));
            }
            return;
        }
        String beforeParen = declaration.substring(0, paren).trim();
        String name = beforeParen.substring(beforeParen.lastIndexOf(' ') + 1);
        if (name.equals(typeName)) {
            if (!declaration.matches("(?s).*\\bprivate\\b.*")) {
                found.add("constructor");
            }
        } else if (!isStatic) {
            found.add("method " + name);
        }
    }

    /**
     * The component names of every record declared in the file, top-level and nested, by record name. Annotations on
     * components (with their own parentheses) and generics are skipped.
     */
    Map<String, List<String>> recordComponents() {
        Map<String, List<String>> records = new LinkedHashMap<>();
        Matcher declaration = Pattern.compile("\\brecord\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(").matcher(code);
        while (declaration.find()) {
            records.put(declaration.group(1), componentsFrom(declaration.end()));
        }
        return records;
    }

    /** The component names of the top-level record, in order; empty for any other kind. */
    List<String> recordComponentNames() {
        return kind == TypeKind.RECORD ? recordComponents().getOrDefault(typeName, List.of()) : List.of();
    }

    private List<String> componentsFrom(int start) {
        int depth = 1;
        StringBuilder components = new StringBuilder();
        for (int i = start; i < code.length() && depth > 0; i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '<') depth++;
            else if (c == ')' || c == '>') depth--;
            if (depth > 0) {
                // Keep top-level text only: nested parentheses (annotation arguments) and generics are blanked.
                components.append(depth == 1 && c != '(' && c != ')' && c != '<' && c != '>' ? c : ' ');
            }
        }
        return Arrays.stream(components.toString().split(","))
                .map(String::trim)
                .filter(component -> !component.isEmpty())
                .map(component -> component.substring(component.lastIndexOf(' ') + 1))
                .toList();
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
