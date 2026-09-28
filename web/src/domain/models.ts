export enum BattingStyle {
  RHB = 'RHB',
  LHB = 'LHB'
}

export interface BattingStats {
  runs: number;
  balls: number;
  fours: number;
  sixes: number;
  isOut: boolean;
  isRetiredHurt: boolean;
  wicketType: WicketType;
  dismissalBowlerId: string | null;
  dismissalFielderId: string | null;
}

export function calculateStrikeRate(runs: number, balls: number): number {
  return balls > 0 ? (runs / balls) * 100 : 0;
}

export interface BowlingStats {
  overs: number;
  balls: number;
  maidens: number;
  runsConceded: number;
  wickets: number;
  dotBalls: number;
  wides: number;
  noBalls: number;
}

export function calculateEconomy(runsConceded: number, overs: number, balls: number): number {
  const totalOvers = overs + balls / 6.0;
  return totalOvers > 0 ? runsConceded / totalOvers : 0;
}

export function formatOvers(overs: number, balls: number): string {
  return `${overs}.${balls % 6}`;
}

export interface FieldingStats {
  catches: number;
  runOuts: number;
  stumpings: number;
  droppedCatches: number;
}

export interface Player {
  id: string;
  name: string;
  battingStats: BattingStats;
  bowlingStats: BowlingStats;
  fieldingStats: FieldingStats;
  isJoker: boolean;
  isCaptain: boolean;
  isViceCaptain: boolean;
  battingStyle: BattingStyle | null;
}

export function createDefaultPlayer(id: string, name: string, style: BattingStyle = BattingStyle.RHB): Player {
  return {
    id,
    name,
    battingStats: {
      runs: 0,
      balls: 0,
      fours: 0,
      sixes: 0,
      isOut: false,
      isRetiredHurt: false,
      wicketType: WicketType.NONE,
      dismissalBowlerId: null,
      dismissalFielderId: null,
    },
    bowlingStats: {
      overs: 0,
      balls: 0,
      maidens: 0,
      runsConceded: 0,
      wickets: 0,
      dotBalls: 0,
      wides: 0,
      noBalls: 0,
    },
    fieldingStats: {
      catches: 0,
      runOuts: 0,
      stumpings: 0,
      droppedCatches: 0,
    },
    isJoker: false,
    isCaptain: false,
    isViceCaptain: false,
    battingStyle: style,
  };
}

export interface Team {
  id: string;
  name: string;
  players: Player[];
  matchesPlayed: number;
  wins: number;
  losses: number;
  points: number;
  nrr: number;
  colorHex?: string | null;
}

export enum ExtrasType {
  NONE = 'NONE',
  WIDE = 'WIDE',
  NO_BALL = 'NO_BALL',
  BYE = 'BYE',
  LEG_BYE = 'LEG_BYE',
  GRANTED = 'GRANTED'
}

export enum WicketType {
  NONE = 'NONE',
  BOWLED = 'BOWLED',
  CAUGHT = 'CAUGHT',
  LBW = 'LBW',
  RUN_OUT = 'RUN_OUT',
  STUMPED = 'STUMPED',
  HIT_WICKET = 'HIT_WICKET',
  HANDLED_BALL = 'HANDLED_BALL',
  OBSTRUCTING_FIELD = 'OBSTRUCTING_FIELD',
  RETIRED_HURT = 'RETIRED_HURT'
}

export interface Ball {
  runs: number;
  extrasType: ExtrasType;
  extraRuns: number;
  wicketType: WicketType;
  strikerId: string | null;
  nonStrikerId: string | null;
  bowlerId: string | null;
  fielderId: string | null;
  isLegalBall: boolean;
  outPlayerId: string | null;
  rotateStrike: boolean;
  hadCrossed: boolean;
  isDroppedCatch: boolean;
  dismissalReason: string | null;
  isAdjustment: boolean;
  adjustmentSlot: string | null;
  adjustmentPlayerId: string | null;
  isReplacement: boolean;
}

export function isPhysicalBall(ball: Ball): boolean {
  return !ball.isAdjustment &&
    ball.extrasType !== ExtrasType.WIDE &&
    ball.extrasType !== ExtrasType.NO_BALL &&
    ball.wicketType !== WicketType.RETIRED_HURT;
}

export interface WicketRecord {
  wicketNumber: number;
  batterName: string;
  totalRuns: number;
  over: string;
  wicketType: WicketType;
  bowlerName?: string | null;
  fielderName?: string | null;
  dismissalReason?: string | null;
}

export interface InningsSummary {
  runs: number;
  wickets: number;
  balls: number;
  teamId: string;
  wicketHistory: WicketRecord[];
  wideCount: number;
  noBallCount: number;
  byeCount: number;
  legByeCount: number;
  recordedBallsCount: number;
  durationMinutes: number;
  battingOrder: string[];
}

export interface GullyRules {
  commonPlayer: boolean;
  unequalTeams: boolean;
  playersJoinMidMatch: boolean;
  playersSwitchMidMatch: boolean;
  lastManStanding: boolean;
  singleSideBatting: boolean;
  noExtraRunsForWidesNoBalls: boolean;
}

export function createDefaultGullyRules(): GullyRules {
  return {
    commonPlayer: false,
    unequalTeams: false,
    playersJoinMidMatch: false,
    playersSwitchMidMatch: false,
    lastManStanding: false,
    singleSideBatting: false,
    noExtraRunsForWidesNoBalls: false,
  };
}

export enum MatchStatus {
  UPCOMING = 'UPCOMING',
  LIVE = 'LIVE',
  COMPLETED = 'COMPLETED',
  ABANDONED = 'ABANDONED'
}

export enum PendingAction {
  NONE = 'NONE',
  SELECT_STRIKER = 'SELECT_STRIKER',
  SELECT_NON_STRIKER = 'SELECT_NON_STRIKER',
  SELECT_BOWLER = 'SELECT_BOWLER',
  TOSS_REQUIRED = 'TOSS_REQUIRED',
  SELECT_WK_A = 'SELECT_WK_A',
  SELECT_WK_B = 'SELECT_WK_B',
  SELECT_FIELDER = 'SELECT_FIELDER',
  START_SECOND_INNINGS = 'START_SECOND_INNINGS',
  SELECT_FIELDER_DROPPED_CATCH = 'SELECT_FIELDER_DROPPED_CATCH',
  SELECT_RUNS_DROPPED_CATCH = 'SELECT_RUNS_DROPPED_CATCH',
  REPLACE_STRIKER = 'REPLACE_STRIKER',
  REPLACE_NON_STRIKER = 'REPLACE_NON_STRIKER',
  REPLACE_BOWLER = 'REPLACE_BOWLER',
  SELECT_RUNS_WICKET = 'SELECT_RUNS_WICKET',
  SELECT_MATCH_SETTINGS = 'SELECT_MATCH_SETTINGS'
}

export interface Match {
  id: string;
  tournamentId?: string | null;
  tournamentName?: string | null;
  teamA: Team;
  teamB: Team;
  tossWinnerId?: string | null;
  tossDecision?: string | null; // "BAT" or "BOWL"
  initialBattingTeamId?: string | null;
  initialBowlingTeamId?: string | null;

  teamACaptainId?: string | null;
  teamBCaptainId?: string | null;
  teamAWicketKeeperId?: string | null;
  teamBWicketKeeperId?: string | null;

  target?: number | null;
  status: MatchStatus;
  currentInnings: number;
  battingTeamId: string;
  bowlingTeamId: string;
  totalRuns: number;
  totalWickets: number;
  totalBalls: number;
  wideCount: number;
  noBallCount: number;
  byeCount: number;
  legByeCount: number;
  ballHistory: Ball[];
  wicketHistory: WicketRecord[];
  strikerId?: string | null;
  nonStrikerId?: string | null;
  currentBowlerId?: string | null;
  lastBowlerId?: string | null;
  winnerId?: string | null;
  manOfTheMatchId?: string | null;
  oversPerInnings: number;
  maxOversPerBowler?: number | null;
  quotaBowlersCount?: number | null;
  quotaMaxOvers?: number | null;
  gullyRules: GullyRules;
  pendingAction?: PendingAction | null;
  innings1Data?: InningsSummary | null;
  isSecondInningsStarted: boolean;
  innings1EndTimeMillis?: number | null;
  innings2StartTimeMillis?: number | null;
  lastNotifiedBowlerId?: string | null;
  battingOrder: string[];
  startTimeMillis?: number | null;
  endTimeMillis?: number | null;
  dateMillis: number;
}

export interface Partnership {
  batter1Id: string;
  batter1Name: string;
  batter1Runs: number;
  batter1Balls: number;
  batter2Id: string;
  batter2Name: string;
  batter2Runs: number;
  batter2Balls: number;
  totalRuns: number;
  totalBalls: number;
}

export interface TournamentSettings {
  overs: number;
  ballType: string;
  powerplayOvers: number;
  maxOversPerBowler?: number | null;
  quotaBowlersCount?: number | null;
  quotaMaxOvers?: number | null;
}

export interface ActiveWicketContext {
  type: WicketType;
  initialStrikerId: string;
  initialNonStrikerId: string;
  initialBowlerId: string;
  completedRuns: number;
  brokenEnd: string;
  dismissalReason?: string | null;
  dismissalFielderId?: string | null;
  hadCrossed?: boolean;
  expectedReplacementAction: PendingAction;
}

export interface OverSummary {
  overNumber: number;
  runs: number;
  wickets: number;
  ballLabels: string[];
  teamTotalRuns: number;
  teamTotalWickets: number;
  battingTeamName: string;
}

export interface MatchUiState {
  match: Match | null;
  isDarkMode?: boolean | null;
  bowlerNotification?: string | null;
  activeWicketContext?: ActiveWicketContext | null;
  isSyncEnabled: boolean;
  connectedDevicesCount: number;
  finishedOverSummary?: OverSummary | null;
}

export interface Tournament {
  id: string;
  name: string;
  teams: Team[];
  matches: Match[];
  settings: TournamentSettings;
  participants: Player[];
}
