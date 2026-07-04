const { expect } = require('@playwright/test');

const activityRegistry = [
  { id: 'import-numbers', name: 'Import number batch' },
  { id: 'filter-and-split-batches', name: 'Filter and split batches' },
  { id: 'notify-owner-report', name: 'Report batches to owner' },
  { id: 'load-campaign', name: 'Load campaign config' },
  { id: 'prepare-call-round', name: 'Prepare call list' },
  { id: 'execute-call-round', name: 'Execute outbound round' },
  { id: 'wait-round-completion', name: 'Wait for dialer completion' },
  { id: 'start-ai-tagging', name: 'Start AI tagging' },
  { id: 'wait-tagging-completion', name: 'Wait for tagging completion' },
  { id: 'filter-next-round', name: 'Filter next round list' },
  { id: 'aggregate-round-results', name: 'Aggregate round result' },
  { id: 'evaluate-next-round', name: 'Evaluate next round' },
  { id: 'finalize-campaign', name: 'Finalize and generate report' },
  { id: 'script-runtime', name: 'Script Runtime' },
];

const DEMO_NODE = {
  importNumbers: 'Import number batch',
  prepareRound: 'Prepare call list',
};

const DEFAULT_WORKFLOW_NAME = 'Untitled Workflow';

function sampleMainFlowWorkflow() {
  return {
    id: 'Definitions_Sample_Main_Flow',
    name: 'Sample Main Flow',
    targetNamespace: 'https://example.com/e2e',
    process: {
      id: 'Process_Sample_Main_Flow',
      name: 'Sample Main Flow',
      isExecutable: true,
      edgeRouting: 'orthogonal',
    },
    nodes: [
      { id: 'StartEvent', kind: 'startEvent', name: 'Start', x: 100, y: 220, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: {} },
      { id: 'Task_ImportNumbers', kind: 'serviceTask', name: 'Import number batch', x: 280, y: 198, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: { flowFoundryTaskDefinition: { type: 'import-numbers', retries: '3' } }, activityType: 'import-numbers', maxAttempts: 3 },
      { id: 'EndEvent', kind: 'endEvent', name: 'End', x: 460, y: 220, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: {} },
    ],
    edges: [
      { id: 'F_Start_Import', from: 'StartEvent', to: 'Task_ImportNumbers', condition: 'default', name: '', documentation: '' },
      { id: 'F_Import_End', from: 'Task_ImportNumbers', to: 'EndEvent', condition: 'default', name: '', documentation: '' },
    ],
  };
}

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
    const clientHeader = route.request().headers()['x-flowfoundry-client'];
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        workflowId: 'workflow_e2e_mock_001',
        runId: 'test-run-001',
        businessKey: body.businessKey || body.input?.campaignId || 'demo-campaign',
        runSource: clientHeader === 'web-modeler' ? 'web-modeler' : 'production',
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
    body: JSON.stringify({
      workflowId: 'workflow_e2e_mock_001',
      runId: 'test-run-001',
      temporalStatus: 'WORKFLOW_EXECUTION_STATUS_RUNNING',
      status: 'RUNNING',
      currentNodeId: 'Task_User',
      waitingHumanTaskNodeId: 'Task_User',
      humanTasks: [
        { nodeId: 'Task_User', mode: 'managed', waiting: true },
        { nodeId: 'Task_OwnerApprove', mode: 'managed', waiting: false },
      ],
      temporalHistory: [
        {
          eventId: 1,
          eventType: 'EVENT_TYPE_WORKFLOW_EXECUTION_STARTED',
          type: 'workflowExecutionStartedEventAttributes',
          workflowType: 'FlowInterpreterWorkflow',
        },
        {
          eventId: 2,
          eventType: 'EVENT_TYPE_ACTIVITY_TASK_SCHEDULED',
          type: 'activityTaskScheduledEventAttributes',
          activityType: 'dynamic-activity-router',
          taskQueue: 'call-campaign',
        },
      ],
    }),
  }));
}

async function openModeler(page) {
  await page.goto('/');
  await page.locator('#navModeler').click();
  await expect(page.locator('#modelerView')).toHaveClass(/active/);
  await expect(page.locator('#paletteItems')).toContainText('Service Task');
}

async function openFreshModeler(page) {
  await page.addInitScript(() => {
    localStorage.clear();
    localStorage.setItem('flowfoundry-locale', 'en');
  });
  await openModeler(page);
}

function outboundSchedulerWorkflow() {
  const task = (id, name, activityType, x, y, retries = 3) =>
    node(id, 'serviceTask', name, x, y, {
      activityType,
      maxAttempts: retries,
      config: { flowFoundryTaskDefinition: { type: activityType, retries: String(retries) } },
    });

  const nodes = [
    node('StartEvent', 'startEvent', 'Submit outbound campaign', 60, 220),
    task('Task_ImportNumbers', 'Import number batch', 'import-numbers', 180, 198),
    task('Task_FilterSplit', 'Filter and split batches', 'filter-and-split-batches', 300, 198),
    task('Task_NotifyOwner', 'Report batches to owner', 'notify-owner-report', 420, 198),
    node('Task_OwnerApprove', 'humanTask', 'Owner approval', 540, 198, {
      config: {
        flowFoundryHumanTask: { mode: 'managed' },
        flowFoundryAssignmentDefinition: { candidateGroups: 'business-owner' },
      },
    }),
    node('Gateway_Approved', 'exclusiveGateway', 'Approved?', 660, 210),
    task('Task_PrepareRound', 'Prepare call list', 'prepare-call-round', 900, 198),
    task('Task_ExecuteRound', 'Execute outbound round', 'execute-call-round', 1020, 198, 5),
    node('Gateway_Continue', 'exclusiveGateway', 'Continue next round?', 1620, 210),
    task('Task_Finalize', 'Finalize and generate report', 'finalize-campaign', 900, 340),
    node('EndEvent', 'endEvent', 'Campaign complete', 1020, 358),
  ];
  const edges = [
    edge('F_Start_Import', 'StartEvent', 'Task_ImportNumbers'),
    edge('F_Import_Filter', 'Task_ImportNumbers', 'Task_FilterSplit'),
    edge('F_Filter_Notify', 'Task_FilterSplit', 'Task_NotifyOwner'),
    edge('F_Notify_Approve', 'Task_NotifyOwner', 'Task_OwnerApprove'),
    edge('F_Approve_Gw', 'Task_OwnerApprove', 'Gateway_Approved'),
    edge('F_Approved_Prepare', 'Gateway_Approved', 'Task_PrepareRound', '${approved == true}'),
    edge('F_Rejected_Finalize', 'Gateway_Approved', 'Task_Finalize', 'default'),
    edge('F_Prepare_Execute', 'Task_PrepareRound', 'Task_ExecuteRound'),
    edge('F_Execute_Continue', 'Task_ExecuteRound', 'Gateway_Continue'),
    edge('F_Stop_Finalize', 'Gateway_Continue', 'Task_Finalize', 'default'),
    edge('F_Finalize_End', 'Task_Finalize', 'EndEvent'),
  ];
  return {
    id: 'Definitions_MultiRoundOutboundScheduler',
    name: 'Multi-round Outbound Scheduler',
    targetNamespace: 'https://example.com/e2e/outbound-scheduler',
    process: {
      id: 'MultiRoundOutboundScheduler',
      name: 'Multi-round Outbound Scheduler',
      isExecutable: true,
      edgeRouting: 'orthogonal',
    },
    nodes,
    edges,
  };
}

async function openFreshModelerWithSampleFlow(page) {
  await openFreshModeler(page);
  await importModel(page, sampleMainFlowWorkflow());
}

async function openFreshModelerWithOutboundDemo(page) {
  await openFreshModeler(page);
  await importModel(page, outboundSchedulerWorkflow());
}


async function importModel(page, model) {
  await page.evaluate(m => {
    state.model = m;
    if (typeof normalizeLoadedModel === 'function') normalizeLoadedModel(state.model);
    syncParticipantAssignments();
    renderAll();
  }, model);
  await expect(page.locator('.node')).toHaveCount(model.nodes.length);
}

function nodeLocator(page, text) {
  return page.locator('.node', { hasText: text }).first();
}

function nodeById(page, id) {
  return page.locator(`.node[data-node-id="${id}"]`);
}

function connectionHandle(page, nodeId, handle) {
  return page.locator(
    `.connection-handle[data-node-id="${nodeId}"][data-handle="${handle}"]`,
  );
}

async function clickNode(page, text) {
  const node = nodeLocator(page, text);
  await expect(node).toBeVisible();
  const nodeId = await node.getAttribute('data-node-id');
  if (nodeId) {
    await page.evaluate(id => select('node', id), nodeId);
  } else {
    await node.click({ force: true });
  }
  await expect(node).toHaveClass(/selected/);
  return node;
}

async function clickNodeById(page, id) {
  const node = nodeById(page, id);
  await expect(node).toBeVisible();
  await page.evaluate(nodeId => select('node', nodeId), id);
  await expect(node).toHaveClass(/selected/);
  return node;
}

async function selectEdge(page, edgeId) {
  await page.evaluate(id => {
    select('edge', id);
  }, edgeId);
  await expect(page.locator('#propType')).toContainText('SequenceFlow');
}

async function modelState(page) {
  return page.evaluate(() => JSON.parse(JSON.stringify(state.model)));
}

async function dragBetween(page, sourceLocator, targetPoint) {
  const box = await sourceLocator.boundingBox();
  if (!box) throw new Error('Source locator has no bounding box');
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(targetPoint.x, targetPoint.y, { steps: 12 });
  await page.mouse.up();
}

async function dragLocatorTo(page, locator, target) {
  await dragBetween(page, locator, target);
}

async function dragNodeTo(page, nodeText, target) {
  await dragLocatorTo(page, nodeLocator(page, nodeText), target);
}

async function dragConnection(page, fromNodeId, fromHandle, toNodeId, toHandle) {
  const from = connectionHandle(page, fromNodeId, fromHandle);
  const to = connectionHandle(page, toNodeId, toHandle);
  await expect(from).toBeVisible();
  await expect(to).toBeVisible();
  const toBox = await to.boundingBox();
  if (!toBox) throw new Error('Target connection handle has no bounding box');
  await dragBetween(page, from, {
    x: toBox.x + toBox.width / 2,
    y: toBox.y + toBox.height / 2,
  });
}

async function getViewportState(page) {
  return page.evaluate(() => ({
    scale: state.scale,
    panX: state.panX,
    panY: state.panY,
    viewportLocked: state.viewportLocked,
  }));
}

async function jsonPanelValue(page) {
  await expect(page.locator('#jsonPanel')).toHaveClass(/open/);
  return JSON.parse(await page.locator('#jsonContent').innerText());
}

async function clickCanvasToolbarButton(page, name) {
  await page.locator('#modelerView .toolbar').first().getByRole('button', { name, exact: true }).click();
}

async function clickRightToolbarButton(page, name) {
  await page.locator('#modelerView .toolbar.right').getByRole('button', { name, exact: true }).click();
}

async function dragPaletteItemToCanvas(page, label, position = null) {
  const paletteItem = page.locator('.palette-item', { hasText: label });
  const canvas = page.locator('#canvas');
  const before = await page.locator('.node').count();
  const canvasBox = await canvas.boundingBox();
  if (!canvasBox) throw new Error('Canvas is not visible');
  const target = position || {
    x: canvasBox.width / 2,
    y: canvasBox.height / 2,
  };
  await paletteItem.dragTo(canvas, { targetPosition: target });
  await expect(page.locator('.node')).toHaveCount(before + 1, { timeout: 10_000 });
}

function modelBase(nodes, edges = []) {
  return {
    id: `Definitions_E2E_${Date.now()}`,
    name: 'E2E Flow',
    targetNamespace: 'https://example.com/e2e',
    process: {
      id: `Process_E2E_${Date.now()}`,
      name: 'E2E Flow',
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
  const p = participant('Participant_Ops', 'Ops Team', 80, 120, 900, 520, 'ops-team');
  const ops = { participantId: 'Participant_Ops' };
  return modelBase([
    p,
    node('StartEvent', 'startEvent', 'Start', 150, 260, ops),
    node('Task_Service', 'serviceTask', 'Service Task', 230, 238, { activityType: 'load-campaign', maxAttempts: 3, ...ops }),
    node('Gateway_Main', 'exclusiveGateway', 'Continue?', 360, 252, ops),
    node('Task_User', 'humanTask', 'Manual Review', 300, 360, { config: { flowFoundryAssignmentDefinition: { candidateGroups: 'ops' } }, ...ops }),
    node('Sub_Process', 'subProcess', 'Sub-process', 520, 200, { width: 240, height: 220, ...ops }),
    node('Sub_Start', 'startEvent', 'Sub Start', 560, 300, { parentSubProcessId: 'Sub_Process', subProcessBoundary: 'start', ...ops }),
    node('Sub_Task', 'serviceTask', 'Sub Notify', 590, 260, { activityType: 'notify-owner-report', parentSubProcessId: 'Sub_Process', ...ops }),
    node('Sub_End', 'endEvent', 'Sub End', 680, 300, { parentSubProcessId: 'Sub_Process', subProcessBoundary: 'end', ...ops }),
    node('EndEvent', 'endEvent', 'End', 760, 360, ops),
  ], [
    edge('F_Start_Service', 'StartEvent', 'Task_Service'),
    edge('F_Service_Gateway', 'Task_Service', 'Gateway_Main'),
    edge('F_Gateway_User', 'Gateway_Main', 'Task_User', '${remainingContacts > 0}'),
    edge('F_User_Sub', 'Task_User', 'Sub_Task'),
    edge('F_Sub_End', 'Sub_Task', 'EndEvent'),
  ]);
}

function nodeTypeMatrixWorkflow() {
  const p = participant('Participant_All', 'All Node Participant', 40, 80, 1680, 760, 'all-nodes');
  const specs = [
    ['Start', 'startEvent', 'Start'],
    ['Task_Service', 'serviceTask', 'Service Task', { activityType: 'load-campaign', maxAttempts: 3 }],
    ['Task_User', 'humanTask', 'Human Task', { config: { flowFoundryHumanTask: { mode: 'managed' }, flowFoundryAssignmentDefinition: { candidateGroups: 'ops' } } }],
    ['Task_Human_Offline', 'humanTask', 'Human Task Offline', { config: { flowFoundryHumanTask: { mode: 'offline' } } }],
    ['Task_Send', 'serviceTask', 'Notify Task', { activityType: 'notify-owner-report', maxAttempts: 3 }],
    ['Task_Receive', 'serviceTask', 'Wait Task', { activityType: 'wait-tagging-completion', maxAttempts: 3 }],
    ['Task_Script', 'scriptTask', 'Script Task', { activityType: 'script-runtime', decisionRef: 'risk-check', decisionVersion: '1.0.0' }],
    ['Task_Workflow', 'workflow', 'Workflow Task', { config: { childWorkflowId: 'Definitions_Child', childWorkflowVersion: '1.0.0', childWorkflowName: 'Child Workflow' } }],
    ['Gateway_Exclusive', 'exclusiveGateway', 'Exclusive Gateway'],
    ['Gateway_Parallel', 'parallelGateway', 'Parallel Gateway'],
    ['Gateway_Inclusive', 'inclusiveGateway', 'Inclusive Gateway'],
    ['Gateway_Event', 'eventBasedGateway', 'Event Gateway'],
    ['Timer', 'intermediateEvent', 'Intermediate Event', { config: { timerDefinition: { type: 'duration', value: '1m' }, subtype: 'timer' } }],
    ['End', 'endEvent', 'End'],
  ];
  const nodes = [p, ...specs.map(([id, kind, name, extra], index) => {
    const col = index % 4;
    const row = Math.floor(index / 4);
    return node(id, kind, name, 120 + col * 380, 170 + row * 130, extra || {});
  })];
  const edges = specs.slice(0, -1).map(([id], index) => edge(`F_${id}_${specs[index + 1][0]}`, id, specs[index + 1][0]));
  return modelBase(nodes, edges);
}

function genericTaskSketchWorkflow() {
  return modelBase([
    node('StartEvent', 'startEvent', 'Start', 120, 180),
    node('Task_Sketch', 'task', 'Generic Task', 360, 158),
    node('EndEvent', 'endEvent', 'End', 620, 180),
  ], [
    edge('F_Start_Sketch', 'StartEvent', 'Task_Sketch'),
    edge('F_Sketch_End', 'Task_Sketch', 'EndEvent'),
  ]);
}

function invalidParticipantWorkflow() {
  return modelBase([
    participant('Participant_A', 'Participant A', 80, 120, 400, 260, 'team-a'),
    node('StartEvent', 'startEvent', 'Start', 160, 220),
    node('Task_Outside', 'serviceTask', 'Outside Task', 480, 220, { activityType: 'load-campaign', maxAttempts: 3 }),
    node('EndEvent', 'endEvent', 'End', 420, 220),
  ], [
    edge('F_Start_Task', 'StartEvent', 'Task_Outside'),
    edge('F_Task_End', 'Task_Outside', 'EndEvent'),
  ]);
}

function simpleConnectionWorkflow() {
  return modelBase([
    node('StartEvent', 'startEvent', 'Start', 120, 180),
    node('Task_A', 'serviceTask', 'Task A', 360, 158, { activityType: 'load-campaign', maxAttempts: 3 }),
    node('Task_B', 'serviceTask', 'Task B', 620, 158, { activityType: 'notify-owner-report', maxAttempts: 3 }),
  ], []);
}

function connectedWorkflow() {
  return modelBase([
    node('StartEvent', 'startEvent', 'Start', 120, 180),
    node('Task_A', 'serviceTask', 'Task A', 360, 158, { activityType: 'load-campaign', maxAttempts: 3 }),
    node('Task_B', 'serviceTask', 'Task B', 620, 158, { activityType: 'notify-owner-report', maxAttempts: 3 }),
  ], [
    edge('F_Start_TaskA', 'StartEvent', 'Task_A'),
  ]);
}

const PLATFORM_ID_PATTERN = /^(workflow|event|subprocess|task|gateway|participant)_[a-z0-9]{8}$/;

function createShortId() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let id = '';
  for (let i = 0; i < 8; i += 1) {
    id += chars[Math.floor(Math.random() * chars.length)];
  }
  return id;
}

function compareVersions(left, right) {
  const l = left.split('.').map(Number);
  const r = right.split('.').map(Number);
  for (let i = 0; i < 3; i += 1) {
    if (l[i] !== r[i]) return l[i] - r[i];
  }
  return 0;
}

function nextPatchVersion(version) {
  const parts = version.split('.').map(Number);
  parts[2] += 1;
  return parts.join('.');
}

function participantAnnotationWorkflow() {
  const p = participant('Participant_Notes', 'Notes Team', 120, 100, 720, 420, 'notes-team');
  const ops = { participantId: 'Participant_Notes' };
  return modelBase([
    p,
    node('Task_Inside', 'serviceTask', 'Inside Task', 260, 240, { activityType: 'load-campaign', maxAttempts: 3, ...ops }),
    node('Annotation_1', 'textAnnotation', 'Sticky note', 300, 160, {
      documentation: 'Bound to participant',
      width: 180,
      height: 72,
      ...ops,
    }),
  ], []);
}

function partialContainmentWorkflow() {
  const p = participant('Participant_Edge', 'Edge Pool', 80, 120, 520, 300, 'edge-pool');
  return modelBase([
    p,
    node('Task_Edge', 'serviceTask', 'Edge Task', 108, 220, {
      activityType: 'load-campaign',
      maxAttempts: 3,
      participantId: 'Participant_Edge',
    }),
    node('EndEvent', 'endEvent', 'End', 420, 220, { participantId: 'Participant_Edge' }),
  ], [
    edge('F_Task_End', 'Task_Edge', 'EndEvent'),
  ]);
}

function workflowRecordFromStore(wf) {
  return {
    id: wf.id,
    name: wf.name,
    version: wf.currentVersion,
    status: wf.status,
    updatedAt: wf.updatedAt,
    versions: wf.versions.map(v => ({
      version: v.version,
      status: v.status,
      createdAt: v.createdAt,
      model: v.model,
    })),
  };
}

function emptyWorkflowModel(workflowId, name) {
  return {
    id: workflowId,
    name,
    targetNamespace: 'https://example.com/flowfoundry',
    process: {
      id: workflowId.replace(/^workflow_/, 'process_'),
      name,
      isExecutable: true,
    },
    nodes: [],
    edges: [],
  };
}

async function mockWorkflowBackend(page, store = {}) {
  const PREFIXES = {
    workflow: 'workflow_',
    event: 'event_',
    subprocess: 'subprocess_',
    task: 'task_',
    gateway: 'gateway_',
    participant: 'participant_',
  };

  if (!store.workflows) store.workflows = new Map();
  if (!store.idRegistry) store.idRegistry = new Set();
  store.idRequests = store.idRequests || [];
  store.workflowApiLog = store.workflowApiLog || [];

  const allocateId = kind => {
    const prefix = PREFIXES[kind];
    if (!prefix) throw new Error(`Unsupported id kind: ${kind}`);
    for (let attempt = 0; attempt < 32; attempt += 1) {
      const id = `${prefix}${createShortId()}`;
      if (!store.idRegistry.has(id)) {
        store.idRegistry.add(id);
        return id;
      }
    }
    throw new Error(`Failed to allocate id for kind: ${kind}`);
  };

  const fulfillJson = async (route, status, body) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  };

  await page.route(/\/api\/workflows(\/.*)?$/, async route => {
    const request = route.request();
    const method = request.method();
    const url = new URL(request.url());
    const rest = url.pathname.replace(/^\/api\/workflows\/?/, '');
    store.workflowApiLog.push({ method, path: rest });

    if (rest === 'ids/kinds' && method === 'GET') {
      return fulfillJson(route, 200, { kinds: Object.keys(PREFIXES) });
    }

    if (rest === 'ids' && method === 'POST') {
      const body = request.postDataJSON();
      store.idRequests.push(body);
      const id = allocateId(body.kind);
      return fulfillJson(route, 200, { kind: body.kind, id });
    }

    if (rest === '' && method === 'GET') {
      const keyword = (url.searchParams.get('keyword') || '').trim().toLowerCase();
      const status = url.searchParams.get('status') || '';
      let items = [...store.workflows.values()];
      if (keyword) {
        items = items.filter(
          wf => wf.name.toLowerCase().includes(keyword) || wf.id.toLowerCase().includes(keyword),
        );
      }
      if (status) items = items.filter(wf => wf.status === status);
      items.sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)));
      return fulfillJson(route, 200, items.map(workflowRecordFromStore));
    }

    if (rest === '' && method === 'POST') {
      const body = request.postDataJSON();
      const id = allocateId('workflow');
      const now = new Date().toISOString();
      const model = body.model
        ? JSON.parse(JSON.stringify(body.model))
        : emptyWorkflowModel(id, body.name);
      model.id = id;
      model.name = body.name;
      const wf = {
        id,
        name: body.name,
        status: 'draft',
        currentVersion: '1.0.0',
        createdAt: now,
        updatedAt: now,
        versions: [{
          version: '1.0.0',
          status: 'draft',
          createdAt: now,
          model,
        }],
      };
      store.workflows.set(id, wf);
      return fulfillJson(route, 201, workflowRecordFromStore(wf));
    }

    const parts = rest.split('/').map(part => decodeURIComponent(part));
    const workflowId = parts[0];
    const wf = store.workflows.get(workflowId);

    if (parts.length === 1) {
      if (method === 'GET') {
        if (!wf) return fulfillJson(route, 404, { message: 'Workflow not found' });
        return fulfillJson(route, 200, workflowRecordFromStore(wf));
      }
      if (method === 'PATCH') {
        if (!wf) return fulfillJson(route, 404, { message: 'Workflow not found' });
        const body = request.postDataJSON();
        if (body.name) {
          wf.name = body.name;
          wf.versions.forEach(version => {
            version.model.name = body.name;
            if (version.model.process) version.model.process.name = body.name;
          });
        }
        if (body.status) {
          wf.status = body.status;
          const current = wf.versions.find(v => v.version === wf.currentVersion);
          if (current) current.status = body.status;
        }
        if (body.activeVersion) {
          if (!wf.versions.some(v => v.version === body.activeVersion)) {
            return fulfillJson(route, 404, { message: 'Workflow version not found' });
          }
          wf.currentVersion = body.activeVersion;
        }
        wf.updatedAt = new Date().toISOString();
        return fulfillJson(route, 200, workflowRecordFromStore(wf));
      }
      if (method === 'DELETE') {
        if (!wf) return fulfillJson(route, 404, { message: 'Workflow not found' });
        store.workflows.delete(workflowId);
        return route.fulfill({ status: 204, body: '' });
      }
    }

    if (parts[1] === 'versions') {
      if (!wf) return fulfillJson(route, 404, { message: 'Workflow not found' });

      if (parts.length === 2 && method === 'POST') {
        const body = request.postDataJSON();
        const sourceVersion = body.sourceVersion || wf.currentVersion;
        const source = wf.versions.find(v => v.version === sourceVersion);
        if (!source) return fulfillJson(route, 404, { message: 'Workflow version not found' });
        const latest = [...wf.versions].map(v => v.version).sort(compareVersions).pop();
        const newVersion = body.version || nextPatchVersion(latest);
        if (wf.versions.some(v => v.version === newVersion)) {
          return fulfillJson(route, 409, { message: `Workflow version already exists: ${newVersion}` });
        }
        const now = new Date().toISOString();
        const model = body.model
          ? JSON.parse(JSON.stringify(body.model))
          : JSON.parse(JSON.stringify(source.model));
        wf.versions.push({ version: newVersion, status: 'draft', createdAt: now, model });
        wf.currentVersion = newVersion;
        wf.status = 'draft';
        wf.updatedAt = now;
        return fulfillJson(route, 201, workflowRecordFromStore(wf));
      }

      if (parts.length === 3) {
        const version = parts[2];
        const versionEntity = wf.versions.find(v => v.version === version);
        if (!versionEntity) return fulfillJson(route, 404, { message: 'Workflow version not found' });

        if (method === 'GET') {
          return fulfillJson(route, 200, {
            version: versionEntity.version,
            status: versionEntity.status,
            createdAt: versionEntity.createdAt,
            model: versionEntity.model,
          });
        }

        if (method === 'PUT') {
          const body = request.postDataJSON();
          if (body.name) {
            wf.name = body.name;
            versionEntity.model.name = body.name;
          }
          if (body.model) versionEntity.model = JSON.parse(JSON.stringify(body.model));
          if (body.status) {
            wf.status = body.status;
            versionEntity.status = body.status;
          }
          wf.currentVersion = version;
          wf.updatedAt = new Date().toISOString();
          return fulfillJson(route, 200, workflowRecordFromStore(wf));
        }
      }
    }

    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Not found' }) });
  });
}

async function mockBackendWithWorkflow(page, store = {}) {
  await mockBackend(page, store);
  await mockWorkflowBackend(page, store);
}

async function openFreshApp(page) {
  await page.addInitScript(() => {
    localStorage.clear();
    localStorage.setItem('flowfoundry-locale', 'en');
  });
  await page.goto('/');
  await expect(page.locator('#workflowListView')).toHaveClass(/active/);
  await page.waitForFunction(() => typeof state !== 'undefined' && Array.isArray(state.workflows));
}

async function switchToModeler(page) {
  await page.locator('#navModeler').click();
  await expect(page.locator('#modelerView')).toHaveClass(/active/);
  await expect(page.locator('#paletteItems')).toContainText('Service Task');
}

async function fillAppDialog(page, value, { confirm = true } = {}) {
  const dialog = page.locator('#appDialogBackdrop.open');
  await expect(dialog).toBeVisible();
  const field = dialog.locator('#appDialogInput:not(.hidden), #appDialogTextarea:not(.hidden)');
  if (await field.count()) {
    await field.fill(value);
  }
  if (confirm) {
    await dialog.locator('#appDialogConfirm').click();
  } else {
    await dialog.locator('#appDialogCancel').click();
  }
  await expect(dialog).not.toBeVisible();
}

async function confirmAppDialog(page) {
  const dialog = page.locator('#appDialogBackdrop.open');
  await expect(dialog).toBeVisible();
  await dialog.locator('#appDialogConfirm').click();
  await expect(dialog).not.toBeVisible();
}

module.exports = {
  DEMO_NODE,
  DEFAULT_WORKFLOW_NAME,
  sampleMainFlowWorkflow,
  outboundSchedulerWorkflow,
  PLATFORM_ID_PATTERN,
  mockBackend,
  mockWorkflowBackend,
  mockBackendWithWorkflow,
  openFreshModeler,
  openFreshModelerWithSampleFlow,
  openFreshModelerWithOutboundDemo,
  openFreshApp,
  switchToModeler,
  fillAppDialog,
  confirmAppDialog,
  importModel,
  clickNode,
  clickNodeById,
  selectEdge,
  modelState,
  dragNodeTo,
  dragConnection,
  dragPaletteItemToCanvas,
  getViewportState,
  jsonPanelValue,
  clickCanvasToolbarButton,
  clickRightToolbarButton,
  nodeLocator,
  nodeById,
  connectionHandle,
  participantWorkflow,
  nodeTypeMatrixWorkflow,
  genericTaskSketchWorkflow,
  invalidParticipantWorkflow,
  simpleConnectionWorkflow,
  connectedWorkflow,
  participantAnnotationWorkflow,
  partialContainmentWorkflow,
};
