
      let adminProfile = null;
      let adminApiKeys = [];
      let adminAuditLogs = [];
      let adminAuditState = { page: 0, size: 10, totalPages: 0, totalElements: 0, loaded: false };
      let adminAllNamespaces = false;

      async function adminApi(path = '', options = {}) {
        const response = await fetch(platformApiUrl(`/admin${path}`), {
          ...options,
          headers: {
            'Content-Type': 'application/json',
            ...platformApiHeaders(),
            ...(options?.headers || {})
          }
        });
        if (!response.ok) {
          const text = await response.text();
          let message = text;
          try {
            message = JSON.parse(text).message || text;
          } catch (ignored) {}
          throw new Error(message || response.statusText);
        }
        if (response.status === 204) return null;
        const text = await response.text();
        return text ? JSON.parse(text) : null;
      }

      function adminNamespacesLabel(apiKey) {
        if (!apiKey) return '-';
        if (apiKey.admin) return t('admin.namespaceAdmin');
        const namespaces = adminApiKeyNamespaceList(apiKey);
        return namespaces.length ? namespaces.join(', ') : '-';
      }

      function adminApiKeyNamespaceList(apiKey) {
        const namespaces = apiKey?.namespaces;
        if (Array.isArray(namespaces)) {
          return namespaces.map(item => String(item).trim()).filter(Boolean);
        }
        if (namespaces && typeof namespaces === 'object') {
          return Object.values(namespaces).map(item => String(item).trim()).filter(Boolean);
        }
        return [];
      }

      function isLocalAdminHost() {
        return location.hostname === '127.0.0.1'
          || location.hostname === 'localhost'
          || location.hostname === '[::1]';
      }

      async function loadAdminProfile() {
        const group = $('navSystemGroup');
        if (!isLocalAdminHost()) {
          group?.classList.add('hidden');
          adminProfile = { admin: false, securityEnabled: true };
          return;
        }
        group?.classList.remove('hidden');
        try {
          adminProfile = await adminApi('/me');
        } catch (err) {
          adminProfile = {
            admin: true,
            apiKeyId: 'localhost-admin',
            securityEnabled: true,
            error: err.message
          };
        }
      }

      async function renderAdminView() {
        await loadAdminProfile();
        const allowed = isLocalAdminHost() || adminProfile?.admin;
        if (typeof applyI18n === 'function') {
          applyI18n($('adminView') || document);
        }
        if (!allowed) {
          $('adminAccessDenied')?.classList.remove('hidden');
          $('adminPanels')?.classList.add('hidden');
          return;
        }
        $('adminAccessDenied')?.classList.add('hidden');
        $('adminPanels')?.classList.remove('hidden');
        $('adminProfileText').textContent = t('admin.profile', {
          actor: adminProfile.apiKeyId || 'localhost-admin',
          security: adminProfile.securityEnabled ? t('admin.securityOn') : t('admin.securityOff')
        });
        renderAdminApiKeysTable();
        try {
          await refreshAdminApiKeys();
          renderAdminAuditPlaceholder();
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function filteredAdminApiKeys() {
        const keyword = $('adminApiKeySearch')?.value.trim().toLowerCase() || '';
        if (!keyword) return adminApiKeys;
        return adminApiKeys.filter(apiKey => {
          const haystack = `${apiKey.id} ${apiKey.displayName} ${adminNamespacesLabel(apiKey)}`.toLowerCase();
          return haystack.includes(keyword);
        });
      }

      function renderAdminApiKeysTable() {
        const tbody = $('adminApiKeysTable');
        if (!tbody) return;
        const apiKeys = filteredAdminApiKeys();
        if (apiKeys.length === 0) {
          const emptyText = adminApiKeys.length === 0 ? t('admin.empty') : t('admin.emptySearch');
          tbody.innerHTML = `<tr><td colspan="6" class="admin-empty">${escapeHtml(emptyText)}</td></tr>`;
          return;
        }
        tbody.innerHTML = apiKeys.map(apiKey => `
          <tr>
            <td>${escapeHtml(apiKey.displayName || apiKey.id || '')}</td>
            <td>${escapeHtml(adminNamespacesLabel(apiKey))}</td>
            <td><code>${escapeHtml(apiKey.keyPrefix || '')}</code></td>
            <td><span class="pill ${apiKey.status === 'active' ? 'completed' : 'failed'}">${escapeHtml(apiKey.status || '')}</span></td>
            <td>${escapeHtml(formatInstant(apiKey.lastUsedAt))}</td>
            <td class="admin-actions">
              <button class="secondary" onclick="viewAdminApiKey('${escapeAttr(apiKey.id)}')">${escapeHtml(t('admin.view'))}</button>
              <button class="secondary" onclick="editAdminApiKey('${escapeAttr(apiKey.id)}')">${escapeHtml(t('admin.edit'))}</button>
              ${apiKey.status === 'active'
                ? `<button class="secondary danger" onclick="disableAdminApiKey('${escapeAttr(apiKey.id)}')">${escapeHtml(t('admin.disable'))}</button>`
                : `<button class="secondary" onclick="enableAdminApiKey('${escapeAttr(apiKey.id)}')">${escapeHtml(t('admin.enable'))}</button>`}
              ${isProtectedAdminApiKey(apiKey.id)
                ? ''
                : `<button class="secondary danger" onclick="deleteAdminApiKey('${escapeAttr(apiKey.id)}')">${escapeHtml(t('admin.delete'))}</button>`}
            </td>
          </tr>
        `).join('');
      }

      async function refreshAdminApiKeys() {
        const query = adminAllNamespaces ? '?allNamespaces=true' : '';
        adminApiKeys = await adminApi(`/api-keys${query}`);
        if (!Array.isArray(adminApiKeys)) {
          adminApiKeys = [];
        }
        renderAdminApiKeysTable();
      }

      function toggleAdminAllNamespaces(checked) {
        adminAllNamespaces = !!checked;
        refreshAdminApiKeys().catch(err => message(err.message, 'error'));
        if (adminAuditState.loaded) {
          searchAdminAuditLogs(0).catch(err => message(err.message, 'error'));
        }
      }

      function readAdminAuditDateTimeInput(id) {
        const value = $(id)?.value;
        if (!value) return '';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? '' : date.toISOString();
      }

      function renderAdminAuditPlaceholder() {
        adminAuditState = { page: 0, size: 10, totalPages: 0, totalElements: 0, loaded: false };
        adminAuditLogs = [];
        const tbody = $('adminAuditTable');
        if (tbody) {
          tbody.innerHTML = `<tr><td colspan="6" class="admin-empty">${escapeHtml(t('admin.auditPrompt'))}</td></tr>`;
        }
        renderAdminAuditPagination();
      }

      function renderAdminAuditPagination() {
        const bar = $('adminAuditPagination');
        if (!bar) return;
        if (!adminAuditState.loaded) {
          bar.innerHTML = '';
          return;
        }
        const page = adminAuditState.page + 1;
        const totalPages = Math.max(adminAuditState.totalPages, 1);
        bar.innerHTML = `
          <button class="secondary" onclick="searchAdminAuditLogs(${adminAuditState.page - 1})" ${adminAuditState.page <= 0 ? 'disabled' : ''}>${escapeHtml(t('admin.auditPrev'))}</button>
          <span>${escapeHtml(t('admin.auditPage', { page, totalPages, total: adminAuditState.totalElements }))}</span>
          <button class="secondary" onclick="searchAdminAuditLogs(${adminAuditState.page + 1})" ${adminAuditState.page + 1 >= adminAuditState.totalPages ? 'disabled' : ''}>${escapeHtml(t('admin.auditNext'))}</button>
        `;
      }

      async function searchAdminAuditLogs(page = 0) {
        const apiKeyId = $('adminAuditApiKeyFilter')?.value.trim() || '';
        const action = $('adminAuditActionFilter')?.value.trim() || '';
        const from = readAdminAuditDateTimeInput('adminAuditFromFilter');
        const to = readAdminAuditDateTimeInput('adminAuditToFilter');
        const includeApiCalls = !!$('adminAuditIncludeApiCalls')?.checked;
        const query = new URLSearchParams();
        if (apiKeyId) query.set('apiKeyId', apiKeyId);
        if (action) query.set('action', action);
        if (from) query.set('from', from);
        if (to) query.set('to', to);
        if (includeApiCalls) query.set('includeApiCalls', 'true');
        if (adminAllNamespaces) query.set('allNamespaces', 'true');
        query.set('page', String(Math.max(page, 0)));
        query.set('size', String(adminAuditState.size || 10));
        try {
          const result = await adminApi(`/audit-logs?${query.toString()}`);
          adminAuditLogs = Array.isArray(result?.items) ? result.items : [];
          adminAuditState = {
            page: result?.page ?? 0,
            size: result?.size ?? 10,
            totalPages: result?.totalPages ?? 0,
            totalElements: result?.totalElements ?? 0,
            loaded: true
          };
        } catch (err) {
          message(err.message, 'error');
          return;
        }
        const tbody = $('adminAuditTable');
        if (!tbody) return;
        if (adminAuditLogs.length === 0) {
          tbody.innerHTML = `<tr><td colspan="6" class="admin-empty">${escapeHtml(t('admin.auditEmpty'))}</td></tr>`;
          renderAdminAuditPagination();
          return;
        }
        tbody.innerHTML = adminAuditLogs.map(log => `
          <tr>
            <td>${escapeHtml(formatInstant(log.occurredAt))}</td>
            <td>${escapeHtml(log.action || '')}</td>
            <td>${escapeHtml(log.apiKeyId || log.actorApiKeyId || '')}</td>
            <td>${escapeHtml(`${log.httpMethod || ''} ${log.path || ''}`.trim())}</td>
            <td>${log.statusCode ?? ''}</td>
            <td>${escapeHtml(log.detail || '')}</td>
          </tr>
        `).join('');
        renderAdminAuditPagination();
      }

      async function refreshAdminAuditLogs() {
        return searchAdminAuditLogs(adminAuditState.page || 0);
      }

      async function viewAdminApiKey(apiKeyId) {
        const apiKey = adminApiKeys.find(item => item.id === apiKeyId) || await adminApi(`/api-keys/${encodeURIComponent(apiKeyId)}`);
        if (!apiKey) return;
        await showAppDialog({
          title: t('admin.viewTitle', { name: apiKey.displayName || apiKey.id }),
          message: [
            `${t('admin.col.name')}: ${apiKey.displayName || '-'}`,
            `${t('admin.col.status')}: ${apiKey.status || '-'}`,
            `${t('admin.col.namespaces')}: ${adminNamespacesLabel(apiKey)}`,
            `${t('admin.col.keyPrefix')}: ${apiKey.keyPrefix || '-'}`,
            `${t('admin.col.lastUsed')}: ${formatInstant(apiKey.lastUsedAt)}`,
            `Created: ${formatInstant(apiKey.createdAt)}`,
            `Updated: ${formatInstant(apiKey.updatedAt)}`,
            apiKey.description ? `Description: ${apiKey.description}` : ''
          ].filter(Boolean).join('\n'),
          input: 'none',
          confirmLabel: t('dialog.confirm')
        });
      }

      function deriveApiKeyId(displayName) {
        const slug = (displayName || '')
          .trim()
          .toLowerCase()
          .replace(/[^a-z0-9]+/g, '-')
          .replace(/^-+|-+$/g, '');
        if (/^[a-z][a-z0-9-]{1,62}$/.test(slug)) {
          return slug;
        }
        return `app-${Date.now().toString(36)}`;
      }

      function adminApiKeyFormFields(apiKey = {}) {
        const isAdmin = !!apiKey.admin;
        return [
          {
            name: 'displayName',
            label: t('admin.prompt.name'),
            type: 'text',
            value: apiKey.displayName || 'my-app'
          },
          {
            name: 'admin',
            label: t('admin.field.accessScope'),
            type: 'radio',
            value: isAdmin ? 'true' : 'false',
            options: [
              {
                value: 'false',
                label: t('admin.choice.namespaceScoped'),
                hint: t('admin.choice.namespaceScopedHint')
              },
              {
                value: 'true',
                label: t('admin.choice.platformAdmin'),
                hint: t('admin.choice.platformAdminHint')
              }
            ]
          },
          {
            name: 'namespaces',
            label: t('admin.prompt.namespaces'),
            hint: t('admin.prompt.namespacesHint'),
            type: 'text',
            value: apiKey.id
              ? adminApiKeyNamespaceList(apiKey).join(', ')
              : 'ai-collection-strategy',
            dependsOn: { field: 'admin', value: 'false' }
          },
          {
            name: 'description',
            label: t('admin.prompt.description'),
            type: 'text',
            value: apiKey.description || ''
          }
        ];
      }

      function parseAdminApiKeyForm(result) {
        if (!result) return null;
        const displayName = (result.displayName || '').trim();
        if (!displayName) {
          message(t('admin.error.nameRequired'), 'error');
          return null;
        }
        const admin = result.admin === 'true';
        let namespaces = [];
        if (!admin) {
          namespaces = (result.namespaces || '').split(',').map(item => item.trim()).filter(Boolean);
          if (namespaces.length === 0) {
            message(t('admin.error.namespacesRequired'), 'error');
            return null;
          }
        }
        return {
          displayName,
          description: (result.description || '').trim(),
          admin,
          namespaces
        };
      }

      async function createAdminApiKey() {
        const result = await showAppFormDialog({
          title: t('admin.createTitle'),
          confirmLabel: t('admin.save'),
          fields: adminApiKeyFormFields()
        });
        const parsed = parseAdminApiKeyForm(result);
        if (!parsed) return;
        const id = deriveApiKeyId(parsed.displayName);
        try {
          const created = await adminApi('/api-keys', {
            method: 'POST',
            body: JSON.stringify({
              id,
              displayName: parsed.displayName,
              description: parsed.description,
              admin: parsed.admin,
              namespaces: parsed.namespaces
            })
          });
          await showAdminKeyDialog(t('admin.keyCreated'), created.secret);
          await refreshAdminApiKeys();
          message(t('admin.created', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function editAdminApiKey(apiKeyId) {
        const apiKey = adminApiKeys.find(item => item.id === apiKeyId);
        if (!apiKey) return;
        const result = await showAppFormDialog({
          title: t('admin.editTitle'),
          confirmLabel: t('admin.save'),
          fields: adminApiKeyFormFields(apiKey)
        });
        const parsed = parseAdminApiKeyForm(result);
        if (!parsed) return;

        try {
          await adminApi(`/api-keys/${encodeURIComponent(apiKeyId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              displayName: parsed.displayName,
              description: parsed.description,
              status: apiKey.status,
              admin: parsed.admin,
              namespaces: parsed.namespaces
            })
          });
          await refreshAdminApiKeys();
          message(t('admin.updated', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function isProtectedAdminApiKey(apiKeyId) {
        return apiKeyId === 'platform-admin';
      }

      function adminApiKeyLastUsedLabel(apiKey) {
        return apiKey?.lastUsedAt ? formatInstant(apiKey.lastUsedAt) : t('admin.lastUsedNever');
      }

      async function disableAdminApiKey(apiKeyId) {
        const apiKey = adminApiKeys.find(item => item.id === apiKeyId);
        if (!apiKey) return;
        const label = apiKey.displayName || apiKeyId;
        const confirmed = await showAppDialog({
          title: t('admin.disable'),
          message: t('admin.confirm.disable', { name: label }),
          input: 'none',
          danger: true
        });
        if (!confirmed) return;
        try {
          await adminApi(`/api-keys/${encodeURIComponent(apiKeyId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              displayName: apiKey.displayName,
              description: apiKey.description || '',
              status: 'disabled',
              admin: apiKey.admin,
              namespaces: Array.isArray(apiKey.namespaces) ? apiKey.namespaces : []
            })
          });
          await refreshAdminApiKeys();
          message(t('admin.disabled', { name: label }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function deleteAdminApiKey(apiKeyId) {
        const apiKey = adminApiKeys.find(item => item.id === apiKeyId);
        if (!apiKey) return;
        const label = apiKey.displayName || apiKeyId;
        const confirmed = await showAppDialog({
          title: t('admin.delete'),
          message: t('admin.confirm.delete', { name: label, lastUsed: adminApiKeyLastUsedLabel(apiKey) }),
          input: 'none',
          danger: true,
          confirmLabel: t('admin.delete')
        });
        if (!confirmed) return;
        try {
          await adminApi(`/api-keys/${encodeURIComponent(apiKeyId)}`, { method: 'DELETE' });
          await refreshAdminApiKeys();
          message(t('admin.deleted', { name: label }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function enableAdminApiKey(apiKeyId) {
        const apiKey = adminApiKeys.find(item => item.id === apiKeyId);
        if (!apiKey) return;
        try {
          await adminApi(`/api-keys/${encodeURIComponent(apiKeyId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              displayName: apiKey.displayName,
              description: apiKey.description || '',
              status: 'active',
              admin: apiKey.admin,
              namespaces: Array.isArray(apiKey.namespaces) ? apiKey.namespaces : []
            })
          });
          await refreshAdminApiKeys();
          message(t('admin.enabled', { name: apiKey.displayName || apiKeyId }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function showAdminKeyDialog(title, apiKey) {
        await showAppDialog({
          title,
          message: `${t('admin.keyCopyHint')}\n\n${apiKey}`,
          input: 'none',
          confirmLabel: t('dialog.confirm')
        });
        if (navigator.clipboard?.writeText) {
          try { await navigator.clipboard.writeText(apiKey); } catch (ignored) {}
        }
      }

      function formatInstant(value) {
        if (!value) return '-';
        try { return new Date(value).toLocaleString(); } catch (ignored) { return String(value); }
      }
