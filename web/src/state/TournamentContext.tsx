import React, { createContext, useContext, useState, useEffect } from 'react';
import { Tournament, Match, Team, Player, TournamentSettings } from '../domain/models';
import { StorageAdapter } from '../storage/storageAdapter';

interface TournamentContextType {
  tournaments: Tournament[];
  globalPlayers: Player[];
  activeTournament: Tournament | null;
  createTournament: (name: string, settings?: Partial<TournamentSettings>) => Tournament;
  createQuickMatchTournament: (teamA: Team, teamB: Team, overs: number) => { tournament: Tournament; match: Match };
  updateTournament: (tournament: Tournament) => void;
  deleteTournament: (id: string) => void;
  updateMatchInTournament: (tournamentId: string, updatedMatch: Match) => void;
  deleteMatchFromTournament: (tournamentId: string, matchId: string) => void;
  addPlayerToGlobalList: (name: string, battingStyle?: any) => Player;
  addPlayersToTeam: (tournamentId: string, teamId: string, players: Player[]) => void;
}

const TournamentContext = createContext<TournamentContextType | undefined>(undefined);

export const TournamentProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [tournaments, setTournaments] = useState<Tournament[]>(() => StorageAdapter.getTournaments());
  const [globalPlayers, setGlobalPlayers] = useState<Player[]>(() => StorageAdapter.getGlobalPlayers());
  const [activeTournamentId, setActiveTournamentId] = useState<string | null>(null);

  useEffect(() => {
    StorageAdapter.saveTournaments(tournaments);
  }, [tournaments]);

  useEffect(() => {
    StorageAdapter.saveGlobalPlayers(globalPlayers);
  }, [globalPlayers]);

  const activeTournament = tournaments.find(t => t.id === activeTournamentId) || tournaments[0] || null;

  const createTournament = (name: string, settings?: Partial<TournamentSettings>): Tournament => {
    const newT: Tournament = {
      id: 'tour_' + Date.now(),
      name,
      teams: [],
      matches: [],
      settings: {
        overs: 20,
        ballType: 'Leather',
        powerplayOvers: 6,
        ...settings
      },
      participants: []
    };
    const updated = [newT, ...tournaments];
    setTournaments(updated);
    setActiveTournamentId(newT.id);
    return newT;
  };

  const createQuickMatchTournament = (teamA: Team, teamB: Team, overs: number) => {
    const tournamentId = 'tour_quick_' + Date.now();
    const matchId = 'match_quick_' + Date.now();

    const match: Match = {
      id: matchId,
      tournamentId,
      tournamentName: 'Quick Match',
      teamA,
      teamB,
      status: 'UPCOMING' as any,
      currentInnings: 1,
      battingTeamId: teamA.id,
      bowlingTeamId: teamB.id,
      totalRuns: 0,
      totalWickets: 0,
      totalBalls: 0,
      wideCount: 0,
      noBallCount: 0,
      byeCount: 0,
      legByeCount: 0,
      ballHistory: [],
      wicketHistory: [],
      oversPerInnings: overs,
      gullyRules: StorageAdapter.getGullyRules(),
      isSecondInningsStarted: false,
      battingOrder: [],
      dateMillis: Date.now()
    };

    const tournament: Tournament = {
      id: tournamentId,
      name: 'Quick Matches',
      teams: [teamA, teamB],
      matches: [match],
      settings: { overs, ballType: 'Tennis', powerplayOvers: Math.min(6, Math.floor(overs / 3)) },
      participants: [...teamA.players, ...teamB.players]
    };

    const existingQuickTour = tournaments.find(t => t.id === tournamentId || t.name === 'Quick Matches');
    if (existingQuickTour) {
      const updatedTour: Tournament = {
        ...existingQuickTour,
        teams: [...existingQuickTour.teams, teamA, teamB],
        matches: [match, ...existingQuickTour.matches],
        participants: [...existingQuickTour.participants, ...teamA.players, ...teamB.players]
      };
      setTournaments(tournaments.map(t => t.id === existingQuickTour.id ? updatedTour : t));
    } else {
      setTournaments([tournament, ...tournaments]);
    }

    setActiveTournamentId(tournamentId);
    return { tournament, match };
  };

  const updateTournament = (updated: Tournament) => {
    setTournaments(tournaments.map(t => t.id === updated.id ? updated : t));
  };

  const deleteTournament = (id: string) => {
    setTournaments(tournaments.filter(t => t.id !== id));
    if (activeTournamentId === id) {
      setActiveTournamentId(null);
    }
  };

  const updateMatchInTournament = (tournamentId: string, updatedMatch: Match) => {
    setTournaments(prev => prev.map(t => {
      if (t.id !== tournamentId) return t;
      const existingMatch = t.matches.find(m => m.id === updatedMatch.id);
      const newMatches = existingMatch
        ? t.matches.map(m => m.id === updatedMatch.id ? updatedMatch : m)
        : [updatedMatch, ...t.matches];
      return { ...t, matches: newMatches };
    }));
  };

  const deleteMatchFromTournament = (tournamentId: string, matchId: string) => {
    setTournaments(prev => prev.map(t => {
      if (t.id !== tournamentId) return t;
      return { ...t, matches: t.matches.filter(m => m.id !== matchId) };
    }));
  };

  const addPlayerToGlobalList = (name: string, battingStyle?: any): Player => {
    const existing = globalPlayers.find(p => p.name.trim().toLowerCase() === name.trim().toLowerCase());
    if (existing) return existing;

    const newPlayer: Player = {
      id: 'p_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
      name,
      battingStats: { runs: 0, balls: 0, fours: 0, sixes: 0, isOut: false, isRetiredHurt: false, wicketType: 'NONE' as any, dismissalBowlerId: null, dismissalFielderId: null },
      bowlingStats: { overs: 0, balls: 0, maidens: 0, runsConceded: 0, wickets: 0, dotBalls: 0, wides: 0, noBalls: 0 },
      fieldingStats: { catches: 0, runOuts: 0, stumpings: 0, droppedCatches: 0 },
      isJoker: false,
      isCaptain: false,
      isViceCaptain: false,
      battingStyle: battingStyle || 'RHB'
    };

    setGlobalPlayers([...globalPlayers, newPlayer]);
    return newPlayer;
  };

  const addPlayersToTeam = (tournamentId: string, teamId: string, players: Player[]) => {
    setTournaments(prev => prev.map(t => {
      if (t.id !== tournamentId) return t;
      return {
        ...t,
        teams: t.teams.map(team => {
          if (team.id !== teamId) return team;
          const updatedPlayers = [...team.players];
          players.forEach(p => {
            if (!updatedPlayers.some(existing => existing.id === p.id)) {
              updatedPlayers.push(p);
            }
          });
          return { ...team, players: updatedPlayers };
        })
      };
    }));
  };

  return (
    <TournamentContext.Provider value={{
      tournaments,
      globalPlayers,
      activeTournament,
      createTournament,
      createQuickMatchTournament,
      updateTournament,
      deleteTournament,
      updateMatchInTournament,
      deleteMatchFromTournament,
      addPlayerToGlobalList,
      addPlayersToTeam
    }}>
      {children}
    </TournamentContext.Provider>
  );
};

export const useTournament = () => {
  const context = useContext(TournamentContext);
  if (!context) throw new Error('useTournament must be used within TournamentProvider');
  return context;
};
