      function parseEmbedParams() {
        const params = new URLSearchParams(globalThis.location.search);
        return {
          workflowId: params.get('workflowId') || '',
          version: params.get('version') || '',
          mode: params.get('mode') || 'design',
          readonly: params.get('readonly') === 'true'
        };
      }

      function notifyEmbedParent(type, payload) {
        if (!state.embedMode || !globalThis.parent || globalThis.parent === globalThis) return;
        let targetOrigin = '*';
        try {
          if (document.referrer) {
            targetOrigin = new URL(document.referrer).origin;
          }
        } catch (ignored) {}
        globalThis.parent.postMessage(
          { type: `flowfoundry:${type}`, ...payload },
          targetOrigin
        );
      }

      function applyEmbedChrome() {
        document.body.classList.add('embed-mode');
        const shell = document.querySelector('.shell');
        if (shell) shell.classList.add('embed-shell');

        const topbar = document.querySelector('.topbar');
        if (topbar) topbar.classList.add('hidden');

        const navPanel = $('navPanel');
        const navExpandTab = $('navExpandTab');
        if (navPanel) navPanel.classList.add('hidden');
        if (navExpandTab) navExpandTab.classList.add('hidden');

        const header = document.querySelector('.model-header');
        if (header && state.embedConfig.readonly) {
          header.querySelectorAll('button').forEach(btn => {
            btn.disabled = true;
            btn.classList.add('hidden');
          });
        }
        if (state.embedConfig.mode === 'design') {
          header?.querySelectorAll('button').forEach(btn => {
            const label = (btn.textContent || '').trim().toLowerCase();
            if (label.includes('run') || label.includes('status') || label.includes('human')) {
              btn.classList.add('hidden');
            }
          });
        }

        state.currentView = 'modeler';
        state.navCollapsed = true;
        applyViewLayout();
        applyNavCollapsed();
      }

      async function loadEmbedWorkflowStore() {
        try {
          state.paletteCollapsed = false;
          const propertiesStored = localStorage.getItem('flowfoundry-properties-collapsed');
          state.propertiesCollapsed = propertiesStored === null ? true : propertiesStored === '1';
        } catch (ignored) {
          state.paletteCollapsed = false;
          state.propertiesCollapsed = true;
        }

        const apiAvailable = await detectWorkflowApi();
        const { workflowId, version } = state.embedConfig;

        if (workflowId && apiAvailable) {
          await openWorkflow(workflowId, version || null, false);
          return;
        }

        if (apiAvailable) {
          await loadWorkflowStore();
          if (state.workflows.length === 0) {
            try {
              const created = await workflowApi('', {
                method: 'POST',
                body: JSON.stringify({ name: state.model.name, model: state.model })
              });
              state.workflows = [created];
              state.activeWorkflowId = created.id;
              state.activeVersion = created.version;
              await openWorkflow(created.id, created.version, false);
              return;
            } catch (err) {
              message(err.message, 'error');
            }
          } else {
            const active = state.workflows.find(w => w.status === 'active') || state.workflows[0];
            if (active) {
              await openWorkflow(active.id, active.version, false);
              return;
            }
          }
        }

        state.activeWorkflowId = state.model.id;
        state.activeVersion = state.activeVersion || '1.0.0';
        state.workflows = [workflowRecordFromModel(state.model, 'draft', state.activeVersion)];
        syncModelHeader();
        if (!apiAvailable) {
          message(t('workflow.message.apiFallback', { error: 'Workflow API unavailable' }), 'warning');
        }
      }

      async function initEmbedModeler() {
        state.embedMode = true;
        state.embedConfig = parseEmbedParams();

        try {
          await loadPlatformPublicConfig();
        } catch (ignored) {
          /* public-config is optional when defaults suffice */
        }

        if (state.embedConfig.locale && typeof setLocale === 'function') {
          setLocale(state.embedConfig.locale);
        }

        try {
          const res = await fetch(platformApiUrl('/activities'), { headers: platformApiHeaders() });
          const data = await res.json();
          state.activities = data.activities || [];
          state.activityGroups = data.groups || [];
        } catch (err) {
          message(t('message.loadActivitiesFailed', { error: err.message }), 'error');
        }

        await loadScriptCatalog();
        await loadEmbedWorkflowStore();
        applyEmbedChrome();
        renderAll();
        scheduleFitView();
        notifyEmbedParent('ready', {
          workflowId: state.activeWorkflowId,
          version: state.activeVersion
        });
      }

      function notifyWorkflowSaved(record) {
        notifyEmbedParent('saved', {
          workflowId: record?.id || state.activeWorkflowId,
          version: record?.version || state.activeVersion,
          name: record?.name || state.model?.name
        });
      }
