      function deleteSelected() {
        if (state.selected.type === 'node') {
          deleteNode(state.selected.id);
          return;
        }
        if (state.selected.type === 'edge') {
          deleteEdge(state.selected.id);
          return;
        }
        message(t('message.selectNodeOrEdge'));
      }

      function deleteNode(id) {
        const target = state.model.nodes.find(n => n.id === id);
        if (!target) return;
        pushHistory();
        const removedNodeIds = new Set([id]);
        if (isSubProcessContainer(target)) {
          state.model.nodes
            .filter(n => n.parentSubProcessId === id)
            .forEach(n => removedNodeIds.add(n.id));
        }
        state.model.nodes = state.model.nodes.filter(n => !removedNodeIds.has(n.id));
        state.model.edges = state.model.edges.filter(e => !removedNodeIds.has(e.from) && !removedNodeIds.has(e.to));
        if (isParticipantContainer(target)) {
          state.model.nodes.forEach(candidate => {
            if (candidate.participantId === id) delete candidate.participantId;
          });
        }
        state.connectionSource = null;
        state.connectionSourceHandle = null;
        state.connectBuffer = [];
        state.selected = { type: 'process', id: null };
        message(t('message.nodeDeleted', { id }));
        renderAll();
      }

      function deleteEdge(id) {
        const target = state.model.edges.find(e => e.id === id);
        if (!target) return;
        pushHistory();
        state.model.edges = state.model.edges.filter(e => e.id !== id);
        state.selected = { type: 'process', id: null };
        message(t('message.edgeDeleted', { id }));
        renderAll();
      }

      function setDefaultFlow(edgeId) {
        const n = selectedNode();
        pushHistory();
        state.model.edges.filter(e => e.from === n.id).forEach(e => {
          if (e.id === edgeId) e.condition = 'default';
          else if (e.condition === 'default') e.condition = '';
        });
        renderAll();
      }

      function keepDragInView(node) {
        if (state.isDragging) return;
        const canvas = $('canvas');
        if (!canvas || !node) return;
        const bounds = nodeBounds(node);
        const margin = 64;
        const viewLeft = -state.panX / state.scale;
        const viewTop = -state.panY / state.scale;
        const viewRight = viewLeft + canvas.clientWidth / state.scale;
        const viewBottom = viewTop + canvas.clientHeight / state.scale;
        if (bounds.right + margin > viewRight) {
          state.panX -= (bounds.right + margin - viewRight) * state.scale;
        }
        if (bounds.bottom + margin > viewBottom) {
          state.panY -= (bounds.bottom + margin - viewBottom) * state.scale;
        }
        if (bounds.left - margin < viewLeft) {
          state.panX += (viewLeft - (bounds.left - margin)) * state.scale;
        }
        if (bounds.top - margin < viewTop) {
          state.panY += (viewTop - (bounds.top - margin)) * state.scale;
        }
        if (typeof applyViewportTransform === 'function') applyViewportTransform();
      }

      function updateDraggedNodesVisual(node, children = []) {
        const el = nodeElement(node.id);
        if (el) {
          el.style.left = `${node.x}px`;
          el.style.top = `${node.y}px`;
        }
        children.forEach(child => {
          const childEl = nodeElement(child.node.id);
          if (childEl) {
            childEl.style.left = `${child.node.x}px`;
            childEl.style.top = `${child.node.y}px`;
          }
        });
        renderEdges();
      }

      function finishNodeDrag() {
        renderCanvas();
        renderMinimap();
        renderProperties();
        updateButtons();
        updateViewportUi();
      }

      function makeDraggable(el, n) {
        let start = null;
        el.onmousedown = evt => {
          if (evt.button !== 0) return;
          if (evt.target.classList.contains('connection-handle')) return;
          if (evt.target.classList.contains('container-resize-handle')) return;
          if (evt.target.closest('.annotation-editor')) return;
          evt.preventDefault();
          evt.stopPropagation();
          state.suppressCanvasClick = true;
          state.isDragging = true;
          selectNodeOnPointerDown(n.id);
          const children = draggableContainerChildren(n);
          start = {
            mx: evt.clientX,
            my: evt.clientY,
            x: n.x,
            y: n.y,
            moved: false,
            children: children.map(child => ({ node: child, x: child.x, y: child.y }))
          };
          document.onmousemove = move => {
            if (!start) return;
            const dx = (move.clientX - start.mx) / state.scale;
            const dy = (move.clientY - start.my) / state.scale;
            if (!start.moved && (Math.abs(dx) > 0 || Math.abs(dy) > 0)) {
              pushHistory(false);
              start.moved = true;
            }
            n.x = start.x + dx;
            n.y = start.y + dy;
            start.children.forEach(child => {
              child.node.x = child.x + dx;
              child.node.y = child.y + dy;
            });
            updateDraggedNodesVisual(n, start.children);
          };
          document.onmouseup = () => {
            if (start) {
              if (isParticipantContainer(n)) {
                syncParticipantAssignments();
              } else if (canPlaceNodeInParticipantMode(n)) {
                if (isSubProcessContainer(n)) syncParticipantAssignments();
                else syncNodeContainerBinding(n);
              } else {
                n.x = start.x;
                n.y = start.y;
                start.children.forEach(child => {
                  child.node.x = child.x;
                  child.node.y = child.y;
                });
                syncParticipantAssignments();
              }
            }
            start = null;
            document.onmousemove = null;
            document.onmouseup = null;
            state.isDragging = false;
            finishNodeDrag();
            setTimeout(() => {
              state.suppressCanvasClick = false;
            }, 0);
          };
        };
      }

      function selectNodeOnPointerDown(nodeId) {
        if (state.selected.type === 'node' && state.selected.id === nodeId) return;
        state.taskMorphMenuNodeId = null;
        state.selected = { type: 'node', id: nodeId };
        expandPropertiesPanel();
        renderProperties();
        updateButtons();
      }

      function startContainerResize(evt, containerId, direction) {
        evt.preventDefault();
        evt.stopPropagation();
        const container = state.model.nodes.find(node => node.id === containerId && (isParticipantContainer(node) || isSubProcessContainer(node)));
        if (!container) return;
        const size = nodeSize(container);
        const min = containerResizeMinSize(container);
        const start = {
          mx: evt.clientX,
          my: evt.clientY,
          x: container.x,
          y: container.y,
          width: size.width,
          height: size.height,
          moved: false
        };
        state.isDragging = true;
        document.onmousemove = move => {
          const dx = (move.clientX - start.mx) / state.scale;
          const dy = (move.clientY - start.my) / state.scale;
          if (!start.moved && (Math.abs(dx) > 0 || Math.abs(dy) > 0)) {
            pushHistory(false);
            start.moved = true;
          }
          let nextWidth = start.width;
          let nextHeight = start.height;
          let nextX = start.x;
          let nextY = start.y;
          if (direction.includes('e')) nextWidth = Math.max(min.width, Math.round(start.width + dx));
          if (direction.includes('w')) {
            nextWidth = Math.max(min.width, Math.round(start.width - dx));
            nextX = start.x + start.width - nextWidth;
          }
          if (direction.includes('s')) nextHeight = Math.max(min.height, Math.round(start.height + dy));
          if (direction.includes('n')) {
            nextHeight = Math.max(min.height, Math.round(start.height - dy));
            nextY = start.y + start.height - nextHeight;
          }
          container.width = nextWidth;
          container.height = nextHeight;
          container.x = nextX;
          container.y = nextY;
          if (isParticipantContainer(container)) syncParticipantAssignments();
          renderCanvas();
        };
        document.onmouseup = () => {
          if (isParticipantContainer(container)) syncParticipantAssignments();
          document.onmousemove = null;
          document.onmouseup = null;
          state.isDragging = false;
          finishNodeDrag();
        };
      }

      function startParticipantResize(evt, participantId, direction) {
        startContainerResize(evt, participantId, direction);
      }

      function containerResizeMinSize(container) {
        if (isSubProcessContainer(container)) return { width: 320, height: 200 };
        return { width: 360, height: 160 };
      }

      function draggableContainerChildren(container) {
        if (isParticipantContainer(container)) return participantChildNodes(container);
        if (isSubProcessContainer(container)) return containedSubProcessNodes(container);
        return [];
      }

      function containedSubProcessNodes(container) {
        const bounds = nodeBounds(container);
        return state.model.nodes.filter(candidate => {
          if (candidate.id === container.id) return false;
          if (isSubProcessContainer(candidate) || isParticipantContainer(candidate)) return false;
          return boundsFullyInside(nodeBounds(candidate), bounds);
        });
      }

      function participantChildNodes(participant) {
        const contentBounds = participantContentBounds(participant);
        return state.model.nodes.filter(candidate => {
          if (candidate.id === participant.id) return false;
          if (candidate.kind === 'participant') return false;
          if (isSubProcessContainer(candidate)) return boundsFullyInside(nodeBounds(candidate), contentBounds);
          if (!isParticipantAssignable(candidate) && candidate.kind !== 'textAnnotation') return false;
          return boundsFullyInside(nodeBounds(candidate), contentBounds);
        });
      }

      function participantNodeCount(participant) {
        return participantChildNodes(participant).length;
      }

      function isParticipantAssignable(node) {
        return Boolean(node) && node.kind !== 'participant' && node.kind !== 'textAnnotation';
      }

      function participantModeEnabled() {
        return state.model.nodes.some(isParticipantContainer);
      }

      function canPlaceNodeInParticipantMode(node, options = {}) {
        if (node.kind === 'participant') return true;
        if (!canPlaceNodeInSubProcess(node, options)) return false;
        if (!participantModeEnabled()) return true;
        if (participantForFullContainment(node)) return true;
        if (!options.silent) message(t('message.participantEnabled'));
        return false;
      }

      function canPlaceNodeInSubProcess(node, options = {}) {
        if (isParticipantContainer(node)) return true;
        const bounds = nodeBounds(node);
        const overlapping = subProcessesOverlapping(bounds, node.id);
        if (overlapping.length === 0) return true;
        const innermost = innermostSubProcess(bounds, overlapping);
        if (boundsFullyInside(bounds, nodeBounds(innermost))) return true;
        if (!options.silent) message(t('message.subProcessViolation'));
        return false;
      }

      function participantContainmentViolations() {
        if (!participantModeEnabled()) return [];
        return state.model.nodes.filter(node => node.kind !== 'participant' && !participantForFullContainment(node));
      }

      function subProcessContainmentViolations() {
        return state.model.nodes.filter(node => {
          if (isParticipantContainer(node)) return false;
          const bounds = nodeBounds(node);
          const overlapping = subProcessesOverlapping(bounds, node.id);
          if (overlapping.length === 0) return false;
          const innermost = innermostSubProcess(bounds, overlapping);
          return !boundsFullyInside(bounds, nodeBounds(innermost));
        });
      }

      function assertParticipantContainment() {
        const participantViolations = participantContainmentViolations();
        if (participantViolations.length > 0) {
          const names = participantViolations.slice(0, 5).map(n => n.name || n.id).join(', ');
          throw new Error(t('message.participantViolation', { names: `${names}${participantViolations.length > 5 ? ' ...' : ''}` }));
        }
        const subProcessViolations = subProcessContainmentViolations();
        if (subProcessViolations.length > 0) {
          const names = subProcessViolations.slice(0, 5).map(n => n.name || n.id).join(', ');
          throw new Error(t('message.subProcessViolationList', { names: `${names}${subProcessViolations.length > 5 ? ' ...' : ''}` }));
        }
      }

      function syncParticipantAssignments() {
        state.model.nodes.forEach(node => syncNodeContainerBinding(node));
      }

      function syncNodeContainerBinding(node) {
        if (isParticipantContainer(node) || isSubProcessContainer(node)) return;
        if (node.kind === 'textAnnotation') {
          syncNodeParticipant(node);
          syncNodeSubProcess(node);
          return;
        }
        if (isParticipantAssignable(node)) syncNodeParticipant(node);
      }

      function syncNodeParticipant(node) {
        if (isParticipantContainer(node) || isSubProcessContainer(node)) return;
        if (!isParticipantAssignable(node) && node.kind !== 'textAnnotation') return;
        const participant = participantForFullContainment(node);
        if (participant) node.participantId = participant.id;
        else delete node.participantId;
      }

      function syncNodeSubProcess(node) {
        if (isParticipantContainer(node) || isSubProcessContainer(node)) return;
        const container = subProcessForFullContainment(node);
        if (container) node.parentSubProcessId = container.id;
        else delete node.parentSubProcessId;
      }

      function participantForFullContainment(node) {
        const bounds = nodeBounds(node);
        return state.model.nodes
          .filter(isParticipantContainer)
          .filter(participant => boundsFullyInside(bounds, participantContentBounds(participant)))
          .sort((a, b) => nodeArea(a) - nodeArea(b))[0];
      }

      function subProcessForFullContainment(node) {
        const bounds = nodeBounds(node);
        return innermostSubProcess(bounds, subProcessesOverlapping(bounds, node.id));
      }

      function subProcessesOverlapping(bounds, excludeId = null) {
        return state.model.nodes.filter(candidate => {
          if (!isSubProcessContainer(candidate)) return false;
          if (excludeId && candidate.id === excludeId) return false;
          return boundsOverlap(bounds, nodeBounds(candidate));
        });
      }

      function innermostSubProcess(bounds, subProcesses) {
        return subProcesses
          .filter(subProcess => boundsOverlap(bounds, nodeBounds(subProcess)))
          .sort((a, b) => nodeArea(a) - nodeArea(b))[0];
      }

      function boundsFullyInside(inner, outer) {
        return inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom;
      }

      function boundsOverlap(a, b) {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
      }

      function participantAtNodeCenter(node) {
        return participantForFullContainment(node);
      }

      function participantById(id) {
        return state.model.nodes.find(node => node.id === id && isParticipantContainer(node));
      }

      function pointInBounds(point, bounds) {
        return point.x >= bounds.left && point.x <= bounds.right && point.y >= bounds.top && point.y <= bounds.bottom;
      }

      function participantContentBounds(participant) {
        const bounds = nodeBounds(participant);
        return {
          left: bounds.left + participantLabelWidth(),
          top: bounds.top,
          right: bounds.right,
          bottom: bounds.bottom
        };
      }

      function participantLabelWidth() {
        return 46;
      }

      function constrainPointToParticipant(point, participant, kind) {
        const bounds = participantContentBounds(participant);
        const size = nodeSize({ kind });
        const margin = 18;
        return {
          x: Math.min(Math.max(point.x, bounds.left + size.width / 2 + margin), bounds.right - size.width / 2 - margin),
          y: Math.min(Math.max(point.y, bounds.top + size.height / 2 + margin), bounds.bottom - size.height / 2 - margin)
        };
      }

      function nodeArea(node) {
        const size = nodeSize(node);
        return size.width * size.height;
      }

      function nodeBounds(n) {
        const size = nodeSize(n);
        return { left: n.x, top: n.y, right: n.x + size.width, bottom: n.y + size.height };
      }

      function nodeCenter(n) {
        const size = nodeSize(n);
        return { x: n.x + size.width / 2, y: n.y + size.height / 2 };
      }

      function autoLayout() {
        pushHistory();
        const containment = captureLayoutContainment();
        const topLevelNodes = state.model.nodes.filter(node => !containment.containedIds.has(node.id));
        const maxRowWidth = 3000;
        const gapX = 120;
        const gapY = 140;
        let x = 80;
        let y = 180;
        let rowHeight = 0;
        topLevelNodes.forEach(node => {
          const size = nodeSize(node);
          if (x > 80 && x + size.width > maxRowWidth) {
            x = 80;
            y += rowHeight + gapY;
            rowHeight = 0;
          }
          moveLayoutGroup(node, x - node.x, y - node.y, containment);
          x += size.width + gapX;
          rowHeight = Math.max(rowHeight, size.height);
        });
        restoreLayoutContainment(containment);
        renderAll();
      }

      function captureLayoutContainment() {
        const subProcessChildren = new Map();
        const participantChildren = new Map();
        const containedIds = new Set();
        const subProcesses = state.model.nodes.filter(isSubProcessContainer);
        const participants = state.model.nodes.filter(isParticipantContainer);

        subProcesses.forEach(container => {
          const bounds = nodeBounds(container);
          const children = state.model.nodes.filter(candidate =>
            candidate.id !== container.id &&
            !isParticipantContainer(candidate) &&
            boundsFullyInside(nodeBounds(candidate), bounds)
          );
          subProcessChildren.set(container.id, new Set(children.map(child => child.id)));
          children.forEach(child => containedIds.add(child.id));
        });

        participants.forEach(participant => {
          const contentBounds = participantContentBounds(participant);
          const children = state.model.nodes.filter(candidate =>
            candidate.id !== participant.id &&
            candidate.kind !== 'participant' &&
            boundsFullyInside(nodeBounds(candidate), contentBounds)
          );
          const ids = new Set(children.map(child => child.id));
          children
            .filter(isSubProcessContainer)
            .forEach(subProcess => collectLayoutDescendants(subProcess.id, subProcessChildren, ids));
          participantChildren.set(participant.id, ids);
          ids.forEach(id => containedIds.add(id));
        });

        return { subProcessChildren, participantChildren, containedIds };
      }

      function collectLayoutDescendants(containerId, subProcessChildren, result) {
        const children = subProcessChildren.get(containerId) || new Set();
        children.forEach(id => {
          result.add(id);
          const child = state.model.nodes.find(node => node.id === id);
          if (child && isSubProcessContainer(child)) collectLayoutDescendants(id, subProcessChildren, result);
        });
      }

      function moveLayoutGroup(node, dx, dy, containment) {
        const ids = new Set([node.id]);
        if (isParticipantContainer(node)) {
          (containment.participantChildren.get(node.id) || new Set()).forEach(id => ids.add(id));
        } else if (isSubProcessContainer(node)) {
          collectLayoutDescendants(node.id, containment.subProcessChildren, ids);
        }
        state.model.nodes.forEach(candidate => {
          if (!ids.has(candidate.id)) return;
          candidate.x += dx;
          candidate.y += dy;
        });
      }

      function restoreLayoutContainment(containment) {
        containment.participantChildren.forEach((ids, participantId) => {
          ids.forEach(id => {
            const node = state.model.nodes.find(candidate => candidate.id === id);
            if (node && (isParticipantAssignable(node) || node.kind === 'textAnnotation')) node.participantId = participantId;
          });
        });
        containment.subProcessChildren.forEach((ids, subProcessId) => {
          ids.forEach(id => {
            const node = state.model.nodes.find(candidate => candidate.id === id);
            if (node && !isParticipantContainer(node)) node.parentSubProcessId = subProcessId;
          });
        });
      }

      function pushHistory(render = true) {
        state.history.push(JSON.stringify(state.model));
        if (state.history.length > 50) state.history.shift();
        state.future = [];
        if (render) updateButtons();
      }

      function undo() {
        if (state.history.length === 0) return;
        state.future.push(JSON.stringify(state.model));
        state.model = JSON.parse(state.history.pop());
        select('process', null);
        renderAll();
      }

      function redo() {
        if (state.future.length === 0) return;
        state.history.push(JSON.stringify(state.model));
        state.model = JSON.parse(state.future.pop());
        select('process', null);
        renderAll();
      }

      function updateButtons() {
        $('undoBtn').disabled = state.history.length === 0;
        $('redoBtn').disabled = state.future.length === 0;
        $('deleteBtn').disabled = !['node', 'edge'].includes(state.selected.type);
      }
