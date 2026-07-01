      const paletteGroups = [
        {
          name: 'Events',
          items: [
            { kind: 'startEvent', label: 'Start Event', basic: true },
            { kind: 'endEvent', label: 'End Event', basic: true },
            { kind: 'intermediateCatchEvent', label: 'Intermediate / Boundary', basic: true, config: { timerDefinition: { type: 'duration', value: '1m' }, subtype: 'timer' } },
            { kind: 'boundaryEvent', label: 'Boundary Event', basic: true, config: { timerDefinition: { type: 'duration', value: '1m' }, attachedToRef: '' } }
          ]
        },
        {
          name: 'Activities',
          items: [
            { kind: 'task', label: 'Generic Task', basic: true },
            { kind: 'serviceTask', label: 'Service Task', basic: true },
            { kind: 'userTask', label: 'User Task', basic: true },
            { kind: 'manualTask', label: 'Manual Task', basic: true, config: { flowFoundryAssignmentDefinition: { candidateGroups: 'manual-operator' } } },
            { kind: 'sendTask', label: 'Send Task', basic: true, activityType: 'send-message' },
            { kind: 'receiveTask', label: 'Receive Task', basic: true, config: { signalName: 'external-message-received' } },
            { kind: 'scriptTask', label: 'Script Task', basic: true, config: { scriptFormat: 'feel', script: 'roundNumber := roundNumber + 1' } },
            { kind: 'businessRuleTask', label: 'Business Rule Task / DMN', basic: true, decisionRef: 'demo-decision', decisionVersion: '1.0.0' }
          ]
        },
        {
          name: 'Gateways',
          items: [
            { kind: 'exclusiveGateway', label: 'Exclusive Gateway', basic: true },
            { kind: 'parallelGateway', label: 'Parallel Gateway', basic: true },
            { kind: 'inclusiveGateway', label: 'Inclusive Gateway', basic: true },
            { kind: 'eventBasedGateway', label: 'Event-based Gateway', basic: true }
          ]
        },
        {
          name: 'Structural',
          items: [
            { kind: 'subProcess', label: 'Sub-process', basic: true },
            { kind: 'participant', label: 'Participant', basic: true, config: { participantRef: 'business-team' } }
          ]
        },
        {
          name: 'Annotations',
          items: [
            { kind: 'textAnnotation', label: 'Text Annotation', basic: true, documentation: '流程说明 / 业务备注' }
          ]
        }
      ];

      const state = {
        activities: [],
        currentView: 'workflows',
        workflows: [],
        activeWorkflowId: null,
        activeVersion: '1.0.0',
        simulation: null,
        paletteDragItem: null,
        taskMorphMenuNodeId: null,
        suppressCanvasClick: false,
        selected: { type: 'process', id: null },
        connectionSource: null,
        connectionSourceHandle: null,
        connectionDraftTarget: null,
        connectBuffer: [],
        scale: 1,
        history: [],
        future: [],
        model: {
          id: 'Definitions_MultiRoundOutboundScheduler',
          name: '多轮外呼任务调度流程',
          targetNamespace: 'https://example.com/bpmn/outbound-scheduler',
          process: {
            id: 'MultiRoundOutboundScheduler',
            name: '多轮外呼任务调度流程',
            isExecutable: true
          },
          nodes: [
            node('StartEvent', 'startEvent', '提交外呼任务', 80, 220),
            task('Task_LoadCampaign', 'serviceTask', '加载任务配置', 'load-campaign', 160, 198, 3),
            task('Task_PrepareRound', 'serviceTask', '准备本轮名单', 'prepare-call-round', 330, 198, 3),
            node('Gateway_ChannelStrategy', 'exclusiveGateway', '名单调度策略?', 520, 210),
            task('Task_SplitBatch', 'serviceTask', '拆分外呼批次', 'prepare-call-round', 610, 80, 3),
            human('Task_ManualConfirm', 'userTask', '人工确认名单', 'call-supervisor', 610, 340),
            task('Task_ExecuteRound', 'serviceTask', '执行本轮外呼', 'execute-call-round', 780, 198, 5),
            task('Task_WaitCompletion', 'serviceTask', '等待外呼结果', 'wait-round-completion', 950, 198, 3),
            task('Task_AggregateResult', 'serviceTask', '汇总本轮结果', 'aggregate-round-results', 1120, 198, 3),
            node('Gateway_RiskCheck', 'exclusiveGateway', '触发风险复核?', 1290, 210),
            human('Task_RiskReview', 'userTask', '运营复核策略', 'operation-manager', 1380, 90),
            task('Task_EvaluateNextRound', 'serviceTask', '评估是否进入下一轮', 'evaluate-next-round', 1540, 198, 3),
            node('Gateway_Continue', 'exclusiveGateway', '继续下一轮?', 1730, 210),
            node('Timer_BetweenRounds', 'intermediateCatchEvent', '轮次间隔', 1850, 330, {
              config: { subtype: 'timer', timerDefinition: { type: 'duration', value: '1m' } }
            }),
            node('Task_IncRound', 'scriptTask', '轮次加一', 1020, 360, {
              config: { scriptFormat: 'feel', script: 'roundNumber := roundNumber + 1' }
            }),
            task('Task_Finalize', 'serviceTask', '结束并生成报告', 'finalize-campaign', 1830, 198, 3),
            node('EndEvent', 'endEvent', '任务完成', 2010, 220)
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
          config: { flowFoundryAssignmentDefinition: { candidateGroups } }
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
