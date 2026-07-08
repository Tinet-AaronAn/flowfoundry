const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModelerWithOutboundDemo,
  importModel,
  clickNode,
  jsonPanelValue,
  clickCanvasToolbarButton,
  clickRightToolbarButton,
  DEMO_NODE,
} = require('./helpers/modeler');

test.describe('FlowFoundry modeler main flow', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModelerWithOutboundDemo(page);
  });

  test('loads modeler, selects a node from its body, and shows the floating toolbar', async ({ page }) => {
    await clickNode(page, DEMO_NODE.importNumbers);

    await expect(page.locator('.node.selected .node-toolbar')).toBeVisible();
    await expect(page.locator('#propType')).toContainText('serviceTask');
    await expect(page.locator('.node.selected .node-toolbar button[title="Append task"]')).toBeVisible();
  });

  test('selects a node when clicking the node body, not only connection handles', async ({ page }) => {
    const nodeName = page.locator('.node', { hasText: DEMO_NODE.importNumbers }).locator('.node-name');
    const box = await nodeName.boundingBox();
    if (!box) throw new Error('Node text is not visible');

    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);

    await expect(page.locator('.node.selected')).toContainText(DEMO_NODE.importNumbers);
    await expect(page.locator('.node.selected .node-toolbar')).toBeVisible();
    await expect(page.locator('#propType')).toContainText('serviceTask');

    const canvasBox = await page.locator('#canvas').boundingBox();
    if (!canvasBox) throw new Error('Canvas is not visible');
    await page.mouse.click(canvasBox.x + 30, canvasBox.y + 90);
    await expect(page.locator('.node.selected')).toHaveCount(0);
  });

  test('keeps node property inputs focused while typing', async ({ page }) => {
    await clickNode(page, DEMO_NODE.importNumbers);
    const nameInput = page.locator('#properties .prop-section').first().locator('input').nth(1);

    await nameInput.fill('');
    await nameInput.click();
    await page.keyboard.type('Continuous Input Name');

    await expect(nameInput).toHaveValue('Continuous Input Name');
    await expect(nameInput).toBeFocused();
    await expect(page.locator('.node.selected .node-name')).toHaveText('Continuous Input Name');
  });

  test('switches process edge routing between rounded orthogonal and curved', async ({ page }) => {
    await importModel(page, {
      id: 'Definitions_Edge_Routing',
      name: 'Edge Routing Test',
      targetNamespace: 'https://example.com/e2e',
      process: {
        id: 'Process_Edge_Routing',
        name: 'Edge Routing Test',
        isExecutable: true,
        edgeRouting: 'orthogonal',
      },
      nodes: [
        { id: 'StartEvent', kind: 'startEvent', name: 'Start', x: 100, y: 220, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: {} },
        { id: 'Task_Service', kind: 'serviceTask', name: 'Service Task', x: 320, y: 120, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: {}, activityType: 'import-numbers', maxAttempts: 3 },
      ],
      edges: [
        { id: 'F_Start_Service', from: 'StartEvent', to: 'Task_Service', name: '', condition: 'default', documentation: '' },
      ],
    });

    const firstEdgePath = page.locator('#edges > path:not(.edge-hit)').first();
    await expect(page.locator('#edgeRouting')).toHaveValue('orthogonal');
    await expect(firstEdgePath).toHaveAttribute('d', /Q/);
    await expect(firstEdgePath).not.toHaveAttribute('d', / C /);

    await page.locator('#edgeRouting').selectOption('curved');
    await expect(firstEdgePath).toHaveAttribute('d', / C /);

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.flow.edgeRouting).toBeUndefined();

    await page.locator('#jsonPanel').getByRole('button', { name: 'Close' }).click();
    await clickRightToolbarButton(page, 'Export');
    const exported = await jsonPanelValue(page);
    expect(exported.model.process.edgeRouting).toBe('curved');
  });

  test('shows edge name instead of FEEL condition when a name is set', async ({ page }) => {
    await importModel(page, {
      id: 'Definitions_Edge_Label',
      name: 'Edge Label Test',
      targetNamespace: 'https://example.com/e2e',
      process: {
        id: 'Process_Edge_Label',
        name: 'Edge Label Test',
        isExecutable: true,
        edgeRouting: 'orthogonal',
      },
      nodes: [
        { id: 'StartEvent', kind: 'startEvent', name: 'Start', x: 100, y: 220, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: {} },
        { id: 'Task_Service', kind: 'serviceTask', name: 'Service Task', x: 230, y: 198, documentation: '', emphasis: 'none', inputMapping: {}, outputMapping: {}, headers: {}, loop: 'none', config: {}, activityType: 'load-campaign', maxAttempts: 3 },
      ],
      edges: [
        { id: 'F_Start_Service', from: 'StartEvent', to: 'Task_Service', name: 'Approved Path', condition: '${amount > 1000}', documentation: '' },
      ],
    });

    await expect(page.locator('#edges text')).toHaveText('Approved Path');
    await expect(page.locator('#edges text')).not.toContainText('${amount > 1000}');
  });

  test('shows gateway name below the gateway node', async ({ page }) => {
    const gateway = page.locator('.gateway-node').first();
    const shape = gateway.locator('.gateway-shape');
    const label = gateway.locator('.gateway-label');

    await expect(label).toBeVisible();
    const shapeBox = await shape.boundingBox();
    const labelBox = await label.boundingBox();
    if (!shapeBox || !labelBox) throw new Error('Gateway shape or label is not visible');

    const shapeCenterX = shapeBox.x + shapeBox.width / 2;
    const labelCenterX = labelBox.x + labelBox.width / 2;
    expect(Math.abs(shapeCenterX - labelCenterX)).toBeLessThan(2);
    expect(labelBox.y).toBeGreaterThan(shapeBox.y + shapeBox.height - 2);
  });

  test('appends a task from the floating toolbar, then supports undo and redo', async ({ page }) => {
    await clickNode(page, DEMO_NODE.importNumbers);
    const before = await page.locator('.node').count();

    await page.locator('.node.selected .node-toolbar button[title="Append task"]').click();
    await expect(page.locator('.node')).toHaveCount(before + 1);
    await expect(page.locator('#appNotice')).toContainText('Node appended');

    await page.getByRole('button', { name: 'Undo' }).click();
    await expect(page.locator('.node')).toHaveCount(before);

    await page.getByRole('button', { name: 'Redo' }).click();
    await expect(page.locator('.node')).toHaveCount(before + 1);
  });

  test('can view DSL and export model JSON from the toolbar', async ({ page }) => {
    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);

    expect(dsl.dslVersion).toBe('1.0');
    expect(dsl.nodes.length).toBeGreaterThan(0);
    expect(dsl.edges.length).toBeGreaterThan(0);

    await page.locator('#jsonPanel').getByRole('button', { name: 'Close' }).click();
    await clickRightToolbarButton(page, 'Export');
    const exported = await jsonPanelValue(page);

    expect(exported.model.nodes.length).toBeGreaterThan(0);
    expect(exported.dsl.dslVersion).toBe('1.0');
    expect(exported.bpmn.processes[0].flowElements.length).toBeGreaterThan(0);
  });
});
