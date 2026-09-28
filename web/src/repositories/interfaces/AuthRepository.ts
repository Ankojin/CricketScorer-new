export interface UserSession {
  id: string;
  name: string;
  email?: string;
  isGuest: boolean;
  token?: string;
}

export interface AuthRepository {
  getCurrentUser(): Promise<UserSession>;
  login(email: string, pass: string): Promise<UserSession>;
  register(name: string, email: string, pass: string): Promise<UserSession>;
  continueAsGuest(): Promise<UserSession>;
  logout(): Promise<void>;
  restoreSession(): Promise<UserSession>;
  getAuthHeaders(): Promise<Record<string, string>>;
}
