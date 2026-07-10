      const FLOW_RUNS_KEY = 'flowfoundry-flow-runs';

      function extractNamespaceFromBusinessKey(businessKey) {
        if (!businessKey || typeof businessKey !== 'string') return '';
        const idx = businessKey.indexOf(':');
        return idx > 0 ? businessKey.slice(0, idx) : '';
      }

      function resolveRunNamespace(run) {
        if (!run) return '';
        if (run.namespace) return String(run.namespace);
        return extractNamespaceFromBusinessKey(run.lastSnapshot?.businessKey);
      }

      function runMatchesNamespace(run, currentNamespace) {
        if (!currentNamespace) return true;
        const runNamespace = resolveRunNamespace(run);
        return runNamespace === currentNamespace;
      }

      function loadFlowRuns() {
        try {
          state.flowRuns = JSON.parse(localStorage.getItem(FLOW_RUNS_KEY) || '[]');
        } catch (ignored) {
          state.flowRuns = [];
        }
        let changed = false;
        state.flowRuns.forEach(run => {
          if (!run.namespace) {
            const inferred = resolveRunNamespace(run);
            if (inferred) {
              run.namespace = inferred;
              changed = true;
            }
          }
        });
        if (changed) persistFlowRuns();
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
        await openRunTimeline(runId);
      }

      async function openFlowRunInModeler(runId) {
        const run = state.flowRuns.find(r => r.id === runId);
        if (!run) return;
        state.activeRunId = runId;
        setActiveWorkflowRunId(runId);
        if (run.input && $('runInput')) $('runInput').value = pretty(run.input);
        renderRunsList();
        closeRunTimeline();
        switchView('modeler');
        await queryRunState(runId, { silent: true, skipJsonPanel: true });
        startRuntimePolling();
      }

      function isRunTimelineOpen() {
        return $('runTimelineBackdrop')?.classList.contains('open');
      }

      const runTimelineUi = {
        view: 'compact',
        sort: 'asc',
        snapshot: null,
        runId: null
      };

      function formatDurationMs(ms) {
        if (ms == null || !Number.isFinite(ms) || ms < 0) return '—';
        if (ms < 1000) return `${Math.round(ms)} ms`;
        const sec = ms / 1000;
        if (sec < 60) return `${sec < 10 ? sec.toFixed(1) : Math.round(sec)} s`;
        const minutes = Math.floor(sec / 60);
        const remSec = Math.round(sec % 60);
        if (minutes < 60) return remSec ? `${minutes}m ${remSec}s` : `${minutes}m`;
        const hours = Math.floor(minutes / 60);
        const remMin = minutes % 60;
        return remMin ? `${hours}h ${remMin}m` : `${hours}h`;
      }

      function parseInstantMs(value) {
        if (value == null || value === '') return null;
        if (typeof value === 'number' && Number.isFinite(value)) return value;
        const ms = Date.parse(String(value));
        return Number.isFinite(ms) ? ms : null;
      }

      function parseDetailEpochMs(detailJson, key) {
        if (!detailJson || !key) return null;
        try {
          const detail = typeof detailJson === 'string' ? JSON.parse(detailJson) : detailJson;
          const raw = detail?.[key];
          if (typeof raw === 'number' && Number.isFinite(raw)) return raw;
          if (typeof raw === 'string' && raw.trim() !== '') {
            const parsed = Number(raw);
            return Number.isFinite(parsed) ? parsed : null;
          }
        } catch {
          return null;
        }
        return null;
      }

      function formatClockTime(value, withMs = false) {
        const ms = parseInstantMs(value);
        if (ms == null) return '—';
        try {
          const d = new Date(ms);
          const base = d.toLocaleTimeString(undefined, {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
          });
          if (!withMs) return base;
          return `${base}.${String(d.getMilliseconds()).padStart(3, '0').slice(0, 2)}`;
        } catch {
          return formatDate(value);
        }
      }

      function formatDateTimeShort(value) {
        const ms = parseInstantMs(value);
        if (ms == null) return '—';
        try {
          return new Date(ms).toLocaleString(undefined, {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
          });
        } catch {
          return formatDate(value);
        }
      }

      function normalizeTimelineStatus(status) {
        const raw = String(status || '').toUpperCase();
        if (!raw) return 'unknown';
        if (raw.includes('FAIL') || raw.includes('ERROR') || raw.includes('TERMINAT')) return 'failed';
        if (raw.includes('WAIT') || raw.includes('TIMER')) return 'waiting';
        if (raw.includes('ROUTE')) return 'routed';
        if (raw.includes('COMPLETE') || raw.includes('SUCCESS') || raw === 'FIRED') return 'completed';
        if (raw.includes('RUN') || raw.includes('START') || raw.includes('ENTER')) return 'running';
        return raw.toLowerCase();
      }

      function timelineStatusLabel(statusKey) {
        const key = `runs.timeline.status.${statusKey}`;
        const label = t(key);
        return label === key ? String(statusKey || '—').toUpperCase() : label;
      }

      function eventTypeLabel(eventType) {
        const key = `runs.timeline.event.${eventType}`;
        const label = t(key);
        return label === key ? String(eventType || '').replace(/_/g, ' ') : label;
      }

      function modelNodeById(nodeId) {
        if (!nodeId || !state.model?.nodes) return null;
        return state.model.nodes.find(n => n.id === nodeId) || null;
      }

      function parseEventDetail(ev) {
        const raw = ev?.detailJson ?? ev?.detail;
        if (raw == null) return null;
        if (typeof raw === 'object') return raw;
        if (typeof raw !== 'string' || !raw.trim()) return null;
        try {
          return JSON.parse(raw);
        } catch {
          return null;
        }
      }

      function resolveTimelineNodeName(source = {}) {
        const nodeId = source.nodeId || null;
        const modelNode = modelNodeById(nodeId);
        const detail = parseEventDetail(source);
        const kind = String(source.nodeKind || modelNode?.kind || '').toUpperCase();
        const canvasName = source.nodeName
          || modelNode?.name
          || null;
        const activityType = source.activityType
          || modelNode?.activityType
          || null;
        const childName = detail?.childWorkflowName
          || modelNode?.config?.childWorkflowName
          || modelNode?.config?.flowFoundryChildWorkflow?.childWorkflowName
          || (kind.includes('CHILD') || modelNode?.kind === 'workflow'
            ? (activityType && activityType !== 'child-workflow' ? activityType : null)
            : null)
          || modelNode?.config?.childWorkflowId
          || null;

        if (kind.includes('CHILD') || modelNode?.kind === 'workflow') {
          return childName || canvasName || nodeId || '—';
        }
        if (kind.includes('GATEWAY') || (modelNode?.kind || '').toLowerCase().includes('gateway')) {
          return canvasName || nodeId || '—';
        }
        if (activityType && (!canvasName || canvasName === nodeId || /^service\s*task$/i.test(canvasName))) {
          return activityType;
        }
        return canvasName || activityType || nodeId || '—';
      }

      function resolveTimelineSubtitle(source = {}, displayName) {
        const nodeId = source.nodeId || '';
        const modelNode = modelNodeById(nodeId);
        const detail = parseEventDetail(source);
        const kind = String(source.nodeKind || modelNode?.kind || '');
        const activityType = source.activityType || modelNode?.activityType || '';
        const childName = detail?.childWorkflowName
          || modelNode?.config?.childWorkflowName
          || modelNode?.config?.flowFoundryChildWorkflow?.childWorkflowName
          || '';
        const parts = [];
        if (nodeId && nodeId !== displayName) parts.push(nodeId);
        if (childName && childName !== displayName && childName !== activityType) parts.push(childName);
        if (activityType && activityType !== displayName && activityType !== 'child-workflow') {
          parts.push(activityType);
        } else if (kind && kind !== displayName && !parts.includes(kind)) {
          parts.push(kind);
        }
        return parts.filter(Boolean).join(' · ');
      }

      function createTimelineStep(key, seed = {}) {
        const displayName = resolveTimelineNodeName(seed);
        return {
          key,
          nodeId: seed.nodeId || null,
          nodeName: displayName,
          nodeKind: seed.nodeKind || '',
          activityType: seed.activityType || '',
          status: seed.status || '',
          startedAt: seed.startedAt || null,
          completedAt: seed.completedAt || null,
          sequenceNo: seed.sequenceNo ?? Number.MAX_SAFE_INTEGER,
          events: [],
          fromNodeRun: false
        };
      }

      function timelineStepKeyForEvent(ev) {
        if (ev.nodeId) return ev.nodeId;
        const type = String(ev.eventType || '');
        if (type.startsWith('WORKFLOW_')) return '__workflow__';
        return `__evt_${ev.sequenceNo ?? ev.id ?? type}`;
      }

      function applyEventToTimelineStep(step, ev) {
        const type = String(ev.eventType || '');
        const isWorkflowEvent = !ev.nodeId && type.startsWith('WORKFLOW_');
        const resolvedName = resolveTimelineNodeName({
          ...ev,
          nodeName: ev.nodeName || step.nodeName,
          nodeKind: ev.nodeKind || step.nodeKind,
          activityType: ev.activityType || step.activityType
        });
        if (resolvedName && resolvedName !== '—') {
          step.nodeName = resolvedName;
        } else if (!step.nodeName) {
          step.nodeName = isWorkflowEvent ? t('runs.timeline.workflow') : type;
        }
        step.nodeKind = step.nodeKind || ev.nodeKind || (isWorkflowEvent ? 'workflow' : '');
        step.activityType = step.activityType || ev.activityType || '';
        if (ev.sequenceNo != null) step.sequenceNo = Math.min(step.sequenceNo, ev.sequenceNo);

        const occurredMs = parseInstantMs(ev.occurredAt);
        const startedMs = parseInstantMs(step.startedAt);
        const detailStartedMs = parseDetailEpochMs(ev.detailJson, 'startedAtEpochMs');
        if (detailStartedMs != null && (startedMs == null || detailStartedMs < startedMs)) {
          step.startedAt = new Date(detailStartedMs).toISOString();
        } else if (occurredMs != null && (startedMs == null || occurredMs < startedMs)) {
          step.startedAt = ev.occurredAt;
        }

        const detailCompletedMs = parseDetailEpochMs(ev.detailJson, 'completedAtEpochMs');
        const isTerminalEvent = type.includes('COMPLETED')
          || type.includes('FAILED')
          || type.includes('FIRED')
          || type === 'GATEWAY_ROUTED'
          || type === 'NODE_FINISHED';
        if (isTerminalEvent) {
          const completedMs = parseInstantMs(step.completedAt);
          if (detailCompletedMs != null && (completedMs == null || detailCompletedMs > completedMs)) {
            step.completedAt = new Date(detailCompletedMs).toISOString();
          } else if (occurredMs != null && (completedMs == null || occurredMs > completedMs)) {
            step.completedAt = ev.occurredAt;
          }
          if (!step.fromNodeRun || !step.status || normalizeTimelineStatus(step.status) === 'running') {
            step.status = ev.status || (type.includes('FAILED') ? 'FAILED' : 'COMPLETED');
          }
        } else if (!step.status) {
          step.status = ev.status || 'RUNNING';
        }
        step.events.push(ev);
      }

      function buildTimelineSteps(snapshot) {
        const nodeRuns = Array.isArray(snapshot?.nodeRuns) ? snapshot.nodeRuns : [];
        const events = Array.isArray(snapshot?.executionLogs) ? snapshot.executionLogs : [];
        const byKey = new Map();

        nodeRuns.forEach((nr, index) => {
          const key = nr.nodeId || `__nr_${index}`;
          const step = byKey.get(key) || createTimelineStep(key, {
            nodeId: nr.nodeId,
            nodeName: nr.nodeName,
            nodeKind: nr.nodeKind,
            activityType: nr.activityType,
            status: nr.status,
            startedAt: nr.startedAt,
            completedAt: nr.completedAt,
            sequenceNo: Number.MAX_SAFE_INTEGER
          });
          step.fromNodeRun = true;
          step.nodeId = nr.nodeId || step.nodeId;
          step.nodeName = resolveTimelineNodeName({
            nodeId: nr.nodeId || step.nodeId,
            nodeName: nr.nodeName || step.nodeName,
            nodeKind: nr.nodeKind || step.nodeKind,
            activityType: nr.activityType || step.activityType
          });
          step.nodeKind = nr.nodeKind || step.nodeKind;
          step.activityType = nr.activityType || step.activityType;
          step.status = nr.status || step.status;
          step.startedAt = nr.startedAt || step.startedAt;
          step.completedAt = nr.completedAt || step.completedAt;
          byKey.set(key, step);
        });

        events.forEach(ev => {
          const key = timelineStepKeyForEvent(ev);
          const step = byKey.get(key) || createTimelineStep(key, {
            nodeId: ev.nodeId,
            nodeName: ev.nodeName,
            nodeKind: ev.nodeKind,
            activityType: ev.activityType,
            status: ev.status,
            startedAt: ev.occurredAt,
            sequenceNo: ev.sequenceNo ?? Number.MAX_SAFE_INTEGER
          });
          applyEventToTimelineStep(step, ev);
          byKey.set(key, step);
        });

        const steps = Array.from(byKey.values());
        steps.forEach(step => {
          step.events.sort(compareTimelineEvents);
          const startMs = parseInstantMs(step.startedAt);
          const endMs = parseInstantMs(step.completedAt);
          step.durationMs = startMs != null && endMs != null && endMs >= startMs ? endMs - startMs : null;
          step.statusKey = normalizeTimelineStatus(step.status);
          if (!step.nodeName || step.nodeName === step.nodeId) {
            step.nodeName = resolveTimelineNodeName(step);
          }
        });

        steps.sort(compareTimelineSteps);
        return steps;
      }

      function compareTimelineEvents(a, b) {
        const ta = parseInstantMs(a?.occurredAt) ?? Number.MAX_SAFE_INTEGER;
        const tb = parseInstantMs(b?.occurredAt) ?? Number.MAX_SAFE_INTEGER;
        if (ta !== tb) return ta - tb;
        return (a?.sequenceNo ?? 0) - (b?.sequenceNo ?? 0);
      }

      function compareTimelineSteps(a, b) {
        const ta = parseInstantMs(a?.startedAt) ?? Number.MAX_SAFE_INTEGER;
        const tb = parseInstantMs(b?.startedAt) ?? Number.MAX_SAFE_INTEGER;
        if (ta !== tb) return ta - tb;
        const sa = a?.sequenceNo ?? Number.MAX_SAFE_INTEGER;
        const sb = b?.sequenceNo ?? Number.MAX_SAFE_INTEGER;
        if (sa !== sb) return sa - sb;
        return String(a?.nodeId || a?.key || '').localeCompare(String(b?.nodeId || b?.key || ''));
      }

      function sortedTimelineSteps(steps) {
        const list = [...(steps || [])].sort(compareTimelineSteps);
        if (runTimelineUi.sort === 'desc') list.reverse();
        return list;
      }

      function sortedExecutionLogs(snapshot) {
        const events = Array.isArray(snapshot?.executionLogs) ? [...snapshot.executionLogs] : [];
        events.sort(compareTimelineEvents);
        if (runTimelineUi.sort === 'desc') events.reverse();
        return events;
      }

      function resolveRunInput(snapshot, run) {
        if (run?.input != null) return run.input;
        if (snapshot?.variables && typeof snapshot.variables === 'object') {
          if (snapshot.variables.input != null) return snapshot.variables.input;
        }
        return snapshot?.input ?? null;
      }

      function resolveRunResult(snapshot) {
        if (!snapshot) return null;
        if (snapshot.lastResult != null) return snapshot.lastResult;
        return {
          status: snapshot.status,
          currentNodeId: snapshot.currentNodeId,
          currentNodeName: snapshot.currentNodeName,
          failureMessage: snapshot.failureMessage,
          failureType: snapshot.failureType
        };
      }

      function summaryItem(label, value) {
        return `<div class="run-timeline-summary-item">
          <span class="run-timeline-summary-label">${escapeHtml(label)}</span>
          <strong>${escapeHtml(value)}</strong>
        </div>`;
      }

      function renderCompactGantt(steps, snapshot) {
        if (!steps.length) {
          return `<div class="run-timeline-empty help">${escapeHtml(t('runs.timeline.empty'))}</div>`;
        }
        const runTerminal = typeof isTerminalRunStatus === 'function'
          ? isTerminalRunStatus(snapshot?.status)
          : ['COMPLETED', 'FAILED', 'CANCELED', 'TERMINATED', 'TIMED_OUT', 'NOT_FOUND']
            .includes(String(snapshot?.status || '').toUpperCase());
        const starts = steps.map(s => parseInstantMs(s.startedAt)).filter(v => v != null);
        const ends = steps.map(s => {
          const end = parseInstantMs(s.completedAt);
          if (end != null) return end;
          const start = parseInstantMs(s.startedAt);
          return start != null ? start + Math.max(s.durationMs || 0, 1) : null;
        }).filter(v => v != null);
        const rangeStart = starts.length ? Math.min(...starts) : 0;
        let rangeEnd = ends.length ? Math.max(...ends) : rangeStart + 1;
        if (rangeEnd <= rangeStart) rangeEnd = rangeStart + 1;
        const span = rangeEnd - rangeStart;
        const nowMs = parseInstantMs(snapshot?.polledAt) || Date.now();

        const rows = sortedTimelineSteps(steps).map(step => {
          const startMs = parseInstantMs(step.startedAt) ?? rangeStart;
          let endMs = parseInstantMs(step.completedAt);
          if (endMs == null) {
            const openEnded = !runTerminal && ['running', 'waiting'].includes(step.statusKey);
            endMs = openEnded ? Math.min(nowMs, rangeEnd) : startMs + Math.max(span * 0.01, 1);
          }
          const left = Math.max(0, ((startMs - rangeStart) / span) * 100);
          const width = Math.max(0.6, ((Math.max(endMs, startMs + 1) - startMs) / span) * 100);
          const displayName = resolveTimelineNodeName(step);
          const sub = resolveTimelineSubtitle(step, displayName);
          const durationText = formatDurationMs(step.durationMs);
          const barStatus = runTerminal && step.statusKey === 'running' && !step.completedAt
            ? 'completed'
            : (step.statusKey || 'unknown');
          return `<div class="run-timeline-gantt-row">
            <div class="run-timeline-gantt-label">
              <div class="run-timeline-gantt-name" title="${escapeAttr(displayName || '')}">${escapeHtml(displayName || '—')}</div>
              ${sub ? `<div class="run-timeline-gantt-sub">${escapeHtml(sub)}</div>` : ''}
            </div>
            <div class="run-timeline-gantt-track">
              <div class="run-timeline-gantt-bar status-${escapeAttr(barStatus)}" style="left:${left.toFixed(2)}%;width:${Math.min(width, 100 - left).toFixed(2)}%" title="${escapeAttr(`${displayName} · ${durationText}`)}">
                ${step.durationMs != null && width > 8 ? `<span class="run-timeline-gantt-duration">${escapeHtml(durationText)}</span>` : ''}
              </div>
            </div>
          </div>`;
        }).join('');

        return `<div class="run-timeline-gantt">
          <div class="run-timeline-gantt-axis">
            <div>${escapeHtml(t('runs.timeline.nodes'))}</div>
            <div class="run-timeline-gantt-axis-times">
              <span>${escapeHtml(formatClockTime(rangeStart, true))}</span>
              <span>${escapeHtml(formatDurationMs(span))}</span>
              <span>${escapeHtml(formatClockTime(rangeEnd, true))}</span>
            </div>
          </div>
          ${rows}
        </div>`;
      }

      function renderFeedView(snapshot) {
        const events = sortedExecutionLogs(snapshot);
        if (!events.length) {
          return `<div class="run-timeline-empty help">${escapeHtml(t('runs.timeline.empty'))}</div>`;
        }
        return `<ul class="run-timeline-feed">${events.map(ev => {
          const isWorkflowEvent = !ev.nodeId && String(ev.eventType || '').startsWith('WORKFLOW_');
          const nodeLabel = isWorkflowEvent
            ? t('runs.timeline.workflow')
            : resolveTimelineNodeName(ev);
          const sub = resolveTimelineSubtitle(ev, nodeLabel);
          return `<li class="run-timeline-feed-item">
            <span class="run-timeline-feed-seq">#${escapeHtml(String(ev.sequenceNo ?? '—'))}</span>
            <span class="run-timeline-feed-time">${escapeHtml(formatClockTime(ev.occurredAt, true))}</span>
            <span class="run-timeline-feed-type">${escapeHtml(eventTypeLabel(ev.eventType))}</span>
            <span class="run-timeline-feed-node"><strong>${escapeHtml(nodeLabel)}</strong>${sub ? `<div>${escapeHtml(sub)}</div>` : ''}</span>
            <span class="pill ${escapeAttr(normalizeTimelineStatus(ev.status))}">${escapeHtml(ev.status || '—')}</span>
          </li>`;
        }).join('')}</ul>`;
      }

      function renderJsonView(snapshot) {
        return `<pre class="run-timeline-json">${escapeHtml(pretty({
          executionLogs: snapshot?.executionLogs || [],
          nodeRuns: snapshot?.nodeRuns || []
        }))}</pre>`;
      }

      function updateRunTimelineViewTabs() {
        document.querySelectorAll('.run-timeline-view-tab').forEach(btn => {
          btn.classList.toggle('active', btn.dataset.view === runTimelineUi.view);
        });
        const sortBtn = $('runTimelineSortBtn');
        if (sortBtn) {
          sortBtn.dataset.sort = runTimelineUi.sort;
          sortBtn.textContent = runTimelineUi.sort === 'desc' ? t('runs.timeline.sortDesc') : t('runs.timeline.sortAsc');
        }
      }

      function renderRunTimelineBody(snapshot) {
        const body = $('runTimelineBody');
        if (!body) return;
        const steps = buildTimelineSteps(snapshot);
        if (runTimelineUi.view === 'feed') {
          body.innerHTML = renderFeedView(snapshot);
        } else if (runTimelineUi.view === 'json') {
          body.innerHTML = renderJsonView(snapshot);
        } else {
          body.innerHTML = renderCompactGantt(steps, snapshot);
        }
      }

      function renderRunTimeline(snapshot, runId) {
        runTimelineUi.snapshot = snapshot || null;
        runTimelineUi.runId = runId || null;
        const title = $('runTimelineTitle');
        const pill = $('runTimelineStatusPill');
        const meta = $('runTimelineMeta');
        const summary = $('runTimelineSummary');
        const inputPre = $('runTimelineInputJson');
        const resultPre = $('runTimelineResultJson');
        const temporalLink = $('runTimelineTemporalLink');
        if (!title) return;

        const run = state.flowRuns.find(r => r.id === runId);
        const status = snapshot?.status || run?.status || '—';
        const statusKey = normalizeTimelineStatus(status);
        const flowName = snapshot?.flowId || run?.definitionName || run?.definitionId || '—';
        const version = snapshot?.version || run?.version || '';
        title.textContent = runId || t('runs.timeline.title');
        title.title = runId || '';
        if (pill) {
          pill.className = `pill ${statusKey}`;
          pill.textContent = status;
        }
        if (meta) {
          meta.innerHTML = `
            <span><strong>${escapeHtml(t('runs.table.definition'))}:</strong> ${escapeHtml(flowName)}${version ? ` · v${escapeHtml(version)}` : ''}</span>
            <span><strong>${escapeHtml(t('runs.table.started'))}:</strong> ${escapeHtml(formatDateTimeShort(run?.startedAt || snapshot?.startedAt))}</span>
            ${snapshot?.runId ? `<span><strong>Run ID:</strong> ${escapeHtml(snapshot.runId)}</span>` : ''}
          `;
        }

        const steps = buildTimelineSteps(snapshot);
        const events = Array.isArray(snapshot?.executionLogs) ? snapshot.executionLogs : [];
        const starts = steps.map(s => parseInstantMs(s.startedAt)).filter(v => v != null);
        const ends = steps.map(s => parseInstantMs(s.completedAt)).filter(v => v != null);
        const eventTimes = events.map(e => parseInstantMs(e.occurredAt)).filter(v => v != null);
        const allStarts = [...starts, ...eventTimes];
        const minStart = allStarts.length ? Math.min(...allStarts) : parseInstantMs(run?.startedAt);
        const maxEnd = ends.length ? Math.max(...ends) : (eventTimes.length ? Math.max(...eventTimes) : null);
        const totalDuration = minStart != null && maxEnd != null && maxEnd >= minStart
          ? maxEnd - minStart
          : null;

        if (summary) {
          summary.innerHTML = [
            summaryItem(t('runs.timeline.started'), formatDateTimeShort(minStart ?? run?.startedAt)),
            summaryItem(t('runs.timeline.completed'), maxEnd != null ? formatDateTimeShort(maxEnd) : (statusKey === 'completed' ? formatDateTimeShort(snapshot?.polledAt) : '—')),
            summaryItem(t('runs.timeline.totalDuration'), formatDurationMs(totalDuration)),
            summaryItem(t('runs.timeline.nodes'), String(steps.filter(s => s.nodeId).length || steps.length)),
            summaryItem(t('runs.timeline.events'), String(events.length)),
            summaryItem(t('runs.table.status'), status)
          ].join('');
        }

        if (inputPre) inputPre.textContent = pretty(resolveRunInput(snapshot, run) ?? {});
        if (resultPre) resultPre.textContent = pretty(resolveRunResult(snapshot) ?? {});

        if (temporalLink) {
          const url = resolveRunTemporalHistoryUrl(run || { id: runId, lastSnapshot: snapshot });
          if (url) {
            temporalLink.href = url;
            temporalLink.hidden = false;
          } else {
            temporalLink.hidden = true;
            temporalLink.removeAttribute('href');
          }
        }

        updateRunTimelineViewTabs();
        renderRunTimelineBody(snapshot);
      }

      function setRunTimelineView(view) {
        runTimelineUi.view = view || 'compact';
        updateRunTimelineViewTabs();
        renderRunTimelineBody(runTimelineUi.snapshot);
      }

      function toggleRunTimelineSort() {
        runTimelineUi.sort = runTimelineUi.sort === 'asc' ? 'desc' : 'asc';
        updateRunTimelineViewTabs();
        renderRunTimelineBody(runTimelineUi.snapshot);
      }

      function copyRunTimelineJson(which) {
        const pre = which === 'result' ? $('runTimelineResultJson') : $('runTimelineInputJson');
        const text = pre?.textContent?.trim();
        if (!text) {
          message(t('message.copyEmpty'), 'warning');
          return;
        }
        navigator.clipboard?.writeText(text)
          .then(() => message(t('message.copiedToClipboard')))
          .catch(() => message(t('message.copyFailed'), 'error'));
      }

      async function openRunTimeline(runId) {
        const id = runId || activeWorkflowRunId();
        if (!id) return;
        const backdrop = $('runTimelineBackdrop');
        if (!backdrop) return;
        state.activeRunId = id;
        setActiveWorkflowRunId(id);
        backdrop.classList.add('open');
        backdrop.setAttribute('aria-hidden', 'false');
        renderRunTimeline(state.runtimeSnapshot?.workflowId === id ? state.runtimeSnapshot : null, id);
        const data = await queryRunState(id, { silent: true, skipJsonPanel: true });
        renderRunTimeline(data || state.runtimeSnapshot, id);
        startRuntimePolling();
      }

      async function refreshRunTimeline() {
        const id = activeWorkflowRunId() || state.activeRunId;
        if (!id) return;
        const data = await queryRunState(id, { silent: true, skipJsonPanel: true });
        renderRunTimeline(data || state.runtimeSnapshot, id);
      }

      function closeRunTimeline() {
        const backdrop = $('runTimelineBackdrop');
        if (!backdrop) return;
        backdrop.classList.remove('open');
        backdrop.setAttribute('aria-hidden', 'true');
        if (typeof shouldPollRuntime === 'function' && !shouldPollRuntime()) stopRuntimePolling();
      }

      function initRunTimelineDialog() {
        const backdrop = $('runTimelineBackdrop');
        if (!backdrop) return;
        $('runTimelineRefreshBtn')?.addEventListener('click', () => refreshRunTimeline());
        $('runTimelineCloseBtn')?.addEventListener('click', () => closeRunTimeline());
        $('runTimelineOpenModelerBtn')?.addEventListener('click', () => {
          const id = activeWorkflowRunId() || state.activeRunId;
          if (id) openFlowRunInModeler(id);
        });
        $('runTimelineCopyInputBtn')?.addEventListener('click', () => copyRunTimelineJson('input'));
        $('runTimelineCopyResultBtn')?.addEventListener('click', () => copyRunTimelineJson('result'));
        $('runTimelineSortBtn')?.addEventListener('click', () => toggleRunTimelineSort());
        backdrop.querySelectorAll('.run-timeline-view-tab').forEach(btn => {
          btn.addEventListener('click', () => setRunTimelineView(btn.dataset.view));
        });
        backdrop.addEventListener('click', event => {
          if (event.target === backdrop) closeRunTimeline();
        });
        document.addEventListener('keydown', event => {
          if (!isRunTimelineOpen()) return;
          if (event.key === 'Escape') {
            event.preventDefault();
            closeRunTimeline();
          }
        });
      }

      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initRunTimelineDialog);
      } else {
        initRunTimelineDialog();
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

      function clearActiveRunIfNamespaceMismatch(namespace) {
        const active = activeFlowRun();
        if (active && !runMatchesNamespace(active, namespace)) {
          state.activeRunId = '';
          setActiveWorkflowRunId('');
          state.runtimeSnapshot = null;
          stopRuntimePolling?.();
        }
      }

      function renderRunsSearchPrompt() {
        const table = $('runsTable');
        if (!table) return;
        table.innerHTML = `<div class="runs-search-prompt admin-empty">${escapeHtml(t('runs.searchPrompt'))}</div>`;
      }

      function resetRunsSearchView() {
        state.runsListLoaded = false;
        if (state.currentView === 'runs') renderRunsSearchPrompt();
      }

      function renderRunsView() {
        stopRuntimePolling?.();
        state.runsListLoaded = false;
        loadFlowRuns();
        renderRunsSearchPrompt();
      }

      function mergeServerRuns(serverItems) {
        const byId = new Map(state.flowRuns.map(run => [run.id, run]));
        (serverItems || []).forEach(item => {
          const existing = byId.get(item.workflowId) || {};
          byId.set(item.workflowId, {
            id: item.workflowId,
            definitionId: item.flowId || existing.definitionId,
            definitionName: item.flowName || existing.definitionName || item.flowId,
            version: item.version || existing.version,
            namespace: item.namespace || existing.namespace,
            input: existing.input,
            runSource: item.runSource || existing.runSource || 'web-modeler',
            status: item.status || existing.status || 'RUNNING',
            startedAt: item.startedAt || existing.startedAt,
            lastQueriedAt: existing.lastQueriedAt,
            lastSnapshot: existing.lastSnapshot
          });
        });
        state.flowRuns = Array.from(byId.values())
          .sort((a, b) => String(b.startedAt || '').localeCompare(String(a.startedAt || '')))
          .slice(0, 100);
        persistFlowRuns();
      }

      async function fetchServerRuns(keyword = '') {
        const params = new URLSearchParams();
        if (keyword) params.set('keyword', keyword);
        params.set('page', '0');
        params.set('size', '100');
        const query = params.toString();
        const res = await fetch(
          platformApiUrl(`/flows/runs${query ? `?${query}` : ''}`),
          { headers: platformApiHeaders() }
        );
        const data = await res.json();
        if (!res.ok) {
          throw new Error(data.message || data.error || res.statusText);
        }
        return Array.isArray(data.items) ? data.items : [];
      }

      function searchRunsList() {
        loadFlowRuns();
        state.runsListLoaded = true;
        renderRunsList();
        const keyword = ($('runsSearch')?.value || '').trim();
        fetchServerRuns(keyword)
          .then(items => {
            mergeServerRuns(items);
            renderRunsList();
          })
          .catch(err => message(t('runs.message.loadFailed', { error: err.message }), 'error'));
      }

      function renderRunsList() {
        const table = $('runsTable');
        if (!table) return;
        if (!state.runsListLoaded) {
          if (state.currentView === 'runs') renderRunsSearchPrompt();
          return;
        }
        const keyword = ($('runsSearch')?.value || '').trim().toLowerCase();
        const currentNamespace = (typeof platformNamespace === 'function' ? platformNamespace() : '') || '';
        const rows = state.flowRuns.filter(run => {
          if (!runMatchesNamespace(run, currentNamespace)) return false;
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

      function resolveRunTemporalHistoryUrl(run) {
        const snapshot = run?.lastSnapshot
          || (run?.id && run.id === state.runtimeSnapshot?.workflowId ? state.runtimeSnapshot : null);
        if (snapshot?.temporalHistoryUrl) return snapshot.temporalHistoryUrl;
        const workflowId = run?.id || snapshot?.workflowId;
        if (!workflowId) return null;
        const temporal = window.FLOWFOUNDRY_PUBLIC_CONFIG?.temporal || {};
        const base = String(snapshot?.temporalUiBaseUrl || temporal.uiBaseUrl || 'http://127.0.0.1:8080').replace(/\/+$/, '');
        const namespace = encodeURIComponent(snapshot?.temporalNamespace || platformNamespace() || 'default');
        const wf = encodeURIComponent(workflowId);
        const runId = snapshot?.runId;
        if (runId) {
          return `${base}/namespaces/${namespace}/workflows/${wf}/${encodeURIComponent(runId)}/history`;
        }
        return `${base}/namespaces/${namespace}/workflows/${wf}/history`;
      }

      function openRunTemporalLog(runId) {
        const run = state.flowRuns.find(item => item.id === runId);
        const url = resolveRunTemporalHistoryUrl(run);
        if (!url) {
          message(t('runs.message.temporalLogUnavailable'), 'error');
          return;
        }
        window.open(url, '_blank', 'noopener,noreferrer');
      }

      async function showRunState(runId) {
        await queryRunState(runId);
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
            <button class="secondary" onclick="showRunState('${escapeAttr(run.id)}')">${escapeHtml(t('runs.showState'))}</button>
            <button class="secondary" onclick="openRunTemporalLog('${escapeAttr(run.id)}')">${escapeHtml(t('runs.temporalLog'))}</button>
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
        const run = state.flowRuns.find(r => r.id === runId);
        if (run) {
          if (data.runSource) run.runSource = data.runSource;
          const namespace = extractNamespaceFromBusinessKey(data.businessKey);
          if (namespace) run.namespace = namespace;
          persistFlowRuns();
        }
        renderRuntimeStatus(data);
        highlightRuntimeNode(data.currentNodeId || data.waitingHumanTaskNodeId || null);
        if (state.currentView === 'modeler') renderCanvas();
        if (isRunStatusDialogOpen()) {
          $('runStatusWorkflowId').value = runId;
          refreshRunStatusSections();
        }
        if (isRunTimelineOpen()) {
          renderRunTimeline(data, runId);
        }
      }
