      const FLOW_RUNS_KEY = 'flowfoundry-flow-runs';

      function loadFlowRuns() {
        try {
          state.flowRuns = JSON.parse(localStorage.getItem(FLOW_RUNS_KEY) || '[]');
        } catch (ignored) {
          state.flowRuns = [];
        }
      }

      function persistFlowRuns() {
        try {
          localStorage.setItem(FLOW_RUNS_KEY, JSON.stringify(state.flowRuns));
        } catch (ignored) {
          /* ignore storage errors */
        }
      }

      function recordFlowRun({ workflowId, input, runSource }) {
        const workflow = activeWorkflow();
        const run = {
          id: workflowId,
          definitionId: state.model.id,
          definitionName: state.model.name,
          version: state.activeVersion || '1.0.0',
          namespace: (typeof platformNamespace === 'function' ? platformNamespace() : '') || '',
          input,
          runSource: runSource || 'web-modeler',
          status: 'RUNNING',
          startedAt: new Date().toISOString(),
          lastQueriedAt: null
        };
        state.flowRuns = [run, ...state.flowRuns.filter(r => r.id !== workflowId)].slice(0, 50);
        state.activeRunId = workflowId;
        persistFlowRuns();
        renderRunsList();
      }

      function activeFlowRun() {
        return state.flowRuns.find(r => r.id === state.activeRunId) || state.flowRuns[0] || null;
      }

      async function selectFlowRun(runId) {
        const run = state.flowRuns.find(r => r.id === runId);
        if (!run) return;
        state.activeRunId = runId;
        setActiveWorkflowRunId(runId);
        if (run.input && $('runInput')) $('runInput').value = pretty(run.input);
        renderRunsList();
        switchView('modeler');
        await queryRunState(runId, { silent: true, skipJsonPanel: true });
        startRuntimePolling();
      }

      function updateFlowRunStatus(runId, status, snapshot = null) {
        const run = state.flowRuns.find(r => r.id === runId);
        if (!run) return;
        if (status) run.status = status;
        run.lastQueriedAt = new Date().toISOString();
        if (snapshot) run.lastSnapshot = snapshot;
        persistFlowRuns();
        renderRunsList();
      }

      function renderRunsList() {
        const table = $('runsTable');
        if (!table) return;
        const keyword = ($('runsSearch')?.value || '').trim().toLowerCase();
        const currentNamespace = (typeof platformNamespace === 'function' ? platformNamespace() : '') || '';
        const rows = state.flowRuns.filter(run => {
          // 严格按选中 namespace 过滤；无 namespace 标签的旧记录保留兼容。
          if (currentNamespace && run.namespace && run.namespace !== currentNamespace) return false;
          if (!keyword) return true;
          return run.id.toLowerCase().includes(keyword)
            || String(run.definitionName || '').toLowerCase().includes(keyword)
            || String(run.definitionId || '').toLowerCase().includes(keyword);
        });
        table.innerHTML = `
          <div class="table-row header runs-row">
            <div>${escapeHtml(t('runs.table.executionId'))}</div>
            <div>${escapeHtml(t('runs.table.definition'))}</div>
            <div>${escapeHtml(t('runs.table.version'))}</div>
            <div>${escapeHtml(t('runs.table.status'))}</div>
            <div>${escapeHtml(t('runs.table.started'))}</div>
            <div>${escapeHtml(t('runs.table.actions'))}</div>
          </div>
          ${rows.map(run => runRowHtml(run)).join('') || `<div class="table-row runs-row"><div>${escapeHtml(t('runs.empty'))}</div><div></div><div></div><div></div><div></div><div></div></div>`}
        `;
      }

      function runRowHtml(run) {
        const active = run.id === state.activeRunId ? ' active' : '';
        return `<div class="table-row runs-row${active}">
          <div><button type="button" class="runs-exec-link" onclick="selectFlowRun('${escapeAttr(run.id)}')">${escapeHtml(run.id)}</button></div>
          <div><strong>${escapeHtml(run.definitionName || '-')}</strong><div class="help">${escapeHtml(run.definitionId || '')}</div></div>
          <div>v${escapeHtml(run.version || '-')}</div>
          <div><span class="pill ${escapeAttr((run.status || 'RUNNING').toLowerCase())}">${escapeHtml(run.status || 'RUNNING')}</span><div class="help">${escapeHtml(run.runSource || 'web-modeler')}</div></div>
          <div>${escapeHtml(formatDate(run.startedAt))}</div>
          <div class="table-actions">
            <button class="secondary" onclick="queryRunState('${escapeAttr(run.id)}')">${escapeHtml(t('runs.query'))}</button>
          </div>
        </div>`;
      }

      async function queryRunState(runId, options = {}) {
        const { silent = false, skipJsonPanel = false } = options;
        const id = runId || activeWorkflowRunId();
        if (!id) return silent ? null : message(t('message.queryWorkflowRequired'));
        try {
          const res = await fetch(platformApiUrl(`/flows/runs/${encodeURIComponent(id)}`), { headers: platformApiHeaders() });
          const data = await res.json();
          if (!res.ok) {
            const errMsg = data.message || data.error || res.statusText;
            updateFlowRunStatus(id, 'NOT_FOUND');
            applyRunStatusSnapshot(id, { status: 'NOT_FOUND', workflowId: id, polledAt: new Date().toISOString() });
            if (!silent) message(t('message.queryFailed', { error: errMsg }), 'error');
            return null;
          }
          applyRunStatusSnapshot(id, { ...data, polledAt: new Date().toISOString() });
          if (!skipJsonPanel) showJsonValue('Workflow State', data);
          return data;
        } catch (err) {
          if (!silent) message(t('message.queryFailed', { error: err.message }), 'error');
          return null;
        }
      }

      function applyRunStatusSnapshot(runId, data) {
        updateFlowRunStatus(runId, data.status || 'RUNNING', data);
        setActiveWorkflowRunId(runId);
        state.runtimeSnapshot = data;
        if (data.runSource) {
          const run = state.flowRuns.find(r => r.id === runId);
          if (run) {
            run.runSource = data.runSource;
            persistFlowRuns();
          }
        }
        renderRuntimeStatus(data);
        highlightRuntimeNode(data.currentNodeId || data.waitingHumanTaskNodeId || null);
        if (state.currentView === 'modeler') renderCanvas();
        if (isRunStatusDialogOpen()) {
          $('runStatusWorkflowId').value = runId;
          refreshRunStatusSections();
        }
      }
