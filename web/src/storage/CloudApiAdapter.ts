import { Match } from '../domain/models';

const DEFAULT_API_BASE = 'https://cricleagueapi.nrkmart.in';

export interface CloudSession {
  token: string;
  userId: string;
  email: string;
  name: string;
}

export class CloudApiError extends Error {
  constructor(public statusCode: number, message: string, public code?: string) {
    super(message);
    this.name = 'CloudApiError';
  }
}

export class UnauthorizedError extends CloudApiError {
  constructor(message = '401 Unauthorized: Invalid or missing authentication token.') {
    super(401, message, 'UNAUTHORIZED');
    this.name = 'UnauthorizedError';
  }
}

export class ForbiddenError extends CloudApiError {
  constructor(message = '403 Forbidden: You do not have permission to access this resource.') {
    super(403, message, 'FORBIDDEN');
    this.name = 'ForbiddenError';
  }
}

export class ConflictError extends CloudApiError {
  constructor(message = '409 Conflict: Stale match revision or concurrent edit detected.') {
    super(409, message, 'STALE_REVISION');
    this.name = 'ConflictError';
  }
}

export class ExpiredTokenError extends CloudApiError {
  constructor(message = '410 Gone: Spectator link has expired or been revoked.') {
    super(410, message, 'SPECTATOR_TOKEN_EXPIRED_OR_REVOKED');
    this.name = 'ExpiredTokenError';
  }
}

export class CloudApiAdapter {
  private static getApiBase(): string {
    return (import.meta as any).env?.VITE_API_BASE_URL || DEFAULT_API_BASE;
  }

  public static getSession(): CloudSession | null {
    try {
      const raw = typeof localStorage !== 'undefined' ? localStorage.getItem('cric_cloud_session') : null;
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  public static saveSession(session: CloudSession | null): void {
    if (typeof localStorage === 'undefined') return;
    if (session) {
      localStorage.setItem('cric_cloud_session', JSON.stringify(session));
    } else {
      localStorage.removeItem('cric_cloud_session');
    }
  }

  public static getAuthHeaders(): Record<string, string> {
    const session = this.getSession();
    if (session && session.token) {
      return { Authorization: `Bearer ${session.token}` };
    }
    return {};
  }

  public static async login(email: string, pass: string): Promise<CloudSession> {
    const res = await fetch(`${this.getApiBase()}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password: pass })
    });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    const session: CloudSession = {
      token: data.token,
      userId: data.user?.userId || '',
      email: data.user?.email || email.trim().toLowerCase(),
      name: data.user?.name || 'User'
    };
    this.saveSession(session);
    return session;
  }

  public static async register(email: string, pass: string, name: string): Promise<CloudSession> {
    const res = await fetch(`${this.getApiBase()}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password: pass, name })
    });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    const session: CloudSession = {
      token: data.token,
      userId: data.user?.userId || '',
      email: data.user?.email || email.trim().toLowerCase(),
      name: data.user?.name || name.trim()
    };
    this.saveSession(session);
    return session;
  }

  public static async fetchOwnerMatches(): Promise<Match[]> {
    const headers = { ...this.getAuthHeaders() };
    const res = await fetch(`${this.getApiBase()}/matches`, { headers });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    return Array.isArray(data) ? data : data.matches || [];
  }

  public static async fetchMatch(matchId: string, spectatorToken?: string | null): Promise<Match> {
    let url = `${this.getApiBase()}/matches/${encodeURIComponent(matchId)}`;
    if (spectatorToken) {
      url += `?st=${encodeURIComponent(spectatorToken)}`;
    }
    const headers = spectatorToken ? {} : this.getAuthHeaders();
    const res = await fetch(url, { headers });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    return data.match || data;
  }

  public static async createShareToken(
    matchId: string,
    ttlMinutes: 15 | 60 | 360 = 60
  ): Promise<{ spectatorToken: string; expiresAtMillis: number; shareUrl: string }> {
    const res = await fetch(`${this.getApiBase()}/matches/${encodeURIComponent(matchId)}/share-token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...this.getAuthHeaders()
      },
      body: JSON.stringify({ ttlMinutes })
    });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    const spectatorToken = data.spectatorToken || data.token || '';
    const origin = typeof window !== 'undefined' ? window.location.origin : 'https://cricscore.in';
    const shareUrl = data.shareUrl || `${origin}?matchId=${encodeURIComponent(matchId)}&st=${encodeURIComponent(spectatorToken)}`;
    return {
      spectatorToken,
      expiresAtMillis: data.expiresAtMillis || Date.now() + ttlMinutes * 60 * 1000,
      shareUrl
    };
  }

  public static async revokeShareToken(matchId: string): Promise<{ success: boolean }> {
    const res = await fetch(`${this.getApiBase()}/matches/${encodeURIComponent(matchId)}/revoke-share`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...this.getAuthHeaders()
      }
    });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    return { success: true };
  }

  public static async createMatch(match: Match): Promise<Match> {
    const res = await fetch(`${this.getApiBase()}/matches`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Client-Platform': 'web',
        ...this.getAuthHeaders()
      },
      body: JSON.stringify(match)
    });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    return data.match || data;
  }

  public static async updateMatch(match: Match): Promise<Match> {
    const res = await fetch(`${this.getApiBase()}/matches/${encodeURIComponent(match.id)}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-Client-Platform': 'web',
        ...this.getAuthHeaders()
      },
      body: JSON.stringify(match)
    });
    const data = await this.readJson(res);
    if (!res.ok) {
      this.handleHttpError(res.status, data);
    }
    return data.match || data;
  }

  private static handleHttpError(status: number, data: any): never {
    const msg = data?.error || data?.message || `HTTP ${status} Request Failed`;
    if (status === 401) throw new UnauthorizedError(msg);
    if (status === 403) throw new ForbiddenError(msg);
    if (status === 409) throw new ConflictError(msg);
    if (status === 410) throw new ExpiredTokenError(msg);
    throw new CloudApiError(status, msg);
  }

  private static async readJson(res: Response): Promise<any> {
    try {
      return await res.json();
    } catch {
      return { error: `Request failed (${res.status})` };
    }
  }
}
