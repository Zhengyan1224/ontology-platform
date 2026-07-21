# Ontology Graph Editing

## ADDED Requirements

### Requirement: View/Edit mode toggle

The system SHALL provide a mode toggle on the ontology visualization page to switch between View and Edit modes.

- View mode SHALL be the default
- In View mode, the graph SHALL be read-only (nodes draggable for position, no editing controls)
- In Edit mode, SHALL display:
  - Drag handles on class nodes
  - Right-click context menus on nodes and edges
  - Manipulation controls for adding edges
- Switching modes SHALL preserve the current draft state (unsaved edits are not lost)
- The mode toggle SHALL be a visible control in the page header

#### Scenario: Toggle between View and Edit mode
- **WHEN** the user clicks the mode toggle from View to Edit
- **THEN** the graph SHALL display edit handles on class nodes
- **THEN** right-click menus SHALL become available

#### Scenario: Switch back to View mode preserves edits
- **WHEN** the user adds a subClassOf edge in Edit mode
- **WHEN** the user switches to View mode
- **THEN** the new edge SHALL NOT be visible (draft edits hidden in View mode)
- **WHEN** the user switches back to Edit mode
- **THEN** the new edge SHALL reappear

### Requirement: Drag-to-create subClassOf edge

In Edit mode, the user SHALL be able to drag from one class node to another to create a new subClassOf relationship.

- Dragging SHALL be initiated from a visible drag handle on the source node
- On drop, a dialog SHALL appear asking the user to confirm the relationship type (Phase 1: subClassOf only)
- The new edge SHALL be rendered as a dashed line until applied
- The edge SHALL be added to the local AxiomConfig draft (not persisted until Apply)

#### Scenario: Drag creates new subClassOf edge
- **WHEN** the user drags from node "Magazine" to node "Publication" in Edit mode
- **THEN** a confirmation dialog SHALL appear with "Magazine is a subclass of Publication"
- **WHEN** the user confirms
- **THEN** a dashed edge SHALL appear from Magazine to Publication

#### Scenario: Cancel drag
- **WHEN** the user drags from one node to another
- **THEN** the dialog SHALL have a Cancel button
- **WHEN** the user clicks Cancel
- **THEN** no edge SHALL be created

### Requirement: Right-click context menu on nodes

In Edit mode, right-clicking a node SHALL display a context menu with actions.

- The menu SHALL include:
  - "Edit Name" — opens inline rename
  - "Add Subclass" — create a new class as subclass of this node
  - "Delete Class" — sets expose=false in the config (does not remove from source DB)
- The menu SHALL be positioned at the cursor location
- Clicking outside the menu SHALL close it

#### Scenario: Right-click node shows context menu
- **WHEN** the user right-clicks a class node in Edit mode
- **THEN** a context menu SHALL appear
- **THEN** the menu SHALL show "Edit Name", "Add Subclass", and "Delete Class"

#### Scenario: Delete class from context menu
- **WHEN** the user right-clicks a class node
- **WHEN** the user selects "Delete Class"
- **THEN** the node SHALL be removed from the graph view (expose=false)
- **THEN** the change SHALL be part of the draft until Apply

### Requirement: Right-click context menu on edges

In Edit mode, right-clicking an edge SHALL display a context menu with actions.

- The menu SHALL include:
  - "Edit" — opens edge properties (Phase 1: just label)
  - "Delete Edge" — removes the edge from the draft
- User-added edges (dashed) SHALL be deletable
- DB-derived edges (solid) SHALL show "Delete Edge" as well (sets an exclusion marker)

#### Scenario: Delete user-added edge
- **WHEN** the user right-clicks a dashed edge in Edit mode
- **WHEN** the user selects "Delete Edge"
- **THEN** the edge SHALL be removed from the graph

### Requirement: AxiomConfig storage

The system SHALL store user-added axioms in a JSON column `axiom_config` on the `tenant_content` table.

- The JSON SHALL have the following schema:
  ```json
  {
    "subClassOf": [
      { "child": "ClassName", "parent": "ClassName", "id": "uuid" }
    ],
    "layout": {
      "NodeId": { "x": number, "y": number }
    }
  }
  ```
- The backend SHALL expose endpoints to GET and PUT `axiom_config` for a tenant
- The backend SHALL validate the JSON schema before saving

#### Scenario: Save axiom config
- **WHEN** the user clicks Apply in Edit mode
- **THEN** the frontend SHALL send the current AxiomConfig to the backend
- **THEN** the backend SHALL validate and persist it

#### Scenario: Load axiom config
- **WHEN** the ontology graph page loads
- **THEN** the frontend SHALL fetch the AxiomConfig for the tenant
- **THEN** user-added edges SHALL be rendered as dashed lines
- **THEN** layout positions SHALL be applied to nodes

### Requirement: Apply merges DB-derived OWL with user axioms

When the user clicks Apply, the system SHALL regenerate the OWL Turtle by merging DB-derived content with user axioms.

- The merge SHALL be additive: DB generates classes, properties, FK-based ObjectProperties; AxiomConfig adds subClassOf statements
- The merged OWL SHALL be written to the tenant's OWL file
- The engine SHALL be reinitialized with the new OWL content
- The graph SHALL be refreshed (dashed edges become solid)

#### Scenario: Apply generates merged OWL
- **WHEN** the user clicks Apply after adding subClassOf edges
- **THEN** the generated OWL SHALL include `:Child rdfs:subClassOf :Parent` for each axiom
- **THEN** the engine SHALL restart with the new OWL
- **THEN** the graph SHALL refresh showing the new edges as solid lines

### Requirement: Generate from DB clears user edits

When the user clicks "Generate from DB", the system SHALL clear all user customizations.

- This SHALL clear: tableOverrides, columnOverrides, AxiomConfig (all subClassOf + layout)
- The graph SHALL reset to the DB-derived state
- A confirmation dialog SHALL appear before clearing

#### Scenario: Generate from DB resets the editor
- **WHEN** the user clicks "Generate from DB"
- **THEN** a confirmation dialog SHALL appear: "This will clear all custom axioms and edits. Continue?"
- **WHEN** the user confirms
- **THEN** all user-added edges SHALL be removed from the graph
- **THEN** all node names SHALL revert to DB-derived defaults
- **THEN** the graph SHALL use a fresh physics-based layout
