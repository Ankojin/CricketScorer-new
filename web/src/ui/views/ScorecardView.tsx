import React, { useState } from 'react';
import { useMatch } from '../../state/MatchContext';
import { formatOvers, calculateStrikeRate, calculateEconomy } from '../../domain/models';
import { StatsCalculator } from '../../domain/statsCalculator';

export const ScorecardView: React.FC = () => {
  const { match } = useMatch();
  const [activeInnings, setActiveInnings] = useState<1 | 2>(1);

  if (!match) {
    return <div className="text-center py-12 text-slate-500">No active match scorecard available.</div>;
  }

  const isBattingA = (activeInnings === 1 ? match.initialBattingTeamId : match.initialBowlingTeamId) === match.teamA.id;
  const battingTeam = isBattingA ? match.teamA : match.teamB;
  const bowlingTeam = isBattingA ? match.teamB : match.teamA;

  const isInnings1 = activeInnings === 1;
  const totalRuns = isInnings1
    ? (match.currentInnings === 1 ? match.totalRuns : (match.innings1Data?.runs || 0))
    : (match.currentInnings === 2 ? match.totalRuns : 0);

  const totalWickets = isInnings1
    ? (match.currentInnings === 1 ? match.totalWickets : (match.innings1Data?.wickets || 0))
    : (match.currentInnings === 2 ? match.totalWickets : 0);

  const totalBalls = isInnings1
    ? (match.currentInnings === 1 ? match.totalBalls : (match.innings1Data?.balls || 0))
    : (match.currentInnings === 2 ? match.totalBalls : 0);

  const wideCount = isInnings1
    ? (match.currentInnings === 1 ? match.wideCount : (match.innings1Data?.wideCount || 0))
    : (match.currentInnings === 2 ? match.wideCount : 0);

  const noBallCount = isInnings1
    ? (match.currentInnings === 1 ? match.noBallCount : (match.innings1Data?.noBallCount || 0))
    : (match.currentInnings === 2 ? match.noBallCount : 0);

  const byeCount = isInnings1
    ? (match.currentInnings === 1 ? match.byeCount : (match.innings1Data?.byeCount || 0))
    : (match.currentInnings === 2 ? match.byeCount : 0);

  const legByeCount = isInnings1
    ? (match.currentInnings === 1 ? match.legByeCount : (match.innings1Data?.legByeCount || 0))
    : (match.currentInnings === 2 ? match.legByeCount : 0);

  const wicketHistory = isInnings1
    ? (match.currentInnings === 1 ? match.wicketHistory : (match.innings1Data?.wicketHistory || []))
    : (match.currentInnings === 2 ? match.wicketHistory : []);

  const activePartnership = match.currentInnings === activeInnings ? StatsCalculator.calculateActivePartnership(match) : null;

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Innings Selector Tabs */}
      <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-2xl">
        <button
          onClick={() => setActiveInnings(1)}
          className={`flex-1 py-2.5 rounded-xl font-extrabold text-sm transition-all ${
            activeInnings === 1 ? 'bg-white dark:bg-slate-900 text-emerald-600 shadow-sm' : 'text-slate-600 dark:text-slate-400'
          }`}
        >
          1st Innings ({match.initialBattingTeamId === match.teamA.id ? match.teamA.name : match.teamB.name})
        </button>
        {match.currentInnings >= 2 && (
          <button
            onClick={() => setActiveInnings(2)}
            className={`flex-1 py-2.5 rounded-xl font-extrabold text-sm transition-all ${
              activeInnings === 2 ? 'bg-white dark:bg-slate-900 text-emerald-600 shadow-sm' : 'text-slate-600 dark:text-slate-400'
            }`}
          >
            2nd Innings ({match.initialBowlingTeamId === match.teamA.id ? match.teamA.name : match.teamB.name})
          </button>
        )}
      </div>

      {/* Innings Summary Banner */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm flex items-center justify-between">
        <div>
          <div className="text-xs font-bold text-slate-400 uppercase">{battingTeam.name} Innings Summary</div>
          <div className="text-2xl font-black text-slate-900 dark:text-white mt-1">
            {totalRuns}/{totalWickets} <span className="text-sm font-normal text-slate-400">({formatOvers(Math.floor(totalBalls / 6), totalBalls % 6)} Ov)</span>
          </div>
        </div>
        <div className="text-right text-xs font-semibold text-slate-500">
          <div>Extras: {wideCount + noBallCount + byeCount + legByeCount}</div>
          <div className="text-[10px] text-slate-400 mt-0.5">({wideCount}wd, {noBallCount}nb, {byeCount}b, {legByeCount}lb)</div>
        </div>
      </div>

      {/* Active Partnership Card */}
      {activePartnership && (
        <div className="bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 rounded-3xl p-4 shadow-sm space-y-1">
          <div className="text-xs font-extrabold text-emerald-800 dark:text-emerald-300 uppercase">Active Partnership</div>
          <div className="font-extrabold text-sm text-slate-900 dark:text-white flex items-center justify-between">
            <span>{activePartnership.batter1Name} ({activePartnership.batter1Runs}r) & {activePartnership.batter2Name} ({activePartnership.batter2Runs}r)</span>
            <span className="font-mono text-emerald-600 dark:text-emerald-400">{activePartnership.totalRuns} runs ({activePartnership.totalBalls}b)</span>
          </div>
        </div>
      )}

      {/* Batting Table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-100 dark:border-slate-800 font-extrabold text-sm text-slate-900 dark:text-white">
          Batting Card
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-semibold">
            <thead className="bg-slate-50 dark:bg-slate-800/50 text-slate-400 uppercase border-b border-slate-100 dark:border-slate-800">
              <tr>
                <th className="p-3">Batter</th>
                <th className="p-3">R</th>
                <th className="p-3">B</th>
                <th className="p-3">4s</th>
                <th className="p-3">6s</th>
                <th className="p-3 text-right">SR</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-slate-800 dark:text-slate-200">
              {battingTeam.players.map(p => {
                const s = p.battingStats;
                const sr = calculateStrikeRate(s.runs, s.balls);
                return (
                  <tr key={p.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-bold">
                      <div>{p.name} {p.id === match.strikerId ? '*' : ''}</div>
                      <div className="text-[10px] text-slate-400 font-normal">
                        {s.isOut ? `b ${s.wicketType}` : (s.balls > 0 ? 'Not Out' : 'Yet to Bat')}
                      </div>
                    </td>
                    <td className="p-3 font-extrabold text-emerald-600">{s.runs}</td>
                    <td className="p-3">{s.balls}</td>
                    <td className="p-3">{s.fours}</td>
                    <td className="p-3">{s.sixes}</td>
                    <td className="p-3 text-right font-mono">{sr.toFixed(1)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Bowling Table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-100 dark:border-slate-800 font-extrabold text-sm text-slate-900 dark:text-white">
          Bowling Card
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-semibold">
            <thead className="bg-slate-50 dark:bg-slate-800/50 text-slate-400 uppercase border-b border-slate-100 dark:border-slate-800">
              <tr>
                <th className="p-3">Bowler</th>
                <th className="p-3">O</th>
                <th className="p-3">M</th>
                <th className="p-3">R</th>
                <th className="p-3">W</th>
                <th className="p-3 text-right">Econ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-slate-800 dark:text-slate-200">
              {bowlingTeam.players.filter(p => p.bowlingStats.overs > 0 || p.bowlingStats.balls > 0).map(p => {
                const bw = p.bowlingStats;
                const econ = calculateEconomy(bw.runsConceded, bw.overs, bw.balls);
                return (
                  <tr key={p.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-bold">{p.name} {p.id === match.currentBowlerId ? '*' : ''}</td>
                    <td className="p-3">{formatOvers(bw.overs, bw.balls)}</td>
                    <td className="p-3">{bw.maidens}</td>
                    <td className="p-3">{bw.runsConceded}</td>
                    <td className="p-3 font-extrabold text-emerald-600">{bw.wickets}</td>
                    <td className="p-3 text-right font-mono">{econ.toFixed(1)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Fall of Wickets */}
      {wicketHistory.length > 0 && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-3">
          <h3 className="font-extrabold text-sm text-slate-900 dark:text-white">Fall of Wickets</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
            {wicketHistory.map((w, idx) => (
              <div key={idx} className="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 flex justify-between items-center">
                <span className="font-bold text-slate-800 dark:text-slate-200">{w.batterName}</span>
                <span className="text-slate-500 font-mono">{w.totalRuns}/{w.wicketNumber} ({w.over} ov)</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
