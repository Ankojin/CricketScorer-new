import React from 'react';
import { Info, CheckCircle2, Shield, Zap, Sparkles, ArrowLeft } from 'lucide-react';

interface AboutScreenProps {
  onBack?: () => void;
}

export const AboutScreen: React.FC<AboutScreenProps> = ({ onBack }) => {
  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-16">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-4">
        {onBack && (
          <button
            onClick={onBack}
            className="flex items-center space-x-1.5 text-xs font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back</span>
          </button>
        )}
        <div className="text-right">
          <span className="text-[10px] font-extrabold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">About CricLeague</span>
          <h1 className="text-lg font-extrabold text-slate-900 dark:text-white">Smart Cricket Platform</h1>
        </div>
      </div>

      {/* Hero Banner */}
      <div className="bg-cricNavy-700 border border-cricBorder text-white rounded-3xl p-6 sm:p-8 shadow-xl space-y-3">
        <div className="flex items-center space-x-2 text-cricElectric-500 text-xs font-bold uppercase tracking-wider">
          <Sparkles className="w-4 h-4" />
          <span>Professional Local & Cloud Scoring</span>
        </div>
        <h2 className="text-2xl sm:text-3xl font-black">What CricLeague is and who it is for</h2>
        <p className="text-slate-300 text-sm font-medium">
          CricLeague is a high-performance, deterministic cricket scoring platform designed for corporate leagues, street cricket (Gully rules), and tournament organizers.
        </p>
      </div>

      {/* Features Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-2">
          <div className="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-950 text-emerald-600 flex items-center justify-center font-bold">
            <Zap className="w-5 h-5" />
          </div>
          <h3 className="font-extrabold text-base text-slate-900 dark:text-white">Instant Web Scorer</h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Score matches free directly in your web browser with zero account requirements. Offline-ready PWA functionality ensures you never lose a ball.
          </p>
        </div>

        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-2">
          <div className="w-10 h-10 rounded-xl bg-sky-50 dark:bg-sky-950 text-sky-600 flex items-center justify-center font-bold">
            <Shield className="w-5 h-5" />
          </div>
          <h3 className="font-extrabold text-base text-slate-900 dark:text-white">Privacy & Isolation</h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Guest sessions operate strictly in isolated local storage. Signing in unlocks multi-device cloud synchronization and tournament management.
          </p>
        </div>
      </div>
    </div>
  );
};
