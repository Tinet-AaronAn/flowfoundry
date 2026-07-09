
      let adminNamespaces = [];

      function filteredAdminNamespaces() {
        const keyword = $('namespaceAdminSearch')?.value.trim().toLowerCase() || '';
        if (!keyword) return adminNamespaces;
        return adminNamespaces.filter(item => {
          const haystack = `${item.id} ${item.displayName || ''} ${item.description || ''}`.toLowerCase();
          return haystack.includes(keyword);
        });
      }

      function renderAdminNamespacesTable() {
        const tbody = $('adminNamespacesTable');
        if (!tbody) return;
        const namespaces = filteredAdminNamespaces();
        if (namespaces.length === 0) {
          const emptyText = adminNamespaces.length === 0 ? t('nsAdmin.empty') : t('nsAdmin.emptySearch');
          tbody.innerHTML = `<tr><td colspan="6" class="admin-empty">${escapeHtml(emptyText)}</td></tr>`;
          return;
        }
        tbody.innerHTML = namespaces.map(item => `
          <tr>
            <td><code>${escapeHtml(item.id || '')}</code></td>
            <td>${escapeHtml(item.displayName || '')}</td>
            <td>${escapeHtml(item.description || '-')}</td>
            <td>${escapeHtml(formatInstant(item.createdAt))}</td>
            <td>${escapeHtml(formatInstant(item.updatedAt))}</td>
            <td class="admin-actions">
              <button class="secondary" onclick="editAdminNamespace('${escapeAttr(item.id)}')">${escapeHtml(t('admin.edit'))}</button>
              <button class="secondary danger" onclick="deleteAdminNamespace('${escapeAttr(item.id)}')">${escapeHtml(t('admin.delete'))}</button>
            </td>
          </tr>
        `).join('');
      }

      async function refreshAdminNamespaces() {
        adminNamespaces = await adminApi('/namespaces');
        if (!Array.isArray(adminNamespaces)) {
          adminNamespaces = [];
        }
        renderAdminNamespacesTable();
      }

      async function renderNamespacesView() {
        await loadAdminProfile();
        const allowed = isLocalAdminHost() || adminProfile?.admin;
        if (typeof applyI18n === 'function') {
          applyI18n($('namespacesView') || document);
        }
        if (!allowed) {
          $('namespacesAccessDenied')?.classList.remove('hidden');
          $('namespacesPanels')?.classList.add('hidden');
          return;
        }
        $('namespacesAccessDenied')?.classList.add('hidden');
        $('namespacesPanels')?.classList.remove('hidden');
        try {
          await refreshAdminNamespaces();
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function adminNamespaceFormFields(namespace = {}) {
        const editing = !!namespace.id;
        const fields = [
          {
            name: 'displayName',
            label: t('nsAdmin.prompt.displayName'),
            type: 'text',
            value: namespace.displayName || ''
          },
          {
            name: 'description',
            label: t('admin.prompt.description'),
            type: 'text',
            value: namespace.description || ''
          }
        ];
        if (!editing) {
          fields.unshift({
            name: 'id',
            label: t('nsAdmin.prompt.id'),
            hint: t('nsAdmin.prompt.idHint'),
            type: 'text',
            value: namespace.id || 'my-namespace'
          });
        }
        return fields;
      }

      function parseAdminNamespaceForm(result, editing) {
        if (!result) return null;
        const displayName = (result.displayName || '').trim();
        if (!displayName) {
          message(t('nsAdmin.error.displayNameRequired'), 'error');
          return null;
        }
        const parsed = {
          displayName,
          description: (result.description || '').trim()
        };
        if (!editing) {
          const id = (result.id || '').trim();
          if (!id) {
            message(t('nsAdmin.error.idRequired'), 'error');
            return null;
          }
          parsed.id = id;
        }
        return parsed;
      }

      async function createAdminNamespace() {
        const result = await showAppFormDialog({
          title: t('nsAdmin.createTitle'),
          confirmLabel: t('admin.save'),
          fields: adminNamespaceFormFields()
        });
        const parsed = parseAdminNamespaceForm(result, false);
        if (!parsed) return;
        try {
          await adminApi('/namespaces', {
            method: 'POST',
            body: JSON.stringify(parsed)
          });
          await refreshAdminNamespaces();
          await initNamespaceContext();
          message(t('nsAdmin.created', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function editAdminNamespace(namespaceId) {
        const namespace = adminNamespaces.find(item => item.id === namespaceId);
        if (!namespace) return;
        const result = await showAppFormDialog({
          title: t('nsAdmin.editTitle'),
          confirmLabel: t('admin.save'),
          fields: adminNamespaceFormFields(namespace)
        });
        const parsed = parseAdminNamespaceForm(result, true);
        if (!parsed) return;
        try {
          await adminApi(`/namespaces/${encodeURIComponent(namespaceId)}`, {
            method: 'PUT',
            body: JSON.stringify(parsed)
          });
          await refreshAdminNamespaces();
          message(t('nsAdmin.updated', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function deleteAdminNamespace(namespaceId) {
        const namespace = adminNamespaces.find(item => item.id === namespaceId);
        if (!namespace) return;
        const confirmed = await showAppDialog({
          title: t('admin.delete'),
          message: t('nsAdmin.confirm.delete', { id: namespace.id, name: namespace.displayName || namespace.id }),
          input: 'none',
          confirmLabel: t('admin.delete'),
          cancelLabel: t('dialog.cancel')
        });
        if (!confirmed) return;
        try {
          await adminApi(`/namespaces/${encodeURIComponent(namespaceId)}`, { method: 'DELETE' });
          await refreshAdminNamespaces();
          await initNamespaceContext();
          message(t('nsAdmin.deleted', { name: namespace.displayName || namespace.id }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }
