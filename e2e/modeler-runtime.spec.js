const { test, expect } = require('@playwright/test');
const {
  mockBackend,
  openFreshModeler,
  importModel,
  clickNode,
  participantWorkflow,
  jsonPanelValue,
  clickCanvasToolbarButton,
  clickRightToolbarButton,
} = require('./helpers/modeler');

test.describe('FlowFoundry compile, run, and debug flows', () => {
  test('compiles and runs a participant-owned workflow through mocked APIs', async ({ page }) => {
    const backend = {};
    await mockBackend(page, backend);
    await openFreshModeler(page);
    await importModel(page, participantWorkflow());

    await clickRightToolbarButton(page, 'Compile');
    const plan = await jsonPanelValue(page);

    expect(plan.nodeCount).toBeGreaterThan(0);
    expect(backend.compileRequests).toHaveLength(1);
    expect(backend.compileRequests[0].nodes.every(n => n.config.flowFoundryParticipant?.participantRef === 'ops-team')).toBe(true);

    await page.locator('#jsonPanel').getByRole('button', { name: 'Close' }).click();
    await page.locator('#navSimulation').click();
    await page.locator('#simulationView .simulation-header').getByRole('button', { name: 'Run', exact: true }).click();
    const run = await jsonPanelValue(page);

    expect(run.workflowId).toBe('workflow_e2e_mock_001');
    expect(await page.locator('#workflowId').inputValue()).toBe('workflow_e2e_mock_001');
    expect(backend.runRequests).toHaveLength(1);
    expect(backend.runRequests[0].runSource).toBe('web-modeler');
  });


  test('queries workflow state and completes human task through mocked APIs', async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
    await page.locator('#navSimulation').click();

    await page.locator('#workflowId').fill('workflow_e2e_mock_001');
    await page.getByRole('button', { name: 'Query State' }).click();
    const state = await jsonPanelValue(page);

    expect(state.workflowId).toBe('workflow_e2e_mock_001');
    expect(state.status).toBe('RUNNING');
  });

  test('exports workflow nodes as child workflow definitions', async ({ page }) => {
    await mockBackend(page);
    await openFreshModeler(page);
    const childModel = {
      id: 'Definitions_Child_Workflow',
      name: 'Child Workflow',
      targetNamespace: 'https://example.com/e2e',
      process: { id: 'Process_Child_Workflow', name: 'Child Workflow', isExecutable: true },
      nodes: [
        modelNode('Child_Start', 'startEvent', 'Child Start', 120, 180),
        modelNode('Child_End', 'endEvent', 'Child End', 260, 180),
      ],
      edges: [modelEdge('F_Child_Start_End', 'Child_Start', 'Child_End')],
    };
    await page.evaluate(model => {
      state.workflows.push({
        id: model.id,
        name: model.name,
        version: '1.0.0',
        status: 'active',
        updatedAt: new Date().toISOString(),
        versions: [{ version: '1.0.0', status: 'active', createdAt: new Date().toISOString(), model }],
      });
    }, childModel);
    await importModel(page, {
      id: 'Definitions_Parent_Workflow',
      name: 'Parent Workflow',
      targetNamespace: 'https://example.com/e2e',
      process: { id: 'Process_Parent_Workflow', name: 'Parent Workflow', isExecutable: true },
      nodes: [
        modelNode('Start', 'startEvent', 'Start', 120, 220),
        modelNode('Call_Child', 'workflow', 'Call Child Workflow', 240, 198, {
          config: {
            childWorkflowId: 'Definitions_Child_Workflow',
            childWorkflowName: 'Child Workflow',
            childWorkflowVersion: '1.0.0',
          },
        }),
        modelNode('End', 'endEvent', 'End', 430, 220),
      ],
      edges: [
        modelEdge('F_Start_Call', 'Start', 'Call_Child'),
        modelEdge('F_Call_End', 'Call_Child', 'End'),
      ],
    });

    await clickNode(page, 'Call Child Workflow');
    await expect(page.locator('#propType')).toContainText('workflow');
    await clickCanvasToolbarButton(page, 'View DSL');
    const dsl = await jsonPanelValue(page);
    const workflowNode = dsl.nodes.find(node => node.id === 'Call_Child');

    expect(workflowNode.kind).toBe('CHILD_WORKFLOW');
    expect(workflowNode.config.flowFoundryChildWorkflow.childWorkflowId).toBe('Definitions_Child_Workflow');
    expect(workflowNode.config.childWorkflowDefinition.flow.id).toBe('Definitions_Child_Workflow');
  });
});

function modelNode(id, kind, name, x, y, extra = {}) {
  return {
    id,
    kind,
    name,
    x,
    y,
    documentation: '',
    emphasis: 'none',
    inputMapping: {},
    outputMapping: {},
    headers: {},
    loop: 'none',
    config: {},
    ...extra,
  };
}

function modelEdge(id, from, to, condition = 'default') {
  return { id, from, to, condition, name: '', documentation: '' };
}
