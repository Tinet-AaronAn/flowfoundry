      let dialogResolver = null;

      function isDialogInputMode() {
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        return (inputEl && !inputEl.classList.contains('hidden'))
          || (textareaEl && !textareaEl.classList.contains('hidden'));
      }

      function closeAppDialog(result) {
        const backdrop = $('appDialogBackdrop');
        if (!backdrop) return;
        backdrop.classList.remove('open');
        backdrop.setAttribute('aria-hidden', 'true');
        backdrop.querySelector('.app-dialog')?.classList.remove('has-choices');
        $('appDialogInput')?.classList.add('hidden');
        $('appDialogTextarea')?.classList.add('hidden');
        $('appDialogChoices')?.classList.add('hidden');
        const choicesHost = $('appDialogChoices');
        if (choicesHost) choicesHost.innerHTML = '';
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
        textareaClass = ''
      } = {}) {
        const backdrop = $('appDialogBackdrop');
        const titleEl = $('appDialogTitle');
        const messageEl = $('appDialogMessage');
        const inputEl = $('appDialogInput');
        const textareaEl = $('appDialogTextarea');
        const confirmBtn = $('appDialogConfirm');
        const cancelBtn = $('appDialogCancel');
        if (!backdrop || !titleEl || !confirmBtn || !cancelBtn) {
          return Promise.resolve(input === 'none' ? false : null);
        }

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
          textareaEl.className = `app-dialog-field textarea${textareaClass ? ` ${textareaClass}` : ''}`;
          textareaEl.classList.remove('hidden');
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

      function confirmAppDialog() {
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
        backdrop.addEventListener('click', event => {
          if (event.target === backdrop) cancelAppDialog();
        });
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
