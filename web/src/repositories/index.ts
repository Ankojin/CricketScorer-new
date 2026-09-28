export * from './interfaces/MatchRepository';
export * from './interfaces/TeamRepository';
export * from './interfaces/PlayerRepository';
export * from './interfaces/TournamentRepository';
export * from './interfaces/AuthRepository';
export * from './interfaces/SyncRepository';

export * from './implementations/LocalMatchRepository';
export * from './implementations/LocalTeamRepository';
export * from './implementations/LocalPlayerRepository';
export * from './implementations/LocalTournamentRepository';
export * from './implementations/LocalAuthRepository';
export * from './implementations/LocalSyncRepository';

import { LocalMatchRepository } from './implementations/LocalMatchRepository';
import { LocalTeamRepository } from './implementations/LocalTeamRepository';
import { LocalPlayerRepository } from './implementations/LocalPlayerRepository';
import { LocalTournamentRepository } from './implementations/LocalTournamentRepository';
import { LocalAuthRepository } from './implementations/LocalAuthRepository';
import { LocalSyncRepository } from './implementations/LocalSyncRepository';

export const matchRepository = new LocalMatchRepository();
export const teamRepository = new LocalTeamRepository();
export const playerRepository = new LocalPlayerRepository();
export const tournamentRepository = new LocalTournamentRepository();
export const authRepository = new LocalAuthRepository();
export const syncRepository = new LocalSyncRepository();
