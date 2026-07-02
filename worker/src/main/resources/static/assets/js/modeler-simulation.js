      function showCompiledPlan() {
        if (!state.lastCompiledPlan) {
          message(t('message.compileFirst'));
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

      function resetSimulation(render = true) {
        state.simulation = null;
        if (render) {
          const log = $('debugLog');
          if (log) log.textContent = t('debug.waiting');
        }
        if (isSimulationCanvas()) renderCanvas();
      }

      function ensureSimulation() {
        if (state.simulation) return state.simulation;
        let input = {};
        try {
          input = JSON.parse($('debugInput').value || '{}');
        } catch (err) {
          $('debugLog').textContent = t('message.simInputInvalid', { error: err.message });
          return null;
        }
        const start = state.model.nodes.find(n => n.kind === 'startEvent') || state.model.nodes[0];
        state.simulation = {
          input,
          vars: { ...input },
          currentNodeId: start?.id,
          visited: [],
          done: false,
          log: []
        };
        state.runtimeHighlightNodeId = null;
        return state.simulation;
      }

      function simulateFlow() {
        resetSimulation(false);
        const sim = ensureSimulation();
        if (!sim) return;
        let guard = 0;
        while (!sim.done && guard < 100) {
          runSimulationStep(sim);
          guard += 1;
        }
        if (guard >= 100) sim.log.push(t('message.simStoppedSteps'));
        renderSimulationLog();
      }

      function stepSimulation() {
        const sim = ensureSimulation();
        if (!sim || sim.done) return renderSimulationLog();
        runSimulationStep(sim);
        renderSimulationLog();
      }

      function runSimulationStep(sim) {
        const current = state.model.nodes.find(n => n.id === sim.currentNodeId);
        if (!current) {
          sim.done = true;
          sim.log.push(t('message.simNodeMissing'));
          return;
        }
        sim.visited.push(current.id);
        state.runtimeHighlightNodeId = current.id;
        sim.log.push(t('message.simEntered', {
          id: current.id,
          kind: current.kind,
          name: current.name || ''
        }));
        applySimulatedNodeEffects(current, sim);
        if (current.kind === 'endEvent') {
          sim.done = true;
          sim.log.push(t('message.simFinished'));
          renderCanvas();
          return;
        }
        const next = selectSimulatedNext(current, sim);
        if (!next) {
          sim.done = true;
          sim.log.push(t('message.simNoOutgoing'));
        } else {
          sim.log.push(t('message.simSelectedEdge', { edgeId: next.id, target: next.to }));
          sim.currentNodeId = next.to;
        }
        renderCanvas();
      }

      function applySimulatedNodeEffects(node, sim) {
        if (node.kind === 'scriptTask' && node.config?.script) {
          const match = String(node.config.script).match(/^(\w+)\s*:=\s*(\w+)\s*\+\s*(\d+)$/);
          if (match) {
            sim.vars[match[1]] = Number(resolveSimValue(match[2], sim)) + Number(match[3]);
            sim.log.push(t('message.simScriptUpdate', { name: match[1], value: sim.vars[match[1]] }));
          }
        } else if (node.kind === 'userTask' || node.kind === 'humanTask' || node.kind === 'manualTask') {
          const mode = node.config?.flowFoundryHumanTask?.mode || (node.kind === 'manualTask' ? 'offline' : 'managed');
          if (mode === 'offline') {
            sim.log.push(t('message.simHumanTaskOffline'));
          } else {
            sim.log.push(t('message.simHumanTask'));
          }
        } else if (node.kind === 'businessRuleTask') {
          sim.vars.matched = true;
          sim.log.push(t('message.simDmn', { decisionRef: node.decisionRef || 'decision' }));
        } else if (node.activityType) {
          sim.log.push(t('message.simActivity', { activityType: node.activityType }));
        }
      }

      function selectSimulatedNext(node, sim) {
        const outgoing = state.model.edges.filter(e => e.from === node.id);
        if (outgoing.length === 0) return null;
        const conditional = outgoing.filter(e => e.condition && e.condition !== 'default');
        const matched = conditional.find(e => evaluateSimCondition(e.condition, sim));
        if (matched) return matched;
        return outgoing.find(e => e.condition === 'default') || outgoing[0];
      }

      function evaluateSimCondition(condition, sim) {
        if (!condition || condition === 'default') return true;
        if (typeof condition === 'object') {
          sim.log.push(t('message.simDmnCondition', { decisionRef: condition.decisionRef || 'decision' }));
          return true;
        }
        const expression = String(condition).replace(/^\$\{|\}$/g, '').trim();
        if (expression.includes(' or ')) return expression.split(/\s+or\s+/).some(part => evaluateSimCondition('${' + part + '}', sim));
        if (expression.includes(' and ')) return expression.split(/\s+and\s+/).every(part => evaluateSimCondition('${' + part + '}', sim));
        const match = expression.match(/^([\w.]+)\s*(==|=|!=|>=|<=|>|<)\s*(.+)$/);
        if (!match) return Boolean(resolveSimValue(expression, sim));
        const left = resolveSimValue(match[1], sim);
        const right = parseSimLiteral(match[3], sim);
        switch (match[2]) {
          case '=':
          case '==': return left === right;
          case '!=': return left !== right;
          case '>': return Number(left) > Number(right);
          case '<': return Number(left) < Number(right);
          case '>=': return Number(left) >= Number(right);
          case '<=': return Number(left) <= Number(right);
          default: return false;
        }
      }

      function resolveSimValue(path, sim) {
        const key = String(path).replace(/^vars\.|^input\./, '');
        if (Object.prototype.hasOwnProperty.call(sim.vars, key)) return sim.vars[key];
        return sim.input[key];
      }

      function parseSimLiteral(value, sim) {
        const text = String(value).trim().replace(/^['"]|['"]$/g, '');
        if (text === 'true') return true;
        if (text === 'false') return false;
        if (!Number.isNaN(Number(text))) return Number(text);
        return resolveSimValue(text, sim) ?? text;
      }

      function renderSimulationLog() {
        const sim = state.simulation;
        const log = $('debugLog');
        if (!log) return;
        log.textContent = sim
          ? `${sim.log.join('\n')}\n\n${t('message.simVarsSnapshot', { vars: pretty(sim.vars) })}`
          : t('debug.waiting');
      }

      function highlightRuntimeNode(nodeId) {
        state.runtimeHighlightNodeId = nodeId || null;
        if (isSimulationCanvas()) renderCanvas();
      }
