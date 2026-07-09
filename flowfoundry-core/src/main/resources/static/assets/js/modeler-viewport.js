      const ZOOM_STEP = 1.15;

      function clampScale(scale) {
        return Math.min(state.maxScale, Math.max(state.minScale, scale));
      }

      function applyViewportTransform() {
        const content = $('canvasContent');
        const canvas = $('canvas');
        if (content) {
          content.style.transform = `translate(${state.panX}px, ${state.panY}px) scale(${state.scale})`;
        }
        if (canvas) {
          const grid = 18 * state.scale;
          canvas.style.backgroundSize = `${grid}px ${grid}px`;
          canvas.style.backgroundPosition = `${state.panX}px ${state.panY}px`;
        }
        updateZoomLabel();
        updateMinimapViewport();
      }

      function modelContentBounds() {
        const nodes = state.model.nodes;
        if (!nodes.length) {
          return { left: 0, top: 0, right: 1200, bottom: 800, width: 1200, height: 800 };
        }
        let left = Infinity;
        let top = Infinity;
        let right = -Infinity;
        let bottom = -Infinity;
        nodes.forEach(n => {
          const bounds = nodeBounds(n);
          left = Math.min(left, bounds.left);
          top = Math.min(top, bounds.top);
          right = Math.max(right, bounds.right);
          bottom = Math.max(bottom, bounds.bottom);
        });
        const padding = 72;
        left -= padding;
        top -= padding;
        right += padding;
        bottom += padding;
        return { left, top, right, bottom, width: right - left, height: bottom - top };
      }

      function setScale(nextScale, anchor = null) {
        if (state.viewportLocked) return;
        const canvas = $('canvas');
        const prevScale = state.scale;
        const scale = clampScale(nextScale);
        if (scale === prevScale) return;

        if (anchor && canvas) {
          const rect = canvas.getBoundingClientRect();
          const mx = anchor.clientX - rect.left;
          const my = anchor.clientY - rect.top;
          const modelX = (mx - state.panX) / prevScale;
          const modelY = (my - state.panY) / prevScale;
          state.scale = scale;
          state.panX = mx - modelX * scale;
          state.panY = my - modelY * scale;
        } else if (canvas) {
          const cx = canvas.clientWidth / 2;
          const cy = canvas.clientHeight / 2;
          const modelX = (cx - state.panX) / prevScale;
          const modelY = (cy - state.panY) / prevScale;
          state.scale = scale;
          state.panX = cx - modelX * scale;
          state.panY = cy - modelY * scale;
        } else {
          state.scale = scale;
        }
        applyViewportTransform();
      }

      function zoomIn() {
        setScale(state.scale * ZOOM_STEP);
      }

      function zoomOut() {
        setScale(state.scale / ZOOM_STEP);
      }

      function fitView() {
        if (state.viewportLocked) return;
        const canvas = $('canvas');
        if (!canvas || !canvas.clientWidth || !canvas.clientHeight) return;
        const bounds = modelContentBounds();
        const viewW = canvas.clientWidth;
        const viewH = canvas.clientHeight;
        const scaleX = viewW / bounds.width;
        const scaleY = viewH / bounds.height;
        const next = clampScale(Math.min(scaleX, scaleY) * 0.92);
        state.scale = next;
        state.panX = (viewW - bounds.width * next) / 2 - bounds.left * next;
        state.panY = (viewH - bounds.height * next) / 2 - bounds.top * next;
        state.lastFitCanvasSize = { w: viewW, h: viewH };
        applyViewportTransform();
        renderMinimap();
      }

      function canvasReadyForFit(canvas) {
        // Embed/iframe layouts often paint a tiny canvas first; wait for a usable size.
        return canvas && canvas.clientWidth >= 160 && canvas.clientHeight >= 160;
      }

      function shouldRefitAfterResize(canvas) {
        if (!canvasReadyForFit(canvas)) return false;
        const last = state.lastFitCanvasSize;
        if (!last || last.w < 160 || last.h < 160) return true;
        const grewW = canvas.clientWidth > last.w * 1.35 || canvas.clientWidth - last.w > 120;
        const grewH = canvas.clientHeight > last.h * 1.35 || canvas.clientHeight - last.h > 120;
        return grewW || grewH;
      }

      function scheduleFitView(attempt = 0) {
        if (state.viewportLocked || state.currentView !== 'modeler') return;
        const modelerView = $('modelerView');
        const canvas = $('canvas');
        if (!modelerView?.classList.contains('active') || !canvas) return;
        if (!canvasReadyForFit(canvas) && attempt < 48) {
          requestAnimationFrame(() => scheduleFitView(attempt + 1));
          return;
        }
        fitView();
      }

      function toggleViewportLock() {
        state.viewportLocked = !state.viewportLocked;
        applyViewportLockState();
        updateViewportUi();
        message(state.viewportLocked ? t('viewport.locked') : t('viewport.unlocked'));
      }

      function applyViewportLockState() {
        $('canvas')?.classList.toggle('viewport-locked', state.viewportLocked);
        $('viewportLockBtn')?.classList.toggle('locked', state.viewportLocked);
        $('zoomInBtn').disabled = state.viewportLocked;
        $('zoomOutBtn').disabled = state.viewportLocked;
        $('fitViewBtn').disabled = state.viewportLocked;
      }

      function updateZoomLabel() {
        const label = $('zoomLevel');
        if (label) label.textContent = `${Math.round(state.scale * 100)}%`;
      }

      function updateViewportUi() {
        updateZoomLabel();
        updateMinimapViewport();
      }

      function renderMinimap() {
        const minimap = $('minimap');
        const canvas = $('minimapCanvas');
        if (!minimap || !canvas) return;
        const ctx = canvas.getContext('2d');
        const mmW = minimap.clientWidth;
        const mmH = minimap.clientHeight;
        if (!mmW || !mmH) return;

        const ratio = window.devicePixelRatio || 1;
        canvas.width = Math.round(mmW * ratio);
        canvas.height = Math.round(mmH * ratio);
        canvas.style.width = `${mmW}px`;
        canvas.style.height = `${mmH}px`;
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.clearRect(0, 0, mmW, mmH);

        const bounds = modelContentBounds();
        const pad = 8;
        const fitScale = Math.min((mmW - pad * 2) / bounds.width, (mmH - pad * 2) / bounds.height);
        const offsetX = pad + (mmW - pad * 2 - bounds.width * fitScale) / 2 - bounds.left * fitScale;
        const offsetY = pad + (mmH - pad * 2 - bounds.height * fitScale) / 2 - bounds.top * fitScale;
        state.minimap = { scale: fitScale, offsetX, offsetY, bounds, mmW, mmH };

        state.model.nodes.forEach(n => {
          const b = nodeBounds(n);
          const w = Math.max(3, (b.right - b.left) * fitScale);
          const h = Math.max(3, (b.bottom - b.top) * fitScale);
          const x = b.left * fitScale + offsetX;
          const y = b.top * fitScale + offsetY;
          if (n.kind === 'participant' || n.kind === 'subProcess') {
            ctx.fillStyle = 'rgba(148, 163, 184, .42)';
            ctx.strokeStyle = 'rgba(203, 213, 225, .35)';
            ctx.lineWidth = 1;
            ctx.fillRect(x, y, w, h);
            ctx.strokeRect(x, y, w, h);
            return;
          }
          if (n.kind.includes('Event')) {
            ctx.fillStyle = 'rgba(226, 232, 240, .72)';
            ctx.beginPath();
            ctx.arc(x + w / 2, y + h / 2, Math.max(2.5, Math.min(w, h) / 2), 0, Math.PI * 2);
            ctx.fill();
            return;
          }
          ctx.fillStyle = 'rgba(156, 163, 175, .78)';
          ctx.fillRect(x, y, w, h);
        });

        updateMinimapViewport();
      }

      function updateMinimapViewport() {
        const mainCanvas = $('canvas');
        const viewport = $('minimapViewport');
        const layout = state.minimap;
        if (!mainCanvas || !viewport || !layout) return;

        const viewX = -state.panX / state.scale;
        const viewY = -state.panY / state.scale;
        const viewW = mainCanvas.clientWidth / state.scale;
        const viewH = mainCanvas.clientHeight / state.scale;

        viewport.style.left = `${viewX * layout.scale + layout.offsetX}px`;
        viewport.style.top = `${viewY * layout.scale + layout.offsetY}px`;
        viewport.style.width = `${Math.max(12, viewW * layout.scale)}px`;
        viewport.style.height = `${Math.max(12, viewH * layout.scale)}px`;
      }

      function panFromMinimapEvent(event) {
        const layout = state.minimap;
        const canvas = $('canvas');
        if (!layout || !canvas) return;
        const rect = $('minimap').getBoundingClientRect();
        const mx = event.clientX - rect.left;
        const my = event.clientY - rect.top;
        const modelX = (mx - layout.offsetX) / layout.scale;
        const modelY = (my - layout.offsetY) / layout.scale;
        state.panX = canvas.clientWidth / 2 - modelX * state.scale;
        state.panY = canvas.clientHeight / 2 - modelY * state.scale;
        applyViewportTransform();
      }

      function isCanvasPanSurface(event) {
        if (state.viewportLocked || !event) return false;
        const canvas = $('canvas');
        if (!canvas) return false;
        const rect = canvas.getBoundingClientRect();
        const { clientX, clientY } = event;
        if (clientX < rect.left || clientX > rect.right || clientY < rect.top || clientY > rect.bottom) {
          return false;
        }
        const hit = document.elementFromPoint(clientX, clientY);
        if (!hit) return false;
        if (hit.closest('.canvas-chrome, .toolbar')) return false;
        if (hit.closest('.connection-handle, .edge-endpoint-handle, .node-toolbar, .container-resize-handle, .task-morph-menu, .annotation-editor, .participant-node, .subprocess-node, .node, .edge-hit')) {
          return false;
        }
        return hit === canvas || canvas.contains(hit);
      }

      function initCanvasPan() {
        const canvas = $('canvas');
        if (!canvas) return;
        let pan = null;

        canvas.addEventListener('mousedown', event => {
          if (event.button !== 0 || state.viewportLocked) return;
          if (!isCanvasPanSurface(event)) return;
          event.preventDefault();
          event.stopPropagation();
          pan = {
            x: event.clientX,
            y: event.clientY,
            panX: state.panX,
            panY: state.panY,
            active: false
          };
        });

        document.addEventListener('mousemove', event => {
          if (!pan) return;
          const dx = event.clientX - pan.x;
          const dy = event.clientY - pan.y;
          if (!pan.active) {
            if (Math.abs(dx) < 3 && Math.abs(dy) < 3) return;
            pan.active = true;
            canvas.classList.add('is-panning');
            state.suppressCanvasClick = true;
          }
          event.preventDefault();
          state.panX = pan.panX + dx;
          state.panY = pan.panY + dy;
          applyViewportTransform();
        });

        document.addEventListener('mouseup', () => {
          if (!pan) return;
          if (pan.active) {
            setTimeout(() => {
              state.suppressCanvasClick = false;
            }, 0);
          }
          pan = null;
          canvas.classList.remove('is-panning');
        });
      }

      function initViewport() {
        const canvas = $('canvas');
        const minimap = $('minimap');
        if (!canvas || !minimap) return;

        canvas.addEventListener('wheel', event => {
          if (state.viewportLocked) return;
          event.preventDefault();
          const absX = Math.abs(event.deltaX);
          const absY = Math.abs(event.deltaY);
          if (absX >= absY && absX > 0) {
            state.panX -= event.deltaX;
            state.panY -= event.deltaY;
            applyViewportTransform();
            return;
          }
          if (absY === 0) return;
          const factor = event.deltaY > 0 ? 0.92 : 1.08;
          setScale(state.scale * factor, { clientX: event.clientX, clientY: event.clientY });
        }, { passive: false });

        initCanvasPan();

        minimap.addEventListener('mousedown', event => {
          if (state.viewportLocked || event.button !== 0) return;
          event.preventDefault();
          panFromMinimapEvent(event);
          const onMove = move => panFromMinimapEvent(move);
          const onUp = () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
          };
          document.addEventListener('mousemove', onMove);
          document.addEventListener('mouseup', onUp);
        });

        const onViewportHostResize = () => {
          if (shouldRefitAfterResize(canvas)) {
            scheduleFitView();
            return;
          }
          renderMinimap();
          updateMinimapViewport();
        };

        if (typeof ResizeObserver !== 'undefined') {
          const observer = new ResizeObserver(onViewportHostResize);
          observer.observe(canvas);
          observer.observe(minimap);
        } else {
          window.addEventListener('resize', onViewportHostResize);
        }

        applyViewportLockState();
        applyViewportTransform();
        updateViewportUi();
      }

      initViewport();
