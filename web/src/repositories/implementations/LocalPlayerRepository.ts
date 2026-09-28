import { PlayerRepository } from '../interfaces/PlayerRepository';
import { Player } from '../../domain/models';
import { StorageAdapter } from '../../storage/storageAdapter';

export class LocalPlayerRepository implements PlayerRepository {
  public async getGlobalPlayers(): Promise<Player[]> {
    return StorageAdapter.getGlobalPlayers();
  }

  public async saveGlobalPlayer(player: Player): Promise<Player> {
    const players = StorageAdapter.getGlobalPlayers();
    const existingIndex = players.findIndex(p => p.id === player.id || p.name.trim().toLowerCase() === player.name.trim().toLowerCase());

    let updated: Player[];
    let savedPlayer: Player;

    if (existingIndex >= 0) {
      savedPlayer = { ...players[existingIndex], ...player };
      updated = [...players];
      updated[existingIndex] = savedPlayer;
    } else {
      savedPlayer = player;
      updated = [...players, savedPlayer];
    }

    StorageAdapter.saveGlobalPlayers(updated);
    return savedPlayer;
  }

  public async deleteGlobalPlayer(id: string): Promise<void> {
    const players = StorageAdapter.getGlobalPlayers();
    const updated = players.filter(p => p.id !== id);
    StorageAdapter.saveGlobalPlayers(updated);
  }

  public async addPlayersToTeam(tournamentId: string, teamId: string, newPlayers: Player[]): Promise<void> {
    const tournaments = StorageAdapter.getTournaments();
    const updated = tournaments.map(t => {
      if (t.id !== tournamentId) return t;
      const teams = t.teams.map(tm => {
        if (tm.id !== teamId) return tm;
        const currentPlayers = [...tm.players];
        newPlayers.forEach(np => {
          if (!currentPlayers.some(p => p.id === np.id)) {
            currentPlayers.push(np);
          }
        });
        return { ...tm, players: currentPlayers };
      });
      return { ...t, teams };
    });
    StorageAdapter.saveTournaments(updated);
  }
}
