import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserSession } from '../repositories/interfaces/AuthRepository';
import { authRepository } from '../repositories';

interface AuthContextType {
  user: UserSession | null;
  isLoading: boolean;
  login: (email: string, pass: string) => Promise<void>;
  register: (name: string, email: string, pass: string) => Promise<void>;
  continueAsGuest: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    authRepository.restoreSession().then(restored => {
      setUser(restored);
      setIsLoading(false);
    });
  }, []);

  const login = async (email: string, pass: string) => {
    const session = await authRepository.login(email, pass);
    setUser(session);
  };

  const register = async (name: string, email: string, pass: string) => {
    const session = await authRepository.register(name, email, pass);
    setUser(session);
  };

  const continueAsGuest = async () => {
    const session = await authRepository.continueAsGuest();
    setUser(session);
  };

  const logout = async () => {
    await authRepository.logout();
    const guestSession = await authRepository.getCurrentUser();
    setUser(guestSession);
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, continueAsGuest, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
