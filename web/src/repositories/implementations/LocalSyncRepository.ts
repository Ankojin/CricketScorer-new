import { SyncRepository } from '../interfaces/SyncRepository';
import { Match } from '../../domain/models';
import { StorageAdapter } from '../../storage/storageAdapter';

const PENDING_SYNC_KEY = 'cricscore_v2_pending_sync_queue';

export class LocalSyncRepository implements SyncRepository {
  private active = false;

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        this.retryPendingSync();
      });
    }
  }

  public isSyncActive(): boolean {
    return this.active;
  }

  public isOnline(): boolean {
    if (typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean') {
      return navigator.onLine;
    }
    return true; // Assume online in test/server environment unless simulated
  }

  public async startSync(): Promise<void> {
    this.active = true;
    if (this.isOnline()) {
      await this.retryPendingSync();
    }
  }

  public async stopSync(): Promise<void> {
    this.active = false;
  }

  public async broadcastMatchState(match: Match): Promise<void> {
    if (!this.active) return;

    if (!this.isOnline()) {
      this.enqueuePendingSync(match);
      return;
    }

    try {
      if (typeof BroadcastChannel !== 'undefined') {
        const channel = new BroadcastChannel('cricscore_p2p_sync');
        channel.postMessage({ type: 'MATCH_UPDATE', match, timestamp: Date.now() });
        channel.close();
      }
    } catch (e) {
      console.warn('Sync broadcast failed, queuing for background retry:', e);
      this.enqueuePendingSync(match);
    }
  }

  public getPendingQueue(): Match[] {
    try {
      const raw = StorageAdapter.getItem(PENDING_SYNC_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  public async retryPendingSync(): Promise<number> {
    if (!this.isOnline()) return 0;

    const queue = this.getPendingQueue();
    if (queue.length === 0) return 0;

    let syncedCount = 0;
    const remainingQueue: Match[] = [];

    for (const match of queue) {
      try {
        if (typeof BroadcastChannel !== 'undefined') {
          const channel = new BroadcastChannel('cricscore_p2p_sync');
          channel.postMessage({ type: 'MATCH_UPDATE', match, timestamp: Date.now() });
          channel.close();
        }
        syncedCount++;
      } catch (e) {
        remainingQueue.push(match);
      }
    }

    this.savePendingQueue(remainingQueue);
    return syncedCount;
  }

  public resolveConflict(localMatch: Match, remoteMatch: Match): Match {
    // Conflict resolution rule matching Android's ScoringEngine:
    // Prefer the match with longer ballHistory length (most complete match events)
    if (remoteMatch.ballHistory.length > localMatch.ballHistory.length) {
      return remoteMatch;
    }
    if (localMatch.ballHistory.length > remoteMatch.ballHistory.length) {
      return localMatch;
    }
    // Tiebreaker: Most recent timestamp
    return (remoteMatch.dateMillis || 0) > (localMatch.dateMillis || 0) ? remoteMatch : localMatch;
  }

  public enqueuePendingSync(match: Match): void {
    const queue = this.getPendingQueue();
    const existingIndex = queue.findIndex(m => m.id === match.id);
    let updated: Match[];

    if (existingIndex >= 0) {
      updated = [...queue];
      updated[existingIndex] = match;
    } else {
      updated = [...queue, match];
    }

    this.savePendingQueue(updated);
  }

  private savePendingQueue(queue: Match[]): void {
    try {
      StorageAdapter.setItem(PENDING_SYNC_KEY, JSON.stringify(queue));
    } catch (e) {
      console.error('Failed to save pending sync queue:', e);
    }
  }
}
