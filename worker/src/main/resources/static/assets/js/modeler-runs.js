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

      function recordFlowRun({ workflowId, input }) {
        const workflow = activeWorkflow();
        const run = {
          id: workflowId,
          definitionId: state.model.id,
          definitionName: state.model.name,
          version: state.activeVersion || '1.0.0',
          input,
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

      function selectFlowRun(runId) {
        const run = state.flowRuns.find(r => r.id === runId);
        if (!run) return;
        state.activeRunId = runId;
        $('workflowId').value = run.id;
        if (run.input) $('runInput').value = pretty(run.input);
        renderRunsList();
        switchView('simulation');
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
        const rows = state.flowRuns.filter(run => {
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
          <div><strong>${escapeHtml(run.id)}</strong></div>
          <div><strong>${escapeHtml(run.definitionName || '-')}</strong><div class="help">${escapeHtml(run.definitionId || '')}</div></div>
          <div>v${escapeHtml(run.version || '-')}</div>
          <div><span class="pill ${escapeAttr((run.status || 'RUNNING').toLowerCase())}">${escapeHtml(run.status || 'RUNNING')}</span></div>
          <div>${escapeHtml(formatDate(run.startedAt))}</div>
          <div class="table-actions">
            <button class="secondary" onclick="selectFlowRun('${escapeAttr(run.id)}')">${escapeHtml(t('runs.select'))}</button>
            <button class="secondary" onclick="queryRunState('${escapeAttr(run.id)}')">${escapeHtml(t('runs.query'))}</button>
            <button class="secondary" onclick="openRunInModeler('${escapeAttr(run.definitionId)}', '${escapeAttr(run.version)}')">${escapeHtml(t('runs.openModeler'))}</button>
          </div>
        </div>`;
      }

      async function queryRunState(runId) {
        const id = runId || $('workflowId').value;
        if (!id) return message(t('message.queryWorkflowRequired'));
        try {
          const res = await fetch(`/api/flows/runs/${encodeURIComponent(id)}`);
          const data = await res.json();
          updateFlowRunStatus(id, data.status || 'RUNNING', data);
          $('workflowId').value = id;
          state.activeRunId = id;
          highlightRuntimeNode(data.currentNodeId || data.waitingHumanTaskNodeId || null);
          if (state.currentView !== 'simulation') switchView('simulation');
          showJsonValue('Workflow State', data);
        } catch (err) {
          message(t('message.queryFailed', { error: err.message }));
        }
      }

      async function openRunInModeler(definitionId, version) {
        if (!definitionId) return;
        const workflow = state.workflows.find(w => w.id === definitionId);
        if (!workflow) {
          message(t('runs.message.definitionMissing'));
          return;
        }
        await openWorkflow(definitionId, version || workflow.version, true);
        switchView('simulation');
      }
