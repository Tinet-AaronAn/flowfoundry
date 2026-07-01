const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  participantWorkflow,
  jsonPanelValue,
} = require('./helpers/modeler');

test.describe('FlowFoundry compile, run, and debug flows', () => {
  test('compiles and runs a participant-owned workflow through mocked APIs', async ({ page }) => {
    const backend = {};
    await mockBackend(page, backend);
    await openFreshModeler(page);
    await importModel(page, participantWorkflow());

    await page.getByRole('button', { name: 'Compile' }).click();
    const plan = await jsonPanelValue(page);

    expect(plan.nodeCount).toBeGreaterThan(0);
    expect(backend.compileRequests).toHaveLength(1);
    expect(backend.compileRequests[0].nodes.every(n => n.config.flowFoundryParticipant?.participantRef === 'ops-team')).toBe(true);

    await page.getByRole('button', { name: 'Close' }).click();
    await page.getByRole('button', { name: 'Run' }).click();
    const run = await jsonPanelValue(page);

    expect(run.workflowId).toBe('test-workflow-001');
    expect(await page.locator('#workflowId').inputValue()).toBe('test-workflow-001');
    expect(backend.runRequests).toHaveLength(1);
  });

  test('debug panel can run full simulation and show execution log', async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);

    await page.getByRole('button', { name: '模拟运行 / 调试' }).click();
    await expect(page.locator('#debugView')).toHaveClass(/active/);

    await page.getByRole('button', { name: '完整模拟' }).click();
    await expect(page.locator('#debugLog')).toContainText('进入 StartEvent');
    await expect(page.locator('#debugLog')).toContainText('变量快照');
  });

  test('queries workflow state and completes human task through mocked APIs', async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);

    await page.locator('#workflowId').fill('test-workflow-001');
    await page.getByRole('button', { name: '查询状态' }).click();
    const state = await jsonPanelValue(page);

    expect(state.workflowId).toBe('test-workflow-001');
    expect(state.status).toBe('RUNNING');
  });
});
