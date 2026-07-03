## 1. Dependencies & Config

- [ ] 1.1 Add `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` to `pom.xml`
- [ ] 1.2 Add OTel exporter + sampling config to `application.yml`
- [ ] 1.3 Add OTel Agent JVM argument documentation to `AGENTS.md`

## 2. Custom @Observed Spans

- [ ] 2.1 Annotate `SparqlController.executeQuery()` with `@Observed`
- [ ] 2.2 Annotate `NaturalLanguageQueryService.answer()` with `@Observed`
- [ ] 2.3 Annotate `FederatedQueryService.executeFederatedQuery()` with `@Observed`
- [ ] 2.4 Annotate `CachedSparqlService.executeQuery()` with `@Observed`

## 3. Verify

- [ ] 3.1 Run application with Agent and verify traces appear in OTel endpoint
- [ ] 3.2 Run test suite to confirm no regression
