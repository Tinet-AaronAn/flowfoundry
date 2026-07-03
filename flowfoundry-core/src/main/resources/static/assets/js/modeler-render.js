      async function loadActivities() {
        try {
          const res = await fetch('/api/activities');
          const data = await res.json();
          state.activities = data.activities || [];
        } catch (err) {
          message(t('message.loadActivitiesFailed', { error: err.message }), 'error');
        }
        await loadFromMemory();
        renderAll();
      }

      function renderAll() {
        applyViewLayout();
        renderPalette();
        applyPaletteCollapsed();
        applyNavCollapsed();
        applyPropertiesCollapsed();
        updateCompiledPlanButton();
        updateSimulationHeader();
        applyI18n();
        renderCanvas();
        renderMinimap();
        renderProperties();
        renderWorkflowList();
        renderRunsList();
        renderNavigation();
        updateButtons();
        updateViewportUi();
      }

      function renderNavigation() {
        $('navWorkflows').classList.toggle('active', state.currentView === 'workflows');
        $('navModeler').classList.toggle('active', state.currentView === 'modeler');
        $('navSimulation').classList.toggle('active', state.currentView === 'simulation');
        $('navRuns').classList.toggle('active', state.currentView === 'runs');
      }

      function applyViewLayout() {
        const inModeler = state.currentView === 'modeler';
        const inSimulation = state.currentView === 'simulation';
        $('app')?.classList.toggle('modeler-view', inModeler);
        $('app')?.classList.toggle('simulation-view', inSimulation);
      }

      function switchView(view) {
        state.currentView = view;
        applyViewLayout();
        $('workflowListView').classList.toggle('active', view === 'workflows');
        $('modelerView').classList.toggle('active', view === 'modeler');
        $('simulationView').classList.toggle('active', view === 'simulation');
        $('runsView').classList.toggle('active', view === 'runs');
        renderNavigation();
        updateSimulationHeader();
        if (view === 'workflows') renderWorkflowList();
        if (view === 'runs') renderRunsList();
        if (view === 'modeler') {
          syncModelHeader();
          ensureBrowserMemoryNotice();
          state.selected = { type: 'process', id: null };
          state.taskMorphMenuNodeId = null;
          collapsePropertiesPanel();
          renderCanvas();
          renderProperties();
          updateButtons();
          scheduleFitView();
        }
        if (view === 'simulation') {
          renderCanvas();
          requestAnimationFrame(() => fitSimulationView());
          startRuntimePolling();
        } else {
          stopRuntimePolling();
        }
      }

      function renderPalette() {
        const keyword = $('paletteSearch').value.trim().toLowerCase();
        $('paletteItems').innerHTML = paletteGroups.map(group => {
          const items = group.items.filter(item => item.label.toLowerCase().includes(keyword));
          if (items.length === 0) return '';
          return `<details open><summary>${t(group.groupKey)}</summary>${items.map(item => `
            <button class="palette-item" draggable="true" data-item="${escapeAttr(encodeURIComponent(JSON.stringify(item)))}" ondragstart="startPaletteDrag(event, this.dataset.item)" ondragend="endPaletteDrag(event)">
              ${glyph(item.kind)} <span>${item.label}</span>
            </button>
          `).join('')}</details>`;
        }).join('');
      }

      function togglePalette() {
        state.paletteCollapsed = !state.paletteCollapsed;
        try {
          localStorage.setItem('flowfoundry-palette-collapsed', state.paletteCollapsed ? '1' : '0');
        } catch (err) {
          /* ignore storage errors */
        }
        applyPaletteCollapsed();
        renderMinimap();
        updateMinimapViewport();
      }

      function applyPaletteCollapsed() {
        $('workbench')?.classList.toggle('palette-collapsed', state.paletteCollapsed);
        const toggle = $('paletteToggleBtn');
        if (toggle) {
          toggle.title = state.paletteCollapsed ? t('palette.expand') : t('palette.collapse');
          toggle.setAttribute('aria-expanded', String(!state.paletteCollapsed));
        }
      }

      function toggleNav() {
        state.navCollapsed = !state.navCollapsed;
        try {
          localStorage.setItem('flowfoundry-nav-collapsed', state.navCollapsed ? '1' : '0');
        } catch (err) {
          /* ignore storage errors */
        }
        applyNavCollapsed();
      }

      function applyNavCollapsed() {
        $('app')?.classList.toggle('nav-collapsed', state.navCollapsed);
        const toggle = $('navToggleBtn');
        if (toggle) {
          toggle.title = state.navCollapsed ? t('nav.expand') : t('nav.collapse');
          toggle.setAttribute('aria-expanded', String(!state.navCollapsed));
        }
      }

      function togglePropertiesPanel() {
        state.propertiesCollapsed = !state.propertiesCollapsed;
        try {
          localStorage.setItem('flowfoundry-properties-collapsed', state.propertiesCollapsed ? '1' : '0');
        } catch (err) {
          /* ignore storage errors */
        }
        applyPropertiesCollapsed();
      }

      function expandPropertiesPanel() {
        if (state.currentView !== 'modeler') return;
        if (!state.propertiesCollapsed) return;
        state.propertiesCollapsed = false;
        try {
          localStorage.setItem('flowfoundry-properties-collapsed', '0');
        } catch (err) {
          /* ignore storage errors */
        }
        applyPropertiesCollapsed();
      }

      function collapsePropertiesPanel() {
        if (state.currentView !== 'modeler') return;
        if (state.propertiesCollapsed) return;
        state.propertiesCollapsed = true;
        try {
          localStorage.setItem('flowfoundry-properties-collapsed', '1');
        } catch (err) {
          /* ignore storage errors */
        }
        applyPropertiesCollapsed();
      }

      function applyPropertiesCollapsed() {
        $('app')?.classList.toggle('properties-collapsed', state.propertiesCollapsed);
        const toggle = $('propertiesToggleBtn');
        if (toggle) {
          toggle.title = state.propertiesCollapsed ? t('properties.expand') : t('properties.collapse');
          toggle.setAttribute('aria-expanded', String(!state.propertiesCollapsed));
        }
      }

      function nodeRenderOrder(a, b) {
        const rank = node => {
          if (node.kind === 'participant') return 0;
          if (node.kind === 'subProcess') return 1;
          return 2;
        };
        return rank(a) - rank(b);
      }

      function glyph(kind) {
        if (kind === 'startEvent') return '<span class="glyph event"></span>';
        if (kind === 'endEvent') return '<span class="glyph event end"></span>';
        if (kind === 'intermediateEvent' || kind === 'intermediateCatchEvent' || kind === 'boundaryEvent') return '<span class="glyph event intermediate"></span>';
        if (kind.includes('Gateway')) return `<span class="glyph gateway"><span>${gatewaySymbol(kind)}</span></span>`;
        if (kind === 'serviceTask') return '<span class="glyph service">⚙</span>';
        if (kind === 'humanTask') return '<span class="glyph human">♙</span>';
        if (kind === 'scriptTask') return '<span class="glyph script">𝑓</span>';
        if (kind === 'workflow') return '<span class="glyph workflow">↳</span>';
        if (['subProcess','participant'].includes(kind)) return `<span class="glyph structural">${structuralGlyph(kind)}</span>`;
        if (kind === 'textAnnotation') return '<span class="glyph annotation">▤</span>';
        return '<span class="glyph task"></span>';
      }

      function renderCanvas() {
        const { content, canvas, readonly } = activeCanvasElements();
        if (!content) return;
        if (!state.isDragging) {
          resizeCanvasToModel();
        }
        if (!readonly) applyViewportTransform();
        [...content.querySelectorAll('.node')].forEach(el => el.remove());
        const nodes = [...state.model.nodes].sort(nodeRenderOrder);
        const highlightId = state.runtimeHighlightNodeId;
        const runtimeFailed = String(state.runtimeSnapshot?.status || '').toUpperCase() === 'FAILED';
        for (const current of nodes) {
          const el = document.createElement('div');
          const selected = !readonly && state.selected.type === 'node' && state.selected.id === current.id;
          const simActive = highlightId === current.id;
          const simFailed = simActive && runtimeFailed;
          el.className = `node ${nodeClass(current)} ${selected ? 'selected' : ''} ${simActive ? 'simulation-active' : ''} ${simFailed ? 'simulation-failed' : ''} ${state.connectionSource === current.id ? 'connecting-source' : ''}`;
          const size = nodeSize(current);
          el.style.left = `${current.x}px`;
          el.style.top = `${current.y}px`;
          el.style.width = `${size.width}px`;
          el.style.height = `${size.height}px`;
          el.dataset.nodeId = current.id;
          el.innerHTML = nodeHtml(current) + (readonly ? '' : nodeToolbarHtml(current));
          if (!readonly) {
            el.onclick = evt => {
              evt.stopPropagation();
              state.suppressCanvasClick = false;
              select('node', current.id);
            };
            makeDraggable(el, current);
          }
          content.appendChild(el);
        }
        if (!readonly && canvas) {
          canvas.onclick = () => {
            if (state.suppressCanvasClick) {
              state.suppressCanvasClick = false;
              return;
            }
            select('process', null);
          };
        }
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
          return `<div class="gateway-shape"><div class="gateway-symbol">${gatewaySymbol(current.kind)}</div></div><div class="gateway-label">${current.name || current.id}</div>${handles}`;
        }
        if (current.kind.includes('Event')) {
          const labelClass = current.kind === 'intermediateEvent' || current.kind === 'intermediateCatchEvent' || current.kind === 'boundaryEvent' ? 'timer-label' : 'event-label';
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
          return `<div class="annotation-shape"><textarea class="annotation-editor" placeholder="${escapeAttr(t('annotation.placeholder'))}" onmousedown="event.stopPropagation()" onclick="event.stopPropagation()" ondblclick="event.stopPropagation()" oninput="updateAnnotationText(event, '${escapeAttr(current.id)}', this.value)">${escapeHtml(text)}</textarea></div>`;
        }
        const icon = activityIcon(current.kind);
        const marker = structuralMarker(current.kind);
        return `<div class="node-body"><div class="node-head">${icon ? `<span class="activity-icon">${icon}</span>` : ''}<div class="node-kind">${current.kind}</div></div><div class="node-name">${current.name || current.id}</div><div class="node-meta">${nodeMeta(current)}</div>${marker ? `<div class="activity-marker">${marker}</div>` : ''}</div>${handles}`;
      }

      function refreshNodePreview(n) {
        const el = nodeElement(n.id) || document.querySelector('#canvasContent .node.selected');
        if (!el) return;
        const size = nodeSize(n);
        el.dataset.nodeId = n.id;
        el.style.width = `${size.width}px`;
        el.style.height = `${size.height}px`;
        el.querySelectorAll('.connection-handle').forEach(handle => {
          handle.dataset.nodeId = n.id;
          handle.title = t('handle.connectFrom', { nodeId: n.id, handle: handle.dataset.handle });
        });

        const label = n.name || n.id;
        const setText = (selector, value) => {
          const target = el.querySelector(selector);
          if (target) target.textContent = value;
        };
        if (n.kind.includes('Gateway')) setText('.gateway-label', label);
        else if (n.kind.includes('Event')) setText('.event-label, .timer-label', label);
        else if (n.kind === 'participant') {
          setText('.participant-label', label);
          const caption = el.querySelector('.participant-caption');
          if (caption) caption.innerHTML = `${escapeHtml(n.config?.participantRef || 'pool / lane')} <span>${participantNodeCount(n)} nodes</span>`;
        } else if (isSubProcessContainer(n)) setText('.subprocess-title', label);
        else if (n.kind === 'textAnnotation') {
          const editor = el.querySelector('.annotation-editor');
          if (editor && document.activeElement !== editor) editor.value = n.documentation || n.name || '';
        } else {
          setText('.node-name', label);
          setText('.node-meta', nodeMeta(n));
        }
        renderEdges();
      }

      function refreshEdgePreview() {
        renderEdges();
      }

      function nodeElement(nodeId) {
        const { content } = activeCanvasElements();
        return content?.querySelector(`.node[data-node-id="${CSS.escape(nodeId)}"]`);
      }

      function supportsNodeToolbar(current) {
        return !['participant', 'subProcess', 'textAnnotation'].includes(current.kind);
      }

      function nodeToolbarHtml(current) {
        if (!supportsNodeToolbar(current)) return '';
        if (state.selected.type !== 'node' || state.selected.id !== current.id) return '';
        const appendDisabled = !canAppendFrom(current) ? 'disabled' : '';
        const changeDisabled = !canMorphTaskType(current) ? 'disabled' : '';
        return `<div class="node-toolbar" onmousedown="event.stopPropagation()" onclick="event.stopPropagation()">
          ${taskMorphMenuHtml(current)}
          <button title="${escapeAttr(changeDisabled ? t('toolbar.changeTaskTypeDisabled') : t('toolbar.changeTaskType'))}" ${changeDisabled} onclick="changeNodeType('${escapeAttr(current.id)}')">⇄</button>
          <span class="node-toolbar-separator"></span>
          <button class="active" title="${escapeAttr(t('toolbar.appendTask'))}" ${appendDisabled} onclick="appendNodeAfter('${escapeAttr(current.id)}', 'serviceTask')">▭</button>
          <button class="warning" title="${escapeAttr(t('toolbar.appendGateway'))}" ${appendDisabled} onclick="appendNodeAfter('${escapeAttr(current.id)}', 'exclusiveGateway')">◇</button>
          <button title="${escapeAttr(t('toolbar.appendEndEvent'))}" ${appendDisabled} onclick="appendNodeAfter('${escapeAttr(current.id)}', 'endEvent')">◉</button>
          <span class="node-toolbar-separator"></span>
          <button class="danger" title="${escapeAttr(t('toolbar.deleteNode'))}" onclick="deleteNode('${escapeAttr(current.id)}')">⌫</button>
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
        if (isSimulationCanvas()) return '';
        if (!isConnectableNode(current)) return '';
        if (state.selected.type === 'edge' && !state.edgeReconnect) return '';
        const active = state.connectionSource === current.id ? ' active' : '';
        return ['left', 'right', 'top', 'bottom'].map(position =>
          `<span class="connection-handle ${position}${active}" data-node-id="${escapeAttr(current.id)}" data-handle="${position}" title="${escapeAttr(t('handle.connectFrom', { nodeId: current.id, handle: position }))}" onmousedown="startConnectionDrag(event, '${current.id}', '${position}')"></span>`
        ).join('');
      }

      function isConnectableNode(current) {
        return !['participant', 'textAnnotation', 'subProcess'].includes(current.kind);
      }

      function containerResizeHandlesHtml(current) {
        if (state.selected.type !== 'node' || state.selected.id !== current.id) return '';
        return ['n', 'e', 's', 'w', 'nw', 'ne', 'sw', 'se'].map(direction =>
          `<span class="container-resize-handle ${direction}" title="${escapeAttr(t('handle.resizeContainer'))}" onmousedown="startContainerResize(event, '${escapeAttr(current.id)}', '${direction}')"></span>`
        ).join('');
      }

      function participantResizeHandlesHtml(current) {
        return containerResizeHandlesHtml(current);
      }

      function isSubProcessContainer(current) {
        return current.kind === 'subProcess';
      }

      function isStartEventKind(kind) {
        return kind === 'startEvent' || String(kind || '').endsWith('StartEvent');
      }

      function runtimeStartNodes(model = state.model) {
        return (model.nodes || []).filter(n => isStartEventKind(n.kind));
      }

      function duplicateStartEventMessage(kind, model = state.model) {
        return '';
      }

      function validateStartEventUniqueness(model = state.model) {
        return true;
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
          humanTask: '♙',
          scriptTask: '𝑓',
          workflow: '↳'
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
        if (current.kind === 'boundaryEvent') return current.config?.attachedToRef || 'attached boundary';
        if (current.kind === 'workflow') return childWorkflowLabel(current);
        if (isHumanTaskKind(current.kind)) {
          const mode = humanTaskMode(current);
          const groups = current.config?.flowFoundryAssignmentDefinition?.candidateGroups;
          if (mode === 'offline') return 'offline';
          return groups || mode;
        }
        if (current.activityType) return current.activityType;
        if (current.decisionRef) return current.decisionRef;
        if (current.config?.flowFoundryAssignmentDefinition?.candidateGroups) return current.config.flowFoundryAssignmentDefinition.candidateGroups;
        if (current.config?.script) return current.config.script;
        return current.id;
      }

      function renderEdges() {
        const { edges: svg, readonly } = activeCanvasElements();
        if (!svg) return;
        svg.innerHTML = '<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#2587e6"/></marker></defs>';
        for (const current of state.model.edges) {
          const from = state.model.nodes.find(n => n.id === current.from);
          const to = state.model.nodes.find(n => n.id === current.to);
          if (!from || !to) continue;
          const p = edgePoints(from, to, current);
          const d = edgePath(p, current);
          const selected = state.selected.type === 'edge' && state.selected.id === current.id;
          const line = svgEl('path', {
            d,
            class: `edge-line${selected ? ' edge-line-selected' : ''}`,
            'marker-end': 'url(#arrow)'
          });
          svg.appendChild(line);
          const hit = svgEl('path', { d, stroke: 'transparent', 'stroke-width': 18, fill: 'none', class: 'edge-hit' });
          if (!readonly) {
            hit.onclick = evt => { evt.stopPropagation(); select('edge', current.id); };
          } else {
            hit.style.pointerEvents = 'none';
          }
          svg.appendChild(hit);
          const label = edgeConditionLabel(current);
          if (label) {
            svg.appendChild(svgEl('text', { x: (p.x1 + p.x2) / 2 - 60, y: (p.y1 + p.y2) / 2 - 10, fill: '#cbd5e1', 'font-size': 12 }, label));
          }
        }
        renderEdgeReconnectDraft(svg);
        if (!readonly) renderEdgeEndpointOverlay();
        if (state.connectionSource && state.connectionSourceHandle && state.connectionDraftTarget) {
          const from = state.model.nodes.find(n => n.id === state.connectionSource);
          if (from) {
            const start = handlePoint(from, state.connectionSourceHandle);
            const end = state.connectionDraftTarget;
            const d = edgePath({
              x1: start.x,
              y1: start.y,
              x2: end.x,
              y2: end.y,
              fromHandle: state.connectionSourceHandle,
              toHandle: inferDraftTargetHandle(start, end)
            });
            svg.appendChild(svgEl('path', {
              d,
              class: 'edge-line edge-line-draft',
              'marker-end': 'url(#arrow)'
            }));
          }
        }
      }

      function renderEdgeEndpointOverlay() {
        document.querySelectorAll('.edge-endpoint-handle').forEach(el => el.remove());
        if (isSimulationCanvas()) return;
        if (state.selected.type !== 'edge' || !state.selected.id) return;
        const edge = state.model.edges.find(e => e.id === state.selected.id);
        if (!edge) return;
        const from = state.model.nodes.find(n => n.id === edge.from);
        const to = state.model.nodes.find(n => n.id === edge.to);
        if (!from || !to) return;
        const points = edgePoints(from, to, edge);
        const reconnect = state.edgeReconnect?.edgeId === edge.id ? state.edgeReconnect : null;
        const { content } = activeCanvasElements();
        if (!content) return;
        [
          {
            endpoint: 'from',
            x: reconnect?.endpoint === 'from' && reconnect.draftPoint ? reconnect.draftPoint.x : points.x1,
            y: reconnect?.endpoint === 'from' && reconnect.draftPoint ? reconnect.draftPoint.y : points.y1,
            title: t('edge.reconnectSource')
          },
          {
            endpoint: 'to',
            x: reconnect?.endpoint === 'to' && reconnect.draftPoint ? reconnect.draftPoint.x : points.x2,
            y: reconnect?.endpoint === 'to' && reconnect.draftPoint ? reconnect.draftPoint.y : points.y2,
            title: t('edge.reconnectTarget')
          }
        ].forEach(handle => {
          const el = document.createElement('div');
          el.className = `edge-endpoint-handle${reconnect?.endpoint === handle.endpoint ? ' dragging' : ''}`;
          el.style.left = `${handle.x}px`;
          el.style.top = `${handle.y}px`;
          el.title = handle.title;
          el.onmousedown = evt => {
            evt.stopPropagation();
            state.suppressCanvasClick = true;
            startEdgeReconnectDrag(evt, edge.id, handle.endpoint);
          };
          content.appendChild(el);
        });
      }

      function renderEdgeReconnectDraft(svg) {
        const reconnect = state.edgeReconnect;
        if (!reconnect?.draftPoint) return;
        const edge = state.model.edges.find(e => e.id === reconnect.edgeId);
        if (!edge) return;
        const from = state.model.nodes.find(n => n.id === edge.from);
        const to = state.model.nodes.find(n => n.id === edge.to);
        if (!from || !to) return;
        let points;
        if (reconnect.endpoint === 'from') {
          const end = handlePoint(to, edge.toHandle || 'left');
          points = {
            x1: reconnect.draftPoint.x,
            y1: reconnect.draftPoint.y,
            x2: end.x,
            y2: end.y,
            fromHandle: inferDraftTargetHandle(end, reconnect.draftPoint),
            toHandle: edge.toHandle || 'left'
          };
        } else {
          const start = handlePoint(from, edge.fromHandle || 'right');
          points = {
            x1: start.x,
            y1: start.y,
            x2: reconnect.draftPoint.x,
            y2: reconnect.draftPoint.y,
            fromHandle: edge.fromHandle || 'right',
            toHandle: inferDraftTargetHandle(start, reconnect.draftPoint)
          };
        }
        svg.appendChild(svgEl('path', {
          d: edgePath(points, points),
          class: 'edge-line edge-line-draft',
          'marker-end': reconnect.endpoint === 'to' ? 'url(#arrow)' : ''
        }));
      }

      function normalizeCanvasOrigin() {
        if (state.isDragging) return;
        let minX = 0;
        let minY = 0;
        state.model.nodes.forEach(n => {
          const bounds = nodeBounds(n);
          minX = Math.min(minX, bounds.left);
          minY = Math.min(minY, bounds.top);
        });
        if (minX >= 0 && minY >= 0) return;
        const shiftX = minX < 0 ? -minX + 120 : 0;
        const shiftY = minY < 0 ? -minY + 120 : 0;
        if (!shiftX && !shiftY) return;
        state.model.nodes.forEach(n => {
          n.x += shiftX;
          n.y += shiftY;
        });
        if (state.connectionDraftTarget) {
          state.connectionDraftTarget.x += shiftX;
          state.connectionDraftTarget.y += shiftY;
        }
        if (state.edgeReconnect?.draftPoint) {
          state.edgeReconnect.draftPoint.x += shiftX;
          state.edgeReconnect.draftPoint.y += shiftY;
        }
        const canvas = activeCanvasElements().canvas;
        if (canvas) {
          state.panX -= shiftX * state.scale;
          state.panY -= shiftY * state.scale;
          if (!isSimulationCanvas() && typeof applyViewportTransform === 'function') applyViewportTransform();
        }
      }

      function resizeCanvasToModel() {
        if (state.isDragging) return;
        normalizeCanvasOrigin();
        const { content, edges: svg } = activeCanvasElements();
        if (!content || !svg) return;
        const extent = canvasExtent();
        content.style.width = `${extent.width}px`;
        content.style.height = `${extent.height}px`;
        svg.setAttribute('width', String(extent.width));
        svg.setAttribute('height', String(extent.height));
        svg.style.width = `${extent.width}px`;
        svg.style.height = `${extent.height}px`;
      }

      function canvasExtent() {
        const padding = 480;
        const minWidth = 2400;
        const minHeight = 1600;
        let maxX = minWidth;
        let maxY = minHeight;
        const includeBounds = bounds => {
          maxX = Math.max(maxX, bounds.right + padding);
          maxY = Math.max(maxY, bounds.bottom + padding);
        };
        state.model.nodes.forEach(n => includeBounds(nodeBounds(n)));
        if (state.connectionDraftTarget) {
          includeBounds({
            left: state.connectionDraftTarget.x,
            top: state.connectionDraftTarget.y,
            right: state.connectionDraftTarget.x,
            bottom: state.connectionDraftTarget.y
          });
        }
        if (state.edgeReconnect?.draftPoint) {
          const point = state.edgeReconnect.draftPoint;
          includeBounds({
            left: point.x,
            top: point.y,
            right: point.x,
            bottom: point.y
          });
        }
        return {
          width: Math.ceil(maxX),
          height: Math.ceil(maxY)
        };
      }

      function edgePoints(from, to, flow) {
        const fromHandle = flow.fromHandle || inferConnectionHandle(from, to, 'source');
        const toHandle = flow.toHandle || inferConnectionHandle(from, to, 'target');
        const start = handlePoint(from, fromHandle);
        const end = handlePoint(to, toHandle);
        return { x1: start.x, y1: start.y, x2: end.x, y2: end.y, fromHandle, toHandle };
      }

      function inferConnectionHandle(from, to, role) {
        const fromCenter = nodeCenter(from);
        const toCenter = nodeCenter(to);
        const dx = toCenter.x - fromCenter.x;
        const dy = toCenter.y - fromCenter.y;
        if (Math.abs(dx) >= Math.abs(dy)) {
          if (role === 'source') return dx >= 0 ? 'right' : 'left';
          return dx >= 0 ? 'left' : 'right';
        }
        if (role === 'source') return dy >= 0 ? 'bottom' : 'top';
        return dy >= 0 ? 'top' : 'bottom';
      }

      function edgePath(points, flow = {}) {
        const style = state.model.process.edgeRouting || 'orthogonal';
        if (style === 'curved') return curvedEdgePath(points);
        return roundedOrthogonalPath(orthogonalRoutePoints(points, flow));
      }

      function curvedEdgePath(p) {
        const c = Math.max(50, Math.abs(p.x2 - p.x1) / 2);
        return `M ${p.x1} ${p.y1} C ${p.x1 + c} ${p.y1}, ${p.x2 - c} ${p.y2}, ${p.x2} ${p.y2}`;
      }

      function orthogonalRoutePoints(p, flow = {}) {
        const fromHandle = flow.fromHandle || p.fromHandle || 'right';
        const toHandle = flow.toHandle || p.toHandle || 'left';
        const start = { x: p.x1, y: p.y1 };
        const end = { x: p.x2, y: p.y2 };
        const startStub = offsetPoint(start, fromHandle, edgeStubLength(fromHandle));
        const endStub = offsetPoint(end, toHandle, edgeStubLength(toHandle));
        const fromHorizontal = ['left', 'right'].includes(fromHandle);
        const toHorizontal = ['left', 'right'].includes(toHandle);
        if (fromHorizontal && toHorizontal) {
          if (horizontalStubsNeedDetour(startStub, endStub, fromHandle, toHandle)) {
            const detourY = Math.max(startStub.y, endStub.y) + edgeDetourLength();
            return [start, startStub, { x: startStub.x, y: detourY }, { x: endStub.x, y: detourY }, endStub, end];
          }
          const midX = startStub.x + (endStub.x - startStub.x) / 2;
          return [start, startStub, { x: midX, y: startStub.y }, { x: midX, y: endStub.y }, endStub, end];
        }
        if (!fromHorizontal && !toHorizontal) {
          if (verticalStubsNeedDetour(startStub, endStub, fromHandle, toHandle)) {
            const detourX = Math.max(startStub.x, endStub.x) + edgeDetourLength();
            return [start, startStub, { x: detourX, y: startStub.y }, { x: detourX, y: endStub.y }, endStub, end];
          }
          const midY = startStub.y + (endStub.y - startStub.y) / 2;
          return [start, startStub, { x: startStub.x, y: midY }, { x: endStub.x, y: midY }, endStub, end];
        }
        return [start, startStub, { x: endStub.x, y: startStub.y }, endStub, end];
      }

      function horizontalStubsNeedDetour(startStub, endStub, fromHandle, toHandle) {
        const tightGap = Math.abs(endStub.x - startStub.x) < edgeDetourLength();
        return (fromHandle === 'right' && toHandle === 'left' && (startStub.x > endStub.x || tightGap)) ||
          (fromHandle === 'left' && toHandle === 'right' && (startStub.x < endStub.x || tightGap));
      }

      function verticalStubsNeedDetour(startStub, endStub, fromHandle, toHandle) {
        const tightGap = Math.abs(endStub.y - startStub.y) < edgeDetourLength();
        return (fromHandle === 'bottom' && toHandle === 'top' && (startStub.y > endStub.y || tightGap)) ||
          (fromHandle === 'top' && toHandle === 'bottom' && (startStub.y < endStub.y || tightGap));
      }

      function offsetPoint(point, handle, distance) {
        const directions = {
          left: { x: -1, y: 0 },
          right: { x: 1, y: 0 },
          top: { x: 0, y: -1 },
          bottom: { x: 0, y: 1 }
        };
        const direction = directions[handle] || directions.right;
        return {
          x: point.x + direction.x * distance,
          y: point.y + direction.y * distance
        };
      }

      function edgeStubLength(handle) {
        return ['left', 'right'].includes(handle) ? 22 : 18;
      }

      function edgeDetourLength() {
        return 36;
      }

      function roundedOrthogonalPath(points) {
        points = compactOrthogonalPoints(points);
        const radius = 14;
        const commands = [`M ${points[0].x} ${points[0].y}`];
        for (let i = 1; i < points.length - 1; i++) {
          const previous = points[i - 1];
          const corner = points[i];
          const next = points[i + 1];
          const before = pointBeforeCorner(previous, corner, radius);
          const after = pointBeforeCorner(next, corner, radius);
          commands.push(`L ${before.x} ${before.y}`);
          commands.push(`Q ${corner.x} ${corner.y} ${after.x} ${after.y}`);
        }
        const last = points[points.length - 1];
        commands.push(`L ${last.x} ${last.y}`);
        return commands.join(' ');
      }

      function compactOrthogonalPoints(points) {
        const withoutDuplicates = points.filter((point, index) => {
          const previous = points[index - 1];
          return !previous || previous.x !== point.x || previous.y !== point.y;
        });
        return withoutDuplicates.filter((point, index) => {
          if (index === 0 || index === withoutDuplicates.length - 1) return true;
          const previous = withoutDuplicates[index - 1];
          const next = withoutDuplicates[index + 1];
          const horizontal = previous.y === point.y && point.y === next.y;
          const vertical = previous.x === point.x && point.x === next.x;
          return !horizontal && !vertical;
        });
      }

      function pointBeforeCorner(from, corner, radius) {
        const dx = from.x - corner.x;
        const dy = from.y - corner.y;
        const distance = Math.max(1, Math.abs(dx) + Math.abs(dy));
        const r = Math.min(radius, distance / 2);
        return {
          x: corner.x + Math.sign(dx) * r,
          y: corner.y + Math.sign(dy) * r
        };
      }

      function inferDraftTargetHandle(start, end) {
        return Math.abs(end.x - start.x) >= Math.abs(end.y - start.y)
          ? (end.x >= start.x ? 'left' : 'right')
          : (end.y >= start.y ? 'top' : 'bottom');
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
        if (n.kind === 'participant') return { width: n.width || 2520, height: n.height || 1040 };
        if (n.kind === 'textAnnotation') return { width: 170, height: 78 };
        if (n.kind === 'subProcess') return { width: n.width || 840, height: n.height || 520 };
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
        if (state.currentView !== 'modeler') return;
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
            <label>Edge Routing</label>
            <select id="edgeRouting" onchange="updateProcess('edgeRouting', this.value)">
              <option value="orthogonal" ${(state.model.process.edgeRouting || 'orthogonal') === 'orthogonal' ? 'selected' : ''}>Orthogonal rounded</option>
              <option value="curved" ${state.model.process.edgeRouting === 'curved' ? 'selected' : ''}>Curved</option>
            </select>
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
          ${responsibilitySection(n)}
          ${routingSection(n)}
          ${taskDefinitionSection(n)}
          ${humanTaskSection(n)}
          ${workflowSection(n)}
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
              <div class="help">${escapeHtml(t('prop.edgeDmnHelp'))}</div>
            ` : `
              <label>FEEL Expression</label><input value="${escapeAttr(feelCondition(e))}" placeholder="\${amount > 1000}" oninput="updateEdgeFeel(this.value)" ${mode === 'default' ? 'disabled' : ''} />
              <div class="help">${escapeHtml(t('prop.edgeFeelHelp'))}</div>
            `}
          </div>
          <div class="prop-section"><h3>Default Flow</h3>
            <label class="switch-row"><input type="checkbox" ${e.condition === 'default' ? 'checked' : ''} onchange="updateEdge('condition', this.checked ? 'default' : '')" /> Taken when no other condition matches</label>
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
            <div class="help">${escapeHtml(t('prop.participantDropHelp'))}</div>
          </div>`;
        }
        return `<div class="prop-section"><h3>Responsibility</h3>
          <label>Participant</label>
          <select onchange="updateParticipantOwner(this.value)">
            <option value="">None</option>
            ${participants.map(p => `<option value="${escapeAttr(p.id)}" ${n.participantId === p.id ? 'selected' : ''}>${escapeHtml(p.name || p.id)}</option>`).join('')}
          </select>
          <div class="help">${escapeHtml(t('prop.participantBoundaryHelp'))}</div>
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
        if (!['task','serviceTask','scriptTask'].includes(n.kind)) return '';
        const options = state.activities.map(a => `<option value="${a.id}" ${a.id === n.activityType ? 'selected' : ''}>${a.name || a.id}</option>`).join('');
        return `<div class="prop-section"><h3>Task Definition</h3>
          <label>Task Type</label>
          <select onchange="updateActivityType(this.value)"><option value="">Select registered activity</option>${options}</select>
          <input style="margin-top:8px" value="${escapeAttr(n.activityType || '')}" placeholder="e.g. email-worker" oninput="updateActivityType(this.value)" />
          <label>Retries</label>
          <input type="number" value="${n.maxAttempts || 3}" oninput="updateNodeNumber('maxAttempts', this.value)" />
        </div>`;
      }

      function humanTaskMode(n) {
        return n.config?.flowFoundryHumanTask?.mode || 'managed';
      }

      function humanTaskSection(n) {
        if (!isHumanTaskKind(n.kind)) return '';
        const mode = humanTaskMode(n);
        return `<div class="prop-section"><h3>${escapeHtml(t('prop.humanTaskTitle'))}</h3>
          <label>${escapeHtml(t('prop.humanTaskMode'))}</label>
          <select onchange="updateConfigPath(['flowFoundryHumanTask','mode'], this.value); renderProperties();">
            <option value="managed" ${mode === 'managed' ? 'selected' : ''}>${escapeHtml(t('prop.humanTaskModeManaged'))}</option>
            <option value="offline" ${mode === 'offline' ? 'selected' : ''}>${escapeHtml(t('prop.humanTaskModeOffline'))}</option>
          </select>
          <div class="help">${escapeHtml(t('prop.humanTaskModeHelp'))}</div>
          ${mode === 'managed' ? `
            <label>Candidate Groups</label><input value="${escapeAttr(n.config?.flowFoundryAssignmentDefinition?.candidateGroups || '')}" oninput="updateConfigPath(['flowFoundryAssignmentDefinition','candidateGroups'], this.value)" />
            <label>Assignee</label><input value="${escapeAttr(n.config?.flowFoundryAssignmentDefinition?.assignee || '')}" oninput="updateConfigPath(['flowFoundryAssignmentDefinition','assignee'], this.value)" />
            <div class="help">${escapeHtml(t('prop.assignmentHelp'))}</div>
          ` : `<div class="help">${escapeHtml(t('prop.humanTaskOfflineHelp'))}</div>`}
        </div>`;
      }

      function scriptSection(n) {
        if (n.kind !== 'scriptTask') return '';
        return `<div class="prop-section"><h3>Script (Node.js)</h3>
          <label>Script Ref</label><input value="${escapeAttr(n.decisionRef || '')}" oninput="updateNode('decisionRef', this.value)" />
          <label>Script Version</label><input value="${escapeAttr(n.decisionVersion || '1.0.0')}" oninput="updateNode('decisionVersion', this.value)" />
          <div class="help">${escapeHtml(t('prop.scriptTaskHelp'))}</div>
        </div>`;
      }

      function workflowSection(n) {
        if (n.kind !== 'workflow') return '';
        const selectedWorkflowId = n.config?.childWorkflowId || '';
        const selectedWorkflow = childWorkflowRecord(selectedWorkflowId);
        const versions = selectedWorkflow?.versions || [];
        return `<div class="prop-section"><h3>Child Workflow</h3>
          <label>Workflow</label>
          <select onchange="updateChildWorkflowRef(this.value)">
            <option value="">Select workflow</option>
            ${childWorkflowOptions(selectedWorkflowId)}
          </select>
          <label>Version</label>
          <select onchange="updateConfig('childWorkflowVersion', this.value)" ${versions.length === 0 ? 'disabled' : ''}>
            ${versions.map(v => `<option value="${escapeAttr(v.version)}" ${(n.config?.childWorkflowVersion || selectedWorkflow?.version) === v.version ? 'selected' : ''}>v${escapeHtml(v.version)}</option>`).join('') || '<option value="1.0.0">v1.0.0</option>'}
          </select>
          <label>Child Task Queue</label>
          <input value="${escapeAttr(n.config?.childTaskQueue || '')}" placeholder="${escapeAttr(t('prop.childTaskQueuePlaceholder'))}" oninput="updateConfig('childTaskQueue', this.value)" />
          <div class="help">${escapeHtml(t('prop.childWorkflowHelp'))}</div>
        </div>`;
      }

      function childWorkflowOptions(selectedWorkflowId) {
        return state.workflows
          .filter(w => w.id !== state.activeWorkflowId)
          .map(w => `<option value="${escapeAttr(w.id)}" ${selectedWorkflowId === w.id ? 'selected' : ''}>${escapeHtml(w.name)} (${escapeHtml(w.id)})</option>`)
          .join('');
      }

      function childWorkflowRecord(id) {
        return state.workflows.find(w => w.id === id);
      }

      function childWorkflowLabel(n) {
        const workflow = childWorkflowRecord(n.config?.childWorkflowId);
        if (!workflow) return n.config?.childWorkflowId || 'child workflow';
        return `${workflow.name} v${n.config?.childWorkflowVersion || workflow.version || 'latest'}`;
      }

      function structuralSection(n) {
        if (!['subProcess','participant'].includes(n.kind)) return '';
        if (n.kind === 'participant') {
          return `<div class="prop-section"><h3>Participant / Pool</h3>
            <label>Participant Ref</label><input value="${escapeAttr(n.config?.participantRef || '')}" placeholder="business-team" oninput="updateConfig('participantRef', this.value)" />
            <label>Pool Width</label><input type="number" value="${n.width || 2520}" oninput="updateNodeNumber('width', this.value)" />
            <label>Pool Height</label><input type="number" value="${n.height || 1040}" oninput="updateNodeNumber('height', this.value)" />
            <div class="help">${escapeHtml(t('prop.participantHelp'))}</div>
          </div>`;
        }
        return `<div class="prop-section"><h3>Sub-process</h3>
          <label>Container Width</label><input type="number" value="${n.width || 840}" oninput="updateNodeNumber('width', this.value)" />
          <label>Container Height</label><input type="number" value="${n.height || 520}" oninput="updateNodeNumber('height', this.value)" />
          <div class="help">${escapeHtml(t('prop.subProcessHelp'))}</div>
        </div>`;
      }

      function annotationSection(n) {
        if (n.kind !== 'textAnnotation') return '';
        return `<div class="prop-section"><h3>Text Annotation</h3>
          <label>Text</label><textarea oninput="updateNode('documentation', this.value)">${escapeHtml(n.documentation || '')}</textarea>
          <div class="help">${escapeHtml(t('prop.annotationHelp'))}</div>
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

      function timerSection(n) {
        if (!['timerEvent','intermediateEvent','intermediateCatchEvent','boundaryEvent'].includes(n.kind)) return '';
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
        state.edgeReconnect = null;
        state.selected = { type, id };
        if (type === 'node' || type === 'edge') {
          expandPropertiesPanel();
        } else {
          collapsePropertiesPanel();
        }
        renderCanvas();
        renderProperties();
        updateButtons();
      }

      function focusNodeProperties(nodeId) {
        state.selected = { type: 'node', id: nodeId };
        expandPropertiesPanel();
        renderCanvas();
        renderProperties();
        message(t('message.nodeSelected', { nodeId }));
      }

      function changeNodeType(nodeId) {
        const n = state.model.nodes.find(node => node.id === nodeId);
        if (!n) return;
        if (!canMorphTaskType(n)) {
          message(t('message.onlyTaskMorph'));
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
        if (['task','serviceTask'].includes(n.kind)) {
          n.activityType = selected.activityType || state.activities[0]?.id || '';
          n.maxAttempts = selected.maxAttempts || 3;
        }
        if (n.kind === 'scriptTask') {
          n.activityType = 'script-runtime';
          n.decisionRef = selected.decisionRef || n.decisionRef || 'demo-script';
          n.decisionVersion = selected.decisionVersion || n.decisionVersion || '1.0.0';
        }
        if (n.kind === 'humanTask') {
          n.activityType = 'human-task';
        }
        state.model.edges = state.model.edges.filter(e => isConnectableNode(n) || (e.from !== n.id && e.to !== n.id));
        if (isParticipantAssignable(n)) syncNodeParticipant(n);
        else delete n.participantId;
        if (isParticipantContainer(n)) syncParticipantAssignments();
        state.taskMorphMenuNodeId = null;
        state.selected = { type: 'node', id: n.id };
        message(t('message.taskTypeChanged', { label: selected.label }));
        renderAll();
      }

      function taskMorphTypes() {
        return [
          { kind: 'task', label: 'Generic Task' },
          { kind: 'serviceTask', label: 'Service Task' },
          { kind: 'humanTask', label: 'Human Task', activityType: 'human-task', config: { flowFoundryHumanTask: { mode: 'managed' }, flowFoundryAssignmentDefinition: { candidateGroups: 'operator' } } },
          { kind: 'scriptTask', label: 'Script Task', activityType: 'script-runtime', decisionRef: 'demo-script', decisionVersion: '1.0.0' }
        ];
      }

      function taskMorphLabel(kind) {
        return taskMorphTypes().find(option => option.kind === kind)?.label || 'Task';
      }

      function taskMorphIcon(kind) {
        return {
          task: '▭',
          serviceTask: '⚙',
          humanTask: '♙',
          scriptTask: '𝑓'
        }[kind] || '▭';
      }

      function appendNodeAfter(sourceId, kind) {
        void appendNodeAfterAsync(sourceId, kind);
      }

      async function appendNodeAfterAsync(sourceId, kind) {
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
        const created = await createNodeFromPaletteItem(spec, point, { selectNewNode: false });
        if (!created) return;
        createSequenceFlow(source.id, created.id, 'right', 'left', { selectNewEdge: false, skipHistory: true });
        state.selected = { type: 'node', id: created.id };
        message(t('message.nodeAppended', { name: created.name }));
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
          message(t('message.nodeNotConnectable'));
          return;
        }
        state.selected = { type: 'node', id: nodeId };
        renderCanvas();
        renderProperties();
        message(t('message.connectHint'));
      }

      function setFirstOutgoingAsDefault(nodeId) {
        const first = state.model.edges.find(e => e.from === nodeId);
        if (!first) {
          message(t('message.noOutgoingEdges'));
          return;
        }
        pushHistory();
        state.model.edges.filter(e => e.from === nodeId).forEach(e => {
          e.condition = e.id === first.id ? 'default' : (e.condition === 'default' ? '' : e.condition);
        });
        state.selected = { type: 'edge', id: first.id };
        message(t('message.defaultFlowSet', { edgeId: first.id }));
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
        if (item.kind.includes('Event')) return createEventDragImage(item);
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

      function createEventDragImage(item) {
        const wrapper = document.createElement('div');
        wrapper.className = 'drag-image';
        const eventClass = item.kind === 'boundaryEvent' ? 'boundary-event' : '';
        wrapper.innerHTML = `
          <div class="node event-node">
            <div class="event-shape ${item.kind} ${eventClass}"></div>
          </div>
        `;
        return wrapper;
      }

      function dragImageHotspot(item) {
        if (item.kind.includes('Gateway')) {
          return { x: 42, y: 42 };
        }
        if (item.kind.includes('Event')) {
          return { x: 42, y: 42 };
        }
        if (item.kind === 'subProcess') {
          return { x: 210, y: 130 };
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

      function canvasPointFromClient(clientX, clientY) {
        const canvas = $('canvas');
        const rect = canvas.getBoundingClientRect();
        return {
          x: (clientX - rect.left - state.panX) / state.scale,
          y: (clientY - rect.top - state.panY) / state.scale
        };
      }

      function canvasPointFromEvent(event) {
        return canvasPointFromClient(event.clientX, event.clientY);
      }

      function connectionHandleNearPoint(clientX, clientY) {
        const point = canvasPointFromClient(clientX, clientY);
        const threshold = 22 / state.scale;
        let best = null;
        let bestDistance = Infinity;
        state.model.nodes.filter(isConnectableNode).forEach(node => {
          ['left', 'right', 'top', 'bottom'].forEach(position => {
            const hp = handlePoint(node, position);
            const distance = Math.hypot(hp.x - point.x, hp.y - point.y);
            if (distance <= threshold && distance < bestDistance) {
              bestDistance = distance;
              best = { nodeId: node.id, handle: position };
            }
          });
        });
        return best;
      }

      function connectionHandleFromPoint(x, y) {
        const element = document.elementFromPoint(x, y);
        if (!element?.closest?.('.edge-endpoint-handle')) {
          const handle = element?.closest?.('.connection-handle');
          if (handle) {
            return { nodeId: handle.dataset.nodeId, handle: handle.dataset.handle };
          }
        }
        if (state.edgeReconnect || state.connectionSource) {
          return connectionHandleNearPoint(x, y);
        }
        return null;
      }

      function highlightConnectionTargetForHandle(target, options = {}) {
        clearConnectionTargetHighlight();
        if (!target) return;
        if (options.excludeNodeId && target.nodeId === options.excludeNodeId) return;
        document
          .querySelector(`.connection-handle[data-node-id="${CSS.escape(target.nodeId)}"][data-handle="${CSS.escape(target.handle)}"]`)
          ?.classList.add('drop-target');
      }

      function addNodeFromPalette(item, point = null) {
        void createNodeFromPaletteItem(item, point);
      }

      async function createNodeFromPaletteItem(item, point = null, options = {}) {
        const id = await allocatePlatformId(platformIdKindForNodeKind(item.kind));
        const position = point || { x: 180 + state.model.nodes.length * 24, y: 140 + state.model.nodes.length * 18 };
        const n = node(id, item.kind, item.label, 180 + state.model.nodes.length * 24, 140 + state.model.nodes.length * 18, {
          activityType: item.activityType,
          decisionRef: item.decisionRef,
          decisionVersion: item.decisionVersion,
          documentation: item.documentation || '',
          config: item.config ? structuredClone(item.config) : {}
        });
        const size = nodeSize(n);
        n.x = Math.round(position.x - size.width / 2);
        n.y = Math.round(position.y - size.height / 2);
        if (isStartEventKind(n.kind)) {
          const duplicateMessage = duplicateStartEventMessage(n.kind);
          if (duplicateMessage) {
            message(duplicateMessage);
            return null;
          }
        }
        if (!canPlaceNodeInParticipantMode(n)) return null;
        pushHistory();
        if (['task','serviceTask'].includes(item.kind)) {
          n.activityType = state.activities[0]?.id || '';
          n.maxAttempts = 3;
        }
        if (item.activityType) n.activityType = item.activityType;
        if (item.kind === 'scriptTask') {
          n.activityType = 'script-runtime';
          if (item.decisionRef) n.decisionRef = item.decisionRef;
          if (item.decisionVersion) n.decisionVersion = item.decisionVersion;
        }
        if (item.kind === 'humanTask') {
          n.activityType = 'human-task';
        }
        state.model.nodes.push(n);
        if (isParticipantContainer(n) || isSubProcessContainer(n)) syncParticipantAssignments();
        else syncNodeContainerBinding(n);
        if (options.selectNewNode !== false) {
          select('node', id);
          message(t('message.nodeAdded', { label: item.label }));
        }
        return n;
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
        message(t('message.connectDrag', { nodeId, handle: position }));
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
            message(t('message.connectCancelled'));
            renderCanvas();
          }
        };
        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
      }

      function startEdgeReconnectDrag(evt, edgeId, endpoint) {
        if (evt.button !== 0) return;
        evt.preventDefault();
        evt.stopPropagation();
        const edge = state.model.edges.find(e => e.id === edgeId);
        if (!edge) return;
        state.selected = { type: 'edge', id: edgeId };
        state.edgeReconnect = {
          edgeId,
          endpoint,
          draftPoint: canvasPointFromEvent(evt)
        };
        message(endpoint === 'from' ? t('message.reconnectSource') : t('message.reconnectTarget'));
        renderCanvas();

        const onMove = move => {
          state.edgeReconnect.draftPoint = canvasPointFromEvent(move);
          highlightEdgeReconnectTarget(move);
          renderEdges();
        };
        const onUp = up => {
          document.removeEventListener('mousemove', onMove);
          document.removeEventListener('mouseup', onUp);
          const target = connectionHandleFromPoint(up.clientX, up.clientY);
          clearConnectionTargetHighlight();
          completeEdgeReconnect(target);
        };
        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
      }

      function highlightEdgeReconnectTarget(evt) {
        const reconnect = state.edgeReconnect;
        const edge = state.model.edges.find(e => e.id === reconnect?.edgeId);
        const target = connectionHandleFromPoint(evt.clientX, evt.clientY);
        if (!edge || !target) {
          clearConnectionTargetHighlight();
          return;
        }
        const fixedNodeId = reconnect.endpoint === 'from' ? edge.to : edge.from;
        highlightConnectionTargetForHandle(target, { excludeNodeId: fixedNodeId });
      }

      function highlightConnectionTarget(evt) {
        const target = connectionHandleFromPoint(evt.clientX, evt.clientY);
        highlightConnectionTargetForHandle(target, { excludeNodeId: state.connectionSource });
      }

      function completeEdgeReconnect(target) {
        const reconnect = state.edgeReconnect;
        const edge = state.model.edges.find(e => e.id === reconnect?.edgeId);
        if (!reconnect || !edge) {
          state.edgeReconnect = null;
          renderCanvas();
          return;
        }
        if (!target) {
          state.edgeReconnect = null;
          message(t('message.reconnectCancelled'));
          renderCanvas();
          return;
        }
        const nextFrom = reconnect.endpoint === 'from' ? target.nodeId : edge.from;
        const nextTo = reconnect.endpoint === 'to' ? target.nodeId : edge.to;
        if (nextFrom === nextTo) {
          state.edgeReconnect = null;
          message(t('message.sameNodeEdge'));
          renderCanvas();
          return;
        }
        const duplicate = state.model.edges.find(e => e.id !== edge.id && e.from === nextFrom && e.to === nextTo);
        if (duplicate) {
          state.edgeReconnect = null;
          select('edge', duplicate.id);
          message(t('message.edgeExists', { edgeId: duplicate.id }));
          return;
        }
        pushHistory();
        if (reconnect.endpoint === 'from') {
          edge.from = target.nodeId;
          edge.fromHandle = target.handle;
        } else {
          edge.to = target.nodeId;
          edge.toHandle = target.handle;
        }
        state.edgeReconnect = null;
        state.selected = { type: 'edge', id: edge.id };
        message(reconnect.endpoint === 'from'
          ? t('message.reconnectSourceDone', { edgeId: edge.id })
          : t('message.reconnectTargetDone', { edgeId: edge.id }));
        renderAll();
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
          message(t('message.edgeExists', { edgeId: duplicate.id }));
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
          message(t('message.edgeCreated', { id }));
        }
        return state.model.edges.find(e => e.id === id);
      }
