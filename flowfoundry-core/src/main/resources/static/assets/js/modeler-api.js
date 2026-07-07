(function initPlatformApi(global) {
  function readStored(key) {
    try {
      return global.localStorage ? global.localStorage.getItem(key) : null;
    } catch (ignored) {
      return null;
    }
  }

  function normalizeApiBase(base) {
    const value = (base || '/api').trim();
    return value.endsWith('/') ? value.slice(0, -1) : value;
  }

  let apiBase = normalizeApiBase(global.FLOWFOUNDRY_API_BASE || '/api');

  function platformApiKey() {
    return global.FLOWFOUNDRY_API_KEY || readStored('flowfoundry.apiKey') || '';
  }

  function platformNamespace() {
    return global.FLOWFOUNDRY_NAMESPACE || readStored('flowfoundry.namespace') || '';
  }

  global.configurePlatformApi = function configurePlatformApi(config) {
    if (!config) return;
    if (config.apiBase) {
      apiBase = normalizeApiBase(config.apiBase);
      global.FLOWFOUNDRY_API_BASE = apiBase;
    }
    if (config.apiKey) global.FLOWFOUNDRY_API_KEY = config.apiKey;
    if (config.namespace) global.FLOWFOUNDRY_NAMESPACE = config.namespace;
  };

  global.platformApiBase = function platformApiBase() {
    return apiBase;
  };

  global.platformApiUrl = function platformApiUrl(path) {
    if (!path) return apiBase;
    if (/^https?:\/\//i.test(path)) return path;
    let suffix = String(path);
    if (suffix.startsWith(apiBase + '/')) return suffix;
    if (suffix.startsWith('/api/')) suffix = suffix.slice(4);
    if (!suffix.startsWith('/')) suffix = `/${suffix}`;
    return `${apiBase}${suffix}`;
  };

  global.platformApiHeaders = function platformApiHeaders(extra) {
    const headers = { ...(extra || {}) };
    const apiKey = platformApiKey();
    if (apiKey) headers['X-Api-Key'] = apiKey;
    const namespace = platformNamespace();
    if (namespace) headers['X-Platform-Namespace'] = namespace;
    return headers;
  };

  global.platformFetch = async function platformFetch(path, options) {
    const response = await fetch(platformApiUrl(path), {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...platformApiHeaders(),
        ...(options?.headers || {})
      }
    });
    return response;
  };

  global.loadPlatformPublicConfig = async function loadPlatformPublicConfig() {
    const response = await fetch(platformApiUrl('/platform/public-config'));
    if (!response.ok) {
      throw new Error(`Failed to load platform config: HTTP ${response.status}`);
    }
    const config = await response.json();
    if (config?.modeler?.apiBase) {
      configurePlatformApi({ apiBase: config.modeler.apiBase });
    }
    global.FLOWFOUNDRY_PUBLIC_CONFIG = config;
    return config;
  };
})(window);
