import React, { createContext, useContext, useState, useEffect } from 'react';
import {
  Match,
  MatchUiState,
  Ball,
  ExtrasType,
  WicketType,
  PendingAction,
  ActiveWicketContext,
  OverSummary,
  Player,
  GullyRules,
  MatchStatus,
  isPhysicalBall
} from '../domain/models';
import { ScoringEngine } from '../domain/scoringEngine';
import { StorageAdapter } from '../storage/storageAdapter';
import { useTournament } from './TournamentContext';

interface MatchContextType {
  match: Match | null;
  uiState: MatchUiState;
  loadMatch: (match: Match) => void;
  handleToss: (winnerId: string, decision: 'BAT' | 'BOWL') => void;
  handleRuns: (runs: number, rotateStrike?: boolean) => void;
  handleExtra: (type: ExtrasType, extraRuns: number) => void;
  handleWicket: (type: WicketType, victimId?: string | null) => void;
  handleRunOutWicket: (runs: number, outId: string, rotate: boolean, fielderId?: string | null) => void;
  selectFielder: (fielderId: string) => void;
  assignPlayerToAction: (playerId: string) => void;
  swapStrike: () => void;
  undo: () => void;
  startSecondInnings: () => void;
  forceChangeBowler: () => void;
  updateMatchSettings: (overs: number, maxOvers?: number | null, quotaCount?: number | null, quotaLimit?: number | null) => void;
  updateMatchGullyRules: (rules: GullyRules) => void;
  addNewPlayerToMatch: (playerName: string, teamId: string, style?: any) => void;
  addGlobalPlayersToMatch: (players: Player[], teamId: string) => void;
  deletePlayerFromMatch: (playerId: string) => void;
  updatePlayerInMatch: (playerId: string, newName: string, bStyle: any, isCaptain: boolean, isViceCaptain: boolean) => void;
  editBall: (index: number, updatedBall: Ball) => void;
  dismissOverSummary: () => void;
  clearBowlerNotification: () => void;
  cancelPendingAction: () => void;
}

const MatchContext = createContext<MatchContextType | undefined>(undefined);

export const MatchProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { updateMatchInTournament, addPlayerToGlobalList, addPlayersToTeam } = useTournament();

  const [match, setMatch] = useState<Match | null>(null);
  const [bowlerNotification, setBowlerNotification] = useState<string | null>(null);
  const [activeWicketContext, setActiveWicketContext] = useState<ActiveWicketContext | null>(null);
  const [finishedOverSummary, setFinishedOverSummary] = useState<OverSummary | null>(null);

  useEffect(() => {
    const savedMatchId = StorageAdapter.getActiveMatchId();
    if (savedMatchId && !match) {
      const tournaments = StorageAdapter.getTournaments();
      const foundMatch = tournaments.flatMap(t => t.matches).find(m => m.id === savedMatchId);
      if (foundMatch) {
        loadMatch(foundMatch);
      }
    }
  }, []);

  useEffect(() => {
    if (match) {
      StorageAdapter.setActiveMatchId(match.id);
      if (match.tournamentId) {
        updateMatchInTournament(match.tournamentId, match);
      }
    }
  }, [match]);

  const loadMatch = (m: Match) => {
    ScoringEngine.clearCache(m.id);
    const recalculated = ScoringEngine.recalculateMatchFromHistory(m);
    setMatch(recalculated);
    setBowlerNotification(null);
    setActiveWicketContext(null);
    setFinishedOverSummary(null);
  };

  const recordBall = (ball: Ball) => {
    if (!match) return;
    if (match.status === MatchStatus.COMPLETED && !ball.isAdjustment) return;

    let updatedHistory = [...match.ballHistory, ball];
    let updatedMatch: Match = {
      ...match,
      startTimeMillis: match.startTimeMillis || Date.now(),
      ballHistory: updatedHistory
    };

    if (ball.wicketType === WicketType.RETIRED_HURT) {
      const outId = ball.outPlayerId || ball.strikerId;
      if (updatedMatch.strikerId === outId) updatedMatch.strikerId = null;
      if (updatedMatch.nonStrikerId === outId) updatedMatch.nonStrikerId = null;
    }

    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);

    if (recalculated.status === MatchStatus.LIVE && ball.isLegalBall && recalculated.totalBalls % 6 === 0) {
      if (recalculated.pendingAction === PendingAction.START_SECOND_INNINGS) {
        setFinishedOverSummary(null);
      } else {
        const lastOverBalls: Ball[] = [];
        let physicalCount = 0;
        for (let i = recalculated.ballHistory.length - 1; i >= 0; i--) {
          const b = recalculated.ballHistory[i];
          if (b.isAdjustment) continue;
          lastOverBalls.push(b);
          if (isPhysicalBall(b)) physicalCount++;
          if (physicalCount === 6) break;
        }

        const runs = lastOverBalls.reduce((acc, b) => acc + b.runs + b.extraRuns, 0);
        const wickets = lastOverBalls.filter(b => b.wicketType !== WicketType.NONE && b.wicketType !== WicketType.RETIRED_HURT).length;
        const labels = lastOverBalls.map(b => {
          if (b.wicketType === WicketType.RETIRED_HURT) return 'RH';
          if (b.wicketType !== WicketType.NONE) return 'W';
          if (b.extrasType === ExtrasType.WIDE) return `${b.extraRuns}wd`;
          if (b.extrasType === ExtrasType.NO_BALL) return `${b.runs + b.extraRuns}nb`;
          if (b.extrasType === ExtrasType.BYE) return `${b.extraRuns}b`;
          if (b.extrasType === ExtrasType.LEG_BYE) return `${b.extraRuns}lb`;
          if (b.extrasType === ExtrasType.GRANTED) return `${b.runs}G`;
          return `${b.runs}`;
        }).reverse();

        const bTeam = ScoringEngine.isTeamA(recalculated.battingTeamId, recalculated) ? recalculated.teamA : recalculated.teamB;
        setFinishedOverSummary({
          overNumber: Math.floor(recalculated.totalBalls / 6),
          runs,
          wickets,
          ballLabels: labels,
          teamTotalRuns: recalculated.totalRuns,
          teamTotalWickets: recalculated.totalWickets,
          battingTeamName: bTeam.name
        });
      }

      const bowlingTeam = ScoringEngine.isTeamA(recalculated.bowlingTeamId, recalculated) ? recalculated.teamA : recalculated.teamB;
      const bPlayer = bowlingTeam.players.find(p => p.id === ball.bowlerId);
      if (bPlayer && recalculated.maxOversPerBowler) {
        const base = recalculated.maxOversPerBowler;
        const qLimit = recalculated.quotaMaxOvers || base;
        const qCount = recalculated.quotaBowlersCount || 0;
        const pOvers = bPlayer.bowlingStats.overs;
        const othersUsingQuota = bowlingTeam.players.filter(p => p.id !== ball.bowlerId && (p.bowlingStats.overs > base || (p.bowlingStats.overs === base && p.bowlingStats.balls > 0))).length;

        if (pOvers >= qLimit || (pOvers >= base && othersUsingQuota >= qCount)) {
          setBowlerNotification(`${bPlayer.name} has completed their spell! 🛑`);
        }
      }
    }

    setMatch(recalculated);
  };

  const handleToss = (winnerId: string, decision: 'BAT' | 'BOWL') => {
    if (!match) return;
    const battingTeamId = ((winnerId === match.teamA.id && decision === 'BAT') || (winnerId === match.teamB.id && decision === 'BOWL')) ? match.teamA.id : match.teamB.id;
    const bowlingTeamId = battingTeamId === match.teamA.id ? match.teamB.id : match.teamA.id;

    const updatedMatch: Match = {
      ...match,
      tossWinnerId: winnerId,
      tossDecision: decision,
      initialBattingTeamId: battingTeamId,
      initialBowlingTeamId: bowlingTeamId,
      battingTeamId,
      bowlingTeamId,
      status: MatchStatus.LIVE,
      pendingAction: PendingAction.NONE
    };

    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const handleRuns = (runs: number, rotateStrike = true) => {
    if (!match || !match.strikerId) return;
    recordBall({
      runs,
      extrasType: ExtrasType.NONE,
      extraRuns: 0,
      wicketType: WicketType.NONE,
      strikerId: match.strikerId || null,
      nonStrikerId: match.nonStrikerId || null,
      bowlerId: match.currentBowlerId || null,
      fielderId: null,
      isLegalBall: true,
      outPlayerId: null,
      rotateStrike,
      hadCrossed: false,
      isDroppedCatch: false,
      dismissalReason: null,
      isAdjustment: false,
      adjustmentSlot: null,
      adjustmentPlayerId: null,
      isReplacement: false
    });
  };

  const handleExtra = (type: ExtrasType, extraRuns: number) => {
    if (!match || !match.strikerId) return;
    const noPenalty = match.gullyRules.noExtraRunsForWidesNoBalls;
    const extraPenalty = noPenalty ? 0 : 1;

    let ball: Ball;
    if (type === ExtrasType.NO_BALL) {
      ball = {
        runs: extraRuns, extrasType: type, extraRuns: extraPenalty, wicketType: WicketType.NONE,
        strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
        fielderId: null, isLegalBall: false, outPlayerId: null, rotateStrike: true,
        hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
    } else if (type === ExtrasType.WIDE) {
      ball = {
        runs: 0, extrasType: type, extraRuns: extraRuns + extraPenalty, wicketType: WicketType.NONE,
        strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
        fielderId: null, isLegalBall: false, outPlayerId: null, rotateStrike: true,
        hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
    } else if (type === ExtrasType.GRANTED) {
      ball = {
        runs: extraRuns, extrasType: type, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
        fielderId: null, isLegalBall: true, outPlayerId: null, rotateStrike: true,
        hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
    } else {
      ball = {
        runs: 0, extrasType: type, extraRuns, wicketType: WicketType.NONE,
        strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
        fielderId: null, isLegalBall: true, outPlayerId: null, rotateStrike: true,
        hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      };
    }
    recordBall(ball);
  };

  const handleWicket = (type: WicketType, victimId?: string | null) => {
    if (!match || !match.strikerId) return;

    if (type === WicketType.RUN_OUT) {
      setActiveWicketContext({
        type, initialStrikerId: match.strikerId, initialNonStrikerId: match.nonStrikerId || '',
        initialBowlerId: match.currentBowlerId || '', completedRuns: 0, brokenEnd: 'STRIKER',
        expectedReplacementAction: PendingAction.NONE
      });
      setMatch({ ...match, pendingAction: PendingAction.SELECT_RUNS_WICKET });
      return;
    }

    if (type === WicketType.CAUGHT) {
      setActiveWicketContext({
        type, initialStrikerId: match.strikerId, initialNonStrikerId: match.nonStrikerId || '',
        initialBowlerId: match.currentBowlerId || '', completedRuns: 0, brokenEnd: 'STRIKER',
        expectedReplacementAction: PendingAction.NONE
      });
      setMatch({ ...match, pendingAction: PendingAction.SELECT_FIELDER });
      return;
    }

    recordBall({
      runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: type,
      strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
      fielderId: null, isLegalBall: type !== WicketType.RETIRED_HURT, outPlayerId: victimId || match.strikerId || null,
      rotateStrike: false, hadCrossed: false, isDroppedCatch: false, dismissalReason: null,
      isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
    });
  };

  const handleRunOutWicket = (runs: number, outId: string, rotate: boolean, fielderId?: string | null) => {
    if (!match || !activeWicketContext) return;
    recordBall({
      runs, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.RUN_OUT,
      strikerId: activeWicketContext.initialStrikerId, nonStrikerId: activeWicketContext.initialNonStrikerId,
      bowlerId: activeWicketContext.initialBowlerId, fielderId: fielderId || null, isLegalBall: true,
      outPlayerId: outId, rotateStrike: rotate, hadCrossed: false, isDroppedCatch: false, dismissalReason: null,
      isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
    });
    setActiveWicketContext(null);
  };

  const selectFielder = (fielderId: string) => {
    if (!match || !activeWicketContext) return;
    if (activeWicketContext.type === WicketType.CAUGHT) {
      recordBall({
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.CAUGHT,
        strikerId: activeWicketContext.initialStrikerId, nonStrikerId: activeWicketContext.initialNonStrikerId,
        bowlerId: activeWicketContext.initialBowlerId, fielderId, isLegalBall: true, outPlayerId: activeWicketContext.initialStrikerId,
        rotateStrike: false, hadCrossed: false, isDroppedCatch: false, dismissalReason: null,
        isAdjustment: false, adjustmentSlot: null, adjustmentPlayerId: null, isReplacement: false
      });
      setActiveWicketContext(null);
    }
  };

  const assignPlayerToAction = (playerId: string) => {
    if (!match) return;
    const action = match.pendingAction || PendingAction.NONE;
    const isManualSub = action === PendingAction.REPLACE_STRIKER || action === PendingAction.REPLACE_NON_STRIKER || action === PendingAction.REPLACE_BOWLER;

    let adjustmentSlot: string | null = null;
    if (action === PendingAction.SELECT_STRIKER || action === PendingAction.REPLACE_STRIKER) adjustmentSlot = 'STRIKER';
    if (action === PendingAction.SELECT_NON_STRIKER || action === PendingAction.REPLACE_NON_STRIKER) adjustmentSlot = 'NON_STRIKER';
    if (action === PendingAction.SELECT_BOWLER || action === PendingAction.REPLACE_BOWLER) adjustmentSlot = 'BOWLER';

    if (adjustmentSlot) {
      recordBall({
        runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
        strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
        fielderId: null, isLegalBall: false, outPlayerId: null, rotateStrike: false, hadCrossed: false,
        isDroppedCatch: false, dismissalReason: null, isAdjustment: true, adjustmentSlot,
        adjustmentPlayerId: playerId, isReplacement: isManualSub
      });
    }
  };

  const swapStrike = () => {
    if (!match || !match.strikerId || !match.nonStrikerId) return;
    recordBall({
      runs: 0, extrasType: ExtrasType.NONE, extraRuns: 0, wicketType: WicketType.NONE,
      strikerId: match.strikerId || null, nonStrikerId: match.nonStrikerId || null, bowlerId: match.currentBowlerId || null,
      fielderId: null, isLegalBall: false, outPlayerId: null, rotateStrike: false,
      hadCrossed: false, isDroppedCatch: false, dismissalReason: null, isAdjustment: true,
      adjustmentSlot: 'SWAP', adjustmentPlayerId: null, isReplacement: false
    });
  };

  const undo = () => {
    if (!match || match.ballHistory.length === 0) return;
    const newHistory = match.ballHistory.slice(0, -1);
    const updatedMatch: Match = {
      ...match,
      ballHistory: newHistory,
      strikerId: null,
      nonStrikerId: null,
      currentBowlerId: null,
      status: MatchStatus.LIVE,
      winnerId: null,
      endTimeMillis: null
    };
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const startSecondInnings = () => {
    if (!match) return;
    const updatedMatch: Match = {
      ...match,
      isSecondInningsStarted: true,
      innings2StartTimeMillis: Date.now(),
      pendingAction: PendingAction.NONE,
      strikerId: null,
      nonStrikerId: null,
      currentBowlerId: null,
      lastBowlerId: null
    };
    ScoringEngine.clearCache(match.id);
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const forceChangeBowler = () => {
    if (!match) return;
    setMatch({ ...match, currentBowlerId: null, pendingAction: PendingAction.SELECT_BOWLER });
  };

  const updateMatchSettings = (overs: number, maxOvers?: number | null, quotaCount?: number | null, quotaLimit?: number | null) => {
    if (!match) return;
    const updatedMatch: Match = {
      ...match,
      oversPerInnings: overs,
      maxOversPerBowler: maxOvers ?? null,
      quotaBowlersCount: quotaCount ?? null,
      quotaMaxOvers: quotaLimit ?? null,
      pendingAction: PendingAction.NONE
    };
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const updateMatchGullyRules = (rules: GullyRules) => {
    if (!match) return;
    const updatedMatch = { ...match, gullyRules: rules };
    setMatch(updatedMatch);
    StorageAdapter.saveGullyRules(rules);
  };

  const addNewPlayerToMatch = (playerName: string, teamId: string, style?: any) => {
    if (!match) return;
    const masterPlayer = addPlayerToGlobalList(playerName, style);

    const isTeamA = match.teamA.id === teamId;
    const updatedTeamA = isTeamA ? { ...match.teamA, players: [...match.teamA.players, masterPlayer] } : match.teamA;
    const updatedTeamB = !isTeamA ? { ...match.teamB, players: [...match.teamB.players, masterPlayer] } : match.teamB;

    const updatedMatch: Match = { ...match, teamA: updatedTeamA, teamB: updatedTeamB };
    ScoringEngine.clearCache(match.id);
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
    if (match.tournamentId) addPlayersToTeam(match.tournamentId, teamId, [masterPlayer]);
  };

  const addGlobalPlayersToMatch = (players: Player[], teamId: string) => {
    if (!match) return;
    const isTeamA = match.teamA.id === teamId;
    const updatedTeamA = isTeamA ? { ...match.teamA, players: [...match.teamA.players, ...players] } : match.teamA;
    const updatedTeamB = !isTeamA ? { ...match.teamB, players: [...match.teamB.players, ...players] } : match.teamB;

    const updatedMatch: Match = { ...match, teamA: updatedTeamA, teamB: updatedTeamB };
    ScoringEngine.clearCache(match.id);
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
    if (match.tournamentId) addPlayersToTeam(match.tournamentId, teamId, players);
  };

  const deletePlayerFromMatch = (playerId: string) => {
    if (!match) return;
    if (playerId === match.strikerId || playerId === match.nonStrikerId || playerId === match.currentBowlerId) return;

    const updatedTeamA = { ...match.teamA, players: match.teamA.players.filter(p => p.id !== playerId) };
    const updatedTeamB = { ...match.teamB, players: match.teamB.players.filter(p => p.id !== playerId) };

    const updatedMatch: Match = { ...match, teamA: updatedTeamA, teamB: updatedTeamB };
    ScoringEngine.clearCache(match.id);
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const updatePlayerInMatch = (playerId: string, newName: string, bStyle: any, isCaptain: boolean, isViceCaptain: boolean) => {
    if (!match) return;
    const updateList = (players: Player[]) => players.map(p => p.id === playerId ? { ...p, name: newName, battingStyle: bStyle, isCaptain, isViceCaptain } : p);

    const updatedMatch: Match = {
      ...match,
      teamA: { ...match.teamA, players: updateList(match.teamA.players) },
      teamB: { ...match.teamB, players: updateList(match.teamB.players) }
    };
    ScoringEngine.clearCache(match.id);
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const editBall = (index: number, updatedBall: Ball) => {
    if (!match || index < 0 || index >= match.ballHistory.length) return;
    const newHistory = [...match.ballHistory];
    newHistory[index] = updatedBall;

    const updatedMatch: Match = { ...match, ballHistory: newHistory, strikerId: null, nonStrikerId: null, currentBowlerId: null };
    const recalculated = ScoringEngine.recalculateMatchFromHistory(updatedMatch);
    setMatch(recalculated);
  };

  const dismissOverSummary = () => setFinishedOverSummary(null);
  const clearBowlerNotification = () => setBowlerNotification(null);
  const cancelPendingAction = () => {
    if (match) setMatch({ ...match, pendingAction: PendingAction.NONE });
  };

  const uiState: MatchUiState = {
    match,
    bowlerNotification,
    activeWicketContext,
    isSyncEnabled: false,
    connectedDevicesCount: 0,
    finishedOverSummary
  };

  return (
    <MatchContext.Provider value={{
      match,
      uiState,
      loadMatch,
      handleToss,
      handleRuns,
      handleExtra,
      handleWicket,
      handleRunOutWicket,
      selectFielder,
      assignPlayerToAction,
      swapStrike,
      undo,
      startSecondInnings,
      forceChangeBowler,
      updateMatchSettings,
      updateMatchGullyRules,
      addNewPlayerToMatch,
      addGlobalPlayersToMatch,
      deletePlayerFromMatch,
      updatePlayerInMatch,
      editBall,
      dismissOverSummary,
      clearBowlerNotification,
      cancelPendingAction
    }}>
      {children}
    </MatchContext.Provider>
  );
};

export const useMatch = () => {
  const context = useContext(MatchContext);
  if (!context) throw new Error('useMatch must be used within MatchProvider');
  return context;
};
