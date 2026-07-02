      const I18N = {
        locale: 'en',
        catalogs: {}
      };

      function registerLocale(locale, catalog) {
        I18N.catalogs[locale] = { ...(I18N.catalogs[locale] || {}), ...catalog };
      }

      function t(key, params = {}) {
        const catalog = I18N.catalogs[I18N.locale] || I18N.catalogs.en || {};
        let text = catalog[key] ?? I18N.catalogs.en?.[key] ?? key;
        Object.entries(params).forEach(([name, value]) => {
          text = text.replace(new RegExp(`\\{${name}\\}`, 'g'), String(value ?? ''));
        });
        return text;
      }

      function detectLocale() {
        try {
          const saved = localStorage.getItem('flowfoundry-locale');
          if (saved && I18N.catalogs[saved]) return saved;
        } catch (ignored) {
          /* ignore storage errors */
        }
        const language = (navigator.language || 'en').toLowerCase();
        return language.startsWith('zh') ? 'zh' : 'en';
      }

      function applyI18n(root = document) {
        root.querySelectorAll('[data-i18n]').forEach(el => {
          el.textContent = t(el.getAttribute('data-i18n'));
        });
        root.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
          el.placeholder = t(el.getAttribute('data-i18n-placeholder'));
        });
        root.querySelectorAll('[data-i18n-title]').forEach(el => {
          el.title = t(el.getAttribute('data-i18n-title'));
        });
        root.querySelectorAll('[data-i18n-aria-label]').forEach(el => {
          el.setAttribute('aria-label', t(el.getAttribute('data-i18n-aria-label')));
        });
        document.title = t('app.title');
        const localeSelect = $('localeSelect');
        if (localeSelect) localeSelect.value = I18N.locale;
      }

      function setLocale(locale) {
        if (!I18N.catalogs[locale]) locale = 'en';
        I18N.locale = locale;
        try {
          localStorage.setItem('flowfoundry-locale', locale);
        } catch (ignored) {
          /* ignore storage errors */
        }
        document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en';
        applyI18n();
        if (typeof renderAll === 'function') renderAll();
      }

      function initI18n() {
        I18N.locale = detectLocale();
        document.documentElement.lang = I18N.locale === 'zh' ? 'zh-CN' : 'en';
        applyI18n();
      }
