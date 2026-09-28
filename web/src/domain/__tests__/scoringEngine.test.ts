import { describe, it, expect, beforeEach } from 'vitest';
import { ScoringEngine } from '../scoringEngine';
import { StatsCalculator } from '../statsCalculator';
import {
  Match,
  Team,
  Ball,
  Player,
  BattingStyle,
  WicketType,
  ExtrasType,
  MatchStatus,
  PendingAction,
  createDefaultPlayer,
  createDefaultGullyRules
} from '../models';

function createTestPlayers(prefix: string, count: number): Player[] {
  return Array.from({ length: count }, (_, i) =>
    createDefaultPlayer(`${prefix}${i + 1}`, `Player ${prefix}${i + 1}`, i % 2 === 0 ? BattingStyle.RHB : BattingStyle.LHB)
  );
}

describe('Cricket Scoring Engine Parity Tests (Android ↔ TypeScript)', () => {
  let teamA: Team;
  let teamB: Team;
  let baseMatch: Match;

  beforeEach(() => {
    ScoringEngine.clearCache();

    teamA = {
      id: 'teamA',
      name: 'Team Alpha',
      players: createTestPlayers('A', 11),
      matchesPlayed: 0,
      wins: 0,
      losses: 0,
      points: 0,
      nrr: 0,
    };

    teamB = {
      id: 'teamB',
      name: 'Team Beta',
      players: createTestPlayers('B', 11),
      matchesPlayed: 0,
      wins: 0,
      losses: 0,
      points: 0,
      nrr: 0,
    };

    baseMatch = {
      id: 'match_1',
      tournamentId: 'tourney_1',
      teamA,
      teamB,
      tossWinnerId: 'teamA',
      tossDecision: 'BAT',
      initialBattingTeamId: 'teamA',
      initialBowlingTeamId: 'teamB',
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'teamA',
      bowlingTeamId: 'teamB',
      totalRuns: 0,
      totalWickets: 0,
      totalBalls: 0,
      wideCount: 0,
      noBallCount: 0,
      byeCount: 0,
      legByeCount: 0,
      ballHistory: [],
      wicketHistory: [],
      strikerId: 'A1',
      nonStrikerId: 'A2',
      currentBowlerId: 'B1',
      lastBowlerId: null,
      oversPerInnings: 5,
      gullyRules: createDefaultGullyRules(),
      pendingAction: PendingAction.NONE,
      isSecondInningsStarted: false,
      battingOrder: ['A1', 'A2'],
      dateMillis: Date.now(),
    };
  });

  describe('1. Basic Scoring', () => {
    it('handles dot ball', () => {
      const ball: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ball] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalRuns).toBe(0);
      expect(res.totalBalls).toBe(1);
      expect(res.totalWickets).toBe(0);
      expect(res.strikerId).toBe('A1');
      expect(res.nonStrikerId).toBe('A2');

      const s = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(s?.runs).toBe(0);
      expect(s?.balls).toBe(1);
    });

    it('handles singles and rotates strike', () => {
      const ball: Ball = {
        runs: 1, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ball] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalRuns).toBe(1);
      expect(res.totalBalls).toBe(1);
      expect(res.strikerId).toBe('A2');
      expect(res.nonStrikerId).toBe('A1');
    });

    it('handles boundaries (4s and 6s) without strike rotation', () => {
      const ball4: Ball = {
        runs: 4, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ball4] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalRuns).toBe(4);
      expect(res.strikerId).toBe('A1');
      const s = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(s?.fours).toBe(1);
      expect(s?.sixes).toBe(0);
    });
  });

  describe('2. Extras', () => {
    it('handles wide scoring without incrementing physical balls', () => {
      const ballWide: Ball = {
        runs: 0, extrasType: ExtrasType.WIDE, extraRuns: 1, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ballWide] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalRuns).toBe(1);
      expect(res.totalBalls).toBe(0);
      expect(res.wideCount).toBe(1);
      expect(res.strikerId).toBe('A1');

      const bw = res.teamB.players.find(p => p.id === 'B1')?.bowlingStats;
      expect(bw?.runsConceded).toBe(1);
      expect(bw?.wides).toBe(1);
      expect(bw?.balls).toBe(0);
    });

    it('handles no-ball scoring with bat runs', () => {
      const ballNoBall: Ball = {
        runs: 2, extrasType: ExtrasType.NO_BALL, extraRuns: 1, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ballNoBall] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalRuns).toBe(3); // 2 bat runs + 1 penalty
      expect(res.totalBalls).toBe(0);
      expect(res.noBallCount).toBe(1);
      expect(res.strikerId).toBe('A1'); // 2 bat runs = even, no rotation
    });
  });

  describe('3. Wickets', () => {
    it('handles bowled wicket', () => {
      const ballWicket: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.BOWLED,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: 'A1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ballWicket] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalWickets).toBe(1);
      expect(res.strikerId).toBeNull();
      expect(res.pendingAction).toBe(PendingAction.SELECT_STRIKER);

      const b1Stats = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(b1Stats?.isOut).toBe(true);
      expect(b1Stats?.wicketType).toBe(WicketType.BOWLED);

      const bwStats = res.teamB.players.find(p => p.id === 'B1')?.bowlingStats;
      expect(bwStats?.wickets).toBe(1);
    });

    it('handles retired hurt without incrementing wickets count', () => {
      const ballRH: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.RETIRED_HURT,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: 'A1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, ballHistory: [ballRH] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalWickets).toBe(0);
      const b1Stats = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(b1Stats?.isRetiredHurt).toBe(true);
      expect(b1Stats?.isOut).toBe(false);
    });
  });

  describe('4. Overs & Innings Transition', () => {
    it('completes over after 6 physical balls and prompts for next bowler', () => {
      const balls: Ball[] = Array.from({ length: 6 }, () => ({
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      }));

      const match = { ...baseMatch, ballHistory: balls };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalBalls).toBe(6);
      expect(res.currentBowlerId).toBeNull();
      expect(res.lastBowlerId).toBe('B1');
      expect(res.pendingAction).toBe(PendingAction.SELECT_BOWLER);
    });

    it('ends 1st Innings and sets target for 2nd Innings', () => {
      // 5 overs match = 30 balls
      const balls: Ball[] = Array.from({ length: 30 }, () => ({
        runs: 1, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      }));

      const match = { ...baseMatch, ballHistory: balls };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.currentInnings).toBe(2);
      expect(res.target).toBe(31); // 30 runs + 1
      expect(res.innings1Data?.runs).toBe(30);
      expect(res.battingTeamId).toBe('teamB');
      expect(res.bowlingTeamId).toBe('teamA');
      expect(res.pendingAction).toBe(PendingAction.START_SECOND_INNINGS);
    });
  });

  describe('5. Gully Rules & LMS', () => {
    it('respects noExtraRunsForWidesNoBalls rule', () => {
      const gullyRules = { ...createDefaultGullyRules(), noExtraRunsForWidesNoBalls: true };

      // Under noExtraRunsForWidesNoBalls, extra penalty is 0, so extraRuns = 0
      const ballWide: Ball = {
        runs: 0, extrasType: ExtrasType.WIDE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const match = { ...baseMatch, gullyRules, ballHistory: [ballWide] };
      const res = ScoringEngine.recalculateMatchFromHistory(match);

      expect(res.totalRuns).toBe(0); // 0 penalty runs added
      expect(res.wideCount).toBe(0);
    });

    it('allows last man standing (LMS) when total wickets reach squadSize - 1', () => {
      const gullyRules = { ...createDefaultGullyRules(), lastManStanding: true };

      // Team with 3 players
      const squad3 = createTestPlayers('A', 3);
      const smallTeamA = { ...teamA, players: squad3 };

      const match = { ...baseMatch, teamA: smallTeamA, gullyRules };

      // Dismiss 2 players (out of 3)
      const w1: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.BOWLED,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: 'A1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
      const adj1: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: null, nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: true, adjustmentSlot: 'STRIKER', adjustmentPlayerId: 'A3', isReplacement: false
      };
      const w2: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.BOWLED,
        strikerId: 'A3', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: 'A3', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const res = ScoringEngine.recalculateMatchFromHistory({ ...match, ballHistory: [w1, adj1, w2] });

      expect(res.totalWickets).toBe(2);
      expect(res.strikerId).toBe('A2');
      expect(res.nonStrikerId).toBeNull(); // LMS: bats alone without non-striker
      expect(res.status).toBe(MatchStatus.LIVE);
    });
  });

  describe('6. Partnerships & Standings', () => {
    it('calculates active partnership correctly', () => {
      const b1: Ball = {
        runs: 2, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const res = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [b1] });
      const p = StatsCalculator.calculateActivePartnership(res);

      expect(p).not.toBeNull();
      expect(p?.totalRuns).toBe(2);
      expect(p?.batter1Id).toBe('A1');
      expect(p?.batter2Id).toBe('A2');
    });
  });
});
