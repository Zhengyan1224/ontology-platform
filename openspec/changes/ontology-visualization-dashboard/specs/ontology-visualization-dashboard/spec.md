# Ontology Visualization Dashboard

## ADDED Requirements

### Requirement: Browser-based ontology graph visualization

The system SHALL provide a browser-accessible page that renders the ontology graph using vis-network.

- Page SHALL be available at `/ontology-viz/` when the server is running
- Page SHALL load vis-network from CDN (`vis-network/standalone`)
- Page SHALL render a force-directed graph with:
  - OWL classes as labeled nodes
  - OWL properties as labeled nodes  
  - subclass/subproperty relationships as directed edges
  - Property domain/range connections as directed edges
- Graph SHALL support: drag, zoom, click-to-highlight connected nodes

#### Scenario: Dashboard loads and renders ontology graph
- **WHEN** a browser navigates to `/ontology-viz/`
- **THEN** the page SHALL display the ontology graph for the default tenant
- **THEN** nodes SHALL be draggable
- **THEN** the page SHALL support zoom in/out

### Requirement: Tenant selector

The dashboard SHALL allow the user to switch between tenants.

- A dropdown SHALL list all available tenants
- Selecting a tenant SHALL fetch and render that tenant's graph
- The dropdown SHALL be populated from `GET /api/v1/tenants`

#### Scenario: User switches tenant
- **WHEN** the user selects a different tenant from the dropdown
- **THEN** the page SHALL fetch the graph for the selected tenant
- **THEN** the graph SHALL update to show the new tenant's ontology

### Requirement: Node search

The dashboard SHALL provide a search input to filter ontology nodes by name.

- The search SHALL match node names case-insensitively
- Matching nodes SHALL be highlighted
- Non-matching nodes SHALL be dimmed
- Clearing the search SHALL restore all nodes

#### Scenario: User searches for a node
- **WHEN** the user types "Employee" in the search box
- **THEN** nodes containing "Employee" (case-insensitive) SHALL be highlighted
- **THEN** non-matching nodes SHALL be dimmed

#### Scenario: Clear search restores all nodes
- **WHEN** the user clears the search input
- **THEN** all nodes SHALL return to full visibility
