import React from 'react';
import { Skeleton } from './Skeleton';

export const LoadingState: React.FC = () => {
  return (
    <div className="max-w-4xl mx-auto space-y-6 p-4">
      <Skeleton className="h-32 w-full rounded-3xl" />
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Skeleton className="h-24 w-full rounded-2xl" />
        <Skeleton className="h-24 w-full rounded-2xl" />
        <Skeleton className="h-24 w-full rounded-2xl" />
        <Skeleton className="h-24 w-full rounded-2xl" />
      </div>
      <Skeleton className="h-48 w-full rounded-3xl" />
    </div>
  );
};
