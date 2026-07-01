const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  clickNode,
  jsonPanelValue,
  clickCanvasToolbarButton,
  clickRightToolbarButton,
} = require('./helpers/modeler');

test.describe('FlowFoundry modeler main flow', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('loads modeler, selects a node from its body, and shows the floating toolbar', async ({ page }) => {
    await clickNode(page, '加载任务配置');

    await expect(page.locator('.node.selected .node-toolbar')).toBeVisible();
    await expect(page.locator('#propType')).toContainText('serviceTask');
    await expect(page.locator('.node.selected .node-toolbar button[title="向后追加 Task"]')).toBeVisible();
  });

  test('selects a node when clicking the node body, not only connection handles', async ({ page }) => {
    const nodeName = page.locator('.node', { hasText: '加载任务配置' }).locator('.node-name');
    const box = await nodeName.boundingBox();
    if (!box) throw new Error('Node text is not visible');

    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);

    await expect(page.locator('.node.selected')).toContainText('加载任务配置');
    await expect(page.locator('.node.selected .node-toolbar')).toBeVisible();
    await expect(page.locator('#propType')).toContainText('serviceTask');

    const canvasBox = await page.locator('#canvas').boundingBox();
    if (!canvasBox) throw new Error('Canvas is not visible');
    await page.mouse.click(canvasBox.x + 30, canvasBox.y + 90);
    await expect(page.locator('.node.selected')).toHaveCount(0);
  });

  test('appends a task from the floating toolbar, then supports undo and redo', async ({ page }) => {
    await clickNode(page, '加载任务配置');
    const before = await page.locator('.node').count();

    await page.locator('.node.selected .node-toolbar button[title="向后追加 Task"]').click();
    await expect(page.locator('.node')).toHaveCount(before + 1);
    await expect(page.locator('#message')).toContainText('已追加节点');

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

    await page.getByRole('button', { name: 'Close' }).click();
    await clickRightToolbarButton(page, 'Export');
    const exported = await jsonPanelValue(page);

    expect(exported.model.nodes.length).toBeGreaterThan(0);
    expect(exported.dsl.dslVersion).toBe('1.0');
    expect(exported.bpmn.processes[0].flowElements.length).toBeGreaterThan(0);
  });
});
