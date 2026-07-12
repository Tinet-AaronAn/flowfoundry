      let dialogResolver = null;

      function isDialogInputMode() {
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        const formEl = $('appDialogForm');
        return (inputEl && !inputEl.classList.contains('hidden'))
          || (textareaEl && !textareaEl.classList.contains('hidden'))
          || (formEl && !formEl.classList.contains('hidden'));
      }

      function closeAppDialog(result) {
        const backdrop = $('appDialogBackdrop');
        if (!backdrop) return;
        backdrop.classList.remove('open');
        backdrop.setAttribute('aria-hidden', 'true');
        backdrop.querySelector('.app-dialog')?.classList.remove('has-choices', 'has-form', 'has-log-viewer');
        $('appDialogInput')?.classList.add('hidden');
        $('appDialogTextarea')?.classList.add('hidden');
        if ($('appDialogTextarea')) {
          $('appDialogTextarea').readOnly = false;
        }
        $('appDialogChoices')?.classList.add('hidden');
        $('appDialogForm')?.classList.add('hidden');
        const choicesHost = $('appDialogChoices');
        if (choicesHost) choicesHost.innerHTML = '';
        const formHost = $('appDialogForm');
        if (formHost) formHost.innerHTML = '';
        if (dialogResolver) {
          const resolve = dialogResolver;
          dialogResolver = null;
          resolve(result);
        }
      }

      function showAppDialog({
        title,
        message = '',
        input = 'text',
        value = '',
        confirmLabel,
        cancelLabel,
        danger = false,
        textareaClass = '',
        readonly = false
      } = {}) {
        const backdrop = $('appDialogBackdrop');
        const dialogEl = backdrop?.querySelector('.app-dialog');
        const titleEl = $('appDialogTitle');
        const messageEl = $('appDialogMessage');
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        const confirmBtn = $('appDialogConfirm');
        const cancelBtn = $('appDialogCancel');
        if (!backdrop || !titleEl || !confirmBtn || !cancelBtn) {
          return Promise.resolve(input === 'none' ? false : null);
        }

        dialogEl?.classList.remove('has-log-viewer');

        titleEl.textContent = title || '';
        if (messageEl) {
          messageEl.textContent = message || '';
          messageEl.hidden = !message;
        }

        inputEl?.classList.add('hidden');
        textareaEl?.classList.add('hidden');
        $('appDialogChoices')?.classList.add('hidden');

        let field = null;
        if (input === 'textarea' && textareaEl) {
          textareaEl.value = value ?? '';
          textareaEl.readOnly = !!readonly;
          textareaEl.className = `app-dialog-field textarea${textareaClass ? ` ${textareaClass}` : ''}`;
          textareaEl.classList.remove('hidden');
          if (textareaClass.includes('log-viewer')) {
            dialogEl?.classList.add('has-log-viewer');
            if (messageEl) messageEl.hidden = true;
          }
          field = textareaEl;
        } else if (input !== 'none' && inputEl) {
          inputEl.value = value ?? '';
          inputEl.classList.remove('hidden');
          field = inputEl;
        }

        confirmBtn.textContent = confirmLabel || t('dialog.confirm');
        cancelBtn.textContent = cancelLabel || t('dialog.cancel');
        confirmBtn.classList.toggle('danger', !!danger);
        confirmBtn.disabled = false;

        backdrop.classList.add('open');
        backdrop.setAttribute('aria-hidden', 'false');

        return new Promise(resolve => {
          dialogResolver = resolve;
          requestAnimationFrame(() => {
            if (field) {
              field.focus();
              field.select?.();
            } else {
              confirmBtn.focus();
            }
          });
        });
      }

      function readAppFormDialogValues(formEl) {
        const data = {};
        if (!formEl) return data;
        formEl.querySelectorAll('input[name], textarea[name], select[name]').forEach(el => {
          if (el.type === 'radio') {
            if (el.checked) data[el.name] = el.value;
            return;
          }
          if (el.type === 'checkbox' && el.dataset.checkboxGroup === 'true') {
            if (!Array.isArray(data[el.name])) data[el.name] = [];
            if (el.checked) data[el.name].push(el.value);
            return;
          }
          data[el.name] = el.value;
        });
        return data;
      }

      function syncAppFormDialogDependencies(formEl) {
        if (!formEl) return;
        formEl.querySelectorAll('[data-depends-on]').forEach(group => {
          const dependsField = group.getAttribute('data-depends-on');
          const dependsValue = group.getAttribute('data-depends-value') ?? '';
          const current = formEl.querySelector(`input[name="${dependsField}"]:checked`)?.value
            ?? formEl.querySelector(`[name="${dependsField}"]`)?.value
            ?? '';
          group.classList.toggle('hidden', current !== dependsValue);
        });
      }

      function confirmAppDialog() {
        const formEl = $('appDialogForm');
        if (formEl && !formEl.classList.contains('hidden')) {
          closeAppDialog(readAppFormDialogValues(formEl));
          return;
        }
        const choicesEl = $('appDialogChoices');
        if (choicesEl && !choicesEl.classList.contains('hidden')) {
          const selected = choicesEl.querySelector('input[name="appDialogChoice"]:checked:not(:disabled)');
          closeAppDialog(selected ? selected.value : null);
          return;
        }
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        if (inputEl && !inputEl.classList.contains('hidden')) {
          closeAppDialog(inputEl.value);
          return;
        }
        if (textareaEl && !textareaEl.classList.contains('hidden')) {
          closeAppDialog(textareaEl.value);
          return;
        }
        closeAppDialog(true);
      }

      function cancelAppDialog() {
        const choicesEl = $('appDialogChoices');
        if (choicesEl && !choicesEl.classList.contains('hidden')) {
          closeAppDialog(null);
          return;
        }
        closeAppDialog(isDialogInputMode() ? null : false);
      }

      function selectAppDialogChoice(choicesEl, choiceEl, confirmBtn) {
        if (!choicesEl || !choiceEl || choiceEl.classList.contains('disabled')) return;
        const input = choiceEl.querySelector('input[type="radio"]');
        if (!input || input.disabled) return;
        input.checked = true;
        choicesEl.querySelectorAll('.app-dialog-choice').forEach(item => item.classList.remove('selected'));
        choiceEl.classList.add('selected');
        if (confirmBtn) confirmBtn.disabled = false;
      }

      function renderAppFormDialogField(field) {
        const hint = field.hint ? `<p class="app-dialog-form-hint">${escapeHtml(field.hint)}</p>` : '';
        const dependsAttrs = field.dependsOn
          ? ` data-depends-on="${escapeAttr(field.dependsOn.field)}" data-depends-value="${escapeAttr(field.dependsOn.value)}"`
          : '';
        const hiddenClass = field.dependsOn ? ' hidden' : '';
        if (field.type === 'radio') {
          const options = (field.options || []).map(option => `
            <label class="app-dialog-form-radio">
              <input type="radio" name="${escapeAttr(field.name)}" value="${escapeAttr(option.value)}"${option.value === field.value ? ' checked' : ''} />
              <div class="app-dialog-form-radio-body">
                <span class="app-dialog-form-radio-label">${escapeHtml(option.label || option.value)}</span>
                ${option.hint ? `<span class="app-dialog-form-radio-hint">${escapeHtml(option.hint)}</span>` : ''}
              </div>
            </label>
          `).join('');
          return `
            <fieldset class="app-dialog-form-group${hiddenClass}"${dependsAttrs}>
              <legend class="app-dialog-form-label">${escapeHtml(field.label || field.name)}</legend>
              ${hint}
              <div class="app-dialog-form-radios">${options}</div>
            </fieldset>
          `;
        }
        if (field.type === 'checkbox-group') {
          const selected = new Set(Array.isArray(field.value) ? field.value : []);
          const options = (field.options || []).map(option => `
            <label class="app-dialog-form-checkbox">
              <input type="checkbox" data-checkbox-group="true" name="${escapeAttr(field.name)}" value="${escapeAttr(option.value)}"${selected.has(option.value) ? ' checked' : ''} />
              <div class="app-dialog-form-checkbox-body">
                <span class="app-dialog-form-checkbox-label">${escapeHtml(option.label || option.value)}</span>
                ${option.hint ? `<span class="app-dialog-form-checkbox-hint">${escapeHtml(option.hint)}</span>` : ''}
              </div>
            </label>
          `).join('');
          const empty = (field.options || []).length === 0
            ? `<p class="app-dialog-form-empty">${escapeHtml(field.emptyLabel || t('admin.error.noNamespacesAvailable'))}</p>`
            : '';
          return `
            <fieldset class="app-dialog-form-group${hiddenClass}"${dependsAttrs}>
              <legend class="app-dialog-form-label">${escapeHtml(field.label || field.name)}</legend>
              ${hint}
              <div class="app-dialog-form-checkboxes">${empty || options}</div>
            </fieldset>
          `;
        }
        const value = field.value ?? '';
        if (field.type === 'textarea') {
          return `
            <label class="app-dialog-form-group${hiddenClass}"${dependsAttrs}>
              <span class="app-dialog-form-label">${escapeHtml(field.label || field.name)}</span>
              ${hint}
              <textarea class="app-dialog-field textarea" name="${escapeAttr(field.name)}" rows="${field.rows || 3}" spellcheck="false">${escapeHtml(value)}</textarea>
            </label>
          `;
        }
        return `
          <label class="app-dialog-form-group${hiddenClass}"${dependsAttrs}>
            <span class="app-dialog-form-label">${escapeHtml(field.label || field.name)}</span>
            ${hint}
            <input type="text" class="app-dialog-field" name="${escapeAttr(field.name)}" value="${escapeAttr(value)}" autocomplete="off" />
          </label>
        `;
      }

      function showAppFormDialog({
        title,
        message = '',
        fields = [],
        confirmLabel,
        cancelLabel
      } = {}) {
        const backdrop = $('appDialogBackdrop');
        const dialogEl = backdrop?.querySelector('.app-dialog');
        const titleEl = $('appDialogTitle');
        const messageEl = $('appDialogMessage');
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        const choicesEl = $('appDialogChoices');
        const formEl = $('appDialogForm');
        const confirmBtn = $('appDialogConfirm');
        const cancelBtn = $('appDialogCancel');
        if (!backdrop || !titleEl || !confirmBtn || !cancelBtn || !formEl) {
          return Promise.resolve(null);
        }

        titleEl.textContent = title || '';
        if (messageEl) {
          messageEl.textContent = message || '';
          messageEl.hidden = !message;
        }

        inputEl?.classList.add('hidden');
        textareaEl?.classList.add('hidden');
        choicesEl?.classList.add('hidden');
        dialogEl?.classList.add('has-form');
        formEl.classList.remove('hidden');
        formEl.innerHTML = fields.map(renderAppFormDialogField).join('');

        const syncDependencies = () => syncAppFormDialogDependencies(formEl);
        formEl.querySelectorAll('input[type="radio"]').forEach(radio => {
          radio.addEventListener('change', syncDependencies);
        });
        syncDependencies();

        confirmBtn.textContent = confirmLabel || t('dialog.confirm');
        cancelBtn.textContent = cancelLabel || t('dialog.cancel');
        confirmBtn.classList.remove('danger');
        confirmBtn.disabled = false;

        backdrop.classList.add('open');
        backdrop.setAttribute('aria-hidden', 'false');

        return new Promise(resolve => {
          dialogResolver = resolve;
          const firstField = formEl.querySelector('.app-dialog-form-group:not(.hidden) input, .app-dialog-form-group:not(.hidden) textarea');
          requestAnimationFrame(() => {
            if (firstField) {
              firstField.focus();
              firstField.select?.();
            } else {
              confirmBtn.focus();
            }
          });
        });
      }

      function showAppChoiceDialog({
        title,
        message = '',
        options = [],
        confirmLabel,
        cancelLabel
      } = {}) {
        const backdrop = $('appDialogBackdrop');
        const dialogEl = backdrop?.querySelector('.app-dialog');
        const titleEl = $('appDialogTitle');
        const messageEl = $('appDialogMessage');
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        const choicesEl = $('appDialogChoices');
        const confirmBtn = $('appDialogConfirm');
        const cancelBtn = $('appDialogCancel');
        if (!backdrop || !titleEl || !confirmBtn || !cancelBtn || !choicesEl) {
          return Promise.resolve(null);
        }

        titleEl.textContent = title || '';
        if (messageEl) {
          messageEl.textContent = message || '';
          messageEl.hidden = !message;
        }

        inputEl?.classList.add('hidden');
        textareaEl?.classList.add('hidden');
        dialogEl?.classList.add('has-choices');
        choicesEl.classList.remove('hidden');
        choicesEl.innerHTML = options.map(option => `
          <div class="app-dialog-choice${option.selected ? ' selected' : ''}${option.disabled ? ' disabled' : ''}" data-value="${escapeAttr(option.value)}" role="button" tabindex="${option.disabled ? '-1' : '0'}">
            <input type="radio" name="appDialogChoice" value="${escapeAttr(option.value)}"${option.selected ? ' checked' : ''}${option.disabled ? ' disabled' : ''} />
            <div class="app-dialog-choice-body">
              <div class="app-dialog-choice-label">${escapeHtml(option.label || option.value)}</div>
              ${option.hint ? `<div class="app-dialog-choice-hint">${escapeHtml(option.hint)}</div>` : ''}
              ${option.badge ? `<span class="app-dialog-choice-badge">${escapeHtml(option.badge)}</span>` : ''}
            </div>
          </div>
        `).join('');

        choicesEl.querySelectorAll('.app-dialog-choice').forEach(choiceEl => {
          choiceEl.addEventListener('click', event => {
            if (event.target.tagName === 'INPUT') return;
            selectAppDialogChoice(choicesEl, choiceEl, confirmBtn);
          });
          choiceEl.querySelector('input[type="radio"]')?.addEventListener('change', () => {
            selectAppDialogChoice(choicesEl, choiceEl, confirmBtn);
          });
          choiceEl.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              selectAppDialogChoice(choicesEl, choiceEl, confirmBtn);
            }
          });
        });

        confirmBtn.textContent = confirmLabel || t('dialog.confirm');
        cancelBtn.textContent = cancelLabel || t('dialog.cancel');
        confirmBtn.classList.remove('danger');
        confirmBtn.disabled = !options.some(option => option.selected && !option.disabled);

        backdrop.classList.add('open');
        backdrop.setAttribute('aria-hidden', 'false');

        return new Promise(resolve => {
          dialogResolver = resolve;
          const selectedEl = choicesEl.querySelector('.app-dialog-choice.selected:not(.disabled) input[type="radio"]');
          requestAnimationFrame(() => (selectedEl || confirmBtn).focus?.());
        });
      }

      function initAppDialog() {
        const backdrop = $('appDialogBackdrop');
        const confirmBtn = $('appDialogConfirm');
        const cancelBtn = $('appDialogCancel');
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        if (!backdrop) return;

        confirmBtn?.addEventListener('click', confirmAppDialog);
        cancelBtn?.addEventListener('click', cancelAppDialog);
        inputEl?.addEventListener('keydown', event => {
          if (event.key === 'Enter') {
            event.preventDefault();
            confirmAppDialog();
          }
        });
        textareaEl?.addEventListener('keydown', event => {
          if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
            event.preventDefault();
            confirmAppDialog();
          }
        });
        document.addEventListener('keydown', event => {
          if (!backdrop.classList.contains('open')) return;
          if (event.key === 'Escape') {
            event.preventDefault();
            cancelAppDialog();
          }
        });
      }

      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAppDialog);
      } else {
        initAppDialog();
      }
