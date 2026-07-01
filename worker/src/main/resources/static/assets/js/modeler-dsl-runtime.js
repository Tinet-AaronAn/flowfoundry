      function buildDsl() {
        assertParticipantContainment();
        const runtimeNodes = state.model.nodes.filter(isRuntimeNode);
        const runtimeNodeIds = new Set(runtimeNodes.map(n => n.id));
        return {
          dslVersion: '1.0',
          flow: { id: state.model.id, name: state.model.name, version: state.activeVersion || '1.0.0' },
          inputs: { campaignId: { type: 'string', required: true } },
          variables: {},
          nodes: runtimeNodes.map(n => ({
              id: n.id,
              kind: n.kind,
              activityType: n.activityType,
              taskQueue: n.taskQueue,
              timeout: n.timeout,
              maxAttempts: n.maxAttempts,
              decisionRef: n.decisionRef,
              decisionVersion: n.decisionVersion,
              inputArgs: n.inputArgs,
              inputMapping: n.inputMapping,
              outputMapping: n.outputMapping,
              config: runtimeConfig(n)
            })),
          edges: state.model.edges
            .filter(e => runtimeNodeIds.has(e.from) && runtimeNodeIds.has(e.to))
            .map(e => ({ from: e.from, to: e.to, condition: e.condition || 'default' }))
        };
      }

      function isRuntimeNode(n) {
        return !isDefaultSubProcessBoundaryNode(n) && !['textAnnotation','participant','subProcess'].includes(n.kind);
      }

      function runtimeConfig(n) {
        const config = { ...(n.config || {}) };
        const participant = participantById(n.participantId);
        if (participant) {
          config.flowFoundryParticipant = {
            participantId: participant.id,
            participantRef: participant.config?.participantRef || '',
            name: participant.name || participant.id
          };
        }
        if (n.kind === 'intermediateCatchEvent' && config.timerDefinition?.value) {
          config.duration = normalizeTimerDuration(config.timerDefinition.value);
        }
        return config;
      }

      function normalizeTimerDuration(value) {
        const text = String(value || '').trim();
        if (text.startsWith('${')) return '1m';
        if (/^\d+[smh]$/.test(text)) return text;
        if (/^\d+M$/.test(text)) return text.toLowerCase();
        return text || '1m';
      }

      function buildBpmnJson() {
        return {
          id: state.model.id,
          name: state.model.name,
          targetNamespace: state.model.targetNamespace,
          processes: [{
            ...state.model.process,
            flowElements: [
              ...state.model.nodes.map(n => ({
                id: n.id,
                name: n.name,
                type: n.kind,
                documentation: n.documentation,
                extensionElements: extensionElements(n),
                scriptFormat: n.config?.scriptFormat,
                script: n.config?.script,
                timerDefinition: n.config?.timerDefinition
              })),
              ...state.model.edges.map(e => ({
                id: e.id,
                type: 'sequenceFlow',
                sourceRef: e.from,
                targetRef: e.to,
                conditionExpression: e.condition === 'default' ? undefined : e.condition
              }))
            ]
          }]
        };
      }

      function extensionElements(n) {
        const result = {};
        if (n.config?.flowFoundryTaskDefinition) result.flowFoundryTaskDefinition = n.config.flowFoundryTaskDefinition;
        if (n.config?.flowFoundryAssignmentDefinition) result.flowFoundryAssignmentDefinition = n.config.flowFoundryAssignmentDefinition;
        if (n.decisionRef) result.flowFoundryDecisionDefinition = { decisionRef: n.decisionRef, decisionVersion: n.decisionVersion };
        if (n.kind === 'participant') result.flowFoundryParticipant = { participantRef: n.config?.participantRef || '' };
        if (n.participantId) {
          const participant = participantById(n.participantId);
          result.flowFoundryParticipantRef = {
            participantId: n.participantId,
            participantRef: participant?.config?.participantRef || '',
            name: participant?.name || n.participantId
          };
        }
        return Object.keys(result).length ? result : undefined;
      }

      async function compileFlow() {
        try {
          const res = await post('/api/flows/compile', buildDsl());
          showJsonValue('Execution Plan', res);
          message('编译成功：Flow DSL 已转换为 Execution Plan');
        } catch (err) {
          message('编译失败：' + err.message);
        }
      }

      async function runFlow() {
        try {
          const input = JSON.parse($('runInput').value || '{}');
          const res = await post('/api/flows/run', { flow: buildDsl(), input });
          $('workflowId').value = res.workflowId;
          showJsonValue('Run Result', res);
          message(`已启动 Temporal Workflow：${res.workflowId}`);
        } catch (err) {
          message('运行失败：' + err.message);
        }
      }

      async function queryState() {
        const id = $('workflowId').value;
        if (!id) return message('请先运行流程或输入 Workflow ID');
        try {
          const res = await fetch(`/api/flows/runs/${encodeURIComponent(id)}`);
          showJsonValue('Workflow State', await res.json());
        } catch (err) {
          message('查询失败：' + err.message);
        }
      }

      async function completeHumanTask() {
        const id = $('workflowId').value;
        if (!id) return message('请先输入 Workflow ID');
        const nodeId = prompt('Human Task Node ID', 'Task_ManualConfirm');
        if (!nodeId) return;
        await post(`/api/flows/runs/${encodeURIComponent(id)}/human-task`, {
          nodeId,
          outcome: 'approved',
          variables: { approved: true }
        });
        message(`已发送人工任务完成 Signal：${nodeId}`);
      }

      function resetSimulation(render = true) {
        state.simulation = null;
        if (render) $('debugLog').textContent = '等待模拟运行';
      }

      function ensureSimulation() {
        if (state.simulation) return state.simulation;
        let input = {};
        try {
          input = JSON.parse($('debugInput').value || '{}');
        } catch (err) {
          $('debugLog').textContent = '模拟输入 JSON 格式错误：' + err.message;
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
        return state.simulation;
      }

      function simulateFlow() {
        const sim = ensureSimulation();
        if (!sim) return;
        let guard = 0;
        while (!sim.done && guard < 100) {
          runSimulationStep(sim);
          guard += 1;
        }
        if (guard >= 100) sim.log.push('停止：超过 100 步，可能存在循环或缺少终止条件。');
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
          sim.log.push('停止：找不到当前节点。');
          return;
        }
        sim.visited.push(current.id);
        state.selected = { type: 'node', id: current.id };
        sim.log.push(`进入 ${current.id} (${current.kind}) - ${current.name || ''}`);
        applySimulatedNodeEffects(current, sim);
        if (current.kind === 'endEvent') {
          sim.done = true;
          sim.log.push('流程结束。');
          renderCanvas();
          return;
        }
        const next = selectSimulatedNext(current, sim);
        if (!next) {
          sim.done = true;
          sim.log.push('停止：没有可走的出边。');
        } else {
          sim.log.push(`选择出边 ${next.id} -> ${next.to}`);
          sim.currentNodeId = next.to;
        }
        renderCanvas();
      }

      function applySimulatedNodeEffects(node, sim) {
        if (node.kind === 'scriptTask' && node.config?.script) {
          const match = String(node.config.script).match(/^(\w+)\s*:=\s*(\w+)\s*\+\s*(\d+)$/);
          if (match) {
            sim.vars[match[1]] = Number(resolveSimValue(match[2], sim)) + Number(match[3]);
            sim.log.push(`脚本更新变量：${match[1]} = ${sim.vars[match[1]]}`);
          }
        } else if (node.kind === 'userTask') {
          sim.log.push('模拟人工任务：自动视为 approved。');
        } else if (node.kind === 'businessRuleTask') {
          sim.vars.matched = true;
          sim.log.push(`模拟 DMN：${node.decisionRef || 'decision'} -> matched=true`);
        } else if (node.activityType) {
          sim.log.push(`模拟 Activity：${node.activityType}`);
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
          sim.log.push(`模拟 DMN 条件：${condition.decisionRef || 'decision'} -> true`);
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
        $('debugLog').textContent = sim
          ? `${sim.log.join('\n')}\n\n变量快照：${pretty(sim.vars)}`
          : '等待模拟运行';
      }
