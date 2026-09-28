import { MatchRepository } from '../interfaces/MatchRepository';
import { Match, Tournament } from '../../domain/models';
import { StorageAdapter } from '../../storage/storageAdapter';

export class LocalMatchRepository implements MatchRepository {
  public async getMatchById(id: string): Promise<Match | null> {
    const tournaments = StorageAdapter.getTournaments();
    for (const t of tournaments) {
      const match = t.matches.find(m => m.id === id);
      if (match) return match;
    }
    return null;
  }

  public async saveMatch(match: Match): Promise<void> {
    const tournamentId = match.tournamentId || 'tour_quick_default';
    const tournaments = StorageAdapter.getTournaments();
    const tournamentIndex = tournaments.findIndex(t => t.id === tournamentId);

    if (tournamentIndex >= 0) {
      const t = tournaments[tournamentIndex];
      const exists = t.matches.some(m => m.id === match.id);
      const matches = exists
        ? t.matches.map(m => m.id === match.id ? match : m)
        : [match, ...t.matches];
      tournaments[tournamentIndex] = { ...t, matches };
    } else {
      const newTourney: Tournament = {
        id: tournamentId,
        name: match.tournamentName || 'Quick Matches',
        teams: [match.teamA, match.teamB],
        matches: [match],
        settings: { overs: match.oversPerInnings, ballType: 'Leather', powerplayOvers: 6 },
        participants: [...match.teamA.players, ...match.teamB.players]
      };
      tournaments.unshift(newTourney);
    }

    StorageAdapter.saveTournaments(tournaments);
  }

  public async deleteMatch(tournamentId: string, matchId: string): Promise<void> {
    const tournaments = StorageAdapter.getTournaments();
    const updated = tournaments.map(t => {
      if (t.id !== tournamentId) return t;
      return { ...t, matches: t.matches.filter(m => m.id !== matchId) };
    });
    StorageAdapter.saveTournaments(updated);
    if (StorageAdapter.getActiveMatchId() === matchId) {
      StorageAdapter.setActiveMatchId(null);
    }
  }

  public async getActiveMatch(): Promise<Match | null> {
    const activeId = StorageAdapter.getActiveMatchId();
    if (!activeId) return null;
    return this.getMatchById(activeId);
  }

  public async setActiveMatchId(id: string | null): Promise<void> {
    StorageAdapter.setActiveMatchId(id);
  }
}
