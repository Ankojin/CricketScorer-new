import React, { useState } from 'react';
import {
  RotateCcw,
  RefreshCw,
  UserCheck,
  AlertCircle,
  X,
  Play,
  Check
} from 'lucide-react';
import { useMatch } from '../../state/MatchContext';
import { ExtrasType, WicketType, PendingAction, formatOvers } from '../../domain/models';

export const LiveScoringView: React.FC = () => {
  const {
    match,
    uiState,
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
    dismissOverSummary,
    clearBowlerNotification,
    cancelPendingAction
  } = useMatch();

  const [showWicketModal, setShowWicketModal] = useState(false);
  const [showExtrasModal, setShowExtrasModal] = useState(false);
  const [selectedExtraType, setSelectedExtraType] = useState<ExtrasType>(ExtrasType.WIDE);

  // Run-out modal state
  const [runOutRuns, setRunOutRuns] = useState(0);
  const [runOutOutId, setRunOutOutId] = useState<string>('');
  const [runOutRotate, setRunOutRotate] = useState(true);

  if (!match) {
    return (
      <div className="text-center py-12 text-slate-500 font-semibold">
        No active match loaded. Start a Quick Match or select a match from the Matches tab.
      </div>
    );
  }

  const isBattingA = match.battingTeamId === match.teamA.id;
  const battingTeam = isBattingA ? match.teamA : match.teamB;
  const bowlingTeam = isBattingA ? match.teamB : match.teamA;

  const striker = battingTeam.players.find(p => p.id === match.strikerId);
  const nonStriker = battingTeam.players.find(p => p.id === match.nonStrikerId);
  const bowler = bowlingTeam.players.find(p => p.id === match.currentBowlerId);

  const pendingAction = match.pendingAction || PendingAction.NONE;

  // Filter available players for selectors
  const availableBatters = battingTeam.players.filter(
    p => p.id !== match.strikerId && p.id !== match.nonStrikerId && !p.battingStats.isOut && !p.battingStats.isRetiredHurt
  );
  const availableBowlers = bowlingTeam.players.filter(p => p.id !== match.lastBowlerId);

  return (
    <div className="max-w-4xl mx-auto space-y-5 pb-12">
      {/* Over End Summary Banner */}
      {uiState.finishedOverSummary && (
        <div className="bg-emerald-600 text-white p-4 rounded-2xl shadow-lg flex items-center justify-between">
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-emerald-200">Over {uiState.finishedOverSummary.overNumber} Complete</div>
            <div className="font-extrabold text-lg">
              {uiState.finishedOverSummary.runs} Runs, {uiState.finishedOverSummary.wickets} Wkts ({uiState.finishedOverSummary.battingTeamName}: {uiState.finishedOverSummary.teamTotalRuns}/{uiState.finishedOverSummary.teamTotalWickets})
            </div>
            <div className="flex space-x-1.5 mt-2">
              {uiState.finishedOverSummary.ballLabels.map((lbl, idx) => (
                <span key={idx} className="px-2 py-0.5 rounded bg-emerald-700 font-mono text-xs font-bold">
                  {lbl}
                </span>
              ))}
            </div>
          </div>
          <button onClick={dismissOverSummary} className="p-2 bg-emerald-700 hover:bg-emerald-800 rounded-xl">
            <X className="w-5 h-5" />
          </button>
        </div>
      )}

      {/* Bowler Notification Banner */}
      {uiState.bowlerNotification && (
        <div className="bg-amber-500 text-white p-3.5 rounded-2xl shadow-md flex items-center justify-between font-bold text-sm">
          <div className="flex items-center space-x-2">
            <AlertCircle className="w-5 h-5" />
            <span>{uiState.bowlerNotification}</span>
          </div>
          <button onClick={clearBowlerNotification} className="p-1 hover:bg-amber-600 rounded-lg">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Scoreboard Header Card */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 text-white rounded-3xl p-6 shadow-xl space-y-4">
        <div className="flex items-center justify-between border-b border-slate-700/60 pb-3 text-xs text-slate-400 font-semibold">
          <span>{match.tournamentName || 'Quick Match'} • {match.oversPerInnings} Overs</span>
          <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-bold border border-emerald-500/30">
            Innings {match.currentInnings}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <div>
            <div className="text-xl sm:text-2xl font-black">{battingTeam.name}</div>
            <div className="text-3xl sm:text-4xl font-extrabold text-emerald-400 mt-1">
              {match.totalRuns}/{match.totalWickets}
              <span className="text-lg font-normal text-slate-300 ml-2">
                ({formatOvers(Math.floor(match.totalBalls / 6), match.totalBalls % 6)} ov)
              </span>
            </div>
          </div>

          {match.currentInnings === 2 && match.target && (
            <div className="text-right bg-slate-800/80 p-3 rounded-2xl border border-slate-700">
              <div className="text-xs text-slate-400 font-bold uppercase">Target: {match.target}</div>
              <div className="text-sm font-extrabold text-amber-400 mt-0.5">
                Need {match.target - match.totalRuns} runs in {match.oversPerInnings * 6 - match.totalBalls} balls
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Pending Action Prompt / Selectors */}
      {pendingAction !== PendingAction.NONE && (
        <div className="bg-amber-50 border-2 border-amber-400 dark:bg-amber-950/40 rounded-2xl p-4 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <span className="font-extrabold text-amber-900 dark:text-amber-200 text-sm flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 text-amber-600" />
              <span>
                {pendingAction === PendingAction.SELECT_STRIKER && 'Select Next Striker'}
                {pendingAction === PendingAction.SELECT_NON_STRIKER && 'Select Non-Striker'}
                {pendingAction === PendingAction.SELECT_BOWLER && 'Select Next Bowler'}
                {pendingAction === PendingAction.START_SECOND_INNINGS && 'First Innings Finished!'}
                {pendingAction === PendingAction.SELECT_FIELDER && 'Select Fielder for Catch'}
                {pendingAction === PendingAction.SELECT_RUNS_WICKET && 'Run Out Details'}
                {pendingAction === PendingAction.REPLACE_STRIKER && 'Replace Striker'}
                {pendingAction === PendingAction.REPLACE_BOWLER && 'Replace Bowler'}
              </span>
            </span>
            <button onClick={cancelPendingAction} className="text-amber-700 text-xs font-bold hover:underline">
              Dismiss
            </button>
          </div>

          {pendingAction === PendingAction.START_SECOND_INNINGS && (
            <button
              onClick={startSecondInnings}
              className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold py-3 rounded-xl flex items-center justify-center space-x-2"
            >
              <Play className="w-5 h-5 fill-white" />
              <span>Start 2nd Innings</span>
            </button>
          )}

          {(pendingAction === PendingAction.SELECT_STRIKER ||
            pendingAction === PendingAction.SELECT_NON_STRIKER ||
            pendingAction === PendingAction.REPLACE_STRIKER ||
            pendingAction === PendingAction.REPLACE_NON_STRIKER) && (
            <div className="flex flex-wrap gap-2 pt-1">
              {availableBatters.map(p => (
                <button
                  key={p.id}
                  onClick={() => assignPlayerToAction(p.id)}
                  className="px-3 py-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-xl text-xs font-bold hover:bg-emerald-50 text-slate-900 dark:text-white"
                >
                  + {p.name}
                </button>
              ))}
            </div>
          )}

          {(pendingAction === PendingAction.SELECT_BOWLER || pendingAction === PendingAction.REPLACE_BOWLER) && (
            <div className="flex flex-wrap gap-2 pt-1">
              {availableBowlers.map(p => (
                <button
                  key={p.id}
                  onClick={() => assignPlayerToAction(p.id)}
                  className="px-3 py-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-xl text-xs font-bold hover:bg-emerald-50 text-slate-900 dark:text-white"
                >
                  + {p.name}
                </button>
              ))}
            </div>
          )}

          {pendingAction === PendingAction.SELECT_FIELDER && (
            <div className="flex flex-wrap gap-2 pt-1">
              {bowlingTeam.players.map(p => (
                <button
                  key={p.id}
                  onClick={() => selectFielder(p.id)}
                  className="px-3 py-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-xl text-xs font-bold hover:bg-emerald-50 text-slate-900 dark:text-white"
                >
                  Caught by {p.name}
                </button>
              ))}
            </div>
          )}

          {pendingAction === PendingAction.SELECT_RUNS_WICKET && (
            <div className="space-y-3 pt-1">
              <div className="flex items-center space-x-2">
                <span className="text-xs font-bold">Completed Runs:</span>
                {[0, 1, 2, 3].map(r => (
                  <button
                    key={r}
                    onClick={() => setRunOutRuns(r)}
                    className={`px-3 py-1 rounded-lg text-xs font-bold ${runOutRuns === r ? 'bg-emerald-600 text-white' : 'bg-slate-200'}`}
                  >
                    {r}
                  </button>
                ))}
              </div>
              <div className="flex items-center space-x-2">
                <span className="text-xs font-bold">Dismissed Batter:</span>
                <button
                  onClick={() => setRunOutOutId(match.strikerId || '')}
                  className={`px-3 py-1 rounded-lg text-xs font-bold ${runOutOutId === match.strikerId ? 'bg-red-600 text-white' : 'bg-slate-200'}`}
                >
                  {striker?.name || 'Striker'}
                </button>
                <button
                  onClick={() => setRunOutOutId(match.nonStrikerId || '')}
                  className={`px-3 py-1 rounded-lg text-xs font-bold ${runOutOutId === match.nonStrikerId ? 'bg-red-600 text-white' : 'bg-slate-200'}`}
                >
                  {nonStriker?.name || 'Non-Striker'}
                </button>
              </div>
              <button
                onClick={() => handleRunOutWicket(runOutRuns, runOutOutId || match.strikerId || '', runOutRotate)}
                className="w-full py-2 bg-emerald-600 text-white font-bold rounded-xl text-xs"
              >
                Confirm Run Out
              </button>
            </div>
          )}
        </div>
      )}

      {/* Active Players Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Batters Card */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 shadow-sm space-y-3">
          <div className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Batting</div>
          <div className="space-y-2">
            <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800">
              <div className="flex items-center space-x-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                <span className="font-bold text-sm text-slate-900 dark:text-white">{striker?.name || 'Striker'} *</span>
              </div>
              <div className="font-extrabold text-sm text-emerald-600">
                {striker?.battingStats.runs || 0} <span className="text-xs text-slate-400 font-normal">({striker?.battingStats.balls || 0})</span>
              </div>
            </div>

            <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800">
              <div className="flex items-center space-x-2">
                <span className="w-2 h-2 rounded-full bg-slate-300" />
                <span className="font-bold text-sm text-slate-900 dark:text-white">{nonStriker?.name || 'Non-Striker'}</span>
              </div>
              <div className="font-extrabold text-sm text-slate-700 dark:text-slate-300">
                {nonStriker?.battingStats.runs || 0} <span className="text-xs text-slate-400 font-normal">({nonStriker?.battingStats.balls || 0})</span>
              </div>
            </div>
          </div>
        </div>

        {/* Bowler Card */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Bowling</span>
            <button onClick={forceChangeBowler} className="text-xs font-bold text-emerald-600 hover:underline">
              Change
            </button>
          </div>
          <div className="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 flex items-center justify-between">
            <span className="font-bold text-sm text-slate-900 dark:text-white">{bowler?.name || 'Select Bowler'}</span>
            <div className="font-extrabold text-sm text-emerald-600">
              {bowler?.bowlingStats.wickets || 0}/{bowler?.bowlingStats.runsConceded || 0}{' '}
              <span className="text-xs text-slate-400 font-normal">({formatOvers(bowler?.bowlingStats.overs || 0, bowler?.bowlingStats.balls || 0)})</span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Scoring Keypad */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-4">
        <div className="grid grid-cols-6 gap-2">
          {[0, 1, 2, 3, 4, 6].map(runs => (
            <button
              key={runs}
              onClick={() => handleRuns(runs)}
              className={`py-4 rounded-2xl font-black text-xl shadow-sm transition-transform active:scale-95 ${
                runs === 4
                  ? 'bg-blue-600 text-white hover:bg-blue-700'
                  : runs === 6
                  ? 'bg-purple-600 text-white hover:bg-purple-700'
                  : 'bg-slate-100 dark:bg-slate-800 text-slate-900 dark:text-white hover:bg-slate-200'
              }`}
            >
              {runs}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <button
            onClick={() => { setSelectedExtraType(ExtrasType.WIDE); setShowExtrasModal(true); }}
            className="py-3 bg-amber-100 text-amber-900 font-extrabold rounded-xl text-sm hover:bg-amber-200"
          >
            WD / EXTRAS
          </button>
          <button
            onClick={() => setShowWicketModal(true)}
            className="py-3 bg-red-600 text-white font-extrabold rounded-xl text-sm hover:bg-red-700"
          >
            WICKET
          </button>
          <button
            onClick={swapStrike}
            className="py-3 bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 font-bold rounded-xl text-xs flex items-center justify-center space-x-1"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>SWAP STRIKE</span>
          </button>
          <button
            onClick={undo}
            className="py-3 bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 font-bold rounded-xl text-xs flex items-center justify-center space-x-1"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>UNDO</span>
          </button>
        </div>
      </div>

      {/* Extras Modal */}
      {showExtrasModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 max-w-sm w-full space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-extrabold text-base">Record Extras</h3>
              <button onClick={() => setShowExtrasModal(false)}><X className="w-5 h-5" /></button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button onClick={() => { handleExtra(ExtrasType.WIDE, 0); setShowExtrasModal(false); }} className="p-3 bg-amber-50 text-amber-900 font-bold rounded-xl text-xs">
                Wide (+1)
              </button>
              <button onClick={() => { handleExtra(ExtrasType.NO_BALL, 0); setShowExtrasModal(false); }} className="p-3 bg-amber-50 text-amber-900 font-bold rounded-xl text-xs">
                No Ball (+1)
              </button>
              <button onClick={() => { handleExtra(ExtrasType.BYE, 1); setShowExtrasModal(false); }} className="p-3 bg-slate-100 font-bold rounded-xl text-xs">
                1 Bye
              </button>
              <button onClick={() => { handleExtra(ExtrasType.LEG_BYE, 1); setShowExtrasModal(false); }} className="p-3 bg-slate-100 font-bold rounded-xl text-xs">
                1 Leg Bye
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Wicket Modal */}
      {showWicketModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 max-w-sm w-full space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-extrabold text-base">Select Wicket Type</h3>
              <button onClick={() => setShowWicketModal(false)}><X className="w-5 h-5" /></button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {[
                { type: WicketType.BOWLED, label: 'Bowled' },
                { type: WicketType.CAUGHT, label: 'Caught' },
                { type: WicketType.LBW, label: 'LBW' },
                { type: WicketType.RUN_OUT, label: 'Run Out' },
                { type: WicketType.STUMPED, label: 'Stumped' },
                { type: WicketType.RETIRED_HURT, label: 'Retired Hurt' },
              ].map(w => (
                <button
                  key={w.type}
                  onClick={() => {
                    handleWicket(w.type);
                    setShowWicketModal(false);
                  }}
                  className="p-3 bg-red-50 text-red-900 font-extrabold rounded-xl text-xs hover:bg-red-100"
                >
                  {w.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
