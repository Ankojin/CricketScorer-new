import React from 'react';
import { Home, Calendar, Users, BarChart3, MoreHorizontal, Radio, FileText, ListOrdered } from 'lucide-react';
import { useMatch } from '../../state/MatchContext';

export type MainTab = 'home' | 'matches' | 'teams' | 'stats' | 'more';
export type MatchTab = 'live' | 'scorecard' | 'overs' | 'more';

interface NavbarProps {
  activeMainTab: MainTab;
  setActiveMainTab: (tab: MainTab) => void;
  activeMatchTab: MatchTab;
  setActiveMatchTab: (tab: MatchTab) => void;
  isScoringActive: boolean;
  setIsScoringActive: (active: boolean) => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeMainTab,
  setActiveMainTab,
  activeMatchTab,
  setActiveMatchTab,
  isScoringActive,
  setIsScoringActive,
}) => {
  const { match } = useMatch();

  return (
    <header className="sticky top-0 z-40 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Brand logo & title */}
          <div
            className="flex items-center space-x-3 cursor-pointer rounded-xl p-1 focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none"
            onClick={() => { setIsScoringActive(false); setActiveMainTab('home'); }}
            tabIndex={0}
            role="button"
            aria-label="CricScore Pro Home"
            onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { setIsScoringActive(false); setActiveMainTab('home'); } }}
          >
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-500 flex items-center justify-center text-white font-black text-xl shadow-md">
              🏏
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="font-extrabold text-lg text-slate-900 dark:text-white tracking-tight">CricScore Pro</span>
                <span className="px-1.5 py-0.5 text-xs font-bold bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 rounded">v2</span>
              </div>
              <span className="text-xs text-slate-500 dark:text-slate-400 hidden sm:block font-medium">Smart Cricket Scoring & Analytics</span>
            </div>
          </div>

          {/* Quick toggle if match is in progress */}
          {match && (
            <div className="flex items-center space-x-2">
              <button
                onClick={() => setIsScoringActive(!isScoringActive)}
                className={`flex items-center space-x-2 px-3.5 py-2 rounded-full text-xs font-bold transition-all shadow-xs focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  isScoringActive
                    ? 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700'
                    : 'bg-emerald-600 text-white hover:bg-emerald-700 shadow-emerald-200'
                }`}
                aria-label={isScoringActive ? 'Exit active match scoring' : 'Enter active match scoring'}
              >
                <Radio className={`w-3.5 h-3.5 ${isScoringActive ? 'text-emerald-600 animate-pulse' : ''}`} />
                <span>{isScoringActive ? 'Exit Scoring' : 'Active Match'}</span>
              </button>
            </div>
          )}
        </div>

        {/* Navigation Tabs Bar */}
        <nav className="flex space-x-1 sm:space-x-4 overflow-x-auto no-scrollbar py-1" aria-label="Main Navigation">
          {isScoringActive && match ? (
            /* Active Match Navigation: Live · Scorecard · Overs · More */
            <>
              <button
                onClick={() => setActiveMatchTab('live')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMatchTab === 'live'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Radio className="w-4 h-4" />
                <span>Live</span>
              </button>
              <button
                onClick={() => setActiveMatchTab('scorecard')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMatchTab === 'scorecard'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <FileText className="w-4 h-4" />
                <span>Scorecard</span>
              </button>
              <button
                onClick={() => setActiveMatchTab('overs')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMatchTab === 'overs'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <ListOrdered className="w-4 h-4" />
                <span>Overs</span>
              </button>
              <button
                onClick={() => setActiveMatchTab('more')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMatchTab === 'more'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <MoreHorizontal className="w-4 h-4" />
                <span>More</span>
              </button>
            </>
          ) : (
            /* Primary Navigation: Home · Matches · Teams · Stats · More */
            <>
              <button
                onClick={() => setActiveMainTab('home')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMainTab === 'home'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Home className="w-4 h-4" />
                <span>Home</span>
              </button>
              <button
                onClick={() => setActiveMainTab('matches')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMainTab === 'matches'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Calendar className="w-4 h-4" />
                <span>Matches</span>
              </button>
              <button
                onClick={() => setActiveMainTab('teams')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMainTab === 'teams'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Users className="w-4 h-4" />
                <span>Teams</span>
              </button>
              <button
                onClick={() => setActiveMainTab('stats')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMainTab === 'stats'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <BarChart3 className="w-4 h-4" />
                <span>Stats</span>
              </button>
              <button
                onClick={() => setActiveMainTab('more')}
                className={`flex items-center space-x-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors whitespace-nowrap focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none min-h-[44px] ${
                  activeMainTab === 'more'
                    ? 'border-emerald-600 text-emerald-600 dark:text-emerald-400'
                    : 'border-transparent text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <MoreHorizontal className="w-4 h-4" />
                <span>More</span>
              </button>
            </>
          )}
        </nav>
      </div>
    </header>
  );
};
