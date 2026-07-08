      function updateFlowMeta() {
        state.model.name = $('flowName').value;
        if (state.model.process) {
          state.model.process.name = state.model.name;
        }
        const workflow = activeWorkflow();
        if (workflow) {
          workflow.name = state.model.name;
          workflow.updatedAt = new Date().toISOString();
          persistWorkflowStore();
          renderWorkflowList();
        }
      }

      function updateProcess(key, value) {
        pushHistory();
        state.model.process[key] = value;
        if (key === 'name') $('flowName').value = value;
        if (key === 'edgeRouting') renderCanvas();
      }

      function updateNode(key, value) {
        const n = selectedNode();
        if (!n) return;
        pushHistory();
        if (key === 'id') {
          const old = n.id;
          n.id = value;
          state.selected.id = value;
          if (isParticipantContainer(n)) {
            state.model.nodes.forEach(candidate => {
              if (candidate.participantId === old) candidate.participantId = value;
            });
          }
          state.model.edges.forEach(e => {
            if (e.from === old) e.from = value;
            if (e.to === old) e.to = value;
          });
        } else {
          n[key] = value;
        }
        refreshNodePreview(n);
        updateButtons();
      }

      function updateLoopMode(value) {
        const n = selectedNode();
        if (!n) return;
        pushHistory();
        n.loop = value;
        const mode = value === 'none' ? 'none' : value === 'standardLoop' ? 'standard' : 'multiInstance';
        n.config = { ...(n.config || {}), flowFoundryLoop: { ...(n.config?.flowFoundryLoop || {}), mode } };
        refreshNodePreview(n);
        renderProperties();
        updateButtons();
      }

      function updateNodeNumber(key, value) {
        updateNode(key, value === '' ? null : Number(value));
      }

      function syncTaskDefinitionFromNode(node) {
        if (!node?.activityType) return;
        node.config = {
          ...(node.config || {}),
          flowFoundryTaskDefinition: {
            ...(node.config?.flowFoundryTaskDefinition || {}),
            type: node.activityType,
            retries: String(node.maxAttempts ?? 3)
          }
        };
      }

      function applyRegisteredActivityDefaults(node, activityId) {
        if (!node) return;
        node.activityType = activityId || '';
        if (!activityId) {
          delete node.timeout;
          node.maxAttempts = 3;
          syncTaskDefinitionFromNode(node);
          return;
        }
        const definition = findRegisteredActivity(activityId);
        node.maxAttempts = definition?.retry?.maximumAttempts ?? 3;
        if (definition?.timeout) {
          node.timeout = definition.timeout;
        } else {
          delete node.timeout;
        }
        if (isPlatformCoreActivity(activityId)) {
          node.taskQueue = 'flowfoundry-platform';
        } else if (definition?.taskQueue && !node.taskQueue) {
          node.taskQueue = definition.taskQueue;
        }
        syncTaskDefinitionFromNode(node);
      }

      function isPlatformCoreActivity(activityId) {
        return activityId === 'script-runtime' || activityId === 'human-task';
      }

      function resolveNodeTaskQueue(node) {
        const activityType = node.activityType
          || (node.kind === 'scriptTask' ? 'script-runtime' : null)
          || (node.kind === 'humanTask' || node.kind === 'userTask' ? 'human-task' : null);
        if (isPlatformCoreActivity(activityType)) {
          return 'flowfoundry-platform';
        }
        return node.taskQueue;
      }

      function updateActivityType(value) {
        const n = selectedNode();
        if (!n) return;
        pushHistory();
        applyRegisteredActivityDefaults(n, value);
        if (value === 'script-runtime') {
          n.scriptCodeId = n.scriptCodeId || 'demo-script';
          n.scriptVersion = n.scriptVersion || '1';
        } else {
          delete n.scriptCodeId;
          delete n.scriptVersion;
          delete n.scriptName;
        }
        refreshNodePreview(n);
        renderProperties();
      }

      function updateTaskRetries(value) {
        const n = selectedNode();
        if (!n) return;
        pushHistory();
        n.maxAttempts = value === '' ? null : Number(value);
        syncTaskDefinitionFromNode(n);
        refreshNodePreview(n);
      }

      function updateTaskTimeout(value) {
        const n = selectedNode();
        if (!n) return;
        pushHistory();
        n.timeout = value || undefined;
        refreshNodePreview(n);
      }

      function updateConfig(key, value) {
        const n = selectedNode();
        pushHistory();
        n.config = { ...(n.config || {}), [key]: value };
        refreshNodePreview(n);
      }

      function updateChildWorkflowRef(workflowId) {
        const n = selectedNode();
        if (!n || n.kind !== 'workflow') return;
        pushHistory();
        const workflow = state.workflows.find(w => w.id === workflowId);
        n.config = {
          ...(n.config || {}),
          childWorkflowId: workflowId,
          childWorkflowName: workflow?.name || '',
          childWorkflowVersion: workflow?.version || workflow?.versions?.[0]?.version || '1.0.0'
        };
        refreshNodePreview(n);
        renderProperties();
      }

      async function updateScriptTaskRef(scriptCodeId) {
        const n = selectedNode();
        if (!n || !isScriptRuntimeNode(n)) return;
        pushHistory();
        const entry = scriptCatalogEntry(scriptCodeId);
        n.scriptCodeId = scriptCodeId || '';
        if (entry?.scriptName) n.scriptName = entry.scriptName;
        const versions = await ensureScriptVersions(scriptCodeId);
        const active = versions.find(v => v.active) || versions.find(v => v.published) || versions[0];
        n.scriptVersion = active?.version || entry?.activeVersion || entry?.latestPublishedVersion || '1';
        refreshNodePreview(n);
        renderProperties();
      }

      function updateParticipantOwner(value) {
        const n = selectedNode();
        if (!n || !isParticipantAssignable(n)) return;
        pushHistory();
        if (value) n.participantId = value;
        else delete n.participantId;
        refreshNodePreview(n);
      }

      function updateConfigPath(path, value) {
        const n = selectedNode();
        pushHistory();
        n.config = { ...(n.config || {}) };
        let current = n.config;
        for (let i = 0; i < path.length - 1; i++) {
          current[path[i]] = { ...(current[path[i]] || {}) };
          current = current[path[i]];
        }
        current[path[path.length - 1]] = value;
        refreshNodePreview(n);
      }

      function updateStartEventSubtype(value) {
        const n = selectedNode();
        pushHistory();
        const subtype = value || 'none';
        n.config = { ...(n.config || {}), startEventSubtype: subtype };
        if (subtype === 'timer') {
          const previous = n.config.timerDefinition || {};
          if (!previous.type || !previous.value) {
            n.config.timerDefinition = {
              type: previous.type || 'cycle',
              value: previous.value || startTimerDefaultValue(previous.type || 'cycle')
            };
          }
        } else {
          delete n.config.timerDefinition;
        }
        refreshNodePreview(n);
        updateButtons();
        renderProperties();
      }

      function updateStartTimer(key, value) {
        const n = selectedNode();
        pushHistory();
        const previous = n.config?.timerDefinition || {};
        const timerDefinition = { ...previous, [key]: value };
        if (key === 'type') {
          const previousType = timerDefinitionType(previous);
          const nextType = value || 'cycle';
          const currentValue = previous.value;
          if (shouldResetStartTimerValueOnTypeChange(currentValue, previousType, nextType)) {
            timerDefinition.value = startTimerDefaultValue(nextType);
          }
          if (previousType === 'date' && nextType !== 'date') {
            delete timerDefinition.timezone;
          }
          if (nextType === 'date' && !timerDefinition.timezone) {
            timerDefinition.timezone = 'Asia/Shanghai';
          }
        }
        n.config = { ...(n.config || {}), startEventSubtype: 'timer', timerDefinition };
        refreshNodePreview(n);
        updateButtons();
        if (key === 'type' && state.selected.id === n.id) renderProperties();
      }

      function updateTimer(key, value) {
        const n = selectedNode();
        pushHistory();
        const previous = n.config?.timerDefinition || {};
        const timerDefinition = { ...previous, [key]: value };
        if (key === 'type') {
          const previousType = timerDefinitionType(previous);
          const nextType = value || 'duration';
          const currentValue = previous.value ?? (previousType === 'duration' ? n.config?.duration : undefined);
          if (shouldResetTimerValueOnTypeChange(currentValue, previousType, nextType)) {
            timerDefinition.value = timerDefaultValue(nextType);
          }
          if (previousType === 'date' && nextType !== 'date') {
            delete timerDefinition.timezone;
            delete timerDefinition.pastTargetStrategy;
          }
          if (nextType === 'date') {
            if (!timerDefinition.timezone) timerDefinition.timezone = '${slot.timezone}';
            if (!timerDefinition.pastTargetStrategy) timerDefinition.pastTargetStrategy = 'fireImmediately';
          }
        }
        n.config = { ...(n.config || {}), timerDefinition };
        refreshNodePreview(n);
        updateButtons();
        if (key === 'type' && state.selected.id === n.id) renderProperties();
      }

      function updateJsonNode(key, value) {
        const n = selectedNode();
        try {
          pushHistory();
          n[key] = JSON.parse(value || '{}');
          refreshNodePreview(n);
        } catch (err) {
          message(t('message.jsonInvalid', { error: err.message }), 'error');
        }
      }

      function updateJsonConfigPath(path, value) {
        const n = selectedNode();
        try {
          pushHistory();
          const parsed = JSON.parse(value || '{}');
          n.config = { ...(n.config || {}) };
          let current = n.config;
          for (let i = 0; i < path.length - 1; i++) {
            current[path[i]] = { ...(current[path[i]] || {}) };
            current = current[path[i]];
          }
          current[path[path.length - 1]] = parsed;
          if (path.length === 1 && path[0] === 'taskHeaders') {
            migrateNodeTaskHeaders(n);
          }
          refreshNodePreview(n);
        } catch (err) {
          message(t('message.jsonInvalid', { error: err.message }), 'error');
        }
      }

      function updateEdge(key, value) {
        const e = selectedEdge();
        if (!e) return;
        pushHistory();
        if (key === 'id') {
          e.id = value;
          state.selected.id = value;
        } else {
          e[key] = value;
        }
        refreshEdgePreview();
      }

      function edgeConditionMode(edge) {
        if (!edge.condition || edge.condition === 'default') return 'default';
        return 'feel';
      }

      function feelCondition(edge) {
        return edgeConditionMode(edge) === 'feel' ? String(edge.condition || '') : '';
      }

      function edgeConditionLabel(edge) {
        if (String(edge.name || '').trim()) return edge.name.trim();
        const source = state.model.nodes.find(n => n.id === edge.from);
        if (source && isActivityKind(source.kind)) return '';
        if (!edge.condition || edge.condition === 'default') return '';
        return String(edge.condition || '');
      }

      function reorderGatewayOutgoingEdges(gatewayId, dragEdgeId, targetEdgeId) {
        const ordered = sortedOutgoingFrom(gatewayId).map(e => e.id);
        const fromIdx = ordered.indexOf(dragEdgeId);
        const toIdx = ordered.indexOf(targetEdgeId);
        if (fromIdx < 0 || toIdx < 0 || fromIdx === toIdx) return;
        ordered.splice(fromIdx, 1);
        ordered.splice(toIdx, 0, dragEdgeId);
        pushHistory();
        ordered.forEach((id, index) => {
          const edge = state.model.edges.find(e => e.id === id);
          if (edge) edge.priority = index;
        });
        renderAll();
      }

      function updateEdgeConditionMode(mode) {
        const e = selectedEdge();
        if (!e) return;
        pushHistory();
        if (mode === 'default') {
          e.condition = 'default';
        } else {
          e.condition = '${amount > 1000}';
        }
        renderAll();
      }

      function updateEdgeFeel(value) {
        const e = selectedEdge();
        if (!e) return;
        pushHistory();
        e.condition = value || 'default';
        refreshEdgePreview();
      }
