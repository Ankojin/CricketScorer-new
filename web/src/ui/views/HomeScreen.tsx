import React from 'react';
import { Play, Trophy, Users, ShieldAlert, ChevronRight, Sparkles, Activity, Clock } from 'lucide-react';
import { useTournament } from '../../state/TournamentContext';
import { useMatch } from '../../state/MatchContext';

interface HomeScreenProps {
  onStartQuickMatch: () => void;
  onOpenLiveMatch: () => void;
  onNavigateTab: (tab: 'matches' | 'teams' | 'stats' | 'more') => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  onStartQuickMatch,
  onOpenLiveMatch,
  onNavigateTab,
}) => {
  const { tournaments } = useTournament();
  const { match, loadMatch } = useMatch();

  const allMatches = tournaments.flatMap(t => t.matches);
  const liveMatch = allMatches.find(m => m.status === 'LIVE') || match;
  const recentMatches = allMatches.filter(m => m.status === 'COMPLETED').slice(0, 3);

  return (
    <div className="space-y-6 pb-12">
      {/* Hero Banner / Quick Match Action */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-emerald-600 via-teal-700 to-slate-900 text-white p-6 sm:p-8 shadow-xl">
        <div className="absolute top-0 right-0 -mt-10 -mr-10 w-64 h-64 bg-white/10 rounded-full blur-2xl pointer-events-none" />
        <div className="relative z-10 max-w-2xl space-y-4">
          <div className="inline-flex items-center space-x-2 bg-emerald-500/20 backdrop-blur-md border border-emerald-400/30 px-3 py-1 rounded-full text-xs font-semibold text-emerald-200">
            <Sparkles className="w-3.5 h-3.5 text-emerald-300" />
            <span>CricScore Pro UI v2 Experience</span>
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight leading-tight">
            Professional Cricket Scoring at Your Fingertips
          </h1>
          <p className="text-emerald-100/90 text-sm sm:text-base font-normal">
            Ball-by-ball processing, Gully rules, Last Man Standing, statistics & custom match setups.
          </p>
          <div className="pt-2 flex flex-wrap gap-3">
            <button
              onClick={onStartQuickMatch}
              className="inline-flex items-center space-x-2 bg-white text-emerald-800 hover:bg-emerald-50 font-bold px-6 py-3 rounded-2xl shadow-lg transition-transform active:scale-95 text-sm sm:text-base"
            >
              <Play className="w-5 h-5 fill-emerald-800" />
              <span>Start Quick Match</span>
            </button>
            {liveMatch && (
              <button
                onClick={() => {
                  loadMatch(liveMatch);
                  onOpenLiveMatch();
                }}
                className="inline-flex items-center space-x-2 bg-emerald-500/30 hover:bg-emerald-500/40 border border-emerald-400/40 text-white font-bold px-5 py-3 rounded-2xl transition-all text-sm sm:text-base backdrop-blur-sm"
              >
                <Activity className="w-5 h-5 text-emerald-300 animate-pulse" />
                <span>Resume Live Match</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Live Match Widget Card */}
      {liveMatch && (
        <div className="bg-white dark:bg-slate-900 border-2 border-emerald-500/30 rounded-2xl p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-ping" />
              <span className="text-xs font-extrabold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">LIVE NOW</span>
              <span className="text-xs text-slate-400">• {liveMatch.tournamentName || 'Quick Match'}</span>
            </div>
            <button
              onClick={() => {
                loadMatch(liveMatch);
                onOpenLiveMatch();
              }}
              className="text-xs font-bold text-emerald-600 hover:underline flex items-center space-x-1"
            >
              <span>Scoreboard</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="grid grid-cols-2 gap-4 items-center pt-1">
            <div className="space-y-1">
              <div className="text-base font-bold text-slate-800 dark:text-slate-100">{liveMatch.teamA.name}</div>
              <div className="text-sm font-semibold text-slate-500">
                {liveMatch.battingTeamId === liveMatch.teamA.id ? (
                  <span className="text-emerald-600 font-extrabold text-base">{liveMatch.totalRuns}/{liveMatch.totalWickets} <span className="text-xs font-normal">({Math.floor(liveMatch.totalBalls/6)}.{liveMatch.totalBalls%6} ov)</span></span>
                ) : 'Yet to Bat'}
              </div>
            </div>
            <div className="space-y-1 text-right">
              <div className="text-base font-bold text-slate-800 dark:text-slate-100">{liveMatch.teamB.name}</div>
              <div className="text-sm font-semibold text-slate-500">
                {liveMatch.battingTeamId === liveMatch.teamB.id ? (
                  <span className="text-emerald-600 font-extrabold text-base">{liveMatch.totalRuns}/{liveMatch.totalWickets} <span className="text-xs font-normal">({Math.floor(liveMatch.totalBalls/6)}.{liveMatch.totalBalls%6} ov)</span></span>
                ) : (liveMatch.innings1Data && liveMatch.innings1Data.teamId === liveMatch.teamB.id ? `${liveMatch.innings1Data.runs}/${liveMatch.innings1Data.wickets}` : 'Yet to Bat')}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Action Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div
          onClick={onStartQuickMatch}
          className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 cursor-pointer hover:border-emerald-500 transition-all shadow-sm group"
        >
          <div className="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-950 text-emerald-600 flex items-center justify-center font-bold mb-3 group-hover:scale-110 transition-transform">
            <Play className="w-5 h-5 fill-emerald-600" />
          </div>
          <h3 className="font-bold text-sm text-slate-900 dark:text-white">Quick Match</h3>
          <p className="text-xs text-slate-500 mt-1">Teams → Toss → Live</p>
        </div>

        <div
          onClick={() => onNavigateTab('teams')}
          className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 cursor-pointer hover:border-emerald-500 transition-all shadow-sm group"
        >
          <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-950 text-blue-600 flex items-center justify-center font-bold mb-3 group-hover:scale-110 transition-transform">
            <Users className="w-5 h-5" />
          </div>
          <h3 className="font-bold text-sm text-slate-900 dark:text-white">Teams & Squads</h3>
          <p className="text-xs text-slate-500 mt-1">Manage players & styles</p>
        </div>

        <div
          onClick={() => onNavigateTab('stats')}
          className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 cursor-pointer hover:border-emerald-500 transition-all shadow-sm group"
        >
          <div className="w-10 h-10 rounded-xl bg-purple-50 dark:bg-purple-950 text-purple-600 flex items-center justify-center font-bold mb-3 group-hover:scale-110 transition-transform">
            <Trophy className="w-5 h-5" />
          </div>
          <h3 className="font-bold text-sm text-slate-900 dark:text-white">Tournaments</h3>
          <p className="text-xs text-slate-500 mt-1">Points table & stats</p>
        </div>

        <div
          onClick={() => onNavigateTab('more')}
          className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 cursor-pointer hover:border-emerald-500 transition-all shadow-sm group"
        >
          <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-950 text-amber-600 flex items-center justify-center font-bold mb-3 group-hover:scale-110 transition-transform">
            <ShieldAlert className="w-5 h-5" />
          </div>
          <h3 className="font-bold text-sm text-slate-900 dark:text-white">Gully Rules</h3>
          <p className="text-xs text-slate-500 mt-1">LMS & Street Cricket</p>
        </div>
      </div>

      {/* Recent Matches */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Clock className="w-4 h-4 text-slate-500" />
            <h2 className="font-extrabold text-base text-slate-900 dark:text-white">Recent Completed Matches</h2>
          </div>
          <button onClick={() => onNavigateTab('matches')} className="text-xs font-bold text-emerald-600 hover:underline">
            View All
          </button>
        </div>

        {recentMatches.length === 0 ? (
          <div className="text-center py-6 text-slate-400 text-sm">
            No completed matches yet. Start a Quick Match to record scores!
          </div>
        ) : (
          <div className="space-y-3">
            {recentMatches.map(m => {
              const winnerTeam = m.winnerId === m.teamA.id ? m.teamA : (m.winnerId === m.teamB.id ? m.teamB : null);
              return (
                <div key={m.id} className="border border-slate-100 dark:border-slate-800 rounded-xl p-3 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors">
                  <div className="flex items-center justify-between text-xs font-semibold text-slate-500 mb-1">
                    <span>{m.tournamentName || 'Quick Match'}</span>
                    <span>{new Date(m.dateMillis).toLocaleDateString()}</span>
                  </div>
                  <div className="flex items-center justify-between font-bold text-sm text-slate-800 dark:text-slate-100">
                    <span>{m.teamA.name} vs {m.teamB.name}</span>
                  </div>
                  <div className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 mt-1">
                    {winnerTeam ? `🏆 ${winnerTeam.name} won` : 'Match Drawn / Tied'}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
