const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
  clickNodeById,
  jsonPanelValue,
  nodeTypeMatrixWorkflow,
  genericTaskSketchWorkflow,
  clickCanvasToolbarButton,
  clickRightToolbarButton,
  dragPaletteItemToCanvas,
} = require('./helpers/modeler');

test.describe('FlowFoundry node type coverage', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
    await importModel(page, nodeTypeMatrixWorkflow());
  });

  const expectedTypes = [
    ['Start', 'startEvent'],
    ['Service Task', 'serviceTask'],
    ['Human Task', 'humanTask'],
    ['Script Task', 'scriptTask'],
    ['Workflow Task', 'workflow'],
    ['Exclusive Gateway', 'exclusiveGateway'],
    ['Parallel Gateway', 'parallelGateway'],
    ['Inclusive Gateway', 'inclusiveGateway'],
    ['Event Gateway', 'eventBasedGateway'],
    ['Intermediate Event', 'intermediateEvent'],
    ['End', 'endEvent'],
  ];

  for (const [label, kind] of expectedTypes) {
    test(`renders and selects ${kind}`, async ({ page }) => {
      if (kind === 'endEvent') {
        await clickNodeById(page, 'End');
      } else if (kind === 'eventBasedGateway') {
        await clickNodeById(page, 'Gateway_Event');
      } else if (kind === 'startEvent') {
        await clickNodeById(page, 'Start');
      } else if (kind === 'intermediateEvent') {
        await clickNodeById(page, 'Timer');
      } else {
        await clickNode(page, label);
      }
      await expect(page.locator('#propType')).toContainText(kind);
      await expect(page.locator('.node.selected')).toContainText(label);
    });
  }

  test('renders generic task from palette as sketch placeholder', async ({ page }) => {
    await openFreshModeler(page);
    await dragPaletteItemToCanvas(page, 'Generic Task', { x: 420, y: 260 });
    await expect(page.locator('#propType')).toContainText('task');
  });

  test('rejects generic task at compile time', async ({ page }) => {
    await importModel(page, genericTaskSketchWorkflow());
    await clickRightToolbarButton(page, 'Compile');
    await expect(page.locator('#appNotice')).toContainText(/Generic Task/i);
  });

  test('renders offline human task mode', async ({ page }) => {
    await clickNodeById(page, 'Task_Human_Offline');
    await expect(page.locator('#propType')).toContainText('humanTask');
    await expect(page.locator('#properties')).toContainText('Offline');
  });


  test('preserves node-specific configuration in generated DSL', async ({ page }) => {
    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);

    const waitTask = dsl.nodes.find(n => n.id === 'Task_Receive');
    const script = dsl.nodes.find(n => n.id === 'Task_Script');
    const workflow = dsl.nodes.find(n => n.id === 'Task_Workflow');
    const timer = dsl.nodes.find(n => n.id === 'Timer');

    const human = dsl.nodes.find(n => n.id === 'Task_User');
    const humanOffline = dsl.nodes.find(n => n.id === 'Task_Human_Offline');

    expect(waitTask.activityType).toBe('wait-tagging-completion');
    expect(human.kind).toBe('ACTIVITY');
    expect(human.activityType).toBe('human-task');
    expect(human.config.flowFoundryHumanTask.mode).toBe('managed');
    expect(humanOffline.kind).toBe('ACTIVITY');
    expect(humanOffline.activityType).toBe('human-task');
    expect(humanOffline.config.flowFoundryHumanTask.mode).toBe('offline');
    expect(script.kind).toBe('ACTIVITY');
    expect(script.activityType).toBe('script-runtime');
    expect(script.decisionRef).toBe('risk-check');
    expect(dsl.nodes.find(n => n.id === 'Gateway_Exclusive').kind).toBe('GATEWAY');
    expect(dsl.nodes.find(n => n.id === 'Gateway_Exclusive').config.gatewayKind).toBe('exclusive');
    expect(dsl.nodes.find(n => n.id === 'Gateway_Parallel').config.gatewayKind).toBe('parallel');
    expect(timer.kind).toBe('INTERMEDIATE_EVENT');
    expect(timer.config.eventSubtype).toBe('timer');
    expect(workflow.config.flowFoundryChildWorkflow.childWorkflowId).toBe('Definitions_Child');
    expect(timer.config.duration).toBe('1m');
    expect(dsl.nodes.every(n => n.config.flowFoundryParticipant?.participantId === 'Participant_All')).toBe(true);
  });
});
