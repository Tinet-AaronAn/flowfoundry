/**
 * FlowFoundry Modeler SDK — 供业务应用页面通过 iframe 嵌入建模器。
 * 同 JAR 部署：脚本由 flowfoundry-core 静态资源提供。
 */
(function initFlowFoundryModelerSdk(global) {
  const MESSAGE_PREFIX = 'flowfoundry:';
  let cachedConfig = null;

  function normalizeBase(base) {
    if (!base) return '';
    return base.endsWith('/') ? base.slice(0, -1) : base;
  }

  async function loadConfig(options) {
    if (options?.config) {
      cachedConfig = options.config;
      return cachedConfig;
    }
    if (cachedConfig) return cachedConfig;
    if (global.loadPlatformPublicConfig) {
      cachedConfig = await global.loadPlatformPublicConfig();
      return cachedConfig;
    }
    const response = await fetch('/api/platform/public-config');
    if (!response.ok) {
      throw new Error(`Failed to load FlowFoundry public config: HTTP ${response.status}`);
    }
    cachedConfig = await response.json();
    return cachedConfig;
  }

  function buildEmbedQuery(options) {
    const params = new URLSearchParams();
    if (options.workflowId) params.set('workflowId', options.workflowId);
    if (options.version) params.set('version', options.version);
    if (options.mode) params.set('mode', options.mode);
    if (options.readonly) params.set('readonly', 'true');
    if (options.locale) params.set('locale', options.locale);
    return params.toString();
  }

  const FlowFoundryModeler = {
    async loadConfig(options) {
      return loadConfig(options || {});
    },

    /**
     * 生成 iframe 嵌入 URL。
     * @param {{ workflowId?: string, version?: string, mode?: 'design'|'runtime', readonly?: boolean, locale?: string, embedPath?: string }} options
     */
    async embedUrl(options) {
      const opts = options || {};
      const config = await loadConfig(opts);
      const modeler = config.modeler || {};
      if (modeler.embedEnabled === false) {
        throw new Error('FlowFoundry modeler embed is disabled by server configuration');
      }
      const embedPath = opts.embedPath || modeler.embedPath || '/modeler/embed.html';
      const params = new URLSearchParams(buildEmbedQuery(opts));
      if (config.staticAssetVersion) {
        params.set('_v', config.staticAssetVersion);
      }
      const query = params.toString();
      return query ? `${embedPath}?${query}` : embedPath;
    },

    /**
     * 在容器元素内挂载 iframe 建模器。
     * @param {string|HTMLElement} container
     * @param {{ workflowId?: string, version?: string, mode?: string, readonly?: boolean, locale?: string, onMessage?: Function }} options
     */
    async mountIframe(container, options) {
      const opts = options || {};
      const target = typeof container === 'string' ? document.querySelector(container) : container;
      if (!target) throw new Error('FlowFoundryModeler.mountIframe: container not found');

      const src = await FlowFoundryModeler.embedUrl(opts);
      const iframe = document.createElement('iframe');
      iframe.src = src;
      iframe.title = opts.title || 'FlowFoundry Workflow Modeler';
      iframe.setAttribute('data-flowfoundry-modeler', 'true');
      iframe.style.width = opts.width || '100%';
      iframe.style.height = opts.height || '100%';
      iframe.style.border = opts.border || '0';
      iframe.allow = 'clipboard-read; clipboard-write';
      target.replaceChildren(iframe);

      if (typeof opts.onMessage === 'function') {
        FlowFoundryModeler.onMessage(opts.onMessage, { targetWindow: iframe.contentWindow });
      }
      return iframe;
    },

    /**
     * 监听建模器 postMessage 事件。
     * @param {(event: MessageEvent, payload: object) => void} handler
     * @param {{ targetWindow?: Window }} options
     */
    onMessage(handler, options) {
      const allowedOrigin = options?.allowedOrigin || global.location?.origin;
      const listener = event => {
        if (allowedOrigin && event.origin !== allowedOrigin) return;
        const data = event.data;
        if (!data || typeof data.type !== 'string' || !data.type.startsWith(MESSAGE_PREFIX)) return;
        handler(event, data);
      };
      global.addEventListener('message', listener);
      return () => global.removeEventListener('message', listener);
    },

    sdkScriptPath(config) {
      const resolved = (config || cachedConfig || {});
      const modeler = resolved.modeler || {};
      const base = modeler.sdkScriptPath || '/assets/js/flowfoundry-modeler-sdk.js';
      const version = resolved.staticAssetVersion;
      if (!version) return base;
      const joiner = base.includes('?') ? '&' : '?';
      return `${base}${joiner}v=${encodeURIComponent(version)}`;
    }
  };

  global.FlowFoundryModeler = FlowFoundryModeler;
})(window);
