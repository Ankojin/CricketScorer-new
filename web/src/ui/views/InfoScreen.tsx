import React from 'react';
import { FileText, CheckCircle2, ShieldAlert, ArrowLeft } from 'lucide-react';

interface InfoScreenProps {
  onBack?: () => void;
}

export const InfoScreen: React.FC<InfoScreenProps> = ({ onBack }) => {
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
          <span className="text-[10px] font-extrabold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">CricLeague Rules</span>
          <h1 className="text-lg font-extrabold text-slate-900 dark:text-white">Rules Engine & Info</h1>
        </div>
      </div>

      {/* Hero Banner */}
      <div className="bg-cricNavy-700 border border-cricBorder text-white rounded-3xl p-6 sm:p-8 shadow-xl space-y-3">
        <div className="flex items-center space-x-2 text-cricElectric-500 text-xs font-bold uppercase tracking-wider">
          <FileText className="w-4 h-4" />
          <span>Street & Custom Cricket Rules Engine</span>
        </div>
        <h2 className="text-2xl sm:text-3xl font-black">Supported Cricket Rules & Formats</h2>
        <p className="text-slate-300 text-sm font-medium">
          CricLeague supports standard ICC limited-overs rules as well as custom Gully Cricket rules for casual and turf matches.
        </p>
      </div>

      {/* Gully Rules Guide */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 sm:p-8 shadow-sm space-y-4">
        <h3 className="font-extrabold text-base text-slate-900 dark:text-white flex items-center space-x-2">
          <ShieldAlert className="w-5 h-5 text-emerald-600" />
          <span>Gully & Turf Cricket Custom Rules</span>
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 text-xs font-semibold text-slate-700 dark:text-slate-300">
          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/80 space-y-1">
            <div className="font-bold text-slate-900 dark:text-white text-sm">Last Man Standing (LMS)</div>
            <p className="text-slate-500">When squad reaches 1 remaining batter, LMS allows the last batter to continue batting alone until out.</p>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/80 space-y-1">
            <div className="font-bold text-slate-900 dark:text-white text-sm">Common Player (Joker)</div>
            <p className="text-slate-500">Allows a designated common player to field/bat for both teams in uneven squad setups.</p>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/80 space-y-1">
            <div className="font-bold text-slate-900 dark:text-white text-sm">No Extras for Wides/No-Balls</div>
            <p className="text-slate-500">Disables automatic 1-run penalty for wides/no-balls in restricted turf matches.</p>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/80 space-y-1">
            <div className="font-bold text-slate-900 dark:text-white text-sm">Single Side Batting</div>
            <p className="text-slate-500">Allows one-ended bowling and batting when pitch constraints prevent two-end running.</p>
          </div>
        </div>
      </div>
    </div>
  );
};
