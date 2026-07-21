let currentEditableConfig = null;
let currentTenantId = null;
let network = null;
let nodesDataSet = null;
let edgesDataSet = null;

// AxiomConfig state (user-added axioms, not yet applied)
let axiomConfig = { subClassOf: [], layout: {} };
let isEditMode = false;
let appliedAxiomIds = new Set();

function getApiKey() {
  return document.getElementById('api-key-input').value.trim();
}

function authHeaders() {
  const apiKey = getApiKey();
  return apiKey ? { 'X-API-Key': apiKey } : {};
}

async function fetchJSON(url, options) {
  const opts = options || {};
  const headers = Object.assign({}, authHeaders(), opts.headers || {});
  const res = await fetch(url, Object.assign({}, opts, { headers }));
  if (!res.ok) {
    let message = res.statusText || ('HTTP ' + res.status);
    try { const body = await res.json(); message = body.message || body.error || message; } catch (ignored) {}
    throw new Error(message);
  }
  return res.json();
}

// --- Mode Toggle ---

document.getElementById('mode-view').addEventListener('click', () => setMode(false));
document.getElementById('mode-edit').addEventListener('click', () => setMode(true));

function setMode(edit) {
  isEditMode = edit;
  document.getElementById('mode-view').className = edit ? '' : 'active';
  document.getElementById('mode-edit').className = edit ? 'active' : '';
  document.getElementById('apply-button').style.display = edit ? '' : 'none';
  document.getElementById('ai-suggest-btn').style.display = edit ? '' : 'none';

  if (!network) return;
  if (isEditMode) {
    network.on('click', onEditClick);
  } else {
    network.off('click', onEditClick);
  }
}

// --- Load Draft ---

async function loadDraft(tenantId) {
  currentTenantId = tenantId;
  await loadAxiomConfig(tenantId);
  const leftPanel = document.getElementById('left-panel');
  leftPanel.innerHTML = '<div class="panel-header">Loading draft...</div>';
  try {
    const response = await fetchJSON('/api/v1/tenants/' + encodeURIComponent(tenantId) + '/mapping-assistant/draft', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ useLlm: false, includeDraftFiles: false })
    });
    currentEditableConfig = response.editableConfig;
    renderSchemaTree(currentEditableConfig);
    renderSuggestionsPanel(response);
    return response;
  } catch (e) {
    leftPanel.innerHTML = '<div class="panel-header">Error</div><div style="padding:8px;color:#c62828;">' + e.message + '</div>';
  }
}

// --- AxiomConfig Management ---

async function loadAxiomConfig(tenantId) {
  try {
    const data = await fetchJSON('/api/v1/tenants/' + encodeURIComponent(tenantId) + '/axiom-config', { headers: authHeaders() });
    axiomConfig.subClassOf = data.subClassOf || [];
    axiomConfig.layout = data.layout || {};
    appliedAxiomIds = new Set(axiomConfig.subClassOf.map(a => a.id));
  } catch (e) {
    axiomConfig = { subClassOf: [], layout: {} };
    appliedAxiomIds = new Set();
  }
}

async function saveAxiomConfig() {
  if (!currentTenantId) return;
  const body = { subClassOf: axiomConfig.subClassOf, layout: axiomConfig.layout };
  await fetchJSON('/api/v1/tenants/' + encodeURIComponent(currentTenantId) + '/axiom-config', {
    method: 'PUT',
    headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
    body: JSON.stringify(body)
  });
}

// --- Graph Integration ---

function renderGraph(data) {
  if (!data || !data.nodes) {
    showMessage('No graph data');
    return;
  }

  const nodeIds = new Set();
  const nodes = [];

  function addNode(n, fallbackType) {
    const id = n.id || n.name;
    if (!id || nodeIds.has(id)) return;
    nodeIds.add(id);
    const type = n.type || fallbackType || 'property';
    nodes.push({
      id: id,
      label: n.label || n.name || id,
      title: (type || '') + ': ' + (n.label || n.name || id),
      group: type,
      shape: nodeShape(type),
      color: nodeColor(type)
    });
  }

  (data.nodes || []).forEach(n => addNode(n, 'property'));

  (data.edges || []).forEach(e => {
    if (e.source && !nodeIds.has(e.source)) addNode({ id: e.source, label: extractLocalName(e.source) || e.source, type: 'property' });
    if (e.target && !nodeIds.has(e.target)) addNode({ id: e.target, label: extractLocalName(e.target) || e.target, type: e.propertyType === 'datatype' ? 'datatype' : 'property' });
  });

  // Add user axiom edges (dashed)
  const axiomEdges = [];
  for (const axiom of axiomConfig.subClassOf) {
    const applied = appliedAxiomIds.has(axiom.id);
    if (!nodeIds.has(axiom.child) || !nodeIds.has(axiom.parent)) continue;
    axiomEdges.push({
      from: axiom.child,
      to: axiom.parent,
      label: 'subClassOf',
      arrows: 'to',
      font: { size: 11, color: applied ? '#1976d2' : '#ff9800' },
      color: { color: applied ? '#1976d2' : '#ff9800', highlight: '#333' },
      dashes: !applied,
      axiomId: axiom.id,
      width: applied ? 1 : 2
    });
  }

  if (nodes.length === 0) {
    showMessage('No graph nodes available');
    return;
  }

  const baseEdges = (data.edges || [])
    .filter(e => e.source && e.target && nodeIds.has(e.source) && nodeIds.has(e.target))
    .map(e => ({
      from: e.source,
      to: e.target,
      label: e.label || '',
      arrows: 'to',
      font: { size: 11, color: '#666' },
      color: { color: '#999', highlight: '#333' },
      dashes: false
    }));

  const allEdges = baseEdges.concat(axiomEdges);

  nodesDataSet = new vis.DataSet(nodes);
  edgesDataSet = new vis.DataSet(allEdges);

  const container = document.getElementById('graph-container');
  container.textContent = '';
  const options = {
    autoResize: true,
    width: '100%',
    height: '100%',
    physics: {
      enabled: true,
      solver: 'forceAtlas2Based',
      stabilization: { enabled: true, iterations: 150, fit: true },
      forceAtlas2Based: { gravitationalConstant: -60, springLength: 140, springConstant: 0.06 }
    },
    interaction: { hover: true, tooltipDelay: 200, multiselect: false },
    layout: { improvedLayout: true },
    edges: { smooth: { type: 'continuous' } },
    manipulation: {
      enabled: false,
      addEdge: function(edgeData, callback) { handleAddEdge(edgeData, callback); }
    }
  };

  if (network) network.destroy();
  network = new vis.Network(container, { nodes: nodesDataSet, edges: edgesDataSet }, options);

  if (isEditMode) {
    network.on('click', onEditClick);
    network.on('oncontext', onContextMenu);
    network.setOptions({ manipulation: { enabled: true } });
  }

  // Restore layout
  applyLayout();

  network.once('stabilizationIterationsDone', () => {
    network.setOptions({ physics: false });
    if (!hasSavedLayout()) {
      network.fit({ animation: { duration: 300, easingFunction: 'easeInOutQuad' } });
    }
  });
  setTimeout(() => { if (network) network.fit(); }, 100);
}

function hasSavedLayout() {
  return Object.keys(axiomConfig.layout).length > 0;
}

function applyLayout() {
  if (!network || !axiomConfig.layout) return;
  const positions = {};
  for (const [id, pos] of Object.entries(axiomConfig.layout)) {
    if (nodesDataSet && nodesDataSet.get(id)) {
      positions[id] = { x: pos.x, y: pos.y };
    }
  }
  if (Object.keys(positions).length > 0) {
    network.storePositions();
    network.moveNode(positions);
    network.setOptions({ physics: false });
  }
}

function nodeShape(type) {
  if (type === 'class') return 'ellipse';
  if (type === 'datatype') return 'box';
  return 'diamond';
}

function nodeColor(type) {
  if (type === 'class') return { background: '#e3f2fd', border: '#1976d2' };
  if (type === 'datatype') return { background: '#e8f5e9', border: '#2e7d32' };
  return { background: '#fce4ec', border: '#c62828' };
}

// --- Edit Click Handler ---

function onEditClick(params) {
  if (params.nodes.length > 0) {
    editNode(params.nodes[0]);
  }
}

function editNode(nodeId) {
  if (!currentEditableConfig) return;
  const table = findTableByClassName(nodeId);
  if (table) {
    editClassNode(nodeId);
    return;
  }
  const parts = findPropertyByNodeId(nodeId);
  if (parts) {
    editPropertyNode(nodeId);
  }
}

// --- Drag-to-Create Edge ---

function handleAddEdge(edgeData, callback) {
  const fromNode = edgeData.from;
  const toNode = edgeData.to;
  if (!fromNode || !toNode) { callback(null); return; }

  showEdgeDialog(fromNode, toNode, function(confirmed) {
    if (confirmed) {
      const id = generateId();
      axiomConfig.subClassOf.push({ child: fromNode, parent: toNode, id: id });
      edgesDataSet.add({
        from: fromNode,
        to: toNode,
        label: 'subClassOf',
        arrows: 'to',
        font: { size: 11, color: '#ff9800' },
        color: { color: '#ff9800', highlight: '#333' },
        dashes: true,
        axiomId: id,
        width: 2
      });
      network.setOptions({ physics: true });
      network.stabilize();
    }
    callback(null);
  });
}

function showEdgeDialog(fromNode, toNode, callback) {
  const existing = document.querySelector('.edge-dialog-overlay');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.className = 'edge-dialog-overlay';
  overlay.onclick = function(e) { if (e.target === overlay) { overlay.remove(); callback(false); } };

  const panel = document.createElement('div');
  panel.className = 'edge-dialog';
  panel.innerHTML = '<h3>Create Relationship</h3>';
  panel.innerHTML += '<p style="font-size:14px;color:#333;margin-bottom:16px;">';
  panel.innerHTML += '<strong>' + escapeHtml(fromNode) + '</strong> → <strong>' + escapeHtml(toNode) + '</strong></p>';
  panel.innerHTML += '<div class="edge-type-option">';
  panel.innerHTML += '<input type="radio" name="edge-type" id="edge-subclassof" value="subClassOf" checked>';
  panel.innerHTML += '<label for="edge-subclassof"><strong>subClassOf</strong> — ' + escapeHtml(fromNode) + ' is a subclass of ' + escapeHtml(toNode) + '</label>';
  panel.innerHTML += '</div>';
  panel.innerHTML += '<div class="edge-dialog-actions">';
  panel.innerHTML += '<button onclick="this.closest(\'.edge-dialog-overlay\').remove(); callback(false)">Cancel</button>';
  panel.innerHTML += '<button class="primary" onclick="this.closest(\'.edge-dialog-overlay\').remove(); callback(true)">Create</button>';
  panel.innerHTML += '</div>';

  // Override callback scope
  const cancelBtn = panel.querySelector('.edge-dialog-actions button:first-child');
  const createBtn = panel.querySelector('.edge-dialog-actions button.primary');
  cancelBtn.onclick = function() { overlay.remove(); callback(false); };
  createBtn.onclick = function() { overlay.remove(); callback(true); };

  overlay.appendChild(panel);
  document.body.appendChild(overlay);
}

// --- Right-Click Context Menu ---

function onContextMenu(params) {
  params.event.preventDefault();
  removeContextMenu();

  if (params.nodes.length > 0) {
    showNodeContextMenu(params.nodes[0], params.event);
  } else if (params.edges.length > 0) {
    showEdgeContextMenu(params.edges[0], params.event);
  }
}

function showNodeContextMenu(nodeId, event) {
  const menu = document.createElement('div');
  menu.className = 'context-menu';

  const node = nodesDataSet ? nodesDataSet.get(nodeId) : null;
  const title = document.createElement('div');
  title.style.cssText = 'padding:6px 16px;font-size:11px;color:#999;border-bottom:1px solid #eee;';
  title.textContent = (node ? node.group : 'Node') + ': ' + nodeId;
  menu.appendChild(title);

  addMenuItem(menu, '✏ Edit Name', function() { editClassNode(nodeId); removeContextMenu(); });
  addMenuItem(menu, '➕ Add Subclass', function() { addSubclass(nodeId); removeContextMenu(); });
  addMenuItem(menu, '❌ Delete Class', function() { deleteClass(nodeId); removeContextMenu(); }, true);

  positionMenu(menu, event);
}

function showEdgeContextMenu(edgeId, event) {
  const edge = edgesDataSet ? edgesDataSet.get(edgeId) : null;
  if (!edge) return;

  const menu = document.createElement('div');
  menu.className = 'context-menu';

  const title = document.createElement('div');
  title.style.cssText = 'padding:6px 16px;font-size:11px;color:#999;border-bottom:1px solid #eee;';
  title.textContent = (edge.axiomId ? 'User axiom: ' : 'Edge: ') + (edge.label || '');
  menu.appendChild(title);

  if (edge.axiomId) {
    addMenuItem(menu, '❌ Delete Edge', function() { deleteAxiomEdge(edge.axiomId, edgeId); removeContextMenu(); }, true);
  } else {
    addMenuItem(menu, '⛔ Cannot delete DB-derived edge', function() { removeContextMenu(); });
  }

  positionMenu(menu, event);
}

function addMenuItem(menu, label, onClick, isDanger) {
  const item = document.createElement('div');
  item.className = 'context-menu-item' + (isDanger ? ' danger' : '');
  item.textContent = label;
  item.onclick = onClick;
  menu.appendChild(item);
}

function positionMenu(menu, event) {
  menu.style.left = event.clientX + 'px';
  menu.style.top = event.clientY + 'px';
  document.body.appendChild(menu);

  // Close on click outside
  setTimeout(function() {
    document.addEventListener('click', function closeMenu() {
      removeContextMenu();
      document.removeEventListener('click', closeMenu);
    });
  }, 0);
}

function removeContextMenu() {
  const existing = document.querySelector('.context-menu');
  if (existing) existing.remove();
}

// --- Node/Edge Actions ---

function addSubclass(parentNodeId) {
  const childName = prompt('Enter name for the new subclass of "' + parentNodeId + '":');
  if (!childName || !childName.trim()) return;
  const name = childName.trim();

  // Add node to graph
  const group = 'class';
  nodesDataSet.add({
    id: name,
    label: name,
    title: 'class: ' + name,
    group: group,
    shape: nodeShape(group),
    color: nodeColor(group)
  });

  // Add subclass edge as axiom
  const id = generateId();
  axiomConfig.subClassOf.push({ child: name, parent: parentNodeId, id: id });
  edgesDataSet.add({
    from: name,
    to: parentNodeId,
    label: 'subClassOf',
    arrows: 'to',
    font: { size: 11, color: '#ff9800' },
    color: { color: '#ff9800', highlight: '#333' },
    dashes: true,
    axiomId: id,
    width: 2
  });

  network.fit({ animation: { duration: 300 } });
}

function deleteClass(nodeId) {
  if (!confirm('Remove "' + nodeId + '" from the view? (This only hides it in the editor)')) return;
  // Remove associated axiom edges
  const toRemove = [];
  edgesDataSet.forEach(function(edge) {
    if (edge.axiomId && (edge.from === nodeId || edge.to === nodeId)) {
      toRemove.push(edge.id);
      axiomConfig.subClassOf = axiomConfig.subClassOf.filter(a => a.id !== edge.axiomId);
    }
  });
  toRemove.forEach(function(id) { edgesDataSet.remove(id); });
  nodesDataSet.remove(nodeId);
}

function deleteAxiomEdge(axiomId, edgeId) {
  edgesDataSet.remove(edgeId);
  axiomConfig.subClassOf = axiomConfig.subClassOf.filter(a => a.id !== axiomId);
  appliedAxiomIds.delete(axiomId);
}

// --- AI Suggest ---

document.getElementById('ai-suggest-btn').addEventListener('click', suggestAxioms);

async function suggestAxioms() {
  if (!currentTenantId) return;
  removeContextMenu();
  removeSuggestDialog();

  var overlay = document.createElement('div');
  overlay.className = 'edge-dialog-overlay';
  overlay.id = 'suggest-overlay';
  var panel = document.createElement('div');
  panel.className = 'edge-dialog';
  panel.style.maxWidth = '500px';
  panel.style.width = '90%';
  panel.innerHTML = '<h3>AI 建议</h3>';
  panel.innerHTML += '<p style="font-size:14px;color:#666;text-align:center;padding:20px 0;">正在获取建议...</p>';
  overlay.appendChild(panel);
  document.body.appendChild(overlay);

  try {
    var data = await fetchJSON('/api/v1/tenants/' + encodeURIComponent(currentTenantId) + '/axiom-config/suggest', {
      headers: Object.assign({}, authHeaders())
    });

    if (data.error) {
      panel.innerHTML = '<h3>AI 建议</h3>';
      panel.innerHTML += '<p style="font-size:14px;color:#c62828;padding:12px;">' + escapeHtml(data.error) + '</p>';
      panel.innerHTML += '<div class="edge-dialog-actions"><button onclick="removeSuggestDialog()">关闭</button></div>';
      return;
    }

    var suggestions = data.suggestions || [];
    if (suggestions.length === 0) {
      panel.innerHTML = '<h3>AI 建议</h3>';
      panel.innerHTML += '<p style="font-size:14px;color:#666;padding:12px;text-align:center;">暂无建议</p>';
      panel.innerHTML += '<div class="edge-dialog-actions"><button onclick="removeSuggestDialog()">关闭</button></div>';
      return;
    }

    var html = '<h3>AI 建议的子类关系</h3>';
    html += '<p style="font-size:12px;color:#999;margin-bottom:12px;">点击"接受"将建议添加到图中（虚线），点击"忽略"跳过。</p>';
    html += '<div style="max-height:400px;overflow-y:auto;">';
    for (var i = 0; i < suggestions.length; i++) {
      var s = suggestions[i];
      html += '<div class="suggest-item" data-index="' + i + '" style="border:1px solid #e0e0e0;border-radius:6px;padding:10px;margin-bottom:8px;background:#fafafa;">';
      html += '<div style="font-size:14px;font-weight:500;margin-bottom:4px;">';
      html += '<strong>' + escapeHtml(s.child) + '</strong> → <strong>' + escapeHtml(s.parent) + '</strong>';
      html += '</div>';
      html += '<div style="font-size:12px;color:#666;margin-bottom:8px;">' + escapeHtml(s.reason || '') + '</div>';
      html += '<div style="display:flex;gap:6px;">';
      html += '<button class="primary" style="font-size:12px;padding:4px 12px;" onclick="acceptSuggestion(' + i + ', \'' + escapeHtml(s.child) + '\', \'' + escapeHtml(s.parent) + '\')">接受</button>';
      html += '<button style="font-size:12px;padding:4px 12px;" onclick="dismissSuggestion(this)">忽略</button>';
      html += '</div></div>';
    }
    html += '</div>';
    html += '<div class="edge-dialog-actions"><button onclick="removeSuggestDialog()">关闭</button></div>';
    panel.innerHTML = html;
  } catch (e) {
    panel.innerHTML = '<h3>AI 建议</h3>';
    panel.innerHTML += '<p style="font-size:14px;color:#c62828;padding:12px;">请求失败：' + escapeHtml(e.message) + '</p>';
    panel.innerHTML += '<div class="edge-dialog-actions"><button onclick="removeSuggestDialog()">关闭</button></div>';
  }
}

function acceptSuggestion(index, child, parent) {
  var id = generateId();
  axiomConfig.subClassOf.push({ child: child, parent: parent, id: id });
  if (edgesDataSet && nodesDataSet) {
    // Ensure nodes exist
    if (!nodesDataSet.get(child)) {
      nodesDataSet.add({ id: child, label: child, title: 'class: ' + child, group: 'class', shape: 'ellipse', color: { background: '#e3f2fd', border: '#1976d2' } });
    }
    if (!nodesDataSet.get(parent)) {
      nodesDataSet.add({ id: parent, label: parent, title: 'class: ' + parent, group: 'class', shape: 'ellipse', color: { background: '#e3f2fd', border: '#1976d2' } });
    }
    edgesDataSet.add({
      from: child,
      to: parent,
      label: 'subClassOf',
      arrows: 'to',
      font: { size: 11, color: '#ff9800' },
      color: { color: '#ff9800', highlight: '#333' },
      dashes: true,
      axiomId: id,
      width: 2
    });
  }
  // Mark as accepted
  var item = document.querySelector('.suggest-item[data-index="' + index + '"]');
  if (item) {
    item.style.opacity = '0.5';
    item.style.pointerEvents = 'none';
    var btn = item.querySelector('button.primary');
    if (btn) btn.textContent = '已接受';
  }
}

function dismissSuggestion(btn) {
  var item = btn.closest('.suggest-item');
  if (item) {
    item.style.display = 'none';
  }
}

function removeSuggestDialog() {
  var existing = document.getElementById('suggest-overlay');
  if (existing) existing.remove();
}

// --- Apply ---

async function applyConfig() {
  if (!currentEditableConfig || !currentTenantId) return;

  // Save layout
  if (network) {
    const positions = network.getPositions();
    for (const [id, pos] of Object.entries(positions)) {
      axiomConfig.layout[id] = { x: pos.x, y: pos.y };
    }
  }

  try {
    // 1. Save axiom_config to DB
    await saveAxiomConfig();

    // 2. Apply naming config (existing behavior)
    const config = collectConfig();
    const response = await fetchJSON('/api/v1/tenants/' + encodeURIComponent(currentTenantId) + '/mapping-assistant/config', {
      method: 'PUT',
      headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
      body: JSON.stringify(config)
    });

    // 3. Reload axiom_config to update appliedAxiomIds
    await loadAxiomConfig(currentTenantId);

    // 4. Refresh graph
    currentEditableConfig = response.editableConfig;
    renderSchemaTree(currentEditableConfig);
    renderSuggestionsPanel(response);
    if (typeof loadGraph === 'function') {
      await loadGraph(currentTenantId);
    }

    showToast('Applied successfully');
  } catch (e) {
    showToast('Error: ' + e.message);
  }
}

document.getElementById('apply-button').addEventListener('click', applyConfig);

// --- Generate from DB (clear) ---

async function generateFromDb() {
  if (!currentTenantId) return;
  if (!confirm('Generate from DB will clear all custom axioms and edits. Continue?')) return;

  try {
    // Clear local state
    axiomConfig = { subClassOf: [], layout: {} };
    appliedAxiomIds = new Set();

    // Clear on server
    await fetchJSON('/api/v1/tenants/' + encodeURIComponent(currentTenantId) + '/axiom-config', {
      method: 'PUT',
      headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
      body: JSON.stringify(axiomConfig)
    });

    // Generate fresh content
    await loadDraft(currentTenantId);
    if (typeof loadGraph === 'function') {
      await loadGraph(currentTenantId);
    }
    showToast('Generated from DB - custom axioms cleared');
  } catch (e) {
    showToast('Error: ' + e.message);
  }
}

// --- Copy existing functions from original editor.js ---

function renderSchemaTree(config) {
  const leftPanel = document.getElementById('left-panel');
  if (!config || !config.tables || config.tables.length === 0) {
    leftPanel.innerHTML = '<div class="panel-header">Schema Tree</div><div style="padding:8px;color:#666;">No tables</div>';
    return;
  }
  let html = '<div class="panel-header">Schema Tree</div>';
  html += '<div class="action-bar">';
  html += '<button class="primary" onclick="applyConfig()">Apply</button>';
  html += '<button onclick="generateFromDb()">Generate from DB</button>';
  html += '</div>';
  config.tables.forEach(function(table, ti) {
    var collapsed = table.collapsed;
    html += '<div class="schema-table" data-table-index="' + ti + '">';
    html += '<div class="schema-table-header" onclick="toggleTable(' + ti + ')">';
    html += '<span class="toggle-icon">' + (collapsed ? '&#9654;' : '&#9660;') + '</span>';
    html += '<input class="class-name-input' + (table.classNameSuggested && table.classNameSuggested !== table.className ? ' suggested' : '') + '" value="' + escapeHtml(table.className) + '" data-table="' + ti + '" onclick="event.stopPropagation()" onchange="updateTableClassName(' + ti + ', this.value)">';
    html += '<span class="table-name-label">' + escapeHtml(table.tableName) + '</span>';
    html += '</div>';
    html += '<div class="schema-columns" id="table-columns-' + ti + '"' + (collapsed ? ' style="display:none"' : '') + '>';
    table.columns.forEach(function(col, ci) {
      var isSuggested = col.propertyNameSuggested && col.propertyNameSuggested !== col.propertyName;
      html += '<div class="schema-column">';
      html += '<input type="checkbox" ' + (col.expose ? 'checked' : '') + ' data-table="' + ti + '" data-col="' + ci + '" onchange="updateColumnExpose(' + ti + ',' + ci + ', this.checked)">';
      html += '<input class="col-name-input' + (isSuggested ? ' suggested' : '') + '" value="' + escapeHtml(col.propertyName) + '" data-table="' + ti + '" data-col="' + ci + '" onchange="updateColumnName(' + ti + ',' + ci + ', this.value)">';
      if (col.isPk) html += '<span class="col-pk-badge">PK</span>';
      if (col.isFk) html += '<span class="col-fk-badge">FK</span>';
      html += '<span class="col-raw-name">' + escapeHtml(col.columnName) + '</span>';
      html += '</div>';
    });
    html += '</div>';
    html += '</div>';
  });
  leftPanel.innerHTML = html;
}

function escapeHtml(str) {
  if (typeof str !== 'string') return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function toggleTable(index) {
  var el = document.getElementById('table-columns-' + index);
  if (el) el.style.display = el.style.display === 'none' ? '' : 'none';
}

function updateTableClassName(ti, value) {
  if (currentEditableConfig && currentEditableConfig.tables[ti]) {
    currentEditableConfig.tables[ti].className = value;
  }
}

function updateColumnName(ti, ci, value) {
  if (currentEditableConfig && currentEditableConfig.tables[ti] && currentEditableConfig.tables[ti].columns[ci]) {
    currentEditableConfig.tables[ti].columns[ci].propertyName = value;
  }
}

function updateColumnExpose(ti, ci, value) {
  if (currentEditableConfig && currentEditableConfig.tables[ti] && currentEditableConfig.tables[ti].columns[ci]) {
    currentEditableConfig.tables[ti].columns[ci].expose = value;
  }
}

function renderSuggestionsPanel(response) {
  var panel = document.getElementById('right-panel');
  var html = '<div class="panel-header">LLM Review</div>';
  if (response.reviewMarkdown) {
    html += '<div style="font-size:13px;line-height:1.5;">' + simpleMarkdown(response.reviewMarkdown) + '</div>';
  } else {
    html += '<div style="padding:8px;color:#666;">No review available</div>';
  }
  html += '<div class="panel-header" style="margin-top:12px;">Draft Files</div>';
  html += '<div class="action-bar">';
  html += '<button onclick="showDraftPopup(\'owl\')">View OWL</button>';
  html += '<button onclick="showDraftPopup(\'obda\')">View OBDA</button>';
  html += '</div>';
  panel.innerHTML = html;
}

function simpleMarkdown(text) {
  if (!text) return '';
  var lines = text.split('\n');
  var html = '';
  var inCode = false;
  lines.forEach(function(line) {
    if (line.trim().startsWith('```')) {
      if (inCode) { html += '</code></pre>'; inCode = false; }
      else { html += '<pre><code>'; inCode = true; }
      return;
    }
    if (inCode) { html += escapeHtml(line) + '\n'; return; }
    if (line.trim().startsWith('### ')) html += '<h4 style="margin:8px 0 4px;">' + escapeHtml(line.substr(4)) + '</h4>';
    else if (line.trim().startsWith('## ')) html += '<h4 style="margin:8px 0 4px;color:#1976d2;">' + escapeHtml(line.substr(3)) + '</h4>';
    else if (line.trim().startsWith('- ')) html += '<li style="margin:2px 0;">' + escapeHtml(line.substr(2)) + '</li>';
    else if (line.trim() === '') html += '<br>';
    else html += '<p style="margin:2px 0;">' + escapeHtml(line) + '</p>';
  });
  if (inCode) html += '</code></pre>';
  return html;
}

function showDraftPopup(type) {
  var existing = document.querySelector('.edit-panel-overlay');
  if (existing) existing.remove();
  var overlay = document.createElement('div');
  overlay.className = 'edit-panel-overlay';
  overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
  var panel = document.createElement('div');
  panel.className = 'edit-panel';
  panel.style.maxWidth = '700px';
  panel.style.maxHeight = '80vh';
  panel.style.overflow = 'auto';
  panel.innerHTML = '<h3>' + type.toUpperCase() + ' Draft</h3>';
  panel.innerHTML += '<pre style="font-size:12px;white-space:pre-wrap;word-break:break-all;background:#f5f5f5;padding:8px;border-radius:4px;max-height:60vh;overflow:auto;">Loading...</pre>';
  panel.innerHTML += '<div class="edit-actions"><button onclick="this.closest(\'.edit-panel-overlay\').remove()">Close</button></div>';
  overlay.appendChild(panel);
  document.body.appendChild(overlay);
  loadDraftFile(type, panel.querySelector('pre'));
}

async function loadDraftFile(type, preEl) {
  if (!currentTenantId) return;
  try {
    var response = await fetchJSON('/api/v1/tenants/' + encodeURIComponent(currentTenantId) + '/mapping-assistant/draft', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ useLlm: false, includeDraftFiles: true })
    });
    var content = type === 'owl' ? response.owlDraft : response.obdaDraft;
    preEl.textContent = content || '(empty)';
  } catch (e) {
    preEl.textContent = 'Error: ' + e.message;
  }
}

function collectConfig() {
  if (!currentEditableConfig) return { tables: [], relationships: [] };
  var tables = currentEditableConfig.tables.map(function(table) {
    return {
      tableName: table.tableName,
      className: table.className,
      iriTemplate: table.iriTemplate,
      expose: table.expose !== false,
      columns: table.columns.map(function(col) {
        return { columnName: col.columnName, propertyName: col.propertyName, expose: col.expose !== false };
      })
    };
  });
  return { tables: tables, relationships: currentEditableConfig.relationships || [] };
}

function setupGraphClickHandler(network) {}

function editClassNode(nodeId) {
  if (!currentEditableConfig) return;
  var table = findTableByClassName(nodeId);
  if (!table) return;
  showEditOverlay('Class: ' + escapeHtml(nodeId), function(html) {
    html += '<label>Class Name: <input id="edit-class-name" type="text" value="' + escapeHtml(table.className) + '"></label>';
    html += '<label>Expose: <input id="edit-class-expose" type="checkbox" ' + (table.expose !== false ? 'checked' : '') + '></label>';
    return html;
  }, function() {
    var newName = document.getElementById('edit-class-name').value.trim();
    var newExpose = document.getElementById('edit-class-expose').checked;
    if (newName) {
      table.className = newName;
      table.expose = newExpose;
      renderSchemaTree(currentEditableConfig);
    }
  });
}

function editPropertyNode(nodeId) {
  if (!currentEditableConfig) return;
  var parts = findPropertyByNodeId(nodeId);
  if (!parts) return;
  var table = parts.table, col = parts.col;
  showEditOverlay('Property: ' + escapeHtml(nodeId), function(html) {
    html += '<label>Property Name: <input id="edit-prop-name" type="text" value="' + escapeHtml(col.propertyName) + '"></label>';
    html += '<label>Expose: <input id="edit-prop-expose" type="checkbox" ' + (col.expose !== false ? 'checked' : '') + '></label>';
    return html;
  }, function() {
    var newName = document.getElementById('edit-prop-name').value.trim();
    var newExpose = document.getElementById('edit-prop-expose').checked;
    if (newName) {
      col.propertyName = newName;
      col.expose = newExpose;
      renderSchemaTree(currentEditableConfig);
    }
  });
}

function findTableByClassName(nodeId) {
  if (!currentEditableConfig) return null;
  // Try direct match first (for backward compat with local-name IDs)
  for (var i = 0; i < currentEditableConfig.tables.length; i++) {
    if (currentEditableConfig.tables[i].className === nodeId) return currentEditableConfig.tables[i];
  }
  // If node ID is an IRI, try matching by local name
  var localName = extractLocalName(nodeId);
  if (localName && localName !== nodeId) {
    for (var i = 0; i < currentEditableConfig.tables.length; i++) {
      if (currentEditableConfig.tables[i].className === localName) return currentEditableConfig.tables[i];
    }
  }
  return null;
}

function extractLocalName(iri) {
  if (!iri || typeof iri !== 'string') return iri;
  var hash = iri.lastIndexOf('#');
  if (hash >= 0) return iri.substring(hash + 1);
  var slash = iri.lastIndexOf('/');
  if (slash >= 0) return iri.substring(slash + 1);
  return iri;
}

function findPropertyByNodeId(nodeId) {
  if (!currentEditableConfig) return null;
  var candidates = [nodeId];
  var localName = extractLocalName(nodeId);
  if (localName && localName !== nodeId) candidates.push(localName);
  for (var i = 0; i < currentEditableConfig.tables.length; i++) {
    var table = currentEditableConfig.tables[i];
    for (var j = 0; j < table.columns.length; j++) {
      if (candidates.indexOf(table.columns[j].propertyName) >= 0) return { table: table, col: table.columns[j] };
    }
  }
  return null;
}

function showEditOverlay(title, contentFn, onSave) {
  var existing = document.querySelector('.edit-panel-overlay');
  if (existing) existing.remove();
  var overlay = document.createElement('div');
  overlay.className = 'edit-panel-overlay';
  overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
  var panel = document.createElement('div');
  panel.className = 'edit-panel';
  panel.innerHTML = '<h3>' + title + '</h3>';
  panel.innerHTML += contentFn('');
  var actions = document.createElement('div');
  actions.className = 'edit-actions';
  var cancelBtn = document.createElement('button');
  cancelBtn.textContent = 'Cancel';
  cancelBtn.onclick = function() { overlay.remove(); };
  actions.appendChild(cancelBtn);
  var saveBtn = document.createElement('button');
  saveBtn.className = 'primary';
  saveBtn.textContent = 'Save';
  saveBtn.onclick = function() { onSave(); overlay.remove(); };
  actions.appendChild(saveBtn);
  panel.appendChild(actions);
  overlay.appendChild(panel);
  document.body.appendChild(overlay);
}

function showToast(message) {
  var existing = document.querySelector('.toast');
  if (existing) existing.remove();
  var toast = document.createElement('div');
  toast.className = 'toast';
  toast.textContent = message;
  toast.style.cssText = 'position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:#333;color:#fff;padding:10px 20px;border-radius:6px;font-size:14px;z-index:200;box-shadow:0 2px 10px rgba(0,0,0,0.2);';
  document.body.appendChild(toast);
  setTimeout(function() { toast.remove(); }, 3000);
}

function generateId() {
  return 'ax-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
}
