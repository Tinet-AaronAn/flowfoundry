      async function post(url, body, headers = {}) {
        const res = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...headers },
          body: JSON.stringify(body)
        });
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) throw new Error(data?.message || text || res.statusText);
        return data;
      }

      async function postFlowRun(body) {
        return post('/api/flows/run', { ...body, runSource: 'web-modeler' }, {
          'X-FlowFoundry-Client': 'web-modeler'
        });
      }

      function showJson(kind) {
        try {
          if (kind === 'xml') {
            showJsonValue('BPMN JSON / XML Source Model', buildBpmnJson());
          } else {
            showJsonValue('Flow DSL', buildDsl());
          }
        } catch (err) {
          message(t('message.generateJsonFailed', { error: err.message }), 'error');
        }
      }

      function showJsonValue(title, value) {
        $('jsonTitle').textContent = title;
        $('jsonContent').textContent = pretty(value);
        $('jsonPanel').classList.add('open');
      }

      function closeJson() { $('jsonPanel').classList.remove('open'); }
      function copyJsonPanel() { navigator.clipboard?.writeText($('jsonContent').textContent); }
      function exportJson() {
        try {
          showJsonValue('Export Model', { model: state.model, dsl: buildDsl(), bpmn: buildBpmnJson() });
        } catch (err) {
          message(t('message.exportFailed', { error: err.message }), 'error');
        }
      }
      async function importJson() {
        const raw = await showAppDialog({
          title: t('message.importPrompt'),
          input: 'textarea'
        });
        if (!raw) return;
        try {
          const parsed = JSON.parse(raw);
          const nextModel = parsed.model || parsed;
          validateStartEventUniqueness(nextModel);
          pushHistory();
          state.model = nextModel;
          syncParticipantAssignments();
          renderAll();
        } catch (err) {
          message(t('message.importFailed', { error: err.message }), 'error');
        }
      }

      let browserMemoryNoticeShown = false;

      function dismissNotice() {
        const notice = $('appNotice');
        if (notice) notice.classList.add('hidden');
      }

      function showNotice({ level = 'info', title = '', text = '' } = {}) {
        const notice = $('appNotice');
        const titleEl = $('appNoticeTitle');
        const textEl = $('appNoticeText');
        if (!notice || !textEl) return;
        notice.className = `app-notice app-notice-${level}`;
        notice.classList.remove('hidden');
        if (titleEl) {
          titleEl.textContent = title || '';
          titleEl.hidden = !title;
        }
        textEl.textContent = text || '';
      }

      function message(text, level = 'info', title = '') {
        if (!text) return;
        showNotice({ level, title, text });
      }

      function ensureBrowserMemoryNotice() {
        if (browserMemoryNoticeShown) return;
        const notice = $('appNotice');
        if (!notice || !notice.classList.contains('hidden')) return;
        browserMemoryNoticeShown = true;
        message(t('modeler.browserMemoryDetail'), 'info', t('modeler.browserMemory'));
      }
      function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c])); }
      function escapeAttr(value) { return escapeHtml(value); }

      document.addEventListener('keydown', event => {
        const tag = event.target?.tagName;
        const isEditing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(tag) || event.target?.isContentEditable;
        if (isEditing) return;
        if (event.key === 'Delete' || event.key === 'Backspace') {
          event.preventDefault();
          deleteSelected();
        }
      });

      configureDefaultMappings();
      loadActivities();
