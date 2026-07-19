# Blitz Repository Improvement Audit

## 1. Executive summary

The repository is a small Spring MVC/JPA/OAuth application whose source code was largely copied from an older Spring Boot project and only partially renamed or upgraded. The declared platform is Java 25, Spring Boot 4.1.0, Spring Security 7.1.0, Hibernate 7.4.1, and Gradle 9.6.1, but much of the application still targets pre-Jakarta Spring Boot conventions.

The project does not currently compile. Running `./gradlew test` on 2026-07-19 failed in `compileJava` with 100 reported compiler errors, so no tests executed. The first repair should be a coherent platform migration, not isolated cleanup.

The most important findings are:

| Priority | Finding | Consequence |
| --- | --- | --- |
| Critical | Most application imports still use `com.jojoldu.book.springboot` although all classes are declared under `com.blitz` | Application compilation fails across controllers, services, DTOs, entities, and authentication code |
| Critical | Persistence and servlet imports use `javax.*` instead of Jakarta APIs | Spring Boot 4/Hibernate 7 compilation fails |
| Critical | Security configuration uses removed APIs (`WebSecurityConfigurerAdapter`, `antMatchers`) | Security configuration cannot compile on Spring Security 7 |
| Critical | Mustache views are present, but only Thymeleaf is configured | MVC view rendering will fail after compilation is repaired |
| Critical | Files named `application.yml` contain Java-properties syntax rather than YAML mappings | Configuration loading is likely to fail before application startup |
| High | New OAuth users receive `GUEST`, while all post APIs require `USER`, and no promotion path exists | A normal newly registered user cannot create, update, or delete posts |
| High | Post author is supplied by the browser and update/delete have no ownership checks | Any authorized `USER` can impersonate authors and alter any post |
| High | CSRF and frame protection are disabled globally | Session-authenticated state-changing endpoints are exposed to avoidable attacks |
| High | There is only one integration test, and it asserts a template engine that does not match the checked-in views | Regressions in the main application flows are undetected |
| Medium | Post listing is unbounded and loads all entities before mapping them | Memory, query time, and page rendering degrade as data grows |

## 2. Verified baseline and constraints

### Build evidence

Command executed:

```powershell
.\gradlew.bat test --stacktrace
```

Result: `compileJava FAILED`; Gradle stopped after reporting 100 compilation errors. Representative failures include missing `com.jojoldu.book.springboot.*` packages, missing `javax.persistence` and `javax.servlet` types, and the removed `WebSecurityConfigurerAdapter` class. This is a verified blocker. Runtime findings below are static-analysis findings because the application cannot yet start.

The resolved runtime classpath confirms Spring Boot 4.1.0, Spring Security 7.1.0, and Hibernate 7.4.1. It includes Thymeleaf and does not include a Mustache implementation.

### Recommended target

Keep the versions declared in `build.gradle.kts` and migrate the application to them consistently. Downgrading the build to accommodate the legacy source would postpone the Jakarta and security migrations and contradict the README's explicit Java 25/Spring Boot 4.1 baseline.

## 3. Prioritized findings

### Critical: restore a coherent, compilable platform

#### C1. Replace stale project-package imports

Files under `src/main/java/com/blitz` declare `com.blitz...` packages, but 13 source files import the former `com.jojoldu.book.springboot...` namespace. Replace each import with its local `com.blitz` equivalent. This affects authentication, configuration, entities, services, DTOs, and controllers.

Do this as one atomic change and add a build check that rejects the old namespace. Leaving even one stale import can hide subsequent migration errors behind compiler noise.

#### C2. Complete the Jakarta migration

`BaseTimeEntity`, `Posts`, and `User` use `javax.persistence`; `CustomOAuth2UserService` and `LoginUserArgumentResolver` use `javax.servlet.http.HttpSession`. Spring Boot 4 uses Jakarta EE APIs, so these imports must become `jakarta.persistence.*` and `jakarta.servlet.http.HttpSession`.

`javax.sql.DataSource` in the test is correct: JDBC remains in Java SE and should not be changed.

#### C3. Replace the removed Spring Security configuration style

`SecurityConfig` extends `WebSecurityConfigurerAdapter` and uses chained `authorizeRequests()`/`antMatchers()`, APIs removed from current Spring Security. Expose a `SecurityFilterChain` bean and use the current authorization-request DSL with `requestMatchers`.

Do not preserve the current global disabling behavior during the migration:

- Keep CSRF enabled for browser/session flows and make the frontend send the CSRF token on POST, PUT, and DELETE.
- If an embedded H2 console remains, scope its frame and CSRF exceptions only to the development-console request matcher.
- Enable H2 console access only in a development profile, never as a production-wide public route.
- Revisit `/profile`: expose deployment metadata through secured Actuator health/info endpoints or explicitly restrict this diagnostic route.

#### C4. Choose one view engine and align source, dependencies, and tests

All five checked-in views use Mustache syntax and `.mustache` extensions, while the build installs Thymeleaf and its security extras. The README and integration test also claim Thymeleaf.

The lowest-risk repair is to add the Spring Boot Mustache starter and remove the unused Thymeleaf dependencies and Thymeleaf-specific assertion. Retain the existing templates. If Thymeleaf is a product requirement, convert every template and partial instead; do not run two engines without a concrete need.

#### C5. Correct configuration formats and profile files

Both `application.yml` files contain lines such as `spring.application.name=blitz`. That is properties syntax, not a YAML key/value mapping. Either rename them to `.properties` or convert them to real YAML. Renaming to `.properties` is the smaller change.

The README documents a `mariadb` profile and three required environment variables, but no `application-mariadb.yml` or `.properties` exists. Add a profile-specific configuration that maps those variables, sets an appropriate JDBC driver/dialect only if auto-detection is insufficient, and disables automatic destructive schema management.

Use a migration tool such as Flyway for both domain tables and Spring Session tables. Keep H2 initialization isolated to development/test profiles.

### High: secure identities and mutations

#### H1. Derive post authorship from the authenticated principal

`PostsSaveRequestDto.author` accepts an arbitrary browser-supplied value, and the save form exposes an editable author field. Remove author from the public write request and derive it from the authenticated user. Prefer storing a relation or immutable user identifier rather than only a display name/email string.

Update and delete must verify ownership (or an explicit administrator permission). The UI must only show edit/delete controls to users who can perform those actions, but server-side authorization remains authoritative.

#### H2. Make roles usable and intentional

OAuth signup always stores `Role.GUEST`, while `/api/v1/**` requires `Role.USER`. No checked-in administration or promotion mechanism exists, making CRUD inaccessible to newly created accounts.

Choose and document one policy:

- Grant `USER` after successful OAuth registration when all authenticated users may post; or
- Keep `GUEST`, implement an explicit approval workflow, and clearly expose read-only versus approved behavior.

For this simple application, assigning `USER` after successful provider validation is the practical default.

#### H3. Use stable OAuth identities

`CustomOAuth2UserService` finds users only by email. Email may be absent, mutable, or shared across providers; automatically merging provider accounts by email can cause account confusion or takeover risks.

Persist `registrationId` plus the provider's stable subject identifier and enforce a unique database constraint on that pair. Treat email as profile data. Validate all required provider attributes and fail with a controlled authentication error instead of unchecked casts or null persistence errors.

Other authentication cleanup:

- Parameterize `OAuth2UserService<OAuth2UserRequest, OAuth2User>` instead of using a raw local variable.
- Replace the literal session key `"user"` with one shared constant or, preferably, use the authenticated principal directly.
- Consider a small provider-mapper strategy rather than growing the `if ("naver"...)` branch.
- Add `serialVersionUID` to `SessionUser` if JDBC/session serialization remains part of the design.

#### H4. Validate input and return predictable HTTP errors

The write DTOs accept null, empty, and oversized values despite entity constraints. Add Bean Validation annotations and `@Valid` at controller boundaries. Align limits with database columns and set explicit limits for content and author/profile fields.

Replace repeated `IllegalArgumentException` calls with a domain-specific not-found exception. The current Korean message incorrectly says “user not found” when looking up a post. Add centralized exception handling that returns consistent error bodies and appropriate statuses:

- `400` for malformed or invalid requests.
- `401`/`403` for authentication and authorization failures.
- `404` for missing posts.
- `409` for uniqueness or optimistic-lock conflicts.

Use `ResponseEntity` or equivalent response conventions: return `201 Created` and `Location` for creation, `204 No Content` for deletion, and structured response DTOs rather than bare IDs where future evolution is likely.

#### H5. Add database invariants and concurrency controls

Add uniqueness for stable OAuth identity and, if retained, normalized email where the business rule requires it. Mark required author/profile fields with deliberate nullability and length constraints. Add `@Version` to mutable posts to prevent silent lost updates.

`saveOrUpdate` is vulnerable to concurrent first-login races unless backed by a unique constraint and conflict handling. Database constraints must enforce identity invariants even when application checks race.

### Medium: scalability, maintainability, frontend, and operations

#### M1. Paginate post listings

`PostsRepository.findAllDesc()` loads the entire posts table, and `PostsService` maps the full result in memory. Accept a bounded `Pageable`, sort by `id` or modification time descending, and return a `Page`/slice DTO. Set a conservative default page size and maximum.

The custom JPQL query is unnecessary for simple sorting; a Spring Data method, `findAll(Pageable)`, or a projection is easier to maintain. Use a DTO projection if list pages do not need full content.

#### M2. Modernize DTO and service design

Use immutable Java records or final fields for API DTOs where framework support permits. Avoid leaking entities into web DTO constructors across layers if the application grows; centralize mappings or keep them as explicit factory methods.

Refactor repeated post lookup/error creation into one service method. Fix minor naming/formatting issues such as `delete (` and the misleading “user not found” text. Consider constructor invariants on entities instead of allowing invalid state until database flush.

#### M3. Improve HTML, JavaScript, and dependency delivery

The UI loads jQuery 3.3.1 and Bootstrap 4.3.1 from third-party CDNs. Upgrade or remove them; the small script can be implemented with browser `fetch` and native event APIs. If CDN assets remain, pin maintained versions and add Subresource Integrity and `crossorigin` attributes. Add a Content Security Policy compatible with the chosen delivery method.

Frontend improvements:

- Put submit buttons inside semantic forms and handle `submit`, keyboard activation, disabled/loading states, and duplicate submissions.
- Add `lang="ko"`, a responsive viewport meta tag, and accessible success/error messaging instead of blocking `alert()` calls.
- Correct the update page's “post number” label, whose `for="title"` points to the wrong input.
- Validate required fields client-side for feedback while retaining server-side validation.
- Do not display serialized server error objects to users; show a safe message and log a correlation identifier.
- Encode path identifiers and avoid global `var` state/hard-coded behavior where possible.
- Only render the “create post” link when the current role can use it.

#### M4. Separate environment configuration and secrets

Keep OAuth client IDs/secrets and MariaDB credentials in environment variables or a secrets manager. Provide an example configuration with placeholders, not working secrets. Set production session-cookie attributes (`Secure`, `HttpOnly`, suitable `SameSite`) and an explicit session timeout.

Add production-focused settings for schema validation, connection-pool sizing, proxy headers, logging, and error-page detail. Avoid exposing the H2 console and internal profile names outside development.

#### M5. Add schema migration, observability, and CI

Introduce Flyway migrations and stop relying on implicit Hibernate DDL for persistent environments. Add Actuator health/readiness endpoints with restricted exposure and structured application logs.

Add a CI workflow that runs the wrapper with a pinned JDK, verifies the wrapper checksum, compiles, tests, and optionally runs static analysis. Add formatting/linting (for example Spotless and Checkstyle/PMD or Error Prone where compatible), dependency update automation, and vulnerability scanning. Configure Gradle dependency locking or verification for reproducible dependency resolution.

### Low: repository hygiene and clarity

- Expand the README with OAuth provider setup, real profile files, migration commands, endpoint behavior, role policy, and test commands after the code is repaired.
- Remove statements that contradict the repository, especially the claim that no sample views or HTTP endpoints exist.
- Preserve UTF-8 explicitly in editor/build settings so Korean UI and messages display consistently across shells.
- Keep `.idea`, workspace state, datasource history, build output, and local secrets outside version control. The root `.gitignore` already ignores `.idea`; retain that rule.
- Add a license and contribution guidance if the repository will be shared.

## 4. Test strategy and acceptance criteria

### Build and architecture

- `./gradlew clean test` succeeds on the documented JDK.
- A namespace guard finds no `com.jojoldu.book.springboot` imports.
- A migration guard finds no obsolete `javax.persistence` or `javax.servlet` imports.
- Application context starts separately with test and development profiles.
- Exactly the intended template engine resolves all five views and partials.

### Persistence and services

- Repository tests cover descending pagination, empty pages, database constraints, timestamps, and optimistic-lock conflicts.
- Service tests cover create, read, update, delete, missing IDs, ownership denial, and concurrent OAuth signup behavior.
- Migration tests apply the schema to both H2-compatible test storage and MariaDB (for example through Testcontainers).

### Web and security

- MVC/API tests cover validation errors, response statuses/bodies, authentication requirements, `GUEST`/`USER` behavior, owner versus non-owner mutation, CSRF success/failure, and safe error responses.
- OAuth mapping tests cover Google, Naver, missing/malformed attributes, stable provider identity, existing-user update, and provider collision scenarios.
- View tests cover anonymous, guest, user, and owner-specific controls.
- `/h2-console/**` and diagnostic endpoints are unavailable under production profiles.

### End-to-end

- A user can authenticate, create a post, see it in a bounded list, update it, and delete it.
- Anonymous users can only perform intentionally public reads.
- A second user cannot modify the first user's post.
- MariaDB starts from migrations without manual table creation, and JDBC-backed sessions work.

## 5. Sequenced implementation roadmap

1. **Make the build truthful:** replace stale package imports, migrate `javax` to `jakarta`, modernize the security bean, and correct configuration file syntax.
2. **Restore startup and rendering:** select Mustache (recommended), align dependencies/tests/README, add explicit development and MariaDB profiles, and verify context startup.
3. **Restore secure behavior:** establish the role policy, stable OAuth identity, principal-derived authorship, ownership checks, CSRF handling, validation, and consistent API errors.
4. **Make persistence production-safe:** add constraints, optimistic locking, Flyway migrations, pagination, and concurrency handling.
5. **Build a regression net:** add focused unit, repository, MVC/security, OAuth, migration, and end-to-end tests; enforce them in CI.
6. **Modernize delivery:** replace or upgrade legacy frontend dependencies, improve accessibility and error UX, add observability, and complete operational documentation.

## 6. File-by-file assessment

### Build and repository root

| File | Assessment and recommended action |
| --- | --- |
| `.gitattributes` | Correctly pins LF for `gradlew`, CRLF for the batch wrapper, and marks JARs binary. Consider adding `*.java`, `*.kt`, `*.md`, and resource files as UTF-8 text if cross-platform encoding problems recur. |
| `.gitignore` | Covers Gradle, common IDEs, build output, and keeps the wrapper JAR includable. Add secret/config override patterns only if local untracked variants are introduced; never ignore migration files or the wrapper. |
| `build.gradle.kts` | Modern platform versions conflict with legacy source. Template dependencies do not match Mustache files. Add validation, migrations, test containers, quality tooling, dependency verification, and the chosen view engine after compilation is restored. |
| `settings.gradle.kts` | Minimal and sufficient for one module. Add repository-management/plugin-management policy only when centralization or dependency controls justify it. |
| `README.md` | Platform/build commands are clear, but Thymeleaf/no-endpoint claims contradict the source and the documented MariaDB profile is absent. Document OAuth, profiles, migrations, roles, endpoints, and tests. |
| `gradlew` | Standard generated POSIX wrapper script; preserve executable permission and regenerate only through Gradle when upgrading. |
| `gradlew.bat` | Standard generated Windows wrapper script; no application-specific issue. |
| `gradle/wrapper/gradle-wrapper.properties` | Pins Gradle 9.6.1 with a SHA-256 checksum, URL validation, and timeout settings. This is good supply-chain hygiene; consider retries suitable for CI. |
| `gradle/wrapper/gradle-wrapper.jar` | The archive has the expected wrapper bootstrap classes and manifest/license entries. Keep it committed, verify it in CI, and update it only with the wrapper task. |

### Application configuration and entry point

| File | Assessment and recommended action |
| --- | --- |
| `src/main/java/com/blitz/Application.java` | Clean application entry point. Keep infrastructure configuration in dedicated classes and verify component/entity scanning after imports are repaired. |
| `src/main/java/com/blitz/config/JpaConfig.java` | JPA auditing configuration is appropriate. Retain it after the Jakarta migration; move the Korean trailing comment above the annotation for readability if desired. |
| `src/main/java/com/blitz/config/WebConfig.java` | Correct extension point, but imports the old namespace. After repair, consider eliminating the custom session resolver in favor of Spring Security's principal support. |
| `src/main/resources/application.yml` | Contains properties syntax under a YAML extension. Rename/convert it, split development and production settings, and make schema/session initialization explicit by profile. |
| `src/test/resources/application-test.yml` | Repeats the malformed main configuration and is not activated by `ApplicationTests`. Convert/rename it, use `@ActiveProfiles("test")`, and isolate test-only database/session behavior. |

### Authentication and security

| File | Assessment and recommended action |
| --- | --- |
| `config/auth/SecurityConfig.java` | Uses removed Spring Security APIs, disables CSRF/frame protection globally, exposes H2/profile routes, and protects all post reads and writes with one broad role rule. Replace with a `SecurityFilterChain` and explicit, least-privilege matchers. |
| `config/auth/CustomOAuth2UserService.java` | Has stale imports, a raw generic delegate, email-only identity, literal session key, and race-prone save/update. Use stable provider IDs, validated attributes, unique constraints, and typed APIs. |
| `config/auth/LoginUser.java` | Small parameter annotation is valid, but becomes unnecessary if controllers accept the authenticated principal directly. If retained, add `@Documented` and keep its contract tested. |
| `config/auth/LoginUserArgumentResolver.java` | Uses stale/Jakarta-incompatible imports and directly reads a magic session attribute. Prefer Spring Security's principal; otherwise share a constant and declare nullable behavior explicitly. |
| `config/auth/dto/OAuthAttributes.java` | Supports only Google and Naver through unchecked map casts and assumes required fields exist. Add provider-specific validation/mappers and stable subject/provider fields. |
| `config/auth/dto/SessionUser.java` | Correctly avoids serializing the JPA entity, but lacks stable provider/user ID and `serialVersionUID`. Keep only fields needed by the session and avoid email as identity. |

### Domain and persistence

| File | Assessment and recommended action |
| --- | --- |
| `domain/BaseTimeEntity.java` | Auditing structure is sound but uses `javax.persistence`. Consider `Instant` for unambiguous storage, make creation time non-updatable, and define database/time-zone behavior. |
| `domain/posts/Posts.java` | Has stale base-class/Jakarta imports, no author constraint/relation, and no optimistic locking. Enforce invariants, add ownership, and add `@Version`. |
| `domain/posts/PostsRepository.java` | The unbounded custom ordering query is simple but not scalable. Replace with bounded pagination/sorting or a list projection. |
| `domain/user/User.java` | Has stale/Jakarta imports and no uniqueness/length constraints. Store stable OAuth identity, constrain required values, and decide how profile updates affect audit fields. |
| `domain/user/UserRepository.java` | `findByEmail` encodes an unsafe identity policy. Query by provider plus subject and support deliberate email lookup separately if needed. |
| `domain/user/Role.java` | Enum/key mapping is simple and readable. Resolve the `GUEST` signup versus `USER` API contradiction and remove unused display titles if they have no UI role. |

### Service and API DTOs

| File | Assessment and recommended action |
| --- | --- |
| `service/PostsService.java` | Stale imports prevent compilation. Repeated lookup logic, misleading exceptions, unbounded listing, no authorization, and bare-ID returns should be refactored. |
| `web/dto/HelloResponseDto.java` | Works as a demonstration DTO but has no production purpose. Remove the hello feature if it is tutorial residue; otherwise convert to an immutable record and test it only where useful. |
| `web/dto/PostsSaveRequestDto.java` | Stale entity import, mutable Lombok shape, no validation, and client-controlled author. Remove author from input and map the authenticated owner in the service. |
| `web/dto/PostsUpdateRequestDto.java` | No validation or concurrency token. Add title/content constraints and an expected version if optimistic locking is surfaced through the API. |
| `web/dto/PostsResponseDto.java` | Stale entity import and mutable response shape. Consider an immutable record including version, timestamps, and authorization-relevant representation where required. |
| `web/dto/PostsListResponseDto.java` | Stale entity import; list fields are appropriate, but mapping follows an unbounded entity query. Use a paged projection and a stable timestamp format. |

### Controllers

| File | Assessment and recommended action |
| --- | --- |
| `web/HelloController.java` | Stale DTO import prevents compilation. The unauthenticated tutorial endpoints are not included in `permitAll` and have no documented product value; remove or explicitly document/test them. |
| `web/IndexController.java` | Stale imports prevent compilation. It directly lists all posts, relies on the session resolver, and exposes only a user name to views. Add pagination and explicit authorization/view-model data. |
| `web/PostsApiController.java` | Stale imports prevent compilation. Add `@Valid`, resource-oriented statuses, structured errors, pagination, principal-derived authorization, and clearer routes (for example `GET /api/v1/posts`). |
| `web/ProfileController.java` | Profile selection logic is compact but exposes deployment profile names publicly and silently chooses the first active profile as fallback. Prefer secured Actuator metadata or restrict it to an operational context and add tests. |

### Templates and frontend

| File | Assessment and recommended action |
| --- | --- |
| `templates/layout/header.mustache` | Confirms Mustache is the intended engine. Add `lang`, viewport, CSP-compatible asset delivery, maintained CSS, integrity metadata, and common semantic layout. |
| `templates/layout/footer.mustache` | Loads old jQuery/Bootstrap assets without SRI and always loads page-specific CRUD JavaScript. Upgrade/remove dependencies and load scripts only where needed with `defer`. |
| `templates/index.mustache` | Displays an unbounded table and always shows the create link despite role restrictions. Add pagination, empty-state/accessibility markup, and permission-aware controls. |
| `templates/posts-save.mustache` | Author is editable, fields are not required/bounded, and the button sits outside the form. Derive author from identity and use semantic validated submission with CSRF. |
| `templates/posts-update.mustache` | Exposes update/delete without ownership-aware rendering, has a mismatched label target, and lacks validation/version data. Correct accessibility and authorization behavior. |
| `static/js/app/index.js` | jQuery-based global object uses blocking alerts, leaks raw error objects, lacks CSRF and validation/loading handling, and hard-codes routes. Replace with a small modular `fetch` client or modernize carefully. |

### Tests

| File | Assessment and recommended action |
| --- | --- |
| `src/test/java/com/blitz/ApplicationTests.java` | The sole test cannot run because main compilation fails. It expects Thymeleaf despite Mustache views and does not activate the test profile. Retarget it to the selected engine and add focused tests described above. |

### IDE metadata

The root `.gitignore` excludes `.idea`, so these files should remain local. If they were ever committed in a parent repository, remove them from tracking while leaving local copies intact.

| File/group | Assessment and recommended action |
| --- | --- |
| `.idea/.gitignore` | Correctly ignores workspace, shelf, queries, and datasource-local files inside the IDE directory. The root ignore already excludes the whole directory. |
| `.idea/AndroidProjectSystem.xml` | Android project-system metadata is irrelevant to this Spring server project; safe to let IntelliJ regenerate/remove locally. |
| `.idea/compiler.xml` | Matches Java 25 and Lombok but embeds a user-cache-specific Lombok path. Do not share it; Gradle is the source of truth. |
| `.idea/gradle.xml` | Normal local Gradle linkage; keep untracked and ensure IDE uses the wrapper/toolchain. |
| `.idea/misc.xml` | Locally selects JDK 25 consistently with Gradle. Keep untracked. |
| `.idea/modules.xml` and `.idea/modules/blitz.test.iml` | Generated module metadata with a test-resource mapping. Let Gradle import regenerate it rather than maintaining it manually. |
| `.idea/workspace.xml` | Contains machine/user workspace state, plugin settings, timestamps, and run history. It must remain untracked. |
| `.idea/dataSources/data_sources_history.xml` | Contains local MariaDB host/port, username, versions, and datasource metadata. No password is visible, but infrastructure metadata is local and should remain untracked. |

## 7. Definition of done for the improvement program

The modernization is complete when the documented wrapper build succeeds from a clean checkout; all checked-in configuration files parse under their named format; views render with one intentional engine; OAuth users have a stable identity and usable role policy; post mutations enforce authenticated ownership and CSRF; database changes are migration-managed; listing is paginated; production profiles do not expose development consoles or profile data; and CI continuously exercises the security, persistence, MVC, and primary end-to-end flows.
