import { Player } from '../../domain/models';

export interface PlayerRepository {
  getGlobalPlayers(): Promise<Player[]>;
  saveGlobalPlayer(player: Player): Promise<Player>;
  deleteGlobalPlayer(id: string): Promise<void>;
  addPlayersToTeam(tournamentId: string, teamId: string, players: Player[]): Promise<void>;
}
