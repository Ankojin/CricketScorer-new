import React from 'react';
import { AuthProvider } from './state/AuthContext';
import { TournamentProvider } from './state/TournamentContext';
import { MatchProvider } from './state/MatchContext';
import { AppRouter } from './ui/navigation/AppRouter';
import { ErrorBoundary } from './ui/components/ErrorBoundary';

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <TournamentProvider>
          <MatchProvider>
            <AppRouter />
          </MatchProvider>
        </TournamentProvider>
      </AuthProvider>
    </ErrorBoundary>
  );
};

export default App;
