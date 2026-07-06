      const paletteGroups = [
        {
          groupKey: 'palette.group.events',
          items: [
            { kind: 'startEvent', label: 'Start Event', basic: true },
            { kind: 'endEvent', label: 'End Event', basic: true },
            { kind: 'intermediateEvent', label: 'Intermediate Event', basic: true, config: { timerDefinition: { type: 'duration', value: '1m' }, subtype: 'timer' } }
          ]
        },
        {
          groupKey: 'palette.group.activities',
          items: [
            { kind: 'task', label: 'Generic Task', basic: true },
            { kind: 'serviceTask', label: 'Service Task', basic: true },
            { kind: 'humanTask', label: 'Human Task', basic: true, activityType: 'human-task', config: { flowFoundryHumanTask: { mode: 'managed' }, flowFoundryAssignmentDefinition: { candidateGroups: 'operator' } } },
            { kind: 'scriptTask', label: 'Script Task', basic: true, activityType: 'script-runtime', scriptCodeId: 'demo-script', scriptVersion: '1' }
          ]
        },
        {
          groupKey: 'palette.group.childWorkflows',
          items: [
            { kind: 'workflow', label: 'Child Workflow', basic: true, config: { childWorkflowId: '', childWorkflowVersion: '1.0.0' } }
          ]
        },
        {
          groupKey: 'palette.group.gateways',
          items: [
            { kind: 'exclusiveGateway', label: 'Exclusive Gateway', basic: true },
            { kind: 'parallelGateway', label: 'Parallel Gateway', basic: true },
            { kind: 'inclusiveGateway', label: 'Inclusive Gateway', basic: true },
            { kind: 'eventBasedGateway', label: 'Event-based Gateway', basic: true }
          ]
        },
        {
          groupKey: 'palette.group.structural',
          items: [
            { kind: 'subProcess', label: 'Sub-process', basic: true },
            { kind: 'participant', label: 'Participant', basic: true, config: { participantRef: 'business-team' } }
          ]
        },
        {
          groupKey: 'palette.group.annotations',
          items: [
            { kind: 'textAnnotation', label: 'Text Annotation', basic: true, documentation: 'Process notes / business remarks' }
          ]
        }
      ];

      const state = {
        activities: [],
        scriptCatalog: [],
        scriptVersionsByCodeId: {},
        scriptCatalogSource: 'stub',
        currentView: 'workflows',
        workflows: [],
        activeWorkflowId: null,
        activeVersion: '1.0.0',
        paletteDragItem: null,
        paletteCollapsed: false,
        navCollapsed: false,
        propertiesCollapsed: true,
        lastCompiledPlan: null,
        runtimeHighlightNodeId: null,
        runtimeSnapshot: null,
        lastRunResult: null,
        flowRuns: [],
        activeRunId: null,
        taskMorphMenuNodeId: null,
        suppressCanvasClick: false,
        isDragging: false,
        selected: { type: 'process', id: null },
        connectionSource: null,
        connectionSourceHandle: null,
        connectionDraftTarget: null,
        edgeReconnect: null,
        connectBuffer: [],
        scale: 1,
        minScale: 0.2,
        maxScale: 1.5,
        viewportLocked: false,
        minimap: null,
        panX: 0,
        panY: 0,
        history: [],
        future: [],
        model: {
          id: 'Definitions_UntitledWorkflow',
          name: 'Untitled Workflow',
          targetNamespace: 'https://flowfoundry.local/bpmn',
          process: {
            id: 'UntitledWorkflow',
            name: 'Untitled Workflow',
            isExecutable: true,
            edgeRouting: 'orthogonal'
          },
          nodes: [
            node('StartEvent', 'startEvent', 'Start', 120, 220),
            node('EndEvent', 'endEvent', 'End', 360, 220)
          ],
          edges: [
            edge('F_Start_End', 'StartEvent', 'EndEvent')
          ]
        }
      };

      function node(id, kind, name, x, y, extra = {}) {
        return {
          id,
          kind,
          name,
          x,
          y,
          documentation: '',
          emphasis: 'none',
          inputMapping: {},
          inputMappingMode: 'passthrough-unmapped',
          outputMapping: {},
          loop: 'none',
          config: {},
          ...extra
        };
      }

      function task(id, kind, name, activityType, x, y, retries) {
        return node(id, kind, name, x, y, {
          activityType,
          maxAttempts: retries,
          config: { flowFoundryTaskDefinition: { type: activityType, retries: String(retries) } }
        });
      }

      function human(id, name, candidateGroups, x, y, mode = 'managed') {
        return node(id, 'humanTask', name, x, y, {
          config: {
            flowFoundryHumanTask: { mode },
            flowFoundryAssignmentDefinition: { candidateGroups },
          },
        });
      }

      function isHumanTaskKind(kind) {
        return kind === 'humanTask';
      }

      function configureDefaultMappings() {}

      function edge(id, from, to, condition = 'default') {
        return { id, from, to, condition, name: '', documentation: '' };
      }

      function isGatewayKind(kind) {
        return String(kind || '').includes('Gateway');
      }

      function isActivityKind(kind) {
        return ['serviceTask', 'scriptTask', 'humanTask', 'userTask', 'activity'].includes(kind);
      }

      function isDefaultEdgeCondition(condition) {
        return condition == null || condition === '' || condition === 'default';
      }

      function sortedOutgoingFrom(nodeId) {
        const outgoing = state.model.edges.filter(e => e.from === nodeId);
        return outgoing.slice().sort((a, b) => {
          const pa = a.priority == null ? Number.MAX_SAFE_INTEGER : a.priority;
          const pb = b.priority == null ? Number.MAX_SAFE_INTEGER : b.priority;
          if (pa !== pb) return pa - pb;
          return state.model.edges.indexOf(a) - state.model.edges.indexOf(b);
        });
      }

      function ensureGatewayEdgePriorities(nodeId) {
        sortedOutgoingFrom(nodeId).forEach((edge, index) => {
          if (edge.priority == null) edge.priority = index;
        });
      }

      function nextOutgoingPriority(nodeId) {
        const outgoing = state.model.edges.filter(e => e.from === nodeId);
        if (outgoing.length === 0) return 0;
        const max = outgoing.reduce((acc, e) => Math.max(acc, e.priority == null ? -1 : e.priority), -1);
        return max + 1;
      }

      function $(id) { return document.getElementById(id); }
      function selectedNode() { return state.model.nodes.find(n => n.id === state.selected.id); }
      function selectedEdge() { return state.model.edges.find(e => e.id === state.selected.id); }
      function pretty(value) { return JSON.stringify(value, null, 2); }
