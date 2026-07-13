let currentEditableConfig = null;
let currentTenantId = null;

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

async function loadDraft(tenantId) {
  currentTenantId = tenantId;
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

function renderSchemaTree(config) {
  const leftPanel = document.getElementById('left-panel');
  if (!config || !config.tables || config.tables.length === 0) {
    leftPanel.innerHTML = '<div class="panel-header">Schema Tree</div><div style="padding:8px;color:#666;">No tables</div>';
    return;
  }
  let html = '<div class="panel-header">Schema Tree</div>';
  html += '<div class="action-bar">';
  html += '<button class="primary" onclick="applyConfig()">Apply</button>';
  html += '<button onclick="loadDraft(currentTenantId)">Reset</button>';
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
  if (el) {
    el.style.display = el.style.display === 'none' ? '' : 'none';
  }
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
    if (inCode) {
      html += escapeHtml(line) + '\n';
      return;
    }
    if (line.trim().startsWith('### ')) {
      html += '<h4 style="margin:8px 0 4px;">' + escapeHtml(line.substr(4)) + '</h4>';
    } else if (line.trim().startsWith('## ')) {
      html += '<h4 style="margin:8px 0 4px;color:#1976d2;">' + escapeHtml(line.substr(3)) + '</h4>';
    } else if (line.trim().startsWith('- ')) {
      html += '<li style="margin:2px 0;">' + escapeHtml(line.substr(2)) + '</li>';
    } else if (line.trim() === '') {
      html += '<br>';
    } else {
      html += '<p style="margin:2px 0;">' + escapeHtml(line) + '</p>';
    }
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

async function applyConfig() {
  if (!currentEditableConfig || !currentTenantId) return;
  var config = collectConfig();
  try {
    var response = await fetchJSON('/api/v1/tenants/' + encodeURIComponent(currentTenantId) + '/mapping-assistant/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    currentEditableConfig = response.editableConfig;
    renderSchemaTree(currentEditableConfig);
    renderSuggestionsPanel(response);
    if (typeof loadGraph === 'function') {
      loadGraph(currentTenantId);
    }
    showToast('Config applied and OWL/OBDA regenerated');
  } catch (e) {
    showToast('Error: ' + e.message);
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
        return {
          columnName: col.columnName,
          propertyName: col.propertyName,
          expose: col.expose !== false
        };
      })
    };
  });
  return { tables: tables, relationships: currentEditableConfig.relationships || [] };
}

function setupGraphClickHandler(network) {
  network.on('click', function(params) {
    if (params.nodes.length === 0) return;
    var nodeId = params.nodes[0];
    var node = nodesDataSet ? nodesDataSet.get(nodeId) : null;
    if (!node) return;
    var group = node.group || 'property';
    if (group === 'class') {
      editClassNode(nodeId);
    } else {
      editPropertyNode(nodeId);
    }
  });
}

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

function findTableByClassName(className) {
  if (!currentEditableConfig) return null;
  for (var i = 0; i < currentEditableConfig.tables.length; i++) {
    if (currentEditableConfig.tables[i].className === className) {
      return currentEditableConfig.tables[i];
    }
  }
  return null;
}

function findPropertyByNodeId(nodeId) {
  if (!currentEditableConfig) return null;
  for (var i = 0; i < currentEditableConfig.tables.length; i++) {
    var table = currentEditableConfig.tables[i];
    for (var j = 0; j < table.columns.length; j++) {
      if (table.columns[j].propertyName === nodeId) {
        return { table: table, col: table.columns[j] };
      }
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
