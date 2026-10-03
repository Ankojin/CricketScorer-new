import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import {
  CloudApiAdapter,
  UnauthorizedError,
  ForbiddenError,
  ConflictError
} from '../../storage/CloudApiAdapter';
import { Match, MatchStatus, createDefaultGullyRules } from '../../domain/models';

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

describe('Option B: React Web Cloud Scoring Writes & Concurrency Protection Tests', () => {
  let sampleMatch: Match;

  beforeEach(() => {
    storageMap.clear();
    vi.restoreAllMocks();

    sampleMatch = {
      id: 'm_cloud_202',
      tournamentId: 'tour_202',
      teamA: { id: 't_a', name: 'Alpha', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 't_b', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 't_a',
      bowlingTeamId: 't_b',
      totalRuns: 24,
      totalWickets: 1,
      totalBalls: 12,
      wideCount: 1,
      noBallCount: 0,
      byeCount: 0,
      legByeCount: 0,
      ballHistory: [],
      wicketHistory: [],
      oversPerInnings: 5,
      gullyRules: createDefaultGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: [],
      revision: 3,
      dateMillis: Date.now()
    };
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sends POST /matches with X-Client-Platform: web header when creating match', async () => {
    CloudApiAdapter.saveSession({
      token: 'owner_jwt_token',
      userId: 'user_123',
      email: 'owner@cricscore.in',
      name: 'Match Owner'
    });

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ match: sampleMatch })
    } as Response);

    const created = await CloudApiAdapter.createMatch(sampleMatch);

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/matches'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer owner_jwt_token',
          'X-Client-Platform': 'web',
          'Content-Type': 'application/json'
        })
      })
    );
    expect(created.id).toBe('m_cloud_202');
  });

  it('sends PUT /matches/{id} with X-Client-Platform: web header when updating score', async () => {
    CloudApiAdapter.saveSession({
      token: 'owner_jwt_token',
      userId: 'user_123',
      email: 'owner@cricscore.in',
      name: 'Match Owner'
    });

    const updatedMatch = { ...sampleMatch, totalRuns: 30, revision: 4 };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => updatedMatch
    } as Response);

    const result = await CloudApiAdapter.updateMatch(updatedMatch);

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/matches/m_cloud_202'),
      expect.objectContaining({
        method: 'PUT',
        headers: expect.objectContaining({
          Authorization: 'Bearer owner_jwt_token',
          'X-Client-Platform': 'web'
        })
      })
    );
    expect(result.totalRuns).toBe(30);
    expect(result.revision).toBe(4);
  });

  it('catches 409 STALE_REVISION conflict error on concurrent edit and throws ConflictError', async () => {
    CloudApiAdapter.saveSession({
      token: 'owner_jwt_token',
      userId: 'user_123',
      email: 'owner@cricscore.in',
      name: 'Match Owner'
    });

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 409,
      json: async () => ({ error: 'STALE_REVISION' })
    } as Response);

    await expect(CloudApiAdapter.updateMatch(sampleMatch)).rejects.toThrow(ConflictError);
  });

  it('catches 403 Forbidden error when non-owner attempts cloud score write', async () => {
    CloudApiAdapter.saveSession({
      token: 'guest_token',
      userId: 'user_999',
      email: 'nonowner@cricscore.in',
      name: 'Non Owner'
    });

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 403,
      json: async () => ({ error: 'Forbidden: You do not own this match' })
    } as Response);

    await expect(CloudApiAdapter.updateMatch(sampleMatch)).rejects.toThrow(ForbiddenError);
  });
});
