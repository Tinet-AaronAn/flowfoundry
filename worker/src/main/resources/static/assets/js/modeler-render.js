      async function loadActivities() {
        try {
          const res = await fetch('/api/activities');
          const data = await res.json();
          state.activities = data.activities || [];
        } catch (err) {
          message('无法加载 Activity Registry：' + err.message);
        }
        loadFromMemory();
        renderAll();
      }

      function renderAll() {
        renderPalette();
        renderCanvas();
        renderProperties();
        renderWorkflowList();
        renderNavigation();
        updateButtons();
      }

      function renderNavigation() {
        $('navWorkflows').classList.toggle('active', state.currentView === 'workflows');
        $('navModeler').classList.toggle('active', state.currentView === 'modeler');
        $('navDebug').classList.toggle('active', state.currentView === 'debug');
      }

      function switchView(view) {
        state.currentView = view;
        $('workflowListView').classList.toggle('active', view === 'workflows');
        $('modelerView').classList.toggle('active', view === 'modeler');
        $('debugView').classList.toggle('active', view === 'debug');
        renderNavigation();
        if (view === 'workflows') renderWorkflowList();
        if (view === 'debug') resetSimulation(false);
      }

      function renderPalette() {
        const keyword = $('paletteSearch').value.trim().toLowerCase();
        $('paletteItems').innerHTML = paletteGroups.map(group => {
          const items = group.items.filter(item => item.label.toLowerCase().includes(keyword));
          if (items.length === 0) return '';
          return `<details open><summary>${group.name}</summary>${items.map(item => `
            <button class="palette-item" draggable="true" data-item="${escapeAttr(encodeURIComponent(JSON.stringify(item)))}" ondragstart="startPaletteDrag(event, this.dataset.item)" ondragend="endPaletteDrag(event)">
              ${glyph(item.kind)} <span>${item.label}</span>
            </button>
          `).join('')}</details>`;
        }).join('');
      }

      function glyph(kind) {
        if (kind === 'startEvent') return '<span class="glyph event"></span>';
        if (kind === 'endEvent') return '<span class="glyph event end"></span>';
        if (kind === 'intermediateCatchEvent' || kind === 'boundaryEvent') return '<span class="glyph event intermediate"></span>';
        if (kind.includes('Gateway')) return `<span class="glyph gateway"><span>${gatewaySymbol(kind)}</span></span>`;
        if (kind === 'serviceTask') return '<span class="glyph service">⚙</span>';
        if (kind === 'userTask') return '<span class="glyph user">♙</span>';
        if (kind === 'manualTask') return '<span class="glyph manual">☞</span>';
        if (kind === 'sendTask') return '<span class="glyph send">✉</span>';
        if (kind === 'receiveTask') return '<span class="glyph receive">▱</span>';
        if (kind === 'scriptTask') return '<span class="glyph script">𝑓</span>';
        if (kind === 'businessRuleTask') return '<span class="glyph rule">▦</span>';
        if (['subProcess','participant'].includes(kind)) return `<span class="glyph structural">${structuralGlyph(kind)}</span>`;
        if (kind === 'textAnnotation') return '<span class="glyph annotation">▤</span>';
        return '<span class="glyph task"></span>';
      }

      function renderCanvas() {
        const content = $('canvasContent');
        resizeCanvasToModel();
        content.style.transform = `scale(${state.scale})`;
        [...content.querySelectorAll('.node')].forEach(el => el.remove());
        for (const current of state.model.nodes) {
          const el = document.createElement('div');
          el.className = `node ${nodeClass(current)} ${state.selected.type === 'node' && state.selected.id === current.id ? 'selected' : ''} ${state.connectionSource === current.id ? 'connecting-source' : ''}`;
          const size = nodeSize(current);
          el.style.left = `${current.x}px`;
          el.style.top = `${current.y}px`;
          el.style.width = `${size.width}px`;
          el.style.height = `${size.height}px`;
          el.innerHTML = nodeHtml(current) + nodeToolbarHtml(current);
          el.onclick = evt => {
            evt.stopPropagation();
            state.suppressCanvasClick = false;
            select('node', current.id);
          };
          makeDraggable(el, current);
          content.appendChild(el);
        }
        $('canvas').onclick = () => {
          if (state.suppressCanvasClick) {
            state.suppressCanvasClick = false;
            return;
          }
          select('process', null);
        };
        renderEdges();
      }

      function nodeClass(current) {
        if (current.kind.includes('Gateway')) return 'gateway-node';
        if (current.kind.includes('Event')) return 'event-node';
        if (current.kind === 'subProcess') return 'subprocess-node';
        if (current.kind === 'participant') return 'participant-node';
        if (current.kind === 'textAnnotation') return 'annotation-node';
        return 'task-node';
      }

      function nodeHtml(current) {
        const handles = nodeHandlesHtml(current);
        if (current.kind.includes('Gateway')) {
          return `<div class="gateway-shape"><div class="gateway-symbol">${gatewaySymbol(current.kind)}</div><div class="gateway-label">${current.name || current.id}</div></div>${handles}`;
        }
        if (current.kind.includes('Event')) {
          const labelClass = current.kind === 'intermediateCatchEvent' || current.kind === 'boundaryEvent' ? 'timer-label' : 'event-label';
          return `<div class="event-shape ${current.kind} ${current.kind === 'boundaryEvent' ? 'boundary-event' : ''}"></div><div class="${labelClass}">${current.name || current.id}</div>${handles}`;
        }
        if (current.kind === 'participant') {
          const ref = current.config?.participantRef || 'pool / lane';
          return `<div class="participant-shape"><div class="participant-label">${current.name || current.id}</div><div class="participant-content-area"><div class="participant-caption">${escapeHtml(ref)} <span>${participantNodeCount(current)} nodes</span></div></div>${participantResizeHandlesHtml(current)}</div>`;
        }
        if (isSubProcessContainer(current)) {
          return `<div class="subprocess-shape"><div class="subprocess-title">${current.name || current.id}</div><div class="subprocess-hint">move / resize container</div>${containerResizeHandlesHtml(current)}</div>`;
        }
        if (current.kind === 'textAnnotation') {
          const text = current.documentation || current.name || '';
          return `<div class="annotation-shape"><textarea class="annotation-editor" placeholder="输入注释，可多行编辑" onmousedown="event.stopPropagation()" onclick="event.stopPropagation()" ondblclick="event.stopPropagation()" oninput="updateAnnotationText(event, '${escapeAttr(current.id)}', this.value)">${escapeHtml(text)}</textarea></div>`;
        }
        const icon = activityIcon(current.kind);
        const marker = structuralMarker(current.kind);
        return `<div class="node-body">${icon ? `<div class="activity-icon">${icon}</div>` : ''}<div class="node-kind">${current.kind}</div><div class="node-name">${current.name || current.id}</div><div class="node-meta">${nodeMeta(current)}</div>${marker ? `<div class="activity-marker">${marker}</div>` : ''}</div>${handles}`;
      }

      function nodeToolbarHtml(current) {
        if (state.selected.type !== 'node' || state.selected.id !== current.id) return '';
        const appendDisabled = !canAppendFrom(current) ? 'disabled' : '';
        const changeDisabled = !canMorphTaskType(current) ? 'disabled' : '';
        const deleteDisabled = isDefaultSubProcessBoundaryNode(current) ? 'disabled' : '';
        return `<div class="node-toolbar" onmousedown="event.stopPropagation()" onclick="event.stopPropagation()">
          ${taskMorphMenuHtml(current)}
          <button title="${changeDisabled ? '只有 Task 节点可改变 Task 类型' : '改变 Task 类型'}" ${changeDisabled} onclick="changeNodeType('${escapeAttr(current.id)}')">⇄</button>
          <span class="node-toolbar-separator"></span>
          <button class="active" title="向后追加 Task" ${appendDisabled} onclick="appendNodeAfter('${escapeAttr(current.id)}', 'serviceTask')">▭</button>
          <button class="warning" title="向后追加 Gateway" ${appendDisabled} onclick="appendNodeAfter('${escapeAttr(current.id)}', 'exclusiveGateway')">◇</button>
          <button title="向后追加 End Event" ${appendDisabled} onclick="appendNodeAfter('${escapeAttr(current.id)}', 'endEvent')">◉</button>
          <span class="node-toolbar-separator"></span>
          <button class="danger" title="${deleteDisabled ? 'Sub-process 默认 Start/End 节点不可删除' : '删除节点'}" ${deleteDisabled} onclick="deleteNode('${escapeAttr(current.id)}')">⌫</button>
        </div>`;
      }

      function canAppendFrom(current) {
        return isConnectableNode(current) && current.kind !== 'endEvent';
      }

      function canMorphTaskType(current) {
        return taskMorphTypes().some(option => option.kind === current?.kind);
      }

      function taskMorphMenuHtml(current) {
        if (state.taskMorphMenuNodeId !== current.id || !canMorphTaskType(current)) return '';
        const title = `Morph ${taskMorphLabel(current.kind)}`;
        const items = taskMorphTypes().map(option => {
          const active = option.kind === current.kind ? ' active' : '';
          return `<button class="task-morph-item${active}" title="${escapeAttr(option.label)}" onclick="morphTaskType('${escapeAttr(current.id)}', '${escapeAttr(option.kind)}')"><span class="task-morph-icon">${taskMorphIcon(option.kind)}</span><span>${escapeHtml(option.label)}</span></button>`;
        }).join('');
        return `<div class="task-morph-menu"><div class="task-morph-title">${escapeHtml(title)}</div>${items}</div>`;
      }

      function nodeHandlesHtml(current) {
        if (!isConnectableNode(current)) return '';
        const active = state.connectionSource === current.id ? ' active' : '';
        return ['left', 'right', 'top', 'bottom'].map(position =>
          `<span class="connection-handle ${position}${active}" data-node-id="${escapeAttr(current.id)}" data-handle="${position}" title="从 ${current.id}.${position} 拖拽连线" onmousedown="startConnectionDrag(event, '${current.id}', '${position}')"></span>`
        ).join('');
      }

      function isConnectableNode(current) {
        return !['participant', 'textAnnotation', 'subProcess'].includes(current.kind);
      }

      function containerResizeHandlesHtml(current) {
        if (state.selected.type !== 'node' || state.selected.id !== current.id) return '';
        return ['e', 's', 'se'].map(direction =>
          `<span class="container-resize-handle ${direction}" title="拖拽调整容器尺寸" onmousedown="startContainerResize(event, '${escapeAttr(current.id)}', '${direction}')"></span>`
        ).join('');
      }

      function participantResizeHandlesHtml(current) {
        return containerResizeHandlesHtml(current);
      }

      function isSubProcessContainer(current) {
        return current.kind === 'subProcess';
      }

      function isDefaultSubProcessBoundaryNode(current) {
        return Boolean(current?.parentSubProcessId && ['start', 'end'].includes(current.subProcessBoundary));
      }

      function isParticipantContainer(current) {
        return current.kind === 'participant';
      }

      function gatewaySymbol(kind) {
        if (kind === 'parallelGateway') return '+';
        if (kind === 'inclusiveGateway') return '○';
        if (kind === 'eventBasedGateway') return '◇';
        return '×';
      }

      function structuralGlyph(kind) {
        if (kind === 'subProcess') return '▣';
        if (kind === 'participant') return '▤';
        return '□';
      }

      function activityIcon(kind) {
        return {
          serviceTask: '⚙',
          userTask: '♙',
          manualTask: '☞',
          sendTask: '✉',
          receiveTask: '▱',
          scriptTask: '𝑓',
          businessRuleTask: '▦'
        }[kind] || '';
      }

      function structuralMarker(kind) {
        return {
          subProcess: '+'
        }[kind] || '';
      }

      function nodeMeta(current) {
        if (current.kind === 'participant') return current.config?.participantRef || 'pool / lane';
        if (current.participantId) {
          const participant = participantById(current.participantId);
          if (participant) return `${participant.name || participant.id} · ${current.activityType || current.id}`;
        }
        if (current.kind === 'receiveTask') return current.config?.signalName || 'signal / message';
        if (current.kind === 'boundaryEvent') return current.config?.attachedToRef || 'attached boundary';
        if (current.activityType) return current.activityType;
        if (current.decisionRef) return current.decisionRef;
        if (current.config?.flowFoundryAssignmentDefinition?.candidateGroups) return current.config.flowFoundryAssignmentDefinition.candidateGroups;
        if (current.config?.script) return current.config.script;
        return current.id;
      }

      function renderEdges() {
        const svg = $('edges');
        svg.innerHTML = '<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#2587e6"/></marker></defs>';
        for (const current of state.model.edges) {
          const from = state.model.nodes.find(n => n.id === current.from);
          const to = state.model.nodes.find(n => n.id === current.to);
          if (!from || !to) continue;
          const p = edgePoints(from, to, current);
          const d = `M ${p.x1} ${p.y1} C ${p.x1 + 80} ${p.y1}, ${p.x2 - 80} ${p.y2}, ${p.x2} ${p.y2}`;
          const line = svgEl('path', { d, stroke: state.selected.type === 'edge' && state.selected.id === current.id ? '#ff7a1a' : '#2587e6', 'stroke-width': 2, fill: 'none', 'marker-end': 'url(#arrow)' });
          svg.appendChild(line);
          const hit = svgEl('path', { d, stroke: 'transparent', 'stroke-width': 18, fill: 'none', class: 'edge-hit' });
          hit.onclick = evt => { evt.stopPropagation(); select('edge', current.id); };
          svg.appendChild(hit);
          if (current.condition && current.condition !== 'default') {
            svg.appendChild(svgEl('text', { x: (p.x1 + p.x2) / 2 - 60, y: (p.y1 + p.y2) / 2 - 10, fill: '#cbd5e1', 'font-size': 12 }, edgeConditionLabel(current)));
          }
        }
        if (state.connectionSource && state.connectionSourceHandle && state.connectionDraftTarget) {
          const from = state.model.nodes.find(n => n.id === state.connectionSource);
          if (from) {
            const start = handlePoint(from, state.connectionSourceHandle);
            const end = state.connectionDraftTarget;
            const c = Math.max(50, Math.abs(end.x - start.x) / 2);
            const d = `M ${start.x} ${start.y} C ${start.x + c} ${start.y}, ${end.x - c} ${end.y}, ${end.x} ${end.y}`;
            svg.appendChild(svgEl('path', {
              d,
              stroke: '#ff7a1a',
              'stroke-width': 2,
              fill: 'none',
              'stroke-dasharray': '7 5',
              'marker-end': 'url(#arrow)'
            }));
          }
        }
      }

      function resizeCanvasToModel() {
        const content = $('canvasContent');
        const svg = $('edges');
        const extent = canvasExtent();
        content.style.width = `${extent.width}px`;
        content.style.height = `${extent.height}px`;
        svg.setAttribute('width', String(extent.width));
        svg.setAttribute('height', String(extent.height));
        svg.style.width = `${extent.width}px`;
        svg.style.height = `${extent.height}px`;
      }

      function canvasExtent() {
        const minWidth = 3200;
        const minHeight = 2200;
        const padding = 900;
        let width = minWidth;
        let height = minHeight;
        state.model.nodes.forEach(n => {
          const bounds = nodeBounds(n);
          width = Math.max(width, Math.ceil(bounds.right + padding));
          height = Math.max(height, Math.ceil(bounds.bottom + padding));
        });
        if (state.connectionDraftTarget) {
          width = Math.max(width, Math.ceil(state.connectionDraftTarget.x + padding));
          height = Math.max(height, Math.ceil(state.connectionDraftTarget.y + padding));
        }
        return { width, height };
      }

      function edgePoints(from, to, flow) {
        const start = handlePoint(from, flow.fromHandle || 'right');
        const end = handlePoint(to, flow.toHandle || 'left');
        return { x1: start.x, y1: start.y, x2: end.x, y2: end.y };
      }

      function handlePoint(n, position) {
        const size = nodeSize(n);
        const points = {
          left: { x: n.x, y: n.y + size.height / 2 },
          right: { x: n.x + size.width, y: n.y + size.height / 2 },
          top: { x: n.x + size.width / 2, y: n.y },
          bottom: { x: n.x + size.width / 2, y: n.y + size.height }
        };
        return points[position] || points.right;
      }

      function nodeSize(n) {
        if (n.kind === 'participant') return { width: n.width || 520, height: n.height || 220 };
        if (n.kind === 'textAnnotation') return { width: 170, height: 78 };
        if (n.kind === 'subProcess') return { width: n.width || 420, height: n.height || 260 };
        if (n.kind.includes('Gateway')) return { width: 54, height: 54 };
        if (n.kind.includes('Event')) return { width: 36, height: 36 };
        return { width: 132, height: 80 };
      }

      function svgEl(name, attrs, text) {
        const el = document.createElementNS('http://www.w3.org/2000/svg', name);
        for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, value);
        if (text) el.textContent = text;
        return el;
      }

      function renderProperties() {
        if (state.selected.type === 'edge') return renderEdgeProperties();
        if (state.selected.type === 'node') return renderNodeProperties();
        return renderProcessProperties();
      }

      function renderProcessProperties() {
        $('propTitle').textContent = 'Process';
        $('propType').textContent = '';
        $('properties').innerHTML = `
          <div class="prop-section"><h3>General</h3>
            <label>Process Name</label><input value="${escapeAttr(state.model.process.name)}" oninput="updateProcess('name', this.value)" />
            <label>Process ID</label><input value="${escapeAttr(state.model.process.id)}" oninput="updateProcess('id', this.value)" />
            <label class="switch-row"><input type="checkbox" ${state.model.process.isExecutable ? 'checked' : ''} onchange="updateProcess('isExecutable', this.checked)" /> Executable</label>
            <div class="help">When off, the engine will not run this process, useful for reference-only diagrams.</div>
          </div>
          <div class="prop-section"><h3>Flow Actions</h3>
            <button onclick="showJson('dsl')">View DSL</button>
          </div>
        `;
      }

      function renderNodeProperties() {
        const n = selectedNode();
        if (!n) return renderProcessProperties();
        $('propTitle').textContent = 'Properties';
        $('propType').textContent = `Type: ${n.kind}`;
        $('properties').innerHTML = `
          ${generalSection(n)}
          <div class="prop-section"><h3>Actions</h3>
            <button class="danger" onclick="deleteSelected()">Delete Node</button>
            <div class="help">删除节点会同时删除所有进入和离开该节点的 Sequence Flow。</div>
          </div>
          ${responsibilitySection(n)}
          ${routingSection(n)}
          ${taskDefinitionSection(n)}
          ${assignmentSection(n)}
          ${businessRuleSection(n)}
          ${receiveTaskSection(n)}
          ${structuralSection(n)}
          ${annotationSection(n)}
          ${scriptSection(n)}
          ${timerSection(n)}
          ${runtimeSections(n)}
        `;
      }

      function renderEdgeProperties() {
        const e = selectedEdge();
        if (!e) return renderProcessProperties();
        const mode = edgeConditionMode(e);
        const dmn = dmnCondition(e);
        $('propTitle').textContent = 'Properties';
        $('propType').textContent = 'Type: SequenceFlow';
        $('properties').innerHTML = `
          <div class="prop-section"><h3>General</h3>
            <label>ID *</label><input value="${escapeAttr(e.id)}" oninput="updateEdge('id', this.value)" />
            <label>Name</label><input value="${escapeAttr(e.name || '')}" oninput="updateEdge('name', this.value)" />
            <label>Documentation</label><textarea oninput="updateEdge('documentation', this.value)">${escapeHtml(e.documentation || '')}</textarea>
          </div>
          <div class="prop-section"><h3>Condition</h3>
            <label>Condition Type</label>
            <div class="segment">
              <button class="${mode === 'feel' ? 'active' : ''}" onclick="updateEdgeConditionMode('feel')">FEEL</button>
              <button class="${mode === 'dmn' ? 'active' : ''}" onclick="updateEdgeConditionMode('dmn')">DMN</button>
              <button class="${mode === 'default' ? 'active' : ''}" onclick="updateEdgeConditionMode('default')">Default</button>
            </div>
            ${mode === 'dmn' ? `
              <label>Decision Ref</label><input value="${escapeAttr(dmn.decisionRef || '')}" placeholder="risk-routing-decision" oninput="updateEdgeDmn('decisionRef', this.value)" />
              <label>Decision Version</label><input value="${escapeAttr(dmn.decisionVersion || 'latest')}" oninput="updateEdgeDmn('decisionVersion', this.value)" />
              <label>Result Path</label><input value="${escapeAttr(dmn.resultPath || 'matched')}" placeholder="matched" oninput="updateEdgeDmn('resultPath', this.value)" />
              <div class="help">DMN 条件会通过 Temporal Activity 调用外部 Node.js DMN/JS 服务，返回布尔值或 matched 字段后决定是否走这条线。</div>
            ` : `
              <label>FEEL Expression</label><input value="${escapeAttr(feelCondition(e))}" placeholder="\${amount > 1000}" oninput="updateEdgeFeel(this.value)" ${mode === 'default' ? 'disabled' : ''} />
              <div class="help">FEEL 会被 Flow Compiler 编译为 Safe FEEL AST，并在 Temporal Workflow 内确定性执行。</div>
            `}
          </div>
          <div class="prop-section"><h3>Default Flow</h3>
            <label class="switch-row"><input type="checkbox" ${e.condition === 'default' ? 'checked' : ''} onchange="updateEdge('condition', this.checked ? 'default' : '')" /> Taken when no other condition matches</label>
          </div>
          <div class="prop-section"><h3>Actions</h3>
            <button class="danger" onclick="deleteSelected()">Delete Sequence Flow</button>
          </div>
        `;
      }

      function generalSection(n) {
        return `<div class="prop-section"><h3>General</h3>
          <label>ID *</label><input value="${escapeAttr(n.id)}" oninput="updateNode('id', this.value)" />
          <label>Name</label><input value="${escapeAttr(n.name || '')}" oninput="updateNode('name', this.value)" />
          <label>Documentation</label><textarea oninput="updateNode('documentation', this.value)">${escapeHtml(n.documentation || '')}</textarea>
        </div>`;
      }

      function responsibilitySection(n) {
        if (!isParticipantAssignable(n)) return '';
        const participants = state.model.nodes.filter(isParticipantContainer);
        if (participants.length === 0) {
          return `<div class="prop-section"><h3>Responsibility</h3>
            <div class="help">拖入 Participant 泳道后，节点会自动记录参与方归属。</div>
          </div>`;
        }
        return `<div class="prop-section"><h3>Responsibility</h3>
          <label>Participant</label>
          <select onchange="updateParticipantOwner(this.value)">
            <option value="">None</option>
            ${participants.map(p => `<option value="${escapeAttr(p.id)}" ${n.participantId === p.id ? 'selected' : ''}>${escapeHtml(p.name || p.id)}</option>`).join('')}
          </select>
          <div class="help">Participant 只表达责任和协作边界，不改变流程控制流。</div>
        </div>`;
      }

      function routingSection(n) {
        const outgoing = state.model.edges.filter(e => e.from === n.id);
        return `<div class="prop-section"><h3>Routing</h3>
          <label>Default outgoing flow</label>
          <select onchange="setDefaultFlow(this.value)">
            <option value="">None</option>
            ${outgoing.map(e => `<option value="${e.id}" ${e.condition === 'default' ? 'selected' : ''}>${e.id} -> ${e.to}</option>`).join('')}
          </select>
          <div class="help">Taken when no other condition matches.</div>
        </div>`;
      }

      function taskDefinitionSection(n) {
        if (!['task','serviceTask','sendTask','businessRuleTask'].includes(n.kind)) return '';
        const options = state.activities.map(a => `<option value="${a.id}" ${a.id === n.activityType ? 'selected' : ''}>${a.name || a.id}</option>`).join('');
        return `<div class="prop-section"><h3>Task Definition</h3>
          <label>Task Type</label>
          <select onchange="updateActivityType(this.value)"><option value="">Select registered activity</option>${options}</select>
          <input style="margin-top:8px" value="${escapeAttr(n.activityType || '')}" placeholder="e.g. email-worker" oninput="updateActivityType(this.value)" />
          <label>Retries</label>
          <input type="number" value="${n.maxAttempts || 3}" oninput="updateNodeNumber('maxAttempts', this.value)" />
        </div>`;
      }

      function assignmentSection(n) {
        if (!['userTask','manualTask'].includes(n.kind)) return '';
        return `<div class="prop-section"><h3>Assignment</h3>
          <label>Candidate Groups</label><input value="${escapeAttr(n.config?.flowFoundryAssignmentDefinition?.candidateGroups || '')}" oninput="updateConfigPath(['flowFoundryAssignmentDefinition','candidateGroups'], this.value)" />
          <label>Assignee</label><input value="${escapeAttr(n.config?.flowFoundryAssignmentDefinition?.assignee || '')}" oninput="updateConfigPath(['flowFoundryAssignmentDefinition','assignee'], this.value)" />
          <div class="help">运行时映射为 Temporal Workflow 等待 Signal，人工任务完成后继续执行。</div>
        </div>`;
      }

      function businessRuleSection(n) {
        if (n.kind !== 'businessRuleTask') return '';
        return `<div class="prop-section"><h3>Business Rule / DMN</h3>
          <label>Decision Ref</label><input value="${escapeAttr(n.decisionRef || '')}" oninput="updateNode('decisionRef', this.value)" />
          <label>Decision Version</label><input value="${escapeAttr(n.decisionVersion || '1.0.0')}" oninput="updateNode('decisionVersion', this.value)" />
          <div class="help">后端默认映射为 dmn-decision Activity，由 Activity 调用既有 Node.js DMN/JS 服务。</div>
        </div>`;
      }

      function receiveTaskSection(n) {
        if (n.kind !== 'receiveTask') return '';
        return `<div class="prop-section"><h3>Receive Task</h3>
          <label>Signal / Message Name</label><input value="${escapeAttr(n.config?.signalName || '')}" placeholder="external-message-received" oninput="updateConfig('signalName', this.value)" />
          <div class="help">运行时映射为 Workflow 等待 Signal / Update，用于等待外部消息或回调。</div>
        </div>`;
      }

      function structuralSection(n) {
        if (!['subProcess','participant'].includes(n.kind)) return '';
        if (n.kind === 'participant') {
          return `<div class="prop-section"><h3>Participant / Pool</h3>
            <label>Participant Ref</label><input value="${escapeAttr(n.config?.participantRef || '')}" placeholder="business-team" oninput="updateConfig('participantRef', this.value)" />
            <label>Pool Width</label><input type="number" value="${n.width || 520}" oninput="updateNodeNumber('width', this.value)" />
            <label>Pool Height</label><input type="number" value="${n.height || 220}" oninput="updateNodeNumber('height', this.value)" />
            <div class="help">Participant 表达组织、系统或角色边界。拖入泳道的运行节点会保存 participantId，但 Participant 本身不进入 Execution Plan。</div>
          </div>`;
        }
        return `<div class="prop-section"><h3>Sub-process</h3>
          <label>Container Width</label><input type="number" value="${n.width || 420}" oninput="updateNodeNumber('width', this.value)" />
          <label>Container Height</label><input type="number" value="${n.height || 260}" oninput="updateNodeNumber('height', this.value)" />
          <div class="help">Sub-process 是容器。移动它时，会带动当前位于容器内部的节点一起移动；内部节点的相对位置和连线会保持。</div>
        </div>`;
      }

      function annotationSection(n) {
        if (n.kind !== 'textAnnotation') return '';
        return `<div class="prop-section"><h3>Text Annotation</h3>
          <label>Text</label><textarea oninput="updateNode('documentation', this.value)">${escapeHtml(n.documentation || '')}</textarea>
          <div class="help">Text Annotation 只用于解释流程，不参与执行。</div>
        </div>`;
      }

      function updateAnnotationText(event, nodeId, value) {
        event.stopPropagation();
        const n = state.model.nodes.find(node => node.id === nodeId);
        if (!n) return;
        n.documentation = value;
        if (state.selected.type !== 'node' || state.selected.id !== nodeId) {
          state.selected = { type: 'node', id: nodeId };
          updateButtons();
        }
        if (state.selected.id === nodeId) renderProperties();
      }

      function scriptSection(n) {
        if (n.kind !== 'scriptTask') return '';
        return `<div class="prop-section"><h3>Script</h3>
          <label>Script Format</label><select onchange="updateConfig('scriptFormat', this.value)"><option ${n.config?.scriptFormat === 'feel' ? 'selected' : ''}>feel</option></select>
          <label>Script</label><textarea oninput="updateConfig('script', this.value)">${escapeHtml(n.config?.script || '')}</textarea>
        </div>`;
      }

      function timerSection(n) {
        if (!['timerEvent','intermediateCatchEvent','boundaryEvent'].includes(n.kind)) return '';
        const def = n.config?.timerDefinition || {};
        return `<div class="prop-section"><h3>Timer Definition</h3>
          <label>Type</label><select onchange="updateTimer('type', this.value)"><option ${def.type === 'duration' ? 'selected' : ''}>duration</option><option ${def.type === 'date' ? 'selected' : ''}>date</option><option ${def.type === 'cycle' ? 'selected' : ''}>cycle</option></select>
          <label>Value</label><input value="${escapeAttr(def.value || n.config?.duration || '1m')}" placeholder="1m / PT1M / \${roundIntervalMinutes}M" oninput="updateTimer('value', this.value)" />
        </div>`;
      }

      function mappingSection(n, key, title) {
        return `<div class="prop-section"><h3>${title}</h3>
          <textarea oninput="updateJsonNode('${key}', this.value)">${pretty(n[key] || {})}</textarea>
        </div>`;
      }

      function headersSection(n) {
        return `<div class="prop-section"><h3>Task Headers</h3>
          <textarea oninput="updateJsonNode('headers', this.value)">${pretty(n.headers || {})}</textarea>
        </div>`;
      }

      function loopSection(n) {
        return `<div class="prop-section"><h3>Loop</h3>
          <div class="segment">${[
            ['none','None'],
            ['multiInstance','Multi-Instance'],
            ['standardLoop','Standard']
          ].map(([value,label]) => `<button class="${n.loop === value ? 'active' : ''}" onclick="updateNode('loop','${value}')">${label}</button>`).join('')}</div>
        </div>`;
      }

      function runtimeSections(n) {
        if (['participant','textAnnotation','subProcess'].includes(n.kind)) return '';
        return `
          ${mappingSection(n, 'inputMapping', 'Input Mappings')}
          ${mappingSection(n, 'outputMapping', 'Output Mappings')}
          ${headersSection(n)}
          ${loopSection(n)}
        `;
      }

      function select(type, id) {
        if (type !== 'node' || id !== state.taskMorphMenuNodeId) {
          state.taskMorphMenuNodeId = null;
        }
        state.selected = { type, id };
        renderCanvas();
        renderProperties();
        updateButtons();
      }

      function focusNodeProperties(nodeId) {
        state.selected = { type: 'node', id: nodeId };
        renderCanvas();
        renderProperties();
        message(`已选中节点：${nodeId}`);
      }

      function changeNodeType(nodeId) {
        const n = state.model.nodes.find(node => node.id === nodeId);
        if (!n) return;
        if (!canMorphTaskType(n)) {
          message('只有 Task 节点可改变 Task 类型');
          return;
        }
        state.taskMorphMenuNodeId = state.taskMorphMenuNodeId === nodeId ? null : nodeId;
        renderCanvas();
      }

      function morphTaskType(nodeId, kind) {
        const n = state.model.nodes.find(node => node.id === nodeId);
        if (!n || !canMorphTaskType(n)) return;
        const selected = taskMorphTypes().find(option => option.kind === kind);
        if (!selected) return;
        if (selected.kind === n.kind) {
          state.taskMorphMenuNodeId = null;
          renderCanvas();
          return;
        }
        pushHistory();
        n.kind = selected.kind;
        n.name = selected.defaultName || selected.label;
        n.config = selected.config ? structuredClone(selected.config) : {};
        n.activityType = selected.activityType || '';
        n.decisionRef = selected.decisionRef;
        n.decisionVersion = selected.decisionVersion;
        n.maxAttempts = selected.maxAttempts;
        if (['task','serviceTask','sendTask'].includes(n.kind)) {
          n.activityType = selected.activityType || state.activities[0]?.id || '';
          n.maxAttempts = selected.maxAttempts || 3;
        }
        if (n.kind === 'businessRuleTask') {
          n.activityType = 'dmn-decision';
          n.decisionRef = selected.decisionRef || 'demo-decision';
          n.decisionVersion = selected.decisionVersion || '1.0.0';
        }
        state.model.edges = state.model.edges.filter(e => isConnectableNode(n) || (e.from !== n.id && e.to !== n.id));
        if (isParticipantAssignable(n)) syncNodeParticipant(n);
        else delete n.participantId;
        if (isParticipantContainer(n)) syncParticipantAssignments();
        state.taskMorphMenuNodeId = null;
        state.selected = { type: 'node', id: n.id };
        message(`已变更节点类型：${selected.label}`);
        renderAll();
      }

      function taskMorphTypes() {
        return [
          { kind: 'task', label: 'Generic Task' },
          { kind: 'serviceTask', label: 'Service Task' },
          { kind: 'userTask', label: 'User Task', config: { flowFoundryAssignmentDefinition: { candidateGroups: 'operator' } } },
          { kind: 'manualTask', label: 'Manual Task', config: { flowFoundryAssignmentDefinition: { candidateGroups: 'manual-operator' } } },
          { kind: 'sendTask', label: 'Send Task', activityType: 'send-message' },
          { kind: 'receiveTask', label: 'Receive Task', config: { signalName: 'external-message-received', waitMode: 'signal' } },
          { kind: 'scriptTask', label: 'Script Task', config: { scriptFormat: 'feel', script: 'roundNumber := roundNumber + 1' } },
          { kind: 'businessRuleTask', label: 'Business Rule Task', activityType: 'dmn-decision', decisionRef: 'demo-decision', decisionVersion: '1.0.0' }
        ];
      }

      function taskMorphLabel(kind) {
        return taskMorphTypes().find(option => option.kind === kind)?.label || 'Task';
      }

      function taskMorphIcon(kind) {
        return {
          task: '▭',
          serviceTask: '⚙',
          userTask: '♙',
          manualTask: '☞',
          sendTask: '✉',
          receiveTask: '▱',
          scriptTask: '∑',
          businessRuleTask: '▤'
        }[kind] || '▭';
      }

      function appendNodeAfter(sourceId, kind) {
        const source = state.model.nodes.find(node => node.id === sourceId);
        if (!source || !canAppendFrom(source)) return;
        const spec = appendNodeSpec(kind);
        const sourceSize = nodeSize(source);
        let point = {
          x: source.x + sourceSize.width + 190,
          y: source.y + sourceSize.height / 2
        };
        const participant = participantById(source.participantId);
        if (participant) point = constrainPointToParticipant(point, participant, spec.kind);
        const created = createNodeFromPaletteItem(spec, point, { selectNewNode: false });
        if (!created) return;
        createSequenceFlow(source.id, created.id, 'right', 'left', { selectNewEdge: false, skipHistory: true });
        state.selected = { type: 'node', id: created.id };
        message(`已追加节点：${created.name}`);
        renderAll();
      }

      function appendNodeSpec(kind) {
        if (kind === 'exclusiveGateway') return { kind, label: 'Exclusive Gateway', basic: true };
        if (kind === 'endEvent') return { kind, label: 'End Event', basic: true };
        return { kind: 'serviceTask', label: 'Service Task', basic: true };
      }

      function hintConnect(nodeId) {
        const n = state.model.nodes.find(node => node.id === nodeId);
        if (!n || !isConnectableNode(n)) {
          message('该节点不支持直接连线');
          return;
        }
        state.selected = { type: 'node', id: nodeId };
        renderCanvas();
        renderProperties();
        message('从节点边缘蓝色圆点按住拖拽到目标节点圆点，即可创建连线');
      }

      function setFirstOutgoingAsDefault(nodeId) {
        const first = state.model.edges.find(e => e.from === nodeId);
        if (!first) {
          message('该节点暂无外连线');
          return;
        }
        pushHistory();
        state.model.edges.filter(e => e.from === nodeId).forEach(e => {
          e.condition = e.id === first.id ? 'default' : (e.condition === 'default' ? '' : e.condition);
        });
        state.selected = { type: 'edge', id: first.id };
        message(`已设置默认流：${first.id}`);
        renderAll();
      }

      function startPaletteDrag(event, encodedItem) {
        const item = JSON.parse(decodeURIComponent(encodedItem));
        state.paletteDragItem = item;
        event.dataTransfer.effectAllowed = 'copy';
        event.dataTransfer.setData('application/json', JSON.stringify(item));
        event.dataTransfer.setData('text/plain', item.kind);
        const dragImage = createPaletteDragImage(item);
        state.paletteDragImage = dragImage;
        document.body.appendChild(dragImage);
        const hotspot = dragImageHotspot(item);
        event.dataTransfer.setDragImage(dragImage, hotspot.x, hotspot.y);
      }

      function endPaletteDrag() {
        setCanvasDragOver(false);
        removePaletteDragImage();
      }

      function createPaletteDragImage(item) {
        if (item.kind.includes('Gateway')) return createGatewayDragImage(item);
        const previewNode = node('__drag_preview__', item.kind, item.label, 0, 0, {
          decisionRef: item.decisionRef,
          decisionVersion: item.decisionVersion,
          config: item.config ? structuredClone(item.config) : {}
        });
        const wrapper = document.createElement('div');
        wrapper.className = 'drag-image';
        wrapper.innerHTML = `<div class="node ${nodeClass(previewNode)}">${nodeHtml(previewNode)}</div>`;
        return wrapper;
      }

      function createGatewayDragImage(item) {
        const wrapper = document.createElement('div');
        wrapper.className = 'drag-image';
        wrapper.innerHTML = `
          <div class="node gateway-node">
            <div class="gateway-shape">
              <div class="gateway-symbol">${gatewaySymbol(item.kind)}</div>
            </div>
          </div>
        `;
        return wrapper;
      }

      function dragImageHotspot(item) {
        if (item.kind.includes('Gateway')) {
          return { x: 42, y: 42 };
        }
        const size = nodeSize({ kind: item.kind });
        return { x: size.width / 2, y: size.height / 2 };
      }

      function removePaletteDragImage() {
        state.paletteDragImage?.remove();
        state.paletteDragImage = null;
      }

      function allowCanvasDrop(event) {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'copy';
      }

      function setCanvasDragOver(active) {
        $('canvas').classList.toggle('drag-over', Boolean(active));
      }

      function dropPaletteNode(event) {
        event.preventDefault();
        event.stopPropagation();
        setCanvasDragOver(false);
        removePaletteDragImage();
        const raw = event.dataTransfer.getData('application/json');
        const item = raw ? JSON.parse(raw) : state.paletteDragItem;
        state.paletteDragItem = null;
        if (!item) return;
        const point = canvasPointFromEvent(event);
        addNodeFromPalette(item, point);
      }

      function canvasPointFromEvent(event) {
        const canvas = $('canvas');
        const rect = canvas.getBoundingClientRect();
        return {
          x: Math.max(0, (event.clientX - rect.left + canvas.scrollLeft) / state.scale),
          y: Math.max(0, (event.clientY - rect.top + canvas.scrollTop) / state.scale)
        };
      }

      function addNodeFromPalette(item, point = null) {
        createNodeFromPaletteItem(item, point);
      }

      function createNodeFromPaletteItem(item, point = null, options = {}) {
        const id = `${item.kind}_${Date.now().toString(36)}`;
        const position = point || { x: 180 + state.model.nodes.length * 24, y: 140 + state.model.nodes.length * 18 };
        const n = node(id, item.kind, item.label, 180 + state.model.nodes.length * 24, 140 + state.model.nodes.length * 18, {
          activityType: item.activityType,
          decisionRef: item.decisionRef,
          decisionVersion: item.decisionVersion,
          documentation: item.documentation || '',
          config: item.config ? structuredClone(item.config) : {}
        });
        const size = nodeSize(n);
        n.x = Math.round(Math.max(0, position.x - size.width / 2));
        n.y = Math.round(Math.max(0, position.y - size.height / 2));
        if (!canPlaceNodeInParticipantMode(n)) return null;
        pushHistory();
        if (['task','serviceTask','sendTask'].includes(item.kind)) {
          n.activityType = state.activities[0]?.id || '';
          n.maxAttempts = 3;
        }
        if (item.activityType) n.activityType = item.activityType;
        if (item.kind === 'receiveTask') n.config = { ...(n.config || {}), waitMode: 'signal' };
        if (item.kind === 'businessRuleTask') {
          n.activityType = 'dmn-decision';
        }
        state.model.nodes.push(n);
        if (item.kind === 'subProcess') {
          addSubProcessDefaultBoundaryNodes(n);
        }
        if (isParticipantContainer(n) || isSubProcessContainer(n)) syncParticipantAssignments();
        else syncNodeParticipant(n);
        if (options.selectNewNode !== false) {
          select('node', id);
          message(`已添加节点：${item.label}`);
        }
        return n;
      }

      function addSubProcessDefaultBoundaryNodes(container) {
        const bounds = nodeBounds(container);
        const centerY = bounds.top + (bounds.bottom - bounds.top) / 2;
        const start = node(`${container.id}_Start`, 'startEvent', 'Start', bounds.left + 70, centerY - 18, {
          parentSubProcessId: container.id,
          subProcessBoundary: 'start',
          documentation: 'Sub-process 默认开始节点，不可删除'
        });
        const end = node(`${container.id}_End`, 'endEvent', 'End', bounds.right - 106, centerY - 18, {
          parentSubProcessId: container.id,
          subProcessBoundary: 'end',
          documentation: 'Sub-process 默认结束节点，不可删除'
        });
        state.model.nodes.push(start, end);
      }

      function startConnectionDrag(evt, nodeId, position) {
        if (evt.button !== 0) return;
        evt.preventDefault();
        evt.stopPropagation();
        const source = state.model.nodes.find(n => n.id === nodeId);
        if (!source) return;
        state.connectionSource = nodeId;
        state.connectionSourceHandle = position;
        state.connectionDraftTarget = canvasPointFromEvent(evt);
        state.selected = { type: 'node', id: nodeId };
        message(`从 ${nodeId}.${position} 拖到目标节点圆点后松开鼠标完成连线`);
        renderCanvas();

        const onMove = move => {
          state.connectionDraftTarget = canvasPointFromEvent(move);
          highlightConnectionTarget(move);
          renderEdges();
        };
        const onUp = up => {
          document.removeEventListener('mousemove', onMove);
          document.removeEventListener('mouseup', onUp);
          const target = connectionHandleFromPoint(up.clientX, up.clientY);
          clearConnectionTargetHighlight();
          if (target && target.nodeId !== nodeId) {
            createSequenceFlow(nodeId, target.nodeId, position, target.handle);
          } else {
            state.connectionSource = null;
            state.connectionSourceHandle = null;
            state.connectionDraftTarget = null;
            message('已取消连接');
            renderCanvas();
          }
        };
        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
      }

      function connectionHandleFromPoint(x, y) {
        const element = document.elementFromPoint(x, y);
        const handle = element?.closest?.('.connection-handle');
        if (!handle) return null;
        return { nodeId: handle.dataset.nodeId, handle: handle.dataset.handle };
      }

      function highlightConnectionTarget(evt) {
        clearConnectionTargetHighlight();
        const target = connectionHandleFromPoint(evt.clientX, evt.clientY);
        if (!target || target.nodeId === state.connectionSource) return;
        document
          .querySelector(`.connection-handle[data-node-id="${CSS.escape(target.nodeId)}"][data-handle="${CSS.escape(target.handle)}"]`)
          ?.classList.add('drop-target');
      }

      function clearConnectionTargetHighlight() {
        document.querySelectorAll('.connection-handle.drop-target').forEach(el => el.classList.remove('drop-target'));
      }

      function createSequenceFlow(from, to, fromHandle = 'right', toHandle = 'left', options = {}) {
        if (!from || !to || from === to) return;
        const duplicate = state.model.edges.find(e => e.from === from && e.to === to);
        if (duplicate) {
          state.connectionSource = null;
          state.connectionSourceHandle = null;
          select('edge', duplicate.id);
          message(`连线已存在：${duplicate.id}`);
          return;
        }
        if (!options.skipHistory) pushHistory();
        const id = `F_${from}_${to}_${Date.now().toString(36)}`;
        state.model.edges.push({ ...edge(id, from, to), fromHandle, toHandle });
        state.connectionSource = null;
        state.connectionSourceHandle = null;
        state.connectionDraftTarget = null;
        state.connectBuffer = [];
        if (options.selectNewEdge !== false) {
          select('edge', id);
          message(`已创建 Sequence Flow：${id}`);
        }
        return state.model.edges.find(e => e.id === id);
      }
