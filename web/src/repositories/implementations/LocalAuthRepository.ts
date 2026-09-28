import { AuthRepository, UserSession } from '../interfaces/AuthRepository';
import { StorageAdapter } from '../../storage/storageAdapter';

const SESSION_KEY = 'cricscore_v2_user_session';
const USERS_KEY = 'cricscore_v2_registered_users';

export class LocalAuthRepository implements AuthRepository {
  private currentSession: UserSession | null = null;

  constructor() {
    this.restoreSession();
  }

  public async restoreSession(): Promise<UserSession> {
    try {
      const raw = StorageAdapter.getItem(SESSION_KEY);
      if (raw) {
        this.currentSession = JSON.parse(raw);
      } else {
        this.currentSession = this.createGuestSession();
      }
    } catch (e) {
      this.currentSession = this.createGuestSession();
    }
    return this.currentSession || this.createGuestSession();
  }

  public async getCurrentUser(): Promise<UserSession> {
    if (!this.currentSession) {
      return this.restoreSession();
    }
    return this.currentSession;
  }

  public async login(email: string, pass: string): Promise<UserSession> {
    if (!email.trim() || !pass) {
      throw new Error('Email and password are required.');
    }

    const registeredUsers = this.getRegisteredUsers();
    const found = registeredUsers.find(u => u.email.toLowerCase() === email.trim().toLowerCase());

    if (!found || found.pass !== pass) {
      throw new Error('Invalid email or password.');
    }

    const session: UserSession = {
      id: found.id,
      name: found.name,
      email: found.email,
      isGuest: false,
      token: 'jwt_token_' + found.id + '_' + Date.now(),
    };

    this.saveSession(session);
    return session;
  }

  public async register(name: string, email: string, pass: string): Promise<UserSession> {
    if (!name.trim() || !email.trim() || !pass) {
      throw new Error('Name, email, and password are required.');
    }

    const registeredUsers = this.getRegisteredUsers();
    if (registeredUsers.some(u => u.email.toLowerCase() === email.trim().toLowerCase())) {
      throw new Error('An account with this email already exists.');
    }

    const newUserId = 'user_' + Date.now();
    const newUser = {
      id: newUserId,
      name: name.trim(),
      email: email.trim().toLowerCase(),
      pass,
    };

    registeredUsers.push(newUser);
    this.saveRegisteredUsers(registeredUsers);

    const session: UserSession = {
      id: newUserId,
      name: newUser.name,
      email: newUser.email,
      isGuest: false,
      token: 'jwt_token_' + newUserId + '_' + Date.now(),
    };

    this.saveSession(session);
    return session;
  }

  public async continueAsGuest(): Promise<UserSession> {
    const session = this.createGuestSession();
    this.saveSession(session);
    return session;
  }

  public async logout(): Promise<void> {
    const guestSession = this.createGuestSession();
    this.saveSession(guestSession);
  }

  public async getAuthHeaders(): Promise<Record<string, string>> {
    const user = await this.getCurrentUser();
    if (user.token && !user.isGuest) {
      return {
        Authorization: `Bearer ${user.token}`,
      };
    }
    return {
      'X-Guest-Mode': 'true',
    };
  }

  private createGuestSession(): UserSession {
    return {
      id: 'guest_user',
      name: 'Guest Scorer',
      isGuest: true,
    };
  }

  private saveSession(session: UserSession): void {
    this.currentSession = session;
    try {
      StorageAdapter.setItem(SESSION_KEY, JSON.stringify(session));
    } catch (e) {
      console.error('Failed to save auth session:', e);
    }
  }

  private getRegisteredUsers(): Array<{ id: string; name: string; email: string; pass: string }> {
    try {
      const raw = StorageAdapter.getItem(USERS_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  private saveRegisteredUsers(users: Array<{ id: string; name: string; email: string; pass: string }>): void {
    try {
      StorageAdapter.setItem(USERS_KEY, JSON.stringify(users));
    } catch (e) {
      console.error('Failed to save users store:', e);
    }
  }
}
