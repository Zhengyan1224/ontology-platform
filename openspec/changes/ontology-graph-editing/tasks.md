## 1. Backend: AxiomConfig Data Layer

- [x] 1.1 Add `axiom_config` TEXT column to `tenant_content` table (add init SQL migration)
- [x] 1.2 Update `TenantContentRepository` to read/write `axiom_config` in upsert and findByTenantId
- [x] 1.3 Add GET and PUT endpoints for axiom_config in `ContentController` (`/tenants/{tenantId}/axiom-config`)
- [x] 1.4 Add server-side validation for axiom_config JSON schema (required fields, types)

## 2. Backend: OWL Generation with Axiom Merging

- [x] 2.1 Read `axiom_config` in `OwlGeneratorService.generateOwl()` and append subClassOf triples
- [x] 2.2 Invalidate `OntologyGraphService` cache after Apply
- [x] 2.3 Clear axiom_config on "Generate from DB" (in the generate endpoint)

## 3. Frontend: View/Edit Mode and UI

- [x] 3.1 Add View/Edit toggle button to the page header
- [x] 3.2 Add CSS styles for edit mode (node handles, dashed edges, context menus)
- [x] 3.3 Style the mode toggle, right-click menus, and edge creation dialog

## 4. Frontend: Drag-to-Create Edge

- [x] 4.1 Enable vis-network manipulation in Edit mode
- [x] 4.2 Implement drag handle visibility on class nodes
- [x] 4.3 Implement edge creation dialog (Phase 1: subClassOf only)
- [x] 4.4 Add new edge to local AxiomConfig draft (dashed rendering)

## 5. Frontend: Right-Click Context Menu

- [x] 5.1 Implement node context menu (Edit Name, Add Subclass, Delete Class)
- [x] 5.2 Implement edge context menu (Edit, Delete Edge)
- [x] 5.3 Wire "Add Subclass" to create a new class node + subClassOf edge
- [x] 5.4 Wire "Delete Class" to set expose=false in the config

## 6. Frontend: Layout Persistence

- [x] 6.1 Save node positions to axiom_config.layout on Apply
- [x] 6.2 Restore node positions from axiom_config.layout on graph load
- [x] 6.3 Clear layout on "Generate from DB"

## 7. Frontend: Apply and Sync

- [x] 7.1 Collect all draft changes (AxiomConfig + naming overrides) on Apply click
- [x] 7.2 Send axiom_config to backend via PUT endpoint
- [x] 7.3 Trigger OWL/OBDA regeneration and engine reinit
- [x] 7.4 Refresh graph — render newly applied edges as solid lines

## 8. Frontend: Generate from DB

- [x] 8.1 Add confirmation dialog before clearing
- [x] 8.2 Clear local AxiomConfig draft and naming overrides
- [x] 8.3 Call generate endpoint and re-render graph fresh

## 9. Integration and Manual Testing

- [ ] 9.1 Verify View/Edit toggle works without data loss
- [ ] 9.2 Verify drag-to-create subClassOf edge flow end-to-end
- [ ] 9.3 Verify Apply merges user axioms with DB-derived OWL correctly
- [ ] 9.4 Verify Generate from DB clears all customizations
- [ ] 9.5 Verify layout persistence across page reloads
- [ ] 9.6 Verify multi-tenant switching preserves per-tenant axiom_config
