import { describe, it, expect, beforeEach } from 'vitest';
import { LocalAuthRepository } from '../implementations/LocalAuthRepository';
import { StorageAdapter } from '../../storage/storageAdapter';

describe('Authentication Layer Unit Tests', () => {
  let authRepo: LocalAuthRepository;

  beforeEach(() => {
    StorageAdapter.clear();
    authRepo = new LocalAuthRepository();
  });

  it('defaults to guest session on initial load', async () => {
    const user = await authRepo.getCurrentUser();
    expect(user.isGuest).toBe(true);
    expect(user.name).toBe('Guest Scorer');
  });

  it('registers a new user and creates an active session', async () => {
    const user = await authRepo.register('Virat Kohli', 'virat@cricscore.in', 'password123');

    expect(user.isGuest).toBe(false);
    expect(user.name).toBe('Virat Kohli');
    expect(user.email).toBe('virat@cricscore.in');
    expect(user.token).toBeDefined();

    const current = await authRepo.getCurrentUser();
    expect(current.id).toBe(user.id);
  });

  it('authenticates valid credentials upon login', async () => {
    await authRepo.register('Rohit Sharma', 'rohit@cricscore.in', 'hitman45');

    // Simulate new session/repo instance
    const newRepo = new LocalAuthRepository();
    const loggedIn = await newRepo.login('rohit@cricscore.in', 'hitman45');

    expect(loggedIn.name).toBe('Rohit Sharma');
    expect(loggedIn.isGuest).toBe(false);
  });

  it('rejects invalid password attempts', async () => {
    await authRepo.register('Bumrah', 'boom@cricscore.in', 'yorker99');

    await expect(authRepo.login('boom@cricscore.in', 'wrongpass')).rejects.toThrow('Invalid email or password.');
  });

  it('restores user session across page reloads', async () => {
    await authRepo.register('KL Rahul', 'kl@cricscore.in', 'kl123');

    // Re-restore session
    const restoredRepo = new LocalAuthRepository();
    const restored = await restoredRepo.restoreSession();

    expect(restored.isGuest).toBe(false);
    expect(restored.name).toBe('KL Rahul');
  });

  it('clears active user session upon logout and reverts to guest mode', async () => {
    await authRepo.register('Hardik Pandya', 'hardik@cricscore.in', 'hp33');
    expect((await authRepo.getCurrentUser()).isGuest).toBe(false);

    await authRepo.logout();
    const current = await authRepo.getCurrentUser();

    expect(current.isGuest).toBe(true);
    expect(current.name).toBe('Guest Scorer');
  });

  it('provides Authorization header for authenticated users and Guest header for guests', async () => {
    const guestHeaders = await authRepo.getAuthHeaders();
    expect(guestHeaders['X-Guest-Mode']).toBe('true');

    await authRepo.register('Gill', 'shubman@cricscore.in', 'gill77');
    const authHeaders = await authRepo.getAuthHeaders();

    expect(authHeaders['Authorization']).toContain('Bearer jwt_token_');
  });
});
