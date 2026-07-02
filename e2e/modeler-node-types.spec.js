const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
  clickNodeById,
  jsonPanelValue,
  nodeTypeMatrixWorkflow,
  clickCanvasToolbarButton,
} = require('./helpers/modeler');

test.describe('FlowFoundry node type coverage', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
    await importModel(page, nodeTypeMatrixWorkflow());
  });

  const expectedTypes = [
    ['Start', 'startEvent'],
    ['Generic Task', 'task'],
    ['Service Task', 'serviceTask'],
    ['Human Task', 'userTask'],
    ['Send Task', 'sendTask'],
    ['Receive Task', 'receiveTask'],
    ['Script Task', 'scriptTask'],
    ['Workflow Task', 'workflow'],
    ['Exclusive Gateway', 'exclusiveGateway'],
    ['Parallel Gateway', 'parallelGateway'],
    ['Inclusive Gateway', 'inclusiveGateway'],
    ['Event Gateway', 'eventBasedGateway'],
    ['Timer Event', 'intermediateCatchEvent'],
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
      } else {
        await clickNode(page, label);
      }
      await expect(page.locator('#propType')).toContainText(kind);
      await expect(page.locator('.node.selected')).toContainText(label);
    });
  }

  test('renders offline human task mode', async ({ page }) => {
    await clickNodeById(page, 'Task_Human_Offline');
    await expect(page.locator('#propType')).toContainText('userTask');
    await expect(page.locator('#properties')).toContainText('Offline');
  });

  test('hides manual task and boundary event from palette', async ({ page }) => {
    await expect(page.locator('.palette-item', { hasText: 'Manual Task' })).toHaveCount(0);
    await expect(page.locator('.palette-item', { hasText: 'Boundary Event' })).toHaveCount(0);
  });

  test('preserves node-specific configuration in generated DSL', async ({ page }) => {
    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);

    const receive = dsl.nodes.find(n => n.id === 'Task_Receive');
    const script = dsl.nodes.find(n => n.id === 'Task_Script');
    const workflow = dsl.nodes.find(n => n.id === 'Task_Workflow');
    const timer = dsl.nodes.find(n => n.id === 'Timer');

    const human = dsl.nodes.find(n => n.id === 'Task_User');
    const humanOffline = dsl.nodes.find(n => n.id === 'Task_Human_Offline');

    expect(receive.config.signalName).toBe('callback');
    expect(human.config.flowFoundryHumanTask.mode).toBe('managed');
    expect(humanOffline.config.flowFoundryHumanTask.mode).toBe('offline');
    expect(script.activityType).toBe('dmn-decision');
    expect(script.decisionRef).toBe('risk-check');
    expect(workflow.config.flowFoundryChildWorkflow.childWorkflowId).toBe('Definitions_Child');
    expect(timer.config.duration).toBe('1m');
    expect(dsl.nodes.every(n => n.config.flowFoundryParticipant?.participantId === 'Participant_All')).toBe(true);
  });
});
