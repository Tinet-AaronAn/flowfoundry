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

  test('renders human task properties without mode selector', async ({ page }) => {
    await clickNodeById(page, 'Task_User');
    await expect(page.locator('#propType')).toContainText('humanTask');
    await expect(page.locator('#properties')).toContainText('Candidate Groups');
    await expect(page.locator('#properties')).not.toContainText('Offline');
  });

  test('script task property panel loads catalog and applies script selection', async ({ page }) => {
    await clickNodeById(page, 'Task_Script');
    await expect(page.locator('#propType')).toContainText('scriptTask');
    await expect(page.locator('#properties')).toContainText('Script (Node.js)');

    const scriptSection = page.locator('#properties .prop-section').filter({
      has: page.getByRole('heading', { name: 'Script (Node.js)' }),
    });
    const scriptSelect = scriptSection.locator('select').first();
    await expect(scriptSelect).toBeEnabled();
    await expect(scriptSelect.locator('option')).toContainText(['Risk Check (risk-check)', 'Demo Script (demo-script)']);

    await scriptSelect.selectOption('demo-script');
    await expect(page.locator('#properties input[placeholder="tinetJsCodeId"]')).toHaveValue('demo-script');
    await expect(page.locator('#properties input[placeholder="tinetScriptName (optional)"]')).toHaveValue('Demo Script');
    await expect(page.locator('#properties input[placeholder="tinetJsCodeVersion"]')).toHaveValue('1');

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    const script = dsl.nodes.find(n => n.id === 'Task_Script');
    expect(script.scriptCodeId).toBe('demo-script');
    expect(script.scriptName).toBe('Demo Script');
    expect(script.scriptVersion).toBe('1');
  });

  test('exclusive gateway edge FEEL page can return to gateway properties', async ({ page }) => {
    await clickNodeById(page, 'Gateway_Exclusive');
    await page.locator('.edge-priority-link').first().click();
    await expect(page.locator('#propType')).toContainText('SequenceFlow');
    await page.locator('#properties button.secondary').click();
    await expect(page.locator('#propType')).toContainText('exclusiveGateway');
    await expect(page.locator('#properties')).toContainText('Routing');
  });

  test('parallel gateway outgoing edge supports FEEL configuration', async ({ page }) => {
    await clickNodeById(page, 'Gateway_Parallel');
    await page.locator('.edge-priority-link').first().click();
    await expect(page.locator('#propType')).toContainText('SequenceFlow');
    await expect(page.locator('#properties')).toContainText('FEEL');
    await page.locator('#properties input[placeholder="${amount > 1000}"]').fill('branchA == true');
    await page.locator('#properties button.secondary').click();
    await expect(page.locator('#propType')).toContainText('parallelGateway');
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
    expect(humanOffline.config.flowFoundryHumanTask.mode).toBe('managed');
    expect(script.kind).toBe('ACTIVITY');
    expect(script.activityType).toBe('script-runtime');
    expect(script.scriptCodeId).toBe('risk-check');
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
