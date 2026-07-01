      function updateFlowMeta() {
        state.model.id = $('flowId').value;
        state.model.name = $('flowName').value;
        const workflow = activeWorkflow();
        if (workflow) {
          workflow.id = state.model.id;
          state.activeWorkflowId = state.model.id;
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
        renderProperties();
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
        renderAll();
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
        renderAll();
      }

      function updateConfig(key, value) {
        const n = selectedNode();
        pushHistory();
        n.config = { ...(n.config || {}), [key]: value };
        renderAll();
      }

      function updateParticipantOwner(value) {
        const n = selectedNode();
        if (!n || !isParticipantAssignable(n)) return;
        pushHistory();
        if (value) n.participantId = value;
        else delete n.participantId;
        renderAll();
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
        renderAll();
      }

      function updateTimer(key, value) {
        const n = selectedNode();
        pushHistory();
        n.config = { ...(n.config || {}), timerDefinition: { ...(n.config?.timerDefinition || {}), [key]: value } };
        renderAll();
      }

      function updateJsonNode(key, value) {
        const n = selectedNode();
        try {
          pushHistory();
          n[key] = JSON.parse(value || '{}');
          renderCanvas();
        } catch (err) {
          message('JSON 格式错误：' + err.message);
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
        renderAll();
      }

      function edgeConditionMode(edge) {
        if (!edge.condition || edge.condition === 'default') return 'default';
        if (typeof edge.condition === 'object' && String(edge.condition.type || edge.condition.language).toLowerCase() === 'dmn') return 'dmn';
        return 'feel';
      }

      function feelCondition(edge) {
        return edgeConditionMode(edge) === 'feel' ? String(edge.condition || '') : '';
      }

      function dmnCondition(edge) {
        return edgeConditionMode(edge) === 'dmn' ? edge.condition : {};
      }

      function edgeConditionLabel(edge) {
        if (edgeConditionMode(edge) === 'dmn') {
          return `DMN: ${edge.condition.decisionRef || 'decision'}`;
        }
        return String(edge.condition || '');
      }

      function updateEdgeConditionMode(mode) {
        const e = selectedEdge();
        if (!e) return;
        pushHistory();
        if (mode === 'default') {
          e.condition = 'default';
        } else if (mode === 'dmn') {
          e.condition = {
            type: 'dmn',
            decisionRef: 'demo-decision',
            decisionVersion: 'latest',
            resultPath: 'matched'
          };
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
        renderAll();
      }

      function updateEdgeDmn(key, value) {
        const e = selectedEdge();
        if (!e) return;
        pushHistory();
        e.condition = {
          type: 'dmn',
          ...dmnCondition(e),
          [key]: value
        };
        renderAll();
      }
