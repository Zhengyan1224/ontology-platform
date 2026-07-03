# Ontology Platform — Agent Guide

## Build & Run

```bash
mvn spring-boot:run              # dev server on :8080
mvn exec:java                    # alternative (exec-maven-plugin)
mvn test                         # all tests (4 classes, H2 in-memory, no deps)
```

**OTel Agent (optional):** For auto-instrumentation of HTTP, JDBC, and thread pool spans,
add `-javaagent:path/to/opentelemetry-javaagent.jar` to JVM args.
Custom business spans via `@Observed` (SPARQL, NLQ, federated queries) work without the Agent.

- Java 21 required
- Default H2 in-memory DB — data lost on restart. SQL init: `src/main/resources/db/init-{books,university}.sql`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`

## Architecture

Spring Boot 3.4.3 + Ontop 5.5 + RDF4J 5.1.4. OBDA platform: SPARQL → Ontop → SQL → RDB → RDF tuples.

**Base package**: `org.zhengyan.ontology.platform` — packages: `config`, `controller`, `engine`, `exception`, `model`, `service`

**Main class**: `OntologyPlatformApplication.java`

**Two built-in tenants** (`sample`=books, `university`=reasoning demo), each with OWL + OBDA files in `src/main/resources/ontologies/`.

NLQ feature uses LangChain4j + OpenAI-compatible API; with `sk-placeholder` API key runs in template-only mode.

## Key Quirks

- **Maven repo**: Ontop artifacts come from `https://maven.ontop.informatik.uni-bremen.de/releases` (configured in `pom.xml`)
- **Spring Boot repackage is skipped** (`<skip>true</skip>` on spring-boot-maven-plugin) — use `spring-boot:run` or `exec:java` directly
- **No linter/formatter/typecheck config** — the project has none
- **No CI/CD, no Dockerfile** — none present
- **Tenants defined in `application.yml`** under `ontology.tenants` — add new tenant there with JDBC URL/creds, OWL path, OBDA path

## OpenSpec Workflow

Change-driven development tracked via `.opencode/skills/openspec-*` skills and `openspec/` directory.
