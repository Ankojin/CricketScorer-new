import React, { useState } from 'react';
import { ShieldAlert, Sun, Moon, Settings, Check } from 'lucide-react';
import { StorageAdapter } from '../../storage/storageAdapter';
import { GullyRules } from '../../domain/models';
import { useMatch } from '../../state/MatchContext';

interface MoreScreenProps {
  isDark: boolean;
  onToggleTheme: (dark: boolean) => void;
}

export const MoreScreen: React.FC<MoreScreenProps> = ({ isDark, onToggleTheme }) => {
  const { updateMatchGullyRules } = useMatch();
  const [gullyRules, setGullyRules] = useState<GullyRules>(() => StorageAdapter.getGullyRules());

  const handleToggleRule = (key: keyof GullyRules) => {
    const updated = { ...gullyRules, [key]: !gullyRules[key] };
    setGullyRules(updated);
    StorageAdapter.saveGullyRules(updated);
    updateMatchGullyRules(updated);
  };

  const rulesList: Array<{ key: keyof GullyRules; label: string; desc: string }> = [
    { key: 'lastManStanding', label: 'Last Man Standing (LMS)', desc: 'Allow the final batter to bat alone without a non-striker' },
    { key: 'commonPlayer', label: 'Common Joker Player', desc: 'Joker player who bats/bowls for both teams in uneven squads' },
    { key: 'unequalTeams', label: 'Unequal Team Sizes', desc: 'Allow matches between teams with different squad sizes' },
    { key: 'singleSideBatting', label: 'Single Side Batting', desc: 'Special street cricket rule for single-end pitch & bowling setup' },
    { key: 'noExtraRunsForWidesNoBalls', label: 'No Extra Penalty for Wides/No-Balls', desc: 'Do not add +1 penalty run for wide or no-ball extras' },
    { key: 'playersJoinMidMatch', label: 'Players Join Mid-Match', desc: 'Allow adding new players to squad rosters after match has started' },
    { key: 'playersSwitchMidMatch', label: 'Players Switch Mid-Match', desc: 'Allow players to switch between teams during an active match' },
  ];

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">Settings & Custom Rules</h1>
        <p className="text-xs text-slate-500 mt-0.5">Configure Gully Rules, appearance, and application defaults</p>
      </div>

      {/* Theme Settings */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-4">
        <h2 className="font-extrabold text-base text-slate-900 dark:text-white flex items-center space-x-2">
          <Settings className="w-4 h-4 text-emerald-600" />
          <span>Appearance</span>
        </h2>
        <div className="flex items-center justify-between p-3 rounded-2xl bg-slate-50 dark:bg-slate-800">
          <div className="flex items-center space-x-3">
            {isDark ? <Moon className="w-5 h-5 text-purple-400" /> : <Sun className="w-5 h-5 text-amber-500" />}
            <div>
              <div className="font-bold text-sm text-slate-900 dark:text-white">Dark Theme</div>
              <div className="text-xs text-slate-400">Switch between light and dark modes</div>
            </div>
          </div>
          <button
            onClick={() => onToggleTheme(!isDark)}
            className={`w-12 h-6 flex items-center rounded-full p-1 transition-colors ${
              isDark ? 'bg-emerald-600 justify-end' : 'bg-slate-300 justify-start'
            }`}
          >
            <div className="w-4 h-4 rounded-full bg-white shadow-md" />
          </button>
        </div>
      </div>

      {/* Gully Rules Configuration */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-5 shadow-sm space-y-4">
        <div className="flex items-center space-x-2 border-b border-slate-100 dark:border-slate-800 pb-3">
          <ShieldAlert className="w-5 h-5 text-amber-500" />
          <div>
            <h2 className="font-extrabold text-base text-slate-900 dark:text-white">Gully & Street Cricket Rules</h2>
            <p className="text-xs text-slate-400">Custom rules applied during live match scoring</p>
          </div>
        </div>

        <div className="space-y-3">
          {rulesList.map(rule => (
            <div
              key={rule.key}
              onClick={() => handleToggleRule(rule.key)}
              className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800 flex items-center justify-between cursor-pointer hover:bg-slate-100/80 dark:hover:bg-slate-800/80 transition-colors"
            >
              <div>
                <div className="font-extrabold text-sm text-slate-900 dark:text-white">{rule.label}</div>
                <div className="text-xs text-slate-400 mt-0.5">{rule.desc}</div>
              </div>
              <div
                className={`w-6 h-6 rounded-lg flex items-center justify-center border transition-all shrink-0 ml-3 ${
                  gullyRules[rule.key]
                    ? 'bg-emerald-600 border-emerald-600 text-white'
                    : 'border-slate-300 dark:border-slate-700'
                }`}
              >
                {gullyRules[rule.key] && <Check className="w-4 h-4" />}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
