import React, { useState } from 'react';
import { Trophy, Award, BarChart3, Search } from 'lucide-react';
import { useTournament } from '../../state/TournamentContext';
import { calculateStrikeRate, calculateEconomy } from '../../domain/models';

export const StatsScreen: React.FC = () => {
  const { tournaments, globalPlayers } = useTournament();
  const [search, setSearch] = useState('');

  // Top batters and bowlers across all tournaments/matches
  const topBatters = [...globalPlayers]
    .sort((a, b) => b.battingStats.runs - a.battingStats.runs)
    .slice(0, 5);

  const topBowlers = [...globalPlayers]
    .sort((a, b) => b.bowlingStats.wickets - a.bowlingStats.wickets)
    .slice(0, 5);

  const filteredPlayers = globalPlayers.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">Tournament Leaderboards & Stats</h1>
        <p className="text-xs text-slate-500 mt-0.5">Player career stats, top run getters, and top wicket takers</p>
      </div>

      {/* Top Performers Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Top Batters */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-3">
          <div className="flex items-center space-x-2 border-b border-slate-100 dark:border-slate-800 pb-3">
            <Trophy className="w-5 h-5 text-amber-500" />
            <h2 className="font-extrabold text-base text-slate-900 dark:text-white">Orange Cap (Top Runs)</h2>
          </div>
          <div className="space-y-2">
            {topBatters.length === 0 ? (
              <div className="text-center py-4 text-xs text-slate-400">No batting stats recorded yet.</div>
            ) : (
              topBatters.map((p, idx) => (
                <div key={p.id} className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 text-xs font-semibold">
                  <div className="flex items-center space-x-2">
                    <span className="w-5 font-black text-amber-500">{idx + 1}</span>
                    <span className="font-bold text-slate-900 dark:text-white">{p.name}</span>
                  </div>
                  <div className="font-mono text-emerald-600 font-extrabold text-sm">
                    {p.battingStats.runs} <span className="text-[10px] text-slate-400 font-normal">({p.battingStats.balls}b)</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Top Bowlers */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-3">
          <div className="flex items-center space-x-2 border-b border-slate-100 dark:border-slate-800 pb-3">
            <Award className="w-5 h-5 text-purple-500" />
            <h2 className="font-extrabold text-base text-slate-900 dark:text-white">Purple Cap (Top Wickets)</h2>
          </div>
          <div className="space-y-2">
            {topBowlers.length === 0 ? (
              <div className="text-center py-4 text-xs text-slate-400">No bowling stats recorded yet.</div>
            ) : (
              topBowlers.map((p, idx) => (
                <div key={p.id} className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 text-xs font-semibold">
                  <div className="flex items-center space-x-2">
                    <span className="w-5 font-black text-purple-500">{idx + 1}</span>
                    <span className="font-bold text-slate-900 dark:text-white">{p.name}</span>
                  </div>
                  <div className="font-mono text-purple-600 font-extrabold text-sm">
                    {p.bowlingStats.wickets} wkts <span className="text-[10px] text-slate-400 font-normal">({p.bowlingStats.runsConceded}r)</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Player Career Search Table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 dark:border-slate-800 pb-3">
          <h2 className="font-extrabold text-base text-slate-900 dark:text-white flex items-center space-x-2">
            <BarChart3 className="w-4 h-4 text-emerald-600" />
            <span>Player Performance Cards</span>
          </h2>
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              placeholder="Search player name..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="pl-9 pr-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-xs font-semibold outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs font-semibold">
            <thead className="bg-slate-50 dark:bg-slate-800/50 text-slate-400 uppercase border-b border-slate-100 dark:border-slate-800">
              <tr>
                <th className="p-3">Player</th>
                <th className="p-3">Bat Runs</th>
                <th className="p-3">SR</th>
                <th className="p-3">4s/6s</th>
                <th className="p-3">Wickets</th>
                <th className="p-3 text-right">Econ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-slate-800 dark:text-slate-200">
              {filteredPlayers.map(p => {
                const sr = calculateStrikeRate(p.battingStats.runs, p.battingStats.balls);
                const econ = calculateEconomy(p.bowlingStats.runsConceded, p.bowlingStats.overs, p.bowlingStats.balls);
                return (
                  <tr key={p.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30">
                    <td className="p-3 font-bold">{p.name}</td>
                    <td className="p-3 font-extrabold text-emerald-600">{p.battingStats.runs}</td>
                    <td className="p-3 font-mono">{sr.toFixed(1)}</td>
                    <td className="p-3">{p.battingStats.fours}/{p.battingStats.sixes}</td>
                    <td className="p-3 font-extrabold text-purple-600">{p.bowlingStats.wickets}</td>
                    <td className="p-3 text-right font-mono">{econ.toFixed(1)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
