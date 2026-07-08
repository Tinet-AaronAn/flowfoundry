      function workflowApiBase() {
        return platformApiUrl('/workflows');
      }

      let workflowApiAvailable = null;

      async function workflowApi(path = '', options = {}) {
        const response = await fetch(`${workflowApiBase()}${path}`, {
          headers: { 'Content-Type': 'application/json', ...platformApiHeaders(), ...(options.headers || {}) },
          ...options
        });
        if (!response.ok) {
          const text = await response.text();
          let message = text;
          try {
            message = JSON.parse(text).message || text;
          } catch (ignored) {}
          throw new Error(message || response.statusText);
        }
        if (response.status === 204) return null;
        return response.json();
      }

      async function detectWorkflowApi() {
        if (workflowApiAvailable !== null) return workflowApiAvailable;
        try {
          await workflowApi('');
          workflowApiAvailable = true;
        } catch (ignored) {
          workflowApiAvailable = false;
        }
        return workflowApiAvailable;
      }

      async function allocatePlatformId(kind) {
        if (!(await detectWorkflowApi())) {
          return `${kind}_${Date.now().toString(36)}`;
        }
        const result = await workflowApi('/ids', {
          method: 'POST',
          body: JSON.stringify({ kind })
        });
        return result.id;
      }

      function platformIdKindForNodeKind(nodeKind) {
        if (nodeKind === 'subProcess') return 'subprocess';
        if (nodeKind === 'participant') return 'participant';
        if (nodeKind.includes('Gateway')) return 'gateway';
        if (nodeKind.includes('Event')) return 'event';
        return 'task';
      }

      function saveToMemory() {
        saveWorkflowDefinition();
      }

      async function loadFromMemory() {
        try {
          state.paletteCollapsed = localStorage.getItem('flowfoundry-palette-collapsed') === '1';
          state.navCollapsed = localStorage.getItem('flowfoundry-nav-collapsed') === '1';
          const propertiesStored = localStorage.getItem('flowfoundry-properties-collapsed');
          state.propertiesCollapsed = propertiesStored === null ? true : propertiesStored === '1';
        } catch (ignored) {
          state.paletteCollapsed = false;
          state.navCollapsed = false;
          state.propertiesCollapsed = true;
        }
        loadFlowRuns();
        await loadWorkflowStore();
        if (state.workflows.length === 0) {
          const saved = localStorage.getItem('temporal-flowfoundry-modeler');
          if (saved) {
            try {
              state.model = JSON.parse(saved);
              normalizeLoadedModel(state.model);
            } catch (ignored) {}
          }
          if (await detectWorkflowApi()) {
            try {
              const created = await workflowApi('', {
                method: 'POST',
                body: JSON.stringify({ name: state.model.name, model: state.model })
              });
              state.workflows = [created];
              state.activeWorkflowId = created.id;
              state.activeVersion = created.version;
            } catch (err) {
              state.activeWorkflowId = state.model.id;
              state.activeVersion = '1.0.0';
              state.workflows = [workflowRecordFromModel(state.model, 'draft', state.activeVersion)];
              await persistWorkflowStore();
            }
          } else {
            state.activeWorkflowId = state.model.id;
            state.activeVersion = '1.0.0';
            state.workflows = [workflowRecordFromModel(state.model, 'draft', state.activeVersion)];
            await persistWorkflowStore();
          }
        }
        const active = state.workflows.find(w => w.status === 'active') || state.workflows[0];
        if (active) await openWorkflow(active.id, active.version, false);
        syncModelHeader();
      }

      function normalizeLoadedModel(model) {
        if (!model?.nodes) return;
        model.nodes.forEach(n => {
          if (n.kind === 'userTask') n.kind = 'humanTask';
          normalizeHumanTaskMode(n);
          migrateNodeTaskHeaders(n);
        });
      }

      function normalizeHumanTaskMode(node) {
        if (!node?.config?.flowFoundryHumanTask) return;
        if (String(node.config.flowFoundryHumanTask.mode || '').toLowerCase() === 'offline') {
          node.config.flowFoundryHumanTask.mode = 'managed';
        }
      }

      async function loadWorkflowStore() {
        if (await detectWorkflowApi()) {
          try {
            state.workflows = await workflowApi('');
            return;
          } catch (err) {
            message(t('workflow.message.apiFallback', { error: err.message }), 'warning');
          }
        }
        try {
          state.workflows = JSON.parse(localStorage.getItem('temporal-flow-workflows') || '[]');
        } catch (ignored) {
          state.workflows = [];
        }
      }

      async function persistWorkflowStore() {
        if (await detectWorkflowApi()) return;
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

      async function saveWorkflowDefinition() {
        const now = new Date().toISOString();
        let workflow = activeWorkflow();
        if (!workflow) {
          workflow = workflowRecordFromModel(state.model, 'draft', state.activeVersion || '1.0.0');
          state.workflows.push(workflow);
          state.activeWorkflowId = workflow.id;
        }
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

        if (await detectWorkflowApi()) {
          try {
            const saved = await workflowApi(`/${encodeURIComponent(workflow.id)}/versions/${encodeURIComponent(workflow.version)}`, {
              method: 'PUT',
              body: JSON.stringify({ name: workflow.name, model: version.model, status: workflow.status })
            });
            replaceWorkflowRecord(saved);
            message(t('workflow.message.saved', { name: saved.name, version: saved.version }));
            if (typeof notifyWorkflowSaved === 'function') notifyWorkflowSaved(saved);
            renderWorkflowList();
            return;
          } catch (err) {
            message(err.message, 'error');
          }
        }

        await persistWorkflowStore();
        renderWorkflowList();
        message(t('workflow.message.saved', { name: workflow.name, version: version.version }));
        if (typeof notifyWorkflowSaved === 'function') {
          notifyWorkflowSaved({ id: workflow.id, version: version.version, name: workflow.name });
        }
      }

      function replaceWorkflowRecord(record) {
        const index = state.workflows.findIndex(w => w.id === record.id);
        if (index >= 0) state.workflows[index] = record;
        else state.workflows.push(record);
        if (state.activeWorkflowId === record.id) {
          state.activeVersion = record.version;
        }
      }

      function syncModelHeader() {
        const flowIdEl = $('flowId');
        if (flowIdEl) flowIdEl.textContent = state.model.id;
        $('flowName').value = state.model.name;
        const versionEl = $('flowVersionId');
        if (versionEl) versionEl.textContent = state.activeVersion || activeWorkflow()?.version || '1.0.0';
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
          <div class="table-row header"><div>${escapeHtml(t('workflow.table.nameId'))}</div><div>${escapeHtml(t('workflow.table.namespace'))}</div><div>${escapeHtml(t('workflow.table.version'))}</div><div>${escapeHtml(t('workflow.table.status'))}</div><div>${escapeHtml(t('workflow.table.updated'))}</div><div>${escapeHtml(t('workflow.table.actions'))}</div></div>
          ${rows.map(w => workflowRowHtml(w)).join('') || `<div class="table-row"><div>${escapeHtml(t('workflow.empty'))}</div><div></div><div></div><div></div><div></div><div></div></div>`}
        `;
      }

      function workflowRowHtml(w) {
        const versions = (w.versions || []).map(v => `<option value="${escapeAttr(v.version)}" ${v.version === w.version ? 'selected' : ''}>v${escapeHtml(v.version)}</option>`).join('');
        return `<div class="table-row">
          <div>
            <button type="button" class="workflow-name-cell" onclick="openWorkflow('${escapeAttr(w.id)}')">
              <strong>${escapeHtml(w.name)}</strong>
              <span class="help">${escapeHtml(w.id)}</span>
            </button>
          </div>
          <div><span class="pill">${escapeHtml(w.namespace || platformNamespace() || '-')}</span></div>
          <div><select onchange="openWorkflow('${escapeAttr(w.id)}', this.value, false)">${versions}</select></div>
          <div><span class="pill ${escapeAttr(w.status)}">${escapeHtml(w.status)}</span></div>
          <div>${escapeHtml(formatDate(w.updatedAt))}</div>
          <div class="table-actions">
            <button class="secondary" onclick="createWorkflowVersion('${escapeAttr(w.id)}')">${escapeHtml(t('workflow.newVersion'))}</button>
            <button class="secondary" onclick="setWorkflowStatus('${escapeAttr(w.id)}', '${w.status === 'active' ? 'retired' : 'active'}')">${escapeHtml(w.status === 'active' ? t('workflow.deactivate') : t('workflow.activate'))}</button>
            <button class="danger" onclick="deleteWorkflow('${escapeAttr(w.id)}')">${escapeHtml(t('workflow.delete'))}</button>
          </div>
        </div>`;
      }

      async function createWorkflow() {
        const name = await showAppDialog({
          title: t('workflow.prompt.name'),
          value: t('workflow.prompt.defaultName')
        });
        if (!name) return;
        if (await detectWorkflowApi()) {
          try {
            const created = await workflowApi('', {
              method: 'POST',
              body: JSON.stringify({ name })
            });
            state.workflows.push(created);
            await openWorkflow(created.id, created.version);
            return;
          } catch (err) {
            message(err.message, 'error');
            return;
          }
        }
        const id = `Definitions_${Date.now().toString(36)}`;
        const model = structuredClone(state.model);
        model.id = id;
        model.name = name;
        model.process = { ...model.process, id: id.replace(/^Definitions_/, 'Process_'), name };
        const workflow = workflowRecordFromModel(model, 'draft', '1.0.0');
        state.workflows.push(workflow);
        await persistWorkflowStore();
        await openWorkflow(id, '1.0.0');
      }

      async function openWorkflow(id, version = null, navigate = true) {
        let workflow = state.workflows.find(w => w.id === id);
        if (!workflow && (await detectWorkflowApi())) {
          try {
            workflow = await workflowApi(`/${encodeURIComponent(id)}`);
            replaceWorkflowRecord(workflow);
          } catch (err) {
            message(err.message, 'error');
            return;
          }
        }
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
        normalizeLoadedModel(state.model);
        syncParticipantAssignments();
        state.history = [];
        state.future = [];
        state.selected = { type: 'process', id: null };
        syncModelHeader();
        if (navigate) switchView('modeler');
        renderAll();
        if (navigate || state.currentView === 'modeler') scheduleFitView();
        message(t('workflow.message.opened', { name: workflow.name, version: selectedVersion.version }));
      }

      async function createWorkflowVersion(id) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        const suggested = nextVersion(workflow.version || '1.0.0');
        const version = await showAppDialog({
          title: t('workflow.prompt.newVersion'),
          value: suggested
        });
        if (!version || workflow.versions.some(v => v.version === version)) return;
        if (await detectWorkflowApi()) {
          try {
            const created = await workflowApi(`/${encodeURIComponent(id)}/versions`, {
              method: 'POST',
              body: JSON.stringify({ sourceVersion: workflow.version, version })
            });
            replaceWorkflowRecord(created);
            await openWorkflow(id, version);
            return;
          } catch (err) {
            message(err.message, 'error');
            return;
          }
        }
        const source = workflow.versions.find(v => v.version === workflow.version) || workflow.versions[workflow.versions.length - 1];
        workflow.version = version;
        workflow.status = 'draft';
        workflow.updatedAt = new Date().toISOString();
        workflow.versions.push({ version, status: 'draft', createdAt: workflow.updatedAt, model: structuredClone(source.model) });
        await persistWorkflowStore();
        await openWorkflow(id, version);
      }

      async function renameWorkflow(id) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        const name = await showAppDialog({
          title: t('workflow.prompt.rename'),
          value: workflow.name
        });
        if (!name) return;
        if (await detectWorkflowApi()) {
          try {
            const updated = await workflowApi(`/${encodeURIComponent(id)}`, {
              method: 'PATCH',
              body: JSON.stringify({ name })
            });
            replaceWorkflowRecord(updated);
            if (state.activeWorkflowId === id) {
              state.model.name = name;
              state.model.process = { ...state.model.process, name };
              syncModelHeader();
            }
            renderWorkflowList();
            return;
          } catch (err) {
            message(err.message, 'error');
            return;
          }
        }
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
        await persistWorkflowStore();
        renderWorkflowList();
      }

      async function syncTimerStartSchedule(workflowId, model, version) {
        if (!(await detectWorkflowApi())) return;
        const startNode = model?.nodes?.find(n => n.kind === 'startEvent');
        if (!startNode || startEventSubtype(startNode.config) !== 'timer') return;
        try {
          const dsl = buildDslForModel(model, version || '1.0.0', new Set([model.id]));
          await workflowApi(`/${encodeURIComponent(workflowId)}/timer-schedule/sync`, {
            method: 'POST',
            body: JSON.stringify(dsl)
          });
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function pauseTimerStartSchedule(workflowId) {
        if (!(await detectWorkflowApi())) return;
        try {
          await workflowApi(`/${encodeURIComponent(workflowId)}/timer-schedule/pause`, { method: 'POST' });
        } catch (err) {
          // Backend also pauses on workflow retire.
        }
      }

      async function setWorkflowStatus(id, status) {
        const workflow = state.workflows.find(w => w.id === id);
        if (!workflow) return;
        const versionRecord =
          workflow.versions?.find(v => v.version === workflow.version) ||
          workflow.versions?.[workflow.versions.length - 1];
        if (await detectWorkflowApi()) {
          try {
            const updated = await workflowApi(`/${encodeURIComponent(id)}`, {
              method: 'PATCH',
              body: JSON.stringify({ status, activeVersion: workflow.version })
            });
            replaceWorkflowRecord(updated);
            if (status === 'active') {
              await syncTimerStartSchedule(id, versionRecord?.model, workflow.version);
            } else if (status === 'retired') {
              await pauseTimerStartSchedule(id);
            }
            renderWorkflowList();
            return;
          } catch (err) {
            message(err.message, 'error');
            return;
          }
        }
        workflow.status = status;
        workflow.updatedAt = new Date().toISOString();
        const version = workflow.versions.find(v => v.version === workflow.version);
        if (version) version.status = status;
        await persistWorkflowStore();
        renderWorkflowList();
      }

      async function deleteWorkflow(id) {
        const workflow = state.workflows.find(w => w.id === id);
        const confirmed = await showAppDialog({
          title: t('workflow.delete'),
          message: t('workflow.confirm.delete', { name: workflow.name }),
          input: 'none',
          confirmLabel: t('dialog.delete'),
          danger: true
        });
        if (!workflow || !confirmed) return;
        if (await detectWorkflowApi()) {
          try {
            await workflowApi(`/${encodeURIComponent(id)}`, { method: 'DELETE' });
            state.workflows = state.workflows.filter(w => w.id !== id);
            if (state.activeWorkflowId === id && state.workflows[0]) await openWorkflow(state.workflows[0].id, state.workflows[0].version, false);
            renderWorkflowList();
            return;
          } catch (err) {
            message(err.message, 'error');
            return;
          }
        }
        state.workflows = state.workflows.filter(w => w.id !== id);
        await persistWorkflowStore();
        if (state.activeWorkflowId === id && state.workflows[0]) await openWorkflow(state.workflows[0].id, state.workflows[0].version, false);
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
