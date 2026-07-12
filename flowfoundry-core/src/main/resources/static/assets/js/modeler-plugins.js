
      let pluginItems = [];
      let pluginSelectedKey = null;
      let pluginRefreshTimer = null;

      async function pluginAdminApi(path = '', options = {}) {
        const headers = {
          ...platformApiHeaders(),
          ...(options?.headers || {})
        };
        if (!(options?.body instanceof FormData)) {
          headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(platformApiUrl(`/admin/plugins${path}`), {
          ...options,
          headers
        });
        if (!response.ok) {
          const text = await response.text();
          let errMessage = text;
          try {
            const parsed = JSON.parse(text);
            errMessage = parsed.message || parsed.error || text;
          } catch (ignored) {}
          throw new Error(errMessage || response.statusText);
        }
        if (response.status === 204) return null;
        const text = await response.text();
        return text ? JSON.parse(text) : null;
      }

      async function fetchPluginLogs(id, version, tail = 500) {
        const response = await fetch(
          platformApiUrl(`/admin/plugins/${encodeURIComponent(id)}/${encodeURIComponent(version)}/logs?tail=${tail}`),
          { headers: platformApiHeaders() }
        );
        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || response.statusText);
        }
        return response.text();
      }

      function pluginKey(item) {
        return `${item.id}:${item.version}`;
      }

      function pluginStatusLabel(status) {
        const key = `plugin.status.${status || 'UNKNOWN'}`;
        const translated = t(key);
        return translated === key ? (status || '-') : translated;
      }

      function pluginStatusClass(item) {
        const state = item?.state || '';
        const desired = item?.desiredState || '';
        if (state === 'RUNNING' && item?.runtimeHealthy === false) {
          return 'status-pill status-warning';
        }
        if (state === 'RUNNING' && item?.runtimeHealthy !== false) {
          return 'status-pill status-ready';
        }
        if (state === 'FAILED') return 'status-pill status-danger';
        if (desired === 'RUNNING' && state !== 'RUNNING') return 'status-pill status-warning';
        if (state === 'STOPPED') return 'status-pill status-muted';
        return 'status-pill';
      }

      function pluginReplicasLabel(item) {
        const ready = item?.readyReplicas ?? 0;
        const desired = item?.runtimeDesiredReplicas ?? item?.replicas ?? 0;
        return `${ready}/${desired}`;
      }

      function findPluginItem(id, version) {
        return pluginItems.find(item => item.id === id && item.version === version);
      }

      function groupedPluginIds() {
        const ids = new Set(pluginItems.map(item => item.id));
        return [...ids].sort((a, b) => a.localeCompare(b));
      }

      async function refreshPluginsView() {
        const page = await pluginAdminApi('');
        pluginItems = page?.items || [];
        renderPluginAlerts();
        renderPluginsTable();
        renderPluginDetail();
      }

      function renderPluginAlerts() {
        const panel = $('pluginAlertsPanel');
        if (!panel) return;
        const alerts = pluginItems.filter(item => {
          if (item.desiredState === 'RUNNING' && item.runtimeHealthy === false) return true;
          if (item.errorDetail) return true;
          return false;
        });
        if (!alerts.length) {
          panel.innerHTML = `<div class="admin-empty">${escapeHtml(t('plugin.noAlerts'))}</div>`;
          return;
        }
        panel.innerHTML = alerts.map(item => `
          <div class="app-notice app-notice-warning temporal-alert">
            <div class="app-notice-text">
              <strong>${escapeHtml(item.id)}:${escapeHtml(item.version)}</strong>
              · ${escapeHtml(pluginStatusLabel(item.state))}
              ${item.errorDetail ? ` — ${escapeHtml(item.errorDetail)}` : ''}
              ${item.runtimeSummary ? `<div class="temporal-meta">${escapeHtml(item.runtimeSummary)}</div>` : ''}
            </div>
          </div>
        `).join('');
      }

      function renderPluginsTable() {
        const tbody = $('pluginsTable');
        if (!tbody) return;
        const keyword = $('pluginSearch')?.value.trim().toLowerCase() || '';
        const rows = pluginItems.filter(item => {
          if (!keyword) return true;
          const haystack = `${item.id} ${item.version} ${item.displayName || ''} ${item.namespace || ''} ${item.taskQueue || ''} ${item.state || ''}`;
          return haystack.toLowerCase().includes(keyword);
        });
        if (!rows.length) {
          tbody.innerHTML = `<tr><td colspan="10" class="admin-empty">${escapeHtml(t('plugin.empty'))}</td></tr>`;
          return;
        }
        tbody.innerHTML = rows.map(item => {
          const key = pluginKey(item);
          const selected = pluginSelectedKey === key;
          return `
          <tr class="${selected ? 'plugin-row-selected' : ''}" onclick="selectPluginRow('${escapeAttr(key)}')">
            <td><code>${escapeHtml(item.id)}</code></td>
            <td>${escapeHtml(item.version)}</td>
            <td>${escapeHtml(item.displayName || '-')}</td>
            <td><span class="${pluginStatusClass(item)}">${escapeHtml(pluginStatusLabel(item.state))}</span></td>
            <td><code>${escapeHtml(item.namespace || '')}</code></td>
            <td><code>${escapeHtml(item.taskQueue || '')}</code></td>
            <td>${escapeHtml(pluginReplicasLabel(item))}</td>
            <td>${item.activityPollers ?? 0}</td>
            <td>${item.typedWorkflows ? '✓' : '—'}</td>
            <td class="admin-actions" onclick="event.stopPropagation()">
              ${item.desiredState === 'RUNNING'
                ? `<button class="secondary" onclick="stopPlugin('${escapeAttr(item.id)}','${escapeAttr(item.version)}')">${escapeHtml(t('plugin.stop'))}</button>`
                : `<button class="secondary" onclick="startPlugin('${escapeAttr(item.id)}','${escapeAttr(item.version)}')">${escapeHtml(t('plugin.start'))}</button>`}
              <button class="secondary" onclick="scalePlugin('${escapeAttr(item.id)}', ${item.replicas || 1})">${escapeHtml(t('plugin.scale'))}</button>
              <button class="secondary" onclick="reloadPlugin('${escapeAttr(item.id)}','${escapeAttr(item.version)}', ${item.typedWorkflows ? 'true' : 'false'})">${escapeHtml(t('plugin.reload'))}</button>
              <button class="secondary" onclick="viewPluginLogs('${escapeAttr(item.id)}','${escapeAttr(item.version)}')">${escapeHtml(t('plugin.logs'))}</button>
              <button class="secondary danger" onclick="deletePlugin('${escapeAttr(item.id)}','${escapeAttr(item.version)}','${escapeAttr(item.state)}')">${escapeHtml(t('admin.delete'))}</button>
            </td>
          </tr>`;
        }).join('');
      }

      function selectPluginRow(key) {
        pluginSelectedKey = key;
        renderPluginsTable();
        renderPluginDetail();
      }

      function renderPluginDetail() {
        const panel = $('pluginDetailPanel');
        if (!panel) return;
        const item = pluginItems.find(entry => pluginKey(entry) === pluginSelectedKey);
        if (!item) {
          panel.innerHTML = `<div class="admin-empty">${escapeHtml(t('plugin.selectHint'))}</div>`;
          return;
        }
        panel.innerHTML = `
          <div class="temporal-card">
            <div class="temporal-card-title">${escapeHtml(t('plugin.detailTitle'))}</div>
            <div class="temporal-stats">
              <span>${escapeHtml(t('plugin.col.id'))}: <code>${escapeHtml(item.id)}</code></span>
              <span>${escapeHtml(t('plugin.col.version'))}: ${escapeHtml(item.version)}</span>
              <span>${escapeHtml(t('plugin.col.desired'))}: ${escapeHtml(item.desiredState || '-')}</span>
              <span>${escapeHtml(t('plugin.col.runtime'))}: ${escapeHtml(item.runtimeSummary || '-')}</span>
              <span>SHA256: <code>${escapeHtml((item.jarSha256 || '').slice(0, 16))}…</code></span>
              ${item.errorDetail ? `<span class="plugin-detail-error">${escapeHtml(item.errorDetail)}</span>` : ''}
            </div>
          </div>
        `;
      }

      async function renderPluginsView() {
        await loadAdminProfile();
        const allowed = isLocalAdminHost() || adminProfile?.admin;
        if (typeof applyI18n === 'function') {
          applyI18n($('pluginsView') || document);
        }
        if (!allowed) {
          $('pluginsAccessDenied')?.classList.remove('hidden');
          $('pluginsPanels')?.classList.add('hidden');
          stopPluginAutoRefresh();
          return;
        }
        $('pluginsAccessDenied')?.classList.add('hidden');
        $('pluginsPanels')?.classList.remove('hidden');
        try {
          await refreshPluginsView();
          startPluginAutoRefresh();
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function startPluginAutoRefresh() {
        stopPluginAutoRefresh();
        pluginRefreshTimer = window.setInterval(() => {
          if (state.currentView !== 'plugins') return;
          refreshPluginsView().catch(err => message(err.message, 'error'));
        }, 15000);
      }

      function stopPluginAutoRefresh() {
        if (pluginRefreshTimer) {
          clearInterval(pluginRefreshTimer);
          pluginRefreshTimer = null;
        }
      }

      async function uploadPluginPackage(file) {
        if (!file) return;
        const formData = new FormData();
        formData.append('file', file);
        try {
          const dto = await pluginAdminApi('', { method: 'POST', body: formData });
          await refreshPluginsView();
          if (typeof refreshActivityCatalog === 'function') {
            await refreshActivityCatalog();
          }
          message(t('plugin.uploaded', { id: dto.id, version: dto.version }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function onPluginFileInputChange(event) {
        const file = event.target?.files?.[0];
        uploadPluginPackage(file);
        if (event.target) event.target.value = '';
      }

      function initPluginUploadDropzone() {
        const zone = $('pluginUploadDropzone');
        if (!zone || zone.dataset.bound === '1') return;
        zone.dataset.bound = '1';
        zone.addEventListener('dragover', event => {
          event.preventDefault();
          zone.classList.add('plugin-upload-active');
        });
        zone.addEventListener('dragleave', () => zone.classList.remove('plugin-upload-active'));
        zone.addEventListener('drop', event => {
          event.preventDefault();
          zone.classList.remove('plugin-upload-active');
          const file = event.dataTransfer?.files?.[0];
          uploadPluginPackage(file);
        });
      }

      async function startPlugin(id, version) {
        const confirmed = await showAppDialog({
          title: t('plugin.start'),
          message: t('plugin.confirm.start', { id, version }),
          input: 'none',
          confirmLabel: t('plugin.start'),
          cancelLabel: t('dialog.cancel')
        });
        if (!confirmed) return;
        try {
          await pluginAdminApi(`/${encodeURIComponent(id)}/${encodeURIComponent(version)}/start`, { method: 'POST' });
          await refreshPluginsView();
          if (typeof refreshActivityCatalog === 'function') await refreshActivityCatalog();
          message(t('plugin.started', { id, version }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function stopPlugin(id, version) {
        const item = findPluginItem(id, version);
        const extra = item?.typedWorkflows ? `\n\n${t('plugin.confirm.stopTyped')}` : '';
        const confirmed = await showAppDialog({
          title: t('plugin.stop'),
          message: t('plugin.confirm.stop', { id, version }) + extra,
          input: 'none',
          confirmLabel: t('plugin.stop'),
          cancelLabel: t('dialog.cancel')
        });
        if (!confirmed) return;
        try {
          await pluginAdminApi(`/${encodeURIComponent(id)}/${encodeURIComponent(version)}/stop`, { method: 'POST' });
          await refreshPluginsView();
          if (typeof refreshActivityCatalog === 'function') await refreshActivityCatalog();
          message(t('plugin.stopped', { id, version }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function reloadPlugin(id, version, typedWorkflows) {
        let messageText = t('plugin.confirm.reload', { id, version });
        if (typedWorkflows) {
          messageText += `\n\n${t('plugin.confirm.reloadTyped')}`;
        }
        const confirmed = await showAppDialog({
          title: t('plugin.reload'),
          message: messageText,
          input: 'none',
          confirmLabel: t('plugin.reload'),
          cancelLabel: t('dialog.cancel')
        });
        if (!confirmed) return;
        try {
          await pluginAdminApi(`/${encodeURIComponent(id)}/${encodeURIComponent(version)}/reload`, { method: 'POST' });
          await refreshPluginsView();
          if (typeof refreshActivityCatalog === 'function') await refreshActivityCatalog();
          message(t('plugin.reloaded', { id, version }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function scalePlugin(id, currentReplicas) {
        const result = await showAppFormDialog({
          title: t('plugin.scaleTitle', { id }),
          confirmLabel: t('admin.save'),
          fields: [
            {
              name: 'replicas',
              label: t('plugin.prompt.replicas'),
              type: 'number',
              value: String(currentReplicas || 1)
            }
          ]
        });
        if (!result) return;
        const replicas = Number.parseInt(result.replicas, 10);
        if (!Number.isFinite(replicas) || replicas < 1) {
          message(t('plugin.error.replicas'), 'error');
          return;
        }
        try {
          await pluginAdminApi(`/${encodeURIComponent(id)}/scale`, {
            method: 'PUT',
            body: JSON.stringify({ replicas })
          });
          await refreshPluginsView();
          message(t('plugin.scaled', { id, replicas }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function deletePlugin(id, version, state) {
        if (state === 'RUNNING') {
          message(t('plugin.error.deleteRunning'), 'error');
          return;
        }
        const confirmed = await showAppDialog({
          title: t('admin.delete'),
          message: t('plugin.confirm.delete', { id, version }),
          input: 'none',
          confirmLabel: t('admin.delete'),
          cancelLabel: t('dialog.cancel')
        });
        if (!confirmed) return;
        try {
          await pluginAdminApi(`/${encodeURIComponent(id)}/${encodeURIComponent(version)}`, { method: 'DELETE' });
          if (pluginSelectedKey === `${id}:${version}`) pluginSelectedKey = null;
          await refreshPluginsView();
          if (typeof refreshActivityCatalog === 'function') await refreshActivityCatalog();
          message(t('plugin.deleted', { id, version }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function viewPluginLogs(id, version) {
        try {
          const logs = await fetchPluginLogs(id, version, 500);
          await showAppDialog({
            title: t('plugin.logsTitle', { id, version }),
            input: 'textarea',
            value: logs || t('plugin.logsEmpty'),
            textareaClass: 'log-viewer',
            readonly: true,
            confirmLabel: t('dialog.confirm'),
            cancelLabel: t('dialog.cancel')
          });
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function pluginActivityBadge(activityId) {
        const source = (state.pluginSources || {})[activityId];
        if (!source) return '';
        const healthy = source.runtimeHealthy !== false;
        const label = t('plugin.activitySource', {
          id: source.pluginId,
          version: source.version || '-',
          state: pluginStatusLabel(source.state)
        });
        return `<div class="plugin-source-badge ${healthy ? 'plugin-source-healthy' : 'plugin-source-unhealthy'}">${escapeHtml(label)}</div>`;
      }
