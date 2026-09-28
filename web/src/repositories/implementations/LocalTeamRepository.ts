import { TeamRepository } from '../interfaces/TeamRepository';
import { Team } from '../../domain/models';
import { StorageAdapter } from '../../storage/storageAdapter';

export class LocalTeamRepository implements TeamRepository {
  public async getTeamById(id: string): Promise<Team | null> {
    const tournaments = StorageAdapter.getTournaments();
    for (const t of tournaments) {
      const team = t.teams.find(tm => tm.id === id);
      if (team) return team;
    }
    return null;
  }

  public async saveTeam(tournamentId: string, team: Team): Promise<void> {
    const tournaments = StorageAdapter.getTournaments();
    const updated = tournaments.map(t => {
      if (t.id !== tournamentId) return t;
      const exists = t.teams.some(tm => tm.id === team.id);
      const teams = exists
        ? t.teams.map(tm => tm.id === team.id ? team : tm)
        : [...t.teams, team];
      return { ...t, teams };
    });
    StorageAdapter.saveTournaments(updated);
  }

  public async updateTeamDetails(
    tournamentId: string,
    teamId: string,
    name: string,
    colorHex?: string | null
  ): Promise<boolean> {
    const tournaments = StorageAdapter.getTournaments();
    const tournament = tournaments.find(t => t.id === tournamentId);
    if (!tournament) return false;

    if (colorHex) {
      const isColorUsedByOther = tournament.teams.some(
        tm => tm.id !== teamId && tm.colorHex === colorHex
      );
      if (isColorUsedByOther) return false;
    }

    const updated = tournaments.map(t => {
      if (t.id !== tournamentId) return t;
      const teams = t.teams.map(tm => {
        if (tm.id !== teamId) return tm;
        return { ...tm, name, colorHex: colorHex ?? tm.colorHex };
      });
      return { ...t, teams };
    });

    StorageAdapter.saveTournaments(updated);
    return true;
  }
}
