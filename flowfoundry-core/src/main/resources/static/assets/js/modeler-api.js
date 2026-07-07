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

  function platformTenantId() {
    return (
      global.FLOWFOUNDRY_TENANT_ID
      || global.FLOWFOUNDRY_NAMESPACE
      || readStored('flowfoundry.tenantId')
      || readStored('flowfoundry.namespace')
      || ''
    );
  }

  global.configurePlatformApi = function configurePlatformApi(config) {
    if (!config) return;
    if (config.apiBase) {
      apiBase = normalizeApiBase(config.apiBase);
      global.FLOWFOUNDRY_API_BASE = apiBase;
    }
    if (config.apiKey) global.FLOWFOUNDRY_API_KEY = config.apiKey;
    if (config.tenantId) global.setPlatformTenantId(config.tenantId);
    if (config.namespace) global.setPlatformTenantId(config.namespace);
  };

  global.setPlatformTenantId = function setPlatformTenantId(tenantId) {
    const value = tenantId == null ? '' : String(tenantId);
    global.FLOWFOUNDRY_TENANT_ID = value;
    global.FLOWFOUNDRY_NAMESPACE = value;
    try {
      if (global.localStorage) {
        global.localStorage.setItem('flowfoundry.tenantId', value);
        global.localStorage.setItem('flowfoundry.namespace', value);
      }
    } catch (ignored) {
      // ignore storage failures in embedded contexts
    }
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
    const tenantId = platformTenantId();
    if (tenantId) {
      headers['X-Tenant-Id'] = tenantId;
      headers['X-Platform-Namespace'] = tenantId;
    }
    return headers;
  };

  global.platformTenantId = platformTenantId;

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
    if (!platformTenantId() && config?.defaultTenantId) {
      setPlatformTenantId(config.defaultTenantId);
    }
    global.FLOWFOUNDRY_PUBLIC_CONFIG = config;
    return config;
  };
})(window);
