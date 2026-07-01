const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
  dragNodeTo,
  jsonPanelValue,
  nodeLocator,
  participantWorkflow,
  invalidParticipantWorkflow,
  clickCanvasToolbarButton,
} = require('./helpers/modeler');

test.describe('FlowFoundry participant and sub-process containers', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('renders participant with a left label lane and right content area', async ({ page }) => {
    await importModel(page, participantWorkflow());

    const participant = nodeLocator(page, '运营团队');
    await expect(participant.locator('.participant-label')).toHaveText('运营团队');
    await expect(participant.locator('.participant-content-area')).toBeVisible();
    await expect(participant.locator('.participant-caption')).toContainText('ops-team');
  });

  test('resizes participant and keeps contained node ownership valid', async ({ page }) => {
    await importModel(page, participantWorkflow());
    await clickNode(page, '运营团队');

    const participant = nodeLocator(page, '运营团队');
    const before = await participant.boundingBox();
    const handle = page.locator('.node.selected .container-resize-handle.se');
    await expect(handle).toBeVisible();
    const handleBox = await handle.boundingBox();
    if (!before || !handleBox) throw new Error('Participant resize handle not visible');

    await page.mouse.move(handleBox.x + handleBox.width / 2, handleBox.y + handleBox.height / 2);
    await page.mouse.down();
    await page.mouse.move(handleBox.x + 180, handleBox.y + 90, { steps: 10 });
    await page.mouse.up();

    const after = await participant.boundingBox();
    expect(after.width).toBeGreaterThan(before.width + 100);
    expect(after.height).toBeGreaterThan(before.height + 50);

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.nodes.every(n => n.config.flowFoundryParticipant?.participantId === 'Participant_Ops')).toBe(true);
  });

  test('rejects DSL when participant mode has nodes outside all participants, then passes after dragging inside', async ({ page }) => {
    await importModel(page, invalidParticipantWorkflow());

    await clickCanvasToolbarButton(page, 'View DSL');
    await expect(page.locator('#message')).toContainText('未归属节点');

    const contentArea = nodeLocator(page, '参与方 A').locator('.participant-content-area');
    const contentBox = await contentArea.boundingBox();
    if (!contentBox) throw new Error('Participant content area not visible');

    await dragNodeTo(page, 'Outside Task', {
      x: contentBox.x + contentBox.width - 120,
      y: contentBox.y + contentBox.height / 2,
    });

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.nodes.find(n => n.id === 'Task_Outside').config.flowFoundryParticipant.participantId).toBe('Participant_A');
  });

  test('sub-process remains structural while inner runtime nodes compile inside participant', async ({ page }) => {
    await importModel(page, participantWorkflow());

    const subProcess = page.locator('.node.subprocess-node', { hasText: '子流程' }).first();
    await subProcess.click({ position: { x: 35, y: 18 }, force: true });
    await expect(subProcess).toHaveClass(/selected/);
    await expect(page.locator('#propType')).toContainText('subProcess');
    await expect(page.locator('.node.selected .container-resize-handle.se')).toBeVisible();

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);

    expect(dsl.nodes.some(n => n.id === 'Sub_Process')).toBe(false);
    expect(dsl.nodes.some(n => n.id === 'Sub_Start')).toBe(false);
    expect(dsl.nodes.some(n => n.id === 'Sub_End')).toBe(false);
    expect(dsl.nodes.find(n => n.id === 'Sub_Task').config.flowFoundryParticipant.participantId).toBe('Participant_Ops');
  });
});
