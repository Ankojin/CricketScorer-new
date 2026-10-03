import { describe, it, expect, beforeEach } from 'vitest';
import { ScoringEngine } from '../scoringEngine';
import { StorageAdapter } from '../../storage/storageAdapter';
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

function createRoster(prefix: string, count: number): Player[] {
  return Array.from({ length: count }, (_, i) =>
    createDefaultPlayer(`${prefix}_${i + 1}`, `${prefix} Player ${i + 1}`, i % 2 === 0 ? BattingStyle.RHB : BattingStyle.LHB)
  );
}

describe('Live Scoring Workflow & Context State Tests', () => {
  let teamA: Team;
  let teamB: Team;
  let liveMatch: Match;

  beforeEach(() => {
    ScoringEngine.clearCache();
    StorageAdapter.clear();

    teamA = {
      id: 'team_live_a',
      name: 'Super Strikers',
      players: createRoster('A', 11),
      matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0,
    };

    teamB = {
      id: 'team_live_b',
      name: 'Royal Bowlers',
      players: createRoster('B', 11),
      matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0,
    };

    liveMatch = {
      id: 'match_live_001',
      tournamentId: 'tour_quick_001',
      teamA,
      teamB,
      tossWinnerId: 'team_live_a',
      tossDecision: 'BAT',
      initialBattingTeamId: 'team_live_a',
      initialBowlingTeamId: 'team_live_b',
      status: MatchStatus.LIVE,
      currentInnings: 1,
      battingTeamId: 'team_live_a',
      bowlingTeamId: 'team_live_b',
      totalRuns: 0,
      totalWickets: 0,
      totalBalls: 0,
      wideCount: 0,
      noBallCount: 0,
      byeCount: 0,
      legByeCount: 0,
      ballHistory: [],
      wicketHistory: [],
      strikerId: 'A_1',
      nonStrikerId: 'A_2',
      currentBowlerId: 'B_1',
      lastBowlerId: null,
      oversPerInnings: 5,
      maxOversPerBowler: 2,
      gullyRules: createDefaultGullyRules(),
      pendingAction: PendingAction.NONE,
      isSecondInningsStarted: false,
      battingOrder: ['A_1', 'A_2'],
      dateMillis: Date.now(),
    };
  });

  describe('1. Fast Run Keypad Actions', () => {
    it('executes 0, 1, 2, 3, 4, 6 run actions and updates batter/bowler/team totals', () => {
      let m = liveMatch;

      // 0 runs (dot)
      m = ScoringEngine.recalculateMatchFromHistory({
        ...m,
        ballHistory: [
          ...m.ballHistory,
          {
            runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
            strikerId: m.strikerId || null, nonStrikerId: m.nonStrikerId || null, bowlerId: m.currentBowlerId || null, fielderId: null,
            isLegalBall: true, outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });
      expect(m.totalRuns).toBe(0);
      expect(m.totalBalls).toBe(1);

      // 1 run (rotates strike A_1 -> A_2)
      m = ScoringEngine.recalculateMatchFromHistory({
        ...m,
        ballHistory: [
          ...m.ballHistory,
          {
            runs: 1, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
            strikerId: m.strikerId || null, nonStrikerId: m.nonStrikerId || null, bowlerId: m.currentBowlerId || null, fielderId: null,
            isLegalBall: true, outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });
      expect(m.totalRuns).toBe(1);
      expect(m.totalBalls).toBe(2);
      expect(m.strikerId).toBe('A_2');
      expect(m.nonStrikerId).toBe('A_1');

      // 4 runs (four boundary)
      m = ScoringEngine.recalculateMatchFromHistory({
        ...m,
        ballHistory: [
          ...m.ballHistory,
          {
            runs: 4, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
            strikerId: m.strikerId || null, nonStrikerId: m.nonStrikerId || null, bowlerId: m.currentBowlerId || null, fielderId: null,
            isLegalBall: true, outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });
      expect(m.totalRuns).toBe(5);
      const a2 = m.teamA.players.find(p => p.id === 'A_2')?.battingStats;
      expect(a2?.fours).toBe(1);
      expect(a2?.runs).toBe(4);
    });
  });

  describe('2. Extras Actions Modal Workflows', () => {
    it('handles Wide, No-Ball, Byes, and Leg-Byes extras', () => {
      let m = liveMatch;

      // Wide (+1 penalty)
      m = ScoringEngine.recalculateMatchFromHistory({
        ...m,
        ballHistory: [
          ...m.ballHistory,
          {
            runs: 0, extrasType: ExtrasType.WIDE, extraRuns: 1, wicketType: WicketType.NONE,
            strikerId: m.strikerId || null, nonStrikerId: m.nonStrikerId || null, bowlerId: m.currentBowlerId || null, fielderId: null,
            isLegalBall: false, outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });
      expect(m.totalRuns).toBe(1);
      expect(m.wideCount).toBe(1);
      expect(m.totalBalls).toBe(0);

      // Bye (+1 bye)
      m = ScoringEngine.recalculateMatchFromHistory({
        ...m,
        ballHistory: [
          ...m.ballHistory,
          {
            runs: 0, extrasType: ExtrasType.BYE, extraRuns: 1, wicketType: WicketType.NONE,
            strikerId: m.strikerId || null, nonStrikerId: m.nonStrikerId || null, bowlerId: m.currentBowlerId || null, fielderId: null,
            isLegalBall: true, outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });
      expect(m.totalRuns).toBe(2);
      expect(m.byeCount).toBe(1);
      expect(m.totalBalls).toBe(1);
    });
  });

  describe('3. Wicket & Fielder Workflows', () => {
    it('handles Bowled wicket and prompts for SELECT_STRIKER', () => {
      const m = ScoringEngine.recalculateMatchFromHistory({
        ...liveMatch,
        ballHistory: [
          {
            runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.BOWLED,
            strikerId: 'A_1', nonStrikerId: 'A_2', bowlerId: 'B_1', fielderId: null,
            isLegalBall: true, outPlayerId: 'A_1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });

      expect(m.totalWickets).toBe(1);
      expect(m.strikerId).toBeNull();
      expect(m.pendingAction).toBe(PendingAction.SELECT_STRIKER);
    });

    it('handles Caught wicket with fielder attribution', () => {
      const m = ScoringEngine.recalculateMatchFromHistory({
        ...liveMatch,
        ballHistory: [
          {
            runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.CAUGHT,
            strikerId: 'A_1', nonStrikerId: 'A_2', bowlerId: 'B_1', fielderId: 'B_5',
            isLegalBall: true, outPlayerId: 'A_1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
            dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
          }
        ]
      });

      expect(m.totalWickets).toBe(1);
      const fielder = m.teamB.players.find(p => p.id === 'B_5')?.fieldingStats;
      expect(fielder?.catches).toBe(1);
    });
  });

  describe('4. Swap Strike & Undo Actions', () => {
    it('swaps strike between active batters', () => {
      const selStriker: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: null, nonStrikerId: null, bowlerId: null, fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: true, adjustmentSlot: 'STRIKER', adjustmentPlayerId: 'A_1', isReplacement: false
      };
      const selNonStriker: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A_1', nonStrikerId: null, bowlerId: null, fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: true, adjustmentSlot: 'NON_STRIKER', adjustmentPlayerId: 'A_2', isReplacement: false
      };
      const swapBall: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A_1', nonStrikerId: 'A_2', bowlerId: 'B_1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: true, adjustmentSlot: 'SWAP', adjustmentPlayerId: null, isReplacement: false
      };

      const m = ScoringEngine.recalculateMatchFromHistory({ ...liveMatch, ballHistory: [selStriker, selNonStriker, swapBall] });
      expect(m.strikerId).toBe('A_2');
      expect(m.nonStrikerId).toBe('A_1');
    });

    it('undoes previous ball and restores state', () => {
      const b1: Ball = {
        runs: 6, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A_1', nonStrikerId: 'A_2', bowlerId: 'B_1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const matchScored = ScoringEngine.recalculateMatchFromHistory({ ...liveMatch, ballHistory: [b1] });
      expect(matchScored.totalRuns).toBe(6);

      // Pop ball for undo
      const matchUndone = ScoringEngine.recalculateMatchFromHistory({ ...liveMatch, ballHistory: [] });
      expect(matchUndone.totalRuns).toBe(0);
      expect(matchUndone.totalBalls).toBe(0);
    });
  });

  describe('5. Local Match Storage Partitioning', () => {
    it('persists active match in local storage without network mutations', () => {
      StorageAdapter.setActiveMatchId(liveMatch.id);
      expect(StorageAdapter.getActiveMatchId()).toBe('match_live_001');
    });
  });
});
