const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
  clickNodeById,
  selectEdge,
  modelState,
  dragNodeTo,
  dragConnection,
  dragPaletteItemToCanvas,
  getViewportState,
  jsonPanelValue,
  clickCanvasToolbarButton,
  nodeById,
  connectionHandle,
  simpleConnectionWorkflow,
  connectedWorkflow,
} = require('./helpers/modeler');

test.describe('FlowFoundry canvas viewport', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('zooms in and out from viewport controls', async ({ page }) => {
    const before = await getViewportState(page);

    await page.locator('#zoomInBtn').click();
    const zoomedIn = await getViewportState(page);
    expect(zoomedIn.scale).toBeGreaterThan(before.scale);
    await expect(page.locator('#zoomLevel')).not.toHaveText('100%');

    await page.locator('#zoomOutBtn').click();
    const zoomedOut = await getViewportState(page);
    expect(zoomedOut.scale).toBeLessThan(zoomedIn.scale);
  });

  test('fits the full diagram into the viewport', async ({ page }) => {
    await page.locator('#zoomInBtn').click();
    const before = await getViewportState(page);
    await page.locator('#fitViewBtn').click();
    const after = await getViewportState(page);
    expect(after.scale).not.toBe(before.scale);
    await expect(page.locator('#zoomLevel')).not.toHaveText(`${Math.round(before.scale * 100)}%`);
  });

  test('locks and unlocks viewport navigation', async ({ page }) => {
    await page.locator('#viewportLockBtn').click();
    await expect(page.locator('#message')).toContainText('View locked');
    await expect(page.locator('#zoomInBtn')).toBeDisabled();
    await expect(page.locator('#zoomOutBtn')).toBeDisabled();
    await expect(page.locator('#fitViewBtn')).toBeDisabled();
    await expect(page.locator('#canvas')).toHaveClass(/viewport-locked/);

    await page.locator('#viewportLockBtn').click();
    await expect(page.locator('#message')).toContainText('View unlocked');
    await expect(page.locator('#zoomInBtn')).toBeEnabled();
  });

  test('pans the canvas by dragging empty space', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    const before = await getViewportState(page);
    const canvas = page.locator('#canvas');
    const box = await canvas.boundingBox();
    if (!box) throw new Error('Canvas is not visible');

    await page.mouse.move(box.x + box.width - 80, box.y + box.height - 80);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width - 220, box.y + box.height - 180, { steps: 10 });
    await page.mouse.up();

    const after = await getViewportState(page);
    expect(Math.abs(after.panX - before.panX) + Math.abs(after.panY - before.panY)).toBeGreaterThan(20);
    await expect(canvas).not.toHaveClass(/is-panning/);
  });

  test('navigates via minimap click', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    const before = await getViewportState(page);
    const minimap = page.locator('#minimap');
    const box = await minimap.boundingBox();
    if (!box) throw new Error('Minimap is not visible');

    await page.mouse.click(box.x + box.width * 0.85, box.y + box.height * 0.85);
    const after = await getViewportState(page);
    expect(Math.abs(after.panX - before.panX) + Math.abs(after.panY - before.panY)).toBeGreaterThan(10);
    await expect(page.locator('#minimapViewport')).toBeVisible();
  });

  test('CV-19 clamps zoom between 20% and 150%', async ({ page }) => {
    for (let i = 0; i < 30; i += 1) {
      await page.locator('#zoomInBtn').click();
    }
    let viewport = await getViewportState(page);
    expect(viewport.scale).toBeLessThanOrEqual(1.5 + 0.001);
    await expect(page.locator('#zoomLevel')).toHaveText('150%');

    for (let i = 0; i < 40; i += 1) {
      await page.locator('#zoomOutBtn').click();
    }
    viewport = await getViewportState(page);
    expect(viewport.scale).toBeGreaterThanOrEqual(0.2 - 0.001);
    await expect(page.locator('#zoomLevel')).toHaveText('20%');
  });

  test('CV-20 auto fits diagram when switching to modeler view', async ({ page }) => {
    await page.locator('#navWorkflows').click();
    await expect(page.locator('#workflowListView')).toHaveClass(/active/);

    await page.locator('#navModeler').click();
    await expect(page.locator('#modelerView')).toHaveClass(/active/);
    await page.waitForTimeout(120);

    const viewport = await getViewportState(page);
    expect(viewport.scale).not.toBe(1);
    await expect(page.locator('#zoomLevel')).not.toHaveText('100%');
  });

  test('CV-21 keeps viewport controls inside canvas-chrome overlay', async ({ page }) => {
    const chrome = page.locator('.canvas-chrome');
    await expect(chrome).toBeVisible();
    await expect(chrome.locator('#zoomInBtn')).toBeVisible();
    await expect(chrome.locator('#zoomOutBtn')).toBeVisible();
    await expect(chrome.locator('#fitViewBtn')).toBeVisible();
    await expect(chrome.locator('#minimap')).toBeVisible();
  });

  test('CV-22 renders sequence flows with dashed animated edge lines', async ({ page }) => {
    await importModel(page, connectedWorkflow());
    const edgeLine = page.locator('#edges path.edge-line').first();
    await expect(edgeLine).toHaveCount(1);

    const style = await edgeLine.evaluate(el => {
      const computed = window.getComputedStyle(el);
      return {
        strokeDasharray: computed.strokeDasharray,
        animationName: computed.animationName,
      };
    });

    expect(style.strokeDasharray).not.toBe('none');
    expect(style.strokeDasharray).toContain('8');
    expect(style.animationName).toBe('edge-flow');
  });
});

test.describe('FlowFoundry canvas connections and edges', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('creates a sequence flow by dragging connection handles', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    const before = await page.locator('#edges path.edge-hit').count();

    await dragConnection(page, 'StartEvent', 'right', 'Task_A', 'left');

    await expect(page.locator('#edges path.edge-hit')).toHaveCount(before + 1);
    await expect(page.locator('#message')).toContainText('Sequence flow created');
    await expect(page.locator('#propType')).toContainText('SequenceFlow');

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.edges.some(edge => edge.from === 'StartEvent' && edge.to === 'Task_A')).toBe(true);
  });

  test('selects and deletes a sequence flow', async ({ page }) => {
    await importModel(page, connectedWorkflow());
    const before = await page.locator('#edges path.edge-hit').count();

    await selectEdge(page, 'F_Start_TaskA');
    await page.locator('#deleteBtn').click();

    await expect(page.locator('#edges path.edge-hit')).toHaveCount(before - 1);
    await expect(page.locator('#message')).toContainText('Sequence flow deleted');
  });

  test('shows edge endpoint handles when a sequence flow is selected', async ({ page }) => {
    await importModel(page, connectedWorkflow());
    await selectEdge(page, 'F_Start_TaskA');

    await expect(page.locator('.edge-endpoint-handle')).toHaveCount(2);
    await expect(page.locator('.edge-endpoint-handle').first()).toHaveAttribute('title', 'Drag to change sequence flow source');
    await expect(page.locator('.edge-endpoint-handle').nth(1)).toHaveAttribute('title', 'Drag to change sequence flow target');
  });

  test('updates edge condition from the properties panel', async ({ page }) => {
    await importModel(page, connectedWorkflow());
    await selectEdge(page, 'F_Start_TaskA');

    await page.locator('#properties button', { hasText: 'FEEL' }).click();
    await page.locator('#properties input[placeholder="${amount > 1000}"]').fill('${approved == true}');

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.edges[0].condition).toBe('${approved == true}');
  });
});

test.describe('FlowFoundry canvas node interactions', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('drags a node and persists the new coordinates in DSL', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    const node = nodeById(page, 'Task_A');
    const before = await node.boundingBox();
    if (!before) throw new Error('Task node is not visible');

    await page.mouse.move(before.x + before.width / 2, before.y + before.height / 2);
    await page.mouse.down();
    await page.mouse.move(before.x + 120, before.y + 60, { steps: 10 });
    await page.mouse.up();

    const model = await modelState(page);
    const task = model.nodes.find(n => n.id === 'Task_A');
    expect(task.x).toBeGreaterThan(360);
    expect(task.y).toBeGreaterThan(158);
  });

  test('deletes a selected node from the toolbar', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    await clickNodeById(page, 'Task_B');
    await page.locator('#deleteBtn').click();

    await expect(nodeById(page, 'Task_B')).toHaveCount(0);
    await expect(page.locator('#message')).toContainText('Node deleted');
  });

  test('deletes the selected node with the keyboard', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    await clickNodeById(page, 'Task_B');
    await page.keyboard.press('Delete');

    await expect(nodeById(page, 'Task_B')).toHaveCount(0);
  });

  test('morphs a service task into a user task from the floating toolbar', async ({ page }) => {
    await importModel(page, simpleConnectionWorkflow());
    await clickNodeById(page, 'Task_A');

    await page.evaluate(() => morphTaskType('Task_A', 'userTask'));

    await expect(page.locator('#propType')).toContainText('userTask');
    await expect(nodeById(page, 'Task_A')).toContainText('Human Task');
  });

  test('drops a palette service task onto the canvas', async ({ page }) => {
    const before = await page.locator('.node').count();
    await dragPaletteItemToCanvas(page, 'Service Task');

    await expect(page.locator('.node')).toHaveCount(before + 1);
    await expect(page.locator('#message')).toContainText('Node added');
    await expect(page.locator('#propType')).toContainText('serviceTask');
  });

  test('creates and edits a text annotation on the canvas', async ({ page }) => {
    await dragPaletteItemToCanvas(page, 'Text Annotation');
    const annotation = page.locator('.annotation-node').last();
    await expect(annotation).toBeVisible();

    const editor = annotation.locator('.annotation-editor');
    await editor.fill('Outbound review note');
    await expect(editor).toHaveValue('Outbound review note');

    const model = await modelState(page);
    const note = model.nodes.find(n => n.kind === 'textAnnotation');
    expect(note.documentation).toContain('Outbound review note');
  });
});

test.describe('FlowFoundry canvas chrome and navigation', () => {
  test.beforeEach(async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
  });

  test('collapses and expands the palette panel', async ({ page }) => {
    await expect(page.locator('#workbench')).not.toHaveClass(/palette-collapsed/);
    await page.locator('#paletteToggleBtn').click();
    await expect(page.locator('#workbench')).toHaveClass(/palette-collapsed/);

    await page.locator('#paletteExpandTab').click();
    await expect(page.locator('#workbench')).not.toHaveClass(/palette-collapsed/);
  });

  test('collapses and expands the nav and properties panels', async ({ page }) => {
    await expect(page.locator('#app')).not.toHaveClass(/nav-collapsed/);
    await page.locator('#navToggleBtn').click();
    await expect(page.locator('#app')).toHaveClass(/nav-collapsed/);

    await page.locator('#navExpandTab').click();
    await expect(page.locator('#app')).not.toHaveClass(/nav-collapsed/);

    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);
    await page.locator('#propertiesExpandTab').click();
    await expect(page.locator('#app')).not.toHaveClass(/properties-collapsed/);

    await page.locator('#propertiesToggleBtn').click();
    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);

    await page.locator('#propertiesExpandTab').click();
    await expect(page.locator('#app')).not.toHaveClass(/properties-collapsed/);
  });

  test('starts with properties collapsed when entering modeler', async ({ page }) => {
    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);
    await expect(page.locator('#propertiesExpandTab')).toBeVisible();

    await page.locator('#navWorkflows').click();
    await page.locator('#navModeler').click();
    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);
  });

  test('auto-expands properties when selecting a node or edge', async ({ page }) => {
    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);

    await page.locator('.node').first().click();
    await expect(page.locator('#app')).not.toHaveClass(/properties-collapsed/);
    await expect(page.locator('#propertiesPanel')).toBeVisible();

    await page.locator('#propertiesToggleBtn').click({ force: true });
    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);

    const edgeHit = page.locator('#edges .edge-hit').first();
    await edgeHit.click({ force: true });
    await expect(page.locator('#app')).not.toHaveClass(/properties-collapsed/);
  });

  test('switches between workflow list and modeler canvas', async ({ page }) => {
    await page.locator('#navWorkflows').click();
    await expect(page.locator('#workflowListView')).toHaveClass(/active/);
    await expect(page.locator('#app')).not.toHaveClass(/modeler-view/);
    await expect(page.locator('#propertiesPanel')).toBeHidden();
    await expect(page.locator('#workflowTable')).toBeVisible();

    await page.locator('#workflowTable button', { hasText: 'Open' }).first().click();
    await expect(page.locator('#modelerView')).toHaveClass(/active/);
    await expect(page.locator('#app')).toHaveClass(/modeler-view/);
    await expect(page.locator('#app')).toHaveClass(/properties-collapsed/);
    await expect(page.locator('#canvas')).toBeVisible();
    await expect(page.locator('.node').first()).toBeVisible();
  });

  test('imports a model through the toolbar prompt and round-trips through export', async ({ page }) => {
    const model = simpleConnectionWorkflow();
    page.once('dialog', dialog => dialog.accept(JSON.stringify({ model })));
    await page.getByRole('button', { name: 'Import' }).click();
    await expect(page.locator('.node')).toHaveCount(model.nodes.length);

    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    expect(dsl.nodes).toHaveLength(model.nodes.length);
    await page.getByRole('button', { name: 'Close' }).click();

    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Export' }).click();
    const exported = await jsonPanelValue(page);
    expect(exported.model.nodes.map(n => n.id).sort()).toEqual(model.nodes.map(n => n.id).sort());
  });
});
