      const RUNTIME_POLL_MS = 2500;
      let runtimePollTimer = null;

      function isTerminalRunStatus(status) {
        return ['COMPLETED', 'FAILED', 'CANCELED', 'TERMINATED', 'TIMED_OUT', 'NOT_FOUND']
          .includes(String(status || '').toUpperCase());
      }

      function startRuntimePolling() {
        stopRuntimePolling();
        const tick = async () => {
          if (!shouldPollRuntime()) {
            stopRuntimePolling();
            return;
          }
          const id = activeWorkflowRunId();
          if (!id) {
            stopRuntimePolling();
            return;
          }
          await queryRunState(id, { silent: true, skipJsonPanel: true });
          if (isRunStatusDialogOpen()) refreshRunStatusSections();
          if (isTerminalRunStatus(state.runtimeSnapshot?.status)) {
            stopRuntimePolling();
          }
        };
        tick();
        runtimePollTimer = setInterval(tick, RUNTIME_POLL_MS);
      }

      function stopRuntimePolling() {
        if (runtimePollTimer) {
          clearInterval(runtimePollTimer);
          runtimePollTimer = null;
        }
      }

      function renderRuntimeStatus(data) {
        const html = buildRuntimeStatusHtml(data);
        ['runtimeStatusPanel', 'runStatusModalPanel'].forEach(id => {
          const panel = $(id);
          if (panel) panel.innerHTML = html;
        });
      }

      function buildRuntimeStatusHtml(data) {
        if (!data) {
          return `<div class="help">${escapeHtml(t('runtime.statusEmpty'))}</div>`;
        }
        const status = data.status || 'RUNNING';
        const currentNodeId = data.currentNodeId || '-';
        const currentNodeName = data.currentNodeName || nodeLabel(data.currentNodeId) || currentNodeId;
        const currentActivityType = data.currentActivityType || '';
        const waitingId = data.waitingHumanTaskNodeId;
        const waitingLabel = waitingId ? (nodeLabel(waitingId) || waitingId) : t('runtime.notWaiting');
        const temporalStatus = data.temporalStatus || '-';
        const polledAt = data.polledAt ? formatDate(data.polledAt) : '-';
        const failureHtml = data.failureMessage
          ? `<div class="runtime-status-failure">${escapeHtml(data.failureType || 'FAILED')}: ${escapeHtml(data.failureMessage)}</div>`
          : '';
        return `
          <div class="runtime-status-row">
            <span class="label">${escapeHtml(t('runtime.statusLabel'))}</span>
            <span class="value"><span class="pill ${escapeAttr(status.toLowerCase())}">${escapeHtml(status)}</span></span>
          </div>
          <div class="runtime-status-row">
            <span class="label">${escapeHtml(t('runtime.temporalStatusLabel'))}</span>
            <span class="value">${escapeHtml(temporalStatus)}</span>
          </div>
          <div class="runtime-status-row">
            <span class="label">${escapeHtml(t('runtime.currentNodeLabel'))}</span>
            <span class="value">${escapeHtml(currentNodeName)}<div class="help">${escapeHtml(currentNodeId)}${currentActivityType ? ` · ${escapeHtml(currentActivityType)}` : ''}</div></span>
          </div>
          <div class="runtime-status-row">
            <span class="label">${escapeHtml(t('runtime.waitingHumanTaskLabel'))}</span>
            <span class="value">${escapeHtml(waitingLabel)}</span>
          </div>
          ${failureHtml}
          <div class="runtime-status-poll">${escapeHtml(t('runtime.lastSynced', { time: polledAt }))}</div>
        `;
      }

      function nodeLabel(nodeId) {
        if (!nodeId) return '';
        const node = state.model?.nodes?.find(n => n.id === nodeId);
        return node?.name || nodeId;
      }

      function humanTaskModeForNode(node) {
        return node.config?.flowFoundryHumanTask?.mode || 'managed';
      }

      function humanTasksFromModel(waitingNodeId = null) {
        return state.model.nodes
          .filter(n => isHumanTaskKind(n.kind))
          .filter(n => humanTaskModeForNode(n) !== 'offline')
          .map(n => ({
            nodeId: n.id,
            mode: humanTaskModeForNode(n),
            waiting: n.id === waitingNodeId,
            name: n.name || n.id
          }));
      }

      function resolveHumanTaskOptions(snapshot) {
        const waitingId = snapshot?.waitingHumanTaskNodeId || null;
        const fromApi = snapshot?.humanTasks || [];
        if (fromApi.length) {
          return fromApi.map(task => ({
            nodeId: task.nodeId,
            mode: task.mode || 'managed',
            waiting: !!task.waiting,
            name: nodeLabel(task.nodeId) || task.nodeId
          }));
        }
        return humanTasksFromModel(waitingId);
      }

      function humanTaskChoiceLabel(task) {
        const modeLabel = task.mode === 'offline' ? t('prop.humanTaskModeOffline') : t('prop.humanTaskModeManaged');
        return `${task.name} (${task.nodeId})`;
      }
