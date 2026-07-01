      function saveToMemory() {
        saveWorkflowDefinition();
      }

      function loadFromMemory() {
        loadWorkflowStore();
        if (state.workflows.length === 0) {
          const saved = localStorage.getItem('temporal-flowfoundry-modeler');
          if (saved) {
            try { state.model = JSON.parse(saved); } catch (ignored) {}
          }
          state.activeWorkflowId = state.model.id;
          state.activeVersion = '1.0.0';
          state.workflows = [workflowRecordFromModel(state.model, 'draft', state.activeVersion)];
          persistWorkflowStore();
        }
        const active = state.workflows.find(w => w.status === 'active') || state.workflows[0];
        if (active) openWorkflow(active.id, active.version, false);
        syncModelHeader();
      }

      function loadWorkflowStore() {
        try {
          state.workflows = JSON.parse(localStorage.getItem('temporal-flow-workflows') || '[]');
        } catch (ignored) {
          state.workflows = [];
        }
      }

      function persistWorkflowStore() {
        localStorage.setItem('temporal-flow-workflows', JSON.stringify(state.workflows));
      }

      function workflowRecordFromModel(model, status = 'draft', version = '1.0.0') {
        const now = new Date().toISOString();
        return {
          id: model.id,
          name: model.name,
          version,
          status,
          updatedAt: now,
          versions: [{
            version,
            status,
            createdAt: now,
            model: structuredClone(model)
          }]
        };
      }

      function activeWorkflow() {
        return state.workflows.find(w => w.id === state.activeWorkflowId);
      }

      function activeWorkflowVersion(workflow = activeWorkflow()) {
        return workflow?.versions?.find(v => v.version === state.activeVersion);
      }

      function saveWorkflowDefinition() {
        const now = new Date().toISOString();
        let workflow = activeWorkflow();
        if (!workflow) {
          workflow = workflowRecordFromModel(state.model, 'draft', state.activeVersion || '1.0.0');
          state.workflows.push(workflow);
          state.activeWorkflowId = workflow.id;
        }
        workflow.id = state.model.id;
        workflow.name = state.model.name;
        workflow.version = state.activeVersion || workflow.version || '1.0.0';
        workflow.updatedAt = now;
        let version = activeWorkflowVersion(workflow);
        if (!version) {
          version = { version: workflow.version, status: workflow.status, createdAt: now, model: structuredClone(state.model) };
          workflow.versions.push(version);
        }
        version.model = structuredClone(state.model);
        version.status = workflow.status;
        persistWorkflowStore();
        renderWorkflowList();
        message(`已保存 Workflow：${workflow.name} v${version.version}`);
      }

      function syncModelHeader() {
        $('flowId').value = state.model.id;
        $('flowName').value = state.model.name;
      }

      function renderWorkflowList() {
        const table = $('workflowTable');
        if (!table) return;
        const keyword = ($('workflowSearch')?.value || '').trim().toLowerCase();
        const status = $('workflowStatusFilter')?.value || '';
        const rows = state.workflows
          .filter(w => (!keyword || w.name.toLowerCase().includes(keyword) || w.id.toLowerCase().includes(keyword)) && (!status || w.status === status))
          .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)));
        table.innerHTML = `
          <div class="table-row header"><div>Name / ID</div><div>Version</div><div>Status</div><div>Updated</div><div>Actions</div></div>
          ${rows.map(w => workflowRowHtml(w)).join('') || '<div class="table-row"><div>暂无 Workflow</div><div></div><div></div><div></div><div></div></div>'}
        `;
      }

      function workflowRowHtml(w) {
        const versions = (w.versions || []).map(v => `<option value="${escapeAttr(v.version)}" ${v.version === w.version ? 'selected' : ''}>v${escapeHtml(v.version)}</option>`).join('');
        return `<div class="table-row">
          <div><strong>${escapeHtml(w.name)}</strong><div class="help">${escapeHtml(w.id)}</div></div>
          <div><select onchange="openWorkflow('${escapeAttr(w.id)}', this.value, false)">${versions}</select></div>
          <div><span class="pill ${escapeAttr(w.status)}">${escapeHtml(w.status)}</span></div>
          <div>${escapeHtml(formatDate(w.updatedAt))}</div>
          <div class="row">
            <button class="secondary" onclick="openWorkflow('${escapeAttr(w.id)}')">打开</button>
            <button class="secondary" onclick="renameWorkflow('${escapeAttr(w.id)}')">改名</button>
            <button class="secondary" onclick="createWorkflowVersion('${escapeAttr(w.id)}')">新版本</button>
            <button class="secondary" onclick="setWorkflowStatus('${escapeAttr(w.id)}', '${w.status === 'active' ? 'retired' : 'active'}')">${w.status === 'active' ? '停用' : '启用'}</button>
            <button class="danger" onclick="deleteWorkflow('${escapeAttr(w.id)}')">删除</button>
          </div>
        </div>`;
      }

      function createWorkflow() {
        const name = prompt('Workflow 名称', '新建业务流程');
        if (!name) return;
        const id = `Definitions_${Date.now().toString(36)}`;
        const model = structuredClone(state.model);
        model.id = id;
        model.name = name;
        model.process = { ...model.process, id: id.replace(/^Definitions_/, 'Process_'), name };
        const workflow = workflowRecordFromModel(model, 'draft', '1.0.0');
        state.workflows.push(workflow);
        persistWorkflowStore();
        openWorkflow(id, '1.0.0');
      }

      function openWorkflow(id, version = null, navigate = true) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        const selectedVersion =
          workflow.versions.find(v => v.version === version) ||
          workflow.versions.find(v => v.version === workflow.version) ||
          workflow.versions[workflow.versions.length - 1];
        if (!selectedVersion) return;
        state.activeWorkflowId = workflow.id;
        state.activeVersion = selectedVersion.version;
        workflow.version = selectedVersion.version;
        state.model = structuredClone(selectedVersion.model);
        syncParticipantAssignments();
        state.history = [];
        state.future = [];
        state.selected = { type: 'process', id: null };
        syncModelHeader();
        if (navigate) switchView('modeler');
        renderAll();
        message(`已打开 Workflow：${workflow.name} v${selectedVersion.version}`);
      }

      function createWorkflowVersion(id) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        const version = prompt('新版本号', nextVersion(workflow.version || '1.0.0'));
        if (!version || workflow.versions.some(v => v.version === version)) return;
        const source = workflow.versions.find(v => v.version === workflow.version) || workflow.versions[workflow.versions.length - 1];
        workflow.version = version;
        workflow.status = 'draft';
        workflow.updatedAt = new Date().toISOString();
        workflow.versions.push({ version, status: 'draft', createdAt: workflow.updatedAt, model: structuredClone(source.model) });
        persistWorkflowStore();
        openWorkflow(id, version);
      }

      function renameWorkflow(id) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        const name = prompt('Workflow 新名称', workflow.name);
        if (!name) return;
        workflow.name = name;
        workflow.updatedAt = new Date().toISOString();
        workflow.versions.forEach(v => {
          v.model.name = name;
          v.model.process = { ...v.model.process, name };
        });
        if (state.activeWorkflowId === id) {
          state.model.name = name;
          state.model.process = { ...state.model.process, name };
          syncModelHeader();
        }
        persistWorkflowStore();
        renderWorkflowList();
      }

      function setWorkflowStatus(id, status) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        workflow.status = status;
        workflow.updatedAt = new Date().toISOString();
        const version = workflow.versions.find(v => v.version === workflow.version);
        if (version) version.status = status;
        persistWorkflowStore();
        renderWorkflowList();
      }

      function deleteWorkflow(id) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow || !confirm(`删除 Workflow ${workflow.name}？`)) return;
        state.workflows = state.workflows.filter(w => w.id !== id);
        persistWorkflowStore();
        if (state.activeWorkflowId === id && state.workflows[0]) openWorkflow(state.workflows[0].id, state.workflows[0].version, false);
        renderWorkflowList();
      }

      function nextVersion(version) {
        const parts = String(version).split('.').map(n => Number(n));
        if (parts.length < 3 || parts.some(Number.isNaN)) return `${version}.1`;
        parts[2] += 1;
        return parts.join('.');
      }

      function formatDate(value) {
        return value ? new Date(value).toLocaleString() : '-';
      }
