
      let adminProfile = null;
      let adminClients = [];
      let adminAuditLogs = [];
      let adminAuditState = { page: 0, size: 10, totalPages: 0, totalElements: 0, loaded: false };

      async function adminApi(path = '', options = {}) {
        const response = await fetch(platformApiUrl(`/admin${path}`), {
          ...options,
          headers: {
            'Content-Type': 'application/json',
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

      function adminNamespacesLabel(client) {
        if (!client) return '-';
        if (client.admin) return t('admin.namespaceAdmin');
        const namespaces = adminClientNamespaceList(client);
        return namespaces.length ? namespaces.join(', ') : '-';
      }

      function adminClientNamespaceList(client) {
        const namespaces = client?.namespaces;
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
            clientId: 'localhost-admin',
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
          actor: adminProfile.clientId || 'localhost-admin',
          security: adminProfile.securityEnabled ? t('admin.securityOn') : t('admin.securityOff')
        });
        renderAdminClientsTable();
        try {
          await refreshAdminClients();
          renderAdminAuditPlaceholder();
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function filteredAdminClients() {
        const keyword = $('adminClientSearch')?.value.trim().toLowerCase() || '';
        if (!keyword) return adminClients;
        return adminClients.filter(client => {
          const haystack = `${client.id} ${client.displayName} ${adminNamespacesLabel(client)}`.toLowerCase();
          return haystack.includes(keyword);
        });
      }

      function renderAdminClientsTable() {
        const tbody = $('adminClientsTable');
        if (!tbody) return;
        const clients = filteredAdminClients();
        if (clients.length === 0) {
          const emptyText = adminClients.length === 0 ? t('admin.empty') : t('admin.emptySearch');
          tbody.innerHTML = `<tr><td colspan="6" class="admin-empty">${escapeHtml(emptyText)}</td></tr>`;
          return;
        }
        tbody.innerHTML = clients.map(client => `
          <tr>
            <td>${escapeHtml(client.displayName || client.id || '')}</td>
            <td>${escapeHtml(adminNamespacesLabel(client))}</td>
            <td><code>${escapeHtml(client.keyPrefix || '')}</code></td>
            <td><span class="pill ${client.status === 'active' ? 'completed' : 'failed'}">${escapeHtml(client.status || '')}</span></td>
            <td>${escapeHtml(formatInstant(client.lastUsedAt))}</td>
            <td class="admin-actions">
              <button class="secondary" onclick="viewAdminClient('${escapeAttr(client.id)}')">${escapeHtml(t('admin.view'))}</button>
              <button class="secondary" onclick="editAdminClient('${escapeAttr(client.id)}')">${escapeHtml(t('admin.edit'))}</button>
              ${client.status === 'active'
                ? `<button class="secondary danger" onclick="disableAdminClient('${escapeAttr(client.id)}')">${escapeHtml(t('admin.disable'))}</button>`
                : `<button class="secondary" onclick="enableAdminClient('${escapeAttr(client.id)}')">${escapeHtml(t('admin.enable'))}</button>`}
              ${isProtectedAdminClient(client.id)
                ? ''
                : `<button class="secondary danger" onclick="deleteAdminClient('${escapeAttr(client.id)}')">${escapeHtml(t('admin.delete'))}</button>`}
            </td>
          </tr>
        `).join('');
      }

      async function refreshAdminClients() {
        adminClients = await adminApi('/api-clients');
        if (!Array.isArray(adminClients)) {
          adminClients = [];
        }
        renderAdminClientsTable();
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
        const clientId = $('adminAuditClientFilter')?.value.trim() || '';
        const action = $('adminAuditActionFilter')?.value.trim() || '';
        const from = readAdminAuditDateTimeInput('adminAuditFromFilter');
        const to = readAdminAuditDateTimeInput('adminAuditToFilter');
        const includeApiCalls = !!$('adminAuditIncludeApiCalls')?.checked;
        const query = new URLSearchParams();
        if (clientId) query.set('clientId', clientId);
        if (action) query.set('action', action);
        if (from) query.set('from', from);
        if (to) query.set('to', to);
        if (includeApiCalls) query.set('includeApiCalls', 'true');
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
            <td>${escapeHtml(log.clientId || log.actorClientId || '')}</td>
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

      async function viewAdminClient(clientId) {
        const client = adminClients.find(item => item.id === clientId) || await adminApi(`/api-clients/${encodeURIComponent(clientId)}`);
        if (!client) return;
        await showAppDialog({
          title: t('admin.viewTitle', { name: client.displayName || client.id }),
          message: [
            `${t('admin.col.name')}: ${client.displayName || '-'}`,
            `${t('admin.col.status')}: ${client.status || '-'}`,
            `${t('admin.col.namespaces')}: ${adminNamespacesLabel(client)}`,
            `${t('admin.col.keyPrefix')}: ${client.keyPrefix || '-'}`,
            `${t('admin.col.lastUsed')}: ${formatInstant(client.lastUsedAt)}`,
            `Created: ${formatInstant(client.createdAt)}`,
            `Updated: ${formatInstant(client.updatedAt)}`,
            client.description ? `Description: ${client.description}` : ''
          ].filter(Boolean).join('\n'),
          input: 'none',
          confirmLabel: t('dialog.confirm')
        });
      }

      function deriveClientId(displayName) {
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

      function adminClientFormFields(client = {}) {
        const isAdmin = !!client.admin;
        return [
          {
            name: 'displayName',
            label: t('admin.prompt.name'),
            type: 'text',
            value: client.displayName || 'my-app'
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
            value: client.id
              ? adminClientNamespaceList(client).join(', ')
              : 'ai-collection-strategy',
            dependsOn: { field: 'admin', value: 'false' }
          },
          {
            name: 'description',
            label: t('admin.prompt.description'),
            type: 'text',
            value: client.description || ''
          }
        ];
      }

      function parseAdminClientForm(result) {
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

      async function createAdminClient() {
        const result = await showAppFormDialog({
          title: t('admin.createTitle'),
          confirmLabel: t('admin.save'),
          fields: adminClientFormFields()
        });
        const parsed = parseAdminClientForm(result);
        if (!parsed) return;
        const id = deriveClientId(parsed.displayName);
        try {
          const created = await adminApi('/api-clients', {
            method: 'POST',
            body: JSON.stringify({
              id,
              displayName: parsed.displayName,
              description: parsed.description,
              admin: parsed.admin,
              namespaces: parsed.namespaces
            })
          });
          await showAdminKeyDialog(t('admin.keyCreated'), created.apiKey);
          await refreshAdminClients();
          message(t('admin.created', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function editAdminClient(clientId) {
        const client = adminClients.find(item => item.id === clientId);
        if (!client) return;
        const result = await showAppFormDialog({
          title: t('admin.editTitle'),
          confirmLabel: t('admin.save'),
          fields: adminClientFormFields(client)
        });
        const parsed = parseAdminClientForm(result);
        if (!parsed) return;

        try {
          await adminApi(`/api-clients/${encodeURIComponent(clientId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              displayName: parsed.displayName,
              description: parsed.description,
              status: client.status,
              admin: parsed.admin,
              namespaces: parsed.namespaces
            })
          });
          await refreshAdminClients();
          message(t('admin.updated', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function isProtectedAdminClient(clientId) {
        return clientId === 'platform-admin';
      }

      function adminClientLastUsedLabel(client) {
        return client?.lastUsedAt ? formatInstant(client.lastUsedAt) : t('admin.lastUsedNever');
      }

      async function disableAdminClient(clientId) {
        const client = adminClients.find(item => item.id === clientId);
        if (!client) return;
        const label = client.displayName || clientId;
        const confirmed = await showAppDialog({
          title: t('admin.disable'),
          message: t('admin.confirm.disable', { name: label }),
          input: 'none',
          danger: true
        });
        if (!confirmed) return;
        try {
          await adminApi(`/api-clients/${encodeURIComponent(clientId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              displayName: client.displayName,
              description: client.description || '',
              status: 'disabled',
              admin: client.admin,
              namespaces: Array.isArray(client.namespaces) ? client.namespaces : []
            })
          });
          await refreshAdminClients();
          message(t('admin.disabled', { name: label }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function deleteAdminClient(clientId) {
        const client = adminClients.find(item => item.id === clientId);
        if (!client) return;
        const label = client.displayName || clientId;
        const confirmed = await showAppDialog({
          title: t('admin.delete'),
          message: t('admin.confirm.delete', { name: label, lastUsed: adminClientLastUsedLabel(client) }),
          input: 'none',
          danger: true,
          confirmLabel: t('admin.delete')
        });
        if (!confirmed) return;
        try {
          await adminApi(`/api-clients/${encodeURIComponent(clientId)}`, { method: 'DELETE' });
          await refreshAdminClients();
          message(t('admin.deleted', { name: label }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function enableAdminClient(clientId) {
        const client = adminClients.find(item => item.id === clientId);
        if (!client) return;
        try {
          await adminApi(`/api-clients/${encodeURIComponent(clientId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              displayName: client.displayName,
              description: client.description || '',
              status: 'active',
              admin: client.admin,
              namespaces: Array.isArray(client.namespaces) ? client.namespaces : []
            })
          });
          await refreshAdminClients();
          message(t('admin.enabled', { name: client.displayName || clientId }), 'success');
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
