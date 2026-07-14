## 1. Database Schema

- [x] 1.1 Create `init-content.sql` with `tenant_content` table DDL
- [x] 1.2 Add `init-content.sql` to `schema-locations` in both `application.yml` files

## 2. Backend Model & Persistence

- [x] 2.1 Add `owlContent` and `obdaContent` fields to `Tenant.java`
- [x] 2.2 Create `TenantContentRepository` for `tenant_content` table CRUD (find, upsert, delete)
- [x] 2.3 Update `TenantPersistenceService` to load `owlContent`/`obdaContent` from `tenant_content` on `findById()`/`findAll()`

## 3. Backend API Endpoints

- [x] 3.1 Add `POST /api/v1/tenants/{tenantId}/generate` endpoint (calls `OwlGeneratorService` + `ObdaGeneratorService`, returns content, does NOT save)
- [x] 3.2 Add `PUT /api/v1/tenants/{tenantId}/content` endpoint (saves `owlContent`/`obdaContent` to `tenant_content`, admin only)
- [x] 3.3 Add `POST /api/v1/tenants/{tenantId}/apply` endpoint (saves content + reinitializes engine + clears cache, admin only)

## 4. Engine Initialization

- [x] 4.1 Modify `OntopEngine.initialize()` to check `tenant.getOwlContent()`/`getObdaContent()` — if non-null, write to temp files and use temp URIs instead of file paths
- [x] 4.2 Ensure temp files are properly cleaned up on engine destroy

## 5. Frontend — Tenant Detail Page

- [x] 5.1 Add OWL/OBDA editor card to `tenant/index.html` with two textareas
- [x] 5.2 Implement "Generate from DB" button → `POST /generate` → fill textareas
- [x] 5.3 Implement "Save Draft" button → `PUT /content` → toast
- [x] 5.4 Implement "Save & Apply" button → `POST /apply` → toast + refresh status
- [x] 5.5 Load existing saved content on page load (from `GET /tenants/{id}`)

## 6. Frontend — Admin Page

- [x] 6.1 Add "Edit Content" link in admin Tenant list table

## 7. Build & Verify

- [x] 7.1 Run `mvn test` to confirm all existing tests pass
- [ ] 7.2 Start application and manually verify the full workflow: generate → edit → save → apply → query