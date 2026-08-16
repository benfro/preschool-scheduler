# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is an early-stage Quarkus project. Only the default scaffolded `GreetingResource` exists so far — no domain model, no Timefold solver configuration, and no LangChain4j/Ollama wiring has been written yet, even though those dependencies are already declared in `pom.xml`. `src/main/resources/application.properties` is currently empty.

Given the project name (`preschool-scheduler`) and dependencies, the intended architecture is a Quarkus REST service that uses **Timefold Solver** to generate schedules (e.g. staff/room/child assignments) as a constraint-satisfaction/optimization problem, with **LangChain4j + Ollama** available for LLM-assisted features. When implementing new functionality, follow Timefold's Quarkus integration conventions (planning entities/variables, constraint providers, solver config) rather than inventing ad hoc scheduling logic.

## Commands

Build tool is Maven, invoked via the wrapper (`./mvnw`) — no local Maven install required.

- Run in dev mode (live reload, Dev UI at `http://localhost:8080/q/dev/`): `./mvnw quarkus:dev`
- Run all unit tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=GreetingResourceTest`
- Run a single test method: `./mvnw test -Dtest=GreetingResourceTest#testHelloEndpoint`
- Package: `./mvnw package` — produces `target/quarkus-app/quarkus-run.jar` (not an über-jar; deps land in `target/quarkus-app/lib/`)
- Package as über-jar: `./mvnw package -Dquarkus.package.jar.type=uber-jar`
- Run packaged app: `java -jar target/quarkus-app/quarkus-run.jar`
- Build native executable: `./mvnw package -Dnative` (or `-Dquarkus.native.container-build=true` to build in a container without local GraalVM)
- Integration tests (`*IT.java`, run via failsafe against the packaged/native app): enabled only under the `native` Maven profile (`-Dnative`), since `skipITs=true` otherwise.

## Architecture notes

- Base package: `net.benfro`.
- Java 25, built as a `quarkus` packaging-type Maven project (not a plain jar) via `quarkus-maven-plugin`.
- Dependency versions are centralized through the Quarkus BOM (`quarkus.platform.version` in `pom.xml`) and the `quarkus-langchain4j-bom`; add new Quarkus/LangChain4j extensions without repeating explicit versions where possible.
- Test naming convention follows Quarkus defaults: `*Test.java` classes are plain `@QuarkusTest` (JVM mode, run by surefire); `*IT.java` classes typically extend the matching `Test` class and are annotated `@QuarkusIntegrationTest` (run against the packaged artifact by failsafe, only in the `native` profile).
- Container images are built via the `quarkus-container-image-podman` extension (Podman, not Docker, is the expected local container runtime) — `src/main/docker/` holds the Dockerfiles for jvm/legacy-jar/native/native-micro image variants.
