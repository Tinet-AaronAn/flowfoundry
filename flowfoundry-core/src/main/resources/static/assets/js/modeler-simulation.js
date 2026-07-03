      function showCompiledPlan() {
        if (!state.lastCompiledPlan) {
          message(t('message.compileFirst'), 'warning');
          return;
        }
        showJsonValue(t('json.compiledPlanTitle'), state.lastCompiledPlan);
      }

      function updateCompiledPlanButton() {
        const btn = $('viewCompiledPlanBtn');
        if (btn) btn.disabled = !state.lastCompiledPlan;
      }

      function activeCanvasElements() {
        const sim = state.currentView === 'simulation';
        return {
          content: $(sim ? 'simulationCanvasContent' : 'canvasContent'),
          edges: $(sim ? 'simulationEdges' : 'edges'),
          canvas: $(sim ? 'simulationCanvas' : 'canvas'),
          readonly: sim
        };
      }

      function isSimulationCanvas() {
        return state.currentView === 'simulation';
      }

      function fitSimulationView() {
        const canvas = $('simulationCanvas');
        const content = $('simulationCanvasContent');
        if (!canvas || !content) return;
        const bounds = modelContentBounds();
        const rect = canvas.getBoundingClientRect();
        if (!rect.width || !rect.height) return;
        const scale = Math.min(rect.width / bounds.width, rect.height / bounds.height, 1);
        const panX = (rect.width - bounds.width * scale) / 2 - bounds.left * scale;
        const panY = (rect.height - bounds.height * scale) / 2 - bounds.top * scale;
        content.style.transform = `translate(${panX}px, ${panY}px) scale(${scale})`;
        content.style.transformOrigin = '0 0';
      }

      function updateSimulationHeader() {
        const badge = $('simulationWorkflowBadge');
        if (!badge) return;
        badge.textContent = state.model?.name ? `${state.model.name} · v${state.activeVersion || '1.0.0'}` : '';
      }

      function highlightRuntimeNode(nodeId) {
        state.runtimeHighlightNodeId = nodeId || null;
        if (isSimulationCanvas()) renderCanvas();
      }
