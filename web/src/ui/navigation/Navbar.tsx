import React from 'react';
import {
  Home,
  Calendar,
  Users,
  BarChart3,
  MoreHorizontal,
  Radio,
  FileText,
  ListOrdered,
  Sparkles,
  Cloud,
  Settings,
  ChevronDown,
  Shield,
  Zap,
  Info
} from 'lucide-react';
import { useMatch } from '../../state/MatchContext';
import { useAuth } from '../../state/AuthContext';

export type MainTab = 'home' | 'matches' | 'teams' | 'stats' | 'about' | 'info' | 'privacy' | 'more';
export type MatchTab = 'live' | 'scorecard' | 'overs' | 'more';

interface NavbarProps {
  activeMainTab: MainTab;
  setActiveMainTab: (tab: MainTab) => void;
  activeMatchTab: MatchTab;
  setActiveMatchTab: (tab: MatchTab) => void;
  isScoringActive: boolean;
  setIsScoringActive: (active: boolean) => void;
  onOpenFeaturesDrawer: () => void;
  onOpenAuthModal: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeMainTab,
  setActiveMainTab,
  activeMatchTab,
  setActiveMatchTab,
  isScoringActive,
  setIsScoringActive,
  onOpenFeaturesDrawer,
  onOpenAuthModal,
}) => {
  const { match, isCloudSynced } = useMatch();
  const { user } = useAuth();

  return (
    <header className="sticky top-0 z-40 bg-cricNavy-500 border-b border-cricBorder shadow-md text-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header Top Row */}
        <div className="flex items-center justify-between h-16 gap-3">
          {/* Left: Brand logo & title */}
          <div
            className="flex items-center space-x-3 cursor-pointer rounded-xl p-1 focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none shrink-0"
            onClick={() => { setIsScoringActive(false); setActiveMainTab('home'); }}
            tabIndex={0}
            role="button"
            aria-label="CricLeague Home"
          >
            <div className="w-10 h-10 rounded-xl bg-slate-900 border border-slate-700 flex items-center justify-center shadow-md overflow-hidden shrink-0">
              <img src="/img/app-icon.png" alt="CricLeague Logo" className="w-full h-full object-cover" onError={(e) => { (e.target as HTMLElement).style.display = 'none'; }} />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="font-extrabold text-lg text-white tracking-tight">CricLeague</span>
                <span className="px-1.5 py-0.5 text-xs font-bold bg-cricGreen-500/20 text-cricElectric-500 border border-cricGreen-500/30 rounded">v2</span>
              </div>
              <span className="text-xs text-slate-300 hidden md:block font-medium">Smart Cricket Scoring & Analytics</span>
            </div>
          </div>

          {/* Right Actions: Features ▾ · Cloud Sync · Sync Badge · Active Match Toggle */}
          <div className="flex items-center space-x-2 shrink-0">
            {/* Features Side Drawer Toggle */}
            <button
              onClick={onOpenFeaturesDrawer}
              className="flex items-center space-x-1.5 bg-cricNavy-700 hover:bg-cricNavy-600 border border-cricBorder px-3 py-1.5 rounded-xl text-xs font-bold text-slate-200 transition-all"
            >
              <Sparkles className="w-3.5 h-3.5 text-cricElectric-500" />
              <span>Features</span>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
            </button>

            {/* Cloud Sync / Account Button */}
            <button
              onClick={onOpenAuthModal}
              className="hidden sm:flex items-center space-x-1.5 bg-cricNavy-700 hover:bg-cricNavy-600 border border-cricBorder px-3 py-1.5 rounded-xl text-xs font-bold text-slate-200 transition-all"
            >
              <Cloud className="w-3.5 h-3.5 text-sky-400" />
              <span>{user && !user.isGuest ? user.name : 'Cloud Sync'}</span>
            </button>

            {/* Cloud Sync Status Indicator */}
            <div className="hidden xl:flex items-center space-x-1.5 bg-cricNavy-800 px-2.5 py-1 rounded-xl text-[11px] font-bold border border-cricBorder text-slate-300">
              <span className={`w-2 h-2 rounded-full ${user && !user.isGuest && isCloudSynced ? 'bg-cricElectric-600 animate-pulse' : 'bg-slate-400'}`} />
              <span>{user && !user.isGuest && isCloudSynced ? 'Cloud Synced' : 'Cloud Sync Off'}</span>
            </div>

            {/* Active Match scoring toggle */}
            {match && (
              <button
                onClick={() => setIsScoringActive(!isScoringActive)}
                className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-all shadow-xs ${
                  isScoringActive
                    ? 'bg-cricNavy-700 text-white border border-cricBorder'
                    : 'bg-cricGreen-500 text-cricNavy-900 font-extrabold hover:bg-emerald-400'
                }`}
              >
                <Radio className={`w-3.5 h-3.5 ${isScoringActive ? 'text-cricElectric-500 animate-pulse' : ''}`} />
                <span>{isScoringActive ? 'Exit Scoring' : 'Active Match'}</span>
              </button>
            )}
          </div>
        </div>

        {/* Single Unified Navigation Bar */}
        <nav className="flex space-x-1 sm:space-x-3 overflow-x-auto no-scrollbar py-1" aria-label="Main Navigation">
          {isScoringActive && match ? (
            /* Active Match Navigation: Live · Scorecard · Overs · Settings */
            <>
              <button
                onClick={() => setActiveMatchTab('live')}
                className={`flex items-center space-x-2 px-4 py-2.5 text-xs font-extrabold border-b-2 transition-colors whitespace-nowrap focus-visible:outline-none ${
                  activeMatchTab === 'live'
                    ? 'border-cricElectric-500 text-cricElectric-500'
                    : 'border-transparent text-slate-300 hover:text-white'
                }`}
              >
                <Radio className="w-3.5 h-3.5" />
                <span>Live Scoring</span>
              </button>
              <button
                onClick={() => setActiveMatchTab('scorecard')}
                className={`flex items-center space-x-2 px-4 py-2.5 text-xs font-extrabold border-b-2 transition-colors whitespace-nowrap focus-visible:outline-none ${
                  activeMatchTab === 'scorecard'
                    ? 'border-cricElectric-500 text-cricElectric-500'
                    : 'border-transparent text-slate-300 hover:text-white'
                }`}
              >
                <FileText className="w-3.5 h-3.5" />
                <span>Scorecard</span>
              </button>
              <button
                onClick={() => setActiveMatchTab('overs')}
                className={`flex items-center space-x-2 px-4 py-2.5 text-xs font-extrabold border-b-2 transition-colors whitespace-nowrap focus-visible:outline-none ${
                  activeMatchTab === 'overs'
                    ? 'border-cricElectric-500 text-cricElectric-500'
                    : 'border-transparent text-slate-300 hover:text-white'
                }`}
              >
                <ListOrdered className="w-3.5 h-3.5" />
                <span>Overs</span>
              </button>
              <button
                onClick={() => setActiveMatchTab('more')}
                className={`flex items-center space-x-2 px-4 py-2.5 text-xs font-extrabold border-b-2 transition-colors whitespace-nowrap focus-visible:outline-none ${
                  activeMatchTab === 'more'
                    ? 'border-cricElectric-500 text-cricElectric-500'
                    : 'border-transparent text-slate-300 hover:text-white'
                }`}
              >
                <MoreHorizontal className="w-3.5 h-3.5" />
                <span>Settings</span>
              </button>
            </>
          ) : (
            /* Single Unified Main Navigation Bar */
            <>
              {[
                { id: 'home', label: 'Home', icon: Home },
                { id: 'matches', label: 'Matches', icon: Calendar },
                { id: 'teams', label: 'Teams & Squads', icon: Users, reqAuth: true },
                { id: 'stats', label: 'Series / Standings', icon: BarChart3, reqAuth: true },
                { id: 'about', label: 'About', icon: Info },
                { id: 'info', label: 'Info', icon: FileText },
                { id: 'privacy', label: 'Privacy', icon: Shield },
                { id: 'more', label: 'Settings', icon: Settings },
              ].map(item => {
                const IconComp = item.icon;
                const isActive = activeMainTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => { setIsScoringActive(false); setActiveMainTab(item.id as any); }}
                    className={`flex items-center space-x-1.5 px-3.5 py-2.5 text-xs font-extrabold border-b-2 transition-colors whitespace-nowrap focus-visible:outline-none ${
                      isActive
                        ? 'border-cricElectric-500 text-cricElectric-500'
                        : 'border-transparent text-slate-300 hover:text-white'
                    }`}
                  >
                    <IconComp className="w-3.5 h-3.5" />
                    <span>{item.label}</span>
                    {item.reqAuth && (!user || user.isGuest) && (
                      <span className="text-[9px] px-1 py-0.2 bg-amber-500/20 text-amber-300 rounded border border-amber-500/30">Sign-in</span>
                    )}
                  </button>
                );
              })}
            </>
          )}
        </nav>
      </div>
    </header>
  );
};
