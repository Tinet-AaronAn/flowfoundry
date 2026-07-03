const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  jsonPanelValue,
} = require('./helpers/modeler');

async function openSimulation(page) {
  await page.locator('#navSimulation').click();
  await expect(page.locator('#simulationView')).toHaveClass(/active/);
}

test.describe('FlowFoundry execution runs', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('keeps runtime controls out of the properties panel', async ({ page }) => {
    await expect(page.locator('#propertiesPanel')).not.toContainText('Business Input JSON');
    await expect(page.locator('#executionConsole')).toHaveCount(0);
    await openSimulation(page);
    await expect(page.locator('#runInput')).toBeVisible();
    await expect(page.locator('#appNotice')).toBeVisible();
    await expect(page.locator('#appNotice')).toHaveClass(/app-notice/);
  });

  test('records a run and lists it on the Runs page', async ({ page }) => {
    await openSimulation(page);
    await page.locator('#simulationView .simulation-header').getByRole('button', { name: 'Run', exact: true }).click();
    const run = await jsonPanelValue(page);
    await page.locator('#jsonPanel').getByRole('button', { name: 'Close' }).click();

    await expect(page.locator('#workflowId')).toHaveValue(run.workflowId);
    await page.locator('#navRuns').click();
    await expect(page.locator('#runsView')).toHaveClass(/active/);
    await expect(page.locator('#runsTable')).toContainText(run.workflowId);
    await expect(page.locator('#propertiesPanel')).toBeHidden();

    await page.locator('#runsTable .runs-exec-link').click();
    await expect(page.locator('#simulationView')).toHaveClass(/active/);
    await expect(page.locator('#workflowId')).toHaveValue(run.workflowId);
  });
});
