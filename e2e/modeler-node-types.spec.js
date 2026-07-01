const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
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
    ['开始', 'startEvent'],
    ['Generic Task', 'task'],
    ['Service Task', 'serviceTask'],
    ['User Task', 'userTask'],
    ['Manual Task', 'manualTask'],
    ['Send Task', 'sendTask'],
    ['Receive Task', 'receiveTask'],
    ['Script Task', 'scriptTask'],
    ['Business Rule Task', 'businessRuleTask'],
    ['Exclusive Gateway', 'exclusiveGateway'],
    ['Parallel Gateway', 'parallelGateway'],
    ['Inclusive Gateway', 'inclusiveGateway'],
    ['Event Gateway', 'eventBasedGateway'],
    ['Timer Event', 'intermediateCatchEvent'],
    ['Boundary Event', 'boundaryEvent'],
    ['结束', 'endEvent'],
  ];

  for (const [label, kind] of expectedTypes) {
    test(`renders and selects ${kind}`, async ({ page }) => {
      await clickNode(page, label);
      await expect(page.locator('#propType')).toContainText(kind);
      await expect(page.locator('.node.selected')).toContainText(label);
    });
  }

  test('preserves node-specific configuration in generated DSL', async ({ page }) => {
    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);

    const receive = dsl.nodes.find(n => n.id === 'Task_Receive');
    const script = dsl.nodes.find(n => n.id === 'Task_Script');
    const rule = dsl.nodes.find(n => n.id === 'Task_Rule');
    const timer = dsl.nodes.find(n => n.id === 'Timer');

    expect(receive.config.signalName).toBe('callback');
    expect(script.config.script).toBe('roundNumber := roundNumber + 1');
    expect(rule.activityType).toBe('dmn-decision');
    expect(timer.config.duration).toBe('1m');
    expect(dsl.nodes.every(n => n.config.flowFoundryParticipant?.participantId === 'Participant_All')).toBe(true);
  });
});
