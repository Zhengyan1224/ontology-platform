## 1. Refactor — Extract shared JDBC metadata reader

- [ ] 1.1 Create `JdbcMetadataReader` class with table/column/PK/FK reading methods extracted from `OwlGeneratorService`
- [ ] 1.2 Update `OwlGeneratorService` to delegate to `JdbcMetadataReader`
- [ ] 1.3 Verify `OwlGeneratorServiceTest` still passes after refactor

## 2. Core — ObdaGeneratorService

- [ ] 2.1 Create `ObdaGeneratorService` that reads metadata via `JdbcMetadataReader` and outputs OBDA format
- [ ] 2.2 Implement per-table class mapping generation (IRI template + class declaration + datatype properties)
- [ ] 2.3 Implement FK → object property mapping generation
- [ ] 2.4 Implement join table detection and handling per `join-table-behavior` config
- [ ] 2.5 Support `iri-template` configuration (`/{pk}` and `-{pk}` styles)

## 3. Configuration — Extend OwlGenerationProperties

- [ ] 3.1 Add `iriTemplate`, `joinTableBehavior`, `mappingStyle` fields to `OwlGenerationProperties`
- [ ] 3.2 Add corresponding YAML defaults to `application.yml`

## 4. Endpoint — Unified generate-mapping API

- [ ] 4.1 Add `generateMapping` method to existing Admin/GenerateController (or relevant controller)
- [ ] 4.2 Implement ZIP response combining OWL + OBDA files
- [ ] 4.3 Mark existing `POST /generate-owl` endpoint as `@Deprecated`
- [ ] 4.4 Wire `ObdaGeneratorService` into the controller

## 5. Test

- [ ] 5.1 Create `ObdaGeneratorServiceTest` with test coverage for: basic table mapping, FK→object property, join tables, empty DB, custom IRI template
- [ ] 5.2 Extend web-layer test to verify `/generate-mapping` endpoint returns ZIP
- [ ] 5.3 Run full test suite (149 expected)
