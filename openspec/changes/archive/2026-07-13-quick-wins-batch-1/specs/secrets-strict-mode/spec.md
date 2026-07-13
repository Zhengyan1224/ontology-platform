## ADDED Requirements

### Requirement: Default secret detection on startup
The system SHALL detect when critical security configuration values are set to their default values and, when strict mode is enabled, prevent the application from starting.

The following secrets SHALL be checked:
- `ontology.auth.jwt-secret`: default is `default-jwt-secret-key`
- `ontology.auth.admin-password`: default is `admin123`
- `ontology.nlq.llm.api-key`: default is `sk-placeholder`

The strict mode behavior SHALL be controlled by a new configuration property `ontology.auth.strict-mode`:
- Default value: `false` (backward compatible — only log a warning)
- When `true`: throw `OntologyPlatformException` on startup if any default secret is detected

#### Scenario: Strict mode disabled logs warning
- **WHEN** `ontology.auth.strict-mode` is `false` or not set
- **AND** the application starts with default secrets
- **THEN** the system SHALL log a warning for each default secret
- **AND** the application SHALL start successfully

#### Scenario: Strict mode enabled blocks startup
- **WHEN** `ontology.auth.strict-mode` is `true`
- **AND** the application starts with default secrets
- **THEN** the system SHALL throw `OntologyPlatformException`
- **AND** the application SHALL fail to start

#### Scenario: Strict mode with environment variables starts successfully
- **WHEN** `ontology.auth.strict-mode` is `true`
- **AND** all secrets are overridden via environment variables (`JWT_SECRET`, `ADMIN_PASSWORD`, `LLM_API_KEY`)
- **THEN** the system SHALL start successfully
- **AND** no warning or error SHALL be logged for secrets
