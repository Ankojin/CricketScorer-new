import React from 'react';
import { ErrorBoundary } from '../components/ErrorBoundary';

interface AppLayoutProps {
  navbar: React.ReactNode;
  children: React.ReactNode;
}

export const AppLayout: React.FC<AppLayoutProps> = ({ navbar, children }) => {
  return (
    <ErrorBoundary>
      <div className="min-h-screen bg-slate-50 dark:bg-cricNavy-900 text-slate-900 dark:text-slate-100 flex flex-col font-sans transition-colors selection:bg-cricGreen-500 selection:text-white">
        {navbar}
        <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
          {children}
        </main>
      </div>
    </ErrorBoundary>
  );
};
