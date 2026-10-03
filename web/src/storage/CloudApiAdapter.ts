import { Match, Tournament } from '../domain/models';

const DEFAULT_API_BASE = 'https://cricleagueapi.nrkmart.in';

export interface CloudSession {
  token: string;
  userId: string;
  email: string;
  name: string;
}

export interface LiveShareResult {
  matchId: string;
  spectatorToken: string;
  tokenVersion?: number;
  expiresInSeconds: number;
  shareUrl: string;
}

export class CloudApiAdapter {
  private static getApiBase(): string {
    return (import.meta as any).env?.VITE_API_BASE_URL || DEFAULT_API_BASE;
  }

  public static getSession(): CloudSession | null {
    try {
      const raw = localStorage.getItem('cric_cloud_session');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  public static saveSession(session: CloudSession | null): void {
    if (session) {
      localStorage.setItem('cric_cloud_session', JSON.stringify(session));
    } else {
      localStorage.removeItem('cric_cloud_session');
    }
  }

  public static async login(email: string, pass: string): Promise<CloudSession> {
    const res = await fetch(`${this.getApiBase()}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password: pass })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Login failed');
    const session: CloudSession = {
      token: data.token,
      userId: data.userId || data.user?.id || '',
      email: data.email || data.user?.email || email,
      name: data.name || data.user?.name || 'User'
    };
    this.saveSession(session);
    return session;
  }

  public static async register(email: string, pass: string, name: string): Promise<CloudSession> {
    const res = await fetch(`${this.getApiBase()}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password: pass, name })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Registration failed');
    const session: CloudSession = {
      token: data.token,
      userId: data.userId || data.user?.id || '',
      email: data.email || data.user?.email || email,
      name
    };
    this.saveSession(session);
    return session;
  }

  public static async fetchActiveSeriesSnapshot(): Promise<{ series: Tournament; activeMatch?: Match } | null> {
    const session = this.getSession();
    const headers: Record<string, string> = { 'Accept': 'application/json' };
    if (session?.token) headers['Authorization'] = `Bearer ${session.token}`;

    try {
      const res = await fetch(`${this.getApiBase()}/api/series/active/snapshot`, { headers });
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null;
    }
  }

  public static async syncMatchToCloud(match: Match): Promise<{ match: Match; revision: number }> {
    const session = this.getSession();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Client-Platform': 'web'
    };
    if (session?.token) headers['Authorization'] = `Bearer ${session.token}`;

    const payload = {
      expectedRevision: match.revision || 0,
      match: {
        ...match,
        revision: (match.revision || 0) + 1,
        lastWriterPlatform: 'web',
        updatedAt: new Date().toISOString()
      }
    };

    const res = await fetch(`${this.getApiBase()}/api/matches/${match.id}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (res.status === 409) {
      throw new Error('REVISION_CONFLICT');
    }
    if (!res.ok) throw new Error(data.error || 'Sync failed');
    return {
      match: data.match || payload.match,
      revision: data.revision || payload.match.revision
    };
  }

  public static async createLiveShareLink(matchId: string, ttlMinutes: number = 360): Promise<LiveShareResult> {
    const session = this.getSession();
    if (!session?.token) throw new Error('Sign in required for live share');

    const res = await fetch(`${this.getApiBase()}/api/matches/${matchId}/share`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${session.token}`
      },
      body: JSON.stringify({ ttlMinutes })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Unable to generate live share link');

    const baseUrl = 'https://cricleague.nrkmart.in';
    const shareUrl = `${baseUrl}/?matchId=${matchId}&st=${encodeURIComponent(data.spectatorToken)}`;

    return {
      matchId,
      spectatorToken: data.spectatorToken,
      tokenVersion: data.tokenVersion,
      expiresInSeconds: data.expiresInSeconds || ttlMinutes * 60,
      shareUrl
    };
  }

  public static async revokeLiveShareLink(matchId: string): Promise<boolean> {
    const session = this.getSession();
    if (!session?.token) throw new Error('Sign in required');

    const res = await fetch(`${this.getApiBase()}/api/matches/${matchId}/share`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${session.token}` }
    });

    if (!res.ok) throw new Error('Failed to revoke live share');
    return true;
  }
}
