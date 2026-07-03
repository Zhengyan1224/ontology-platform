## 1. Static Page

- [x] 1.1 Create `src/main/resources/static/ontology-viz/index.html` with vis-network CDN, tenant selector, search bar, and graph container
- [ ] 1.2 Verify page loads at `http://localhost:8080/ontology-viz/` and renders graph for default tenant (manual: `mvn spring-boot:run` → open browser)

## 2. Security

- [x] 2.1 Add `/ontology-viz/**` to anonymous access whitelist in `SecurityConfig.java`
- [ ] 2.2 Verify unauthenticated browser can load the page (manual)

## 3. Polish & Test

- [ ] 3.1 Verify tenant switching fetches and re-renders new graph (manual)
- [ ] 3.2 Verify node search filters correctly (manual)
