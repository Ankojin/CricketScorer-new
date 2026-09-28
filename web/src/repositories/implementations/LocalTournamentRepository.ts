import { TournamentRepositoryInterface } from '../interfaces/TournamentRepository';
import { Tournament } from '../../domain/models';
import { StorageAdapter } from '../../storage/storageAdapter';

export class LocalTournamentRepository implements TournamentRepositoryInterface {
  public async getAllTournaments(): Promise<Tournament[]> {
    return StorageAdapter.getTournaments();
  }

  public async getTournamentById(id: string): Promise<Tournament | null> {
    const tournaments = StorageAdapter.getTournaments();
    return tournaments.find(t => t.id === id) || null;
  }

  public async saveTournament(tournament: Tournament): Promise<void> {
    const tournaments = StorageAdapter.getTournaments();
    const exists = tournaments.some(t => t.id === tournament.id);
    const updated = exists
      ? tournaments.map(t => t.id === tournament.id ? tournament : t)
      : [tournament, ...tournaments];
    StorageAdapter.saveTournaments(updated);
  }

  public async deleteTournament(id: string): Promise<void> {
    const tournaments = StorageAdapter.getTournaments();
    const updated = tournaments.filter(t => t.id !== id);
    StorageAdapter.saveTournaments(updated);
  }

  public async exportTournamentJson(id: string): Promise<string | null> {
    const tournament = await this.getTournamentById(id);
    if (!tournament) return null;
    return JSON.stringify(tournament, null, 2);
  }

  public async importTournamentJson(json: string): Promise<boolean> {
    try {
      const parsed: Tournament = JSON.parse(json);
      if (!parsed.id || !parsed.name || !Array.isArray(parsed.teams)) {
        return false;
      }
      await this.saveTournament(parsed);
      return true;
    } catch (e) {
      console.error('Failed to import tournament JSON', e);
      return false;
    }
  }
}
