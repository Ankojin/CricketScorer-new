import { Team } from '../../domain/models';

export interface TeamRepository {
  getTeamById(id: string): Promise<Team | null>;
  saveTeam(tournamentId: string, team: Team): Promise<void>;
  updateTeamDetails(tournamentId: string, teamId: string, name: string, colorHex?: string | null): Promise<boolean>;
}
