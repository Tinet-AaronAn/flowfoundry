const { expect } = require('@playwright/test');

const activityRegistry = [
  { id: 'load-campaign', name: '加载任务配置' },
  { id: 'prepare-call-round', name: '准备本轮名单' },
  { id: 'execute-call-round', name: '执行本轮外呼' },
  { id: 'wait-round-completion', name: '等待外呼结果' },
  { id: 'aggregate-round-results', name: '汇总本轮结果' },
  { id: 'evaluate-next-round', name: '评估是否进入下一轮' },
  { id: 'finalize-campaign', name: '结束并生成报告' },
  { id: 'send-message', name: '发送消息' },
  { id: 'dmn-decision', name: 'DMN 决策' },
];

async function mockBackend(page, state = {}) {
  state.compileRequests = [];
  state.runRequests = [];

  await page.route('**/api/activities', route => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ activities: activityRegistry }),
  }));

  await page.route('**/api/flows/compile', async route => {
    const body = route.request().postDataJSON();
    state.compileRequests.push(body);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        flowId: body.flow?.id || body.id || 'compiled-flow',
        version: body.flow?.version || '1.0.0',
        startNodeId: body.nodes?.[0]?.id || 'StartEvent',
        nodeCount: body.nodes?.length || 0,
        edgeCount: body.edges?.length || 0,
        nodes: body.nodes || [],
        edges: body.edges || [],
      }),
    });
  });

  await page.route('**/api/flows/run', async route => {
    const body = route.request().postDataJSON();
    state.runRequests.push(body);
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        workflowId: 'test-workflow-001',
        runId: 'test-run-001',
        businessKey: body.businessKey || body.input?.campaignId || 'demo-campaign',
        executionPlan: {
          flowId: body.flow?.flow?.id || body.flow?.id || 'test-flow',
          nodes: body.flow?.nodes || [],
          edges: body.flow?.edges || [],
        },
      }),
    });
  });

  await page.route('**/api/flows/runs/**', route => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ workflowId: 'test-workflow-001', status: 'RUNNING', currentNodeId: 'Task_User' }),
  }));
}

async function openModeler(page) {
  await page.goto('/');
  await page.getByRole('button', { name: '流程画布' }).click();
  await expect(page.locator('#modelerView')).toHaveClass(/active/);
  await expect(page.locator('#paletteItems')).toContainText('Service Task');
}

async function openFreshModeler(page) {
  await page.addInitScript(() => localStorage.clear());
  await openModeler(page);
}

async function importModel(page, model) {
  page.once('dialog', dialog => dialog.accept(JSON.stringify({ model })));
  await page.getByRole('button', { name: 'Import' }).click();
  await expect(page.locator('.node')).toHaveCount(model.nodes.length);
}

function nodeLocator(page, text) {
  return page.locator('.node', { hasText: text }).first();
}

async function clickNode(page, text) {
  const node = nodeLocator(page, text);
  await expect(node).toBeVisible();
  await node.click();
  await expect(node).toHaveClass(/selected/);
  return node;
}

async function dragLocatorTo(locator, target) {
  const box = await locator.boundingBox();
  if (!box) throw new Error('Source locator has no bounding box');
  await locator.page().mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await locator.page().mouse.down();
  await locator.page().mouse.move(target.x, target.y, { steps: 12 });
  await locator.page().mouse.up();
}

async function dragNodeTo(page, nodeText, target) {
  await dragLocatorTo(nodeLocator(page, nodeText), target);
}

async function jsonPanelValue(page) {
  await expect(page.locator('#jsonPanel')).toHaveClass(/open/);
  return JSON.parse(await page.locator('#jsonContent').innerText());
}

async function clickCanvasToolbarButton(page, name) {
  await page.locator('#modelerView .toolbar').first().getByRole('button', { name }).click();
}

async function clickRightToolbarButton(page, name) {
  await page.locator('#modelerView .toolbar.right').getByRole('button', { name }).click();
}

function modelBase(nodes, edges = []) {
  return {
    id: `Definitions_E2E_${Date.now()}`,
    name: 'E2E 流程',
    targetNamespace: 'https://example.com/e2e',
    process: {
      id: `Process_E2E_${Date.now()}`,
      name: 'E2E 流程',
      isExecutable: true,
    },
    nodes,
    edges,
  };
}

function participant(id, name, x, y, width = 760, height = 260, ref = id) {
  return {
    id,
    kind: 'participant',
    name,
    x,
    y,
    width,
    height,
    documentation: '',
    config: { participantRef: ref },
  };
}

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
    ...extra,
  };
}

function edge(id, from, to, condition = 'default') {
  return { id, from, to, condition, name: '', documentation: '' };
}

function participantWorkflow() {
  const p = participant('Participant_Ops', '运营团队', 80, 120, 560, 420, 'ops-team');
  return modelBase([
    p,
    node('StartEvent', 'startEvent', '开始', 150, 260),
    node('Task_Service', 'serviceTask', '服务任务', 230, 238, { activityType: 'load-campaign', maxAttempts: 3 }),
    node('Gateway_Main', 'exclusiveGateway', '是否继续?', 340, 252),
    node('Task_User', 'userTask', '人工复核', 420, 235, { config: { flowFoundryAssignmentDefinition: { candidateGroups: 'ops' } } }),
    node('Sub_Process', 'subProcess', '子流程', 470, 180, { width: 160, height: 180 }),
    node('Sub_Start', 'startEvent', 'Sub Start', 500, 252, { parentSubProcessId: 'Sub_Process', subProcessBoundary: 'start' }),
    node('Sub_Task', 'sendTask', '子流程发送', 535, 230, { activityType: 'send-message', parentSubProcessId: 'Sub_Process' }),
    node('Sub_End', 'endEvent', 'Sub End', 600, 252, { parentSubProcessId: 'Sub_Process', subProcessBoundary: 'end' }),
    node('EndEvent', 'endEvent', '结束', 600, 310),
  ], [
    edge('F_Start_Service', 'StartEvent', 'Task_Service'),
    edge('F_Service_Gateway', 'Task_Service', 'Gateway_Main'),
    edge('F_Gateway_User', 'Gateway_Main', 'Task_User', '${remainingContacts > 0}'),
    edge('F_User_Sub', 'Task_User', 'Sub_Task'),
    edge('F_Sub_End', 'Sub_Task', 'EndEvent'),
  ]);
}

function nodeTypeMatrixWorkflow() {
  const p = participant('Participant_All', '全节点参与方', 40, 80, 1680, 760, 'all-nodes');
  const specs = [
    ['Start', 'startEvent', '开始'],
    ['Task_Generic', 'task', 'Generic Task'],
    ['Task_Service', 'serviceTask', 'Service Task', { activityType: 'load-campaign', maxAttempts: 3 }],
    ['Task_User', 'userTask', 'User Task', { config: { flowFoundryAssignmentDefinition: { candidateGroups: 'ops' } } }],
    ['Task_Manual', 'manualTask', 'Manual Task', { config: { flowFoundryAssignmentDefinition: { candidateGroups: 'manual' } } }],
    ['Task_Send', 'sendTask', 'Send Task', { activityType: 'send-message', maxAttempts: 3 }],
    ['Task_Receive', 'receiveTask', 'Receive Task', { config: { signalName: 'callback', waitMode: 'signal' } }],
    ['Task_Script', 'scriptTask', 'Script Task', { config: { scriptFormat: 'feel', script: 'roundNumber := roundNumber + 1' } }],
    ['Task_Rule', 'businessRuleTask', 'Business Rule Task', { activityType: 'dmn-decision', decisionRef: 'risk-check', decisionVersion: '1.0.0' }],
    ['Gateway_Exclusive', 'exclusiveGateway', 'Exclusive Gateway'],
    ['Gateway_Parallel', 'parallelGateway', 'Parallel Gateway'],
    ['Gateway_Inclusive', 'inclusiveGateway', 'Inclusive Gateway'],
    ['Gateway_Event', 'eventBasedGateway', 'Event Gateway'],
    ['Timer', 'intermediateCatchEvent', 'Timer Event', { config: { timerDefinition: { type: 'duration', value: '1m' }, subtype: 'timer' } }],
    ['Boundary', 'boundaryEvent', 'Boundary Event', { config: { timerDefinition: { type: 'duration', value: '1m' }, attachedToRef: 'Task_Service' } }],
    ['End', 'endEvent', '结束'],
  ];
  const nodes = [p, ...specs.map(([id, kind, name, extra], index) => {
    const col = index % 4;
    const row = Math.floor(index / 4);
    return node(id, kind, name, 120 + col * 380, 170 + row * 130, extra || {});
  })];
  const edges = specs.slice(0, -1).map(([id], index) => edge(`F_${id}_${specs[index + 1][0]}`, id, specs[index + 1][0]));
  return modelBase(nodes, edges);
}

function invalidParticipantWorkflow() {
  return modelBase([
    participant('Participant_A', '参与方 A', 80, 120, 400, 260, 'team-a'),
    node('StartEvent', 'startEvent', '开始', 160, 220),
    node('Task_Outside', 'serviceTask', 'Outside Task', 480, 220, { activityType: 'load-campaign', maxAttempts: 3 }),
    node('EndEvent', 'endEvent', '结束', 420, 220),
  ], [
    edge('F_Start_Task', 'StartEvent', 'Task_Outside'),
    edge('F_Task_End', 'Task_Outside', 'EndEvent'),
  ]);
}

module.exports = {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
  dragNodeTo,
  jsonPanelValue,
  clickCanvasToolbarButton,
  clickRightToolbarButton,
  nodeLocator,
  participantWorkflow,
  nodeTypeMatrixWorkflow,
  invalidParticipantWorkflow,
};
