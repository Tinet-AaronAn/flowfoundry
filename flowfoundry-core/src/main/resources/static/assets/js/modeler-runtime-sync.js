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
          ${buildPendingChildWorkflowsHtml(data)}
          ${failureHtml}
          <div class="runtime-status-poll">${escapeHtml(t('runtime.lastSynced', { time: polledAt }))}</div>
        `;
      }

      function pendingChildWorkflows(snapshot) {
        return Array.isArray(snapshot?.pendingChildWorkflows) ? snapshot.pendingChildWorkflows : [];
      }

      function buildPendingChildWorkflowsHtml(data) {
        const children = pendingChildWorkflows(data);
        const cards = children.map(child => childWorkflowCardHtml(child)).join('');
        return `
          <div class="runtime-pending-children">
            <div class="runtime-status-row">
              <span class="label">${escapeHtml(t('runtime.pendingChildWorkflowsLabel'))}</span>
              <span class="value">${children.length ? `${children.length}` : escapeHtml(t('runtime.pendingChildWorkflowsEmpty'))}</span>
            </div>
            ${cards}
          </div>
        `;
      }

      function childWorkflowCardHtml(child) {
        const flowLabel = child.flowId || child.workflowId || '-';
        const parentNodeId = child.parentNodeId || '';
        const parentNodeName = nodeLabel(parentNodeId) || parentNodeId || '-';
        const currentNodeId = child.currentNodeId || '-';
        const currentNodeName = child.currentNodeName || nodeLabel(currentNodeId) || currentNodeId;
        const status = child.status || 'RUNNING';
        const waitingId = child.waitingHumanTaskNodeId;
        const waitingLabel = waitingId ? (nodeLabel(waitingId) || waitingId) : t('runtime.notWaiting');
        const historyUrl = child.temporalHistoryUrl || '';
        const canCompleteChildHumanTask =
          String(status).toUpperCase() === 'WAITING_HUMAN_TASK' && !!waitingId;
        const actions = [];
        if (historyUrl) {
          actions.push(
            `<button type="button" class="secondary runtime-child-action" onclick="openTemporalHistoryUrl('${escapeAttr(historyUrl)}')">${escapeHtml(t('runtime.openChildTemporalUi'))}</button>`
          );
        }
        if (canCompleteChildHumanTask) {
          actions.push(
            `<button type="button" class="runtime-child-action" onclick="completeHumanTaskForWorkflow('${escapeAttr(child.workflowId)}', '${escapeAttr(waitingId)}')">${escapeHtml(t('runtime.completeChildHumanTask'))}</button>`
          );
        }
        return `
          <div class="runtime-child-card">
            <div class="runtime-child-card-title">${escapeHtml(t('runtime.childWorkflowScope', { flowId: flowLabel }))}</div>
            <div class="help runtime-child-workflow-id">${escapeHtml(child.workflowId || '')}</div>
            <div class="runtime-status-row">
              <span class="label">${escapeHtml(t('runtime.statusLabel'))}</span>
              <span class="value"><span class="pill ${escapeAttr(String(status).toLowerCase())}">${escapeHtml(status)}</span></span>
            </div>
            <div class="runtime-status-row">
              <span class="label">${escapeHtml(t('runtime.childWorkflowParentNode'))}</span>
              <span class="value">${escapeHtml(parentNodeName)}<div class="help">${escapeHtml(parentNodeId || '-')}</div></span>
            </div>
            <div class="runtime-status-row">
              <span class="label">${escapeHtml(t('runtime.currentNodeLabel'))}</span>
              <span class="value">${escapeHtml(currentNodeName)}<div class="help">${escapeHtml(currentNodeId)}${child.currentActivityType ? ` · ${escapeHtml(child.currentActivityType)}` : ''}</div></span>
            </div>
            <div class="runtime-status-row">
              <span class="label">${escapeHtml(t('runtime.waitingHumanTaskLabel'))}</span>
              <span class="value">${escapeHtml(waitingLabel)}</span>
            </div>
            ${actions.length ? `<div class="runtime-child-actions">${actions.join('')}</div>` : ''}
          </div>
        `;
      }

      function openTemporalHistoryUrl(url) {
        const target = String(url || '').trim();
        if (!target) return;
        window.open(target, '_blank', 'noopener,noreferrer');
      }

      async function submitHumanTaskCompletion(workflowId, nodeId) {
        await post(`/flows/runs/${encodeURIComponent(workflowId)}/human-task`, {
          nodeId,
          outcome: 'approved',
          variables: { approved: true }
        });
      }

      async function completeHumanTaskForWorkflow(workflowId, nodeId) {
        if (!workflowId || !nodeId) return;
        try {
          await submitHumanTaskCompletion(workflowId, nodeId);
          message(t('message.humanTaskSignalSent', { nodeId }));
          const rootId = activeWorkflowRunId();
          if (rootId) {
            await queryRunState(rootId, { silent: true, skipJsonPanel: true });
          } else {
            await queryRunState(workflowId, { silent: true, skipJsonPanel: true });
          }
          startRuntimePolling();
        } catch (err) {
          message(t('message.queryFailed', { error: err.message }), 'error');
        }
      }

      function resolveAllWaitingHumanTaskTargets(snapshot) {
        const targets = [];
        if (!snapshot) return targets;
        const rootStatus = String(snapshot.status || '').toUpperCase();
        const rootWaitingId = snapshot.waitingHumanTaskNodeId
          || resolveHumanTaskOptions(snapshot).find(task => task.waiting)?.nodeId
          || null;
        if (rootStatus === 'WAITING_HUMAN_TASK' && rootWaitingId) {
          resolveHumanTaskOptions(snapshot)
            .filter(task => task.nodeId === rootWaitingId)
            .forEach(task => {
              targets.push({
                workflowId: snapshot.workflowId,
                scopeLabel: t('runtime.humanTaskScopeRoot'),
                nodeId: task.nodeId,
                name: task.name || task.nodeId,
                waiting: true
              });
            });
        }
        for (const child of pendingChildWorkflows(snapshot)) {
          const childStatus = String(child.status || '').toUpperCase();
          const waitingId = child.waitingHumanTaskNodeId;
          if (childStatus !== 'WAITING_HUMAN_TASK' || !waitingId) continue;
          const taskName = nodeLabel(waitingId) || waitingId;
          targets.push({
            workflowId: child.workflowId,
            scopeLabel: t('runtime.humanTaskScopeChild', { flowId: child.flowId || child.workflowId }),
            nodeId: waitingId,
            name: taskName,
            waiting: true
          });
        }
        return targets;
      }

      function nodeLabel(nodeId) {
        if (!nodeId) return '';
        const node = state.model?.nodes?.find(n => n.id === nodeId);
        return node?.name || nodeId;
      }

      function humanTaskModeForNode(node) {
        const raw = node.config?.flowFoundryHumanTask?.mode || 'managed';
        return String(raw).toLowerCase() === 'offline' ? 'managed' : raw;
      }

      function humanTasksFromModel(waitingNodeId = null) {
        return state.model.nodes
          .filter(n => isHumanTaskKind(n.kind))
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
            mode: task.mode === 'offline' ? 'managed' : (task.mode || 'managed'),
            waiting: !!task.waiting,
            name: nodeLabel(task.nodeId) || task.nodeId
          }));
        }
        return humanTasksFromModel(waitingId);
      }

      function humanTaskChoiceLabel(task) {
        return `${task.name} (${task.nodeId})`;
      }
