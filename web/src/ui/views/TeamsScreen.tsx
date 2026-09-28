import React, { useState } from 'react';
import { Users, UserPlus, Trash2, Edit2, Shield, Star } from 'lucide-react';
import { useTournament } from '../../state/TournamentContext';
import { BattingStyle, Player } from '../../domain/models';

export const TeamsScreen: React.FC = () => {
  const { globalPlayers, addPlayerToGlobalList } = useTournament();
  const [newPlayerName, setNewPlayerName] = useState('');
  const [newPlayerStyle, setNewPlayerStyle] = useState<BattingStyle>(BattingStyle.RHB);

  const handleAddGlobalPlayer = () => {
    if (!newPlayerName.trim()) return;
    addPlayerToGlobalList(newPlayerName.trim(), newPlayerStyle);
    setNewPlayerName('');
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">Global Player Directory</h1>
          <p className="text-xs text-slate-500 mt-0.5">Manage master players and batting styles for all matches</p>
        </div>
      </div>

      {/* Add New Player Form */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-4">
        <h2 className="font-extrabold text-base text-slate-900 dark:text-white">Add Master Player</h2>
        <div className="flex flex-col sm:flex-row space-y-2 sm:space-y-0 sm:space-x-3">
          <input
            type="text"
            placeholder="Player Full Name"
            value={newPlayerName}
            onChange={e => setNewPlayerName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleAddGlobalPlayer()}
            className="flex-1 px-4 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-white font-semibold text-sm outline-none focus:ring-2 focus:ring-emerald-500"
          />
          <select
            value={newPlayerStyle}
            onChange={e => setNewPlayerStyle(e.target.value as BattingStyle)}
            className="px-3 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-white font-bold text-xs"
          >
            <option value={BattingStyle.RHB}>Right-Hand Bat (RHB)</option>
            <option value={BattingStyle.LHB}>Left-Hand Bat (LHB)</option>
          </select>
          <button
            onClick={handleAddGlobalPlayer}
            className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold px-5 py-2.5 rounded-xl text-xs flex items-center justify-center space-x-1.5 shadow-md"
          >
            <UserPlus className="w-4 h-4" />
            <span>Add Player</span>
          </button>
        </div>
      </div>

      {/* Global Players List */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
          <h2 className="font-extrabold text-base text-slate-900 dark:text-white flex items-center space-x-2">
            <Users className="w-4 h-4 text-emerald-600" />
            <span>All Registered Players ({globalPlayers.length})</span>
          </h2>
        </div>

        {globalPlayers.length === 0 ? (
          <div className="text-center py-8 text-slate-400 text-sm font-semibold">
            No players registered yet. Add players above to build your squads!
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {globalPlayers.map((p, idx) => (
              <div
                key={p.id}
                className="p-3.5 rounded-2xl border border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/50 flex items-center justify-between"
              >
                <div>
                  <div className="font-extrabold text-sm text-slate-900 dark:text-white flex items-center space-x-1.5">
                    <span>{p.name}</span>
                    {p.isCaptain && <span className="px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 text-[10px] font-black">C</span>}
                  </div>
                  <div className="text-xs text-slate-400 font-semibold mt-0.5">{p.battingStyle || 'RHB'}</div>
                </div>
                <div className="text-xs font-mono font-bold text-emerald-600">
                  {p.battingStats.runs} runs
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
