import React from 'react';
import { Play, Trophy, Users, ShieldAlert, ChevronRight, Sparkles, Activity, Clock, Zap } from 'lucide-react';
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
      {/* Hero Banner (Matching Image 1 & 2) */}
      <div className="relative overflow-hidden rounded-3xl bg-cricNavy-700 border border-cricBorder text-white p-6 sm:p-10 shadow-2xl flex flex-col md:flex-row items-center justify-between gap-6 text-center md:text-left">
        <div className="absolute top-0 right-0 -mt-10 -mr-10 w-72 h-72 bg-cricElectric-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 max-w-2xl space-y-4 mx-auto md:mx-0">
          <div className="inline-flex items-center space-x-2 bg-cricGreen-500/20 backdrop-blur-md border border-cricGreen-500/30 px-3.5 py-1.5 rounded-full text-xs font-bold text-cricElectric-500">
            <span className="w-2 h-2 rounded-full bg-cricElectric-500 animate-ping" />
            <span>LIVE CRICKET SCORING PLATFORM</span>
          </div>
          <h1 className="text-3xl sm:text-5xl font-black tracking-tight leading-none uppercase text-white">
            TRACK EVERY BALL. <br />
            OWN EVERY OVER. <br />
            <span className="text-cricElectric-500">SHARE EVERY MOMENT.</span>
          </h1>
          <p className="text-slate-300 text-sm sm:text-base font-medium max-w-lg">
            Score locally without an account. Sign in only when you need cloud data.
          </p>
          <div className="pt-2 flex flex-wrap justify-center md:justify-start gap-3">
            <button
              onClick={onStartQuickMatch}
              className="inline-flex items-center space-x-2 bg-cricGreen-500 hover:bg-emerald-400 text-cricNavy-900 font-black px-6 py-3.5 rounded-2xl shadow-lg transition-transform active:scale-95 text-sm sm:text-base"
            >
              <Zap className="w-5 h-5 fill-cricNavy-900" />
              <span>Web Score →</span>
            </button>
            <a
              href="https://play.google.com/store"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center space-x-2 bg-cricGreen-600 hover:bg-emerald-700 text-white font-bold px-5 py-3.5 rounded-2xl transition-all text-sm sm:text-base shadow-md"
            >
              <span>▶ GET IT ON Google Play</span>
            </a>
          </div>
        </div>

        {/* Hero Graphic Asset Container */}
        <div className="relative z-10 w-full sm:w-72 md:w-80 lg:w-96 shrink-0 hidden md:block">
          <div className="p-2.5 bg-cricNavy-800/90 border-2 border-cricGreen-500/40 rounded-3xl shadow-2xl backdrop-blur-md overflow-hidden group">
            <img
              src="/img/landing-hero-main.png"
              alt="CricLeague Hero Graphic"
              className="w-full h-auto max-h-80 object-cover rounded-2xl shadow-lg group-hover:scale-102 transition-transform duration-300"
              onError={e => (e.target as HTMLElement).style.display = 'none'}
            />
          </div>
        </div>
      </div>

      {/* # MATCH HUB (Unified Action Center) */}
      <div className="bg-cricNavy-700 border border-cricBorder rounded-3xl p-6 sm:p-8 shadow-xl space-y-6">
        <div className="text-center space-y-1">
          <div className="inline-flex items-center space-x-1.5 bg-cricGreen-500/20 text-cricElectric-500 px-3 py-1 rounded-full text-xs font-extrabold border border-cricGreen-500/30">
            <span>🏏 CRICLEAGUE MATCH HUB</span>
          </div>
          <h2 className="text-xl sm:text-2xl font-black text-white uppercase tracking-wider pt-1">
            Start A Game or Manage Your League
          </h2>
          <p className="text-xs sm:text-sm text-slate-300 max-w-lg mx-auto">
            Launch an instant quick game, manage teams & squads, view series standings, or configure Gully rules.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 pt-2">
          {/* Quick Match Card */}
          <div
            onClick={onStartQuickMatch}
            className="p-5 bg-cricNavy-800 hover:bg-cricNavy-600 border border-cricBorder rounded-2xl cursor-pointer transition-all shadow-md group space-y-3"
          >
            <div className="w-10 h-10 rounded-xl bg-cricGreen-500/20 text-cricElectric-500 flex items-center justify-center font-black group-hover:scale-110 transition-transform">
              <Play className="w-5 h-5 fill-cricElectric-500" />
            </div>
            <div>
              <h3 className="font-extrabold text-sm text-white group-hover:text-cricElectric-500 transition-colors">Quick Match</h3>
              <p className="text-xs text-slate-400 mt-0.5">Free local scoring • Teams → Toss → Live</p>
            </div>
          </div>

          {/* Teams & Squads Card */}
          <div
            onClick={() => onNavigateTab('teams')}
            className="p-5 bg-cricNavy-800 hover:bg-cricNavy-600 border border-cricBorder rounded-2xl cursor-pointer transition-all shadow-md group space-y-3"
          >
            <div className="w-10 h-10 rounded-xl bg-sky-500/20 text-sky-400 flex items-center justify-center font-black group-hover:scale-110 transition-transform">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center justify-between">
                <h3 className="font-extrabold text-sm text-white group-hover:text-sky-400 transition-colors">Teams & Squads</h3>
                <span className="text-[9px] px-1.5 py-0.2 bg-amber-500/20 text-amber-300 rounded border border-amber-500/30 font-bold">Sign-in</span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">Manage player profiles & batting styles</p>
            </div>
          </div>

          {/* Tournaments / Series Card */}
          <div
            onClick={() => onNavigateTab('stats')}
            className="p-5 bg-cricNavy-800 hover:bg-cricNavy-600 border border-cricBorder rounded-2xl cursor-pointer transition-all shadow-md group space-y-3"
          >
            <div className="w-10 h-10 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center font-black group-hover:scale-110 transition-transform">
              <Trophy className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center justify-between">
                <h3 className="font-extrabold text-sm text-white group-hover:text-amber-400 transition-colors">Tournaments & Series</h3>
                <span className="text-[9px] px-1.5 py-0.2 bg-amber-500/20 text-amber-300 rounded border border-amber-500/30 font-bold">Sign-in</span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">Points table, NRR standings & stats</p>
            </div>
          </div>

          {/* Gully Rules Card */}
          <div
            onClick={() => onNavigateTab('more')}
            className="p-5 bg-cricNavy-800 hover:bg-cricNavy-600 border border-cricBorder rounded-2xl cursor-pointer transition-all shadow-md group space-y-3"
          >
            <div className="w-10 h-10 rounded-xl bg-purple-500/20 text-purple-400 flex items-center justify-center font-black group-hover:scale-110 transition-transform">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-sm text-white group-hover:text-purple-400 transition-colors">Gully & Turf Rules</h3>
              <p className="text-xs text-slate-400 mt-0.5">LMS, Joker, custom overs & powerplays</p>
            </div>
          </div>
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
