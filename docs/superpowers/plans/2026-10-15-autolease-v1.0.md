# AutoLease v1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the legacy leasing backend into AutoLease v1.0: client data with a fixed-seed generator, a client cabinet (web + PWA), and the «Лизинговый ассистент» agent with three tools, strict JSON answers and stored traces, running on the integration and functional testing stand `localhost:8081`.

**Architecture:** One Spring Boot app. Read side for the cabinet and the agent is a single `ClientFinanceQueryService` (plain SQL through `NamedParameterJdbcTemplate`); the agent is an own ReAct loop (`AgentOrchestrator`) over `ToolRouter` → `LlmClient` (`ollama` or `mock`) → `AgentTool`s, every step written to Postgres by `TraceRecorder`. UI is static HTML/JS served by the same app and talks only to `/api/**` with a JWT.

**Tech Stack:** Java 21, Spring Boot 3.5.6, Gradle 8.14.3 (Kotlin DSL), PostgreSQL 18, Flyway, Spring Security + JJWT, springdoc-openapi 2.8.13, networknt json-schema-validator 1.5.8, JUnit 5 + Testcontainers, Ollama (`qwen3:8b`) on the Windows host, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-14-autolease-qa-design.md` (sections 2 and 3, release v1.0).

## Global Constraints

- Build and test from the repository root in Git Bash, always with: `export JAVA_HOME="/c/Program Files/JetBrains/PyCharm2025.3/jbr"` then `./gradlew ...`. Docker Desktop must be running (Testcontainers).
- Root package `ru.bell.autoleasing`. Match existing style: Lombok (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter/@Setter`), English code, Russian user-facing messages.
- Money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Never `double` for money.
- Database statuses keep the legacy convention (Russian text written by the existing converters): payments `оплачен` / `не оплачен` / new `отменен`; contracts `активен` / `закрыт`; cars `доступен` / `в лизинге`; client type `ООО` / `ИП`. Roles in `app_users.role` are `CLIENT` / `MANAGER`.
- The client id always comes from the authenticated `AppUserPrincipal`, never from a request parameter, path of another client, or model tool arguments.
- No `Map.of` / `Set.of` / `HashMap` where iteration order reaches output (prompts, tool schemas, JSON answers): use `LinkedHashMap`, `TreeMap`, `List`.
- `APP_ENV` values: `local` (default), `test` (JUnit), `integration-testing`, `acceptance`, `production`.
- App listens on 8080 inside the container; integration and functional testing stand = host port 8081, database `autolease_it`. Ollama: `http://host.docker.internal:11434` from the container, `http://localhost:11434` from the host.
- LLM providers: `ollama` (model `qwen3:8b`) and `mock`. No paid APIs.
- v1.0 is the measured baseline (spec §3): keyword router, one attempt at JSON (no retry, no repair beyond stripping a code fence), no check that figures match tool results. Do not add such improvements — they belong to later releases.
- UI: plain HTML/CSS/JS ES modules in `src/main/resources/static`, no frameworks, no CDNs, no inline scripts (Content Security Policy `script-src 'self'`). Every interactive control and every data table has `data-testid`. Server and model text is rendered with `textContent`, never `innerHTML`.
- Write terms in full in user-facing text (no unexplained abbreviations).
- Git: one branch per story `feature/<KEY>-<slug>` from `develop`; commit messages start with the story key (`<KEY>` below = that story's Jira key); merge the story into `develop` with `--no-ff` only after `./gradlew test` passes on the merged result.
- Test credentials live only in `application.yml` (`autolease.seed.*`); never repeat them in commit messages or docs.

## Review Focus

1. Ollama not running or too slow → `POST /api/agent/ask` returns 503 with `trace_id` (no stack trace, no hang beyond the read timeout), trace status `LLM_ERROR`. Pinned in Task 12 (server error → `LlmUnavailableException`) and Task 14 (orchestrator + 503 mapping).
2. Model output that is not clean JSON (code fences, prose, missing `warnings`, numbers as strings, extra fields) → 200 with the fallback answer and warning `INVALID_MODEL_OUTPUT`, never 500. Pinned in Task 14 (`AnswerParserTest`).
3. Client without contracts (generator client 29) → cabinet endpoints return empty lists and zero totals, tools return empty results, agent still answers. Pinned in Task 8 (client 1003) and Task 10.
4. Malformed or reversed query parameters (bad date, `from` after `to`, `limit` out of range, missing parameter, non-UUID trace id) → 400 with a Russian message, never 500. Pinned in Tasks 8 and 13.
5. Invalid, expired or orphaned JWT (user deleted after login) → 401, never 500; UI then returns to the login page. Pinned in Task 5.

## Stories and branches

Before Task 1 the orchestrator runs `/release v1.0` steps 1–3 (Jira stories, QA tasks, private ledger) and fills the keys here.

| Story | Slug | Tasks | Jira key |
|---|---|---|---|
| A. Platform | `platform` | 1–3 | set at story creation |
| B. Client data | `client-data` | 4–7 | set at story creation |
| C. Client cabinet | `client-cabinet` | 8–9 | set at story creation |
| D. Leasing assistant | `leasing-assistant` | 10–14 | set at story creation |
| E. Assistant UI | `assistant-ui` | 15 | set at story creation |
| — | release check | 16 | — |

## File Structure (new code)

```
build.gradle.kts, settings.gradle.kts, gradlew*, gradle/wrapper/*, .gitattributes
docker-compose.yml, dockerfile, docker/postgres/init/01-create-databases.sql
src/main/java/ru/bell/autoleasing/
  platform/      AppInfoController, RequestIdFilter, ClockConfig, OpenApiConfig
  user/          UserRole, AppUser, AppUserRepository, AppUserPrincipal, DbUserDetailsService
  client/enums/  ClientType, ClientTypeConverter
  schedule/      PaymentScheduleCalculator, ScheduledPayment
  seed/          SeedProperties, SeedData, SeedDataGenerator, Inn, SeedDataWriter, SeedRunner
  cabinet/       ClientFinanceQueryService, CabinetController, dto/*
  agent/         AgentProperties
  agent/tool/    ToolSpec, AgentTool, ToolContext, ToolArgs, ToolArgumentException, ToolRegistry,
                 PaymentsSummaryTool, ScheduleTool, ContractsOverviewTool, ContractsOverview
  agent/router/  ToolRouter, RoutingDecision
  agent/llm/     LlmClient, LlmMessage, LlmToolCall, LlmRequest, LlmResponse, LlmUnavailableException,
                 OllamaLlmClient, MockLlmClient, LlmConfig
  agent/trace/   TraceStatus, StepType, TraceRecorder, TraceQueryService, TraceController, dto/*
  agent/core/    AgentOrchestrator, AnswerParser, SystemPrompt, AgentUnavailableException
  agent/api/     AgentController, AskRequest, AgentAnswer, Figure, AgentErrorResponse
src/main/resources/
  db/migration/V5_0__Client_data_model.sql, V6_0__Agent_traces.sql
  agent/answer-schema.json, agent/system-prompt-v1.txt
  static/  index.html, cabinet.html, traces.html, manifest.json, sw.js, css/app.css,
           js/{api,login,cabinet,chat,traces,env-badge}.js, icons/icon-{192,512}.png
src/test/java/ru/bell/autoleasing/support/  IntegrationTest, PostgresTestContainer, TestPrincipals
src/test/resources/  application-test.yml, docker-java.properties, sql/cleanup.sql, sql/finance-fixture.sql
```

---

## Story A — Platform

### Task 1: Gradle build and test infrastructure

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `.gitattributes`
- Create: `src/test/java/ru/bell/autoleasing/support/PostgresTestContainer.java`, `src/test/java/ru/bell/autoleasing/support/IntegrationTest.java`, `src/test/java/ru/bell/autoleasing/ApplicationContextTest.java`
- Create: `src/test/resources/application-test.yml`, `src/test/resources/docker-java.properties`
- Modify: `dockerfile`, `.gitignore`
- Delete: `pom.xml`

**Interfaces:**
- Produces: `@IntegrationTest` meta-annotation (Spring Boot test + profile `test` + Postgres container + MockMvc) used by every later integration test; Gradle tasks `test`, `bootJar` (jar name `autolease.jar`); build info (`BuildProperties`, version `1.0.0`).

- [ ] **Step 1: Create the branch**

```bash
git checkout develop && git checkout -b feature/<KEY>-platform
```

- [ ] **Step 2: Generate the Gradle wrapper (no local Gradle needed)**

```bash
SCR="$(mktemp -d)" && cd "$SCR" && touch settings.gradle.kts \
 && MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W)":/w -w /w gradle:8.14.3-jdk21 gradle wrapper --gradle-version 8.14.3 --distribution-type bin \
 && cp -r gradlew gradlew.bat gradle /c/Other/Projects/Java/leasingpublic/ && cd /c/Other/Projects/Java/leasingpublic
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` exist in the repository root.

- [ ] **Step 3: Write `.gitattributes`** (the container build fails on CRLF line endings)

```
gradlew text eol=lf
*.sh text eol=lf
*.bat text eol=crlf
*.jar binary
*.png binary
*.p12 binary
```

- [ ] **Step 4: Write `settings.gradle.kts`**

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "autolease"
```

- [ ] **Step 5: Write `build.gradle.kts`** (same dependencies as `pom.xml`, plus test containers)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.bell"
version = "1.0.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

val lombokVersion = "1.18.38"
val mapstructVersion = "1.6.3"
val jjwtVersion = "0.12.3"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("org.ehcache:ehcache::jakarta")
    implementation("org.springframework.data:spring-data-envers")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework:spring-aspects")
    implementation("org.springframework.retry:spring-retry")
    implementation("commons-validator:commons-validator:1.10.0")
    implementation("com.google.guava:guava:33.5.0-jre")
    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot { buildInfo() }

tasks.withType<Test> { useJUnitPlatform() }
tasks.bootJar { archiveFileName.set("autolease.jar") }
tasks.jar { enabled = false }
```

Check against `pom.xml` before deleting it: every `<dependency>` there must have a line above (scopes: `test` → `testImplementation`, `runtime` → `runtimeOnly`, `provided` lombok → `compileOnly` + `annotationProcessor`). Then `git rm pom.xml`.

- [ ] **Step 6: Test infrastructure**

`src/test/resources/docker-java.properties` (Docker Engine 29 rejects the old default client API version):

```
api.version=1.44
```

`src/test/resources/application-test.yml`:

```yaml
autolease:
  app-env: test
  clock:
    fixed-date: 2026-10-10
  seed:
    enabled: false
  agent:
    llm:
      provider: mock

spring:
  kafka:
    bootstrap-servers: localhost:65535
    listener:
      auto-startup: false
    admin:
      auto-create: false
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: false
          use_query_cache: false
      jakarta:
        persistence:
          sharedCache:
            mode: NONE

```

(`autolease.*` keys are read by code added in later tasks; unknown keys are harmless now.)

`src/test/java/ru/bell/autoleasing/support/PostgresTestContainer.java`:

```java
package ru.bell.autoleasing.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
```

`src/test/java/ru/bell/autoleasing/support/IntegrationTest.java`:

```java
package ru.bell.autoleasing.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
public @interface IntegrationTest {
}
```

- [ ] **Step 7: Write the smoke test**

`src/test/java/ru/bell/autoleasing/ApplicationContextTest.java`:

```java
package ru.bell.autoleasing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ApplicationContextTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextStartsAndFlywayCreatesLegacyTables() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'autoleasing_core'",
                Integer.class);
        assertThat(tables).isGreaterThanOrEqualTo(4);
    }
}
```

- [ ] **Step 8: Run it**

Run: `export JAVA_HOME="/c/Program Files/JetBrains/PyCharm2025.3/jbr" && ./gradlew test`
Expected: BUILD SUCCESSFUL, 1 test passed. If the legacy context fails to start, fix only what blocks startup in the test profile and list each fix in the report.

- [ ] **Step 9: Dockerfile for Gradle**

Replace `dockerfile` with:

```dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/autolease.jar ./app.jar
RUN groupadd -r app && useradd -r -g app app && mkdir -p /app/logs && chown -R app:app /app
USER app
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
```

Append to `.gitignore`: `.gradle/` and `logs/`.

Run: `docker build -t autolease:1.0.0 .`
Expected: image builds.

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "<KEY> Move build to Gradle, add Testcontainers test setup"
```

---

### Task 2: Runtime configuration — environment, request id, JSON logs, Swagger

**Files:**
- Create: `src/main/java/ru/bell/autoleasing/platform/AppInfoController.java`, `RequestIdFilter.java`, `ClockConfig.java`, `OpenApiConfig.java`
- Modify: `src/main/resources/application.yml`, `AutoLeasingApplication.java`, `config/security/SecurityConfig.java`, `threads/schedulers/contract/DatabaseConsistencyScheduler.java`, `build.gradle.kts`
- Delete: `src/main/resources/logback.xml`, `utilities/DatabaseInit.java`
- Test: `src/test/java/ru/bell/autoleasing/platform/PlatformEndpointsTest.java`

**Interfaces:**
- Consumes: `@IntegrationTest` (Task 1).
- Produces: `GET /api/public/info` → `{"appEnv": String, "version": String}` (public); `RequestIdFilter.HEADER = "X-Request-Id"`, `RequestIdFilter.MDC_KEY = "requestId"`; bean `java.time.Clock` (zone `Europe/Moscow`, fixed when `autolease.clock.fixed-date` is set); property `autolease.app-env`; Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs` with bearer scheme `bearerAuth`.

- [ ] **Step 1: Write the failing test**

`src/test/java/ru/bell/autoleasing/platform/PlatformEndpointsTest.java`:

```java
package ru.bell.autoleasing.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PlatformEndpointsTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    Clock clock;

    @Test
    void infoIsPublicAndShowsEnvironmentAndVersion() throws Exception {
        mockMvc.perform(get("/api/public/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appEnv").value("test"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/public/info"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void echoesValidRequestId() throws Exception {
        mockMvc.perform(get("/api/public/info").header(RequestIdFilter.HEADER, "qa-check-001"))
                .andExpect(header().string(RequestIdFilter.HEADER, "qa-check-001"));
    }

    @Test
    void replacesInvalidRequestId() throws Exception {
        mockMvc.perform(get("/api/public/info").header(RequestIdFilter.HEADER, "bad id <script>"))
                .andExpect(header().string(RequestIdFilter.HEADER, not("bad id <script>")))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void openApiIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AutoLease API"));
    }

    @Test
    void clockIsFixedInTests() {
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 10));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*PlatformEndpointsTest"`
Expected: FAIL (no `RequestIdFilter`, compilation error).

- [ ] **Step 3: Implement**

Add to `build.gradle.kts` dependencies: `implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")`.

`platform/RequestIdFilter.java`:

```java
package ru.bell.autoleasing.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

`platform/AppInfoController.java`:

```java
package ru.bell.autoleasing.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class AppInfoController {
    private final AppInfo info;

    public AppInfoController(@Value("${autolease.app-env}") String appEnv, ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        this.info = new AppInfo(appEnv, properties != null ? properties.getVersion() : "dev");
    }

    @GetMapping("/info")
    public AppInfo info() {
        return info;
    }

    public record AppInfo(String appEnv, String version) {
    }
}
```

`platform/ClockConfig.java`:

```java
package ru.bell.autoleasing.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${autolease.timezone:Europe/Moscow}") String zone,
                       @Value("${autolease.clock.fixed-date:}") String fixedDate) {
        ZoneId zoneId = ZoneId.of(zone);
        if (!fixedDate.isBlank()) {
            return Clock.fixed(LocalDate.parse(fixedDate).atTime(12, 0).atZone(zoneId).toInstant(), zoneId);
        }
        return Clock.system(zoneId);
    }
}
```

`platform/OpenApiConfig.java`:

```java
package ru.bell.autoleasing.platform;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI autoleaseOpenApi(ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        return new OpenAPI()
                .info(new Info().title("AutoLease API").version(properties != null ? properties.getVersion() : "dev"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

`AutoLeasingApplication.java` — remove the `DatabaseInit` call and import, add `@ConfigurationPropertiesScan`:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class AutoLeasingApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutoLeasingApplication.class, args);
    }
}
```

Delete `utilities/DatabaseInit.java` (databases are created by the Postgres init script in Task 3) and `src/main/resources/logback.xml`.

`DatabaseConsistencyScheduler` — add above the class:

```java
@ConditionalOnProperty(name = "autolease.legacy.contract-file-sync.enabled", havingValue = "true")
```

`SecurityConfig` (minimal edit; Task 5 rewrites it): in the API chain add `.requestMatchers("/api/public/**").permitAll()` next to `/api/auth/**`; in the web chain add `.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()` before `anyRequest()`.

`application.yml` — apply these changes, keep every other existing key as is:

```yaml
autolease:
  app-env: ${APP_ENV:local}
  timezone: Europe/Moscow
  legacy:
    contract-file-sync:
      enabled: false

jwt:
  secret: ${JWT_SECRET:13355c453eccbc036f706bd46032ffbe75b8863fccb29efedbc48c169a24b800}
  expiration: 86400000

server:
  port: ${SERVER_PORT:8080}
  ssl:
    enabled: ${SERVER_SSL_ENABLED:false}
    key-store: 'classpath:keystore.p12'
    key-store-password: password
    key-store-type: PKCS12
    key-alias: tomcat

spring:
  application:
    name: autolease
  kafka:
    bootstrap-servers: localhost:9092      # container sets SPRING_KAFKA_BOOTSTRAP_SERVERS
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/autolease_local}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:1234}

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  swagger-ui:
    path: /swagger-ui.html

logging:
  structured:
    format:
      console: logstash
    json:
      add:
        app: autolease
        env: ${APP_ENV:local}
  level:
    ru.bell.autoleasing: DEBUG
```

Remove the `main.db.autoinit` key. Keep the existing `logging.level` entries for Kafka and Hibernate under the same `logging.level` block.

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "<KEY> Add environment info endpoint, request id, JSON logs, Swagger"
```

---

### Task 3: Docker Compose core profile and the integration and functional testing stand

**Files:**
- Modify: `docker-compose.yml` (rewrite)
- Create: `docker/postgres/init/01-create-databases.sql`

**Interfaces:**
- Consumes: image built from `dockerfile` (Task 1), env-driven config (Task 2).
- Produces: `docker compose --profile core up -d --build` → Postgres `localhost:5433` (databases `autolease_local`, `autolease_it`, `autolease_acceptance`, `autolease_production`), Kafka `localhost:9092` (inside network `kafka:29092`), Kafdrop `localhost:9000`, stand `autolease-it` at `localhost:8081`.

- [ ] **Step 1: Postgres init script** `docker/postgres/init/01-create-databases.sql`

```sql
CREATE DATABASE autolease_local;
CREATE DATABASE autolease_it;
CREATE DATABASE autolease_acceptance;
CREATE DATABASE autolease_production;
```

- [ ] **Step 2: Rewrite `docker-compose.yml`**

```yaml
name: autolease

services:
  db:
    profiles: [core]
    image: postgres:18-alpine
    container_name: autolease-db
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: 1234
    ports:
      - "5433:5432"
    volumes:
      - pgdata:/var/lib/postgresql
      - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  kafka:
    profiles: [core]
    image: apache/kafka:4.1.0
    container_name: broker1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: CONTROLLER://0.0.0.0:29093,BROKER://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: BROKER://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka:29093"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,BROKER:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: BROKER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
      CLUSTER_ID: AkU3OEVBNTcwATJENDM2Qa
    volumes:
      - kafka-data:/var/lib/kafka/data
    restart: unless-stopped

  kafdrop:
    profiles: [core]
    image: obsidiandynamics/kafdrop:4.2.0
    container_name: kafdrop
    ports:
      - "9000:9000"
    environment:
      KAFKA_BROKERCONNECT: "kafka:29092"
      JVM_OPTS: "-Xms64M -Xmx256M"
    depends_on:
      - kafka

  app-it:
    profiles: [core]
    build:
      context: .
      dockerfile: dockerfile
    image: autolease:${AUTOLEASE_IT_VERSION:-1.0.0}
    pull_policy: never
    container_name: autolease-it
    ports:
      - "8081:8080"
    environment:
      APP_ENV: integration-testing
      DB_URL: jdbc:postgresql://db:5432/autolease_it
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      AGENT_LLM_PROVIDER: ollama
      OLLAMA_BASE_URL: http://host.docker.internal:11434
      SEED_ENABLED: "true"
    extra_hosts:
      - "host.docker.internal:host-gateway"
    depends_on:
      db:
        condition: service_healthy
      kafka:
        condition: service_started
    restart: unless-stopped

volumes:
  pgdata:
  kafka-data:
```

(`AGENT_LLM_PROVIDER`, `OLLAMA_BASE_URL`, `SEED_ENABLED` are read by code from Tasks 7 and 12.)

- [ ] **Step 3: Start and verify**

```bash
docker compose --profile core up -d --build
sleep 40
curl -s localhost:8081/api/public/info
docker logs autolease-it 2>&1 | tail -n 3
curl -s -o /dev/null -w "%{http_code}\n" localhost:8081/swagger-ui.html
```

Expected: `{"appEnv":"integration-testing","version":"1.0.0"}`; log lines are JSON objects containing `"env":"integration-testing"`; Swagger returns `200` (or `302` to `/swagger-ui/index.html`).

- [ ] **Step 4: Commit and merge story A**

```bash
git add -A && git commit -m "<KEY> Compose core profile with the integration and functional testing stand"
export JAVA_HOME="/c/Program Files/JetBrains/PyCharm2025.3/jbr" && ./gradlew test
git checkout develop && git merge --no-ff feature/<KEY>-platform -m "<KEY> Merge platform" && ./gradlew test
```

---

## Story B — Client data

### Task 4: Data model migration and entities

**Files:**
- Create: `src/main/resources/db/migration/V5_0__Client_data_model.sql`
- Create: `client/enums/ClientType.java`, `client/enums/ClientTypeConverter.java`
- Modify: `client/model/Client.java`, `client/model/dto/ClientDto.java`, `contract/model/Contract.java`, `payment/model/Payment.java`, `payment/enums/PaymentStatus.java`
- Create (test): `src/test/resources/sql/cleanup.sql`, `src/test/java/ru/bell/autoleasing/client/ClientDataModelTest.java`

**Interfaces:**
- Produces: columns `clients.client_type/inn/legal_name`, `contracts.contract_number/start_date`, `payments.due_date/paid_date/principal_part/markup/vat/penalty`; table `autoleasing_core.app_users(id, username, password_hash, role, client_id, enabled)`; `PaymentStatus.CANCELLED` (`"отменен"`); `ClientType.LLC` (`"ООО"`), `ClientType.INDIVIDUAL_ENTREPRENEUR` (`"ИП"`) with `getDescription()` and `fromString(String)`; entity getters/setters `Client.getClientType/getInn/getLegalName`, `Contract.getContractNumber/getStartDate`, `Payment.getDueDate/getPaidDate/getPrincipalPart/getMarkup/getVat/getPenalty`; test script `/sql/cleanup.sql`.

- [ ] **Step 1: Create the branch**

```bash
git checkout develop && git checkout -b feature/<KEY>-client-data
```

- [ ] **Step 2: Write the failing test**

`src/test/resources/sql/cleanup.sql`:

```sql
TRUNCATE autoleasing_core.app_users, autoleasing_core.payments, autoleasing_core.contracts,
         autoleasing_core.clients, autoleasing_core.cars RESTART IDENTITY CASCADE;
TRUNCATE autoleasing_core_history.payments_aud, autoleasing_core_history.contracts_aud,
         autoleasing_core_history.clients_aud, autoleasing_core_history.cars_aud,
         autoleasing_core_history.revinfo CASCADE;
```

`src/test/java/ru/bell/autoleasing/client/ClientDataModelTest.java`:

```java
package ru.bell.autoleasing.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.client.repository.ClientRepository;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientDataModelTest {

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesLegalDataAndWritesAuditRow() {
        Client client = new Client();
        client.setName("Смирнов Андрей Петрович");
        client.setPassport("4510000001");
        client.setPhone("+79000000001");
        client.setClientType(ClientType.INDIVIDUAL_ENTREPRENEUR);
        client.setInn("500100000199");
        client.setLegalName("ИП Смирнов А. П.");

        Long id = clientRepository.save(client).getId();

        assertThat(jdbc.queryForObject("SELECT client_type FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ИП");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core_history.clients_aud WHERE id = ? AND inn = '500100000199'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void clientTypeDefaultsToLimitedLiabilityCompany() {
        Client client = new Client();
        client.setName("Козлов Роман Игоревич");
        client.setPassport("4510000003");
        client.setPhone("+79000000003");

        Long id = clientRepository.save(client).getId();

        assertThat(jdbc.queryForObject("SELECT client_type FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ООО");
    }

    @Test
    void cancelledPaymentStatusIsStoredAsRussianText() {
        assertThat(PaymentStatus.fromString("отменен")).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(PaymentStatus.CANCELLED.toString()).isEqualTo("отменен");
    }

    @Test
    void clientRoleRequiresClientId() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO autoleasing_core.app_users (username, password_hash, role) VALUES ('nobody', 'x', 'CLIENT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests "*ClientDataModelTest"`
Expected: FAIL (compilation: `ClientType` missing).

- [ ] **Step 4: Migration** `V5_0__Client_data_model.sql`

```sql
ALTER TABLE autoleasing_core.clients
    ADD COLUMN client_type VARCHAR(10) NOT NULL DEFAULT 'ООО',
    ADD COLUMN inn VARCHAR(12),
    ADD COLUMN legal_name VARCHAR(300);
CREATE UNIQUE INDEX ux_clients_inn ON autoleasing_core.clients (inn);

ALTER TABLE autoleasing_core.contracts
    ADD COLUMN contract_number VARCHAR(30),
    ADD COLUMN start_date DATE;
CREATE UNIQUE INDEX ux_contracts_number ON autoleasing_core.contracts (contract_number);

ALTER TABLE autoleasing_core.payments
    ADD COLUMN due_date DATE,
    ADD COLUMN paid_date DATE,
    ADD COLUMN principal_part DECIMAL(20, 2) NOT NULL DEFAULT 0,
    ADD COLUMN markup DECIMAL(20, 2) NOT NULL DEFAULT 0,
    ADD COLUMN vat DECIMAL(20, 2) NOT NULL DEFAULT 0,
    ADD COLUMN penalty DECIMAL(20, 2) NOT NULL DEFAULT 0;
CREATE INDEX idx_payments_due_date ON autoleasing_core.payments (due_date);
CREATE INDEX idx_payments_paid_date ON autoleasing_core.payments (paid_date);

ALTER TABLE autoleasing_core_history.clients_aud
    ADD COLUMN client_type VARCHAR(10),
    ADD COLUMN inn VARCHAR(12),
    ADD COLUMN legal_name VARCHAR(300);
ALTER TABLE autoleasing_core_history.contracts_aud
    ADD COLUMN contract_number VARCHAR(30),
    ADD COLUMN start_date DATE;
ALTER TABLE autoleasing_core_history.payments_aud
    ADD COLUMN due_date DATE,
    ADD COLUMN paid_date DATE,
    ADD COLUMN principal_part DECIMAL,
    ADD COLUMN markup DECIMAL,
    ADD COLUMN vat DECIMAL,
    ADD COLUMN penalty DECIMAL;

CREATE TABLE autoleasing_core.app_users (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CLIENT', 'MANAGER')),
    client_id     BIGINT REFERENCES autoleasing_core.clients (id),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_app_users_client CHECK ((role = 'CLIENT') = (client_id IS NOT NULL))
);
```

- [ ] **Step 5: Enums and entities**

`client/enums/ClientType.java` — same shape as `PaymentStatus` (description, `@JsonCreator fromString`, `@JsonValue toString`):

```java
package ru.bell.autoleasing.client.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ClientType {
    LLC("ООО"),
    INDIVIDUAL_ENTREPRENEUR("ИП");

    private final String description;

    ClientType(String description) {
        this.description = description;
    }

    @JsonCreator
    public static ClientType fromString(String text) {
        for (ClientType type : values()) {
            if (type.description.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Не найден ClientType: " + text);
    }

    @JsonValue
    @Override
    public String toString() {
        return description;
    }
}
```

`client/enums/ClientTypeConverter.java` — copy of `PaymentStatusConverter` for `ClientType`.

`PaymentStatus` — add `CANCELLED("отменен")` after `UNPAID`.

`Client` — add:

```java
    @Column(name = "client_type", nullable = false, length = 10)
    @Convert(converter = ClientTypeConverter.class)
    private ClientType clientType;

    @Column(name = "inn", length = 12)
    private String inn;

    @Column(name = "legal_name", length = 300)
    private String legalName;

    @PrePersist
    void applyDefaults() {
        if (clientType == null) {
            clientType = ClientType.LLC;
        }
    }
```

`ClientDto` — add optional components `ClientType clientType, String inn, String legalName` at the end (MapStruct maps them by name); fix every `new ClientDto(...)` call site.

`Contract` — add `@Column(name = "contract_number", length = 30) private String contractNumber;` and `@Column(name = "start_date") private LocalDate startDate;`; on the `payments` list add `@OrderBy("dueDate ASC")`.

`Payment` — add:

```java
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "principal_part", precision = 20, scale = 2, nullable = false)
    private BigDecimal principalPart;

    @Column(name = "markup", precision = 20, scale = 2, nullable = false)
    private BigDecimal markup;

    @Column(name = "vat", precision = 20, scale = 2, nullable = false)
    private BigDecimal vat;

    @Column(name = "penalty", precision = 20, scale = 2, nullable = false)
    private BigDecimal penalty;

    @PrePersist
    void applyDefaults() {
        if (principalPart == null) principalPart = BigDecimal.ZERO.setScale(2);
        if (markup == null) markup = BigDecimal.ZERO.setScale(2);
        if (vat == null) vat = BigDecimal.ZERO.setScale(2);
        if (penalty == null) penalty = BigDecimal.ZERO.setScale(2);
    }
```

`Client`, `Contract` and `Payment` use `@AllArgsConstructor`, and `fromFileString` / mappers call those constructors: after adding fields, update every call site (pass `null` for the new fields) until `./gradlew compileJava` is green. Do not change `toFileString` formats.

- [ ] **Step 6: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "<KEY> Add client legal data, payment dates and components, application users"
```

---

### Task 5: Database users, roles CLIENT and MANAGER, security rewrite

**Files:**
- Create: `user/UserRole.java`, `user/AppUser.java`, `user/AppUserRepository.java`, `user/AppUserPrincipal.java`, `user/DbUserDetailsService.java`
- Modify: `config/security/SecurityConfig.java` (rewrite), `config/security/UserDetailConfig.java`, `config/security/JwtAuthenticationFilter.java`, `auth/jwt/login/AuthenticationController.java`, `auth/jwt/login/dto/AuthenticationResponse.java`, `application.yml`, `build.gradle.kts`
- Delete: `auth/login/LoginController.java`, `src/main/resources/templates/`, `src/main/resources/static/css/main.css`
- Test: `src/test/java/ru/bell/autoleasing/support/TestPrincipals.java`, `src/test/java/ru/bell/autoleasing/user/AuthenticationFlowTest.java`

**Interfaces:**
- Consumes: table `app_users` (Task 4).
- Produces: `record AppUserPrincipal(Long id, String username, String passwordHash, UserRole role, Long clientId, boolean enabled) implements UserDetails` (resolved by `@AuthenticationPrincipal`); `enum UserRole { CLIENT, MANAGER }`; `AppUserRepository.findByUsername(String): Optional<AppUser>`; `POST /api/auth/login` → `{"token": String, "role": "CLIENT"|"MANAGER"}`; access rules: `/api/auth/**`, `/api/public/**` public; `/api/me/**`, `/api/agent/ask` → CLIENT; every other `/api/**` → MANAGER; anonymous API call → 401; everything outside `/api/**` public; test helper `TestPrincipals.client(long clientId)`, `TestPrincipals.manager()`.

- [ ] **Step 1: Write the failing test**

`support/TestPrincipals.java`:

```java
package ru.bell.autoleasing.support;

import ru.bell.autoleasing.user.AppUserPrincipal;
import ru.bell.autoleasing.user.UserRole;

public final class TestPrincipals {
    private TestPrincipals() {
    }

    public static AppUserPrincipal client(long clientId) {
        return new AppUserPrincipal(clientId, "client" + clientId, "not-used", UserRole.CLIENT, clientId, true);
    }

    public static AppUserPrincipal manager() {
        return new AppUserPrincipal(9999L, "manager", "not-used", UserRole.MANAGER, null, true);
    }
}
```

`user/AuthenticationFlowTest.java`:

```java
package ru.bell.autoleasing.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthenticationFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private void createManager(String username, String password) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(UserRole.MANAGER);
        user.setEnabled(true);
        users.save(user);
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("token").asText();
    }

    @Test
    void managerLogsInAndReceivesRole() throws Exception {
        createManager("manager_t", "Secret123!");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager_t\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        createManager("manager_w", "Secret123!");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager_w\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOpensManagerApi() throws Exception {
        createManager("manager_k", "Secret123!");
        String token = login("manager_k", "Secret123!");
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousApiCallIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIsUnauthorized() throws Exception {
        createManager("manager_d", "Secret123!");
        String token = login("manager_d", "Secret123!");
        users.deleteAll();
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotUseManagerApi() throws Exception {
        mockMvc.perform(get("/api/clients").with(user(TestPrincipals.client(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staticResourcesArePublic() throws Exception {
        mockMvc.perform(get("/api/public/info")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*AuthenticationFlowTest"`
Expected: FAIL (compilation: `AppUser` missing).

- [ ] **Step 3: Implement users**

`user/UserRole.java`: `public enum UserRole { CLIENT, MANAGER }`

`user/AppUser.java`:

```java
package ru.bell.autoleasing.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_users", schema = "autoleasing_core")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "client_id")
    private Long clientId;

    @Column(nullable = false)
    private boolean enabled = true;
}
```

`user/AppUserRepository.java`: `public interface AppUserRepository extends JpaRepository<AppUser, Long> { Optional<AppUser> findByUsername(String username); }`

`user/AppUserPrincipal.java`:

```java
package ru.bell.autoleasing.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AppUserPrincipal(Long id, String username, String passwordHash, UserRole role, Long clientId,
                               boolean enabled) implements UserDetails {

    public static AppUserPrincipal from(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(),
                user.getClientId(), user.isEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
```

`user/DbUserDetailsService.java`:

```java
package ru.bell.autoleasing.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));
    }
}
```

`UserDetailConfig` — keep only the `PasswordEncoder` bean (remove the in-memory users and the `security.admin.*` fields).

- [ ] **Step 4: Rewrite `SecurityConfig`**

```java
package ru.bell.autoleasing.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; "
            + "manifest-src 'self'; worker-src 'self'";

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/public/**").permitAll()
                        .requestMatchers("/api/me/**", "/api/agent/ask").hasRole("CLIENT")
                        .anyRequest().hasRole("MANAGER"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)));
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

`JwtAuthenticationFilter` — wrap `this.userDetailsService.loadUserByUsername(username)` so that `UsernameNotFoundException` logs `warn("Пользователь из JWT не найден: {}")` and continues the chain unauthenticated (`filterChain.doFilter(request, response); return;`). If `garbageTokenIsUnauthorized` gets 500, widen the existing token-parsing `catch` to `io.jsonwebtoken.JwtException | IllegalArgumentException` (same handling: log, continue unauthenticated).

`AuthenticationResponse` — fields `token`, `role`. In `AuthenticationController` after `loadUserByUsername`: `String role = ((AppUserPrincipal) user).role().name(); return ResponseEntity.ok(new AuthenticationResponse(jwtToken, role));`

Remove from `application.yml`: `security.use_jwt`, `security.use_csrf`, `security.admin.*` (keep `security.rate-limit`). Remove `spring-boot-starter-thymeleaf` from `build.gradle.kts`. Delete `LoginController`, `templates/`, `static/css/main.css`.

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "<KEY> Store users in the database with roles CLIENT and MANAGER"
```

---

### Task 6: Payment schedule calculator and contract creation with dates and components

**Files:**
- Create: `schedule/ScheduledPayment.java`, `schedule/PaymentScheduleCalculator.java`
- Modify: `payment/service/PaymentServiceImpl.java` (`createPaymentsForContract`), `contract/service/ContractServiceImpl.java` (`save`, `validateAndFillContract`, `updatePaymentStatus`)
- Test: `src/test/java/ru/bell/autoleasing/schedule/PaymentScheduleCalculatorTest.java`, `src/test/java/ru/bell/autoleasing/payment/PaymentCreationTest.java`

**Interfaces:**
- Consumes: `AnnuityUtilities.calculateMonthlyAnnuity(BigDecimal, BigDecimal, int)`, `AnnuityUtilities.getCreditAmount(Car, BigDecimal)`, `Clock` bean, entity fields from Task 4.
- Produces: `record ScheduledPayment(int number, LocalDate dueDate, BigDecimal amount, BigDecimal principalPart, BigDecimal markup, BigDecimal vat)`; `PaymentScheduleCalculator.calculate(BigDecimal financedAmount, BigDecimal annualRatePercent, int months, LocalDate startDate): List<ScheduledPayment>`. Rules: `amount` = annuity, last payment absorbs rounding; interest of month = round(balance × rate / 1200); `vat` = round(amount × 20 / 120); `markup` = round(interest × 100 / 120); `principalPart` = amount − vat − markup; `dueDate` = `startDate.plusMonths(number)`.

- [ ] **Step 1: Write the failing test**

```java
package ru.bell.autoleasing.schedule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentScheduleCalculatorTest {

    private static BigDecimal sum(List<ScheduledPayment> schedule) {
        return schedule.stream().map(ScheduledPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void firstAndLastPaymentOfTwelveMonths() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("1200000.00"), new BigDecimal("12"), 12, LocalDate.of(2024, 1, 31));

        assertThat(s).hasSize(12);
        ScheduledPayment first = s.get(0);
        assertThat(first.amount()).isEqualByComparingTo("106618.55");
        assertThat(first.principalPart()).isEqualByComparingTo("78848.79");
        assertThat(first.markup()).isEqualByComparingTo("10000.00");
        assertThat(first.vat()).isEqualByComparingTo("17769.76");
        ScheduledPayment last = s.get(11);
        assertThat(last.amount()).isEqualByComparingTo("106618.51");
        assertThat(last.principalPart()).isEqualByComparingTo("87969.07");
        assertThat(last.markup()).isEqualByComparingTo("879.69");
        assertThat(last.vat()).isEqualByComparingTo("17769.75");
        assertThat(sum(s)).isEqualByComparingTo("1279422.56");
    }

    @Test
    void dueDatesAreCountedFromStartAndClampedToMonthEnd() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("1200000.00"), new BigDecimal("12"), 12, LocalDate.of(2024, 1, 31));

        assertThat(s.get(0).dueDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(s.get(1).dueDate()).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(s.get(2).dueDate()).isEqualTo(LocalDate.of(2024, 4, 30));
        assertThat(s.get(11).dueDate()).isEqualTo(LocalDate.of(2025, 1, 31));
    }

    @Test
    void componentsAddUpToAmountInEveryPayment() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("1000000.00"), new BigDecimal("10.5"), 7, LocalDate.of(2025, 5, 15));

        assertThat(s.get(0).amount()).isEqualByComparingTo("147900.70");
        assertThat(s.get(0).principalPart()).isEqualByComparingTo("115958.91");
        assertThat(s.get(0).markup()).isEqualByComparingTo("7291.67");
        assertThat(s.get(0).vat()).isEqualByComparingTo("24650.12");
        assertThat(s.get(6).principalPart()).isEqualByComparingTo("122181.49");
        assertThat(sum(s)).isEqualByComparingTo("1035304.90");
        s.forEach(p -> assertThat(p.principalPart().add(p.markup()).add(p.vat())).isEqualByComparingTo(p.amount()));
    }

    @Test
    void zeroRateHasNoMarkup() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("120000.00"), BigDecimal.ZERO, 12, LocalDate.of(2025, 1, 1));

        s.forEach(p -> {
            assertThat(p.amount()).isEqualByComparingTo("10000.00");
            assertThat(p.markup()).isEqualByComparingTo("0.00");
            assertThat(p.vat()).isEqualByComparingTo("1666.67");
            assertThat(p.principalPart()).isEqualByComparingTo("8333.33");
        });
    }

    @Test
    void rejectsNonPositiveFinancedAmount() {
        assertThatThrownBy(() -> PaymentScheduleCalculator.calculate(BigDecimal.ZERO, BigDecimal.TEN, 12, LocalDate.of(2025, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*PaymentScheduleCalculatorTest"`
Expected: FAIL (compilation).

- [ ] **Step 3: Implement**

```java
package ru.bell.autoleasing.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduledPayment(int number, LocalDate dueDate, BigDecimal amount, BigDecimal principalPart,
                               BigDecimal markup, BigDecimal vat) {
}
```

```java
package ru.bell.autoleasing.schedule;

import ru.bell.autoleasing.utilities.AnnuityUtilities;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PaymentScheduleCalculator {
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal VAT_PERCENT = BigDecimal.valueOf(20);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED_TWENTY = BigDecimal.valueOf(120);
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private PaymentScheduleCalculator() {
    }

    public static List<ScheduledPayment> calculate(BigDecimal financedAmount, BigDecimal annualRatePercent,
                                                   int months, LocalDate startDate) {
        if (financedAmount == null || financedAmount.signum() <= 0) {
            throw new IllegalArgumentException("сумма финансирования должна быть > 0");
        }
        BigDecimal annuity = AnnuityUtilities.calculateMonthlyAnnuity(financedAmount, annualRatePercent, months);
        BigDecimal monthlyRate = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal balance = financedAmount;
        List<ScheduledPayment> result = new ArrayList<>(months);
        for (int number = 1; number <= months; number++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalWithVat = annuity.subtract(interest);
            BigDecimal amount = annuity;
            if (number == months) {
                principalWithVat = balance;
                amount = principalWithVat.add(interest);
            }
            balance = balance.subtract(principalWithVat);
            BigDecimal vat = amount.multiply(VAT_PERCENT).divide(HUNDRED_TWENTY, 2, RoundingMode.HALF_UP);
            BigDecimal markup = interest.multiply(HUNDRED).divide(HUNDRED_TWENTY, 2, RoundingMode.HALF_UP);
            BigDecimal principalPart = amount.subtract(vat).subtract(markup);
            result.add(new ScheduledPayment(number, startDate.plusMonths(number), amount, principalPart, markup, vat));
        }
        return result;
    }
}
```

- [ ] **Step 4: Run the unit test**

Run: `./gradlew test --tests "*PaymentScheduleCalculatorTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing wiring test** `payment/PaymentCreationTest.java`

```java
package ru.bell.autoleasing.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bell.autoleasing.contract.repository.JpaContractRepository;
import ru.bell.autoleasing.payment.service.PaymentService;
import ru.bell.autoleasing.support.IntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PaymentCreationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired JpaContractRepository contracts;
    @Autowired PaymentService paymentService;
    @Autowired TransactionTemplate tx;

    @Test
    void createsScheduleWithDatesAndComponents() {
        jdbc.update("INSERT INTO autoleasing_core.cars (vin, brand, model, release_year, price, status, version) "
                + "VALUES ('TESTVIN0000000009', 'Lada', 'Vesta', 2025, 1500000.00, 'в лизинге', 0)");
        jdbc.update("INSERT INTO autoleasing_core.clients (id, name, passport, phone) VALUES (77, 'Тест Тест Тестович', '4500000077', '+79000000077')");
        jdbc.update("INSERT INTO autoleasing_core.contracts (id, car_vin, client_id, duration_in_months, down_payment, interest_rate, "
                + "monthly_payment, status, start_date, contract_number) VALUES (501, 'TESTVIN0000000009', 77, 12, 300000.00, 12.00, "
                + "106618.55, 'активен', '2024-01-31', 'AL-2024-00501')");

        tx.executeWithoutResult(status -> paymentService.createPaymentsForContract(contracts.findById(501L).orElseThrow()));

        Map<String, Object> first = jdbc.queryForMap("SELECT * FROM autoleasing_core.payments WHERE contract_id = 501 AND payment_id = 1");
        assertThat(first.get("due_date").toString()).isEqualTo(LocalDate.of(2024, 2, 29).toString());
        assertThat((BigDecimal) first.get("amount")).isEqualByComparingTo("106618.55");
        assertThat((BigDecimal) first.get("markup")).isEqualByComparingTo("10000.00");
        assertThat(first.get("status")).isEqualTo("не оплачен");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core.payments WHERE contract_id = 501", Integer.class)).isEqualTo(12);
    }
}
```

(`JpaContractRepository` must expose `findById`; if it is not a `JpaRepository`, use its existing lookup method that fetches the car.)

- [ ] **Step 6: Wire the calculator**

`PaymentServiceImpl.createPaymentsForContract` — replace the body:

```java
    @Override
    @Transactional
    public List<Payment> createPaymentsForContract(Contract contract) {
        BigDecimal financed = AnnuityUtilities.getCreditAmount(contract.getCar(), contract.getDownPayment());
        List<ScheduledPayment> schedule = PaymentScheduleCalculator.calculate(
                financed, contract.getInterestRate(), contract.getDurationInMonths(), contract.getStartDate());
        List<Payment> created = new ArrayList<>(schedule.size());
        for (ScheduledPayment scheduled : schedule) {
            Payment payment = new Payment();
            payment.setPaymentIdContractId(new PaymentIdContractId((long) scheduled.number(), contract.getId()));
            payment.setDueDate(scheduled.dueDate());
            payment.setAmount(scheduled.amount());
            payment.setPrincipalPart(scheduled.principalPart());
            payment.setMarkup(scheduled.markup());
            payment.setVat(scheduled.vat());
            payment.setPenalty(BigDecimal.ZERO.setScale(2));
            payment.setStatus(PaymentStatus.UNPAID);
            payment.setContract(contract);
            paymentRepository.insert(payment);
            created.add(payment);
        }
        return created;
    }
```

`ContractServiceImpl` — inject `java.time.Clock` (add `private final Clock clock;`):
- `validateAndFillContract`: if `downPayment.compareTo(car.getPrice()) >= 0` throw `new ValidationException("Первоначальный взнос должен быть меньше стоимости автомобиля")`; set `startDate = LocalDate.now(clock)`.
- `save`: right after `jpaContractRepository.save(contractFilled)` set `contract.setContractNumber(String.format("AL-%d-%05d", contract.getStartDate().getYear(), contract.getId()))`.
- `updatePaymentStatus`: take the found payment into a variable, `setStatus(paymentStatus)`, and when `paymentStatus == PaymentStatus.PAID` also `setPaidDate(LocalDate.now(clock))`.

- [ ] **Step 7: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "<KEY> Payment schedule with due dates, principal, markup and VAT"
```

---

### Task 7: Fixed-seed data generator

**Files:**
- Create: `seed/SeedProperties.java`, `seed/SeedData.java`, `seed/Inn.java`, `seed/SeedDataGenerator.java`, `seed/SeedDataWriter.java`, `seed/SeedRunner.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/ru/bell/autoleasing/seed/InnTest.java`, `SeedDataGeneratorTest.java`, `SeedDataWriterTest.java`

**Interfaces:**
- Consumes: `PaymentScheduleCalculator` (Task 6), enums `CarStatus`, `ClientType`, `ContractStatus`, `PaymentStatus`, `UserRole`, `PasswordEncoder`.
- Produces: `new SeedDataGenerator(long seed, LocalDate asOf).generate(): SeedData`; `SeedDataWriter.write(SeedData, String clientPasswordHash, String managerPasswordHash)`; startup seeding when `autolease.seed.enabled=true` and table `clients` is empty. Data contract: 70 cars, 30 clients (ids 1–30), 60 contracts (ids 1–60), about 2 000 payments, users `manager` + `client01`…`client30`. Edge cases: client 29 has no contracts; client 30 has one closed, fully paid contract (id 60); contract 1 starts 2024-01-31 (leap-year due date 2024-02-29); contract 2 has zero down payment; contract 3 starts on the first day of a month; contract 4 has a zero rate; contract 59 starts 2026-09-30 (nothing due yet); client 2 legal name contains quotes; client 4 legal name is long.

- [ ] **Step 1: Write the failing tests**

`seed/InnTest.java`:

```java
package ru.bell.autoleasing.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InnTest {
    @Test
    void validatesTenAndTwelveDigitNumbers() {
        assertThat(Inn.isValid("7701000019")).isTrue();
        assertThat(Inn.isValid("500100000199")).isTrue();
        assertThat(Inn.isValid("7701000018")).isFalse();
        assertThat(Inn.isValid("500100000198")).isFalse();
        assertThat(Inn.isValid("123")).isFalse();
        assertThat(Inn.isValid(null)).isFalse();
    }

    @Test
    void completesCheckDigits() {
        assertThat(Inn.complete("770100001")).isEqualTo("7701000019");
        assertThat(Inn.complete("5001000001")).isEqualTo("500100000199");
    }
}
```

`seed/SeedDataGeneratorTest.java`:

```java
package ru.bell.autoleasing.seed;

import org.junit.jupiter.api.Test;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.user.UserRole;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataGeneratorTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 1);
    private final SeedData data = new SeedDataGenerator(20261014L, AS_OF).generate();

    @Test
    void sameSeedGivesIdenticalData() {
        assertThat(new SeedDataGenerator(20261014L, AS_OF).generate()).isEqualTo(data);
    }

    @Test
    void otherSeedGivesOtherData() {
        assertThat(new SeedDataGenerator(1L, AS_OF).generate()).isNotEqualTo(data);
    }

    @Test
    void sizesMatchTheSpecification() {
        assertThat(data.cars()).hasSize(70);
        assertThat(data.clients()).hasSize(30);
        assertThat(data.contracts()).hasSize(60);
        assertThat(data.payments()).hasSizeBetween(1500, 2800);
        assertThat(data.users()).hasSize(31);
        assertThat(data.users()).filteredOn(u -> u.role() == UserRole.MANAGER).hasSize(1);
    }

    @Test
    void edgeCasesArePresent() {
        assertThat(data.contracts()).noneMatch(c -> c.clientId() == 29);
        assertThat(data.contracts()).filteredOn(c -> c.clientId() == 30).singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo(60);
                    assertThat(c.status()).isEqualTo(ContractStatus.CLOSED);
                });
        assertThat(data.payments()).filteredOn(p -> p.contractId() == 60).allMatch(p -> p.status() == PaymentStatus.PAID);
        assertThat(data.payments()).anyMatch(p -> p.contractId() == 1 && p.dueDate().equals(LocalDate.of(2024, 2, 29)));
        assertThat(contract(2).downPayment()).isEqualByComparingTo("0.00");
        assertThat(contract(3).startDate().getDayOfMonth()).isEqualTo(1);
        assertThat(contract(4).interestRate()).isEqualByComparingTo("0");
        assertThat(contract(59).startDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(data.payments()).filteredOn(p -> p.contractId() == 59).allMatch(p -> p.status() == PaymentStatus.UNPAID);
        assertThat(client(2).legalName()).contains("\"");
        assertThat(client(4).legalName().length()).isGreaterThan(80);
    }

    @Test
    void paymentsAreInternallyConsistent() {
        Map<Long, SeedData.ContractSeed> contracts = data.contracts().stream()
                .collect(Collectors.toMap(SeedData.ContractSeed::id, Function.identity()));
        data.payments().forEach(p -> {
            assertThat(p.principalPart().add(p.markup()).add(p.vat())).isEqualByComparingTo(p.amount());
            assertThat(p.status() == PaymentStatus.PAID).isEqualTo(p.paidDate() != null);
            if (p.dueDate().isAfter(AS_OF)) {
                assertThat(p.status()).isIn(PaymentStatus.UNPAID, PaymentStatus.CANCELLED);
            }
            if (p.penalty().signum() > 0) {
                assertThat(p.paidDate()).isAfter(p.dueDate());
            }
            if (p.status() == PaymentStatus.CANCELLED) {
                assertThat(contracts.get(p.contractId()).status()).isEqualTo(ContractStatus.CLOSED);
            }
        });
    }

    @Test
    void innsAreValidUniqueAndMatchClientType() {
        assertThat(data.clients()).allMatch(c -> Inn.isValid(c.inn()));
        assertThat(data.clients().stream().map(SeedData.ClientSeed::inn).collect(Collectors.toSet())).hasSize(30);
        assertThat(data.clients()).allMatch(c -> c.inn().length() == (c.type() == ClientType.LLC ? 10 : 12));
    }

    @Test
    void carStatusFollowsActiveContracts() {
        Set<String> leased = data.contracts().stream().filter(c -> c.status() == ContractStatus.ACTIVE)
                .map(SeedData.ContractSeed::carVin).collect(Collectors.toSet());
        data.cars().forEach(car -> assertThat(car.status())
                .isEqualTo(leased.contains(car.vin()) ? CarStatus.LEASED : CarStatus.AVAILABLE));
    }

    private SeedData.ContractSeed contract(long id) {
        return data.contracts().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }

    private SeedData.ClientSeed client(long id) {
        return data.clients().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }
}
```

`seed/SeedDataWriterTest.java`:

```java
package ru.bell.autoleasing.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.support.IntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SeedDataWriterTest {

    @Autowired SeedDataWriter writer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writesEverythingAndResetsIdentitySequences() {
        SeedData data = new SeedDataGenerator(20261014L, LocalDate.of(2026, 10, 1)).generate();

        writer.write(data, "client-hash", "manager-hash");

        assertThat(count("autoleasing_core.cars")).isEqualTo(70);
        assertThat(count("autoleasing_core.clients")).isEqualTo(30);
        assertThat(count("autoleasing_core.contracts")).isEqualTo(60);
        assertThat(count("autoleasing_core.payments")).isEqualTo(data.payments().size());
        assertThat(count("autoleasing_core.app_users")).isEqualTo(31);
        BigDecimal expected = data.payments().stream().map(SeedData.PaymentSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM autoleasing_core.payments", BigDecimal.class)).isEqualByComparingTo(expected);
        Long newId = jdbc.queryForObject("INSERT INTO autoleasing_core.clients (name, passport, phone) "
                + "VALUES ('Новый Клиент Тестович', '4599999999', '+79999999999') RETURNING id", Long.class);
        assertThat(newId).isEqualTo(31L);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "*Inn*" --tests "*SeedData*"`
Expected: FAIL (compilation).

- [ ] **Step 3: Implement `Inn` and `SeedData`**

```java
package ru.bell.autoleasing.seed;

public final class Inn {
    private static final int[] W10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] W11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] W12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    private Inn() {
    }

    /** Adds check digits: 9 digits → 10-digit number of a company, 10 digits → 12-digit number of a person. */
    public static String complete(String digits) {
        if (digits.length() == 9) {
            return digits + check(digits, W10);
        }
        if (digits.length() == 10) {
            String eleven = digits + check(digits, W11);
            return eleven + check(eleven, W12);
        }
        throw new IllegalArgumentException("ожидалось 9 или 10 цифр: " + digits);
    }

    public static boolean isValid(String inn) {
        if (inn == null || !inn.matches("\\d{10}|\\d{12}")) {
            return false;
        }
        return complete(inn.substring(0, inn.length() == 10 ? 9 : 10)).equals(inn);
    }

    private static int check(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        return sum % 11 % 10;
    }
}
```

```java
package ru.bell.autoleasing.seed;

import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.user.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SeedData(List<CarSeed> cars, List<ClientSeed> clients, List<ContractSeed> contracts,
                       List<PaymentSeed> payments, List<UserSeed> users) {

    public record CarSeed(String vin, String brand, String model, int releaseYear, BigDecimal price, CarStatus status) {
    }

    public record ClientSeed(long id, String name, String passport, String phone, ClientType type, String inn,
                             String legalName) {
    }

    public record ContractSeed(long id, String number, String carVin, long clientId, int durationInMonths,
                               BigDecimal downPayment, BigDecimal interestRate, BigDecimal monthlyPayment,
                               ContractStatus status, LocalDate startDate) {
    }

    public record PaymentSeed(long contractId, long paymentId, LocalDate dueDate, LocalDate paidDate, BigDecimal amount,
                              BigDecimal principalPart, BigDecimal markup, BigDecimal vat, BigDecimal penalty,
                              PaymentStatus status) {
    }

    public record UserSeed(String username, UserRole role, Long clientId) {
    }
}
```

- [ ] **Step 4: Implement `SeedDataGenerator`**

Only `java.util.Random(seed)` drives choices; `HashSet`s are used for membership checks only, never iterated.

```java
package ru.bell.autoleasing.seed;

import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.schedule.PaymentScheduleCalculator;
import ru.bell.autoleasing.schedule.ScheduledPayment;
import ru.bell.autoleasing.user.UserRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class SeedDataGenerator {
    public static final int CARS = 70;
    public static final int CLIENTS = 30;
    public static final int CONTRACTS = 60;
    private static final int[] DURATIONS = {12, 24, 36, 48, 60};
    private static final LocalDate FIRST_START = LocalDate.of(2022, 1, 1);
    private static final LocalDate LAST_START = LocalDate.of(2026, 9, 30);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal PENALTY_PER_DAY = new BigDecimal("0.001");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final String[][] MODELS = {
            {"Haval", "Jolion"}, {"Haval", "F7"}, {"Chery", "Tiggo 7 Pro"}, {"Geely", "Monjaro"},
            {"Geely", "Coolray"}, {"Lada", "Vesta"}, {"Lada", "Largus"}, {"Toyota", "Camry"}, {"Kia", "K5"},
            {"Hyundai", "Sonata"}, {"GAZ", "Gazelle Next"}, {"Exeed", "TXL"}, {"Tank", "300"},
            {"Changan", "UNI-K"}, {"Volkswagen", "Tiguan"}};
    private static final String[] LAST_NAMES = {"Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев", "Петров",
            "Соколов", "Михайлов", "Новиков", "Фёдоров", "Морозов", "Волков", "Алексеев", "Лебедев", "Семёнов",
            "Егоров", "Павлов", "Козлов", "Степанов", "Николаев"};
    private static final String[] FIRST_NAMES = {"Александр", "Дмитрий", "Максим", "Сергей", "Андрей", "Алексей",
            "Артём", "Илья", "Кирилл", "Михаил", "Никита", "Роман"};
    private static final String[] PATRONYMICS = {"Александрович", "Дмитриевич", "Сергеевич", "Андреевич",
            "Алексеевич", "Игоревич", "Владимирович", "Николаевич"};
    private static final String[] COMPANIES = {"Северный Транзит", "Вектор Логистик", "СтройМонтаж", "Альфа Сервис",
            "ТехноПарк", "Гранит", "Меридиан", "Восток Трейд", "Профи Карго", "Сокол Групп", "Энергия Плюс",
            "Байкал Авто", "Орион", "Кристалл", "Прогресс", "Дельта Строй", "Уралтранс", "Регион Снаб", "Импульс",
            "Партнёр"};
    private static final String QUOTED_NAME = "ООО «Торговый дом \"Рога и Копыта\"»";
    private static final String LONG_NAME = "ООО «Межрегиональная транспортно-экспедиционная компания "
            + "\"Северо-Западный логистический альянс\"»";

    private final Random rnd;
    private final LocalDate asOf;
    private final Set<String> usedVins = new HashSet<>();
    private final Set<String> usedInns = new HashSet<>();
    private final Set<String> usedPassports = new HashSet<>();
    private final Set<String> usedPhones = new HashSet<>();

    public SeedDataGenerator(long seed, LocalDate asOf) {
        this.rnd = new Random(seed);
        this.asOf = asOf;
    }

    public SeedData generate() {
        List<SeedData.CarSeed> cars = generateCars();
        List<SeedData.ClientSeed> clients = generateClients();
        int[] perClient = distributeContracts();
        List<SeedData.ContractSeed> contracts = new ArrayList<>(CONTRACTS);
        List<SeedData.PaymentSeed> payments = new ArrayList<>();
        long contractId = 1;
        for (int clientId = 1; clientId <= CLIENTS; clientId++) {
            for (int k = 0; k < perClient[clientId]; k++) {
                SeedData.CarSeed car = cars.get((int) contractId - 1);
                contracts.add(generateContract(contractId, clientId, car, payments));
                contractId++;
            }
        }
        Set<String> leased = new HashSet<>();
        contracts.stream().filter(c -> c.status() == ContractStatus.ACTIVE).forEach(c -> leased.add(c.carVin()));
        List<SeedData.CarSeed> finalCars = cars.stream()
                .map(c -> new SeedData.CarSeed(c.vin(), c.brand(), c.model(), c.releaseYear(), c.price(),
                        leased.contains(c.vin()) ? CarStatus.LEASED : CarStatus.AVAILABLE))
                .toList();
        List<SeedData.UserSeed> users = new ArrayList<>();
        users.add(new SeedData.UserSeed("manager", UserRole.MANAGER, null));
        clients.forEach(c -> users.add(new SeedData.UserSeed(String.format("client%02d", c.id()), UserRole.CLIENT, c.id())));
        return new SeedData(finalCars, clients, contracts, payments, users);
    }

    private List<SeedData.CarSeed> generateCars() {
        List<SeedData.CarSeed> cars = new ArrayList<>(CARS);
        for (int i = 0; i < CARS; i++) {
            String[] model = MODELS[rnd.nextInt(MODELS.length)];
            int year = 2019 + rnd.nextInt(8);
            BigDecimal price = BigDecimal.valueOf(200 + rnd.nextInt(701)).multiply(BigDecimal.valueOf(10_000)).setScale(2);
            cars.add(new SeedData.CarSeed(nextVin(), model[0], model[1], year, price, CarStatus.AVAILABLE));
        }
        return cars;
    }

    private List<SeedData.ClientSeed> generateClients() {
        List<SeedData.ClientSeed> clients = new ArrayList<>(CLIENTS);
        int companyIndex = 0;
        for (long id = 1; id <= CLIENTS; id++) {
            String last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
            String first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
            String patronymic = PATRONYMICS[rnd.nextInt(PATRONYMICS.length)];
            ClientType type = id % 3 == 0 ? ClientType.INDIVIDUAL_ENTREPRENEUR : ClientType.LLC;
            String inn = type == ClientType.LLC ? uniqueInn("77", 7) : uniqueInn("50", 8);
            String legalName;
            if (type == ClientType.INDIVIDUAL_ENTREPRENEUR) {
                legalName = "ИП " + last + " " + first.charAt(0) + ". " + patronymic.charAt(0) + ".";
            } else if (id == 2) {
                legalName = QUOTED_NAME;
            } else if (id == 4) {
                legalName = LONG_NAME;
            } else {
                legalName = "ООО «" + COMPANIES[companyIndex++ % COMPANIES.length] + "»";
            }
            String passport = uniqueDigits(10, usedPassports);
            String phone = "+79" + uniqueDigits(9, usedPhones);
            clients.add(new SeedData.ClientSeed(id, last + " " + first + " " + patronymic, passport, phone, type, inn, legalName));
        }
        return clients;
    }

    /** Index = client id. Client 29 gets none, client 30 gets exactly one, clients 1–28 share the rest (1–4 each). */
    private int[] distributeContracts() {
        int[] perClient = new int[CLIENTS + 1];
        for (int c = 1; c <= 28; c++) {
            perClient[c] = 1;
        }
        int remaining = CONTRACTS - 28 - 1;
        while (remaining > 0) {
            int c = 1 + rnd.nextInt(28);
            if (perClient[c] < 4) {
                perClient[c]++;
                remaining--;
            }
        }
        perClient[30] = 1;
        return perClient;
    }

    private SeedData.ContractSeed generateContract(long id, long clientId, SeedData.CarSeed car,
                                                   List<SeedData.PaymentSeed> payments) {
        int duration = DURATIONS[rnd.nextInt(DURATIONS.length)];
        LocalDate start = FIRST_START.plusDays(rnd.nextInt((int) ChronoUnit.DAYS.between(FIRST_START, LAST_START) + 1));
        int downPercent = rnd.nextInt(31);
        BigDecimal rate = BigDecimal.valueOf(80 + rnd.nextInt(101), 1);
        boolean earlyBuyout = rnd.nextInt(100) < 8;
        switch ((int) id) {
            case 1 -> { start = LocalDate.of(2024, 1, 31); duration = 36; }
            case 2 -> downPercent = 0;
            case 3 -> start = LocalDate.of(2025, 12, 1);
            case 4 -> rate = BigDecimal.valueOf(0, 1);
            case 59 -> { start = LocalDate.of(2026, 9, 30); duration = 12; earlyBuyout = false; }
            case 60 -> { start = LocalDate.of(2022, 3, 15); duration = 24; earlyBuyout = false; }
            default -> { }
        }
        BigDecimal down = car.price().multiply(BigDecimal.valueOf(downPercent)).divide(HUNDRED, 0, RoundingMode.HALF_UP).setScale(2);
        List<ScheduledPayment> schedule = PaymentScheduleCalculator.calculate(car.price().subtract(down), rate, duration, start);
        int dueByAsOf = (int) schedule.stream().filter(p -> !p.dueDate().isAfter(asOf)).count();
        int buyoutAfter = earlyBuyout && dueByAsOf >= 6 ? 3 + rnd.nextInt(dueByAsOf - 4) : -1;

        boolean allPaid = true;
        for (ScheduledPayment p : schedule) {
            PaymentStatus status;
            LocalDate paidDate = null;
            BigDecimal penalty = ZERO;
            if (buyoutAfter > 0) {
                if (p.number() > buyoutAfter) {
                    status = PaymentStatus.CANCELLED;
                } else {
                    status = PaymentStatus.PAID;
                    paidDate = p.dueDate().minusDays(rnd.nextInt(6));
                }
            } else if (p.dueDate().isAfter(asOf)) {
                status = PaymentStatus.UNPAID;
            } else if (id == 60) {
                status = PaymentStatus.PAID;
                paidDate = p.dueDate();
            } else {
                int roll = rnd.nextInt(100);
                boolean recent = p.dueDate().isAfter(asOf.minusMonths(3));
                if (roll < 80) {
                    status = PaymentStatus.PAID;
                    paidDate = p.dueDate().minusDays(rnd.nextInt(6));
                } else if (roll < 94 || !recent) {
                    int daysLate = 1 + rnd.nextInt(20);
                    LocalDate late = p.dueDate().plusDays(daysLate);
                    if (late.isAfter(asOf)) {
                        status = PaymentStatus.UNPAID;
                    } else {
                        status = PaymentStatus.PAID;
                        paidDate = late;
                        penalty = p.amount().multiply(PENALTY_PER_DAY).multiply(BigDecimal.valueOf(daysLate))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    status = PaymentStatus.UNPAID;
                }
            }
            if (status != PaymentStatus.PAID) {
                allPaid = false;
            }
            payments.add(new SeedData.PaymentSeed(id, p.number(), p.dueDate(), paidDate, p.amount(),
                    p.principalPart(), p.markup(), p.vat(), penalty, status));
        }
        ContractStatus status = buyoutAfter > 0 || allPaid ? ContractStatus.CLOSED : ContractStatus.ACTIVE;
        return new SeedData.ContractSeed(id, String.format("AL-%d-%05d", start.getYear(), id), car.vin(), clientId,
                duration, down, rate, schedule.get(0).amount(), status, start);
    }

    private String nextVin() {
        while (true) {
            StringBuilder vin = new StringBuilder(17);
            for (int i = 0; i < 17; i++) {
                vin.append(VIN_CHARS.charAt(rnd.nextInt(VIN_CHARS.length())));
            }
            if (usedVins.add(vin.toString())) {
                return vin.toString();
            }
        }
    }

    private String uniqueInn(String prefix, int randomDigits) {
        while (true) {
            String inn = Inn.complete(prefix + digits(randomDigits));
            if (usedInns.add(inn)) {
                return inn;
            }
        }
    }

    private String uniqueDigits(int length, Set<String> used) {
        while (true) {
            String value = digits(length);
            if (used.add(value)) {
                return value;
            }
        }
    }

    private String digits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Run the pure tests**

Run: `./gradlew test --tests "*InnTest" --tests "*SeedDataGeneratorTest"`
Expected: PASS. If `paymentsAreInternallyConsistent` fails, fix the generator, not the test.

- [ ] **Step 6: Implement writer, properties, runner**

```java
package ru.bell.autoleasing.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties("autolease.seed")
public record SeedProperties(boolean enabled, long seed, LocalDate asOf, String clientPassword, String managerPassword) {
}
```

```java
package ru.bell.autoleasing.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.user.UserRole;

import java.sql.Date;
import java.sql.Types;

@Component
@RequiredArgsConstructor
public class SeedDataWriter {
    private final JdbcTemplate jdbc;

    @Transactional
    public void write(SeedData data, String clientPasswordHash, String managerPasswordHash) {
        jdbc.batchUpdate("INSERT INTO autoleasing_core.cars (vin, brand, model, release_year, price, status, version) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", data.cars(), 200, (ps, c) -> {
            ps.setString(1, c.vin());
            ps.setString(2, c.brand());
            ps.setString(3, c.model());
            ps.setInt(4, c.releaseYear());
            ps.setBigDecimal(5, c.price());
            ps.setString(6, c.status().toString());
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.clients (id, name, passport, phone, client_type, inn, legal_name) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", data.clients(), 200, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setString(2, c.name());
            ps.setString(3, c.passport());
            ps.setString(4, c.phone());
            ps.setString(5, c.type().toString());
            ps.setString(6, c.inn());
            ps.setString(7, c.legalName());
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.contracts (id, contract_number, car_vin, client_id, duration_in_months, "
                + "down_payment, interest_rate, monthly_payment, status, start_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.contracts(), 200, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setString(2, c.number());
            ps.setString(3, c.carVin());
            ps.setLong(4, c.clientId());
            ps.setInt(5, c.durationInMonths());
            ps.setBigDecimal(6, c.downPayment());
            ps.setBigDecimal(7, c.interestRate());
            ps.setBigDecimal(8, c.monthlyPayment());
            ps.setString(9, c.status().toString());
            ps.setDate(10, Date.valueOf(c.startDate()));
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.payments (contract_id, payment_id, due_date, paid_date, amount, "
                + "principal_part, markup, vat, penalty, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.payments(), 500, (ps, p) -> {
            ps.setLong(1, p.contractId());
            ps.setLong(2, p.paymentId());
            ps.setDate(3, Date.valueOf(p.dueDate()));
            if (p.paidDate() == null) {
                ps.setNull(4, Types.DATE);
            } else {
                ps.setDate(4, Date.valueOf(p.paidDate()));
            }
            ps.setBigDecimal(5, p.amount());
            ps.setBigDecimal(6, p.principalPart());
            ps.setBigDecimal(7, p.markup());
            ps.setBigDecimal(8, p.vat());
            ps.setBigDecimal(9, p.penalty());
            ps.setString(10, p.status().toString());
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.app_users (username, password_hash, role, client_id) VALUES (?, ?, ?, ?)",
                data.users(), 200, (ps, u) -> {
            ps.setString(1, u.username());
            ps.setString(2, u.role() == UserRole.MANAGER ? managerPasswordHash : clientPasswordHash);
            ps.setString(3, u.role().name());
            if (u.clientId() == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, u.clientId());
            }
        });
        jdbc.execute("SELECT setval(pg_get_serial_sequence('autoleasing_core.clients', 'id'), (SELECT MAX(id) FROM autoleasing_core.clients))");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('autoleasing_core.contracts', 'id'), (SELECT MAX(id) FROM autoleasing_core.contracts))");
    }
}
```

```java
package ru.bell.autoleasing.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "autolease.seed.enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {
    private final SeedProperties properties;
    private final SeedDataWriter writer;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core.clients", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Генерация данных пропущена: клиентов уже {}", existing);
            return;
        }
        SeedData data = new SeedDataGenerator(properties.seed(), properties.asOf()).generate();
        writer.write(data, encoder.encode(properties.clientPassword()), encoder.encode(properties.managerPassword()));
        log.info("Сгенерированы данные: автомобилей={}, клиентов={}, договоров={}, платежей={}, пользователей={}",
                data.cars().size(), data.clients().size(), data.contracts().size(), data.payments().size(), data.users().size());
    }
}
```

`application.yml` — add under `autolease:`:

```yaml
  seed:
    enabled: ${SEED_ENABLED:true}
    seed: 20261014
    as-of: 2026-10-01
    client-password: Client123!
    manager-password: Manager123!
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 8: Verify on the stand, commit and merge story B**

```bash
git add -A && git commit -m "<KEY> Fixed-seed generator for clients, contracts, payments and users"
docker compose --profile core up -d --build app-it && sleep 40
docker exec autolease-db psql -U postgres -d autolease_it -c "SELECT COUNT(*) FROM autoleasing_core.payments"
./gradlew test && git checkout develop && git merge --no-ff feature/<KEY>-client-data -m "<KEY> Merge client data" && ./gradlew test
```

Expected: payment count between 1 500 and 2 800; log line «Сгенерированы данные…».

Note: the writer stores enum values with `toString()`, which for the legacy enums returns the Russian description (as `PaymentStatus` does). If `CarStatus` or `ContractStatus` `toString()` differs, use the value their converter writes.

---

## Story C — Client cabinet

### Task 8: Finance query service and cabinet API

**Files:**
- Create: `cabinet/ClientFinanceQueryService.java`, `cabinet/CabinetController.java`, `cabinet/dto/MeResponse.java`, `ContractSummary.java`, `PaymentRow.java`, `PaymentsSummary.java`, `UpcomingPayments.java`
- Modify: `exceptions/ControllerAdvice.java`
- Test: `src/test/resources/sql/finance-fixture.sql`, `src/test/java/ru/bell/autoleasing/cabinet/ClientFinanceQueryServiceTest.java`, `CabinetControllerTest.java`

**Interfaces:**
- Consumes: `AppUserPrincipal` (Task 5), schema (Task 4), `Clock`, `NotFoundException(String)`, `ValidationException(String)`.
- Produces (used by the agent tools in Task 10 and the UI in Task 9):
  - `record MeResponse(String username, long clientId, String clientType, String legalName, String inn, String contactName)`
  - `record ContractSummary(long contractId, String contractNumber, String status, String statusText, LocalDate startDate, int durationInMonths, BigDecimal monthlyPayment, String car, String vin, BigDecimal remainingDebt, int unpaidCount, LocalDate nextDueDate)`
  - `record PaymentRow(long contractId, String contractNumber, long paymentId, LocalDate dueDate, LocalDate paidDate, BigDecimal amount, BigDecimal principalPart, BigDecimal markup, BigDecimal vat, BigDecimal penalty, String status, String statusText, boolean overdue)`
  - `record PaymentsSummary(LocalDate periodFrom, LocalDate periodTo, BigDecimal paidTotal, int paidCount, BigDecimal accruedTotal, int accruedCount, String currency)`
  - `record UpcomingPayments(PaymentRow nextPayment, List<PaymentRow> upcoming, int overdueCount, BigDecimal overdueTotal)`
  - `ClientFinanceQueryService`: `Optional<MeResponse> findProfile(long clientId, String username)`, `List<ContractSummary> findContracts(long clientId, LocalDate today)`, `List<PaymentRow> findSchedule(long clientId, long contractId, LocalDate today)`, `List<PaymentRow> findPayments(long clientId, LocalDate from, LocalDate to, PaymentStatus statusOrNull, LocalDate today)`, `PaymentsSummary summarize(long clientId, LocalDate from, LocalDate to)`, `UpcomingPayments findUpcoming(long clientId, Long contractIdOrNull, int limit, LocalDate today)`.
  - Definitions: remaining debt = sum of `amount` of payments with status `не оплачен`; paid total = sum of `amount + penalty` of payments `оплачен` with `paid_date` in the period; accrued total = sum of `amount` of payments not `отменен` with `due_date` in the period; periods are inclusive on both ends; overdue = `не оплачен` and `due_date` before today; next payment = earliest `не оплачен` with `due_date` on or after today; `status` = enum name (`PAID`, `UNPAID`, `CANCELLED`, `ACTIVE`, `CLOSED`), `statusText` = Russian text.
  - HTTP (role CLIENT): `GET /api/me`, `GET /api/me/contracts`, `GET /api/me/contracts/{contractId}/schedule`, `GET /api/me/payments?from&to[&status]`, `GET /api/me/payments/summary?from&to`, `GET /api/me/payments/upcoming[?contractId][&limit=3]` (limit 1–12). Another client's contract → 404.
  - Test fixture `/sql/finance-fixture.sql` (clients 1001, 1002, 1003; contracts 2001, 2002, 2003) reused by Tasks 10 and 14.

- [ ] **Step 1: Create the branch**

```bash
git checkout develop && git checkout -b feature/<KEY>-client-cabinet
```

- [ ] **Step 2: Write the fixture** `src/test/resources/sql/finance-fixture.sql`

```sql
INSERT INTO autoleasing_core.cars (vin, brand, model, release_year, price, status, version) VALUES
 ('TESTVIN0000000001', 'Haval', 'Jolion', 2025, 2500000.00, 'в лизинге', 0),
 ('TESTVIN0000000002', 'Lada', 'Largus', 2024, 1800000.00, 'доступен', 0),
 ('TESTVIN0000000003', 'Geely', 'Monjaro', 2026, 4200000.00, 'в лизинге', 0);

INSERT INTO autoleasing_core.clients (id, name, passport, phone, client_type, inn, legal_name) VALUES
 (1001, 'Смирнов Андрей Петрович', '4510000001', '+79000000001', 'ООО', '7701000019', 'ООО «Альфа Тест»'),
 (1002, 'Петров Илья Сергеевич', '4510000002', '+79000000002', 'ИП', '500100000199', 'ИП Петров И. С.'),
 (1003, 'Козлов Роман Игоревич', '4510000003', '+79000000003', 'ООО', '7701000026', 'ООО «Без Договоров»');

INSERT INTO autoleasing_core.contracts (id, contract_number, car_vin, client_id, duration_in_months, down_payment,
                                        interest_rate, monthly_payment, status, start_date) VALUES
 (2001, 'AL-2026-02001', 'TESTVIN0000000001', 1001, 6, 500000.00, 12.00, 10000.00, 'активен', '2026-06-30'),
 (2002, 'AL-2026-02002', 'TESTVIN0000000002', 1001, 4, 300000.00, 10.00, 5000.00, 'закрыт', '2026-01-01'),
 (2003, 'AL-2026-02003', 'TESTVIN0000000003', 1002, 3, 0.00, 15.00, 20000.00, 'активен', '2026-09-01');

INSERT INTO autoleasing_core.payments (contract_id, payment_id, due_date, paid_date, amount, principal_part, markup,
                                       vat, penalty, status) VALUES
 (2001, 1, '2026-07-31', '2026-07-30', 10000.00, 7000.00, 1333.33, 1666.67, 0.00, 'оплачен'),
 (2001, 2, '2026-08-31', '2026-09-03', 10000.00, 7000.00, 1333.33, 1666.67, 30.00, 'оплачен'),
 (2001, 3, '2026-09-30', '2026-09-30', 10000.00, 7000.00, 1333.33, 1666.67, 0.00, 'оплачен'),
 (2001, 4, '2026-10-31', NULL, 10000.00, 7000.00, 1333.33, 1666.67, 0.00, 'не оплачен'),
 (2001, 5, '2026-11-30', NULL, 10000.00, 7000.00, 1333.33, 1666.67, 0.00, 'не оплачен'),
 (2001, 6, '2026-12-31', NULL, 10000.00, 7000.00, 1333.33, 1666.67, 0.00, 'не оплачен'),
 (2002, 1, '2026-02-01', '2026-02-01', 5000.00, 3500.00, 666.67, 833.33, 0.00, 'оплачен'),
 (2002, 2, '2026-03-01', '2026-02-27', 5000.00, 3500.00, 666.67, 833.33, 0.00, 'оплачен'),
 (2002, 3, '2026-04-01', NULL, 5000.00, 3500.00, 666.67, 833.33, 0.00, 'отменен'),
 (2002, 4, '2026-05-01', NULL, 5000.00, 3500.00, 666.67, 833.33, 0.00, 'отменен'),
 (2003, 1, '2026-10-01', NULL, 20000.00, 14000.00, 2666.67, 3333.33, 0.00, 'не оплачен'),
 (2003, 2, '2026-11-01', NULL, 20000.00, 14000.00, 2666.67, 3333.33, 0.00, 'не оплачен'),
 (2003, 3, '2026-12-01', NULL, 20000.00, 14000.00, 2666.67, 3333.33, 0.00, 'не оплачен');
```

Today in tests is 2026-10-10 (fixed clock).

- [ ] **Step 3: Write the failing service test**

```java
package ru.bell.autoleasing.cabinet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.cabinet.dto.ContractSummary;
import ru.bell.autoleasing.cabinet.dto.PaymentRow;
import ru.bell.autoleasing.cabinet.dto.PaymentsSummary;
import ru.bell.autoleasing.cabinet.dto.UpcomingPayments;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.support.IntegrationTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientFinanceQueryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Autowired
    ClientFinanceQueryService finance;

    @Test
    void contractsShowRemainingDebtAndNextDueDate() {
        List<ContractSummary> contracts = finance.findContracts(1001, TODAY);

        assertThat(contracts).extracting(ContractSummary::contractId).containsExactly(2002L, 2001L);
        ContractSummary active = contracts.get(1);
        assertThat(active.remainingDebt()).isEqualByComparingTo("30000.00");
        assertThat(active.unpaidCount()).isEqualTo(3);
        assertThat(active.nextDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(active.car()).isEqualTo("Haval Jolion");
        assertThat(active.status()).isEqualTo("ACTIVE");
        ContractSummary closed = contracts.get(0);
        assertThat(closed.remainingDebt()).isEqualByComparingTo("0.00");
        assertThat(closed.nextDueDate()).isNull();
    }

    @Test
    void summaryIncludesBothPeriodEndsAndPenalty() {
        PaymentsSummary september = finance.summarize(1001, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(september.paidTotal()).isEqualByComparingTo("20030.00");
        assertThat(september.paidCount()).isEqualTo(2);
        assertThat(september.accruedTotal()).isEqualByComparingTo("10000.00");
        assertThat(september.accruedCount()).isEqualTo(1);

        PaymentsSummary february = finance.summarize(1001, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(february.paidTotal()).isEqualByComparingTo("10000.00");
        assertThat(february.accruedTotal()).isEqualByComparingTo("5000.00");
    }

    @Test
    void cancelledPaymentsAreNotAccrued() {
        PaymentsSummary april = finance.summarize(1001, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(april.accruedTotal()).isEqualByComparingTo("0.00");
        assertThat(april.accruedCount()).isZero();
    }

    @Test
    void clientWithoutContractsGetsEmptyResults() {
        assertThat(finance.findContracts(1003, TODAY)).isEmpty();
        PaymentsSummary year = finance.summarize(1003, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(year.paidTotal()).isEqualByComparingTo("0.00");
        assertThat(year.accruedTotal()).isEqualByComparingTo("0.00");
        assertThat(finance.findUpcoming(1003, null, 3, TODAY).nextPayment()).isNull();
    }

    @Test
    void reversedPeriodIsRejected() {
        assertThatThrownBy(() -> finance.summarize(1001, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void paymentsFilterByDueDateAndStatus() {
        List<PaymentRow> rows = finance.findPayments(1001, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), null, TODAY);
        assertThat(rows).extracting(PaymentRow::paymentId).containsExactly(3L, 4L);
        List<PaymentRow> unpaid = finance.findPayments(1001, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), PaymentStatus.UNPAID, TODAY);
        assertThat(unpaid).extracting(PaymentRow::paymentId).containsExactly(4L);
    }

    @Test
    void upcomingSeparatesOverdueFromNextPayment() {
        UpcomingPayments upcoming = finance.findUpcoming(1002, null, 3, TODAY);
        assertThat(upcoming.nextPayment().dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(upcoming.upcoming()).hasSize(3);
        assertThat(upcoming.upcoming().get(0).overdue()).isTrue();
        assertThat(upcoming.overdueCount()).isEqualTo(1);
        assertThat(upcoming.overdueTotal()).isEqualByComparingTo("20000.00");
        assertThat(finance.findUpcoming(1001, null, 1, TODAY).upcoming()).hasSize(1);
    }

    @Test
    void otherClientsContractIsNotFound() {
        assertThatThrownBy(() -> finance.findSchedule(1002, 2001, TODAY)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> finance.findUpcoming(1001, 2003L, 3, TODAY)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void limitOutOfRangeIsRejected() {
        assertThatThrownBy(() -> finance.findUpcoming(1001, null, 0, TODAY)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> finance.findUpcoming(1001, null, 13, TODAY)).isInstanceOf(ValidationException.class);
    }
}
```

- [ ] **Step 4: Run to verify it fails**

Run: `./gradlew test --tests "*ClientFinanceQueryServiceTest"`
Expected: FAIL (compilation).

- [ ] **Step 5: Implement DTOs and the service**

DTOs are plain records exactly as listed in **Interfaces** (package `ru.bell.autoleasing.cabinet.dto`).

```java
package ru.bell.autoleasing.cabinet;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.cabinet.dto.*;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientFinanceQueryService {
    private static final int MAX_LIMIT = 12;
    private static final String PAYMENT_SELECT = """
            SELECT p.contract_id, c.contract_number, p.payment_id, p.due_date, p.paid_date, p.amount,
                   p.principal_part, p.markup, p.vat, p.penalty, p.status
            FROM autoleasing_core.payments p
            JOIN autoleasing_core.contracts c ON c.id = p.contract_id
            WHERE c.client_id = :clientId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<MeResponse> findProfile(long clientId, String username) {
        return jdbc.query("SELECT id, name, client_type, inn, legal_name FROM autoleasing_core.clients WHERE id = :clientId",
                        params(clientId),
                        (rs, i) -> new MeResponse(username, rs.getLong("id"), rs.getString("client_type"),
                                rs.getString("legal_name"), rs.getString("inn"), rs.getString("name")))
                .stream().findFirst();
    }

    public List<ContractSummary> findContracts(long clientId, LocalDate today) {
        String sql = """
                SELECT c.id, c.contract_number, c.status, c.start_date, c.duration_in_months, c.monthly_payment,
                       car.brand, car.model, car.vin,
                       COALESCE(SUM(p.amount) FILTER (WHERE p.status = :unpaid), 0) AS remaining_debt,
                       COUNT(p.payment_id) FILTER (WHERE p.status = :unpaid) AS unpaid_count,
                       MIN(p.due_date) FILTER (WHERE p.status = :unpaid AND p.due_date >= :today) AS next_due_date
                FROM autoleasing_core.contracts c
                JOIN autoleasing_core.cars car ON car.vin = c.car_vin
                LEFT JOIN autoleasing_core.payments p ON p.contract_id = c.id
                WHERE c.client_id = :clientId
                GROUP BY c.id, car.vin
                ORDER BY c.start_date, c.id
                """;
        return jdbc.query(sql, params(clientId).addValue("today", today), (rs, i) -> {
            ContractStatus status = ContractStatus.fromString(rs.getString("status"));
            return new ContractSummary(rs.getLong("id"), rs.getString("contract_number"), status.name(),
                    status.toString(), toLocalDate(rs.getDate("start_date")), rs.getInt("duration_in_months"),
                    money(rs.getBigDecimal("monthly_payment")), rs.getString("brand") + " " + rs.getString("model"),
                    rs.getString("vin"), money(rs.getBigDecimal("remaining_debt")), rs.getInt("unpaid_count"),
                    toLocalDate(rs.getDate("next_due_date")));
        });
    }

    public List<PaymentRow> findSchedule(long clientId, long contractId, LocalDate today) {
        requireOwnContract(clientId, contractId);
        return jdbc.query(PAYMENT_SELECT + " AND p.contract_id = :contractId ORDER BY p.due_date, p.payment_id",
                params(clientId).addValue("contractId", contractId), (rs, i) -> paymentRow(rs, today));
    }

    public List<PaymentRow> findPayments(long clientId, LocalDate from, LocalDate to, PaymentStatus statusOrNull,
                                         LocalDate today) {
        requirePeriod(from, to);
        MapSqlParameterSource p = params(clientId).addValue("from", from).addValue("to", to);
        String sql = PAYMENT_SELECT + " AND p.due_date BETWEEN :from AND :to";
        if (statusOrNull != null) {
            sql += " AND p.status = :status";
            p.addValue("status", statusOrNull.toString());
        }
        return jdbc.query(sql + " ORDER BY p.due_date, p.contract_id, p.payment_id", p, (rs, i) -> paymentRow(rs, today));
    }

    public PaymentsSummary summarize(long clientId, LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        String sql = """
                SELECT
                  COALESCE(SUM(p.amount + p.penalty) FILTER (WHERE p.status = :paid AND p.paid_date BETWEEN :from AND :to), 0) AS paid_total,
                  COUNT(*) FILTER (WHERE p.status = :paid AND p.paid_date BETWEEN :from AND :to) AS paid_count,
                  COALESCE(SUM(p.amount) FILTER (WHERE p.status <> :cancelled AND p.due_date BETWEEN :from AND :to), 0) AS accrued_total,
                  COUNT(*) FILTER (WHERE p.status <> :cancelled AND p.due_date BETWEEN :from AND :to) AS accrued_count
                FROM autoleasing_core.payments p
                JOIN autoleasing_core.contracts c ON c.id = p.contract_id
                WHERE c.client_id = :clientId
                """;
        return jdbc.queryForObject(sql, params(clientId).addValue("from", from).addValue("to", to),
                (rs, i) -> new PaymentsSummary(from, to, money(rs.getBigDecimal("paid_total")), rs.getInt("paid_count"),
                        money(rs.getBigDecimal("accrued_total")), rs.getInt("accrued_count"), "RUB"));
    }

    public UpcomingPayments findUpcoming(long clientId, Long contractIdOrNull, int limit, LocalDate today) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ValidationException("limit должен быть от 1 до " + MAX_LIMIT);
        }
        MapSqlParameterSource p = params(clientId);
        String sql = PAYMENT_SELECT + " AND p.status = :unpaid";
        if (contractIdOrNull != null) {
            requireOwnContract(clientId, contractIdOrNull);
            sql += " AND p.contract_id = :contractId";
            p.addValue("contractId", contractIdOrNull);
        }
        List<PaymentRow> unpaid = jdbc.query(sql + " ORDER BY p.due_date, p.contract_id, p.payment_id", p,
                (rs, i) -> paymentRow(rs, today));
        List<PaymentRow> overdue = unpaid.stream().filter(PaymentRow::overdue).toList();
        PaymentRow next = unpaid.stream().filter(r -> !r.dueDate().isBefore(today)).findFirst().orElse(null);
        BigDecimal overdueTotal = overdue.stream().map(PaymentRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UpcomingPayments(next, unpaid.stream().limit(limit).toList(), overdue.size(), money(overdueTotal));
    }

    private void requireOwnContract(long clientId, long contractId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM autoleasing_core.contracts WHERE id = :contractId AND client_id = :clientId",
                params(clientId).addValue("contractId", contractId), Integer.class);
        if (count == null || count == 0) {
            throw new NotFoundException("Договор " + contractId + " не найден");
        }
    }

    private static void requirePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ValidationException("Начало периода должно быть не позже конца периода");
        }
    }

    private static MapSqlParameterSource params(long clientId) {
        return new MapSqlParameterSource("clientId", clientId)
                .addValue("paid", PaymentStatus.PAID.toString())
                .addValue("unpaid", PaymentStatus.UNPAID.toString())
                .addValue("cancelled", PaymentStatus.CANCELLED.toString());
    }

    private static PaymentRow paymentRow(ResultSet rs, LocalDate today) throws SQLException {
        PaymentStatus status = PaymentStatus.fromString(rs.getString("status"));
        LocalDate due = toLocalDate(rs.getDate("due_date"));
        boolean overdue = status == PaymentStatus.UNPAID && due != null && due.isBefore(today);
        return new PaymentRow(rs.getLong("contract_id"), rs.getString("contract_number"), rs.getLong("payment_id"), due,
                toLocalDate(rs.getDate("paid_date")), money(rs.getBigDecimal("amount")),
                money(rs.getBigDecimal("principal_part")), money(rs.getBigDecimal("markup")),
                money(rs.getBigDecimal("vat")), money(rs.getBigDecimal("penalty")), status.name(), status.toString(), overdue);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
```

(If `ContractStatus` has no `fromString(String)`, add one with the same shape as `PaymentStatus.fromString`.)

- [ ] **Step 6: Run the service test**

Run: `./gradlew test --tests "*ClientFinanceQueryServiceTest"`
Expected: PASS.

- [ ] **Step 7: Write the failing controller test**

```java
package ru.bell.autoleasing.cabinet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CabinetControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void profileShowsLegalData() throws Exception {
        mockMvc.perform(get("/api/me").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("ООО «Альфа Тест»"))
                .andExpect(jsonPath("$.clientType").value("ООО"))
                .andExpect(jsonPath("$.inn").value("7701000019"));
    }

    @Test
    void contractsListIsOwnOnly() throws Exception {
        mockMvc.perform(get("/api/me/contracts").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].remainingDebt").value(30000.0));
    }

    @Test
    void otherClientsScheduleIsNotFound() throws Exception {
        mockMvc.perform(get("/api/me/contracts/2003/schedule").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isNotFound());
    }

    @Test
    void summaryForSeptember() throws Exception {
        mockMvc.perform(get("/api/me/payments/summary").param("from", "2026-09-01").param("to", "2026-09-30")
                        .with(user(TestPrincipals.client(1001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidTotal").value(20030.0))
                .andExpect(jsonPath("$.accruedTotal").value(10000.0));
    }

    @Test
    void badParametersAreBadRequest() throws Exception {
        var client = user(TestPrincipals.client(1001));
        mockMvc.perform(get("/api/me/payments/summary").param("from", "2026-10-01").param("to", "2026-09-01").with(client))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(get("/api/me/payments").param("from", "2026-13-01").param("to", "2026-12-31").with(client))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/payments").param("from", "2026-01-01").with(client))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/payments").param("from", "2026-01-01").param("to", "2026-12-31").param("status", "оплачен").with(client))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/payments/upcoming").param("limit", "50").with(client))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerAndAnonymousCannotUseCabinet() throws Exception {
        mockMvc.perform(get("/api/me").with(user(TestPrincipals.manager()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 8: Implement the controller and error mapping**

```java
package ru.bell.autoleasing.cabinet;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.cabinet.dto.*;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.user.AppUserPrincipal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class CabinetController {
    private final ClientFinanceQueryService finance;
    private final Clock clock;

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal user) {
        return finance.findProfile(user.clientId(), user.username())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }

    @GetMapping("/contracts")
    public List<ContractSummary> contracts(@AuthenticationPrincipal AppUserPrincipal user) {
        return finance.findContracts(user.clientId(), LocalDate.now(clock));
    }

    @GetMapping("/contracts/{contractId}/schedule")
    public List<PaymentRow> schedule(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long contractId) {
        return finance.findSchedule(user.clientId(), contractId, LocalDate.now(clock));
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments(@AuthenticationPrincipal AppUserPrincipal user,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) PaymentStatus status) {
        return finance.findPayments(user.clientId(), from, to, status, LocalDate.now(clock));
    }

    @GetMapping("/payments/summary")
    public PaymentsSummary summary(@AuthenticationPrincipal AppUserPrincipal user,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.summarize(user.clientId(), from, to);
    }

    @GetMapping("/payments/upcoming")
    public UpcomingPayments upcoming(@AuthenticationPrincipal AppUserPrincipal user,
                                     @RequestParam(required = false) Long contractId,
                                     @RequestParam(defaultValue = "3") int limit) {
        return finance.findUpcoming(user.clientId(), contractId, limit, LocalDate.now(clock));
    }
}
```

`ControllerAdvice` — add two handlers (keep the existing ones):

```java
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DefaultErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DefaultErrorResponse("Неверное значение параметра " + e.getName() + ": " + e.getValue()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<DefaultErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DefaultErrorResponse("Не указан обязательный параметр " + e.getParameterName()));
    }
```

- [ ] **Step 9: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "<KEY> Cabinet API: profile, contracts, schedule, payments, summary"
```

---

### Task 9: Cabinet UI and PWA

**Files:**
- Create: `src/main/resources/static/index.html`, `cabinet.html`, `manifest.json`, `sw.js`, `css/app.css`, `js/api.js`, `js/env-badge.js`, `js/login.js`, `js/cabinet.js`, `icons/icon-192.png`, `icons/icon-512.png`
- Test: `src/test/java/ru/bell/autoleasing/ui/StaticUiTest.java`

**Interfaces:**
- Consumes: `POST /api/auth/login` → `{token, role}`, `GET /api/public/info`, cabinet API (Task 8).
- Produces: `js/api.js` exports `saveSession(token, role)`, `clearSession()`, `getRole()`, `homeFor(role)`, `requireRole(role)`, `logout()`, `apiJson(path, options)` (adds the bearer token, redirects to `/index.html?expired=1` on 401, throws `ApiError{status, message, requestId, body}`), `formatMoney(value)`, `formatDate(iso)`, `el(tag, attrs, ...children)` (attribute `testid` → `data-testid`; children become text nodes), `registerServiceWorker()`. Tabs work by `data-tab` buttons and `data-panel` sections (Task 15 adds one more pair). Test ids: `env-badge`, `login-username`, `login-password`, `login-submit`, `login-error`, `login-notice`, `client-name`, `logout-button`, `cabinet-error`, `tab-contracts`, `tab-schedule`, `tab-payments`, `contracts-table`, `contracts-empty`, `contract-row-{id}`, `schedule-contract-select`, `schedule-table`, `payments-from`, `payments-to`, `payments-status`, `payments-apply`, `payments-total`, `payments-table`, `payments-error`.

- [ ] **Step 1: Write the failing test**

```java
package ru.bell.autoleasing.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class StaticUiTest {

    @Autowired
    MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/cabinet.html", "/css/app.css", "/js/api.js", "/js/login.js",
            "/js/cabinet.js", "/js/env-badge.js", "/manifest.json", "/sw.js", "/icons/icon-192.png", "/icons/icon-512.png"})
    void staticFilesArePublic(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void loginPageHasTestIds() throws Exception {
        String html = mockMvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString();
        for (String id : new String[]{"env-badge", "login-username", "login-password", "login-submit", "login-error"}) {
            org.assertj.core.api.Assertions.assertThat(html).contains("data-testid=\"" + id + "\"");
        }
    }

    @Test
    void cabinetPageHasTestIds() throws Exception {
        String html = mockMvc.perform(get("/cabinet.html")).andReturn().getResponse().getContentAsString();
        for (String id : new String[]{"client-name", "logout-button", "tab-contracts", "tab-schedule", "tab-payments",
                "contracts-table", "contracts-empty", "schedule-contract-select", "schedule-table", "payments-from",
                "payments-to", "payments-status", "payments-apply", "payments-total", "payments-table"}) {
            org.assertj.core.api.Assertions.assertThat(html).contains("data-testid=\"" + id + "\"");
        }
    }

    @Test
    void pagesHaveNoInlineScriptsAndSendContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/cabinet.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>"))));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*StaticUiTest"`
Expected: FAIL (404 for every file).

- [ ] **Step 3: Icons** (solid brand-colour squares, Python standard library only)

```bash
mkdir -p src/main/resources/static/icons && python -I -c "
import struct, zlib
def png(size, path):
    raw = b''.join(b'\x00' + bytes([0x1f, 0x4e, 0x79]) * size for _ in range(size))
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
png(192, 'src/main/resources/static/icons/icon-192.png')
png(512, 'src/main/resources/static/icons/icon-512.png')
"
```

- [ ] **Step 4: `manifest.json` and `sw.js`**

```json
{
  "name": "AutoLease — личный кабинет",
  "short_name": "AutoLease",
  "lang": "ru",
  "start_url": "/cabinet.html",
  "scope": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#1f4e79",
  "icons": [
    {"src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png"},
    {"src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable"}
  ]
}
```

`sw.js` — network first for the shell (a new deploy is visible at once), cache only as offline fallback, never touches `/api/`:

```js
const CACHE = 'autolease-shell-v1';
const SHELL = ['/', '/index.html', '/cabinet.html', '/css/app.css', '/js/api.js', '/js/login.js',
  '/js/cabinet.js', '/js/env-badge.js', '/manifest.json', '/icons/icon-192.png', '/icons/icon-512.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return;
  }
  event.respondWith(fetch(event.request)
    .then((response) => {
      const copy = response.clone();
      caches.open(CACHE).then((cache) => cache.put(event.request, copy));
      return response;
    })
    .catch(() => caches.match(event.request)));
});
```

- [ ] **Step 5: `js/api.js` and `js/env-badge.js`**

```js
const TOKEN_KEY = 'autolease.token';
const ROLE_KEY = 'autolease.role';

export function saveSession(token, role) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ROLE_KEY, role);
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
}

export function getRole() {
  return localStorage.getItem(ROLE_KEY);
}

export function homeFor(role) {
  return role === 'MANAGER' ? '/swagger-ui.html' : '/cabinet.html';
}

export function requireRole(role) {
  if (getRole() !== role) {
    clearSession();
    location.replace('/index.html');
    throw new Error('Нет доступа');
  }
}

export function logout() {
  clearSession();
  location.replace('/index.html');
}

export class ApiError extends Error {
  constructor(status, message, requestId, body) {
    super(message);
    this.status = status;
    this.requestId = requestId;
    this.body = body;
  }
}

export async function apiJson(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers.set('Authorization', 'Bearer ' + token);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    clearSession();
    location.replace('/index.html?expired=1');
    throw new ApiError(401, 'Сессия истекла');
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, (body && body.message) || 'Ошибка ' + response.status,
      response.headers.get('X-Request-Id'), body);
  }
  return body;
}

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB' });

export function formatMoney(value) {
  return value === null || value === undefined ? '—' : money.format(value);
}

export function formatDate(iso) {
  if (!iso) return '—';
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year}`;
}

export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'testid') node.dataset.testid = value;
    else if (key === 'class') node.className = value;
    else node.setAttribute(key, value);
  }
  for (const child of children) {
    node.append(child instanceof Node ? child : document.createTextNode(child ?? ''));
  }
  return node;
}

export function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }
}
```

```js
const badge = document.querySelector('[data-testid="env-badge"]');
if (badge) {
  fetch('/api/public/info')
    .then((response) => response.json())
    .then((info) => {
      badge.textContent = `${info.appEnv} · v${info.version}`;
      badge.dataset.env = info.appEnv;
    })
    .catch(() => { badge.textContent = 'нет связи с сервером'; });
}
```

- [ ] **Step 6: Login page** `index.html` and `js/login.js`

```html
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#1f4e79">
  <title>AutoLease — вход</title>
  <link rel="manifest" href="/manifest.json">
  <link rel="icon" href="/icons/icon-192.png">
  <link rel="stylesheet" href="/css/app.css">
  <script type="module" src="/js/login.js"></script>
</head>
<body>
<header class="topbar">
  <span class="brand">AutoLease</span>
  <span class="env-badge" data-testid="env-badge"></span>
</header>
<main class="login">
  <h1>Вход в личный кабинет</h1>
  <p class="notice" data-testid="login-notice" hidden></p>
  <form id="login-form" data-testid="login-form">
    <label>Логин <input name="username" autocomplete="username" required data-testid="login-username"></label>
    <label>Пароль <input name="password" type="password" autocomplete="current-password" required data-testid="login-password"></label>
    <button type="submit" data-testid="login-submit">Войти</button>
    <p class="error" data-testid="login-error" hidden></p>
  </form>
</main>
</body>
</html>
```

```js
import { saveSession, getRole, homeFor, registerServiceWorker } from './api.js';
import './env-badge.js';

registerServiceWorker();
const form = document.getElementById('login-form');
const error = document.querySelector('[data-testid="login-error"]');
const notice = document.querySelector('[data-testid="login-notice"]');

if (new URLSearchParams(location.search).has('expired')) {
  notice.textContent = 'Сессия истекла, войдите снова.';
  notice.hidden = false;
} else if (getRole()) {
  location.replace(homeFor(getRole()));
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  error.hidden = true;
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value }),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Ошибка входа: ' + response.status);
    saveSession(data.token, data.role);
    location.replace(homeFor(data.role));
  } catch (e) {
    error.textContent = e.message;
    error.hidden = false;
  }
});
```

- [ ] **Step 7: Cabinet page** `cabinet.html`

```html
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#1f4e79">
  <title>AutoLease — личный кабинет</title>
  <link rel="manifest" href="/manifest.json">
  <link rel="icon" href="/icons/icon-192.png">
  <link rel="stylesheet" href="/css/app.css">
  <script type="module" src="/js/cabinet.js"></script>
</head>
<body>
<header class="topbar">
  <span class="brand">AutoLease</span>
  <span class="client-name" data-testid="client-name"></span>
  <span class="env-badge" data-testid="env-badge"></span>
  <button type="button" class="link" data-testid="logout-button">Выйти</button>
</header>
<nav class="tabs">
  <button type="button" data-tab="contracts" data-testid="tab-contracts">Договоры</button>
  <button type="button" data-tab="schedule" data-testid="tab-schedule">График</button>
  <button type="button" data-tab="payments" data-testid="tab-payments">Платежи</button>
</nav>
<main>
  <p class="error" data-testid="cabinet-error" hidden></p>

  <section data-panel="contracts">
    <h2>Мои договоры</h2>
    <p class="empty" data-testid="contracts-empty" hidden>Договоров пока нет.</p>
    <div class="table-wrap">
      <table data-testid="contracts-table">
        <thead><tr><th>Договор</th><th>Автомобиль</th><th>Статус</th><th>Начало</th><th>Срок</th>
          <th>Платёж в месяц</th><th>Остаток долга</th><th>Ближайший платёж</th></tr></thead>
        <tbody></tbody>
      </table>
    </div>
  </section>

  <section data-panel="schedule" hidden>
    <h2>График платежей</h2>
    <label>Договор <select data-testid="schedule-contract-select"></select></label>
    <div class="table-wrap">
      <table data-testid="schedule-table">
        <thead><tr><th>№</th><th>Дата платежа</th><th>Сумма</th><th>Выкупная часть</th><th>Удорожание</th>
          <th>НДС</th><th>Пени</th><th>Статус</th><th>Дата оплаты</th></tr></thead>
        <tbody></tbody>
      </table>
    </div>
  </section>

  <section data-panel="payments" hidden>
    <h2>Платежи за период</h2>
    <div class="filters">
      <label>С <input type="date" data-testid="payments-from"></label>
      <label>По <input type="date" data-testid="payments-to"></label>
      <label>Статус
        <select data-testid="payments-status">
          <option value="">Все</option>
          <option value="PAID">Оплачен</option>
          <option value="UNPAID">Не оплачен</option>
          <option value="CANCELLED">Отменён</option>
        </select>
      </label>
      <button type="button" data-testid="payments-apply">Показать</button>
    </div>
    <p class="error" data-testid="payments-error" hidden></p>
    <p class="summary" data-testid="payments-total"></p>
    <div class="table-wrap">
      <table data-testid="payments-table">
        <thead><tr><th>Договор</th><th>Дата платежа</th><th>Сумма</th><th>Статус</th><th>Дата оплаты</th></tr></thead>
        <tbody></tbody>
      </table>
    </div>
  </section>
</main>
</body>
</html>
```

- [ ] **Step 8: `js/cabinet.js`**

```js
import { apiJson, requireRole, logout, formatMoney, formatDate, el, registerServiceWorker } from './api.js';
import './env-badge.js';

requireRole('CLIENT');
registerServiceWorker();

const $ = (testId) => document.querySelector(`[data-testid="${testId}"]`);
$('logout-button').addEventListener('click', logout);

export function showTab(name) {
  document.querySelectorAll('[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== name; });
}
document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

function showError(testId, error) {
  const node = $(testId);
  node.textContent = error.requestId ? `${error.message} (код запроса ${error.requestId})` : error.message;
  node.hidden = false;
}

function statusCell(row) {
  const text = row.overdue ? `${row.statusText}, просрочен` : row.statusText;
  return el('td', { class: `status status-${row.status.toLowerCase()}${row.overdue ? ' overdue' : ''}` }, text);
}

const money = (value) => el('td', { class: 'num' }, formatMoney(value));

async function loadProfile() {
  const me = await apiJson('/api/me');
  $('client-name').textContent = `${me.legalName} · ИНН ${me.inn}`;
}

async function loadSchedule(contractId) {
  const rows = await apiJson(`/api/me/contracts/${encodeURIComponent(contractId)}/schedule`);
  $('schedule-table').querySelector('tbody').replaceChildren(...rows.map((p) => el('tr', { class: p.overdue ? 'overdue' : '' },
    el('td', {}, String(p.paymentId)), el('td', {}, formatDate(p.dueDate)), money(p.amount), money(p.principalPart),
    money(p.markup), money(p.vat), money(p.penalty), statusCell(p), el('td', {}, formatDate(p.paidDate)))));
}

async function loadContracts() {
  const contracts = await apiJson('/api/me/contracts');
  $('contracts-table').querySelector('tbody').replaceChildren(...contracts.map((c) => el('tr', { testid: `contract-row-${c.contractId}` },
    el('td', {}, c.contractNumber), el('td', {}, c.car), el('td', {}, c.statusText), el('td', {}, formatDate(c.startDate)),
    el('td', {}, `${c.durationInMonths} мес.`), money(c.monthlyPayment), money(c.remainingDebt),
    el('td', {}, formatDate(c.nextDueDate)))));
  $('contracts-empty').hidden = contracts.length > 0;
  const select = $('schedule-contract-select');
  select.replaceChildren(...contracts.map((c) => el('option', { value: String(c.contractId) }, `${c.contractNumber} — ${c.car}`)));
  if (contracts.length > 0) await loadSchedule(contracts[0].contractId);
}

async function loadPayments() {
  $('payments-error').hidden = true;
  const from = $('payments-from').value;
  const to = $('payments-to').value;
  const status = $('payments-status').value;
  try {
    const query = new URLSearchParams({ from, to });
    if (status) query.set('status', status);
    const [rows, summary] = await Promise.all([
      apiJson('/api/me/payments?' + query),
      apiJson('/api/me/payments/summary?' + new URLSearchParams({ from, to })),
    ]);
    $('payments-table').querySelector('tbody').replaceChildren(...rows.map((p) => el('tr', {},
      el('td', {}, p.contractNumber), el('td', {}, formatDate(p.dueDate)), money(p.amount), statusCell(p),
      el('td', {}, formatDate(p.paidDate)))));
    $('payments-total').textContent = `Оплачено: ${formatMoney(summary.paidTotal)} (${summary.paidCount}) · `
      + `Начислено: ${formatMoney(summary.accruedTotal)} (${summary.accruedCount})`;
  } catch (e) {
    showError('payments-error', e);
  }
}

const year = new Date().getFullYear();
$('payments-from').value = `${year}-01-01`;
$('payments-to').value = `${year}-12-31`;
$('payments-apply').addEventListener('click', loadPayments);
$('schedule-contract-select').addEventListener('change', (e) => loadSchedule(e.target.value).catch((err) => showError('cabinet-error', err)));

showTab('contracts');
Promise.all([loadProfile(), loadContracts(), loadPayments()]).catch((e) => showError('cabinet-error', e));
```

- [ ] **Step 9: `css/app.css`** (mobile first; tables scroll inside `.table-wrap`, the page itself never scrolls sideways)

```css
:root { --brand: #1f4e79; --bg: #f5f7fa; --text: #1b1f24; --muted: #5b6573; --danger: #b42318; --warn: #b54708; --ok: #027a48; }
* { box-sizing: border-box; }
body { margin: 0; font: 16px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: var(--text); background: var(--bg); }
.topbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; padding: 12px 16px; background: var(--brand); color: #fff; }
.brand { font-weight: 700; }
.client-name { flex: 1 1 auto; font-size: 14px; }
.env-badge { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: rgba(255, 255, 255, .2); }
button { font: inherit; padding: 8px 14px; border: 0; border-radius: 6px; background: var(--brand); color: #fff; cursor: pointer; }
button.link { background: transparent; text-decoration: underline; padding: 4px; }
input, select, textarea { font: inherit; padding: 8px; border: 1px solid #c8ced6; border-radius: 6px; max-width: 100%; }
label { display: inline-flex; flex-direction: column; gap: 4px; font-size: 14px; color: var(--muted); }
main { padding: 16px; max-width: 1200px; margin: 0 auto; }
.login form { display: flex; flex-direction: column; gap: 12px; max-width: 360px; }
.tabs { display: flex; gap: 4px; padding: 8px 16px 0; overflow-x: auto; background: #fff; border-bottom: 1px solid #dde2e8; }
.tabs button { background: transparent; color: var(--brand); border-radius: 6px 6px 0 0; }
.tabs button.active { background: var(--brand); color: #fff; }
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; margin-bottom: 12px; }
.table-wrap { overflow-x: auto; background: #fff; border-radius: 8px; }
table { border-collapse: collapse; width: 100%; font-size: 14px; }
th, td { padding: 8px 10px; border-bottom: 1px solid #eef1f4; text-align: left; white-space: nowrap; }
td.num { text-align: right; font-variant-numeric: tabular-nums; }
tr.overdue td, td.overdue { color: var(--danger); }
.status-paid { color: var(--ok); }
.status-cancelled { color: var(--muted); }
.error { color: var(--danger); }
.notice { color: var(--warn); }
.empty, .summary { color: var(--muted); }
@media (min-width: 768px) { .login { padding-top: 48px; } }
```

- [ ] **Step 10: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "<KEY> Client cabinet web UI with installable PWA shell"
```

- [ ] **Step 12: Stand check (orchestrator) and merge story C**

Rebuild `app-it`, open `http://localhost:8081/` in the browser pane, log in as `client01` (password from `application.yml`), check the three tabs; switch to a 375 px wide viewport and confirm there is no horizontal page scroll. Then:

```bash
./gradlew test && git checkout develop && git merge --no-ff feature/<KEY>-client-cabinet -m "<KEY> Merge client cabinet" && ./gradlew test
```

---

## Story D — Leasing assistant

### Task 10: Agent tools

**Files:**
- Create: `agent/tool/ToolSpec.java`, `AgentTool.java`, `ToolContext.java`, `ToolArgs.java`, `ToolArgumentException.java`, `ToolRegistry.java`, `ContractsOverview.java`, `PaymentsSummaryTool.java`, `ScheduleTool.java`, `ContractsOverviewTool.java`
- Test: `src/test/java/ru/bell/autoleasing/agent/tool/ToolArgsTest.java`, `AgentToolsTest.java`

**Interfaces:**
- Consumes: `ClientFinanceQueryService` and DTOs (Task 8), fixture `/sql/finance-fixture.sql`.
- Produces:
  - `record ToolSpec(String name, String description, Map<String, Object> parameters)` with `record Param(String name, String type, String description, boolean required)` and `static ToolSpec of(String name, String description, List<Param> params)` (JSON Schema object, insertion-ordered)
  - `interface AgentTool { String name(); ToolSpec spec(); Object execute(Map<String, Object> arguments, ToolContext context); }`
  - `record ToolContext(long clientId, LocalDate today)`
  - `ToolArgumentException extends RuntimeException` (message in Russian, fed back to the model)
  - `ToolRegistry`: `Optional<AgentTool> find(String name)`, `List<ToolSpec> specs(Collection<String> names)` (sorted by name, unknown names skipped), `Set<String> names()` (sorted)
  - Tool names: `PaymentsSummaryTool.NAME = "payments_summary"` (args `period_from`, `period_to` required, `ГГГГ-ММ-ДД`; returns `PaymentsSummary`), `ScheduleTool.NAME = "schedule"` (args `contract_id` optional, `limit` optional 1–12 default 3; returns `UpcomingPayments`), `ContractsOverviewTool.NAME = "contracts_overview"` (no args; returns `record ContractsOverview(List<ContractSummary> contracts, BigDecimal totalRemainingDebt)`).
  - Tools never read a client id from `arguments`; only from `ToolContext`.

- [ ] **Step 1: Create the branch**

```bash
git checkout develop && git checkout -b feature/<KEY>-leasing-assistant
```

- [ ] **Step 2: Write the failing tests**

```java
package ru.bell.autoleasing.agent.tool;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolArgsTest {

    @Test
    void parsesDatesAndNumbersInDifferentShapes() {
        assertThat(ToolArgs.requiredDate(Map.of("d", " 2026-09-01 "), "d")).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(ToolArgs.optionalLong(Map.of("id", 2001), "id")).isEqualTo(2001L);
        assertThat(ToolArgs.optionalLong(Map.of("id", "2001"), "id")).isEqualTo(2001L);
        assertThat(ToolArgs.optionalLong(Map.of(), "id")).isNull();
        assertThat(ToolArgs.optionalInt(Map.of(), "limit", 3, 1, 12)).isEqualTo(3);
        assertThat(ToolArgs.optionalInt(Map.of("limit", 5.0), "limit", 3, 1, 12)).isEqualTo(5);
    }

    @Test
    void rejectsBadValuesWithRussianMessage() {
        assertThatThrownBy(() -> ToolArgs.requiredDate(Map.of(), "period_from"))
                .isInstanceOf(ToolArgumentException.class).hasMessageContaining("period_from");
        assertThatThrownBy(() -> ToolArgs.requiredDate(Map.of("d", "01.09.2026"), "d"))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ToolArgs.optionalLong(Map.of("id", "два"), "id"))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ToolArgs.optionalInt(Map.of("limit", 40), "limit", 3, 1, 12))
                .isInstanceOf(ToolArgumentException.class);
    }
}
```

```java
package ru.bell.autoleasing.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.cabinet.dto.ContractSummary;
import ru.bell.autoleasing.cabinet.dto.PaymentsSummary;
import ru.bell.autoleasing.cabinet.dto.UpcomingPayments;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.support.IntegrationTest;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AgentToolsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Autowired
    ToolRegistry registry;

    private Object run(String tool, Map<String, Object> args, long clientId) {
        return registry.find(tool).orElseThrow().execute(args, new ToolContext(clientId, TODAY));
    }

    @Test
    void registryHasThreeToolsSortedByName() {
        assertThat(registry.names()).containsExactly("contracts_overview", "payments_summary", "schedule");
        assertThat(registry.specs(registry.names())).allSatisfy(spec -> assertThat(spec.parameters().get("type")).isEqualTo("object"));
    }

    @Test
    void paymentsSummaryUsesInclusivePeriod() {
        PaymentsSummary s = (PaymentsSummary) run("payments_summary",
                Map.of("period_from", "2026-09-01", "period_to", "2026-09-30"), 1001);
        assertThat(s.paidTotal()).isEqualByComparingTo("20030.00");
        assertThat(s.paidCount()).isEqualTo(2);
        assertThat(s.accruedTotal()).isEqualByComparingTo("10000.00");
    }

    @Test
    void paymentsSummaryNeedsBothDatesInOrder() {
        assertThatThrownBy(() -> run("payments_summary", Map.of("period_from", "2026-09-01"), 1001))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> run("payments_summary", Map.of("period_from", "2026-10-01", "period_to", "2026-09-01"), 1001))
                .isInstanceOf(ToolArgumentException.class);
    }

    @Test
    void scheduleReturnsNextPaymentAndOverdue() {
        UpcomingPayments u = (UpcomingPayments) run("schedule", Map.of(), 1002);
        assertThat(u.nextPayment().dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(u.overdueCount()).isEqualTo(1);
        assertThat(u.overdueTotal()).isEqualByComparingTo("20000.00");
    }

    @Test
    void scheduleAcceptsContractIdAsString() {
        UpcomingPayments u = (UpcomingPayments) run("schedule", Map.of("contract_id", "2001", "limit", 2), 1001);
        assertThat(u.nextPayment().paymentId()).isEqualTo(4L);
        assertThat(u.upcoming()).hasSize(2);
    }

    @Test
    void scheduleRefusesOtherClientsContract() {
        assertThatThrownBy(() -> run("schedule", Map.of("contract_id", 2003), 1001)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void contractsOverviewIgnoresClientIdArgument() {
        ContractsOverview o = (ContractsOverview) run("contracts_overview", Map.of("client_id", 1002), 1001);
        assertThat(o.contracts()).extracting(ContractSummary::contractId).containsExactly(2002L, 2001L);
        assertThat(o.totalRemainingDebt()).isEqualByComparingTo("30000.00");
    }

    @Test
    void clientWithoutContractsGetsEmptyOverview() {
        ContractsOverview o = (ContractsOverview) run("contracts_overview", Map.of(), 1003);
        assertThat(o.contracts()).isEmpty();
        assertThat(o.totalRemainingDebt()).isEqualByComparingTo("0.00");
        assertThat(((UpcomingPayments) run("schedule", Map.of(), 1003)).nextPayment()).isNull();
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew test --tests "*ToolArgsTest" --tests "*AgentToolsTest"`
Expected: FAIL (compilation).

- [ ] **Step 4: Implement**

```java
package ru.bell.autoleasing.agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolSpec(String name, String description, Map<String, Object> parameters) {

    public record Param(String name, String type, String description, boolean required) {
    }

    public static ToolSpec of(String name, String description, List<Param> params) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Param p : params) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", p.type());
            property.put("description", p.description());
            properties.put(p.name(), property);
            if (p.required()) {
                required.add(p.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return new ToolSpec(name, description, schema);
    }
}
```

```java
package ru.bell.autoleasing.agent.tool;

import java.util.Map;

public interface AgentTool {
    String name();

    ToolSpec spec();

    Object execute(Map<String, Object> arguments, ToolContext context);
}
```

`ToolContext`: `public record ToolContext(long clientId, java.time.LocalDate today) {}`
`ToolArgumentException`: `public class ToolArgumentException extends RuntimeException { public ToolArgumentException(String message) { super(message); } }`
`ContractsOverview`: `public record ContractsOverview(List<ContractSummary> contracts, BigDecimal totalRemainingDebt) {}`

```java
package ru.bell.autoleasing.agent.tool;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class ToolArgs {
    private ToolArgs() {
    }

    public static LocalDate requiredDate(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new ToolArgumentException("Параметр " + name + " обязателен, формат ГГГГ-ММ-ДД");
        }
        try {
            return LocalDate.parse(value.toString().trim());
        } catch (DateTimeParseException e) {
            throw new ToolArgumentException("Параметр " + name + " должен быть датой ГГГГ-ММ-ДД, получено: " + value);
        }
    }

    public static Long optionalLong(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new ToolArgumentException("Параметр " + name + " должен быть целым числом, получено: " + value);
        }
    }

    public static int optionalInt(Map<String, Object> args, String name, int defaultValue, int min, int max) {
        Long value = optionalLong(args, name);
        if (value == null) {
            return defaultValue;
        }
        if (value < min || value > max) {
            throw new ToolArgumentException("Параметр " + name + " должен быть от " + min + " до " + max + ", получено: " + value);
        }
        return value.intValue();
    }
}
```

```java
package ru.bell.autoleasing.agent.tool;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new TreeMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name));
    }

    public List<ToolSpec> specs(Collection<String> names) {
        return names.stream().sorted().map(tools::get).filter(Objects::nonNull).map(AgentTool::spec).toList();
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}
```

```java
package ru.bell.autoleasing.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.cabinet.ClientFinanceQueryService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentsSummaryTool implements AgentTool {
    public static final String NAME = "payments_summary";
    private static final ToolSpec SPEC = ToolSpec.of(NAME,
            "Сколько клиент оплатил и сколько ему начислено к оплате за период (даты включительно).",
            List.of(new ToolSpec.Param("period_from", "string", "Начало периода, ГГГГ-ММ-ДД", true),
                    new ToolSpec.Param("period_to", "string", "Конец периода, ГГГГ-ММ-ДД", true)));

    private final ClientFinanceQueryService finance;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) {
        LocalDate from = ToolArgs.requiredDate(arguments, "period_from");
        LocalDate to = ToolArgs.requiredDate(arguments, "period_to");
        if (from.isAfter(to)) {
            throw new ToolArgumentException("period_from не может быть позже period_to");
        }
        return finance.summarize(context.clientId(), from, to);
    }
}
```

```java
package ru.bell.autoleasing.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.cabinet.ClientFinanceQueryService;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScheduleTool implements AgentTool {
    public static final String NAME = "schedule";
    private static final ToolSpec SPEC = ToolSpec.of(NAME,
            "Ближайшие неоплаченные платежи клиента по графику, следующий платёж и просроченные платежи.",
            List.of(new ToolSpec.Param("contract_id", "integer", "Номер договора (id); если не указан — по всем договорам", false),
                    new ToolSpec.Param("limit", "integer", "Сколько платежей показать, от 1 до 12, по умолчанию 3", false)));

    private final ClientFinanceQueryService finance;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) {
        Long contractId = ToolArgs.optionalLong(arguments, "contract_id");
        int limit = ToolArgs.optionalInt(arguments, "limit", 3, 1, 12);
        return finance.findUpcoming(context.clientId(), contractId, limit, context.today());
    }
}
```

```java
package ru.bell.autoleasing.agent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.cabinet.ClientFinanceQueryService;
import ru.bell.autoleasing.cabinet.dto.ContractSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContractsOverviewTool implements AgentTool {
    public static final String NAME = "contracts_overview";
    private static final ToolSpec SPEC = ToolSpec.of(NAME,
            "Договоры лизинга клиента: автомобиль, статус, платёж в месяц, остаток долга и общий остаток долга.",
            List.of());

    private final ClientFinanceQueryService finance;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) {
        List<ContractSummary> contracts = finance.findContracts(context.clientId(), context.today());
        BigDecimal total = contracts.stream().map(ContractSummary::remainingDebt)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new ContractsOverview(contracts, total);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "<KEY> Agent tools: payments summary, schedule, contracts overview"
```

---

### Task 11: Tool router

**Files:**
- Create: `agent/router/RoutingDecision.java`, `agent/router/ToolRouter.java`
- Test: `src/test/java/ru/bell/autoleasing/agent/router/ToolRouterTest.java`

**Interfaces:**
- Consumes: tool names (Task 10).
- Produces: `record RoutingDecision(Set<String> allowedTools, String rule)` (`allowedTools` sorted); `ToolRouter.route(String question): RoutingDecision`. Rules on the lower-cased question with `ё` → `е`: `PAYMENTS` = `оплат|заплат|платил|списал|начисл` → `payments_summary`; `SCHEDULE` = `график|следующ|ближайш|когда\s.*плат|срок` → `schedule`; `CONTRACTS` = `договор|машин|автомоб|долг|остат` → `contracts_overview`. Several matches → union, rule names joined with `+` in rule order. No match → all three tools, rule `FALLBACK_ALL`.

- [ ] **Step 1: Write the failing test**

```java
package ru.bell.autoleasing.agent.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRouterTest {
    private final ToolRouter router = new ToolRouter();

    @Test
    void paymentsQuestion() {
        RoutingDecision d = router.route("Сколько я заплатил в сентябре?");
        assertThat(d.allowedTools()).containsExactly("payments_summary");
        assertThat(d.rule()).isEqualTo("PAYMENTS");
    }

    @Test
    void scheduleQuestionWithLetterYo() {
        RoutingDecision d = router.route("Когда следующий платёж?");
        assertThat(d.allowedTools()).containsExactly("schedule");
        assertThat(d.rule()).isEqualTo("SCHEDULE");
    }

    @Test
    void contractsQuestion() {
        assertThat(router.route("Какой остаток долга по договору?").allowedTools()).containsExactly("contracts_overview");
    }

    @Test
    void severalIntentsAreCombined() {
        RoutingDecision d = router.route("Покажи график и сколько уже оплачено");
        assertThat(d.allowedTools()).containsExactly("payments_summary", "schedule");
        assertThat(d.rule()).isEqualTo("PAYMENTS+SCHEDULE");
    }

    @Test
    void upperCaseIsRecognised() {
        assertThat(router.route("ОПЛАТА ЗА МАЙ").allowedTools()).containsExactly("payments_summary");
    }

    @Test
    void unknownBlankAndNullFallBackToAllTools() {
        for (String q : new String[]{"Привет!", "   ", null}) {
            RoutingDecision d = router.route(q);
            assertThat(d.allowedTools()).containsExactly("contracts_overview", "payments_summary", "schedule");
            assertThat(d.rule()).isEqualTo("FALLBACK_ALL");
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*ToolRouterTest"`
Expected: FAIL (compilation).

- [ ] **Step 3: Implement**

`RoutingDecision`: `public record RoutingDecision(Set<String> allowedTools, String rule) {}`

```java
package ru.bell.autoleasing.agent.router;

import org.springframework.stereotype.Component;
import ru.bell.autoleasing.agent.tool.ContractsOverviewTool;
import ru.bell.autoleasing.agent.tool.PaymentsSummaryTool;
import ru.bell.autoleasing.agent.tool.ScheduleTool;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class ToolRouter {
    private record Rule(String name, Pattern pattern, String tool) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("PAYMENTS", Pattern.compile("оплат|заплат|платил|списал|начисл"), PaymentsSummaryTool.NAME),
            new Rule("SCHEDULE", Pattern.compile("график|следующ|ближайш|когда\\s.*плат|срок"), ScheduleTool.NAME),
            new Rule("CONTRACTS", Pattern.compile("договор|машин|автомоб|долг|остат"), ContractsOverviewTool.NAME));

    private static final Set<String> ALL_TOOLS = Collections.unmodifiableSet(new TreeSet<>(
            List.of(PaymentsSummaryTool.NAME, ScheduleTool.NAME, ContractsOverviewTool.NAME)));

    public RoutingDecision route(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT).replace('ё', 'е');
        Set<String> tools = new TreeSet<>();
        List<String> matched = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                tools.add(rule.tool());
                matched.add(rule.name());
            }
        }
        if (tools.isEmpty()) {
            return new RoutingDecision(ALL_TOOLS, "FALLBACK_ALL");
        }
        return new RoutingDecision(Collections.unmodifiableSet(tools), String.join("+", matched));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "*ToolRouterTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "<KEY> Deterministic tool router by question intent"
```

---

### Task 12: Language model clients — Ollama and mock

**Files:**
- Create: `agent/AgentProperties.java`, `agent/llm/LlmClient.java`, `LlmMessage.java`, `LlmToolCall.java`, `LlmRequest.java`, `LlmResponse.java`, `LlmUnavailableException.java`, `OllamaLlmClient.java`, `MockLlmClient.java`, `LlmConfig.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/ru/bell/autoleasing/agent/llm/OllamaLlmClientTest.java`, `MockLlmClientTest.java`

**Interfaces:**
- Consumes: `ToolSpec`, tool names (Task 10), `Clock`, Spring `ObjectMapper`, `RestClient.Builder`.
- Produces:
  - `@ConfigurationProperties("autolease.agent") record AgentProperties(int maxSteps, int questionMaxLength, String promptVersion, Llm llm)` with `record Llm(String provider, String model, String baseUrl, int numCtx, double temperature, int timeoutSeconds)`
  - `record LlmMessage(String role, String content, List<LlmToolCall> toolCalls, String toolName)` + factories `system(String)`, `user(String)`, `assistant(String, List<LlmToolCall>)`, `tool(String toolName, String content)`
  - `record LlmToolCall(String name, Map<String, Object> arguments)`
  - `record LlmRequest(List<LlmMessage> messages, List<ToolSpec> tools)`
  - `record LlmResponse(String content, List<LlmToolCall> toolCalls, int promptTokens, int completionTokens, String model)` + `boolean hasToolCalls()`
  - `interface LlmClient { LlmResponse chat(LlmRequest request); String provider(); String model(); }`
  - `LlmUnavailableException extends RuntimeException` (any transport or HTTP error)
  - bean `LlmClient` chosen by `autolease.agent.llm.provider` = `ollama` | `mock`
  - Mock behaviour: first turn (no tool results after the last user message) → one call for every offered tool; `payments_summary` gets the previous month if the question contains «прошл», the current year if it contains «год», otherwise the current month; second turn → JSON `{"answer","figures","warnings"}` built from tool results (`Оплачено`, `Начислено`, `Следующий платёж`, `Остаток долга`, unit `RUB`).

- [ ] **Step 1: Write the failing tests**

```java
package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaLlmClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AgentProperties.Llm props = new AgentProperties.Llm("ollama", "qwen3:8b", "http://ollama.test", 8192, 0.0, 30);
    private final OllamaLlmClient client = new OllamaLlmClient(builder, props, new ObjectMapper());

    private static LlmRequest scheduleQuestion() {
        return new LlmRequest(List.of(LlmMessage.system("s"), LlmMessage.user("Когда платить?")),
                List.of(ToolSpec.of("schedule", "d", List.of())));
    }

    @Test
    void sendsModelOptionsAndToolsAndParsesToolCall() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3:8b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.think").value(false))
                .andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("schedule"))
                .andExpect(jsonPath("$.messages[1].content").value("Когда платить?"))
                .andRespond(withSuccess("""
                        {"model":"qwen3:8b","message":{"role":"assistant","content":"",
                         "tool_calls":[{"function":{"name":"schedule","arguments":{"limit":2}}}]},
                         "done":true,"prompt_eval_count":321,"eval_count":12}
                        """, MediaType.APPLICATION_JSON));

        LlmResponse response = client.chat(scheduleQuestion());

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls().get(0).name()).isEqualTo("schedule");
        assertThat(response.toolCalls().get(0).arguments()).containsEntry("limit", 2);
        assertThat(response.promptTokens()).isEqualTo(321);
        assertThat(response.completionTokens()).isEqualTo(12);
    }

    @Test
    void sendsToolResultsBackWithToolName() {
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("schedule"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_name").value("schedule"))
                .andRespond(withSuccess("""
                        {"model":"qwen3:8b","message":{"role":"assistant","content":"{\\"answer\\":\\"ok\\"}"},"done":true}
                        """, MediaType.APPLICATION_JSON));

        LlmResponse response = client.chat(new LlmRequest(List.of(LlmMessage.system("s"), LlmMessage.user("q"),
                LlmMessage.assistant("", List.of(new LlmToolCall("schedule", Map.of()))),
                LlmMessage.tool("schedule", "{}")), List.of()));

        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.content()).isEqualTo("{\"answer\":\"ok\"}");
    }

    @Test
    void parsesArgumentsGivenAsString() {
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withSuccess("""
                {"message":{"role":"assistant","content":"",
                 "tool_calls":[{"function":{"name":"schedule","arguments":"{\\"contract_id\\": 7}"}}]},"done":true}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.chat(scheduleQuestion()).toolCalls().get(0).arguments()).containsEntry("contract_id", 7);
    }

    @Test
    void serverErrorAndMissingModelBecomeUnavailable() {
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withServerError());
        assertThatThrownBy(() -> client.chat(scheduleQuestion())).isInstanceOf(LlmUnavailableException.class);

        server.reset();
        server.expect(requestTo("http://ollama.test/api/chat")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":\"model 'qwen3:8b' not found\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.chat(scheduleQuestion())).isInstanceOf(LlmUnavailableException.class);
    }
}
```

```java
package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZoneId.of("Europe/Moscow"));
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final MockLlmClient client = new MockLlmClient(clock, mapper);
    private final List<ToolSpec> paymentsTool = List.of(ToolSpec.of("payments_summary", "d", List.of()));

    @Test
    void firstTurnAsksForPreviousMonth() {
        LlmResponse r = client.chat(new LlmRequest(List.of(LlmMessage.system("s"),
                LlmMessage.user("Сколько я заплатил в прошлом месяце?")), paymentsTool));

        assertThat(r.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("payments_summary");
            assertThat(call.arguments()).containsEntry("period_from", "2026-09-01").containsEntry("period_to", "2026-09-30");
        });
    }

    @Test
    void firstTurnDefaultsToCurrentMonth() {
        LlmResponse r = client.chat(new LlmRequest(List.of(LlmMessage.user("Сколько я заплатил?")), paymentsTool));
        assertThat(r.toolCalls().get(0).arguments()).containsEntry("period_from", "2026-10-01").containsEntry("period_to", "2026-10-31");
    }

    @Test
    void secondTurnBuildsJsonAnswerFromToolResults() throws Exception {
        String toolResult = "{\"periodFrom\":\"2026-09-01\",\"periodTo\":\"2026-09-30\",\"paidTotal\":20030.00,"
                + "\"paidCount\":2,\"accruedTotal\":10000.00,\"accruedCount\":1,\"currency\":\"RUB\"}";
        LlmRequest request = new LlmRequest(List.of(LlmMessage.system("s"), LlmMessage.user("Сколько я заплатил в прошлом месяце?"),
                LlmMessage.assistant("", List.of(new LlmToolCall("payments_summary", java.util.Map.of()))),
                LlmMessage.tool("payments_summary", toolResult)), paymentsTool);

        LlmResponse r = client.chat(request);

        assertThat(r.hasToolCalls()).isFalse();
        JsonNode answer = mapper.readTree(r.content());
        assertThat(answer.path("figures").get(0).path("label").asText()).isEqualTo("Оплачено");
        assertThat(answer.path("figures").get(0).path("value").decimalValue()).isEqualByComparingTo("20030.00");
        assertThat(answer.path("warnings").isArray()).isTrue();
        assertThat(client.chat(request).content()).isEqualTo(r.content());
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "*LlmClientTest"`
Expected: FAIL (compilation).

- [ ] **Step 3: Implement the model types**

```java
package ru.bell.autoleasing.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("autolease.agent")
public record AgentProperties(int maxSteps, int questionMaxLength, String promptVersion, Llm llm) {
    public record Llm(String provider, String model, String baseUrl, int numCtx, double temperature, int timeoutSeconds) {
    }
}
```

```java
package ru.bell.autoleasing.agent.llm;

import java.util.List;

public record LlmMessage(String role, String content, List<LlmToolCall> toolCalls, String toolName) {
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, List.of(), null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, List.of(), null);
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls == null ? List.of() : List.copyOf(toolCalls), null);
    }

    public static LlmMessage tool(String toolName, String content) {
        return new LlmMessage("tool", content, List.of(), toolName);
    }
}
```

`LlmToolCall`: `public record LlmToolCall(String name, Map<String, Object> arguments) {}`
`LlmRequest`: `public record LlmRequest(List<LlmMessage> messages, List<ToolSpec> tools) {}`
`LlmResponse`: record as in **Interfaces** with `public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }`
`LlmClient`: interface as in **Interfaces**.
`LlmUnavailableException`: `public LlmUnavailableException(String message, Throwable cause) { super(message, cause); }`

- [ ] **Step 4: Implement `OllamaLlmClient`**

```java
package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OllamaLlmClient implements LlmClient {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final AgentProperties.Llm props;
    private final ObjectMapper mapper;

    public OllamaLlmClient(RestClient.Builder builder, AgentProperties.Llm props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.restClient = builder.baseUrl(props.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String model() {
        return props.model();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        JsonNode response;
        try {
            response = restClient.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(request)).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Ollama недоступна: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new LlmUnavailableException("Пустой ответ Ollama", null);
        }
        return parse(response);
    }

    private ObjectNode buildBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.model());
        body.put("stream", false);
        body.put("think", false);
        ObjectNode options = body.putObject("options");
        options.put("num_ctx", props.numCtx());
        options.put("temperature", props.temperature());
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role());
            node.put("content", message.content() == null ? "" : message.content());
            if (message.toolName() != null) {
                node.put("tool_name", message.toolName());
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                ArrayNode calls = node.putArray("tool_calls");
                for (LlmToolCall call : message.toolCalls()) {
                    ObjectNode function = calls.addObject().putObject("function");
                    function.put("name", call.name());
                    function.set("arguments", mapper.valueToTree(call.arguments()));
                }
            }
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec spec : request.tools()) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", spec.name());
                function.put("description", spec.description());
                function.set("parameters", mapper.valueToTree(spec.parameters()));
            }
        }
        return body;
    }

    private LlmResponse parse(JsonNode response) {
        JsonNode message = response.path("message");
        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            calls.add(new LlmToolCall(function.path("name").asText(), arguments(function.path("arguments"))));
        }
        return new LlmResponse(message.path("content").asText(""), calls, response.path("prompt_eval_count").asInt(0),
                response.path("eval_count").asInt(0), response.path("model").asText(props.model()));
    }

    private Map<String, Object> arguments(JsonNode node) {
        try {
            if (node.isTextual()) {
                return mapper.readValue(node.asText(), MAP);
            }
            if (node.isObject()) {
                return mapper.convertValue(node, MAP);
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Map.of("_raw", node.toString());
        }
        return Map.of();
    }
}
```

- [ ] **Step 5: Implement `MockLlmClient` and `LlmConfig`**

```java
package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.bell.autoleasing.agent.tool.ContractsOverviewTool;
import ru.bell.autoleasing.agent.tool.PaymentsSummaryTool;
import ru.bell.autoleasing.agent.tool.ScheduleTool;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** Deterministic stand-in for the model: for JUnit, continuous integration and load tests. */
public class MockLlmClient implements LlmClient {
    private final Clock clock;
    private final ObjectMapper mapper;

    public MockLlmClient(Clock clock, ObjectMapper mapper) {
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-1";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        List<LlmMessage> messages = request.messages();
        int lastUser = 0;
        for (int i = 0; i < messages.size(); i++) {
            if ("user".equals(messages.get(i).role())) {
                lastUser = i;
            }
        }
        String question = messages.get(lastUser).content();
        List<LlmMessage> toolResults = messages.subList(lastUser + 1, messages.size()).stream()
                .filter(m -> "tool".equals(m.role())).toList();
        int promptTokens = messages.stream().mapToInt(m -> m.content() == null ? 0 : m.content().length()).sum() / 4;
        if (toolResults.isEmpty() && request.tools() != null && !request.tools().isEmpty()) {
            List<LlmToolCall> calls = request.tools().stream()
                    .map(tool -> new LlmToolCall(tool.name(), argumentsFor(tool.name(), question))).toList();
            return new LlmResponse("", calls, promptTokens, 10 * calls.size(), model());
        }
        String content = finalAnswer(toolResults);
        return new LlmResponse(content, List.of(), promptTokens, content.length() / 4, model());
    }

    private Map<String, Object> argumentsFor(String tool, String question) {
        if (!PaymentsSummaryTool.NAME.equals(tool)) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(clock);
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        LocalDate from;
        LocalDate to;
        if (q.contains("прошл")) {
            YearMonth month = YearMonth.from(today).minusMonths(1);
            from = month.atDay(1);
            to = month.atEndOfMonth();
        } else if (q.contains("год")) {
            from = today.withDayOfYear(1);
            to = today.withDayOfYear(today.lengthOfYear());
        } else {
            YearMonth month = YearMonth.from(today);
            from = month.atDay(1);
            to = month.atEndOfMonth();
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("period_from", from.toString());
        args.put("period_to", to.toString());
        return args;
    }

    private String finalAnswer(List<LlmMessage> toolResults) {
        List<Map<String, Object>> figures = new ArrayList<>();
        for (LlmMessage result : toolResults) {
            JsonNode node = read(result.content());
            if (node.has("error")) {
                continue;
            }
            switch (result.toolName()) {
                case PaymentsSummaryTool.NAME -> {
                    String period = node.path("periodFrom").asText() + " — " + node.path("periodTo").asText();
                    figures.add(figure("Оплачено", node.path("paidTotal").decimalValue(), period));
                    figures.add(figure("Начислено", node.path("accruedTotal").decimalValue(), period));
                }
                case ScheduleTool.NAME -> {
                    JsonNode next = node.path("nextPayment");
                    if (next.isObject()) {
                        figures.add(figure("Следующий платёж", next.path("amount").decimalValue(), next.path("dueDate").asText()));
                    }
                }
                case ContractsOverviewTool.NAME ->
                        figures.add(figure("Остаток долга", node.path("totalRemainingDebt").decimalValue(), null));
                default -> {
                }
            }
        }
        String answer = figures.isEmpty()
                ? "Не удалось найти данные по вашему вопросу."
                : "По вашим данным: " + figures.stream().map(f -> f.get("label") + " — " + f.get("value") + " руб.")
                .collect(Collectors.joining("; ")) + ".";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        body.put("figures", figures);
        body.put("warnings", List.of());
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> figure(String label, BigDecimal value, String period) {
        Map<String, Object> figure = new LinkedHashMap<>();
        figure.put("label", label);
        figure.put("value", value);
        figure.put("unit", "RUB");
        figure.put("period", period);
        return figure;
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode().put("error", "invalid tool result");
        }
    }
}
```

```java
package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.bell.autoleasing.agent.AgentProperties;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    public LlmClient llmClient(AgentProperties properties, RestClient.Builder builder, ObjectMapper mapper, Clock clock) {
        AgentProperties.Llm llm = properties.llm();
        return switch (llm.provider()) {
            case "ollama" -> {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(Duration.ofSeconds(llm.timeoutSeconds()));
                yield new OllamaLlmClient(builder.clone().requestFactory(factory), llm, mapper);
            }
            case "mock" -> new MockLlmClient(clock, mapper);
            default -> throw new IllegalStateException("Неизвестный провайдер языковой модели: " + llm.provider());
        };
    }
}
```

`application.yml` — add under `autolease:`:

```yaml
  agent:
    max-steps: 5
    question-max-length: 1000
    prompt-version: v1
    llm:
      provider: ${AGENT_LLM_PROVIDER:ollama}
      model: "${AGENT_LLM_MODEL:qwen3:8b}"
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      num-ctx: 8192
      temperature: 0.0
      timeout-seconds: 120
```

- [ ] **Step 6: Run tests**

Run: `./gradlew test`
Expected: all PASS (the test profile uses `provider: mock`).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "<KEY> Language model clients: Ollama and deterministic mock"
```

---

### Task 13: Trace storage and trace API

**Files:**
- Create: `src/main/resources/db/migration/V6_0__Agent_traces.sql`, `agent/trace/TraceStatus.java`, `StepType.java`, `TraceRecorder.java`, `TraceQueryService.java`, `TraceController.java`, `agent/trace/dto/TraceView.java`, `TraceStepView.java`, `TraceSummary.java`
- Modify: `src/test/resources/sql/cleanup.sql`
- Test: `src/test/java/ru/bell/autoleasing/agent/trace/TraceApiTest.java`

**Interfaces:**
- Consumes: Spring `ObjectMapper`, `JdbcTemplate`, `NotFoundException`, `ValidationException`.
- Produces:
  - tables `autoleasing_agent.traces`, `autoleasing_agent.trace_steps`
  - `enum TraceStatus { IN_PROGRESS, OK, INVALID_OUTPUT, MAX_STEPS, LLM_ERROR }`, `enum StepType { ROUTING, LLM_CALL, TOOL_CALL }`
  - `TraceRecorder`: `start(UUID traceId, long clientId, String username, String question, String provider, String model, String promptVersion, String requestId, String appEnv)`, `step(UUID traceId, int stepNo, StepType type, String toolName, String argumentsJson, String resultJson, String error, long latencyMs, int promptTokens, int completionTokens)`, `finish(UUID traceId, TraceStatus status, String routingRule, String answerJson, int promptTokens, int completionTokens, long latencyMs, String error)` — each call commits on its own, so a failed run keeps its partial trace
  - `GET /api/agent/traces/{id}` → `TraceView` (role MANAGER; non-UUID → 400; unknown → 404); `GET /api/agent/traces?limit=50` → `List<TraceSummary>` newest first (limit 1–200)
  - `record TraceView(UUID id, long clientId, String username, String question, String routingRule, String status, JsonNode answer, String error, String llmProvider, String llmModel, String promptVersion, int promptTokens, int completionTokens, Long latencyMs, String requestId, String appEnv, OffsetDateTime createdAt, OffsetDateTime finishedAt, List<TraceStepView> steps)`
  - `record TraceStepView(int stepNo, String stepType, String toolName, String argumentsJson, String resultJson, String error, long latencyMs, int promptTokens, int completionTokens)`
  - `record TraceSummary(UUID id, String username, String question, String status, Long latencyMs, int totalTokens, OffsetDateTime createdAt)`

- [ ] **Step 1: Migration** `V6_0__Agent_traces.sql`

```sql
CREATE SCHEMA IF NOT EXISTS autoleasing_agent;

CREATE TABLE autoleasing_agent.traces (
    id                UUID PRIMARY KEY,
    client_id         BIGINT       NOT NULL,
    username          VARCHAR(50)  NOT NULL,
    question          TEXT         NOT NULL,
    routing_rule      VARCHAR(100),
    status            VARCHAR(20)  NOT NULL,
    answer_json       TEXT,
    error             TEXT,
    llm_provider      VARCHAR(20)  NOT NULL,
    llm_model         VARCHAR(100) NOT NULL,
    prompt_version    VARCHAR(20)  NOT NULL,
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    latency_ms        BIGINT,
    request_id        VARCHAR(64),
    app_env           VARCHAR(30)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ
);
CREATE INDEX idx_traces_created_at ON autoleasing_agent.traces (created_at DESC);
CREATE INDEX idx_traces_client_id ON autoleasing_agent.traces (client_id);

CREATE TABLE autoleasing_agent.trace_steps (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    trace_id          UUID        NOT NULL REFERENCES autoleasing_agent.traces (id),
    step_no           INT         NOT NULL,
    step_type         VARCHAR(20) NOT NULL,
    tool_name         VARCHAR(100),
    arguments_json    TEXT,
    result_json       TEXT,
    error             TEXT,
    latency_ms        BIGINT      NOT NULL DEFAULT 0,
    prompt_tokens     INT         NOT NULL DEFAULT 0,
    completion_tokens INT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (trace_id, step_no)
);
```

Append to `src/test/resources/sql/cleanup.sql`:

```sql
TRUNCATE autoleasing_agent.trace_steps, autoleasing_agent.traces;
```

- [ ] **Step 2: Write the failing test**

```java
package ru.bell.autoleasing.agent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TraceApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired TraceRecorder recorder;

    private UUID recordTrace(String question) {
        UUID id = UUID.randomUUID();
        recorder.start(id, 1001, "client1001", question, "mock", "mock-1", "v1", "req-1", "test");
        recorder.step(id, 1, StepType.ROUTING, null, "[\"schedule\"]", "SCHEDULE", null, 0, 0, 0);
        recorder.step(id, 2, StepType.TOOL_CALL, "schedule", "{}", "{\"nextPayment\":null}", null, 12, 0, 0);
        recorder.finish(id, TraceStatus.OK, "SCHEDULE", "{\"answer\":\"Платежей нет\"}", 100, 20, 345, null);
        return id;
    }

    @Test
    void managerReadsTraceWithSteps() throws Exception {
        UUID id = recordTrace("Когда платить?");
        mockMvc.perform(get("/api/agent/traces/" + id).with(user(TestPrincipals.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.answer.answer").value("Платежей нет"))
                .andExpect(jsonPath("$.routingRule").value("SCHEDULE"))
                .andExpect(jsonPath("$.promptTokens").value(100))
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[1].toolName").value("schedule"));
    }

    @Test
    void listShowsRecentTraces() throws Exception {
        recordTrace("Первый вопрос");
        recordTrace("Второй вопрос");
        mockMvc.perform(get("/api/agent/traces").param("limit", "10").with(user(TestPrincipals.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].totalTokens").value(120));
    }

    @Test
    void clientCannotReadTraces() throws Exception {
        UUID id = recordTrace("Когда платить?");
        mockMvc.perform(get("/api/agent/traces/" + id).with(user(TestPrincipals.client(1001))))
                .andExpect(status().isForbidden());
    }

    @Test
    void badRequestsAreRejected() throws Exception {
        var manager = user(TestPrincipals.manager());
        mockMvc.perform(get("/api/agent/traces/not-a-uuid").with(manager)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/agent/traces/" + UUID.randomUUID()).with(manager)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agent/traces").param("limit", "0").with(manager)).andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests "*TraceApiTest"`
Expected: FAIL (compilation).

- [ ] **Step 4: Implement**

Enums and DTO records exactly as in **Interfaces**.

```java
package ru.bell.autoleasing.agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TraceRecorder {
    private final JdbcTemplate jdbc;

    public void start(UUID traceId, long clientId, String username, String question, String provider, String model,
                      String promptVersion, String requestId, String appEnv) {
        jdbc.update("INSERT INTO autoleasing_agent.traces (id, client_id, username, question, status, llm_provider, "
                        + "llm_model, prompt_version, request_id, app_env) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                traceId, clientId, username, question, TraceStatus.IN_PROGRESS.name(), provider, model, promptVersion,
                requestId, appEnv);
    }

    public void step(UUID traceId, int stepNo, StepType type, String toolName, String argumentsJson, String resultJson,
                     String error, long latencyMs, int promptTokens, int completionTokens) {
        jdbc.update("INSERT INTO autoleasing_agent.trace_steps (trace_id, step_no, step_type, tool_name, arguments_json, "
                        + "result_json, error, latency_ms, prompt_tokens, completion_tokens) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                traceId, stepNo, type.name(), toolName, argumentsJson, resultJson, error, latencyMs, promptTokens,
                completionTokens);
    }

    public void finish(UUID traceId, TraceStatus status, String routingRule, String answerJson, int promptTokens,
                       int completionTokens, long latencyMs, String error) {
        jdbc.update("UPDATE autoleasing_agent.traces SET status = ?, routing_rule = ?, answer_json = ?, prompt_tokens = ?, "
                        + "completion_tokens = ?, latency_ms = ?, error = ?, finished_at = now() WHERE id = ?",
                status.name(), routingRule, answerJson, promptTokens, completionTokens, latencyMs, error, traceId);
    }
}
```

```java
package ru.bell.autoleasing.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.agent.trace.dto.TraceStepView;
import ru.bell.autoleasing.agent.trace.dto.TraceSummary;
import ru.bell.autoleasing.agent.trace.dto.TraceView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TraceQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Optional<TraceView> find(UUID id) {
        List<TraceStepView> steps = jdbc.query("SELECT * FROM autoleasing_agent.trace_steps WHERE trace_id = ? ORDER BY step_no",
                (rs, i) -> new TraceStepView(rs.getInt("step_no"), rs.getString("step_type"), rs.getString("tool_name"),
                        rs.getString("arguments_json"), rs.getString("result_json"), rs.getString("error"),
                        rs.getLong("latency_ms"), rs.getInt("prompt_tokens"), rs.getInt("completion_tokens")), id);
        return jdbc.query("SELECT * FROM autoleasing_agent.traces WHERE id = ?",
                (rs, i) -> new TraceView(rs.getObject("id", UUID.class), rs.getLong("client_id"), rs.getString("username"),
                        rs.getString("question"), rs.getString("routing_rule"), rs.getString("status"),
                        parse(rs.getString("answer_json")), rs.getString("error"), rs.getString("llm_provider"),
                        rs.getString("llm_model"), rs.getString("prompt_version"), rs.getInt("prompt_tokens"),
                        rs.getInt("completion_tokens"), rs.getObject("latency_ms", Long.class), rs.getString("request_id"),
                        rs.getString("app_env"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class), steps), id).stream().findFirst();
    }

    public List<TraceSummary> recent(int limit) {
        return jdbc.query("SELECT id, username, question, status, latency_ms, prompt_tokens + completion_tokens AS total_tokens, "
                        + "created_at FROM autoleasing_agent.traces ORDER BY created_at DESC LIMIT ?",
                (rs, i) -> new TraceSummary(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("question"),
                        rs.getString("status"), rs.getObject("latency_ms", Long.class), rs.getInt("total_tokens"),
                        rs.getObject("created_at", OffsetDateTime.class)), limit);
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return TextNode.valueOf(json);
        }
    }
}
```

```java
package ru.bell.autoleasing.agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.agent.trace.dto.TraceSummary;
import ru.bell.autoleasing.agent.trace.dto.TraceView;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/traces")
@RequiredArgsConstructor
public class TraceController {
    private final TraceQueryService traces;

    @GetMapping("/{id}")
    public TraceView get(@PathVariable String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Неверный идентификатор трассировки: " + id);
        }
        return traces.find(uuid).orElseThrow(() -> new NotFoundException("Трассировка " + id + " не найдена"));
    }

    @GetMapping
    public List<TraceSummary> recent(@RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 200) {
            throw new ValidationException("limit должен быть от 1 до 200");
        }
        return traces.recent(limit);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "<KEY> Store agent traces with steps, trace API for managers"
```

---

### Task 14: Orchestrator, answer schema and `POST /api/agent/ask`

**Files:**
- Create: `src/main/resources/agent/answer-schema.json`, `src/main/resources/agent/system-prompt-v1.txt`
- Create: `agent/core/SystemPrompt.java`, `AnswerParser.java`, `AgentOrchestrator.java`, `AgentUnavailableException.java`; `agent/api/AgentController.java`, `AskRequest.java`, `AgentAnswer.java`, `Figure.java`, `AgentErrorResponse.java`
- Modify: `exceptions/ControllerAdvice.java`, `build.gradle.kts`
- Test: `src/test/java/ru/bell/autoleasing/agent/core/AnswerParserTest.java`, `AgentOrchestratorTest.java`; `src/test/java/ru/bell/autoleasing/agent/api/AgentControllerTest.java`

**Interfaces:**
- Consumes: `ToolRouter` (Task 11), `ToolRegistry`, `ToolContext`, `ToolArgumentException` (Task 10), `LlmClient` and model types, `AgentProperties` (Task 12), `TraceRecorder`, `TraceStatus`, `StepType` (Task 13), `AppUserPrincipal`, `TestPrincipals` (Task 5), `RequestIdFilter.MDC_KEY` (Task 2), fixture (Task 8).
- Produces:
  - `record AgentAnswer(String answer, List<Figure> figures, @JsonProperty("tools_used") List<String> toolsUsed, @JsonProperty("trace_id") String traceId, List<String> warnings)`; `record Figure(String label, BigDecimal value, String unit, String period)`
  - `POST /api/agent/ask` body `{"question": String}` (role CLIENT) → 200 `AgentAnswer`; blank or longer than `question-max-length` → 400; model unavailable → 503 `{"message", "trace_id"}`
  - `AnswerParser.parse(String modelContent, UUID traceId, List<String> toolsUsed): AnswerParser.Result(AgentAnswer answer, boolean valid, String error)`; constants `INVALID_OUTPUT_WARNING = "INVALID_MODEL_OUTPUT"`, `FALLBACK_TEXT`. The server always sets `trace_id` and `tools_used` (tools that actually ran), then validates the whole object against `answer-schema.json`; any failure → fallback answer with the warning.
  - `AgentOrchestrator.ask(AppUserPrincipal user, String question): AgentAnswer`; `MAX_STEPS_WARNING = "MAX_STEPS_EXCEEDED"`; tool outside the routing decision → not executed, step error `TOOL_NOT_ALLOWED`.
  - Trace of one successful tool question: steps `ROUTING`, `LLM_CALL`, `TOOL_CALL`, `LLM_CALL`; status `OK`.

- [ ] **Step 1: Resources**

Add to `build.gradle.kts`: `implementation("com.networknt:json-schema-validator:1.5.8")`.

`src/main/resources/agent/answer-schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["answer", "figures", "tools_used", "trace_id", "warnings"],
  "additionalProperties": false,
  "properties": {
    "answer": {"type": "string", "minLength": 1},
    "figures": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["label", "value", "unit"],
        "additionalProperties": false,
        "properties": {
          "label": {"type": "string", "minLength": 1},
          "value": {"type": "number"},
          "unit": {"type": "string"},
          "period": {"type": ["string", "null"]}
        }
      }
    },
    "tools_used": {"type": "array", "items": {"type": "string"}},
    "trace_id": {"type": "string"},
    "warnings": {"type": "array", "items": {"type": "string"}}
  }
}
```

`src/main/resources/agent/system-prompt-v1.txt`:

```
Ты — «Лизинговый ассистент» компании AutoLease. Отвечаешь клиенту на русском языке о его договорах лизинга и платежах.
Сегодня: {today}.
Используй инструменты, чтобы получить данные клиента. Числа бери только из результатов инструментов.
Не давай финансовых, налоговых и инвестиционных советов.
Ответ верни строго в формате JSON без пояснений:
{"answer": "текст ответа", "figures": [{"label": "название", "value": 0.00, "unit": "RUB", "period": "период или null"}], "warnings": []}
```

- [ ] **Step 2: Write the failing parser test**

```java
package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerParserTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AnswerParser parser = new AnswerParser(mapper);
    private final UUID traceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID = """
            {"answer":"Оплачено 20 030 руб.","figures":[{"label":"Оплачено","value":20030.00,"unit":"RUB","period":"2026-09"}],"warnings":[]}
            """;

    @Test
    void validAnswerGetsServerTraceIdAndTools() {
        AnswerParser.Result r = parser.parse(VALID, traceId, List.of("payments_summary"));
        assertThat(r.valid()).isTrue();
        assertThat(r.answer().traceId()).isEqualTo(traceId.toString());
        assertThat(r.answer().toolsUsed()).containsExactly("payments_summary");
        assertThat(r.answer().figures().get(0).value()).isEqualByComparingTo("20030.00");
    }

    @Test
    void toolsClaimedByModelAreReplaced() {
        String claimed = "{\"answer\":\"a\",\"figures\":[],\"warnings\":[],\"tools_used\":[\"schedule\"],\"trace_id\":\"x\"}";
        AnswerParser.Result r = parser.parse(claimed, traceId, List.of());
        assertThat(r.valid()).isTrue();
        assertThat(r.answer().toolsUsed()).isEmpty();
        assertThat(r.answer().traceId()).isEqualTo(traceId.toString());
    }

    @Test
    void codeFenceIsAccepted() {
        assertThat(parser.parse("```json\n" + VALID + "```", traceId, List.of()).valid()).isTrue();
    }

    @Test
    void proseGivesFallback() {
        AnswerParser.Result r = parser.parse("Вы заплатили 20 030 рублей.", traceId, List.of("payments_summary"));
        assertThat(r.valid()).isFalse();
        assertThat(r.answer().answer()).isEqualTo(AnswerParser.FALLBACK_TEXT);
        assertThat(r.answer().warnings()).containsExactly(AnswerParser.INVALID_OUTPUT_WARNING);
        assertThat(r.answer().toolsUsed()).containsExactly("payments_summary");
    }

    @Test
    void schemaViolationsGiveFallback() {
        String[] invalid = {
                "{\"answer\":\"a\",\"figures\":[]}",
                "{\"answer\":\"a\",\"figures\":[{\"label\":\"x\",\"value\":\"20030\",\"unit\":\"RUB\"}],\"warnings\":[]}",
                "{\"answer\":\"a\",\"figures\":[],\"warnings\":[],\"advice\":\"купите акции\"}",
                "{\"answer\":\"\",\"figures\":[],\"warnings\":[]}",
                "[]", "", "null"};
        for (String content : invalid) {
            AnswerParser.Result r = parser.parse(content, traceId, List.of());
            assertThat(r.valid()).as(content).isFalse();
            assertThat(r.error()).as(content).isNotBlank();
        }
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests "*AnswerParserTest"`
Expected: FAIL (compilation).

- [ ] **Step 4: Implement the answer types and parser**

```java
package ru.bell.autoleasing.agent.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AgentAnswer(String answer, List<Figure> figures, @JsonProperty("tools_used") List<String> toolsUsed,
                          @JsonProperty("trace_id") String traceId, List<String> warnings) {
}
```

`Figure`: `public record Figure(String label, java.math.BigDecimal value, String unit, String period) {}`
`AskRequest`: `public record AskRequest(String question) {}`
`AgentErrorResponse`: `public record AgentErrorResponse(String message, @JsonProperty("trace_id") String traceId) {}`

```java
package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.agent.api.AgentAnswer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AnswerParser {
    public static final String INVALID_OUTPUT_WARNING = "INVALID_MODEL_OUTPUT";
    public static final String FALLBACK_TEXT = "Не удалось сформировать ответ. Попробуйте переформулировать вопрос.";

    public record Result(AgentAnswer answer, boolean valid, String error) {
    }

    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public AnswerParser(ObjectMapper mapper) {
        this.mapper = mapper;
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(AnswerParser.class.getResourceAsStream("/agent/answer-schema.json"));
    }

    public Result parse(String modelContent, UUID traceId, List<String> toolsUsed) {
        String text = stripFences(modelContent);
        try {
            JsonNode node = mapper.readTree(text);
            if (node == null || !node.isObject()) {
                return invalid(traceId, toolsUsed, "Ответ модели не является JSON-объектом");
            }
            ObjectNode answer = (ObjectNode) node;
            answer.set("tools_used", mapper.valueToTree(toolsUsed));
            answer.put("trace_id", traceId.toString());
            Set<ValidationMessage> errors = schema.validate(answer);
            if (!errors.isEmpty()) {
                return invalid(traceId, toolsUsed, errors.stream().map(ValidationMessage::getMessage).sorted()
                        .collect(Collectors.joining("; ")));
            }
            return new Result(mapper.treeToValue(answer, AgentAnswer.class), true, null);
        } catch (JsonProcessingException e) {
            return invalid(traceId, toolsUsed, "Невалидный JSON: " + e.getOriginalMessage());
        }
    }

    static String stripFences(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return text;
    }

    private Result invalid(UUID traceId, List<String> toolsUsed, String error) {
        return new Result(new AgentAnswer(FALLBACK_TEXT, List.of(), List.copyOf(toolsUsed), traceId.toString(),
                List.of(INVALID_OUTPUT_WARNING)), false, error);
    }
}
```

- [ ] **Step 5: Run the parser test**

Run: `./gradlew test --tests "*AnswerParserTest"`
Expected: PASS.

- [ ] **Step 6: Write the failing orchestrator test**

```java
package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.api.AgentAnswer;
import ru.bell.autoleasing.agent.llm.*;
import ru.bell.autoleasing.agent.router.ToolRouter;
import ru.bell.autoleasing.agent.tool.AgentTool;
import ru.bell.autoleasing.agent.tool.ToolRegistry;
import ru.bell.autoleasing.agent.tool.ToolSpec;
import ru.bell.autoleasing.agent.trace.StepType;
import ru.bell.autoleasing.agent.trace.TraceRecorder;
import ru.bell.autoleasing.agent.trace.TraceStatus;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.support.TestPrincipals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {
    private static final String FINAL = "{\"answer\":\"Следующий платёж 31.10.2026\",\"figures\":[{\"label\":\"Следующий платёж\","
            + "\"value\":10000.00,\"unit\":\"RUB\",\"period\":\"2026-10-31\"}],\"warnings\":[]}";

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final LlmClient llm = mock(LlmClient.class);
    private final TraceRecorder traces = mock(TraceRecorder.class);
    private final AgentTool scheduleTool = mock(AgentTool.class);
    private final AgentTool paymentsTool = mock(AgentTool.class);
    private final AgentProperties props = new AgentProperties(5, 1000, "v1",
            new AgentProperties.Llm("mock", "mock-1", "http://unused", 8192, 0.0, 30));
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(scheduleTool.name()).thenReturn("schedule");
        when(scheduleTool.spec()).thenReturn(ToolSpec.of("schedule", "d", List.of()));
        when(paymentsTool.name()).thenReturn("payments_summary");
        when(paymentsTool.spec()).thenReturn(ToolSpec.of("payments_summary", "d", List.of()));
        when(llm.provider()).thenReturn("mock");
        when(llm.model()).thenReturn("mock-1");
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        orchestrator = new AgentOrchestrator(llm, new ToolRouter(), new ToolRegistry(List.of(scheduleTool, paymentsTool)),
                traces, new AnswerParser(mapper), new SystemPrompt(props), props, mapper, clock, "test");
    }

    private static LlmResponse toolCall(String tool, Map<String, Object> args) {
        return new LlmResponse("", List.of(new LlmToolCall(tool, args)), 50, 5, "mock-1");
    }

    private static LlmResponse text(String content) {
        return new LlmResponse(content, List.of(), 80, 30, "mock-1");
    }

    @Test
    void runsToolThenReturnsValidatedAnswer() {
        when(llm.chat(any())).thenReturn(toolCall("schedule", Map.of()), text(FINAL));
        when(scheduleTool.execute(any(), any())).thenReturn(Map.of("nextPayment", Map.of("amount", 10000.00)));

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        assertThat(answer.toolsUsed()).containsExactly("schedule");
        assertThat(answer.warnings()).isEmpty();
        assertThat(answer.figures()).hasSize(1);
        verify(traces).finish(any(), eq(TraceStatus.OK), eq("SCHEDULE"), anyString(), eq(130), eq(35), anyLong(), isNull());
        verify(traces).step(any(), eq(3), eq(StepType.TOOL_CALL), eq("schedule"), anyString(), anyString(), isNull(), anyLong(), eq(0), eq(0));
    }

    @Test
    void clientIdComesFromPrincipalNotFromArguments() {
        when(llm.chat(any())).thenReturn(toolCall("schedule", Map.of("client_id", 1002)), text(FINAL));
        when(scheduleTool.execute(any(), any())).thenReturn(Map.of());

        orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        verify(scheduleTool).execute(any(), argThat(ctx -> ctx.clientId() == 1001L));
    }

    @Test
    void toolOutsideRoutingDecisionIsNotExecuted() {
        when(llm.chat(any())).thenReturn(toolCall("payments_summary", Map.of()), text(FINAL));

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        verify(paymentsTool, never()).execute(any(), any());
        verify(traces).step(any(), anyInt(), eq(StepType.TOOL_CALL), eq("payments_summary"), anyString(), anyString(),
                eq("TOOL_NOT_ALLOWED"), anyLong(), anyInt(), anyInt());
        assertThat(answer.toolsUsed()).isEmpty();
    }

    @Test
    void invalidModelOutputGivesFallback() {
        when(llm.chat(any())).thenReturn(text("Ваш следующий платёж 10 000 рублей"));

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        assertThat(answer.warnings()).containsExactly(AnswerParser.INVALID_OUTPUT_WARNING);
        verify(traces).finish(any(), eq(TraceStatus.INVALID_OUTPUT), anyString(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }

    @Test
    void unavailableModelIsReportedWithTraceId() {
        when(llm.chat(any())).thenThrow(new LlmUnavailableException("connection refused", null));

        assertThatThrownBy(() -> orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?"))
                .isInstanceOfSatisfying(AgentUnavailableException.class, e -> assertThat(e.getTraceId()).isNotNull());
        verify(traces).finish(any(), eq(TraceStatus.LLM_ERROR), anyString(), isNull(), anyInt(), anyInt(), anyLong(), contains("connection refused"));
    }

    @Test
    void stopsAfterMaxSteps() {
        when(llm.chat(any())).thenReturn(toolCall("schedule", Map.of()));
        when(scheduleTool.execute(any(), any())).thenReturn(Map.of());

        AgentAnswer answer = orchestrator.ask(TestPrincipals.client(1001), "Когда следующий платёж?");

        verify(llm, times(5)).chat(any());
        assertThat(answer.warnings()).containsExactly(AgentOrchestrator.MAX_STEPS_WARNING);
        verify(traces).finish(any(), eq(TraceStatus.MAX_STEPS), anyString(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }

    @Test
    void rejectsBlankAndTooLongQuestionsWithoutCallingTheModel() {
        assertThatThrownBy(() -> orchestrator.ask(TestPrincipals.client(1001), "   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> orchestrator.ask(TestPrincipals.client(1001), "а".repeat(1001))).isInstanceOf(ValidationException.class);
        verifyNoInteractions(llm, traces);
    }
}
```

- [ ] **Step 7: Run to verify it fails**

Run: `./gradlew test --tests "*AgentOrchestratorTest"`
Expected: FAIL (compilation).

- [ ] **Step 8: Implement prompt, exception and orchestrator**

```java
package ru.bell.autoleasing.agent.core;

import org.springframework.stereotype.Component;
import ru.bell.autoleasing.agent.AgentProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Component
public class SystemPrompt {
    private final String version;
    private final String template;

    public SystemPrompt(AgentProperties properties) {
        this.version = properties.promptVersion();
        String path = "/agent/system-prompt-" + version + ".txt";
        try (InputStream in = SystemPrompt.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Нет файла промпта " + path);
            }
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String version() {
        return version;
    }

    public String render(LocalDate today) {
        return template.replace("{today}", today.toString());
    }
}
```

```java
package ru.bell.autoleasing.agent.core;

import lombok.Getter;

import java.util.UUID;

@Getter
public class AgentUnavailableException extends RuntimeException {
    private final UUID traceId;

    public AgentUnavailableException(UUID traceId, Throwable cause) {
        super("Ассистент временно недоступен, попробуйте позже", cause);
        this.traceId = traceId;
    }
}
```

```java
package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.api.AgentAnswer;
import ru.bell.autoleasing.agent.llm.*;
import ru.bell.autoleasing.agent.router.RoutingDecision;
import ru.bell.autoleasing.agent.router.ToolRouter;
import ru.bell.autoleasing.agent.tool.*;
import ru.bell.autoleasing.agent.trace.StepType;
import ru.bell.autoleasing.agent.trace.TraceRecorder;
import ru.bell.autoleasing.agent.trace.TraceStatus;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.platform.RequestIdFilter;
import ru.bell.autoleasing.user.AppUserPrincipal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class AgentOrchestrator {
    public static final String MAX_STEPS_WARNING = "MAX_STEPS_EXCEEDED";

    private final LlmClient llm;
    private final ToolRouter router;
    private final ToolRegistry tools;
    private final TraceRecorder traces;
    private final AnswerParser parser;
    private final SystemPrompt systemPrompt;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String appEnv;

    public AgentOrchestrator(LlmClient llm, ToolRouter router, ToolRegistry tools, TraceRecorder traces, AnswerParser parser,
                             SystemPrompt systemPrompt, AgentProperties properties, ObjectMapper mapper, Clock clock,
                             @Value("${autolease.app-env}") String appEnv) {
        this.llm = llm;
        this.router = router;
        this.tools = tools;
        this.traces = traces;
        this.parser = parser;
        this.systemPrompt = systemPrompt;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.appEnv = appEnv;
    }

    public AgentAnswer ask(AppUserPrincipal user, String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new ValidationException("Вопрос не должен быть пустым");
        }
        if (q.length() > properties.questionMaxLength()) {
            throw new ValidationException("Вопрос длиннее " + properties.questionMaxLength() + " символов");
        }
        UUID traceId = UUID.randomUUID();
        long started = System.nanoTime();
        LocalDate today = LocalDate.now(clock);
        ToolContext context = new ToolContext(user.clientId(), today);
        traces.start(traceId, user.clientId(), user.username(), q, llm.provider(), llm.model(), systemPrompt.version(),
                MDC.get(RequestIdFilter.MDC_KEY), appEnv);

        RoutingDecision decision = router.route(q);
        int stepNo = 0;
        traces.step(traceId, ++stepNo, StepType.ROUTING, null, json(decision.allowedTools()), decision.rule(), null, 0, 0, 0);

        List<LlmMessage> messages = new ArrayList<>(List.of(LlmMessage.system(systemPrompt.render(today)), LlmMessage.user(q)));
        List<ToolSpec> specs = tools.specs(decision.allowedTools());
        Set<String> toolsUsed = new LinkedHashSet<>();
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            for (int i = 0; i < properties.maxSteps(); i++) {
                long callStarted = System.nanoTime();
                LlmResponse response = llm.chat(new LlmRequest(List.copyOf(messages), specs));
                promptTokens += response.promptTokens();
                completionTokens += response.completionTokens();
                traces.step(traceId, ++stepNo, StepType.LLM_CALL, null, null,
                        response.hasToolCalls() ? json(response.toolCalls()) : response.content(), null,
                        millisSince(callStarted), response.promptTokens(), response.completionTokens());

                if (!response.hasToolCalls()) {
                    AnswerParser.Result result = parser.parse(response.content(), traceId, List.copyOf(toolsUsed));
                    traces.finish(traceId, result.valid() ? TraceStatus.OK : TraceStatus.INVALID_OUTPUT, decision.rule(),
                            json(result.answer()), promptTokens, completionTokens, millisSince(started), result.error());
                    return result.answer();
                }

                messages.add(LlmMessage.assistant(response.content(), response.toolCalls()));
                for (LlmToolCall call : response.toolCalls()) {
                    long toolStarted = System.nanoTime();
                    String resultJson;
                    String error = null;
                    Optional<AgentTool> tool = tools.find(call.name());
                    if (!decision.allowedTools().contains(call.name()) || tool.isEmpty()) {
                        error = "TOOL_NOT_ALLOWED";
                        resultJson = json(Map.of("error", "Инструмент " + call.name() + " недоступен для этого вопроса"));
                    } else {
                        try {
                            resultJson = json(tool.get().execute(call.arguments(), context));
                            toolsUsed.add(call.name());
                        } catch (ToolArgumentException | NotFoundException | ValidationException e) {
                            error = e.getMessage();
                            resultJson = json(Map.of("error", e.getMessage()));
                        }
                    }
                    traces.step(traceId, ++stepNo, StepType.TOOL_CALL, call.name(), json(call.arguments()), resultJson,
                            error, millisSince(toolStarted), 0, 0);
                    messages.add(LlmMessage.tool(call.name(), resultJson));
                }
            }
            AgentAnswer fallback = new AgentAnswer(AnswerParser.FALLBACK_TEXT, List.of(), List.copyOf(toolsUsed),
                    traceId.toString(), List.of(MAX_STEPS_WARNING));
            traces.finish(traceId, TraceStatus.MAX_STEPS, decision.rule(), json(fallback), promptTokens, completionTokens,
                    millisSince(started), "Превышено число шагов: " + properties.maxSteps());
            return fallback;
        } catch (LlmUnavailableException e) {
            log.warn("Языковая модель недоступна, трассировка {}: {}", traceId, e.getMessage());
            traces.finish(traceId, TraceStatus.LLM_ERROR, decision.rule(), null, promptTokens, completionTokens,
                    millisSince(started), e.getMessage());
            throw new AgentUnavailableException(traceId, e);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
```

- [ ] **Step 9: Run the orchestrator test**

Run: `./gradlew test --tests "*AgentOrchestratorTest"`
Expected: PASS.

- [ ] **Step 10: Write the failing endpoint test**

```java
package ru.bell.autoleasing.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String ask(String question) throws Exception {
        return mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AskRequest(question))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void answersPreviousMonthPaymentsWithMockModel() throws Exception {
        JsonNode answer = mapper.readTree(ask("Сколько я заплатил в прошлом месяце?"));

        assertThat(answer.path("tools_used").get(0).asText()).isEqualTo("payments_summary");
        assertThat(answer.path("figures").get(0).path("value").decimalValue()).isEqualByComparingTo("20030.00");
        assertThat(answer.path("warnings").size()).isZero();
        UUID traceId = UUID.fromString(answer.path("trace_id").asText());
        assertThat(jdbc.queryForObject("SELECT status FROM autoleasing_agent.traces WHERE id = ?", String.class, traceId)).isEqualTo("OK");
        assertThat(jdbc.queryForList("SELECT step_type FROM autoleasing_agent.trace_steps WHERE trace_id = ? ORDER BY step_no",
                String.class, traceId)).containsExactly("ROUTING", "LLM_CALL", "TOOL_CALL", "LLM_CALL");
    }

    @Test
    void managerSeesTheTraceOfAnAnswer() throws Exception {
        String traceId = mapper.readTree(ask("Когда следующий платёж?")).path("trace_id").asText();
        mockMvc.perform(get("/api/agent/traces/" + traceId).with(user(TestPrincipals.manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.username").value("client1001"))
                .andExpect(jsonPath("$.steps.length()").value(4));
    }

    @Test
    void blankQuestionIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerCannotAsk() throws Exception {
        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.manager()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Когда платить?\"}"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 11: Implement the controller and the 503 mapping**

```java
package ru.bell.autoleasing.agent.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.agent.core.AgentOrchestrator;
import ru.bell.autoleasing.user.AppUserPrincipal;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentOrchestrator orchestrator;

    @PostMapping("/ask")
    public AgentAnswer ask(@AuthenticationPrincipal AppUserPrincipal user, @RequestBody AskRequest request) {
        return orchestrator.ask(user, request == null ? null : request.question());
    }
}
```

`ControllerAdvice` — add:

```java
    @ExceptionHandler(AgentUnavailableException.class)
    public ResponseEntity<AgentErrorResponse> handleAgentUnavailable(AgentUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AgentErrorResponse(e.getMessage(), e.getTraceId().toString()));
    }
```

- [ ] **Step 12: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 13: Commit and merge story D**

```bash
git add -A && git commit -m "<KEY> Leasing assistant: orchestrator, strict JSON answer, ask endpoint"
./gradlew test && git checkout develop && git merge --no-ff feature/<KEY>-leasing-assistant -m "<KEY> Merge leasing assistant" && ./gradlew test
```

---

## Story E — Assistant UI

### Task 15: Chat with answer cards and the manager trace view

**Files:**
- Create: `src/main/resources/static/js/chat.js`, `traces.html`, `js/traces.js`
- Modify: `static/cabinet.html`, `static/js/cabinet.js`, `static/js/api.js` (`homeFor`), `static/sw.js`, `static/css/app.css`
- Test: `src/test/java/ru/bell/autoleasing/ui/StaticUiTest.java`

**Interfaces:**
- Consumes: `POST /api/agent/ask` → `AgentAnswer` or 503 `{message, trace_id}` (Task 14); `GET /api/agent/traces`, `GET /api/agent/traces/{id}` (Task 13); `api.js` helpers and the `data-tab` / `data-panel` convention (Task 9).
- Produces: assistant tab in the cabinet; manager home `/traces.html` (`homeFor('MANAGER')`); `traces.html?id=<uuid>` opens a trace directly. Test ids: `tab-assistant`, `chat-suggestion`, `chat-messages`, `chat-form`, `chat-input`, `chat-send`, `chat-question`, `chat-pending`, `chat-answer-card`, `chat-answer-text`, `chat-figure`, `chat-warning`, `chat-trace-id`, `chat-copy-trace`, `chat-toggle-json`, `chat-raw-json`, `chat-error`; `traces-table`, `trace-row`, `traces-refresh`, `trace-id-input`, `trace-open`, `trace-detail`, `trace-summary`, `trace-steps`, `trace-answer`, `traces-error`.

- [ ] **Step 1: Create the branch**

```bash
git checkout develop && git checkout -b feature/<KEY>-assistant-ui
```

- [ ] **Step 2: Extend the failing test** — in `StaticUiTest` add `"/traces.html", "/js/chat.js", "/js/traces.js"` to `staticFilesArePublic`, and:

```java
    @Test
    void chatAndTracePagesHaveTestIds() throws Exception {
        String cabinet = mockMvc.perform(get("/cabinet.html")).andReturn().getResponse().getContentAsString();
        for (String id : new String[]{"tab-assistant", "chat-suggestion", "chat-messages", "chat-form", "chat-input", "chat-send"}) {
            org.assertj.core.api.Assertions.assertThat(cabinet).contains("data-testid=\"" + id + "\"");
        }
        String traces = mockMvc.perform(get("/traces.html")).andReturn().getResponse().getContentAsString();
        for (String id : new String[]{"logout-button", "env-badge", "traces-table", "traces-refresh", "trace-id-input",
                "trace-open", "trace-detail", "trace-summary", "trace-steps", "trace-answer", "traces-error"}) {
            org.assertj.core.api.Assertions.assertThat(traces).contains("data-testid=\"" + id + "\"");
        }
    }
```

Run: `./gradlew test --tests "*StaticUiTest"`
Expected: FAIL.

- [ ] **Step 3: Assistant tab in `cabinet.html`**

Add to `<nav class="tabs">`: `<button type="button" data-tab="assistant" data-testid="tab-assistant">Ассистент</button>`. Add before `</main>`:

```html
  <section data-panel="assistant" hidden>
    <h2>Лизинговый ассистент</h2>
    <div class="suggestions">
      <button type="button" class="chip" data-testid="chat-suggestion">Сколько я заплатил в прошлом месяце?</button>
      <button type="button" class="chip" data-testid="chat-suggestion">Когда следующий платёж?</button>
      <button type="button" class="chip" data-testid="chat-suggestion">Какой остаток долга по договорам?</button>
    </div>
    <div class="chat" data-testid="chat-messages" aria-live="polite"></div>
    <form class="chat-form" data-testid="chat-form">
      <textarea rows="2" maxlength="1000" placeholder="Задайте вопрос о договорах и платежах" data-testid="chat-input"></textarea>
      <button type="submit" data-testid="chat-send">Отправить</button>
    </form>
  </section>
```

In `js/cabinet.js` add `import './chat.js';` after the `env-badge.js` import.

- [ ] **Step 4: `js/chat.js`**

```js
import { apiJson, formatMoney, el } from './api.js';

const $ = (testId) => document.querySelector(`[data-testid="${testId}"]`);
const messages = $('chat-messages');
const form = $('chat-form');
const input = $('chat-input');
const send = $('chat-send');

function scrollDown() {
  messages.scrollTop = messages.scrollHeight;
}

function figureText(figure) {
  const value = figure.unit === 'RUB' ? formatMoney(figure.value) : `${figure.value} ${figure.unit}`;
  return figure.period ? `${figure.label}: ${value} (${figure.period})` : `${figure.label}: ${value}`;
}

function answerCard(answer) {
  const raw = el('pre', { class: 'raw-json', testid: 'chat-raw-json', hidden: '' }, JSON.stringify(answer, null, 2));
  const toggle = el('button', { type: 'button', class: 'link', testid: 'chat-toggle-json' }, 'Показать JSON');
  toggle.addEventListener('click', () => {
    raw.hidden = !raw.hidden;
    toggle.textContent = raw.hidden ? 'Показать JSON' : 'Скрыть JSON';
  });
  const copy = el('button', { type: 'button', class: 'link', testid: 'chat-copy-trace' }, 'Скопировать');
  copy.addEventListener('click', () => navigator.clipboard && navigator.clipboard.writeText(answer.trace_id));
  return el('div', { class: 'card answer', testid: 'chat-answer-card' },
    el('p', { testid: 'chat-answer-text' }, answer.answer),
    el('ul', { class: 'figures' }, ...(answer.figures || []).map((f) => el('li', { testid: 'chat-figure' }, figureText(f)))),
    el('div', { class: 'warnings' }, ...(answer.warnings || []).map((w) => el('span', { class: 'badge warn', testid: 'chat-warning' }, w))),
    el('div', { class: 'meta' },
      el('span', {}, 'Инструменты: ' + ((answer.tools_used || []).join(', ') || 'нет')),
      el('span', { testid: 'chat-trace-id' }, 'Трассировка: ' + answer.trace_id), copy, toggle),
    raw);
}

function errorCard(error) {
  const traceId = error.body && error.body.trace_id;
  return el('div', { class: 'card error', testid: 'chat-error' }, error.message,
    traceId ? el('div', { class: 'meta' }, 'Трассировка: ' + traceId) : '',
    error.requestId ? el('div', { class: 'meta' }, 'Код запроса: ' + error.requestId) : '');
}

async function ask(question) {
  const text = question.trim();
  if (!text) return;
  messages.append(el('div', { class: 'bubble user', testid: 'chat-question' }, text));
  const pending = el('div', { class: 'bubble pending', testid: 'chat-pending' }, 'Ассистент думает…');
  messages.append(pending);
  scrollDown();
  send.disabled = true;
  try {
    const answer = await apiJson('/api/agent/ask', { method: 'POST', body: JSON.stringify({ question: text }) });
    pending.replaceWith(answerCard(answer));
  } catch (e) {
    pending.replaceWith(errorCard(e));
  } finally {
    send.disabled = false;
    scrollDown();
  }
}

form.addEventListener('submit', (event) => {
  event.preventDefault();
  const question = input.value;
  input.value = '';
  ask(question);
});
input.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});
document.querySelectorAll('[data-testid="chat-suggestion"]').forEach((button) =>
  button.addEventListener('click', () => ask(button.textContent)));
```

- [ ] **Step 5: `traces.html` and `js/traces.js`**

```html
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#1f4e79">
  <title>AutoLease — трассировки ассистента</title>
  <link rel="manifest" href="/manifest.json">
  <link rel="icon" href="/icons/icon-192.png">
  <link rel="stylesheet" href="/css/app.css">
  <script type="module" src="/js/traces.js"></script>
</head>
<body>
<header class="topbar">
  <span class="brand">AutoLease · менеджер</span>
  <span class="client-name"></span>
  <span class="env-badge" data-testid="env-badge"></span>
  <button type="button" class="link" data-testid="logout-button">Выйти</button>
</header>
<main>
  <p class="error" data-testid="traces-error" hidden></p>
  <div class="filters">
    <label>Идентификатор трассировки <input data-testid="trace-id-input" size="38"></label>
    <button type="button" data-testid="trace-open">Открыть</button>
    <button type="button" data-testid="traces-refresh">Обновить список</button>
  </div>
  <section data-testid="trace-detail" hidden>
    <h2>Трассировка</h2>
    <dl class="summary-grid" data-testid="trace-summary"></dl>
    <div class="table-wrap">
      <table data-testid="trace-steps">
        <thead><tr><th>№</th><th>Тип</th><th>Инструмент</th><th>Аргументы</th><th>Результат</th><th>Ошибка</th>
          <th>мс</th><th>Токены</th></tr></thead>
        <tbody></tbody>
      </table>
    </div>
    <h3>Ответ</h3>
    <pre class="raw-json" data-testid="trace-answer"></pre>
  </section>
  <h2>Последние вопросы</h2>
  <div class="table-wrap">
    <table data-testid="traces-table">
      <thead><tr><th>Время</th><th>Пользователь</th><th>Вопрос</th><th>Статус</th><th>Время ответа</th><th>Токены</th></tr></thead>
      <tbody></tbody>
    </table>
  </div>
</main>
</body>
</html>
```

```js
import { apiJson, requireRole, logout, el, registerServiceWorker } from './api.js';
import './env-badge.js';

requireRole('MANAGER');
registerServiceWorker();

const $ = (testId) => document.querySelector(`[data-testid="${testId}"]`);
$('logout-button').addEventListener('click', logout);

const time = (iso) => (iso ? new Date(iso).toLocaleString('ru-RU') : '—');
const cell = (text) => el('td', { class: 'wrap mono' }, text ?? '—');

function showError(error) {
  $('traces-error').textContent = error.message;
  $('traces-error').hidden = false;
}

async function loadList() {
  const list = await apiJson('/api/agent/traces?limit=50');
  $('traces-table').querySelector('tbody').replaceChildren(...list.map((t) => {
    const row = el('tr', { class: 'clickable', testid: 'trace-row' },
      el('td', {}, time(t.createdAt)), el('td', {}, t.username), el('td', { class: 'wrap' }, t.question),
      el('td', { class: `status-${t.status.toLowerCase()}` }, t.status),
      el('td', { class: 'num' }, t.latencyMs == null ? '—' : `${t.latencyMs} мс`),
      el('td', { class: 'num' }, String(t.totalTokens)));
    row.addEventListener('click', () => openTrace(t.id));
    return row;
  }));
}

async function openTrace(id) {
  $('traces-error').hidden = true;
  try {
    const t = await apiJson('/api/agent/traces/' + encodeURIComponent(String(id).trim()));
    const summary = [
      ['Идентификатор', t.id], ['Вопрос', t.question], ['Пользователь', `${t.username} (клиент ${t.clientId})`],
      ['Статус', t.status], ['Правило маршрутизации', t.routingRule], ['Модель', `${t.llmProvider} / ${t.llmModel}`],
      ['Версия промпта', t.promptVersion], ['Токены', `${t.promptTokens} + ${t.completionTokens}`],
      ['Время ответа', t.latencyMs == null ? '—' : `${t.latencyMs} мс`], ['Окружение', t.appEnv],
      ['Код запроса', t.requestId], ['Создана', time(t.createdAt)], ['Ошибка', t.error]];
    $('trace-summary').replaceChildren(...summary.flatMap(([key, value]) => [el('dt', {}, key), el('dd', {}, value ?? '—')]));
    $('trace-steps').querySelector('tbody').replaceChildren(...t.steps.map((s) => el('tr', { class: s.error ? 'overdue' : '' },
      el('td', {}, String(s.stepNo)), el('td', {}, s.stepType), el('td', {}, s.toolName ?? '—'), cell(s.argumentsJson),
      cell(s.resultJson), cell(s.error), el('td', { class: 'num' }, String(s.latencyMs)),
      el('td', { class: 'num' }, `${s.promptTokens} + ${s.completionTokens}`))));
    $('trace-answer').textContent = JSON.stringify(t.answer, null, 2);
    $('trace-detail').hidden = false;
    $('trace-id-input').value = t.id;
  } catch (e) {
    showError(e);
  }
}

$('trace-open').addEventListener('click', () => openTrace($('trace-id-input').value));
$('traces-refresh').addEventListener('click', () => loadList().catch(showError));
loadList().catch(showError);
const idFromUrl = new URLSearchParams(location.search).get('id');
if (idFromUrl) openTrace(idFromUrl);
```

- [ ] **Step 6: Wiring and styles**

`js/api.js`: `homeFor(role)` returns `'/traces.html'` for `MANAGER`.
`sw.js`: `CACHE = 'autolease-shell-v2'`; add `'/traces.html', '/js/chat.js', '/js/traces.js'` to `SHELL`.
Append to `css/app.css`:

```css
.suggestions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.chip { background: #e8eef5; color: var(--brand); border-radius: 16px; padding: 6px 12px; }
.chat { display: flex; flex-direction: column; gap: 10px; max-height: 60vh; overflow-y: auto; padding: 8px 0; }
.bubble { max-width: 85%; padding: 8px 12px; border-radius: 12px; background: #fff; }
.bubble.user { align-self: flex-end; background: var(--brand); color: #fff; }
.bubble.pending { color: var(--muted); font-style: italic; }
.card { background: #fff; border-radius: 10px; padding: 12px; box-shadow: 0 1px 2px rgba(0, 0, 0, .08); max-width: 100%; }
.card.error { border-left: 4px solid var(--danger); }
.figures { margin: 8px 0; padding-left: 18px; }
.badge { display: inline-block; font-size: 12px; padding: 2px 8px; border-radius: 10px; margin-right: 6px; }
.badge.warn { background: #fef0c7; color: var(--warn); }
.meta { display: flex; flex-wrap: wrap; gap: 8px 16px; font-size: 12px; color: var(--muted); margin-top: 6px; word-break: break-all; }
.raw-json { background: #0f172a; color: #e2e8f0; padding: 10px; border-radius: 8px; overflow-x: auto; font-size: 12px; white-space: pre-wrap; word-break: break-word; }
.chat-form { display: flex; gap: 8px; margin-top: 12px; }
.chat-form textarea { flex: 1; min-width: 0; resize: vertical; }
.summary-grid { display: grid; grid-template-columns: minmax(120px, max-content) 1fr; gap: 4px 12px; }
.summary-grid dt { color: var(--muted); }
.summary-grid dd { margin: 0; word-break: break-word; }
td.wrap { white-space: pre-wrap; word-break: break-word; max-width: 360px; }
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
tr.clickable { cursor: pointer; }
tr.clickable:hover { background: #f0f4f8; }
.status-ok { color: var(--ok); }
.status-invalid_output, .status-max_steps, .status-llm_error { color: var(--danger); }
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 8: Commit, stand check (orchestrator), merge story E**

```bash
git add -A && git commit -m "<KEY> Assistant chat with answer cards and manager trace view"
```

Rebuild `app-it`; in the browser pane as `client01` ask the three suggested questions (Ollama running), then log in as `manager` and open one trace from the list. Then:

```bash
./gradlew test && git checkout develop && git merge --no-ff feature/<KEY>-assistant-ui -m "<KEY> Merge assistant UI" && ./gradlew test
```

---

## Task 16: Release check on the stand with the real model (orchestrator, no new code)

**Files:** none (results go to `docs/PROGRESS.md` via `/checkpoint`).

- [ ] **Step 1: Fresh stand from `develop`**

```bash
git checkout develop
docker compose --profile core down
docker volume rm autolease_pgdata
docker compose --profile core up -d --build
sleep 60
curl -s localhost:8081/api/public/info
```

Expected: `{"appEnv":"integration-testing","version":"1.0.0"}`; log «Сгенерированы данные…». (The volume removal wipes only the stand database — confirm with the user before running it if anything else lives there.)

- [ ] **Step 2: Model reachable**

```bash
ollama list | grep qwen3:8b
curl -s localhost:11434/api/chat -d '{"model":"qwen3:8b","messages":[{"role":"user","content":"Привет"}],"stream":false,"think":false}' | head -c 200
```

- [ ] **Step 3: Three questions through the API** (password from `application.yml`)

```bash
TOKEN=$(curl -s localhost:8081/api/auth/login -H 'Content-Type: application/json' -d '{"username":"client01","password":"<client-password>"}' | python -c "import sys, json; print(json.load(sys.stdin)['token'])")
for q in "Сколько я заплатил в прошлом месяце?" "Когда следующий платёж?" "Какой остаток долга по договорам?"; do
  curl -s -w '\n%{http_code} %{time_total}s\n' localhost:8081/api/agent/ask -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"question\":\"$q\"}"
done
```

Acceptance: every call returns 200 (or 503 only if Ollama is down); no 500; each answer has a `trace_id`; at least two of the three traces contain a `TOOL_CALL` step. Answers that fall back with `INVALID_MODEL_OUTPUT` are acceptable for the baseline — record how many.

- [ ] **Step 4: Browser check** — desktop and 375 px viewport: login, three cabinet tabs, assistant tab, manager trace view; no horizontal page scroll; env badge shows `integration-testing · v1.0.0`.

- [ ] **Step 5: Hand over** — run `/release v1.0` from the build step (release candidate `release/1.0.0`, tag, notes, announce to the user), then `/checkpoint`.

---

## Execution notes (orchestrator)

- Dispatch each task to an implementer subagent with `model: "sonnet"`, giving it the full task text plus **Global Constraints**; review each task with a reviewer subagent on Sonnet before starting the next one.
- Stories are sequential (each depends on the previous). Inside a story, tasks are sequential.
- Steps marked «orchestrator» (stand and browser checks, merges after review) are done in the main session.
- After every story merge: `/checkpoint` (updates `docs/PROGRESS.md`).

