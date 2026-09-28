import { Match } from '../../domain/models';

export interface MatchRepository {
  getMatchById(id: string): Promise<Match | null>;
  saveMatch(match: Match): Promise<void>;
  deleteMatch(tournamentId: string, matchId: string): Promise<void>;
  getActiveMatch(): Promise<Match | null>;
  setActiveMatchId(id: string | null): Promise<void>;
}
