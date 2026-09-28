import React, { useState } from 'react';
import { Play, Calendar, Trophy, ChevronRight, Activity } from 'lucide-react';
import { useTournament } from '../../state/TournamentContext';
import { useMatch } from '../../state/MatchContext';
import { MatchStatus, Match } from '../../domain/models';

interface MatchesScreenProps {
  onStartQuickMatch: () => void;
  onOpenLiveMatch: () => void;
}

export const MatchesScreen: React.FC<MatchesScreenProps> = ({ onStartQuickMatch, onOpenLiveMatch }) => {
  const { tournaments } = useTournament();
  const { loadMatch } = useMatch();
  const [filter, setFilter] = useState<'ALL' | 'LIVE' | 'COMPLETED'>('ALL');

  const allMatches = tournaments.flatMap(t => t.matches);
  const filteredMatches = allMatches.filter(m => {
    if (filter === 'LIVE') return m.status === MatchStatus.LIVE;
    if (filter === 'COMPLETED') return m.status === MatchStatus.COMPLETED;
    return true;
  });

  return (
    <div className="max-w-4xl mx-auto space-y-5 pb-12">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">Match History & Live</h1>
          <p className="text-xs text-slate-500 mt-0.5">Filter and manage all matches across tournaments</p>
        </div>
        <button
          onClick={onStartQuickMatch}
          className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold px-4 py-2 rounded-xl text-xs flex items-center space-x-1.5 shadow-md"
        >
          <Play className="w-3.5 h-3.5 fill-white" />
          <span>Quick Match</span>
        </button>
      </div>

      {/* Filter Chips */}
      <div className="flex space-x-2">
        {(['ALL', 'LIVE', 'COMPLETED'] as const).map(f => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              filter === f
                ? 'bg-slate-900 text-white dark:bg-emerald-600 shadow-sm'
                : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700'
            }`}
          >
            {f}
          </button>
        ))}
      </div>

      {/* Matches List */}
      {filteredMatches.length === 0 ? (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-12 text-center text-slate-400 space-y-3">
          <Calendar className="w-12 h-12 mx-auto text-slate-300" />
          <p className="font-semibold text-sm">No matches found for this filter.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredMatches.map(m => {
            const isLive = m.status === MatchStatus.LIVE;
            return (
              <div
                key={m.id}
                onClick={() => {
                  loadMatch(m);
                  onOpenLiveMatch();
                }}
                className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-5 shadow-sm hover:border-emerald-500 cursor-pointer transition-all space-y-3 group"
              >
                <div className="flex items-center justify-between text-xs font-semibold text-slate-400">
                  <span className="flex items-center space-x-1">
                    <Trophy className="w-3.5 h-3.5 text-slate-400" />
                    <span>{m.tournamentName || 'Quick Match'}</span>
                  </span>
                  {isLive ? (
                    <span className="flex items-center space-x-1 text-emerald-600 font-extrabold uppercase">
                      <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
                      <span>LIVE</span>
                    </span>
                  ) : (
                    <span>{new Date(m.dateMillis).toLocaleDateString()}</span>
                  )}
                </div>

                <div className="flex items-center justify-between font-extrabold text-slate-900 dark:text-white text-base">
                  <span>{m.teamA.name} vs {m.teamB.name}</span>
                  <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-emerald-600 transition-colors" />
                </div>

                <div className="text-xs font-bold text-slate-500">
                  {m.status === MatchStatus.COMPLETED
                    ? (m.winnerId ? `🏆 Winner: ${m.winnerId === m.teamA.id ? m.teamA.name : m.teamB.name}` : 'Match Tied / Drawn')
                    : `${m.totalRuns}/${m.totalWickets} (${Math.floor(m.totalBalls/6)}.${m.totalBalls%6} ov)`
                  }
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
