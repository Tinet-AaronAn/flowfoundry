      async function initTenantContext() {
        try {
          await loadPlatformPublicConfig();
        } catch (ignored) {
          // public-config is optional when security is disabled
        }
        let allowed = [];
        try {
          const response = await platformFetch('/workflows/context');
          if (response.ok) {
            const context = await response.json();
            allowed = [...(context.allowedTenantIds || [])].sort();
            if (context.tenantId) {
              setPlatformTenantId(context.tenantId);
            }
          }
        } catch (ignored) {
          // fall back to bootstrap defaults
        }
        if (!platformTenantId() && global.FLOWFOUNDRY_PUBLIC_CONFIG?.defaultTenantId) {
          setPlatformTenantId(global.FLOWFOUNDRY_PUBLIC_CONFIG.defaultTenantId);
        }
        state.allowedTenantIds = allowed.length ? allowed : [platformTenantId()].filter(Boolean);
        renderTenantSelector();
      }

      function renderTenantSelector() {
        const select = $('tenantSelect');
        const wrap = $('tenantSwitch');
        if (!select || !wrap) return;
        const allowed = state.allowedTenantIds?.length
          ? state.allowedTenantIds
          : [platformTenantId()].filter(Boolean);
        if (allowed.length === 0) {
          wrap.hidden = true;
          return;
        }
        const current = platformTenantId() || allowed[0];
        if (!platformTenantId() && current) setPlatformTenantId(current);
        select.innerHTML = allowed
          .map(id => `<option value="${escapeAttr(id)}" ${id === current ? 'selected' : ''}>${escapeHtml(id)}</option>`)
          .join('');
        wrap.hidden = allowed.length <= 1;
      }

      async function setActiveTenantId(tenantId) {
        if (!tenantId) return;
        setPlatformTenantId(tenantId);
        await refreshWorkflowStoreForTenant();
        renderTenantSelector();
        if (typeof renderWorkflowList === 'function') renderWorkflowList();
        message(t('message.tenantChanged', { tenantId }), 'info');
      }

      async function refreshWorkflowStoreForTenant() {
        state.workflows = [];
        state.activeWorkflowId = '';
        if (await detectWorkflowApi()) {
          try {
            state.workflows = await workflowApi('');
          } catch (err) {
            message(err.message, 'error');
            state.workflows = [];
          }
        }
        renderWorkflowList?.();
      }
