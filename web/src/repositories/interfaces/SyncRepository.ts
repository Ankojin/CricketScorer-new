import { Match } from '../../domain/models';

export interface SyncRepository {
  isSyncActive(): boolean;
  isOnline(): boolean;
  startSync(): Promise<void>;
  stopSync(): Promise<void>;
  broadcastMatchState(match: Match): Promise<void>;
  getPendingQueue(): Match[];
  retryPendingSync(): Promise<number>;
  resolveConflict(localMatch: Match, remoteMatch: Match): Match;
}
