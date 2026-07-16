## ADDED Requirements

### Requirement: Document Upload
System SHALL accept document uploads (PDF, DOCX, TXT) and store them per tenant.

#### Scenario: Upload a PDF document
- **WHEN** ADMIN sends POST /tenants/{tenantId}/documents with multipart file of type application/pdf
- **THEN** system returns 201 with document metadata

#### Scenario: Reject unsupported file type
- **WHEN** ADMIN uploads .exe file
- **THEN** system returns 415 Unsupported Media Type

### Requirement: Document Processing
System SHALL parse uploaded documents into text chunks with configurable chunk size and overlap, then generate vector embeddings.

#### Scenario: Process a document
- **WHEN** ADMIN sends POST /tenants/{tenantId}/documents/{documentId}/process
- **THEN** system parses the document, creates N chunks with embeddings, and returns 200 with chunk_count

#### Scenario: Query document chunks by similarity
- **WHEN** ADMIN or DEV sends POST /tenants/{tenantId}/documents/query with {text: "What is the return policy?", topK: 5}
- **THEN** system returns 200 with top K matching chunks and similarity scores

### Requirement: Document Browsing
System SHALL list and allow deleting documents per tenant.

#### Scenario: List tenant documents
- **WHEN** ADMIN or DEV sends GET /tenants/{tenantId}/documents
- **THEN** system returns 200 with document list

#### Scenario: Delete document
- **WHEN** ADMIN sends DELETE /tenants/{tenantId}/documents/{documentId}
- **THEN** system deletes the document and its chunks, returns 204

### Requirement: MCP Document Tools
System SHALL expose document operations as MCP tools.

#### Scenario: MCP queries documents
- **WHEN** MCP client calls document_query tool with text and topK params
- **THEN** response includes matching chunks with scores
