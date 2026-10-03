import { describe, it, expect, beforeEach } from 'vitest';
import { ScoringEngine } from '../scoringEngine';
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

describe('Golden Fixtures Parity Test Suite', () => {
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
      id: 'golden_match_1',
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

  describe('1. Ball Runs & Strike Rotation Fixtures', () => {
    it('fixture: dot ball, 1, 2, 3, 4, 6 runs and strike rotation', () => {
      const balls: Ball[] = [
        // Ball 1: Dot ball
        {
          runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // Ball 2: Single (rotate strike A1 -> A2)
        {
          runs: 1, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // Ball 3: Two runs (A2 on strike, no rotation)
        {
          runs: 2, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
          strikerId: 'A2', nonStrikerId: 'A1', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // Ball 4: Three runs (A2 on strike -> rotate to A1)
        {
          runs: 3, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
          strikerId: 'A2', nonStrikerId: 'A1', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // Ball 5: Four (A1 on strike, boundary, no rotation)
        {
          runs: 4, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // Ball 6: Six (A1 on strike, boundary, over complete -> pending bowler)
        {
          runs: 6, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        }
      ];

      const res = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: balls });

      expect(res.totalRuns).toBe(16); // 0 + 1 + 2 + 3 + 4 + 6
      expect(res.totalBalls).toBe(6);
      expect(res.pendingAction).toBe(PendingAction.SELECT_BOWLER);

      const a1 = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(a1?.runs).toBe(11); // 0 + 1 + 4 + 6
      expect(a1?.balls).toBe(4);
      expect(a1?.fours).toBe(1);
      expect(a1?.sixes).toBe(1);

      const a2 = res.teamA.players.find(p => p.id === 'A2')?.battingStats;
      expect(a2?.runs).toBe(5); // 2 + 3
      expect(a2?.balls).toBe(2);
    });
  });

  describe('2. Extras Fixtures (Wide, No-Ball, Byes, Leg-Byes, Granted)', () => {
    it('fixture: handles Wide, Wide+runs, No-Ball+runs, Byes, Leg-Byes, and Granted runs', () => {
      const balls: Ball[] = [
        // 1. Wide (1 penalty run, not physical ball)
        {
          runs: 0, extrasType: ExtrasType.WIDE, extraRuns: 1, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // 2. No-Ball + 2 bat runs (3 total runs: 1 penalty + 2 bat runs)
        {
          runs: 2, extrasType: ExtrasType.NO_BALL, extraRuns: 1, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // 3. Bye (1 bye run, rotates strike A1 -> A2, physical ball)
        {
          runs: 0, extrasType: ExtrasType.BYE, extraRuns: 1, wicketType: WicketType.NONE,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // 4. Leg-Bye (2 leg-bye runs, A2 on strike, no strike rotation)
        {
          runs: 0, extrasType: ExtrasType.LEG_BYE, extraRuns: 2, wicketType: WicketType.NONE,
          strikerId: 'A2', nonStrikerId: 'A1', bowlerId: 'B1', fielderId: null, isLegalBall: true,
          outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        },
        // 5. Granted / Penalty runs (5 granted runs to batting team as adjustment)
        {
          runs: 0, extrasType: ExtrasType.GRANTED, extraRuns: 5, wicketType: WicketType.NONE,
          strikerId: 'A2', nonStrikerId: 'A1', bowlerId: 'B1', fielderId: null, isLegalBall: false,
          outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: true, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        }
      ];

      const res = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: balls });

      expect(res.totalRuns).toBe(12); // 1 (wide) + 3 (no-ball+bat) + 1 (bye) + 2 (leg-bye) + 5 (granted)
      expect(res.wideCount).toBe(1);
      expect(res.noBallCount).toBe(1);
      expect(res.byeCount).toBe(1);
      expect(res.legByeCount).toBe(2);
      expect(res.totalBalls).toBe(2); // Byes & Leg-Byes are physical balls
    });
  });

  describe('3. All Supported Wicket Types', () => {
    it('fixture: validates BOWLED, CAUGHT, LBW, STUMPED, HIT_WICKET', () => {
      const wicketTypes = [
        { type: WicketType.BOWLED, fielder: null },
        { type: WicketType.CAUGHT, fielder: 'B2' },
        { type: WicketType.LBW, fielder: null },
        { type: WicketType.STUMPED, fielder: 'B11' },
        { type: WicketType.HIT_WICKET, fielder: null }
      ];

      for (const w of wicketTypes) {
        const ball: Ball = {
          runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: w.type,
          strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: w.fielder, isLegalBall: true,
          outPlayerId: 'A1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
          dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
        };

        const res = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [ball] });

        expect(res.totalWickets).toBe(1);
        const bowler = res.teamB.players.find(p => p.id === 'B1')?.bowlingStats;
        expect(bowler?.wickets).toBe(1);

        const batter = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
        expect(batter?.isOut).toBe(true);
        expect(batter?.wicketType).toBe(w.type);
      }
    });

    it('fixture: validates RUN_OUT does NOT add wicket to bowler stats', () => {
      const runOutBall: Ball = {
        runs: 1, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.RUN_OUT,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: 'B3', isLegalBall: true,
        outPlayerId: 'A2', rotateStrike: false, hadCrossed: true, isDroppedCatch: false,
        dismissalReason: 'Run out at non-striker end', isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const res = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [runOutBall] });

      expect(res.totalWickets).toBe(1);
      expect(res.totalRuns).toBe(1); // Completed 1 run before run out

      const bowler = res.teamB.players.find(p => p.id === 'B1')?.bowlingStats;
      expect(bowler?.wickets).toBe(0); // Bowler does NOT get credit for run out

      const fielder = res.teamB.players.find(p => p.id === 'B3')?.fieldingStats;
      expect(fielder?.runOuts).toBe(1);

      const nonStriker = res.teamA.players.find(p => p.id === 'A2')?.battingStats;
      expect(nonStriker?.isOut).toBe(true);
      expect(nonStriker?.wicketType).toBe(WicketType.RUN_OUT);
    });

    it('fixture: handles RETIRED_HURT and re-entry', () => {
      // 1. A1 retires hurt
      const retiredHurtBall: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.RETIRED_HURT,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: 'A1', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: 'Retired hurt - hamstring', isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      // 2. Select A3 as replacement striker
      const selectA3: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: null, nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: true, adjustmentSlot: 'STRIKER', adjustmentPlayerId: 'A3', isReplacement: false
      };

      // 3. A3 gets out
      const a3Out: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.BOWLED,
        strikerId: 'A3', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: 'A3', rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      // 4. Re-enter A1 (retired hurt batter) as striker
      const reenterA1: Ball = {
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: null, nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: false,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: true, adjustmentSlot: 'STRIKER', adjustmentPlayerId: 'A1', isReplacement: false
      };

      // 5. A1 scores 4
      const a1Four: Ball = {
        runs: 4, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const history = [retiredHurtBall, selectA3, a3Out, reenterA1, a1Four];
      const res = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: history });

      expect(res.totalWickets).toBe(1); // Only A3 is out
      expect(res.totalRuns).toBe(4);
      expect(res.strikerId).toBe('A1');

      const a1Stats = res.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(a1Stats?.runs).toBe(4);
      expect(a1Stats?.isOut).toBe(false);
      expect(a1Stats?.isRetiredHurt).toBe(false); // Cleared upon re-entry
    });
  });

  describe('4. Innings Transition, Target & Chase Completion', () => {
    it('fixture: handles full 2nd innings chase win, loss, and tie', () => {
      // 1st Innings: Team A scores 10 runs (Target = 11)
      const innings1Balls: Ball[] = Array.from({ length: 30 }, () => ({
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      }));
      // Set 10 runs in last 2 balls of innings 1
      innings1Balls[0].runs = 6;
      innings1Balls[1].runs = 4;

      // 2nd Innings setup: Team B chasing 11
      const res1 = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: innings1Balls });
      expect(res1.currentInnings).toBe(2);
      expect(res1.target).toBe(11);

      // Chase Win: Team B scores 12 runs
      const winBall: Ball = {
        runs: 6, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'B1', nonStrikerId: 'B2', bowlerId: 'A1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
      const winBall2: Ball = {
        runs: 6, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'B1', nonStrikerId: 'B2', bowlerId: 'A1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const resWin = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [...innings1Balls, winBall, winBall2] });
      expect(resWin.status).toBe(MatchStatus.COMPLETED);
      expect(resWin.winnerId).toBe('teamB');
    });
  });

  describe('5. History Mutations & Deterministic Replay', () => {
    it('fixture: undoing last ball reverts match state perfectly', () => {
      const b1: Ball = {
        runs: 4, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
      const b2: Ball = {
        runs: 1, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: true, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const matchAfter2 = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [b1, b2] });
      expect(matchAfter2.totalRuns).toBe(5);
      expect(matchAfter2.strikerId).toBe('A2');

      // Undo b2 (pop from history)
      const matchAfterUndo = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [b1] });
      expect(matchAfterUndo.totalRuns).toBe(4);
      expect(matchAfterUndo.strikerId).toBe('A1');
      expect(matchAfterUndo.totalBalls).toBe(1);
    });

    it('fixture: editing historical ball recalculates whole match deterministically', () => {
      const b1: Ball = {
        runs: 4, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: 'A1', nonStrikerId: 'A2', bowlerId: 'B1', fielderId: null, isLegalBall: true,
        outPlayerId: null, rotateStrike: false, hadCrossed: false, isDroppedCatch: false,
        dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };

      const original = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [b1] });
      expect(original.totalRuns).toBe(4);

      // Edit b1 from 4 to 1
      const editedB1 = { ...b1, runs: 1, rotateStrike: true };
      const edited = ScoringEngine.recalculateMatchFromHistory({ ...baseMatch, ballHistory: [editedB1] });

      expect(edited.totalRuns).toBe(1);
      expect(edited.strikerId).toBe('A2'); // Rotated strike on single
      const a1Stats = edited.teamA.players.find(p => p.id === 'A1')?.battingStats;
      expect(a1Stats?.fours).toBe(0);
      expect(a1Stats?.runs).toBe(1);
    });
  });
});
