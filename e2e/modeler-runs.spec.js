const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
} = require('./helpers/modeler');

test.describe('FlowFoundry execution runs', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('keeps runtime controls on the modeler header, not in properties', async ({ page }) => {
    await expect(page.locator('#propertiesPanel')).not.toContainText('Business Input JSON');
    await expect(page.locator('#executionConsole')).toHaveCount(0);
    await expect(page.locator('#modelerView').getByRole('button', { name: 'Run', exact: true })).toBeVisible();
    await expect(page.locator('#modelerView').getByRole('button', { name: 'Run Status', exact: true })).toBeVisible();
    await expect(page.locator('#propertiesPanel #runInput')).toHaveCount(0);
    await expect(page.locator('#appNotice')).toBeVisible();
    await expect(page.locator('#appNotice')).toHaveClass(/app-notice/);
  });

  test('records a run and lists it on the Runs page', async ({ page }) => {
    await page.locator('#modelerView').getByRole('button', { name: 'Run', exact: true }).click();
    await page.locator('#appDialogConfirm').click();
    await expect(page.locator('#jsonPanel')).not.toHaveClass(/open/);
    await expect(page.locator('#workflowId')).toHaveValue(/workflow_/);
    const workflowId = await page.locator('#workflowId').inputValue();

    await page.locator('#navRuns').click();
    await expect(page.locator('#runsView')).toHaveClass(/active/);
    await page.locator('#runsView').getByRole('button', { name: 'Search', exact: true }).click();
    await expect(page.locator('#runsTable')).toContainText(workflowId);
    await expect(page.locator('#propertiesPanel')).toBeHidden();

    await page.locator('#runsTable .runs-exec-link').click();
    await expect(page.locator('#runTimelineBackdrop')).toHaveClass(/open/);
    await expect(page.locator('#runTimelineTitle')).toContainText(workflowId);
    await expect(page.locator('#runTimelineBody .run-timeline-gantt-row')).toHaveCount(3);
    await expect(page.locator('#runTimelineBody')).toContainText('Import number batch');
    await expect(page.locator('#runTimelineBody')).toContainText('Owner approval');
    await expect(page.locator('#runTimelineIo')).toBeVisible();
    await expect(page.locator('#modelerView')).not.toHaveClass(/active/);

    await page.locator('.run-timeline-view-tab[data-view="feed"]').click();
    await expect(page.locator('#runTimelineBody .run-timeline-feed-item')).toHaveCount(5);
    await expect(page.locator('#runTimelineBody')).toContainText('Node completed');

    await page.locator('#runTimelineOpenModelerBtn').click();
    await expect(page.locator('#runTimelineBackdrop')).not.toHaveClass(/open/);
    await expect(page.locator('#modelerView')).toHaveClass(/active/);
    await expect(page.locator('#workflowId')).toHaveValue(workflowId);
  });
});
