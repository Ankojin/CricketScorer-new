import React from 'react';
import {
  X,
  Home,
  Zap,
  Play,
  Trophy,
  Coins,
  Settings,
  Info,
  Shield,
  FileText,
} from 'lucide-react';

interface FeaturesDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigate: (tab: string) => void;
  onOpenQuickMatch: () => void;
  onOpenCoinToss: () => void;
}

export const FeaturesDrawer: React.FC<FeaturesDrawerProps> = ({
  isOpen,
  onClose,
  onNavigate,
  onOpenQuickMatch,
  onOpenCoinToss,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-xs transition-opacity animate-fadeIn">
      <div className="bg-slate-900 border-l border-slate-800 text-white w-full max-w-sm h-full flex flex-col shadow-2xl overflow-y-auto">
        {/* Drawer Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between sticky top-0 bg-slate-900/95 backdrop-blur-md z-10">
          <div className="flex items-center space-x-2">
            <span className="text-xs font-black uppercase tracking-wider text-emerald-400">CricLeague Features</span>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white bg-slate-800 rounded-xl"
            aria-label="Close Features Drawer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Features Shortcuts List */}
        <div className="p-4 space-y-2.5 flex-1">
          {/* Home */}
          <button
            onClick={() => { onNavigate('home'); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-emerald-500/20 text-emerald-400 group-hover:bg-emerald-500 group-hover:text-white transition-colors shrink-0">
              <Home className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Home</div>
              <div className="text-xs text-slate-400">Back to CricLeague landing page</div>
            </div>
          </button>

          {/* WebScore */}
          <button
            onClick={() => { onOpenQuickMatch(); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-sky-500/20 text-sky-400 group-hover:bg-sky-500 group-hover:text-white transition-colors shrink-0">
              <Zap className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">WebScore</div>
              <div className="text-xs text-slate-400">Score Free in your Browser</div>
            </div>
          </button>

          {/* Quick Match */}
          <button
            onClick={() => { onOpenQuickMatch(); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-emerald-500/20 text-emerald-400 group-hover:bg-emerald-500 group-hover:text-white transition-colors shrink-0">
              <Play className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Quick Match</div>
              <div className="text-xs text-slate-400">Squads, Players, Scorecards & Stats</div>
            </div>
          </button>

          {/* Series / Tournaments */}
          <button
            onClick={() => { onNavigate('stats'); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-amber-500/20 text-amber-400 group-hover:bg-amber-500 group-hover:text-white transition-colors shrink-0">
              <Trophy className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Series / Tournaments</div>
              <div className="text-xs text-slate-400">Tournaments, Fixtures & Standings</div>
            </div>
          </button>

          {/* Coin Toss */}
          <button
            onClick={() => { onOpenCoinToss(); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-yellow-500/20 text-yellow-400 group-hover:bg-yellow-500 group-hover:text-white transition-colors shrink-0">
              <Coins className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Coin Toss</div>
              <div className="text-xs text-slate-400">Flip a virtual 3D coin anytime</div>
            </div>
          </button>

          {/* Settings & Gully Rules */}
          <button
            onClick={() => { onNavigate('more'); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-purple-500/20 text-purple-400 group-hover:bg-purple-500 group-hover:text-white transition-colors shrink-0">
              <Settings className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Settings & Gully Rules</div>
              <div className="text-xs text-slate-400">Custom overs, powerplays & rules</div>
            </div>
          </button>

          {/* About */}
          <button
            onClick={() => { onNavigate('about'); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-sky-500/20 text-sky-400 group-hover:bg-sky-500 group-hover:text-white transition-colors shrink-0">
              <Info className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">About</div>
              <div className="text-xs text-slate-400">What CricLeague is and who it is for</div>
            </div>
          </button>

          {/* Info */}
          <button
            onClick={() => { onNavigate('info'); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-indigo-500/20 text-indigo-400 group-hover:bg-indigo-500 group-hover:text-white transition-colors shrink-0">
              <FileText className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Info & Documentation</div>
              <div className="text-xs text-slate-400">Rules engine & app guide</div>
            </div>
          </button>

          {/* Privacy */}
          <button
            onClick={() => { onNavigate('privacy'); onClose(); }}
            className="w-full text-left p-3.5 rounded-2xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 flex items-center space-x-3 transition-colors group"
          >
            <div className="p-2.5 rounded-xl bg-teal-500/20 text-teal-400 group-hover:bg-teal-500 group-hover:text-white transition-colors shrink-0">
              <Shield className="w-4 h-4" />
            </div>
            <div>
              <div className="font-bold text-sm text-white">Web Privacy Policy</div>
              <div className="text-xs text-slate-400">Privacy-first statement</div>
            </div>
          </button>
        </div>
      </div>
    </div>
  );
};
