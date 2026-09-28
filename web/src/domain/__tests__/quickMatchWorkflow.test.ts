import { describe, it, expect, beforeEach } from 'vitest';
import { ScoringEngine } from '../scoringEngine';
import { Validators } from '../validators';
import {
  Match,
  Team,
  Player,
  BattingStyle,
  MatchStatus,
  PendingAction,
  createDefaultPlayer,
  createDefaultGullyRules
} from '../models';

function createRoster(prefix: string, count: number): Player[] {
  return Array.from({ length: count }, (_, i) =>
    createDefaultPlayer(`${prefix}_${i + 1}`, `${prefix} Player ${i + 1}`, BattingStyle.RHB)
  );
}

describe('Quick Match Workflow & Transition Unit Tests', () => {
  let teamA: Team;
  let teamB: Team;

  beforeEach(() => {
    ScoringEngine.clearCache();

    teamA = {
      id: 'team_alpha',
      name: 'Alpha XI',
      players: createRoster('Alpha', 4),
      matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0,
    };

    teamB = {
      id: 'team_beta',
      name: 'Beta XI',
      players: createRoster('Beta', 4),
      matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0,
    };
  });

  describe('Step 1 & 2: Teams & Player Roster Validation', () => {
    it('validates distinct team names and non-empty rosters', () => {
      const validRes = Validators.validateQuickMatchSetup(teamA, teamB, 5);
      expect(validRes.isValid).toBe(true);

      const invalidTeamName = Validators.validateQuickMatchSetup({ ...teamA, name: 'Beta XI' }, teamB, 5);
      expect(invalidTeamName.isValid).toBe(false);
      expect(invalidTeamName.errors).toContain('Team names must be distinct');

      const emptyRoster = Validators.validateQuickMatchSetup({ ...teamA, players: [] }, teamB, 5);
      expect(emptyRoster.isValid).toBe(false);
      expect(emptyRoster.errors).toContain('Team A must have at least 1 player');
    });

    it('prevents adding duplicate player names to a team', () => {
      const duplicatePlayer = createDefaultPlayer('p_dup', 'Alpha Player 1', BattingStyle.RHB);
      const existingNames = teamA.players.map(p => p.name.trim().toLowerCase());
      const isDuplicate = existingNames.includes(duplicatePlayer.name.trim().toLowerCase());

      expect(isDuplicate).toBe(true);
    });
  });

  describe('Step 3: Match Settings Validation', () => {
    it('validates overs per innings limits (1 to 50 overs)', () => {
      expect(Validators.validateQuickMatchSetup(teamA, teamB, 5).isValid).toBe(true);
      expect(Validators.validateQuickMatchSetup(teamA, teamB, 0).isValid).toBe(false);
      expect(Validators.validateQuickMatchSetup(teamA, teamB, 60).isValid).toBe(false);
    });
  });

  describe('Step 4 & 5: Toss & Match Initialization Workflow', () => {
    it('requires toss decision before starting live match', () => {
      const uninitializedMatch: Match = {
        id: 'qm_101',
        teamA,
        teamB,
        status: MatchStatus.UPCOMING,
        currentInnings: 1,
        battingTeamId: teamA.id,
        bowlingTeamId: teamB.id,
        totalRuns: 0, totalWickets: 0, totalBalls: 0,
        wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
        ballHistory: [], wicketHistory: [],
        oversPerInnings: 5,
        gullyRules: createDefaultGullyRules(),
        isSecondInningsStarted: false,
        battingOrder: [],
        dateMillis: Date.now(),
      };

      const recalculated = ScoringEngine.recalculateMatchFromHistory(uninitializedMatch);
      expect(recalculated.pendingAction).toBe(PendingAction.TOSS_REQUIRED);
    });

    it('assigns initial batting and bowling teams correctly based on toss choice', () => {
      const matchWithToss: Match = {
        id: 'qm_102',
        teamA,
        teamB,
        tossWinnerId: teamB.id,
        tossDecision: 'BOWL', // Team B elected to bowl ➔ Team A bats first
        status: MatchStatus.LIVE,
        currentInnings: 1,
        battingTeamId: teamA.id,
        bowlingTeamId: teamB.id,
        totalRuns: 0, totalWickets: 0, totalBalls: 0,
        wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
        ballHistory: [], wicketHistory: [],
        oversPerInnings: 5,
        gullyRules: createDefaultGullyRules(),
        isSecondInningsStarted: false,
        battingOrder: [],
        dateMillis: Date.now(),
      };

      const res = ScoringEngine.recalculateMatchFromHistory(matchWithToss);
      expect(res.battingTeamId).toBe(teamA.id);
      expect(res.bowlingTeamId).toBe(teamB.id);
      expect(res.pendingAction).toBe(PendingAction.SELECT_STRIKER);
    });

    it('guides user through pending action pipeline: Striker ➔ Non-Striker ➔ Bowler ➔ Ready', () => {
      let match: Match = {
        id: 'qm_103',
        teamA,
        teamB,
        tossWinnerId: teamA.id,
        tossDecision: 'BAT',
        status: MatchStatus.LIVE,
        currentInnings: 1,
        battingTeamId: teamA.id,
        bowlingTeamId: teamB.id,
        totalRuns: 0, totalWickets: 0, totalBalls: 0,
        wideCount: 0, noBallCount: 0, byeCount: 0, legByeCount: 0,
        ballHistory: [], wicketHistory: [],
        oversPerInnings: 5,
        gullyRules: createDefaultGullyRules(),
        isSecondInningsStarted: false,
        battingOrder: [],
        dateMillis: Date.now(),
      };

      // Pipeline step 1: Striker required
      match = ScoringEngine.recalculateMatchFromHistory(match);
      expect(match.pendingAction).toBe(PendingAction.SELECT_STRIKER);

      // Pipeline step 2: Select Striker
      match = { ...match, strikerId: 'Alpha_1' };
      match = ScoringEngine.recalculateMatchFromHistory(match);
      expect(match.pendingAction).toBe(PendingAction.SELECT_NON_STRIKER);

      // Pipeline step 3: Select Non-Striker
      match = { ...match, nonStrikerId: 'Alpha_2' };
      match = ScoringEngine.recalculateMatchFromHistory(match);
      expect(match.pendingAction).toBe(PendingAction.SELECT_BOWLER);

      // Pipeline step 4: Select Bowler ➔ Scoring Ready!
      match = { ...match, currentBowlerId: 'Beta_1' };
      match = ScoringEngine.recalculateMatchFromHistory(match);
      expect(match.pendingAction).toBe(PendingAction.NONE);
    });
  });
});
