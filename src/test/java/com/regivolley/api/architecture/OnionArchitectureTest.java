package com.regivolley.api.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
    private static final String FACTORIES = DOMAIN + ".factory";
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
            FACTORIES, SHARED, DOMAIN_EXCEPTIONS, DOMAIN + ".repository", DOMAIN + ".port");

    /** The names an aggregate or entity may not give a static method: how an aggregate is born is a factory's business (#34). */
    private static final Pattern CREATOR_NAMES = Pattern.compile(
            "create\\w*|reconstruct\\w*|reconstitute\\w*|register\\w*|record\\w*|renew\\w*|of|from\\w*");

    /** The only non-private static methods an aggregate or entity may declare: a finder over a collection that creates nothing. */
    private static final Set<String> ALLOWED_STATIC_METHODS = Set.of("Subscription.holdingPlaceFor");

    /** The only places a type in {@code domain.model.entity} may mint an id: a root numbers the parts it creates, never a new instance of itself. */
    private static final Map<String, String> ALLOWED_ID_GENERATION = Map.of("Session", "BookingId", "Association", "LevelId");

    /** Internal entities and the one aggregate root that creates them (a root guards its parts): {@code Session.book}, {@code Association.addLevel}. */
    private static final Map<String, String> INTERNAL_ENTITY_ROOTS = Map.of("Booking", "Session", "Level", "Association");

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
    void theControllerDependencyRuleCatchesAServiceARepositoryAnEntityAFactoryAndAPersistenceClass() {
        // Arrange
        JavaSourceFile evil = JavaSourceFile.parse("web/controller/EvilController.java",
                "package com.regivolley.api.infrastructure.web.controller;\n"
                        + "import com.regivolley.api.application.usecase.LoginService;\n"
                        + "import com.regivolley.api.application.usecase.LoginUseCase;\n"
                        + "import com.regivolley.api.domain.repository.MemberRepository;\n"
                        + "import com.regivolley.api.domain.model.entity.Member;\n"
                        + "import com.regivolley.api.domain.factory.MemberFactory;\n"
                        + "import com.regivolley.api.infrastructure.persistence.adapter.MemberRepositoryAdapter;\n"
                        + "import com.regivolley.api.infrastructure.security.CurrentActor;\n"
                        + "import com.regivolley.api.application.command.LoginCommand;\n"
                        + "class EvilController {}\n");

        // Act
        List<String> violations = controllerDependencyViolations(List.of(evil));

        // Assert - the use case interface, the command and the caller annotation pass; the other five do not
        assertThat(violations).hasSize(5);
        assertThat(violations).anyMatch(v -> v.contains("LoginService"))
                .anyMatch(v -> v.contains("domain.factory.MemberFactory"))
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

    // ------------------------------------------------------------------------------------------------ factories (#34)

    @Test
    void factoriesLiveOnlyInTheDomainFactoryPackageAndOnlyFactoriesLiveThere() {
        // Arrange - scoped to the domain: infrastructure.security has an unrelated AccessTokenDecoderFactory
        String suffix = "Factory";

        // Act
        List<String> violations = new ArrayList<>(typesIn(DOMAIN, source -> source.typeName().endsWith(suffix)
                && !source.residesIn(FACTORIES)));
        violations.addAll(typesIn(FACTORIES, source -> !source.typeName().endsWith(suffix)
                || source.kind() != JavaSourceFile.TypeKind.CLASS || !source.isFinal()));

        // Assert
        assertThat(violations).as("domain.factory holds only final *Factory classes").isEmpty();
    }

    @Test
    void thereIsOneFactoryForEveryAggregateRoot() {
        // Arrange
        Set<String> roots = sources.stream()
                .filter(source -> source.residesIn(ENTITIES) && source.implementsAnyOf(Set.of("AggregateRoot")))
                .map(JavaSourceFile::typeName)
                .collect(Collectors.toSet());

        // Act
        Set<String> factories = sources.stream()
                .filter(source -> source.residesIn(FACTORIES))
                .map(JavaSourceFile::typeName)
                .collect(Collectors.toSet());

        // Assert - non-vacuity too: there are roots, and each has exactly its own factory
        assertThat(roots).hasSizeGreaterThan(5);
        assertThat(factories).isEqualTo(roots.stream().map(root -> root + "Factory").collect(Collectors.toSet()));
    }

    @Test
    void factoriesDependOnlyOnTheModelTheSharedKernelAndDomainExceptions() {
        // Arrange - not on ports, not on domain services (a service may use a factory, never the reverse), nothing outside the domain
        List<JavaSourceFile> factories = sources.stream().filter(source -> source.residesIn(FACTORIES)).toList();

        // Act
        List<String> violations = factoryDependencyViolations(factories);
        boolean seesTheModel = factories.stream().anyMatch(source -> !source.referencesInto(Set.of(ENTITIES)).isEmpty());

        // Assert
        assertThat(violations).isEmpty();
        assertThat(seesTheModel).as("the rule must be looking at real factories").isTrue();
    }

    @Test
    void theFactoryDependencyRuleCatchesAPortAServiceAndAnInfrastructureClass() {
        // Arrange
        JavaSourceFile evil = JavaSourceFile.parse("factory/EvilFactory.java",
                "package com.regivolley.api.domain.factory;\n"
                        + "import com.regivolley.api.domain.model.entity.Plan;\n"
                        + "import com.regivolley.api.domain.exception.InvalidPlanException;\n"
                        + "import com.regivolley.api.domain.shared.FieldRules;\n"
                        + "import com.regivolley.api.domain.repository.PlanRepository;\n"
                        + "import com.regivolley.api.domain.service.PaymentLedger;\n"
                        + "import com.regivolley.api.infrastructure.config.ClockConfig;\n"
                        + "final class EvilFactory {}\n");

        // Act
        List<String> violations = factoryDependencyViolations(List.of(evil));

        // Assert - the model, the exception and the shared kernel pass; the other three do not
        assertThat(violations).hasSize(3);
        assertThat(violations).anyMatch(v -> v.contains("PlanRepository"))
                .anyMatch(v -> v.contains("PaymentLedger"))
                .anyMatch(v -> v.contains("ClockConfig"));
    }

    @Test
    void theModelTheSharedKernelAndTheExceptionsNeverDependOnFactories() {
        // Arrange - an aggregate that called its factory would make the two depend on each other
        Set<String> forbidden = Set.of(FACTORIES);

        // Act
        List<String> violations = new ArrayList<>(violations(MODEL, forbidden));
        violations.addAll(violations(SHARED, forbidden));
        violations.addAll(violations(DOMAIN_EXCEPTIONS, forbidden));

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void aggregatesAndEntitiesDeclareNoNonPrivateStaticMethodExceptTheAllowlist() {
        // Arrange
        // (the production sources)

        // Act
        List<String> violations = staticMethodViolations(sources, ALLOWED_STATIC_METHODS);
        boolean seesStaticMethods = sources.stream().anyMatch(source -> source.residesIn(VALUE_OBJECTS)
                && !source.staticMethods().isEmpty());
        boolean allowlistIsReal = sources.stream().anyMatch(source -> source.residesIn(ENTITIES)
                && source.typeName().equals("Subscription")
                && source.staticMethods().stream().anyMatch(method -> method.name().equals("holdingPlaceFor")));

        // Assert - value objects keep their own (of, defaults, ...): the scanner does read static methods, and the allowlist is not stale
        assertThat(violations).isEmpty();
        assertThat(seesStaticMethods).isTrue();
        assertThat(allowlistIsReal).isTrue();
    }

    @Test
    void theStaticMethodRuleCatchesEveryNonPrivateStaticMethodThatIsNotAllowlisted() {
        // Arrange
        JavaSourceFile plan = JavaSourceFile.parse("model/entity/Plan.java",
                "package com.regivolley.api.domain.model.entity;\n"
                        + "public final class Plan implements AggregateRoot {\n"
                        + "  public static Plan create(String name) { return null; }\n"
                        + "  static Plan duplicate(Plan p) { return null; }\n"
                        + "  public static int unrelated() { return 1; }\n"
                        + "  public static java.util.Optional<Plan> holdingPlaceFor(java.util.Collection<Plan> all) { return null; }\n"
                        + "  private static java.util.List<Booking> privateHelper() { return null; }\n"
                        + "  private static final java.util.Map<String, String> TABLE = java.util.Map.of();\n"
                        + "}\n");
        JavaSourceFile subscription = JavaSourceFile.parse("model/entity/Subscription.java",
                "package com.regivolley.api.domain.model.entity;\n"
                        + "public final class Subscription implements AggregateRoot {\n"
                        + "  public static java.util.Optional<Subscription> holdingPlaceFor(java.util.Collection<Subscription> all) { return null; }\n"
                        + "  public static Subscription create() { return null; }\n"
                        + "}\n");

        // Act
        List<String> violations = staticMethodViolations(List.of(plan, subscription), ALLOWED_STATIC_METHODS);

        // Assert - Plan: create, duplicate, unrelated and its own holdingPlaceFor (the allowlist names Subscription's); Subscription: create
        assertThat(violations).hasSize(5);
        assertThat(violations).anyMatch(v -> v.contains("Plan.java") && v.contains(" create"))
                .anyMatch(v -> v.contains("Plan.java") && v.contains("duplicate"))
                .anyMatch(v -> v.contains("Plan.java") && v.contains("unrelated"))
                .anyMatch(v -> v.contains("Plan.java") && v.contains("holdingPlaceFor"))
                .anyMatch(v -> v.contains("Subscription.java") && v.contains(" create"));
        assertThat(violations).noneMatch(v -> v.contains("privateHelper") || v.contains("TABLE"))
                .noneMatch(v -> v.contains("Subscription.java") && v.contains("holdingPlaceFor"));
    }

    @Test
    void anAggregateMintsAnIdOnlyForTheEntityItCreatesNeverForANewInstanceOfItself() {
        // Arrange
        // (the production sources)

        // Act
        List<String> violations = idGenerationViolations(sources);
        List<String> realOnes = sources.stream().filter(source -> source.residesIn(ENTITIES))
                .flatMap(source -> source.qualifiersOfCallsTo("generate").stream().map(id -> source.typeName() + "." + id))
                .toList();

        // Assert - non-vacuity: the two allowed places do mint their entities' ids
        assertThat(violations).isEmpty();
        assertThat(realOnes).containsExactlyInAnyOrder("Session.BookingId", "Association.LevelId");
    }

    @Test
    void theIdGenerationRuleCatchesARootMintingItsOwnIdOrTheWrongEntitysId() {
        // Arrange
        JavaSourceFile plan = entityFile("Plan", "AggregateRoot", "return new Plan(PlanId.generate(), null);");
        JavaSourceFile session = entityFile("Session", "AggregateRoot", "Object a = BookingId.generate(); Object b = SessionId.generate(); return null;");
        JavaSourceFile association = entityFile("Association", "AggregateRoot", "return new Level(LevelId.generate(), null, null, 0);");
        JavaSourceFile booking = entityFile("Booking", "Entity", "return new Booking(BookingId.generate());");

        // Act
        List<String> violations = idGenerationViolations(List.of(plan, session, association, booking));

        // Assert - only Session.BookingId and Association.LevelId pass
        assertThat(violations).hasSize(3);
        assertThat(violations).anyMatch(v -> v.contains("Plan") && v.contains("PlanId"))
                .anyMatch(v -> v.contains("Session") && v.contains("SessionId"))
                .anyMatch(v -> v.contains("Booking") && v.contains("BookingId"));
    }

    @Test
    void factoriesAreStatelessUtilityClasses() {
        // Arrange
        List<JavaSourceFile> factories = sources.stream().filter(source -> source.residesIn(FACTORIES)).toList();

        // Act
        List<String> violations = factories.stream()
                .flatMap(source -> source.nonStaticMembers().stream().map(member -> source.name() + " declares " + member))
                .toList();

        // Assert - no fields to hold state, no instance methods, no way to instantiate one
        assertThat(factories).isNotEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    void theStatelessFactoryRuleCatchesAFieldAnInstanceMethodAndAPublicConstructor() {
        // Arrange
        JavaSourceFile stateful = JavaSourceFile.parse("factory/PlanFactory.java",
                "package com.regivolley.api.domain.factory;\n"
                        + "public final class PlanFactory {\n"
                        + "  private final java.time.Clock clock;\n"
                        + "  public PlanFactory(java.time.Clock clock) { this.clock = clock; }\n"
                        + "  public Object create() { return null; }\n"
                        + "  public static Object fine() { return null; }\n"
                        + "}\n");
        JavaSourceFile stateless = JavaSourceFile.parse("factory/VenueFactory.java",
                "package com.regivolley.api.domain.factory;\n"
                        + "public final class VenueFactory {\n"
                        + "  private VenueFactory() {}\n"
                        + "  public static Object create() { return null; }\n"
                        + "}\n");

        // Act
        List<String> violations = List.of(stateful, stateless).stream()
                .flatMap(source -> source.nonStaticMembers().stream().map(member -> source.name() + " declares " + member))
                .toList();

        // Assert
        assertThat(violations).containsExactly("factory/PlanFactory.java declares field clock",
                "factory/PlanFactory.java declares constructor", "factory/PlanFactory.java declares method create");
    }

    @Test
    void reflectionAndMethodHandlesStayInInfrastructure() {
        // Arrange - reflection could build an aggregate without going through a factory or its constructor's callers
        long outsideInfrastructure = sources.stream().filter(source -> !source.residesIn(INFRASTRUCTURE)).count();

        // Act
        List<String> violations = reflectionViolations(sources);

        // Assert
        assertThat(outsideInfrastructure).isGreaterThan(100);
        assertThat(violations).isEmpty();
    }

    @Test
    void theReflectionRuleCatchesADomainClassAnApplicationImportAndSparesInfrastructure() {
        // Arrange
        JavaSourceFile domain = JavaSourceFile.parse("model/entity/Plan.java",
                "package com.regivolley.api.domain.model.entity;\nclass Plan { Object o = Plan.class.getDeclaredConstructor().newInstance(); }\n");
        JavaSourceFile application = JavaSourceFile.parse("usecase/CreatePlanService.java",
                "package com.regivolley.api.application.usecase;\nimport java.lang.reflect.Constructor;\nclass CreatePlanService {}\n");
        JavaSourceFile infrastructure = JavaSourceFile.parse("security/Odd.java",
                "package com.regivolley.api.infrastructure.security;\nclass Odd { Object o = Class.forName(\"x\"); }\n");

        // Act
        List<String> violations = reflectionViolations(List.of(domain, application, infrastructure));

        // Assert
        assertThat(violations).hasSize(3);
        assertThat(violations).anyMatch(v -> v.contains("Plan.java") && v.contains("newInstance"))
                .anyMatch(v -> v.contains("CreatePlanService.java") && v.contains("java.lang.reflect"))
                .noneMatch(v -> v.contains("Odd.java"));
    }

    @Test
    void aggregatesAndEntitiesAreInstantiatedOnlyByTheirFactoryTheirOwnClassOrTheirRoot() {
        // Arrange
        // (the production sources)

        // Act
        List<String> violations = instantiationViolations(sources);

        // Assert - this also covers every persistence mapper and every application service
        assertThat(violations).isEmpty();
    }

    @Test
    void theInternalEntityTableNamesEveryEntityAndItsRootInTheSamePackage() {
        // Arrange
        Set<String> entities = sources.stream()
                .filter(source -> source.residesIn(ENTITIES) && source.implementsAnyOf(Set.of("Entity")))
                .map(JavaSourceFile::typeName)
                .collect(Collectors.toSet());
        Set<String> roots = sources.stream()
                .filter(source -> source.residesIn(ENTITIES) && source.implementsAnyOf(Set.of("AggregateRoot")))
                .map(JavaSourceFile::typeName)
                .collect(Collectors.toSet());

        // Act
        Set<String> tabled = INTERNAL_ENTITY_ROOTS.keySet();

        // Assert - a new internal entity must say which root creates it, or the instantiation rule would let anyone build it
        assertThat(entities).isNotEmpty().isEqualTo(tabled);
        assertThat(roots).containsAll(INTERNAL_ENTITY_ROOTS.values());
    }

    @Test
    void everyAggregateRootAndEntityIsBuiltByItsOwnFactory() {
        // Arrange
        List<String> types = sources.stream().filter(source -> source.residesIn(ENTITIES))
                .map(JavaSourceFile::typeName).toList();

        // Act
        List<String> unbuilt = types.stream()
                .filter(type -> {
                    String factory = INTERNAL_ENTITY_ROOTS.getOrDefault(type, type) + "Factory";
                    return sources.stream().noneMatch(source -> source.residesIn(FACTORIES)
                            && source.typeName().equals(factory) && source.instantiates(type));
                })
                .toList();

        // Assert - guards the instantiation rule against passing because the factories build nothing
        assertThat(types).isNotEmpty();
        assertThat(unbuilt).isEmpty();
    }

    @Test
    void theInstantiationRuleCatchesAMapperAServiceAnotherAggregateAndTheWrongRoot() {
        // Arrange
        JavaSourceFile session = entityFile("Session", "AggregateRoot", "Booking b = new Booking(); return new Session();");
        JavaSourceFile booking = entityFile("Booking", "Entity", "return new Booking();");
        JavaSourceFile plan = entityFile("Plan", "AggregateRoot", "return new Plan();");
        JavaSourceFile association = entityFile("Association", "AggregateRoot", "return new Association();");
        JavaSourceFile sessionFactory = JavaSourceFile.parse("factory/SessionFactory.java",
                "package com.regivolley.api.domain.factory;\nfinal class SessionFactory { Object a = new Session(); Object b = new Booking(); }\n");
        JavaSourceFile mapper = JavaSourceFile.parse("mapper/SessionPersistenceMapper.java",
                "package com.regivolley.api.infrastructure.persistence.mapper;\nclass SessionPersistenceMapper { Object s = new Session(a, b); }\n");
        JavaSourceFile service = JavaSourceFile.parse("usecase/CreatePlanService.java",
                "package com.regivolley.api.application.usecase;\nclass CreatePlanService { java.util.function.Supplier<Plan> s = Plan::new; }\n");
        JavaSourceFile otherAggregate = entityFile("Association", "AggregateRoot", "return new Plan();");
        JavaSourceFile memberFactory = JavaSourceFile.parse("factory/MemberFactory.java",
                "package com.regivolley.api.domain.factory;\nfinal class MemberFactory { Object a = new Plan(); Object b = new Level(); }\n");
        JavaSourceFile levelEntity = entityFile("Level", "Entity", "return null;");
        JavaSourceFile wrongRoot = JavaSourceFile.parse("model/entity/Plan.java",
                "package com.regivolley.api.domain.model.entity;\npublic final class Plan implements AggregateRoot { Object l = new Booking(a); }\n");

        // Act
        List<String> clean = instantiationViolations(List.of(session, booking, plan, association, sessionFactory));
        List<String> violations = instantiationViolations(List.of(session, booking, otherAggregate, sessionFactory,
                mapper, service, wrongRoot, levelEntity, memberFactory));

        // Assert - the factory of the root, the class itself and the owning root are fine; another factory, a mapper, a service and the wrong root are reported
        assertThat(clean).isEmpty();
        assertThat(violations).hasSize(6);
        assertThat(violations).anyMatch(v -> v.contains("MemberFactory") && v.contains("Plan"))
                .anyMatch(v -> v.contains("MemberFactory") && v.contains("Level"))
                .anyMatch(v -> v.contains("SessionPersistenceMapper") && v.contains("Session"))
                .anyMatch(v -> v.contains("CreatePlanService") && v.contains("Plan"))
                .anyMatch(v -> v.contains("model/entity/Association.java") && v.contains("Plan"))
                .anyMatch(v -> v.contains("model/entity/Plan.java") && v.contains("Booking"));
    }

    @Test
    void persistenceMappersReconstituteAggregatesOnlyThroughFactories() {
        // Arrange
        List<JavaSourceFile> mappers = sources.stream()
                .filter(source -> source.residesIn(PERSISTENCE_MAPPERS))
                .filter(source -> !source.referencesInto(Set.of(ENTITIES)).isEmpty())
                .toList();

        // Act
        List<String> withoutFactory = mapperFactoryViolations(sources);
        List<String> staticCalls = creatorCalls(sources, PERSISTENCE_MAPPERS);

        long aggregateRoots = sources.stream()
                .filter(source -> source.residesIn(ENTITIES) && source.implementsAnyOf(Set.of("AggregateRoot"))).count();

        // Assert - one mapper per aggregate root, each going through a factory, and none calling a creator on an aggregate
        assertThat(aggregateRoots).isGreaterThan(5);
        assertThat(mappers).hasSize((int) aggregateRoots);
        assertThat(withoutFactory).isEmpty();
        assertThat(staticCalls).isEmpty();
    }

    @Test
    void theMapperRuleCatchesAMapperThatNeverAsksAFactory() {
        // Arrange
        JavaSourceFile careless = JavaSourceFile.parse("mapper/PlanPersistenceMapper.java",
                "package com.regivolley.api.infrastructure.persistence.mapper;\n"
                        + "import com.regivolley.api.domain.model.entity.Plan;\nclass PlanPersistenceMapper {}\n");
        JavaSourceFile careful = JavaSourceFile.parse("mapper/VenuePersistenceMapper.java",
                "package com.regivolley.api.infrastructure.persistence.mapper;\n"
                        + "import com.regivolley.api.domain.model.entity.Venue;\n"
                        + "import com.regivolley.api.domain.factory.VenueFactory;\nclass VenuePersistenceMapper {}\n");
        JavaSourceFile credentials = JavaSourceFile.parse("mapper/EmailLinkPersistenceMapper.java",
                "package com.regivolley.api.infrastructure.persistence.mapper;\n"
                        + "import com.regivolley.api.application.identity.EmailLink;\nclass EmailLinkPersistenceMapper {}\n");

        // Act
        List<String> violations = mapperFactoryViolations(List.of(careless, careful, credentials));

        // Assert - only the mapper that rebuilds a domain aggregate without a factory is reported
        assertThat(violations).containsExactly("mapper/PlanPersistenceMapper.java");
    }

    @Test
    void applicationServicesCreateAggregatesOnlyThroughFactories() {
        // Arrange
        List<JavaSourceFile> services = sources.stream().filter(source -> source.residesIn(USE_CASES)).toList();

        // Act
        List<String> staticCalls = creatorCalls(sources, USE_CASES);
        boolean someServiceUsesAFactory = services.stream().anyMatch(source -> !source.referencesInto(Set.of(FACTORIES)).isEmpty());

        // Assert - no aggregate is built in application (instantiation is covered above); the services that create do ask a factory
        assertThat(staticCalls).isEmpty();
        assertThat(someServiceUsesAFactory).isTrue();
    }

    @Test
    void theCreatorCallRuleCatchesAStaticCreatorOnAnAggregate() {
        // Arrange
        JavaSourceFile mapper = JavaSourceFile.parse("mapper/PlanPersistenceMapper.java",
                "package com.regivolley.api.infrastructure.persistence.mapper;\n"
                        + "class PlanPersistenceMapper { Object a = Plan.reconstruct(x); Object b = PlanFactory.reconstitute(x); }\n");
        JavaSourceFile plan = entityFile("Plan", "AggregateRoot", "return null;");

        // Act
        List<String> violations = creatorCalls(List.of(mapper, plan), PERSISTENCE_MAPPERS);

        // Assert
        assertThat(violations).hasSize(1);
        assertThat(violations).first().asString().contains("PlanPersistenceMapper").contains("Plan");
    }

    private static JavaSourceFile entityFile(String typeName, String marker, String methodBody) {
        return JavaSourceFile.parse("model/entity/" + typeName + ".java",
                "package com.regivolley.api.domain.model.entity;\npublic final class " + typeName + " implements " + marker
                        + " { Object build() { " + methodBody + " } }\n");
    }

    /** Persistence mappers that handle domain aggregates yet never name a factory. */
    private static List<String> mapperFactoryViolations(List<JavaSourceFile> files) {
        return files.stream()
                .filter(source -> source.residesIn(PERSISTENCE_MAPPERS))
                .filter(source -> !source.referencesInto(Set.of(ENTITIES)).isEmpty())
                .filter(source -> source.referencesInto(Set.of(FACTORIES)).isEmpty())
                .map(JavaSourceFile::name)
                .toList();
    }

    private static List<String> factoryDependencyViolations(List<JavaSourceFile> factories) {
        Set<String> allowed = Set.of(MODEL, SHARED, DOMAIN_EXCEPTIONS, FACTORIES);
        return factories.stream()
                .flatMap(source -> source.referencesInto(Set.of(BASE)).stream()
                        .filter(reference -> allowed.stream().noneMatch(prefix -> reference.startsWith(prefix + ".")))
                        .map(reference -> source.name() + " -> " + reference))
                .toList();
    }

    private static List<String> staticMethodViolations(List<JavaSourceFile> files, Set<String> allowlist) {
        return files.stream()
                .filter(source -> source.residesIn(ENTITIES))
                .flatMap(source -> source.staticMethods().stream()
                        .filter(method -> !method.isPrivate())
                        .filter(method -> !allowlist.contains(source.typeName() + "." + method.name()))
                        .map(method -> source.name() + " declares static " + method.returnType() + " " + method.name()))
                .toList();
    }

    private static List<String> idGenerationViolations(List<JavaSourceFile> files) {
        return files.stream()
                .filter(source -> source.residesIn(ENTITIES))
                .flatMap(source -> source.qualifiersOfCallsTo("generate").stream()
                        .filter(qualifier -> qualifier.endsWith("Id"))
                        .filter(qualifier -> !qualifier.equals(ALLOWED_ID_GENERATION.get(source.typeName())))
                        .map(qualifier -> source.name() + " mints an id with " + qualifier + ".generate()"))
                .toList();
    }

    private static List<String> reflectionViolations(List<JavaSourceFile> files) {
        return files.stream()
                .filter(source -> !source.residesIn(INFRASTRUCTURE))
                .flatMap(source -> source.reflectionUses().stream().map(use -> source.name() + " uses " + use))
                .toList();
    }

    private static List<String> instantiationViolations(List<JavaSourceFile> files) {
        List<JavaSourceFile> model = files.stream()
                .filter(source -> source.residesIn(ENTITIES) && source.kind() != JavaSourceFile.TypeKind.NONE)
                .toList();
        List<String> violations = new ArrayList<>();
        for (JavaSourceFile type : model) {
            String root = INTERNAL_ENTITY_ROOTS.getOrDefault(type.typeName(), type.typeName());
            for (JavaSourceFile file : files) {
                if (!file.instantiates(type.typeName())) {
                    continue;
                }
                boolean allowed = file.residesIn(FACTORIES)
                        ? file.typeName().equals(root + "Factory")
                        : file.residesIn(ENTITIES) && (file.typeName().equals(type.typeName()) || file.typeName().equals(root));
                if (!allowed) {
                    violations.add(file.name() + " instantiates " + type.typeName());
                }
            }
        }
        return violations;
    }

    /** Calls like {@code Session.create(...)} from the files under {@code layer} on any aggregate root or entity. */
    private static List<String> creatorCalls(List<JavaSourceFile> files, String layer) {
        List<String> types = files.stream().filter(source -> source.residesIn(ENTITIES))
                .map(JavaSourceFile::typeName).toList();
        return files.stream()
                .filter(source -> source.residesIn(layer))
                .flatMap(source -> types.stream()
                        .filter(type -> source.callsStatic(type, CREATOR_NAMES.pattern()))
                        .map(type -> source.name() + " calls a static creator of " + type))
                .toList();
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
