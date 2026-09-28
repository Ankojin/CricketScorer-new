import { describe, it, expect, beforeEach } from 'vitest';
import { LocalSyncRepository } from '../implementations/LocalSyncRepository';
import { LocalMatchRepository } from '../implementations/LocalMatchRepository';
import { StorageAdapter } from '../../storage/storageAdapter';
import { Match, MatchStatus, createDefaultGullyRules } from '../../domain/models';

describe('Offline & Synchronization Unit Tests', () => {
  let syncRepo: LocalSyncRepository;
  let matchRepo: LocalMatchRepository;

  beforeEach(() => {
    StorageAdapter.clear();
    syncRepo = new LocalSyncRepository();
    matchRepo = new LocalMatchRepository();
  });

  it('allows offline scoring and persists state locally without data loss', async () => {
    const testMatch: Match = {
      id: 'm_off_1',
      tournamentId: 'tour_1',
      teamA: { id: 'a', name: 'Alpha', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 'b', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'a',
      bowlingTeamId: 'b',
      totalRuns: 24,
      totalWickets: 1,
      totalBalls: 12,
      wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [], wicketHistory: [],
      oversPerInnings: 20,
      gullyRules: createDefaultGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: [],
      dateMillis: Date.now()
    };

    // Save offline
    await matchRepo.saveMatch(testMatch);

    // Retrieve offline
    const retrieved = await matchRepo.getMatchById('m_off_1');
    expect(retrieved).not.toBeNull();
    expect(retrieved?.totalRuns).toBe(24);
    expect(retrieved?.totalWickets).toBe(1);
  });

  it('restores active match state seamlessly across application reloads', async () => {
    const activeMatch: Match = {
      id: 'm_reload_1',
      tournamentId: 'tour_1',
      teamA: { id: 'a', name: 'Alpha', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 'b', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'a',
      bowlingTeamId: 'b',
      totalRuns: 42,
      totalWickets: 2,
      totalBalls: 18,
      wideCount: 1, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [], wicketHistory: [],
      oversPerInnings: 20,
      gullyRules: createDefaultGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: [],
      dateMillis: Date.now()
    };

    await matchRepo.saveMatch(activeMatch);
    await matchRepo.setActiveMatchId('m_reload_1');

    // Simulate application reload (new repository instance reading storage)
    const newMatchRepo = new LocalMatchRepository();
    const reloadedMatch = await newMatchRepo.getActiveMatch();

    expect(reloadedMatch).not.toBeNull();
    expect(reloadedMatch?.id).toBe('m_reload_1');
    expect(reloadedMatch?.totalRuns).toBe(42);
  });

  it('queues pending sync items when broadcast is attempted while offline or failed', async () => {
    await syncRepo.startSync();

    const offlineMatch: Match = {
      id: 'm_queue_1',
      tournamentId: 'tour_1',
      teamA: { id: 'a', name: 'Alpha', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 'b', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'a',
      bowlingTeamId: 'b',
      totalRuns: 15,
      totalWickets: 0,
      totalBalls: 6,
      wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [], wicketHistory: [],
      oversPerInnings: 20,
      gullyRules: createDefaultGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: [],
      dateMillis: Date.now()
    };

    // Enqueue pending item
    syncRepo.enqueuePendingSync(offlineMatch);

    const pending = syncRepo.getPendingQueue();
    expect(pending.length).toBe(1);
    expect(pending[0].id).toBe('m_queue_1');
  });

  it('flushes pending sync queue upon reconnection', async () => {
    await syncRepo.startSync();

    const pendingMatch: Match = {
      id: 'm_flush_1',
      tournamentId: 'tour_1',
      teamA: { id: 'a', name: 'Alpha', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 'b', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'a',
      bowlingTeamId: 'b',
      totalRuns: 30, totalWickets: 1, totalBalls: 12,
      wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [], wicketHistory: [],
      oversPerInnings: 20,
      gullyRules: createDefaultGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: [],
      dateMillis: Date.now()
    };

    syncRepo.enqueuePendingSync(pendingMatch);
    expect(syncRepo.getPendingQueue().length).toBe(1);

    // Flush retry
    const syncedCount = await syncRepo.retryPendingSync();
    expect(syncedCount).toBe(1);
    expect(syncRepo.getPendingQueue().length).toBe(0);
  });

  it('resolves conflicts by selecting match state with longer ballHistory length', () => {
    const localMatch: Match = {
      id: 'm_conf_1',
      teamA: { id: 'a', name: 'A', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 'b', name: 'B', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE, currentInnings: 1, battingTeamId: 'a', bowlingTeamId: 'b',
      totalRuns: 10, totalWickets: 0, totalBalls: 3,
      wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [
        { runs: 1, extrasType: 'NONE' as any, extraRuns: 0, wicketType: 'NONE' as any, strikerId: null, nonStrikerId: null, bowlerId: null, fielderId: null, isLegalBall: true, outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false },
        { runs: 4, extrasType: 'NONE' as any, extraRuns: 0, wicketType: 'NONE' as any, strikerId: null, nonStrikerId: null, bowlerId: null, fielderId: null, isLegalBall: true, outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false }
      ],
      wicketHistory: [], oversPerInnings: 20, gullyRules: createDefaultGullyRules(), isSecondInningsStarted: false, battingOrder: [], dateMillis: 1000
    };

    const remoteMatch: Match = {
      ...localMatch,
      totalRuns: 16,
      totalBalls: 4,
      ballHistory: [
        ...localMatch.ballHistory,
        { runs: 6, extrasType: 'NONE' as any, extraRuns: 0, wicketType: 'NONE' as any, strikerId: null, nonStrikerId: null, bowlerId: null, fielderId: null, isLegalBall: true, outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false }
      ],
      dateMillis: 2000
    };

    const resolved = syncRepo.resolveConflict(localMatch, remoteMatch);
    expect(resolved.ballHistory.length).toBe(3);
    expect(resolved.totalRuns).toBe(16);
  });
});
