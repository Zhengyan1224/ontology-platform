# Ontology Visualization

## Purpose

Expose the ontology structure as a JSON graph (nodes + edges) suitable for frontend visualization with D3.js or vis.js.

## Requirements

### Requirement: Ontology graph data API

The system SHALL provide an endpoint that returns the ontology structure as a graph suitable for visualization.

- Endpoint: `GET /api/v1/tenants/{tenantId}/graph`
- Response format SHALL be JSON with `nodes` and `edges` arrays
- Nodes SHALL represent:
  - OWL classes (with `type: "class"`, `name`, `label` if available)
  - OWL properties (with `type: "property"`, `name`, `domain`, `range`)
- Edges SHALL represent:
  - `rdfs:subClassOf` relationships
  - `rdfs:subPropertyOf` relationships
  - Property domain/range connections
- The response SHALL be suitable for rendering with D3.js force-directed graph or vis.js

#### Scenario: Graph endpoint returns ontology structure
- **WHEN** a GET request is sent to `/api/v1/tenants/{tenantId}/graph`
- **THEN** the response SHALL contain a JSON object with `nodes` and `edges`
- **THEN** each node SHALL have `id`, `type`, and `name` fields
- **THEN** each edge SHALL have `source`, `target`, and `label` fields

#### Scenario: Tenant not found
- **WHEN** the tenant ID does not exist
- **THEN** the response SHALL have status 404

### Requirement: Graph data caching

The ontology graph data SHALL be cached to avoid re-parsing on every request.

- The graph data SHALL be cached in memory per tenant
- Cache SHALL be invalidated when the tenant is reinitialized
- Cache TTL SHALL be configurable via `ontology.viz.cache-ttl` (default 600 seconds)

#### Scenario: Subsequent graph requests use cache
- **WHEN** a graph request is made for a previously queried tenant
- **THEN** the response SHALL be served from cache
