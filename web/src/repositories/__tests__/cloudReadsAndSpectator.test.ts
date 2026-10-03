import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import {
  CloudApiAdapter,
  UnauthorizedError,
  ForbiddenError,
  ConflictError,
  ExpiredTokenError
} from '../../storage/CloudApiAdapter';
import { StorageAdapter } from '../../storage/storageAdapter';

const storageMap = new Map<string, string>();
const mockStorage = {
  getItem: (key: string) => storageMap.get(key) || null,
  setItem: (key: string, value: string) => storageMap.set(key, value),
  removeItem: (key: string) => storageMap.delete(key),
  clear: () => storageMap.clear(),
  length: 0,
  key: () => null
};

if (typeof globalThis.localStorage === 'undefined') {
  (globalThis as any).localStorage = mockStorage;
}

describe('Cloud Reads, Spectator Token & HTTP Error Handling Tests', () => {
  beforeEach(() => {
    storageMap.clear();
    StorageAdapter.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches match snapshot for owner with Authorization header', async () => {
    CloudApiAdapter.saveSession({
      token: 'owner_jwt_token',
      userId: 'user_123',
      email: 'owner@cricscore.in',
      name: 'Match Owner'
    });

    const mockMatch = { id: 'm_101', status: 'LIVE', totalRuns: 45, totalWickets: 2 };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => mockMatch
    } as Response);

    const result = await CloudApiAdapter.fetchMatch('m_101');

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/matches/m_101'),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer owner_jwt_token'
        })
      })
    );
    expect(result.id).toBe('m_101');
    expect(result.totalRuns).toBe(45);
  });

  it('fetches live match in Spectator Mode with ?st=token and no Authorization header', async () => {
    const mockMatch = { id: 'm_101', status: 'LIVE', totalRuns: 100, totalWickets: 3 };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => mockMatch
    } as Response);

    const result = await CloudApiAdapter.fetchMatch('m_101', 'spectator_token_abc');

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/matches/m_101?st=spectator_token_abc'),
      expect.objectContaining({
        headers: {}
      })
    );
    expect(result.totalRuns).toBe(100);
  });

  it('creates a spectator share token via POST /matches/{id}/share-token', async () => {
    CloudApiAdapter.saveSession({
      token: 'owner_jwt_token',
      userId: 'user_123',
      email: 'owner@cricscore.in',
      name: 'Match Owner'
    });

    const mockShareResponse = {
      spectatorToken: 'st_xyz_789',
      expiresAtMillis: Date.now() + 60 * 60 * 1000
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => mockShareResponse
    } as Response);

    const res = await CloudApiAdapter.createShareToken('m_101', 60);

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/matches/m_101/share-token'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer owner_jwt_token',
          'Content-Type': 'application/json'
        }),
        body: JSON.stringify({ ttlMinutes: 60 })
      })
    );
    expect(res.spectatorToken).toBe('st_xyz_789');
    expect(res.shareUrl).toContain('st=st_xyz_789');
  });

  it('revokes spectator share token via POST /matches/{id}/revoke-share', async () => {
    CloudApiAdapter.saveSession({
      token: 'owner_jwt_token',
      userId: 'user_123',
      email: 'owner@cricscore.in',
      name: 'Match Owner'
    });

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ success: true })
    } as Response);

    const res = await CloudApiAdapter.revokeShareToken('m_101');

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/matches/m_101/revoke-share'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer owner_jwt_token'
        })
      })
    );
    expect(res.success).toBe(true);
  });

  describe('HTTP Error Status Handling', () => {
    it('throws UnauthorizedError on 401', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({ error: 'Invalid authentication token' })
      } as Response);

      await expect(CloudApiAdapter.fetchOwnerMatches()).rejects.toThrow(UnauthorizedError);
    });

    it('throws ForbiddenError on 403', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: async () => ({ error: 'Forbidden' })
      } as Response);

      await expect(CloudApiAdapter.fetchMatch('m_private')).rejects.toThrow(ForbiddenError);
    });

    it('throws ConflictError on 409 STALE_REVISION', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: async () => ({ error: 'STALE_REVISION' })
      } as Response);

      await expect(CloudApiAdapter.fetchMatch('m_101')).rejects.toThrow(ConflictError);
    });

    it('throws ExpiredTokenError on 410 (Spectator Token Expired/Revoked)', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 410,
        json: async () => ({ error: 'Spectator token expired or revoked' })
      } as Response);

      await expect(CloudApiAdapter.fetchMatch('m_101', 'expired_st')).rejects.toThrow(ExpiredTokenError);
    });
  });
});
