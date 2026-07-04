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

      function updateActivityType(value) {
        const n = selectedNode();
        if (!n) return;
        pushHistory();
        n.activityType = value;
        n.config = { ...(n.config || {}), flowFoundryTaskDefinition: { ...(n.config?.flowFoundryTaskDefinition || {}), type: value, retries: String(n.maxAttempts || 3) } };
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

      function updateTimer(key, value) {
        const n = selectedNode();
        pushHistory();
        n.config = { ...(n.config || {}), timerDefinition: { ...(n.config?.timerDefinition || {}), [key]: value } };
        refreshNodePreview(n);
        updateButtons();
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
