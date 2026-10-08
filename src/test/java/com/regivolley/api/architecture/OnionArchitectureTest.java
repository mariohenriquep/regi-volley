package com.regivolley.api.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the dependency direction the whole codebase is built around: dependencies only ever
 * point inward, towards the domain (docs/architecture.md §1, §2, §3, §11). Plain JUnit 5 over the
 * production sources - no architecture library. If one of these tests fails, the layering has
 * been violated and the fix is architectural, not cosmetic.
 *
 * <p>Beyond the layer direction it freezes the DDD building blocks each package stands for
 * (docs/architecture.md section 4): value objects, aggregate roots and entities, domain services
 * and the web/persistence class kinds.
 */
class OnionArchitectureTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");

    private static final String BASE = "com.regivolley.api";
    private static final String DOMAIN = BASE + ".domain";
    private static final String APPLICATION = BASE + ".application";
    private static final String INFRASTRUCTURE = BASE + ".infrastructure";
    private static final String PERSISTENCE = INFRASTRUCTURE + ".persistence";
    private static final String JPA_ENTITIES = PERSISTENCE + ".entity";
    private static final String PERSISTENCE_MAPPERS = PERSISTENCE + ".mapper";
    private static final String SPRING_DATA_REPOSITORIES = PERSISTENCE + ".adapter";
    private static final String WEB = INFRASTRUCTURE + ".web";
    private static final String WEB_DTOS = WEB + ".dto";
    private static final String WEB_MAPPERS = WEB + ".mapper";
    private static final String WEB_CONTROLLERS = WEB + ".controller";
    private static final String WEB_EXCEPTIONS = WEB + ".exception";
    private static final String SECURITY = INFRASTRUCTURE + ".security";

    private static final String MODEL = DOMAIN + ".model";
    private static final String ENTITIES = MODEL + ".entity";
    private static final String VALUE_OBJECTS = MODEL + ".valueobject";
    private static final String RESULTS = MODEL + ".result";
    private static final String DOMAIN_SERVICES = DOMAIN + ".service";
    private static final String SHARED = DOMAIN + ".shared";
    private static final String DOMAIN_EXCEPTIONS = DOMAIN + ".exception";
    private static final String DOMAIN_PORTS = DOMAIN + ".port";
    private static final String USE_CASES = APPLICATION + ".usecase";
    private static final String IDENTITY = APPLICATION + ".identity";
    private static final String APPLICATION_EXCEPTIONS = APPLICATION + ".exception";
    private static final String APPLICATION_PORTS = APPLICATION + ".port";
    private static final String COMMANDS = APPLICATION + ".command";
    private static final String RESULTS_OF_USE_CASES = APPLICATION + ".result";
    private static final Set<String> DOMAIN_PACKAGES = Set.of(ENTITIES, VALUE_OBJECTS, RESULTS, DOMAIN_SERVICES,
            SHARED, DOMAIN_EXCEPTIONS, DOMAIN + ".repository", DOMAIN + ".port");

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

    @Test
    void everyDomainTypeSitsInAPackageOfItsBuildingBlock() {
        // Arrange
        Set<String> blocks = DOMAIN_PACKAGES;

        // Act
        List<String> violations = typesIn(DOMAIN, source -> blocks.stream().noneMatch(source::residesIn));

        // Assert
        assertThat(violations).as("domain types belong to one of " + blocks).isEmpty();
    }

    @Test
    void valueObjectsAreRecordsEnumsOrFinalClassesImplementingValueObject() {
        // Arrange
        Set<String> marker = Set.of("ValueObject");

        // Act
        List<String> violations = typesIn(VALUE_OBJECTS, source -> !isImmutableShape(source) || !source.implementsAnyOf(marker));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void valueObjectsNeverDependOnEntitiesResultsOrDomainServices() {
        // Arrange
        Set<String> forbidden = Set.of(ENTITIES, RESULTS, DOMAIN_SERVICES);

        // Act
        List<String> violations = violations(VALUE_OBJECTS, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void everyTypeInTheEntityPackageIsAnAggregateRootOrAnEntity() {
        // Arrange
        Set<String> markers = Set.of("AggregateRoot", "Entity");

        // Act
        List<String> violations = typesIn(ENTITIES, source -> !source.implementsAnyOf(markers));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void sharedKernelDependsOnNothingElseInTheProjectExceptDomainExceptions() {
        // Arrange - FieldRules throws InvalidFieldException; exceptions are the one allowed leaf.
        Set<String> forbidden = Set.of(MODEL, DOMAIN_SERVICES, DOMAIN + ".repository", DOMAIN + ".port",
                APPLICATION, INFRASTRUCTURE);

        // Act
        List<String> violations = violations(SHARED, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void domainServicesLiveOnlyInTheDomainServicePackage() {
        // Arrange
        List<String> serviceSuffixes = List.of("Service", "Eligibility", "Calculator", "Evaluator", "Specification");

        // Act
        List<String> violations = typesIn(DOMAIN, source -> !source.residesIn(DOMAIN_SERVICES)
                && serviceSuffixes.stream().anyMatch(source.typeName()::endsWith));

        // Assert
        assertThat(violations).as("names like " + serviceSuffixes + " denote a domain service").isEmpty();
    }

    @Test
    void domainServicePackageHoldsNoEntitiesOrValueObjects() {
        // Arrange
        Set<String> buildingBlocks = Set.of("AggregateRoot", "Entity", "ValueObject");

        // Act
        List<String> violations = typesIn(DOMAIN_SERVICES, source -> source.implementsAnyOf(buildingBlocks));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void outboundPortsAreInterfaces() {
        // Arrange
        List<String> portPackages = List.of(DOMAIN + ".repository", DOMAIN_PORTS, APPLICATION_PORTS);

        // Act
        List<String> violations = new ArrayList<>();
        for (String portPackage : portPackages) {
            violations.addAll(typesIn(portPackage, source -> source.kind() != JavaSourceFile.TypeKind.INTERFACE));
        }

        // Assert
        assertThat(violations).as("ports are interfaces; adapters live in infrastructure").isEmpty();
    }

    @Test
    void applicationPortsAreOnlyForWhatTheApplicationNeedsFromTheOutsideWorld() {
        // Arrange
        Set<String> forbidden = Set.of("org.springframework", "jakarta");

        // Act
        List<String> violations = violations(APPLICATION_PORTS, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void applicationNeverTouchesSpringTransactionApis() {
        // Arrange - transactions are demarcated through the TransactionRunner port (architecture.md section 10)
        Set<String> forbidden = Set.of("org.springframework.transaction", "jakarta.transaction");

        // Act
        List<String> violations = violations(APPLICATION, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void commandsAreRecordsOrEnumsAndResultsAreRecords() {
        // Arrange
        // (the application packages from the constants)

        // Act
        List<String> commandViolations = typesIn(COMMANDS, source -> source.kind() != JavaSourceFile.TypeKind.RECORD
                && source.kind() != JavaSourceFile.TypeKind.ENUM);
        List<String> resultViolations = typesIn(RESULTS_OF_USE_CASES, source -> source.kind() != JavaSourceFile.TypeKind.RECORD);

        // Assert
        assertThat(commandViolations).isEmpty();
        assertThat(resultViolations).isEmpty();
    }

    @Test
    void useCasesAreInterfacesImplementedByServicesInTheUseCasePackage() {
        // Arrange
        String useCaseSuffix = "UseCase";
        String serviceSuffix = "Service";

        // Act
        List<String> violations = new ArrayList<>(typesIn(BASE, source -> source.typeName().endsWith(useCaseSuffix)
                && (!source.residesIn(USE_CASES) || (source.kind() != JavaSourceFile.TypeKind.INTERFACE
                && !source.typeName().equals("UseCase")))));
        violations.addAll(typesIn(APPLICATION, source -> source.typeName().endsWith(serviceSuffix)
                && !source.residesIn(USE_CASES)));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void webDtosLiveOnlyInTheWebDtoPackage() {
        // Arrange - scoped to infrastructure: the domain legitimately has a JoinRequest aggregate.
        List<String> suffixes = List.of("Request", "Response", "Dto");

        // Act
        List<String> violations = typesIn(INFRASTRUCTURE, source -> !source.residesIn(WEB_DTOS)
                && suffixes.stream().anyMatch(source.typeName()::endsWith));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void domainAndApplicationDefineNoWebShapedTypes() {
        // Arrange
        List<String> suffixes = List.of("Response", "Dto");

        // Act
        List<String> violations = new ArrayList<>();
        for (String layer : List.of(DOMAIN, APPLICATION)) {
            violations.addAll(typesIn(layer, source -> suffixes.stream().anyMatch(source.typeName()::endsWith)));
        }

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void mappersLiveOnlyInTheWebOrPersistenceMapperPackages() {
        // Arrange
        String suffix = "Mapper";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix)
                && !source.residesIn(WEB_MAPPERS) && !source.residesIn(PERSISTENCE_MAPPERS));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void webMappersAndPersistenceMappersStayInTheirOwnPackage() {
        // Arrange
        String webSuffix = "WebMapper";
        String persistenceSuffix = "PersistenceMapper";

        // Act
        List<String> violations = typesIn(BASE, source ->
                (source.typeName().endsWith(webSuffix) && !source.residesIn(WEB_MAPPERS))
                        || (source.typeName().endsWith(persistenceSuffix) && !source.residesIn(PERSISTENCE_MAPPERS)));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void jpaEntitiesLiveOnlyInThePersistenceEntityPackage() {
        // Arrange
        String suffix = "JpaEntity";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix)
                && !source.residesIn(JPA_ENTITIES));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void springDataRepositoriesLiveOnlyInThePersistenceAdapterPackage() {
        // Arrange
        String suffix = "JpaRepository";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix)
                && !source.residesIn(SPRING_DATA_REPOSITORIES));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void nimbusStaysInTheSecurityPackage() {
        // Arrange
        Set<String> forbidden = Set.of("com.nimbusds");

        // Act
        List<String> violations = sources.stream()
                .filter(source -> !source.residesIn(SECURITY))
                .flatMap(source -> describe(source, forbidden).stream())
                .toList();

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void springSecurityStaysInTheSecurityPackageExceptForTheTwoExceptionsTheAdviceTranslates() {
        // Arrange
        Set<String> forbidden = Set.of("org.springframework.security");

        // Act
        List<String> violations = sources.stream()
                .filter(source -> !source.residesIn(SECURITY) && !source.residesIn(WEB_EXCEPTIONS))
                .flatMap(source -> describe(source, forbidden).stream())
                .toList();

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void theExceptionAdviceImportsOnlyTheTwoSpringSecurityExceptionsItTranslates() {
        // Arrange - a 403/401 thrown from inside a handler must keep its status instead of becoming a 500
        Set<String> allowed = Set.of("org.springframework.security.access.AccessDeniedException",
                "org.springframework.security.core.AuthenticationException");

        // Act
        List<String> violations = sources.stream()
                .filter(source -> source.residesIn(WEB_EXCEPTIONS))
                .flatMap(source -> source.referencesInto(Set.of("org.springframework.security")).stream()
                        .filter(reference -> !allowed.contains(reference))
                        .map(reference -> source.name() + " -> " + reference))
                .toList();

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void anActorIsConstructedByExactlyOneClassTheAuthenticatedActor() {
        // Arrange - the caller's identity must come from a verified token and nowhere else (threat model D-6)
        String actor = "Actor";

        // Act
        List<String> constructors = sources.stream()
                .filter(source -> source.instantiates(actor))
                .map(source -> source.packageName() + "." + source.typeName())
                .toList();

        // Assert - exactly one: proves exclusivity, and that the check is not passing vacuously
        assertThat(constructors).containsExactly(SECURITY + ".AuthenticatedActor");
    }

    @Test
    void serviceClassesLiveOnlyInTheUseCasePackageOrAsDomainServices() {
        // Arrange - the request flow is controller -> use case interface -> service in application (architecture.md section 1); a
        // "*Service" anywhere else is a second place that manages requests
        String suffix = "Service";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix)
                && !source.residesIn(USE_CASES) && !source.residesIn(DOMAIN_SERVICES));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void controllersDependOnlyOnUseCaseInterfacesCommandsResultsDtosMappersAndTheCaller() {
        // Arrange
        // (the production sources)

        // Act
        List<String> violations = controllerDependencyViolations(sources);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void theControllerDependencyRuleReallyChecksTheRealControllers() {
        // Arrange
        long controllers = sources.stream().filter(source -> source.residesIn(WEB_CONTROLLERS)).count();
        boolean authController = sources.stream().anyMatch(source -> source.name().endsWith("AuthController.java"));

        // Act
        boolean seesAUseCase = sources.stream().filter(source -> source.residesIn(WEB_CONTROLLERS))
                .anyMatch(source -> !source.referencesInto(Set.of(USE_CASES)).isEmpty());

        // Assert - guards against the rule passing because it looked at nothing
        assertThat(controllers).isGreaterThan(1);
        assertThat(authController).isTrue();
        assertThat(seesAUseCase).isTrue();
    }

    @Test
    void theControllerDependencyRuleCatchesAServiceARepositoryAnEntityAndAPersistenceClass() {
        // Arrange
        JavaSourceFile evil = JavaSourceFile.parse("web/controller/EvilController.java",
                "package com.regivolley.api.infrastructure.web.controller;\n"
                        + "import com.regivolley.api.application.usecase.LoginService;\n"
                        + "import com.regivolley.api.application.usecase.LoginUseCase;\n"
                        + "import com.regivolley.api.domain.repository.MemberRepository;\n"
                        + "import com.regivolley.api.domain.model.entity.Member;\n"
                        + "import com.regivolley.api.infrastructure.persistence.adapter.MemberRepositoryAdapter;\n"
                        + "import com.regivolley.api.infrastructure.security.CurrentActor;\n"
                        + "import com.regivolley.api.application.command.LoginCommand;\n"
                        + "class EvilController {}\n");

        // Act
        List<String> violations = controllerDependencyViolations(List.of(evil));

        // Assert - the use case interface, the command and the caller annotation pass; the other four do not
        assertThat(violations).hasSize(4);
        assertThat(violations).anyMatch(v -> v.contains("LoginService"))
                .anyMatch(v -> v.contains("MemberRepository"))
                .anyMatch(v -> v.contains("domain.model.entity.Member"))
                .anyMatch(v -> v.contains("MemberRepositoryAdapter"));
    }

    @Test
    void applicationIdentityTypesAreFreeOfFrameworks() {
        // Arrange - the identity models are plain Java like the domain: no Spring, no JPA, no servlet
        Set<String> forbidden = Set.of("org.springframework", "jakarta", "com.nimbusds");

        // Act
        List<String> violations = violations(IDENTITY, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void applicationExceptionsAndIdentityHaveNothingToDoWithPorts() {
        // Arrange - the models and exceptions are the vocabulary of the ports, never the other way round
        Set<String> forbidden = Set.of(APPLICATION_PORTS, USE_CASES);

        // Act
        List<String> violations = new ArrayList<>(violations(IDENTITY, forbidden));
        violations.addAll(violations(APPLICATION_EXCEPTIONS, forbidden));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void everyApplicationTypeSitsInAPackageOfItsKind() {
        // Arrange
        Set<String> kinds = Set.of(COMMANDS, RESULTS_OF_USE_CASES, USE_CASES, APPLICATION_PORTS, IDENTITY, APPLICATION_EXCEPTIONS);

        // Act
        List<String> violations = typesIn(APPLICATION, source -> kinds.stream().noneMatch(source::residesIn));

        // Assert
        assertThat(violations).as("application types belong to one of " + kinds).isEmpty();
    }

    @Test
    void persistenceReachesTheSecurityPackageOnlyThroughTheAccountLookupSeam() {
        // Arrange - the stores are application ports now; the one thing persistence still implements from security is the per-request lookup
        Set<String> allowed = Set.of(SECURITY + ".SecurityAccountLookup", SECURITY + ".SecurityAccount");

        // Act
        List<String> violations = sources.stream()
                .filter(source -> source.residesIn(PERSISTENCE))
                .flatMap(source -> source.referencesInto(Set.of(SECURITY)).stream()
                        .filter(reference -> !allowed.contains(reference))
                        .map(reference -> source.name() + " -> " + reference))
                .toList();

        // Assert
        assertThat(violations).isEmpty();
    }

    private static List<String> controllerDependencyViolations(List<JavaSourceFile> files) {
        return files.stream()
                .filter(source -> source.residesIn(WEB_CONTROLLERS))
                .flatMap(source -> source.referencesInto(Set.of(BASE)).stream()
                        .filter(reference -> !allowedForController(reference))
                        .map(reference -> source.name() + " -> " + reference))
                .toList();
    }

    /** What a controller may name: use case interfaces, commands, results, web DTOs and mappers, its own package, the caller annotation and principal, and the request id (the container error page prints it). */
    private static boolean allowedForController(String reference) {
        String simpleName = reference.substring(reference.lastIndexOf('.') + 1);
        return reference.startsWith(COMMANDS + ".") || reference.startsWith(RESULTS_OF_USE_CASES + ".")
                || reference.startsWith(WEB_DTOS + ".") || reference.startsWith(WEB_MAPPERS + ".")
                || reference.startsWith(WEB_CONTROLLERS + ".")
                || (reference.startsWith(USE_CASES + ".") && simpleName.endsWith("UseCase"))
                || reference.equals(SECURITY + ".CurrentActor") || reference.equals(SECURITY + ".AuthenticatedActor")
                || reference.equals(SECURITY + ".RequestIds");
    }

    @Test
    void controllersLiveOnlyInTheWebControllerPackage() {
        // Arrange
        String suffix = "Controller";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix) && !source.residesIn(WEB_CONTROLLERS));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void exceptionMappingLivesOnlyInTheWebExceptionPackage() {
        // Arrange
        String suffix = "ExceptionHandler";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix) && !source.residesIn(WEB_EXCEPTIONS));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void servletFiltersLiveOnlyInTheSecurityPackage() {
        // Arrange - by what they are, not by what they are called
        Set<String> filterTypes = Set.of("jakarta.servlet.Filter", "org.springframework.web.filter");

        // Act
        List<String> violations = sources.stream()
                .filter(source -> !source.residesIn(SECURITY))
                .flatMap(source -> describe(source, filterTypes).stream())
                .toList();

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void thePrincipalResolverLivesOnlyInTheSecurityPackage() {
        // Arrange
        String suffix = "PrincipalResolver";

        // Act
        List<String> violations = typesIn(BASE, source -> source.typeName().endsWith(suffix) && !source.residesIn(SECURITY));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void theWebLayerNeverTouchesPersistence() {
        // Arrange - controllers translate HTTP to use cases and back (architecture.md section 6); data access is not theirs
        Set<String> forbidden = Set.of(PERSISTENCE, DOMAIN + ".repository", "jakarta.persistence", "org.springframework.data");

        // Act
        List<String> violations = violations(WEB, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void noWebInputRecordCarriesATenantRoleOrVersionField() {
        // Arrange - the tenant comes from the token, never from the client (threat model D-12, rule 2); only *Response
        // records may name them (a response says who the caller is, it does not accept it)
        Set<String> forbidden = Set.of("associationId", "tenantId", "roles", "version");

        // Act
        List<String> violations = dtoRecordViolations(sources, forbidden);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void theWebInputRecordRuleReallyChecksTheRealDtos() {
        // Arrange
        Set<String> checked = sources.stream()
                .filter(source -> source.residesIn(WEB_DTOS))
                .flatMap(source -> source.recordComponents().keySet().stream())
                .filter(name -> !name.endsWith("Response"))
                .collect(Collectors.toSet());

        // Act
        boolean seesTheErrorBody = checked.contains("ApiError");

        // Assert - guards against the rule passing because it looked at nothing
        assertThat(seesTheErrorBody).isTrue();
    }

    @Test
    void theWebInputRecordRuleCatchesTopLevelAndNestedOffenders() {
        // Arrange
        JavaSourceFile topLevel = JavaSourceFile.parse("web/dto/EvilRequest.java",
                "package com.regivolley.api.infrastructure.web.dto;\nrecord EvilRequest(String name, java.util.UUID associationId) {}\n");
        JavaSourceFile nested = JavaSourceFile.parse("web/dto/OrderPayload.java",
                "package com.regivolley.api.infrastructure.web.dto;\nrecord OrderPayload(String name, java.util.List<Line> lines) {\n"
                        + "  record Line(int quantity, java.util.UUID tenantId) {}\n}\n");
        JavaSourceFile response = JavaSourceFile.parse("web/dto/WhoResponse.java",
                "package com.regivolley.api.infrastructure.web.dto;\nrecord WhoResponse(java.util.UUID associationId) {}\n");

        // Act
        List<String> violations = dtoRecordViolations(List.of(topLevel, nested, response), Set.of("associationId", "tenantId"));

        // Assert
        assertThat(violations).hasSize(2);
        assertThat(violations).anyMatch(v -> v.contains("EvilRequest") && v.contains("associationId"));
        assertThat(violations).anyMatch(v -> v.contains("Line") && v.contains("tenantId"));
    }

    private static List<String> dtoRecordViolations(List<JavaSourceFile> files, Set<String> forbiddenComponents) {
        List<String> violations = new ArrayList<>();
        for (JavaSourceFile source : files) {
            if (!source.residesIn(WEB_DTOS)) {
                continue;
            }
            source.recordComponents().forEach((record, components) -> {
                if (!record.endsWith("Response")) {
                    components.stream().filter(forbiddenComponents::contains)
                            .forEach(component -> violations.add(record + " has a component named " + component));
                }
            });
        }
        return violations;
    }

    private static boolean isImmutableShape(JavaSourceFile source) {
        return source.kind() == JavaSourceFile.TypeKind.RECORD
                || source.kind() == JavaSourceFile.TypeKind.ENUM
                || (source.kind() == JavaSourceFile.TypeKind.CLASS && source.isFinal());
    }

    /** {@code file (kind)} for every file under {@code packagePrefix} matching {@code violates}. */
    private static List<String> typesIn(String packagePrefix, Predicate<JavaSourceFile> violates) {
        return sources.stream()
                .filter(source -> source.residesIn(packagePrefix))
                .filter(source -> source.kind() != JavaSourceFile.TypeKind.NONE)
                .filter(violates)
                .map(source -> source.name() + " (" + source.kind() + ")")
                .toList();
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
