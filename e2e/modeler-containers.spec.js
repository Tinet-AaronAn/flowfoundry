const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModelerWithOutboundDemo,
  importModel,
  clickNode,
  clickNodeById,
  dragNodeTo,
  jsonPanelValue,
  nodeLocator,
  participantWorkflow,
  invalidParticipantWorkflow,
  participantAnnotationWorkflow,
  partialContainmentWorkflow,
  clickCanvasToolbarButton,
} = require('./helpers/modeler');

test.describe('FlowFoundry participant and sub-process containers', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModelerWithOutboundDemo(page);
  });

  test('renders participant with a left label lane and right content area', async ({ page }) => {
    await importModel(page, participantWorkflow());

    const participant = nodeLocator(page, 'Ops Team');
    await expect(participant.locator('.participant-label')).toHaveText('Ops Team');
    await expect(participant.locator('.participant-content-area')).toBeVisible();
    await expect(participant.locator('.participant-caption')).toContainText('ops-team');
  });

  test('creates participant with the larger default pool size', async ({ page }) => {
    const before = await page.locator('.node').count();
    const paletteItem = page.locator('.palette-item', { hasText: 'Participant' });
    const canvas = page.locator('#canvas');
    const canvasBox = await canvas.boundingBox();
    if (!canvasBox) throw new Error('Canvas is not visible');

    await paletteItem.dragTo(canvas, {
      targetPosition: { x: canvasBox.width / 2, y: canvasBox.height / 2 },
    });
    await expect(page.locator('.node.participant-node')).toHaveCount(1, { timeout: 10_000 });
    const size = await page.evaluate(() => {
      const node = state.model.nodes.filter(n => n.kind === 'participant').at(-1);
      const dims = nodeSize(node);
      return { width: node.width || dims.width, height: node.height || dims.height };
    });
    expect(size.width).toBe(2520);
    expect(size.height).toBe(1040);
  });

  test('resizes participant and keeps contained node ownership valid', async ({ page }) => {
    await importModel(page, participantWorkflow());
    await clickNodeById(page, 'Participant_Ops');

    const participant = nodeLocator(page, 'Ops Team');
    await expect(participant).toBeVisible();
    const beforeWidth = await page.evaluate(() => state.model.nodes.find(n => n.id === 'Participant_Ops').width);
    const handle = page.locator('.node.selected .container-resize-handle.se');
    await expect(handle).toBeVisible();
    const handleBox = await handle.boundingBox();
    if (!handleBox) throw new Error('Participant resize handle not visible');

    const startX = handleBox.x + handleBox.width / 2;
    const startY = handleBox.y + handleBox.height / 2;
    const endX = startX + 420;
    const endY = startY + 220;
    await page.evaluate(({ startX, startY, endX, endY }) => {
      const fakeDown = {
        clientX: startX,
        clientY: startY,
        preventDefault() {},
        stopPropagation() {},
      };
      startContainerResize(fakeDown, 'Participant_Ops', 'se');
      window.dispatchEvent(new MouseEvent('mousemove', { clientX: endX, clientY: endY, bubbles: true }));
      window.dispatchEvent(new MouseEvent('mouseup', { clientX: endX, clientY: endY, bubbles: true }));
    }, { startX, startY, endX, endY });

    await expect.poll(async () =>
      page.evaluate(() => state.model.nodes.find(n => n.id === 'Participant_Ops').width)
    ).toBeGreaterThan(beforeWidth + 40);

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.nodes.every(n => n.config.flowFoundryParticipant?.participantId === 'Participant_Ops')).toBe(true);
  });

  test('auto layout preserves participant and sub-process containment', async ({ page }) => {
    await importModel(page, participantWorkflow());

    await page.locator('#modelerView .toolbar').first().getByRole('button', { name: 'Auto Layout' }).click();

    const layout = await page.evaluate(() => {
      const byId = Object.fromEntries(state.model.nodes.map(node => [node.id, node]));
      const center = node => {
        const size = nodeSize(node);
        return { x: node.x + size.width / 2, y: node.y + size.height / 2 };
      };
      return {
        subProcessInsideParticipant: pointInBounds(center(byId.Sub_Process), participantContentBounds(byId.Participant_Ops)),
        subTaskInsideSubProcess: pointInBounds(center(byId.Sub_Task), nodeBounds(byId.Sub_Process)),
        subProcessParticipantId: byId.Sub_Process.participantId,
        subTaskParticipantId: byId.Sub_Task.participantId,
        subTaskParentSubProcessId: byId.Sub_Task.parentSubProcessId,
        edgeStillConnectsSubTask: state.model.edges.some(edge => edge.from === 'Sub_Task' && edge.to === 'EndEvent'),
      };
    });

    expect(layout.subProcessInsideParticipant).toBe(true);
    expect(layout.subTaskParentSubProcessId).toBe('Sub_Process');
    expect(layout.subProcessParticipantId).toBe('Participant_Ops');
    expect(layout.subTaskParticipantId).toBe('Participant_Ops');
    expect(layout.edgeStillConnectsSubTask).toBe(true);
  });

  test('rejects DSL when participant mode has nodes outside all participants, then passes after dragging inside', async ({ page }) => {
    await importModel(page, invalidParticipantWorkflow());

    await clickCanvasToolbarButton(page, 'View DSL');
    await expect(page.locator('#appNotice')).toContainText('Violations');

    const contentArea = nodeLocator(page, 'Participant A').locator('.participant-content-area');
    const contentBox = await contentArea.boundingBox();
    if (!contentBox) throw new Error('Participant content area not visible');

    await dragNodeTo(page, 'Outside Task', {
      x: contentBox.x + contentBox.width / 2,
      y: contentBox.y + contentBox.height / 2,
    });

    await page.evaluate(() => {
      const task = state.model.nodes.find(node => node.id === 'Task_Outside');
      task.x = 180;
      task.y = 200;
      syncParticipantAssignments();
      renderAll();
    });

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.nodes.find(n => n.id === 'Task_Outside').config.flowFoundryParticipant.participantId).toBe('Participant_A');
  });

  test('sub-process remains structural while inner runtime nodes compile inside participant', async ({ page }) => {
    await importModel(page, participantWorkflow());

    await clickNodeById(page, 'Sub_Process');
    await expect(page.locator('#propType')).toContainText('subProcess');
    await expect(page.locator('.node.selected .container-resize-handle.se')).toBeVisible();

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);

    expect(dsl.nodes.some(n => n.id === 'Sub_Process')).toBe(false);
    expect(dsl.nodes.some(n => n.id === 'Sub_Start')).toBe(true);
    expect(dsl.nodes.some(n => n.id === 'Sub_End')).toBe(true);
    expect(dsl.nodes.find(n => n.id === 'Sub_Task').config.flowFoundryParticipant.participantId).toBe('Participant_Ops');
  });

  test('allows deleting start and end nodes inside a sub-process', async ({ page }) => {
    await importModel(page, participantWorkflow());

    await clickNodeById(page, 'Sub_Start');
    await page.locator('#deleteBtn').click();
    await expect(page.locator('.node', { hasText: 'Sub Start' })).toHaveCount(0);

    await clickNodeById(page, 'Sub_End');
    await page.locator('#deleteBtn').click();
    await expect(page.locator('.node', { hasText: 'Sub End' })).toHaveCount(0);
  });

  test('creates an empty sub-process container without default start or end nodes', async ({ page }) => {
    const before = await page.locator('.node').count();
    const paletteItem = page.locator('.palette-item', { hasText: 'Sub-process' });
    const canvas = page.locator('#canvas');
    const canvasBox = await canvas.boundingBox();
    if (!canvasBox) throw new Error('Canvas is not visible');

    await paletteItem.dragTo(canvas, {
      targetPosition: { x: canvasBox.width / 2, y: canvasBox.height / 2 },
    });
    await expect(page.locator('.node.subprocess-node')).toHaveCount(1, { timeout: 10_000 });
    const size = await page.evaluate(() => {
      const node = state.model.nodes.filter(n => n.kind === 'subProcess').at(-1);
      const dims = nodeSize(node);
      return { width: node.width || dims.width, height: node.height || dims.height, id: node.id };
    });
    expect(size.width).toBe(840);
    expect(size.height).toBe(520);
    await clickNodeById(page, size.id);
    await expect(page.locator('#properties input').nth(2)).toHaveValue('840');
    await expect(page.locator('#properties input').nth(3)).toHaveValue('520');
    await expect(page.locator('.node', { hasText: 'Start' })).toHaveCount(0);
    await expect(page.locator('.node', { hasText: 'End' })).toHaveCount(0);
  });

  test('CT-09 rejects partial participant overlap until the node is fully contained', async ({ page }) => {
    await importModel(page, partialContainmentWorkflow());
    await page.locator('#fitViewBtn').click();

    let assignment = await page.evaluate(() => {
      const task = state.model.nodes.find(node => node.id === 'Task_Edge');
      return task.participantId || null;
    });
    expect(assignment).toBeNull();

    const contentArea = nodeLocator(page, 'Edge Pool').locator('.participant-content-area');
    const contentBox = await contentArea.boundingBox();
    if (!contentBox) throw new Error('Participant content area not visible');

    await dragNodeTo(page, 'Edge Task', {
      x: contentBox.x + contentBox.width / 2,
      y: contentBox.y + contentBox.height / 2,
    });

    assignment = await page.evaluate(() => state.model.nodes.find(node => node.id === 'Task_Edge').participantId);
    expect(assignment).toBe('Participant_Edge');
  });

  test('CT-10 moves annotation together when participant container is dragged', async ({ page }) => {
    await importModel(page, participantAnnotationWorkflow());
    await page.locator('#fitViewBtn').click();

    const before = await page.evaluate(() => {
      const participant = state.model.nodes.find(node => node.id === 'Participant_Notes');
      const annotation = state.model.nodes.find(node => node.id === 'Annotation_1');
      return {
        participantX: participant.x,
        participantY: participant.y,
        annotationX: annotation.x,
        annotationY: annotation.y,
        deltaX: annotation.x - participant.x,
        deltaY: annotation.y - participant.y,
      };
    });

    const participant = nodeLocator(page, 'Notes Team');
    const box = await participant.boundingBox();
    if (!box) throw new Error('Participant is not visible');

    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2 + 180, box.y + box.height / 2 + 120, { steps: 12 });
    await page.mouse.up();

    const after = await page.evaluate(() => {
      const participant = state.model.nodes.find(node => node.id === 'Participant_Notes');
      const annotation = state.model.nodes.find(node => node.id === 'Annotation_1');
      return {
        participantX: participant.x,
        participantY: participant.y,
        annotationX: annotation.x,
        annotationY: annotation.y,
      };
    });

    expect(after.participantX - before.participantX).toBeGreaterThan(100);
    expect(after.annotationX - before.annotationX).toBeGreaterThan(100);
    expect(after.annotationX - after.participantX).toBeCloseTo(before.deltaX, 0);
    expect(after.annotationY - after.participantY).toBeCloseTo(before.deltaY, 0);
  });
});
