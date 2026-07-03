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
        return {
          content: $('canvasContent'),
          edges: $('edges'),
          canvas: $('canvas'),
          readonly: false
        };
      }

      function highlightRuntimeNode(nodeId) {
        state.runtimeHighlightNodeId = nodeId || null;
        if (state.currentView === 'modeler') renderCanvas();
      }

      function isSimulationCanvas() {
        return false;
      }
