import { CloudApiAdapter, CloudSession } from '../../storage/CloudApiAdapter';
import { AuthRepository, UserSession } from '../interfaces/AuthRepository';

const GUEST_SESSION: UserSession = {
  id: 'guest_user',
  name: 'Guest Scorer',
  isGuest: true
};

export class CloudAuthRepository implements AuthRepository {
  private currentSession: UserSession | null = null;

  public async restoreSession(): Promise<UserSession> {
    this.currentSession = this.toUserSession(CloudApiAdapter.getSession()) || GUEST_SESSION;
    return this.currentSession;
  }

  public async getCurrentUser(): Promise<UserSession> {
    return this.currentSession || this.restoreSession();
  }

  public async login(email: string, pass: string): Promise<UserSession> {
    const session = this.toUserSession(await CloudApiAdapter.login(email, pass));
    if (!session) throw new Error('Login response did not include a user session');
    this.currentSession = session;
    return session;
  }

  public async register(name: string, email: string, pass: string): Promise<UserSession> {
    const session = this.toUserSession(await CloudApiAdapter.register(email, pass, name));
    if (!session) throw new Error('Registration response did not include a user session');
    this.currentSession = session;
    return session;
  }

  public async continueAsGuest(): Promise<UserSession> {
    CloudApiAdapter.saveSession(null);
    this.currentSession = GUEST_SESSION;
    return this.currentSession;
  }

  public async logout(): Promise<void> {
    await this.continueAsGuest();
  }

  public async getAuthHeaders(): Promise<Record<string, string>> {
    const user = await this.getCurrentUser();
    return user.token && !user.isGuest ? { Authorization: `Bearer ${user.token}` } : {};
  }

  private toUserSession(session: CloudSession | null): UserSession | null {
    if (!session?.token || !session.userId) return null;
    return {
      id: session.userId,
      name: session.name,
      email: session.email,
      isGuest: false,
      token: session.token
    };
  }
}
