      const paletteGroups = [
        {
          groupKey: 'palette.group.events',
          items: [
            { kind: 'startEvent', label: 'Start Event', basic: true },
            { kind: 'endEvent', label: 'End Event', basic: true },
            { kind: 'intermediateCatchEvent', label: 'Intermediate Timer', basic: true, config: { timerDefinition: { type: 'duration', value: '1m' }, subtype: 'timer' } }
          ]
        },
        {
          groupKey: 'palette.group.activities',
          items: [
            { kind: 'task', label: 'Generic Task', basic: true },
            { kind: 'serviceTask', label: 'Service Task', basic: true },
            { kind: 'userTask', label: 'Human Task', basic: true, config: { flowFoundryHumanTask: { mode: 'managed' }, flowFoundryAssignmentDefinition: { candidateGroups: 'operator' } } },
            { kind: 'sendTask', label: 'Send Task', basic: true, activityType: 'send-message' },
            { kind: 'receiveTask', label: 'Receive Task', basic: true, config: { signalName: 'external-message-received' } },
            { kind: 'scriptTask', label: 'Script Task', basic: true, activityType: 'dmn-decision', decisionRef: 'demo-script', decisionVersion: '1.0.0' },
            { kind: 'workflow', label: 'Workflow', basic: true, config: { childWorkflowId: '', childWorkflowVersion: '1.0.0' } }
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
        currentView: 'workflows',
        workflows: [],
        activeWorkflowId: null,
        activeVersion: '1.0.0',
        paletteDragItem: null,
        paletteCollapsed: false,
        navCollapsed: false,
        propertiesCollapsed: true,
        lastCompiledPlan: null,
        simulation: null,
        runtimeHighlightNodeId: null,
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
          id: 'Definitions_MultiRoundOutboundScheduler',
          name: 'Multi-round Outbound Scheduler',
          targetNamespace: 'https://example.com/bpmn/outbound-scheduler',
          process: {
            id: 'MultiRoundOutboundScheduler',
            name: 'Multi-round Outbound Scheduler',
            isExecutable: true,
            edgeRouting: 'orthogonal'
          },
          nodes: [
            node('StartEvent', 'startEvent', 'Submit outbound task', 80, 220),
            task('Task_LoadCampaign', 'serviceTask', 'Load campaign config', 'load-campaign', 160, 198, 3),
            task('Task_PrepareRound', 'serviceTask', 'Prepare call list', 'prepare-call-round', 330, 198, 3),
            node('Gateway_ChannelStrategy', 'exclusiveGateway', 'Channel strategy?', 520, 210),
            task('Task_SplitBatch', 'serviceTask', 'Split outbound batches', 'prepare-call-round', 610, 80, 3),
            human('Task_ManualConfirm', 'userTask', 'Manual list confirmation', 'call-supervisor', 610, 340),
            task('Task_ExecuteRound', 'serviceTask', 'Execute outbound round', 'execute-call-round', 780, 198, 5),
            task('Task_WaitCompletion', 'serviceTask', 'Wait for round result', 'wait-round-completion', 950, 198, 3),
            task('Task_AggregateResult', 'serviceTask', 'Aggregate round result', 'aggregate-round-results', 1120, 198, 3),
            node('Gateway_RiskCheck', 'exclusiveGateway', 'Trigger risk review?', 1290, 210),
            human('Task_RiskReview', 'userTask', 'Operations strategy review', 'operation-manager', 1380, 90),
            task('Task_EvaluateNextRound', 'serviceTask', 'Evaluate next round', 'evaluate-next-round', 1540, 198, 3),
            node('Gateway_Continue', 'exclusiveGateway', 'Continue next round?', 1730, 210),
            node('Timer_BetweenRounds', 'intermediateCatchEvent', 'Round interval', 1850, 330, {
              config: { subtype: 'timer', timerDefinition: { type: 'duration', value: '1m' } }
            }),
            node('Task_IncRound', 'scriptTask', 'Increment round', 1020, 360, {
              activityType: 'dmn-decision',
              decisionRef: 'increment-round',
              decisionVersion: '1.0.0'
            }),
            task('Task_Finalize', 'serviceTask', 'Finalize and generate report', 'finalize-campaign', 1830, 198, 3),
            node('EndEvent', 'endEvent', 'Campaign complete', 2010, 220)
          ],
          edges: [
            edge('F_Start_Load', 'StartEvent', 'Task_LoadCampaign'),
            edge('F_Load_Prepare', 'Task_LoadCampaign', 'Task_PrepareRound'),
            edge('F_Prepare_ChannelGw', 'Task_PrepareRound', 'Gateway_ChannelStrategy'),
            edge('F_Channel_Normal', 'Gateway_ChannelStrategy', 'Task_ExecuteRound', 'default'),
            edge('F_Channel_SplitBatch', 'Gateway_ChannelStrategy', 'Task_SplitBatch', '${remainingContacts > 500}'),
            edge('F_Channel_Manual', 'Gateway_ChannelStrategy', 'Task_ManualConfirm', '${vipSegment == true}'),
            edge('F_Split_Execute', 'Task_SplitBatch', 'Task_ExecuteRound'),
            edge('F_Manual_Execute', 'Task_ManualConfirm', 'Task_ExecuteRound'),
            edge('F_Execute_Wait', 'Task_ExecuteRound', 'Task_WaitCompletion'),
            edge('F_Wait_Aggregate', 'Task_WaitCompletion', 'Task_AggregateResult'),
            edge('F_Aggregate_RiskGw', 'Task_AggregateResult', 'Gateway_RiskCheck'),
            edge('F_Risk_Review', 'Gateway_RiskCheck', 'Task_RiskReview', '${complaintRate > 0.03 or failedRate > 0.4}'),
            edge('F_Risk_No', 'Gateway_RiskCheck', 'Task_EvaluateNextRound', 'default'),
            edge('F_Risk_Evaluate', 'Task_RiskReview', 'Task_EvaluateNextRound'),
            edge('F_Evaluate_ContinueGw', 'Task_EvaluateNextRound', 'Gateway_Continue'),
            edge('F_Continue_Timer', 'Gateway_Continue', 'Timer_BetweenRounds', '${continueNextRound == true}'),
            edge('F_Stop_Finalize', 'Gateway_Continue', 'Task_Finalize', 'default'),
            edge('F_Timer_IncRound', 'Timer_BetweenRounds', 'Task_IncRound'),
            edge('F_Inc_Prepare', 'Task_IncRound', 'Task_PrepareRound'),
            edge('F_Finalize_End', 'Task_Finalize', 'EndEvent')
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
          outputMapping: {},
          headers: {},
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

      function human(id, kind, name, candidateGroups, x, y) {
        return node(id, kind, name, x, y, {
          config: {
            flowFoundryHumanTask: { mode: 'managed' },
            flowFoundryAssignmentDefinition: { candidateGroups },
          },
        });
      }

      function configureDefaultMappings() {
        const mappings = {
          Task_LoadCampaign: {
            inputMapping: { campaignId: '$.input.campaignId' },
            outputMapping: {
              maxRounds: '$.input.maxRounds',
              roundIntervalMinutes: '$.input.roundIntervalMinutes',
              roundNumber: '$.input.roundNumber',
              remainingContacts: '$.input.remainingContacts'
            }
          },
          Task_PrepareRound: {
            inputMapping: { campaignId: '$.input.campaignId', roundNumber: '$.vars.roundNumber' },
            outputMapping: { batchId: '$.lastResult.batchId', contactCount: '$.lastResult.contactCount' }
          },
          Task_SplitBatch: {
            inputMapping: { campaignId: '$.input.campaignId', roundNumber: '$.vars.roundNumber' },
            outputMapping: { batchId: '$.lastResult.batchId', contactCount: '$.lastResult.contactCount' }
          },
          Task_ExecuteRound: {
            inputMapping: { campaignId: '$.input.campaignId', roundNumber: '$.vars.roundNumber', batchId: '$.vars.batchId' },
            outputMapping: { dialerTaskId: '$.lastResult.dialerTaskId', submittedCount: '$.lastResult.submittedCount' }
          },
          Task_WaitCompletion: {
            inputMapping: { campaignId: '$.input.campaignId', roundNumber: '$.vars.roundNumber', dialerTaskId: '$.vars.dialerTaskId' }
          },
          Task_AggregateResult: {
            inputMapping: { campaignId: '$.input.campaignId', roundNumber: '$.vars.roundNumber' },
            outputMapping: {}
          },
          Task_EvaluateNextRound: {
            inputMapping: {
              campaignId: '$.input.campaignId',
              roundNumber: '$.vars.roundNumber',
              remainingContacts: '$.vars.remainingContacts',
              maxRounds: '$.vars.maxRounds'
            },
            outputMapping: { continueNextRound: '$.lastResult.continueNextRound' }
          },
          Task_Finalize: {
            inputMapping: { campaignId: '$.input.campaignId', totalRoundsExecuted: '$.vars.roundNumber' },
            outputMapping: { finalStatus: '$.lastResult.finalStatus', reportUrl: '$.lastResult.reportUrl' }
          }
        };
        for (const current of state.model.nodes) {
          Object.assign(current, mappings[current.id] || {});
        }
      }

      function edge(id, from, to, condition = 'default') {
        return { id, from, to, condition, name: '', documentation: '' };
      }

      function $(id) { return document.getElementById(id); }
      function selectedNode() { return state.model.nodes.find(n => n.id === state.selected.id); }
      function selectedEdge() { return state.model.edges.find(e => e.id === state.selected.id); }
      function pretty(value) { return JSON.stringify(value, null, 2); }
