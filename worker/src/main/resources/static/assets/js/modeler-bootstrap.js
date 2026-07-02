      async function post(url, body) {
        const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) throw new Error(data?.message || text || res.statusText);
        return data;
      }

      function showJson(kind) {
        try {
          if (kind === 'xml') {
            showJsonValue('BPMN JSON / XML Source Model', buildBpmnJson());
          } else {
            showJsonValue('Flow DSL', buildDsl());
          }
        } catch (err) {
          message(t('message.generateJsonFailed', { error: err.message }));
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
          message(t('message.exportFailed', { error: err.message }));
        }
      }
      function importJson() {
        const raw = prompt(t('message.importPrompt'));
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
          message(t('message.importFailed', { error: err.message }));
        }
      }

      function message(text) { $('message').textContent = text; }
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
      $('message').textContent = t('runtime.waiting');
      loadActivities();
