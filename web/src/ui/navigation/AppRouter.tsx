import React, { useState } from 'react';
import { AppLayout } from '../layout/AppLayout';
import { Navbar, MainTab, MatchTab } from './Navbar';
import { HomeScreen } from '../views/HomeScreen';
import { QuickMatchSetup } from '../views/QuickMatchSetup';
import { LiveScoringView } from '../views/LiveScoringView';
import { ScorecardView } from '../views/ScorecardView';
import { OversView } from '../views/OversView';
import { MatchesScreen } from '../views/MatchesScreen';
import { TeamsScreen } from '../views/TeamsScreen';
import { StatsScreen } from '../views/StatsScreen';
import { MoreScreen } from '../views/MoreScreen';
import { StorageAdapter } from '../../storage/storageAdapter';

export const AppRouter: React.FC = () => {
  const [activeMainTab, setActiveMainTab] = useState<MainTab>('home');
  const [activeMatchTab, setActiveMatchTab] = useState<MatchTab>('live');
  const [isScoringActive, setIsScoringActive] = useState<boolean>(false);
  const [isQuickMatchSetupOpen, setIsQuickMatchSetupOpen] = useState<boolean>(false);

  const [isDark, setIsDark] = useState<boolean>(() => {
    const saved = StorageAdapter.getDarkMode();
    if (saved !== null) return saved;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  });

  const handleToggleTheme = (dark: boolean) => {
    setIsDark(dark);
    StorageAdapter.saveDarkMode(dark);
    if (dark) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  const navbar = (
    <Navbar
      activeMainTab={activeMainTab}
      setActiveMainTab={(tab) => {
        setIsScoringActive(false);
        setActiveMainTab(tab);
      }}
      activeMatchTab={activeMatchTab}
      setActiveMatchTab={setActiveMatchTab}
      isScoringActive={isScoringActive}
      setIsScoringActive={setIsScoringActive}
    />
  );

  return (
    <AppLayout navbar={navbar}>
      {isQuickMatchSetupOpen ? (
        <QuickMatchSetup
          onCancel={() => setIsQuickMatchSetupOpen(false)}
          onComplete={() => {
            setIsQuickMatchSetupOpen(false);
            setIsScoringActive(true);
            setActiveMatchTab('live');
          }}
        />
      ) : isScoringActive ? (
        <>
          {activeMatchTab === 'live' && <LiveScoringView />}
          {activeMatchTab === 'scorecard' && <ScorecardView />}
          {activeMatchTab === 'overs' && <OversView />}
          {activeMatchTab === 'more' && (
            <MoreScreen isDark={isDark} onToggleTheme={handleToggleTheme} />
          )}
        </>
      ) : (
        <>
          {activeMainTab === 'home' && (
            <HomeScreen
              onStartQuickMatch={() => setIsQuickMatchSetupOpen(true)}
              onOpenLiveMatch={() => {
                setIsScoringActive(true);
                setActiveMatchTab('live');
              }}
              onNavigateTab={(tab) => setActiveMainTab(tab)}
            />
          )}
          {activeMainTab === 'matches' && (
            <MatchesScreen
              onStartQuickMatch={() => setIsQuickMatchSetupOpen(true)}
              onOpenLiveMatch={() => {
                setIsScoringActive(true);
                setActiveMatchTab('live');
              }}
            />
          )}
          {activeMainTab === 'teams' && <TeamsScreen />}
          {activeMainTab === 'stats' && <StatsScreen />}
          {activeMainTab === 'more' && (
            <MoreScreen isDark={isDark} onToggleTheme={handleToggleTheme} />
          )}
        </>
      )}
    </AppLayout>
  );
};
