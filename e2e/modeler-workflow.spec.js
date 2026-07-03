const { test, expect } = require('@playwright/test');
const {
  PLATFORM_ID_PATTERN,
  mockBackendWithWorkflow,
  openFreshApp,
  switchToModeler,
  fillAppDialog,
  confirmAppDialog,
  DEFAULT_WORKFLOW_NAME,
  outboundSchedulerWorkflow,
  importModel,
  sampleMainFlowWorkflow,
  clickNode,
  modelState,
  dragPaletteItemToCanvas,
} = require('./helpers/modeler');

test.describe('FlowFoundry workflow API integration', () => {
  let store;

  test.beforeEach(async ({ page }) => {
    store = {};
    await mockBackendWithWorkflow(page, store);
    await openFreshApp(page);
    const model = outboundSchedulerWorkflow();
    const workflowId = await page.evaluate(() => state.workflows[0]?.id);
    if (workflowId && store.workflows.has(workflowId)) {
      const wf = store.workflows.get(workflowId);
      wf.name = model.name;
      wf.versions[0].model = JSON.parse(JSON.stringify(model));
    }
    await page.evaluate(m => {
      const workflow = state.workflows[0];
      if (!workflow) return;
      workflow.name = m.name;
      workflow.versions[0].model = JSON.parse(JSON.stringify(m));
      state.model = JSON.parse(JSON.stringify(m));
      syncParticipantAssignments();
      renderAll();
    }, model);
  });

  test('WF-01 detects workflow API and lists workflows from backend', async ({ page }) => {
    await expect(page.locator('#workflowTable .table-row')).not.toHaveCount(0);
    await expect(page.locator('#workflowTable')).toContainText('Multi-round Outbound Scheduler');
    expect(store.workflowApiLog.some(entry => entry.method === 'GET' && entry.path === '')).toBe(true);
    await expect(page.locator('#appNotice')).not.toContainText('Workflow API unavailable');
  });

  test('WF-02 creates workflow via API with workflow_ prefixed id and version 1.0.0', async ({ page }) => {
    const beforeRows = await page.locator('#workflowTable .table-row:not(.header)').count();

    await page.getByRole('button', { name: 'New Workflow' }).click();
    await fillAppDialog(page, 'Campaign Alpha');

    await expect(page.locator('#workflowTable .table-row:not(.header)')).toHaveCount(beforeRows + 1);
    await expect(page.locator('#workflowTable')).toContainText('Campaign Alpha');

    const created = [...store.workflows.values()].find(wf => wf.name === 'Campaign Alpha');
    expect(created).toBeTruthy();
    expect(created.id).toMatch(PLATFORM_ID_PATTERN);
    expect(created.id.startsWith('workflow_')).toBe(true);
    expect(created.currentVersion).toBe('1.0.0');
    expect(store.workflowApiLog.some(entry => entry.method === 'POST' && entry.path === '')).toBe(true);
  });

  test('WF-03 saves current version model through PUT API', async ({ page }) => {
    await switchToModeler(page);
    await importModel(page, sampleMainFlowWorkflow());
    await clickNode(page, 'Import number batch');

    const nameInput = page.locator('#properties .prop-section').first().locator('input').nth(1);
    await nameInput.fill('Persisted Task Name');
    await page.waitForFunction(() => state.model.nodes.some(node => node.name === 'Persisted Task Name'));
    await page.locator('#navWorkflows').click();
    await page.getByRole('button', { name: 'Save Current Version' }).click();

    await expect(page.locator('#appNotice')).toContainText('Workflow saved');
    const putEntry = store.workflowApiLog.find(entry => entry.method === 'PUT' && entry.path.includes('/versions/'));
    expect(putEntry).toBeTruthy();

    const workflowId = await page.evaluate(() => state.activeWorkflowId);
    const version = await page.evaluate(() => state.activeVersion);
    const stored = store.workflows.get(workflowId);
    const savedNode = stored.versions.find(v => v.version === version).model.nodes
      .find(node => node.name === 'Persisted Task Name');
    expect(savedNode).toBeTruthy();
  });

  test('WF-04 creates a new version with patch increment', async ({ page }) => {
    const workflowId = await page.evaluate(() => state.workflows[0].id);
    const row = page.locator('#workflowTable .table-row', { hasText: workflowId }).first();

    await row.getByRole('button', { name: 'New Version' }).click();
    await fillAppDialog(page, '1.0.1');

    await expect(page.locator('#appNotice')).toContainText('Workflow opened');
    const wf = store.workflows.get(workflowId);
    expect(wf.versions.map(v => v.version)).toContain('1.0.1');
    expect(wf.currentVersion).toBe('1.0.1');
    expect(store.workflowApiLog.some(entry => entry.method === 'POST' && entry.path.endsWith('/versions'))).toBe(true);
  });

  test('WF-05 renames workflow from modeler header and saves through PUT API', async ({ page }) => {
    const workflowId = await page.evaluate(() => state.workflows[0].id);
    const row = page.locator('#workflowTable .table-row', { hasText: workflowId }).first();

    await row.locator('.workflow-name-cell').click();
    await expect(page.locator('#modelerView')).toHaveClass(/active/);
    await page.locator('#flowName').fill('Renamed Scheduler');
    await page.waitForFunction(() => state.model.name === 'Renamed Scheduler');
    await page.locator('#navWorkflows').click();
    await page.getByRole('button', { name: 'Save Current Version' }).click();

    await expect(page.locator('#workflowTable')).toContainText('Renamed Scheduler');
    await expect(page.locator('#appNotice')).toContainText('Workflow saved');
    const activeWorkflowId = await page.evaluate(() => state.activeWorkflowId);
    expect(store.workflows.get(activeWorkflowId).name).toBe('Renamed Scheduler');
    expect(store.workflowApiLog.some(entry => entry.method === 'PUT' && entry.path.includes('/versions/'))).toBe(true);
  });

  test('WF-06 deletes workflow through DELETE API', async ({ page }) => {
    await page.getByRole('button', { name: 'New Workflow' }).click();
    await fillAppDialog(page, 'Second Flow');
    await page.locator('#navWorkflows').click();
    await expect(page.locator('#workflowListView')).toHaveClass(/active/);
    const second = [...store.workflows.values()].find(wf => wf.name === 'Second Flow');
    expect(second).toBeTruthy();

    const before = await page.locator('#workflowTable .table-row:not(.header)').count();
    await page.locator('#workflowTable .table-row', { hasText: second.id }).getByRole('button', { name: 'Delete' }).click();
    await confirmAppDialog(page);

    await expect(page.locator('#workflowTable .table-row:not(.header)')).toHaveCount(before - 1);
    expect(store.workflows.has(second.id)).toBe(false);
    expect(store.workflowApiLog.some(entry => entry.method === 'DELETE' && entry.path === second.id)).toBe(true);
  });

  test('WF-07 allocates platform IDs with correct prefix when dropping palette nodes', async ({ page }) => {
    await switchToModeler(page);
    const before = await page.locator('.node').count();

    await dragPaletteItemToCanvas(page, 'Service Task');
    await dragPaletteItemToCanvas(page, 'Start Event', { x: 220, y: 180 });
    await dragPaletteItemToCanvas(page, 'Exclusive Gateway', { x: 420, y: 180 });

    await expect(page.locator('.node')).toHaveCount(before + 3);
    expect(store.idRequests.map(req => req.kind).sort()).toEqual(
      expect.arrayContaining(['task', 'event', 'gateway']),
    );

    const newIds = await page.evaluate(() => state.model.nodes.slice(-3).map(node => node.id));
    newIds.forEach(id => expect(id).toMatch(PLATFORM_ID_PATTERN));
    expect(newIds.some(id => id.startsWith('task_'))).toBe(true);
    expect(newIds.some(id => id.startsWith('event_'))).toBe(true);
    expect(newIds.some(id => id.startsWith('gateway_'))).toBe(true);
  });

  test('WF-08 filters workflow list by keyword and status', async ({ page }) => {
    const workflowId = await page.evaluate(() => state.workflows[0].id);
    await page.evaluate(async id => {
      await setWorkflowStatus(id, 'active');
    }, workflowId);

    await page.getByRole('button', { name: 'New Workflow' }).click();
    await fillAppDialog(page, 'Draft Only Flow');
    await page.locator('#navWorkflows').click();
    await expect(page.locator('#workflowListView')).toHaveClass(/active/);

    await page.locator('#workflowSearch').fill('Draft Only');
    await expect(page.locator('#workflowTable')).toContainText('Draft Only Flow');
    await expect(page.locator('#workflowTable')).not.toContainText('Multi-round Outbound Scheduler');

    await page.locator('#workflowSearch').fill('');
    await page.locator('#workflowStatusFilter').selectOption('active');
    await expect(page.locator('#workflowTable')).toContainText('Multi-round Outbound Scheduler');
    await expect(page.locator('#workflowTable')).not.toContainText('Draft Only Flow');
  });

  test('WF-09 keeps workflow action buttons wrapped without horizontal overflow', async ({ page }) => {
    const actions = page.locator('#workflowTable .table-actions').first();
    await expect(actions).toBeVisible();

    const layout = await actions.evaluate(el => {
      const style = window.getComputedStyle(el);
      return {
        flexWrap: style.flexWrap,
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth,
      };
    });

    expect(layout.flexWrap).toBe('wrap');
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 2);
  });

  test('WF-10 keeps status filter dropdown at fixed 148px width', async ({ page }) => {
    const filter = page.locator('#workflowStatusFilter');
    const box = await filter.boundingBox();
    if (!box) throw new Error('Status filter is not visible');
    expect(Math.round(box.width)).toBe(148);
  });
});
