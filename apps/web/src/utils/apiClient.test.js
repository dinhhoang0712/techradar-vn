import { describe, it, expect, beforeEach, vi } from 'vitest';

// apiClient holds module-level state (single-flight refreshPromise), so we
// re-import a fresh copy for every test to keep them isolated.
let apiClient;

const jsonResponse = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
});

beforeEach(async () => {
  vi.resetModules();
  ({ apiClient } = await import('./apiClient.js'));

  localStorage.clear();
  window.alert = vi.fn();
  // Controllable location so the redirect-on-401 path never triggers real navigation.
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { href: '', pathname: '/dashboard' },
  });
  vi.restoreAllMocks();
});

describe('apiClient — happy path', () => {
  it('returns parsed JSON and attaches the Bearer token when present', async () => {
    localStorage.setItem('access_token', 'abc123');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { success: true, data: { id: 1 } }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await apiClient('/user/profile');

    expect(result).toEqual({ success: true, data: { id: 1 } });
    const [url, config] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/user/profile');
    expect(config.headers.Authorization).toBe('Bearer abc123');
    expect(config.headers['Content-Type']).toBe('application/json');
  });

  it('omits the Authorization header when no token is stored', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: 1 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiClient('/status');

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('forwards method and body from options', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, {}));
    vi.stubGlobal('fetch', fetchMock);

    await apiClient('/admin/cms', { method: 'POST', body: JSON.stringify({ title: 'x' }) });

    const config = fetchMock.mock.calls[0][1];
    expect(config.method).toBe('POST');
    expect(config.body).toBe(JSON.stringify({ title: 'x' }));
  });
});

describe('apiClient — error mapping', () => {
  it('maps 503 to SERVER_MAINTENANCE', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(503, {})));
    await expect(apiClient('/radar/top4')).rejects.toMatchObject({
      message: 'SERVER_MAINTENANCE',
      status: 503,
    });
  });

  it('maps a network failure to SERVER_CONNECTION_FAILED', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(apiClient('/radar/top4')).rejects.toMatchObject({
      message: 'SERVER_CONNECTION_FAILED',
      status: 0,
    });
  });

  it('surfaces the backend error message for other non-OK responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(400, { message: 'Email already exists' })));
    await expect(apiClient('/auth/register', { method: 'POST' })).rejects.toMatchObject({
      message: 'Email already exists',
      status: 400,
    });
  });
});

describe('apiClient — session timeout (15 min)', () => {
  it('throws SESSION_TIMEOUT and clears storage without calling fetch', async () => {
    localStorage.setItem('access_token', 'abc');
    localStorage.setItem('login_timestamp', String(Date.now() - 901 * 1000));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient('/user/profile')).rejects.toMatchObject({
      message: 'SESSION_TIMEOUT',
      status: 401,
    });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(localStorage.getItem('access_token')).toBeNull();
  });

  it('does NOT time out when the session is fresh', async () => {
    localStorage.setItem('access_token', 'abc');
    localStorage.setItem('login_timestamp', String(Date.now() - 60 * 1000));
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await apiClient('/user/profile');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('apiClient — refresh-on-401', () => {
  it('refreshes the token once, stores it, and retries the original request', async () => {
    localStorage.setItem('access_token', 'expired');
    localStorage.setItem('refresh_token', 'r1');

    const fetchMock = vi.fn((url) => {
      if (url.endsWith('/auth/refresh')) {
        return Promise.resolve(jsonResponse(200, { access_token: 'new-access', refresh_token: 'r2' }));
      }
      // First protected call 401s; the retry (now carrying the new token) succeeds.
      const token = fetchMock.protectedCalls++ === 0 ? 401 : 200;
      return Promise.resolve(jsonResponse(token, token === 200 ? { data: 'ok' } : {}));
    });
    fetchMock.protectedCalls = 0;
    vi.stubGlobal('fetch', fetchMock);

    const result = await apiClient('/user/profile');

    expect(result).toEqual({ data: 'ok' });
    expect(localStorage.getItem('access_token')).toBe('new-access');
    expect(localStorage.getItem('refresh_token')).toBe('r2');
    const refreshCalls = fetchMock.mock.calls.filter(([u]) => u.endsWith('/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
  });

  it('logs out (clears storage, throws UNAUTHORIZED) when there is no refresh token', async () => {
    localStorage.setItem('access_token', 'expired');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {})));

    await expect(apiClient('/user/profile')).rejects.toMatchObject({
      message: 'UNAUTHORIZED',
      status: 401,
    });
    expect(localStorage.getItem('access_token')).toBeNull();
  });

  it('never attempts a refresh for /auth/* endpoints', async () => {
    localStorage.setItem('refresh_token', 'r1');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(401, {}));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient('/auth/login', { method: 'POST' })).rejects.toMatchObject({ status: 401 });
    expect(fetchMock.mock.calls.some(([u]) => u.endsWith('/auth/refresh'))).toBe(false);
  });

  it('single-flights concurrent 401s into one refresh call', async () => {
    localStorage.setItem('access_token', 'expired');
    localStorage.setItem('refresh_token', 'r1');

    const fetchMock = vi.fn((url) => {
      if (url.endsWith('/auth/refresh')) {
        return Promise.resolve(jsonResponse(200, { access_token: 'new' }));
      }
      // Every protected call before a successful refresh returns 401 the first time.
      const key = url;
      fetchMock.seen[key] = (fetchMock.seen[key] || 0) + 1;
      return Promise.resolve(jsonResponse(fetchMock.seen[key] === 1 ? 401 : 200, { data: key }));
    });
    fetchMock.seen = {};
    vi.stubGlobal('fetch', fetchMock);

    await Promise.all([apiClient('/a'), apiClient('/b'), apiClient('/c')]);

    const refreshCalls = fetchMock.mock.calls.filter(([u]) => u.endsWith('/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
  });
});
