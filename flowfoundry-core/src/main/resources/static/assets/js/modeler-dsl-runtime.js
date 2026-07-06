      function resolveTaskHeaders(node) {
        const fromConfig = node?.config?.taskHeaders;
        const fromLegacy = node?.headers;
        const configHeaders =
          fromConfig && typeof fromConfig === 'object' && !Array.isArray(fromConfig) ? fromConfig : null;
        const legacyHeaders =
          fromLegacy && typeof fromLegacy === 'object' && !Array.isArray(fromLegacy) ? fromLegacy : null;
        if (configHeaders && Object.keys(configHeaders).length > 0) {
          return legacyHeaders && Object.keys(legacyHeaders).length > 0
            ? { ...legacyHeaders, ...configHeaders }
            : { ...configHeaders };
        }
        if (legacyHeaders && Object.keys(legacyHeaders).length > 0) {
          return { ...legacyHeaders };
        }
        return null;
      }

      function migrateNodeTaskHeaders(node) {
        if (!node) return;
        const headers = resolveTaskHeaders(node);
        if (headers && Object.keys(headers).length > 0) {
          node.config = { ...(node.config || {}), taskHeaders: headers };
        } else if (node.config?.taskHeaders) {
          const nextConfig = { ...(node.config || {}) };
          delete nextConfig.taskHeaders;
          node.config = nextConfig;
        }
        if (node.headers !== undefined) delete node.headers;
      }

      function mergeTaskHeadersConfig(config, node) {
        const headers = resolveTaskHeaders(node);
        if (headers && Object.keys(headers).length > 0) {
          config.taskHeaders = headers;
        }
        return config;
      }

      function buildDsl() {
        assertParticipantContainment();
        validateStartEventUniqueness(state.model);
        assertNoGenericTaskNodes(state.model);
        return buildDslForModel(state.model, state.activeVersion || '1.0.0', new Set([state.model.id]));
      }

      function isGenericTaskKind(kind) {
        return kind === 'task' || kind === 'genericTask';
      }

      function assertNoGenericTaskNodes(model) {
        const genericNodes = model.nodes.filter(n => isRuntimeNode(n) && isGenericTaskKind(n.kind));
        if (!genericNodes.length) return;
        const names = genericNodes.map(n => n.name || n.id).join(', ');
        throw new Error(t('message.genericTaskNotCompilable', { names }));
      }

      function buildDslForModel(model, version = '1.0.0', seenWorkflowIds = new Set()) {
        assertNoGenericTaskNodes(model);
        const runtimeNodes = model.nodes.filter(isRuntimeNode);
        const runtimeNodeIds = new Set(runtimeNodes.map(n => n.id));
        return {
          dslVersion: '1.0',
          flow: {
            id: model.id,
            name: model.name,
            version,
            edgeRouting: model.process?.edgeRouting || 'orthogonal'
          },
          inputs: { campaignId: { type: 'string', required: true } },
          variables: {},
          nodes: runtimeNodes.map(n => ({
              id: n.id,
              name: n.name || n.id,
              canvasKind: n.kind,
              kind: executionNodeKind(n.kind),
              activityType: resolveActivityType(n),
              taskQueue: n.taskQueue,
              timeout: n.timeout,
              maxAttempts: n.maxAttempts,
              scriptCodeId: n.scriptCodeId,
              scriptVersion: n.scriptVersion,
              scriptName: n.scriptName,
              inputArgs: n.inputArgs,
              inputMapping: n.inputMapping,
              inputMappingMode: n.inputMappingMode,
              outputMapping: n.outputMapping,
              config: runtimeConfig(n, model, seenWorkflowIds)
            })),
          edges: model.edges
            .filter(e => runtimeNodeIds.has(e.from) && runtimeNodeIds.has(e.to))
            .map(e => {
              const item = { from: e.from, to: e.to, condition: e.condition || 'default' };
              if (e.priority != null) item.priority = e.priority;
              return item;
            })
        };
      }

      function isRuntimeNode(n) {
        return !['textAnnotation','participant','subProcess'].includes(n.kind);
      }

      function executionNodeKind(kind) {
        switch (kind) {
          case 'startEvent':
          case 'start': // legacy
            return 'START';
          case 'endEvent':
          case 'end': // legacy
            return 'END';
          case 'serviceTask':
          case 'scriptTask':
          case 'activity': // legacy
            return 'ACTIVITY';
          case 'workflow':
          case 'childWorkflow': // legacy
          case 'callActivity': // legacy
            return 'CHILD_WORKFLOW';
          case 'humanTask':
          case 'userTask':
            return 'ACTIVITY';
          case 'exclusiveGateway':
          case 'inclusiveGateway':
          case 'parallelGateway':
          case 'eventBasedGateway':
            return 'GATEWAY';
          case 'intermediateEvent':
          case 'intermediateCatchEvent': // legacy
          case 'timerEvent': // legacy
          case 'boundaryEvent': // legacy
          case 'timer': // legacy
            return 'INTERMEDIATE_EVENT';
          default:
            return kind;
        }
      }

      function canvasGatewayKind(kind) {
        switch (kind) {
          case 'exclusiveGateway': return 'exclusive';
          case 'parallelGateway': return 'parallel';
          case 'inclusiveGateway': return 'inclusive';
          case 'eventBasedGateway': return 'eventBased';
          default: return null;
        }
      }

      function isIntermediateEventCanvasKind(kind) {
        return [
          'intermediateEvent',
          'intermediateCatchEvent',
          'timerEvent',
          'boundaryEvent',
          'timer'
        ].includes(kind);
      }

      function resolveEventSubtype(config = {}) {
        if (config.subtype) return config.subtype;
        if (config.timerDefinition?.value) return 'timer';
        return 'timer';
      }

      function resolveActivityType(node) {
        if (node.activityType) return node.activityType;
        if (node.kind === 'scriptTask') return 'script-runtime';
        if (node.kind === 'humanTask' || node.kind === 'userTask') return 'human-task';
        return node.activityType;
      }

      function runtimeConfig(n, model = state.model, seenWorkflowIds = new Set()) {
        const config = { ...(n.config || {}) };
        const participant = participantByIdInModel(n.participantId, model);
        if (participant) {
          config.flowFoundryParticipant = {
            participantId: participant.id,
            participantRef: participant.config?.participantRef || '',
            name: participant.name || participant.id
          };
        }
        const gatewayKind = canvasGatewayKind(n.kind);
        if (gatewayKind) {
          config.gatewayKind = gatewayKind;
        }
        if (isIntermediateEventCanvasKind(n.kind)) {
          config.eventSubtype = resolveEventSubtype(config);
          if (config.timerDefinition?.value) {
            config.duration = normalizeTimerDuration(config.timerDefinition.value);
          }
        }
        if (n.kind === 'workflow') {
          config.flowFoundryChildWorkflow = {
            childWorkflowId: config.childWorkflowId || '',
            childWorkflowVersion: config.childWorkflowVersion || 'latest',
            name: config.childWorkflowName || ''
          };
          const childDefinition = childWorkflowDefinition(n, seenWorkflowIds);
          if (childDefinition) config.childWorkflowDefinition = childDefinition;
        }
        if (n.kind === 'humanTask' || n.kind === 'userTask') {
          config.flowFoundryHumanTask = {
            mode: n.config?.flowFoundryHumanTask?.mode || 'managed',
          };
          config.nodeId = n.id;
        }
        const loop = buildFlowFoundryLoop(n);
        if (loop) {
          config.flowFoundryLoop = loop;
        }
        mergeTaskHeadersConfig(config, n);
        return config;
      }

      function buildFlowFoundryLoop(n) {
        if (!n.loop || n.loop === 'none') return null;
        const stored = n.config?.flowFoundryLoop || {};
        const mode = n.loop === 'standardLoop' ? 'standard' : 'multiInstance';
        const loop = {
          mode,
          maxIterations: stored.maxIterations ?? 100,
          sequential: stored.sequential !== false
        };
        if (mode === 'standard') {
          loop.condition = stored.condition || '';
          loop.iterationVar = stored.iterationVar || 'loop.iteration';
        } else {
          loop.collection = stored.collection || '';
          loop.elementVar = stored.elementVar || 'loop.item';
          loop.indexVar = stored.indexVar || 'loop.index';
        }
        return loop;
      }

      function participantByIdInModel(id, model) {
        return model.nodes.find(node => node.id === id && isParticipantContainer(node));
      }

      function childWorkflowDefinition(node, seenWorkflowIds) {
        const workflowId = node.config?.childWorkflowId;
        if (!workflowId || seenWorkflowIds.has(workflowId)) return null;
        const workflow = state.workflows.find(w => w.id === workflowId);
        if (!workflow) return null;
        const version =
          workflow.versions?.find(v => v.version === node.config?.childWorkflowVersion) ||
          workflow.versions?.find(v => v.version === workflow.version) ||
          workflow.versions?.[workflow.versions.length - 1];
        if (!version?.model) return null;
        const nextSeen = new Set(seenWorkflowIds);
        nextSeen.add(workflowId);
        return buildDslForModel(version.model, version.version, nextSeen);
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
        if (n.config?.flowFoundryHumanTask) result.flowFoundryHumanTask = n.config.flowFoundryHumanTask;
        if (n.config?.taskHeaders && Object.keys(n.config.taskHeaders).length) {
          result.flowFoundryTaskHeaders = n.config.taskHeaders;
        }
        if (n.scriptCodeId) result.flowFoundryScriptDefinition = { scriptCodeId: n.scriptCodeId, scriptVersion: n.scriptVersion, scriptName: n.scriptName };
        if (n.kind === 'workflow') {
          result.flowFoundryChildWorkflow = {
            childWorkflowId: n.config?.childWorkflowId || '',
            childWorkflowVersion: n.config?.childWorkflowVersion || 'latest',
            name: n.config?.childWorkflowName || ''
          };
        }
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
          state.lastCompiledPlan = res;
          updateCompiledPlanButton();
          if (isRunStatusDialogOpen()) updateRunStatusCompiledPlan();
          message(t('message.compileSuccess'));
        } catch (err) {
          message(t('message.compileFailed', { error: err.message }), 'error');
        }
      }

      async function runFlow(input) {
        try {
          const parsed = input ?? JSON.parse($('runInput').value || '{}');
          const res = await postFlowRun({ flow: buildDsl(), input: parsed });
          setActiveWorkflowRunId(res.workflowId);
          if ($('runInput')) $('runInput').value = pretty(parsed);
          if (res.executionPlan) state.lastCompiledPlan = res.executionPlan;
          state.lastRunResult = res;
          recordFlowRun({ workflowId: res.workflowId, input: parsed, runSource: res.runSource || 'web-modeler' });
          highlightRuntimeNode(null);
          startRuntimePolling();
          await queryRunState(res.workflowId, { silent: true, skipJsonPanel: true });
          message(t('message.runSuccess', { workflowId: res.workflowId }));
        } catch (err) {
          message(t('message.runFailed', { error: err.message }), 'error');
        }
      }

      async function queryState() {
        return queryRunState(activeWorkflowRunId());
      }

      async function completeHumanTask() {
        const id = activeWorkflowRunId();
        if (!id) return message(t('message.queryWorkflowRequired'));
        let snapshot = state.runtimeSnapshot;
        if (!snapshot || snapshot.workflowId !== id) {
          snapshot = await queryRunState(id, { silent: true, skipJsonPanel: true });
        }
        snapshot = snapshot || state.runtimeSnapshot;
        const humanTasks = resolveHumanTaskOptions(snapshot);
        if (!humanTasks.length) {
          return message(t('message.noHumanTasks'), 'warning');
        }
        const isWaiting = String(snapshot?.status || '').toUpperCase() === 'WAITING_HUMAN_TASK';
        const waitingId = snapshot?.waitingHumanTaskNodeId || humanTasks.find(task => task.waiting)?.nodeId || null;
        if (!isWaiting || !waitingId) {
          return message(t('message.noWaitingHumanTask'), 'warning');
        }
        const nodeId = await showAppChoiceDialog({
          title: t('prompt.selectHumanTask'),
          message: t('prompt.selectHumanTaskHelp'),
          options: humanTasks.map(task => ({
            value: task.nodeId,
            label: task.name || task.nodeId,
            hint: task.nodeId,
            badge: task.waiting ? t('runtime.waiting') : '',
            disabled: task.nodeId !== waitingId,
            selected: task.nodeId === waitingId
          }))
        });
        if (!nodeId) return;
        try {
          await post(`/api/flows/runs/${encodeURIComponent(id)}/human-task`, {
            nodeId,
            outcome: 'approved',
            variables: { approved: true }
          });
          message(t('message.humanTaskSignalSent', { nodeId }));
          await queryRunState(id, { silent: true, skipJsonPanel: true });
          startRuntimePolling();
        } catch (err) {
          message(t('message.humanTaskFailed', { error: err.message }), 'error');
        }
      }
