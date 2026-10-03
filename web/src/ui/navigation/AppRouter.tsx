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
import { PrivacyScreen } from '../views/PrivacyScreen';
import { AboutScreen } from '../views/AboutScreen';
import { InfoScreen } from '../views/InfoScreen';
import { FeaturesDrawer } from '../components/FeaturesDrawer';
import { CoinTossModal } from '../components/CoinTossModal';
import { AuthModal } from '../components/AuthModal';
import { StorageAdapter } from '../../storage/storageAdapter';
import { useAuth } from '../../state/AuthContext';

export const AppRouter: React.FC = () => {
  const { user } = useAuth();

  const [activeMainTab, setActiveMainTab] = useState<MainTab>('home');
  const [activeMatchTab, setActiveMatchTab] = useState<MatchTab>('live');
  const [isScoringActive, setIsScoringActive] = useState<boolean>(false);
  const [isQuickMatchSetupOpen, setIsQuickMatchSetupOpen] = useState<boolean>(false);

  // Modals & Drawers
  const [isFeaturesDrawerOpen, setIsFeaturesDrawerOpen] = useState(false);
  const [isCoinTossOpen, setIsCoinTossOpen] = useState(false);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authPromptMessage, setAuthPromptMessage] = useState<string | null>(null);

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

  const handleNavigateMainTab = (tab: MainTab) => {
    setIsScoringActive(false);
    setIsQuickMatchSetupOpen(false); // Reset Quick Match setup screen when navigating to any page!

    // Sign-In Access Control Guard for Teams and Tournaments/Series
    if ((tab === 'teams' || tab === 'stats') && (!user || user.isGuest)) {
      const promptMsg = tab === 'teams'
        ? 'Sign in required to create, save, and sync permanent Teams & Squads across devices.'
        : 'Sign in required to create, manage, and sync Tournaments & Series across devices.';
      setAuthPromptMessage(promptMsg);
      setIsAuthModalOpen(true);
      return;
    }

    setActiveMainTab(tab);
  };

  const navbar = (
    <Navbar
      activeMainTab={activeMainTab}
      setActiveMainTab={handleNavigateMainTab}
      activeMatchTab={activeMatchTab}
      setActiveMatchTab={setActiveMatchTab}
      isScoringActive={isScoringActive}
      setIsScoringActive={setIsScoringActive}
      onOpenFeaturesDrawer={() => setIsFeaturesDrawerOpen(true)}
      onOpenAuthModal={() => {
        setAuthPromptMessage(null);
        setIsAuthModalOpen(true);
      }}
    />
  );

  return (
    <AppLayout navbar={navbar}>
      {/* Features Side Drawer */}
      <FeaturesDrawer
        isOpen={isFeaturesDrawerOpen}
        onClose={() => setIsFeaturesDrawerOpen(false)}
        onNavigate={(tab) => handleNavigateMainTab(tab as MainTab)}
        onOpenQuickMatch={() => setIsQuickMatchSetupOpen(true)}
        onOpenCoinToss={() => setIsCoinTossOpen(true)}
      />

      {/* Coin Toss Modal */}
      <CoinTossModal
        isOpen={isCoinTossOpen}
        onClose={() => setIsCoinTossOpen(false)}
      />

      {/* Auth / Cloud Sync Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => {
          setIsAuthModalOpen(false);
          setAuthPromptMessage(null);
        }}
        messagePrompt={authPromptMessage}
      />

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
              onNavigateTab={(tab) => handleNavigateMainTab(tab as MainTab)}
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
          {activeMainTab === 'about' && (
            <AboutScreen onBack={() => handleNavigateMainTab('home')} />
          )}
          {activeMainTab === 'info' && (
            <InfoScreen onBack={() => handleNavigateMainTab('home')} />
          )}
          {activeMainTab === 'privacy' && (
            <PrivacyScreen onBack={() => handleNavigateMainTab('home')} />
          )}
        </>
      )}
    </AppLayout>
  );
};
