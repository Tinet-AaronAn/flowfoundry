      function deleteSelected() {
        if (state.selected.type === 'node') {
          deleteNode(state.selected.id);
          return;
        }
        if (state.selected.type === 'edge') {
          deleteEdge(state.selected.id);
          return;
        }
        message('请先选择一个节点或连线');
      }

      function deleteNode(id) {
        const target = state.model.nodes.find(n => n.id === id);
        if (!target) return;
        if (isDefaultSubProcessBoundaryNode(target)) {
          message('Sub-process 默认 Start/End 节点不可删除');
          return;
        }
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
        message(`已删除节点：${id}`);
        renderAll();
      }

      function deleteEdge(id) {
        const target = state.model.edges.find(e => e.id === id);
        if (!target) return;
        pushHistory();
        state.model.edges = state.model.edges.filter(e => e.id !== id);
        state.selected = { type: 'process', id: null };
        message(`已删除连线：${id}`);
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
            renderCanvas();
          };
          document.onmouseup = () => {
            if (start) {
              if (isParticipantContainer(n)) {
                syncParticipantAssignments();
              } else if (canPlaceNodeInParticipantMode(n)) {
                if (isSubProcessContainer(n)) syncParticipantAssignments();
                else syncNodeParticipant(n);
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
            renderAll();
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
          width: size.width,
          height: size.height,
          moved: false
        };
        document.onmousemove = move => {
          const dx = (move.clientX - start.mx) / state.scale;
          const dy = (move.clientY - start.my) / state.scale;
          if (!start.moved && (Math.abs(dx) > 0 || Math.abs(dy) > 0)) {
            pushHistory(false);
            start.moved = true;
          }
          if (direction.includes('e')) container.width = Math.max(min.width, Math.round(start.width + dx));
          if (direction.includes('s')) container.height = Math.max(min.height, Math.round(start.height + dy));
          if (isParticipantContainer(container)) syncParticipantAssignments();
          renderCanvas();
        };
        document.onmouseup = () => {
          if (isParticipantContainer(container)) syncParticipantAssignments();
          document.onmousemove = null;
          document.onmouseup = null;
          renderAll();
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
          if (candidate.parentSubProcessId === container.id) return true;
          const center = nodeCenter(candidate);
          return center.x >= bounds.left && center.x <= bounds.right && center.y >= bounds.top && center.y <= bounds.bottom;
        });
      }

      function participantChildNodes(participant) {
        return state.model.nodes.filter(candidate => isParticipantAssignable(candidate) && candidate.participantId === participant.id);
      }

      function participantNodeCount(participant) {
        return participantChildNodes(participant).length;
      }

      function isParticipantAssignable(node) {
        return Boolean(node) && node.kind !== 'participant';
      }

      function participantModeEnabled() {
        return state.model.nodes.some(isParticipantContainer);
      }

      function canPlaceNodeInParticipantMode(node, options = {}) {
        if (!participantModeEnabled() || isParticipantContainer(node) || !isParticipantAssignable(node)) return true;
        const participant = participantAtNodeCenter(node);
        if (participant) return true;
        if (!options.silent) message('已启用 Participant：所有节点都必须放在某个 Participant 泳道内');
        return false;
      }

      function participantContainmentViolations() {
        if (!participantModeEnabled()) return [];
        return state.model.nodes.filter(node => isParticipantAssignable(node) && !participantAtNodeCenter(node));
      }

      function assertParticipantContainment() {
        const violations = participantContainmentViolations();
        if (violations.length === 0) return;
        const names = violations.slice(0, 5).map(n => n.name || n.id).join(', ');
        throw new Error(`已启用 Participant，所有节点都必须在 Participant 内。当前未归属节点：${names}${violations.length > 5 ? ' ...' : ''}`);
      }

      function syncParticipantAssignments() {
        state.model.nodes.filter(isParticipantAssignable).forEach(syncNodeParticipant);
      }

      function syncNodeParticipant(node) {
        if (!isParticipantAssignable(node)) return;
        const participant = participantAtNodeCenter(node);
        if (participant) node.participantId = participant.id;
        else delete node.participantId;
      }

      function participantAtNodeCenter(node) {
        const center = nodeCenter(node);
        return state.model.nodes
          .filter(isParticipantContainer)
          .filter(participant => pointInBounds(center, participantContentBounds(participant)))
          .sort((a, b) => nodeArea(a) - nodeArea(b))[0];
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
        state.model.nodes.forEach((n, i) => {
          n.x = 80 + (i % 8) * 220;
          n.y = 180 + Math.floor(i / 8) * 180;
        });
        syncParticipantAssignments();
        renderAll();
      }

      function fitView() {
        state.scale = 0.78;
        renderCanvas();
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
