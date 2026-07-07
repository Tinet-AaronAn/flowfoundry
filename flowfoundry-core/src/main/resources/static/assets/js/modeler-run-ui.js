      const DEFAULT_RUN_INPUT = pretty({
        campaignId: 'demo-campaign',
        remainingContacts: 0,
        vipSegment: false,
        roundNumber: 1,
        maxRounds: 1,
        roundIntervalMinutes: 1
      });

      function formatRunInputJson(raw) {
        const text = String(raw ?? '').trim();
        if (!text) return pretty({});
        try {
          return pretty(JSON.parse(text));
        } catch {
          return text;
        }
      }

      function activeWorkflowRunId() {
        return (
          $('workflowId')?.value?.trim()
          || state.activeRunId
          || ''
        );
      }

      function setActiveWorkflowRunId(workflowId) {
        state.activeRunId = workflowId || null;
        const input = $('workflowId');
        if (input) input.value = workflowId || '';
      }

      function isRunStatusDialogOpen() {
        return $('runStatusBackdrop')?.classList.contains('open');
      }

      function shouldPollRuntime() {
        const id = activeWorkflowRunId();
        if (!id) return false;
        if (state.currentView === 'modeler') return true;
        if (isRunStatusDialogOpen()) return true;
        return false;
      }

      async function openRunDialog() {
        const stored = $('runInput')?.value?.trim();
        const displayValue = formatRunInputJson(stored || DEFAULT_RUN_INPUT);
        const raw = await showAppDialog({
          title: t('modeler.runDialogTitle'),
          message: t('runtime.runHelp'),
          input: 'textarea',
          value: displayValue,
          textareaClass: 'json-editor',
          confirmLabel: t('toolbar.run')
        });
        if (raw == null) return;
        try {
          const parsed = JSON.parse(raw || '{}');
          if ($('runInput')) $('runInput').value = pretty(parsed);
          await runFlow(parsed);
        } catch (err) {
          message(t('message.runInputInvalid', { error: err.message }), 'error');
        }
      }

      function activeCompiledPlan() {
        return state.lastCompiledPlan || state.lastRunResult?.executionPlan || null;
      }

      function updateRunStatusCompiledPlan() {
        const pre = $('runStatusCompiledJson');
        const empty = $('runStatusCompiledEmpty');
        const plan = activeCompiledPlan();
        if (!pre) return;
        if (!plan) {
          pre.textContent = '';
          pre.hidden = true;
          if (empty) empty.hidden = false;
          return;
        }
        pre.textContent = pretty(plan);
        pre.hidden = false;
        if (empty) empty.hidden = true;
      }

      function resolveTemporalHistoryUrl() {
        const snapshot = state.runtimeSnapshot;
        if (snapshot?.temporalHistoryUrl) return snapshot.temporalHistoryUrl;
        const workflowId = snapshot?.workflowId || activeWorkflowRunId();
        if (!workflowId) return null;
        const temporal = window.FLOWFOUNDRY_PUBLIC_CONFIG?.temporal || {};
        const base = String(temporal.uiBaseUrl || 'http://127.0.0.1:8080').replace(/\/+$/, '');
        const namespace = encodeURIComponent(temporal.namespace || snapshot?.temporalNamespace || 'default');
        const wf = encodeURIComponent(workflowId);
        const runId = snapshot?.runId;
        if (runId) {
          return `${base}/namespaces/${namespace}/workflows/${wf}/${encodeURIComponent(runId)}/history`;
        }
        return `${base}/namespaces/${namespace}/workflows/${wf}/history`;
      }

      function updateRunStatusTemporalUiLink() {
        const link = $('runStatusTemporalUiLink');
        if (!link) return;
        const url = resolveTemporalHistoryUrl();
        if (!url) {
          link.hidden = true;
          link.removeAttribute('href');
          return;
        }
        link.href = url;
        link.hidden = false;
      }

      function updateRunStatusTemporalLogs() {
        const pre = $('runStatusLogJson');
        if (!pre) return;
        const snapshot = state.runtimeSnapshot;
        const history = snapshot?.temporalHistory;
        if (Array.isArray(history) && history.length) {
          pre.textContent = pretty(history);
          return;
        }
        pre.textContent = pretty({
          events: [],
          note: t('runtime.temporalHistoryEmpty')
        });
      }

      function copyRunStatusTemporalLogs() {
        const text = $('runStatusLogJson')?.textContent?.trim();
        if (!text) {
          message(t('message.copyEmpty'), 'warning');
          return;
        }
        navigator.clipboard?.writeText(text)
          .then(() => message(t('message.copiedToClipboard')))
          .catch(() => message(t('message.copyFailed'), 'error'));
      }

      function refreshRunStatusSections() {
        const id = activeWorkflowRunId();
        if ($('runStatusWorkflowId')) $('runStatusWorkflowId').value = id || '';
        updateRunStatusCompiledPlan();
        updateRunStatusTemporalLogs();
        updateRunStatusTemporalUiLink();
      }

      async function refreshRunStatusDialog() {
        const id = activeWorkflowRunId();
        if (!id) {
          renderRuntimeStatus(null);
          refreshRunStatusSections();
          return;
        }
        await queryRunState(id, { silent: true, skipJsonPanel: true });
        refreshRunStatusSections();
      }

      function openRunStatusDialog() {
        const backdrop = $('runStatusBackdrop');
        if (!backdrop) return;
        backdrop.classList.add('open');
        backdrop.setAttribute('aria-hidden', 'false');
        refreshRunStatusSections();
        const id = activeWorkflowRunId();
        if (!id) {
          renderRuntimeStatus(null);
          return;
        }
        refreshRunStatusDialog();
        startRuntimePolling();
      }

      function closeRunStatusDialog() {
        const backdrop = $('runStatusBackdrop');
        if (!backdrop) return;
        backdrop.classList.remove('open');
        backdrop.setAttribute('aria-hidden', 'true');
        if (!shouldPollRuntime()) stopRuntimePolling();
      }

      function initRunStatusDialog() {
        const backdrop = $('runStatusBackdrop');
        if (!backdrop) return;
        $('runStatusRefreshBtn')?.addEventListener('click', () => refreshRunStatusDialog());
        $('runStatusLogCopyBtn')?.addEventListener('click', () => copyRunStatusTemporalLogs());
        $('runStatusCloseBtn')?.addEventListener('click', () => closeRunStatusDialog());
        backdrop.addEventListener('click', event => {
          if (event.target === backdrop) closeRunStatusDialog();
        });
        document.addEventListener('keydown', event => {
          if (!isRunStatusDialogOpen()) return;
          if (event.key === 'Escape') {
            event.preventDefault();
            closeRunStatusDialog();
          }
        });
      }

      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initRunStatusDialog);
      } else {
        initRunStatusDialog();
      }
