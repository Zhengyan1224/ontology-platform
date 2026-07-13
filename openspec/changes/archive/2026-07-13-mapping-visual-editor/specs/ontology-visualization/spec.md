# Ontology Visualization

## MODIFIED Requirements

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
- The UI SHALL support clicking a node to open an edit panel for its mapping configuration

#### Scenario: Graph node click opens edit panel
- **WHEN** a user clicks a node in the vis-network visualization
- **THEN** the UI SHALL display an edit panel with the node's current mapping properties
