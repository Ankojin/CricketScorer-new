import { Tournament } from '../../domain/models';

export interface TournamentRepositoryInterface {
  getAllTournaments(): Promise<Tournament[]>;
  getTournamentById(id: string): Promise<Tournament | null>;
  saveTournament(tournament: Tournament): Promise<void>;
  deleteTournament(id: string): Promise<void>;
  exportTournamentJson(id: string): Promise<string | null>;
  importTournamentJson(json: string): Promise<boolean>;
}
