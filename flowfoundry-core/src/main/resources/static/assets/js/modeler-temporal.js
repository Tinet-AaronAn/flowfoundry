
      let temporalOverview = null;
      let temporalNamespaces = [];
      let temporalWorkers = [];
      let temporalSchedules = [];
      let temporalClusters = [];
      let temporalActiveTab = 'overview';

      async function temporalAdminApi(path = '', options = {}) {
        return adminApi(`/temporal${path}`, options);
      }

      function temporalStatusLabel(status) {
        const key = `temporal.status.${status || 'UNKNOWN'}`;
        const translated = t(key);
        return translated === key ? (status || '-') : translated;
      }

      function temporalStatusClass(status) {
        switch (status) {
          case 'READY': return 'status-pill status-ready';
          case 'NO_TEMPORAL_NS': return 'status-pill status-warning';
          case 'NO_WORKER':
          case 'STALE_WORKER': return 'status-pill status-danger';
          case 'ORPHAN_TEMPORAL': return 'status-pill status-muted';
          default: return 'status-pill';
        }
      }

      function setTemporalTab(tab) {
        temporalActiveTab = tab;
        ['overview', 'namespaces', 'workers', 'schedules', 'clusters'].forEach(name => {
          $(`temporalTab${name.charAt(0).toUpperCase()}${name.slice(1)}`)?.classList.toggle('active', name === tab);
          $(`temporalPanel${name.charAt(0).toUpperCase()}${name.slice(1)}`)?.classList.toggle('hidden', name !== tab);
        });
        if (tab === 'overview') renderTemporalOverview();
        if (tab === 'namespaces') renderTemporalNamespacesTable();
        if (tab === 'workers') renderTemporalWorkersTable();
        if (tab === 'schedules') renderTemporalSchedulesTable();
        if (tab === 'clusters') renderTemporalClustersTable();
      }

      async function refreshTemporalView(refresh = true) {
        const query = refresh ? '?refresh=true' : '';
        const [overview, namespaces, workers, schedules, clusters] = await Promise.all([
          temporalAdminApi('/overview'),
          temporalAdminApi(`/namespaces${query}`),
          temporalAdminApi(`/workers${query}`),
          temporalAdminApi('/schedules'),
          temporalAdminApi('/clusters')
        ]);
        temporalOverview = overview;
        temporalNamespaces = namespaces?.items || [];
        temporalWorkers = workers?.items || [];
        temporalSchedules = schedules?.items || [];
        temporalClusters = clusters?.items || [];
        setTemporalTab(temporalActiveTab);
      }

      function renderTemporalOverview() {
        const panel = $('temporalPanelOverview');
        if (!panel || !temporalOverview) return;
        const cluster = temporalOverview.cluster || {};
        const summary = temporalOverview.summary || {};
        panel.innerHTML = `
          <div class="temporal-summary-grid">
            <div class="temporal-card">
              <div class="temporal-card-title">${escapeHtml(t('temporal.clusterTitle'))}</div>
              <div>${escapeHtml(cluster.displayName || cluster.id || '-')}</div>
              <div class="temporal-meta"><code>${escapeHtml(cluster.host || '-')}</code></div>
              <div class="temporal-meta">${cluster.reachable ? t('temporal.connected') : t('temporal.disconnected')}${cluster.serverVersion ? ` · ${escapeHtml(cluster.serverVersion)}` : ''}</div>
            </div>
            <div class="temporal-card">
              <div class="temporal-card-title">${escapeHtml(t('temporal.summaryTitle'))}</div>
              <div class="temporal-stats">
                <span>${escapeHtml(t('temporal.stat.platform'))}: ${summary.platformNamespaces ?? 0}</span>
                <span>${escapeHtml(t('temporal.stat.ready'))}: ${summary.ready ?? 0}</span>
                <span>${escapeHtml(t('temporal.stat.noTemporal'))}: ${summary.noTemporalNs ?? 0}</span>
                <span>${escapeHtml(t('temporal.stat.noWorker'))}: ${summary.noWorker ?? 0}</span>
              </div>
            </div>
          </div>
        `;
      }

      function renderTemporalNamespacesTable() {
        const tbody = $('temporalNamespacesTable');
        if (!tbody) return;
        const keyword = $('temporalNamespaceSearch')?.value.trim().toLowerCase() || '';
        const rows = temporalNamespaces.filter(item => {
          if (!keyword) return true;
          return `${item.id} ${item.displayName || ''} ${item.status || ''}`.toLowerCase().includes(keyword);
        });
        if (!rows.length) {
          tbody.innerHTML = `<tr><td colspan="8" class="admin-empty">${escapeHtml(t('temporal.emptyNamespaces'))}</td></tr>`;
          return;
        }
        tbody.innerHTML = rows.map(item => `
          <tr>
            <td><code>${escapeHtml(item.id)}</code></td>
            <td>${escapeHtml(item.displayName || '-')}</td>
            <td>${escapeHtml(item.temporalClusterName || item.temporalClusterId || '-')}</td>
            <td>${item.platformRegistered ? '✓' : '—'}</td>
            <td>${item.temporalRegistered ? '✓' : '—'}</td>
            <td>${item.worker?.alive ? escapeHtml(t('temporal.workerAlive')) : escapeHtml(t('temporal.workerOffline'))}</td>
            <td><span class="${temporalStatusClass(item.status)}">${escapeHtml(temporalStatusLabel(item.status))}</span></td>
            <td class="admin-actions">
              ${item.status === 'NO_TEMPORAL_NS' && item.platformRegistered ? `<button class="secondary" onclick="registerTemporalNamespace('${escapeAttr(item.id)}')">${escapeHtml(t('temporal.register'))}</button>` : ''}
              ${item.temporalUiUrl ? `<button class="secondary" onclick="window.open('${escapeAttr(item.temporalUiUrl)}', '_blank')">${escapeHtml(t('temporal.openUi'))}</button>` : ''}
            </td>
          </tr>
        `).join('');
      }

      function renderTemporalWorkersTable() {
        const tbody = $('temporalWorkersTable');
        if (!tbody) return;
        if (!temporalWorkers.length) {
          tbody.innerHTML = `<tr><td colspan="8" class="admin-empty">${escapeHtml(t('temporal.emptyWorkers'))}</td></tr>`;
          return;
        }
        tbody.innerHTML = temporalWorkers.map(item => `
          <tr>
            <td>${escapeHtml(item.appId || '-')}</td>
            <td><code>${escapeHtml(item.namespace || '')}</code></td>
            <td>${escapeHtml(item.temporalClusterName || item.temporalClusterId || '-')}</td>
            <td><code>${escapeHtml(item.taskQueue || '')}</code></td>
            <td>${item.alive ? escapeHtml(t('temporal.workerAlive')) : escapeHtml(t('temporal.workerOffline'))}</td>
            <td>${item.workflowPollers ?? 0}</td>
            <td>${item.activityPollers ?? 0}</td>
            <td>${item.platformCoreQueue?.pollers ?? 0}</td>
          </tr>
        `).join('');
      }

      function renderTemporalSchedulesTable() {
        const tbody = $('temporalSchedulesTable');
        if (!tbody) return;
        if (!temporalSchedules.length) {
          tbody.innerHTML = `<tr><td colspan="6" class="admin-empty">${escapeHtml(t('temporal.emptySchedules'))}</td></tr>`;
          return;
        }
        tbody.innerHTML = temporalSchedules.map(item => `
          <tr>
            <td><code>${escapeHtml(item.scheduleId || '')}</code></td>
            <td><code>${escapeHtml(item.namespace || '')}</code></td>
            <td><code>${escapeHtml(item.workflowId || '')}</code></td>
            <td>${escapeHtml(item.state || '-')}</td>
            <td>${escapeHtml(formatInstant(item.nextRunTime))}</td>
            <td class="admin-actions">
              ${item.state === 'PAUSED'
                ? `<button class="secondary" onclick="resumeTemporalSchedule('${escapeAttr(item.namespace)}','${escapeAttr(item.scheduleId)}')">${escapeHtml(t('temporal.resume'))}</button>`
                : `<button class="secondary" onclick="pauseTemporalSchedule('${escapeAttr(item.namespace)}','${escapeAttr(item.scheduleId)}')">${escapeHtml(t('temporal.pause'))}</button>`}
            </td>
          </tr>
        `).join('');
      }

      function renderTemporalClustersTable() {
        const tbody = $('temporalClustersTable');
        if (!tbody) return;
        if (!temporalClusters.length) {
          tbody.innerHTML = `<tr><td colspan="7" class="admin-empty">${escapeHtml(t('temporal.emptyClusters'))}</td></tr>`;
          return;
        }
        tbody.innerHTML = temporalClusters.map(item => `
          <tr>
            <td><code>${escapeHtml(item.id)}</code></td>
            <td>${escapeHtml(item.displayName || '')}</td>
            <td><code>${escapeHtml(item.host || '')}</code></td>
            <td>${item.defaultCluster ? '✓' : '—'}</td>
            <td>${item.reachable ? escapeHtml(t('temporal.connected')) : escapeHtml(t('temporal.disconnected'))}</td>
            <td>${escapeHtml(item.serverVersion || '-')}</td>
            <td class="admin-actions">
              <button class="secondary" onclick="editTemporalCluster('${escapeAttr(item.id)}')">${escapeHtml(t('admin.edit'))}</button>
              ${item.defaultCluster ? '' : `<button class="secondary danger" onclick="deleteTemporalCluster('${escapeAttr(item.id)}')">${escapeHtml(t('admin.delete'))}</button>`}
            </td>
          </tr>
        `).join('');
      }

      async function renderTemporalView() {
        await loadAdminProfile();
        const allowed = isLocalAdminHost() || adminProfile?.admin;
        if (typeof applyI18n === 'function') {
          applyI18n($('temporalView') || document);
        }
        if (!allowed) {
          $('temporalAccessDenied')?.classList.remove('hidden');
          $('temporalPanels')?.classList.add('hidden');
          return;
        }
        $('temporalAccessDenied')?.classList.add('hidden');
        $('temporalPanels')?.classList.remove('hidden');
        try {
          await refreshTemporalView(true);
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function registerTemporalNamespace(namespaceId) {
        try {
          await temporalAdminApi(`/namespaces/${encodeURIComponent(namespaceId)}/register`, { method: 'POST' });
          await refreshTemporalView(true);
          await refreshAdminNamespaces();
          message(t('temporal.registered', { id: namespaceId }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function pauseTemporalSchedule(namespaceId, scheduleId) {
        try {
          await temporalAdminApi(`/schedules/${encodeURIComponent(namespaceId)}/${encodeURIComponent(scheduleId)}/pause`, { method: 'POST' });
          await refreshTemporalView(true);
          message(t('temporal.paused', { id: scheduleId }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function resumeTemporalSchedule(namespaceId, scheduleId) {
        try {
          await temporalAdminApi(`/schedules/${encodeURIComponent(namespaceId)}/${encodeURIComponent(scheduleId)}/resume`, { method: 'POST' });
          await refreshTemporalView(true);
          message(t('temporal.resumed', { id: scheduleId }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function temporalClusterFormFields(cluster = {}) {
        const editing = !!cluster.id;
        const fields = [
          { name: 'displayName', label: t('temporal.prompt.displayName'), type: 'text', value: cluster.displayName || '' },
          { name: 'host', label: t('temporal.prompt.host'), type: 'text', value: cluster.host || '127.0.0.1:7233' },
          { name: 'uiBaseUrl', label: t('temporal.prompt.uiBaseUrl'), type: 'text', value: cluster.uiBaseUrl || 'http://127.0.0.1:8080' },
          { name: 'defaultCluster', label: t('temporal.prompt.defaultCluster'), type: 'checkbox', value: !!cluster.defaultCluster }
        ];
        if (!editing) {
          fields.unshift({ name: 'id', label: t('temporal.prompt.id'), type: 'text', value: cluster.id || 'cluster-1' });
        }
        return fields;
      }

      function parseTemporalClusterForm(result, editing) {
        if (!result) return null;
        const displayName = (result.displayName || '').trim();
        const host = (result.host || '').trim();
        if (!displayName || !host) {
          message(t('temporal.error.clusterRequired'), 'error');
          return null;
        }
        const parsed = {
          displayName,
          host,
          uiBaseUrl: (result.uiBaseUrl || '').trim(),
          defaultCluster: !!result.defaultCluster
        };
        if (!editing) {
          const id = (result.id || '').trim();
          if (!id) {
            message(t('temporal.error.idRequired'), 'error');
            return null;
          }
          parsed.id = id;
        }
        return parsed;
      }

      async function createTemporalCluster() {
        const result = await showAppFormDialog({
          title: t('temporal.createClusterTitle'),
          confirmLabel: t('admin.save'),
          fields: temporalClusterFormFields()
        });
        const parsed = parseTemporalClusterForm(result, false);
        if (!parsed) return;
        try {
          await temporalAdminApi('/clusters', { method: 'POST', body: JSON.stringify(parsed) });
          await refreshTemporalView(true);
          message(t('temporal.clusterCreated', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function editTemporalCluster(clusterId) {
        const cluster = temporalClusters.find(item => item.id === clusterId);
        if (!cluster) return;
        const result = await showAppFormDialog({
          title: t('temporal.editClusterTitle'),
          confirmLabel: t('admin.save'),
          fields: temporalClusterFormFields(cluster)
        });
        const parsed = parseTemporalClusterForm(result, true);
        if (!parsed) return;
        try {
          await temporalAdminApi(`/clusters/${encodeURIComponent(clusterId)}`, {
            method: 'PUT',
            body: JSON.stringify(parsed)
          });
          await refreshTemporalView(true);
          message(t('temporal.clusterUpdated', { name: parsed.displayName }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      async function deleteTemporalCluster(clusterId) {
        const cluster = temporalClusters.find(item => item.id === clusterId);
        if (!cluster) return;
        const confirmed = await showAppDialog({
          title: t('admin.delete'),
          message: t('temporal.confirm.deleteCluster', { id: cluster.id, name: cluster.displayName || cluster.id }),
          input: 'none',
          confirmLabel: t('admin.delete'),
          cancelLabel: t('dialog.cancel')
        });
        if (!confirmed) return;
        try {
          await temporalAdminApi(`/clusters/${encodeURIComponent(clusterId)}`, { method: 'DELETE' });
          await refreshTemporalView(true);
          message(t('temporal.clusterDeleted', { name: cluster.displayName || cluster.id }), 'success');
        } catch (err) {
          message(err.message, 'error');
        }
      }

      function openTemporalUi() {
        const url = temporalOverview?.cluster?.uiBaseUrl;
        if (url) window.open(url, '_blank');
      }

      async function offerTemporalNamespaceWizard(namespaceId) {
        const alignment = temporalNamespaces.find(item => item.id === namespaceId);
        if (!alignment || alignment.status === 'READY') return;
        const steps = [];
        if (alignment.status === 'NO_TEMPORAL_NS') {
          steps.push(t('temporal.wizard.register'));
        }
        if (alignment.status === 'NO_WORKER' || alignment.status === 'STALE_WORKER') {
          steps.push(t('temporal.wizard.worker'));
        }
        if (!steps.length) return;
        const proceed = await showAppDialog({
          title: t('temporal.wizard.title'),
          message: t('temporal.wizard.message', { id: namespaceId, steps: steps.join('\n') }),
          input: 'none',
          confirmLabel: alignment.status === 'NO_TEMPORAL_NS' ? t('temporal.register') : t('dialog.confirm'),
          cancelLabel: t('dialog.cancel')
        });
        if (!proceed) return;
        if (alignment.status === 'NO_TEMPORAL_NS') {
          await registerTemporalNamespace(namespaceId);
        } else {
          switchView('temporal');
        }
      }
