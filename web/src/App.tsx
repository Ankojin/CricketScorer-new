import React from 'react';
import { TournamentProvider } from './state/TournamentContext';
import { MatchProvider } from './state/MatchContext';
import { AppRouter } from './ui/navigation/AppRouter';
import { ErrorBoundary } from './ui/components/ErrorBoundary';

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <TournamentProvider>
        <MatchProvider>
          <AppRouter />
        </MatchProvider>
      </TournamentProvider>
    </ErrorBoundary>
  );
};

export default App;
