import { describe, it, expect } from 'vitest';
import { StatsCalculator } from '../statsCalculator';
import {
  Match,
  Team,
  MatchStatus,
  calculateStrikeRate,
  calculateEconomy,
  createDefaultPlayer,
  createDefaultGullyRules
} from '../models';

describe('Statistical Calculations & Parity Unit Tests', () => {
  it('calculates Strike Rate accurately', () => {
    expect(calculateStrikeRate(45, 30)).toBe(150.0);
    expect(calculateStrikeRate(100, 50)).toBe(200.0);
    expect(calculateStrikeRate(0, 0)).toBe(0.0);
  });

  it('calculates Bowler Economy accurately', () => {
    // 24 runs in 4.0 overs = 6.0 econ
    expect(calculateEconomy(24, 4, 0)).toBe(6.0);
    // 15 runs in 2.3 overs (2 overs 3 balls = 2.5 overs) = 6.0 econ
    expect(calculateEconomy(15, 2, 3)).toBe(6.0);
    expect(calculateEconomy(0, 0, 0)).toBe(0.0);
  });

  it('calculates NRR and points table standings with tiebreakers', () => {
    const teamA: Team = { id: 'team_A', name: 'Alpha', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 };
    const teamB: Team = { id: 'team_B', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 };

    const completedMatch: Match = {
      id: 'm_101',
      teamA,
      teamB,
      tossWinnerId: 'team_A',
      tossDecision: 'BAT',
      initialBattingTeamId: 'team_A',
      initialBowlingTeamId: 'team_B',
      status: MatchStatus.COMPLETED,
      winnerId: 'team_A', // Team A wins
      currentInnings: 2,
      battingTeamId: 'team_B',
      bowlingTeamId: 'team_A',
      totalRuns: 120, // Team B scored 120 in 20 overs
      totalWickets: 8,
      totalBalls: 120,
      wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [], wicketHistory: [],
      oversPerInnings: 20,
      gullyRules: createDefaultGullyRules(),
      innings1Data: {
        runs: 160, wickets: 5, balls: 120, teamId: 'team_A',
        wicketHistory: [], wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
        recordedBallsCount: 120, durationMinutes: 80, battingOrder: []
      },
      isSecondInningsStarted: true,
      battingOrder: [],
      dateMillis: Date.now()
    };

    const standings = StatsCalculator.calculateStandings([completedMatch], [teamA, teamB]);

    expect(standings.length).toBe(2);
    // Team A: Won match ➔ 2 Points, NRR = (160/20) - (120/20) = 8.0 - 6.0 = +2.0
    expect(standings[0].teamId).toBe('team_A');
    expect(standings[0].points).toBe(2);
    expect(standings[0].nrr).toBe(2.0);

    // Team B: Lost match ➔ 0 Points, NRR = (120/20) - (160/20) = 6.0 - 8.0 = -2.0
    expect(standings[1].teamId).toBe('team_B');
    expect(standings[1].points).toBe(0);
    expect(standings[1].nrr).toBe(-2.0);
  });

  it('calculates active partnerships correctly from player stats', () => {
    const p1 = createDefaultPlayer('p1', 'Batter One');
    p1.battingStats = { ...p1.battingStats, runs: 34, balls: 20 };

    const p2 = createDefaultPlayer('p2', 'Batter Two');
    p2.battingStats = { ...p2.battingStats, runs: 16, balls: 12 };

    const teamA: Team = { id: 't_a', name: 'Alpha', players: [p1, p2], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 };
    const teamB: Team = { id: 't_b', name: 'Beta', players: [], matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0 };

    const match: Match = {
      id: 'm_p',
      teamA, teamB,
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 't_a', bowlingTeamId: 't_b',
      totalRuns: 50, totalWickets: 0, totalBalls: 32,
      wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
      ballHistory: [], wicketHistory: [],
      strikerId: 'p1', nonStrikerId: 'p2',
      oversPerInnings: 20,
      gullyRules: createDefaultGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: ['p1', 'p2'],
      dateMillis: Date.now()
    };

    const partnership = StatsCalculator.calculateActivePartnership(match);

    expect(partnership).not.toBeNull();
    expect(partnership?.totalRuns).toBe(50);
    expect(partnership?.totalBalls).toBe(32);
  });
});
