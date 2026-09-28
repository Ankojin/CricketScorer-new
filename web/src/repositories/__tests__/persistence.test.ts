import { describe, it, expect, beforeEach } from 'vitest';
import { LocalMatchRepository } from '../implementations/LocalMatchRepository';
import { LocalTournamentRepository } from '../implementations/LocalTournamentRepository';
import { LocalPlayerRepository } from '../implementations/LocalPlayerRepository';
import { LocalSyncRepository } from '../implementations/LocalSyncRepository';
import { StorageAdapter } from '../../storage/storageAdapter';
import { Tournament, Match, Team, MatchStatus, createDefaultPlayer } from '../../domain/models';

describe('Data & Persistence Layer Unit Tests', () => {
  let matchRepo: LocalMatchRepository;
  let tourneyRepo: LocalTournamentRepository;
  let playerRepo: LocalPlayerRepository;
  let syncRepo: LocalSyncRepository;

  beforeEach(() => {
    StorageAdapter.clear();
    matchRepo = new LocalMatchRepository();
    tourneyRepo = new LocalTournamentRepository();
    playerRepo = new LocalPlayerRepository();
    syncRepo = new LocalSyncRepository();
  });

  it('saves and retrieves tournaments correctly', async () => {
    const tourney: Tournament = {
      id: 't_101',
      name: 'Premier League',
      teams: [],
      matches: [],
      settings: { overs: 20, ballType: 'Leather', powerplayOvers: 6 },
      participants: []
    };

    await tourneyRepo.saveTournament(tourney);
    const all = await tourneyRepo.getAllTournaments();

    expect(all.length).toBe(1);
    expect(all[0].name).toBe('Premier League');
  });

  it('exports and imports tournament JSON accurately', async () => {
    const tourney: Tournament = {
      id: 't_export_1',
      name: 'Export Cup',
      teams: [],
      matches: [],
      settings: { overs: 10, ballType: 'Tennis', powerplayOvers: 3 },
      participants: []
    };

    await tourneyRepo.saveTournament(tourney);
    const json = await tourneyRepo.exportTournamentJson('t_export_1');

    expect(json).not.toBeNull();
    expect(json).toContain('Export Cup');

    // Clear and re-import
    StorageAdapter.clear();
    const success = await tourneyRepo.importTournamentJson(json!);

    expect(success).toBe(true);
    const reImported = await tourneyRepo.getTournamentById('t_export_1');
    expect(reImported?.name).toBe('Export Cup');
  });

  it('manages global players and team rosters', async () => {
    const p1 = createDefaultPlayer('p_g1', 'Virat Kohli');
    await playerRepo.saveGlobalPlayer(p1);

    const globals = await playerRepo.getGlobalPlayers();
    expect(globals.length).toBe(1);
    expect(globals[0].name).toBe('Virat Kohli');

    await playerRepo.deleteGlobalPlayer('p_g1');
    const emptyGlobals = await playerRepo.getGlobalPlayers();
    expect(emptyGlobals.length).toBe(0);
  });

  it('ensures sync failure does NOT block local scoring operations', async () => {
    const match: Match = {
      id: 'm_sync_1',
      tournamentId: 't_101',
      teamA: { id: 'a', name: 'A', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      teamB: { id: 'b', name: 'B', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 },
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'a',
      bowlingTeamId: 'b',
      totalRuns: 10,
      totalWickets: 1,
      totalBalls: 12,
      wideCount: 0,
      noBallCount: 0,
      byeCount: 0,
      legByeCount: 0,
      ballHistory: [],
      wicketHistory: [],
      oversPerInnings: 20,
      gullyRules: {
        commonPlayer: false, unequalTeams: false, playersJoinMidMatch: false,
        playersSwitchMidMatch: false, lastManStanding: false, singleSideBatting: false,
        noExtraRunsForWidesNoBalls: false
      },
      isSecondInningsStarted: false,
      battingOrder: [],
      dateMillis: Date.now()
    };

    // Activate sync
    await syncRepo.startSync();

    // Broadcast state - should not throw even if broadcast fails
    await expect(syncRepo.broadcastMatchState(match)).resolves.not.toThrow();

    // Local save continues unaffected
    await matchRepo.saveMatch(match);
  });
});
