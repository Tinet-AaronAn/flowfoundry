      async function initNamespaceContext() {
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
            allowed = [...(context.allowedNamespaces || [])].sort();
            if (context.namespace) {
              setPlatformNamespace(context.namespace);
            }
          }
        } catch (ignored) {
          // fall back to bootstrap defaults
        }
        const defaultNamespace = window.FLOWFOUNDRY_PUBLIC_CONFIG?.defaultNamespace;
        if (!platformNamespace() && defaultNamespace) {
          setPlatformNamespace(defaultNamespace);
        }
        state.allowedNamespaces = allowed.length ? allowed : [platformNamespace()].filter(Boolean);
        renderNamespaceSelector();
      }

      function isGlobalAdminView() {
        return state.currentView === 'admin' || state.currentView === 'namespaces';
      }

      function setNamespaceSelectorVisible(visible) {
        const wrap = $('namespaceSwitch');
        if (!wrap) return;
        wrap.classList.toggle('hidden', !visible);
        if (visible) wrap.removeAttribute('hidden');
        else wrap.setAttribute('hidden', '');
      }

      function renderNamespaceSelector() {
        const select = $('namespaceSelect');
        const wrap = $('namespaceSwitch');
        if (!select || !wrap) return;
        if (isGlobalAdminView()) {
          setNamespaceSelectorVisible(false);
          return;
        }
        const allowed = state.allowedNamespaces?.length
          ? state.allowedNamespaces
          : [platformNamespace()].filter(Boolean);
        if (allowed.length === 0) {
          setNamespaceSelectorVisible(false);
          return;
        }
        const current = platformNamespace() || allowed[0];
        if (!platformNamespace() && current) setPlatformNamespace(current);
        select.innerHTML = allowed
          .map(id => `<option value="${escapeAttr(id)}" ${id === current ? 'selected' : ''}>${escapeHtml(id)}</option>`)
          .join('');
        setNamespaceSelectorVisible(allowed.length > 1);
      }

      async function setActiveNamespace(namespace) {
        if (!namespace) return;
        setPlatformNamespace(namespace);
        await refreshWorkflowStoreForNamespace();
        renderNamespaceSelector();
        if (typeof clearActiveRunIfNamespaceMismatch === 'function') {
          clearActiveRunIfNamespaceMismatch(namespace);
        }
        if (typeof renderWorkflowList === 'function') renderWorkflowList();
        if (typeof resetRunsSearchView === 'function') resetRunsSearchView();
        message(t('message.namespaceChanged', { namespace }), 'info');
      }

      async function refreshWorkflowStoreForNamespace() {
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
