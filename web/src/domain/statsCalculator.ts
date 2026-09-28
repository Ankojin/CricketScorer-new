import { Match, Team, Player, Partnership, MatchStatus } from './models';

export interface StandingsRow {
  teamId: string;
  teamName: string;
  matchesPlayed: number;
  wins: number;
  losses: number;
  ties: number;
  points: number;
  nrr: number;
  runsScored: number;
  oversFaced: number;
  runsConceded: number;
  oversBowled: number;
}

export class StatsCalculator {
  public static calculateStandings(matches: Match[], teams: Team[]): StandingsRow[] {
    const tableMap = new Map<string, StandingsRow>();

    teams.forEach(t => {
      tableMap.set(t.id, {
        teamId: t.id,
        teamName: t.name,
        matchesPlayed: 0,
        wins: 0,
        losses: 0,
        ties: 0,
        points: 0,
        nrr: 0,
        runsScored: 0,
        oversFaced: 0,
        runsConceded: 0,
        oversBowled: 0,
      });
    });

    matches.filter(m => m.status === MatchStatus.COMPLETED).forEach(m => {
      const isTeamAWinner = m.winnerId === m.teamA.id;
      const isTeamBWinner = m.winnerId === m.teamB.id;
      const isTie = !m.winnerId;

      const rowA = tableMap.get(m.teamA.id);
      const rowB = tableMap.get(m.teamB.id);

      if (rowA) {
        rowA.matchesPlayed++;
        if (isTeamAWinner) {
          rowA.wins++;
          rowA.points += 2;
        } else if (isTie) {
          rowA.ties++;
          rowA.points += 1;
        } else {
          rowA.losses++;
        }

        const aRunsScored = m.initialBattingTeamId === m.teamA.id
          ? (m.innings1Data?.runs || 0)
          : m.totalRuns;
        const aBallsFaced = m.initialBattingTeamId === m.teamA.id
          ? (m.innings1Data?.balls || 0)
          : m.totalBalls;

        const aRunsConceded = m.initialBowlingTeamId === m.teamA.id
          ? (m.innings1Data?.runs || 0)
          : m.totalRuns;
        const aBallsBowled = m.initialBowlingTeamId === m.teamA.id
          ? (m.innings1Data?.balls || 0)
          : m.totalBalls;

        rowA.runsScored += aRunsScored;
        rowA.oversFaced += aBallsFaced / 6.0;
        rowA.runsConceded += aRunsConceded;
        rowA.oversBowled += aBallsBowled / 6.0;
      }

      if (rowB) {
        rowB.matchesPlayed++;
        if (isTeamBWinner) {
          rowB.wins++;
          rowB.points += 2;
        } else if (isTie) {
          rowB.ties++;
          rowB.points += 1;
        } else {
          rowB.losses++;
        }

        const bRunsScored = m.initialBattingTeamId === m.teamB.id
          ? (m.innings1Data?.runs || 0)
          : m.totalRuns;
        const bBallsFaced = m.initialBattingTeamId === m.teamB.id
          ? (m.innings1Data?.balls || 0)
          : m.totalBalls;

        const bRunsConceded = m.initialBowlingTeamId === m.teamB.id
          ? (m.innings1Data?.runs || 0)
          : m.totalRuns;
        const bBallsBowled = m.initialBowlingTeamId === m.teamB.id
          ? (m.innings1Data?.balls || 0)
          : m.totalBalls;

        rowB.runsScored += bRunsScored;
        rowB.oversFaced += bBallsFaced / 6.0;
        rowB.runsConceded += bRunsConceded;
        rowB.oversBowled += bBallsBowled / 6.0;
      }
    });

    const rows = Array.from(tableMap.values()).map(r => {
      const forRate = r.oversFaced > 0 ? r.runsScored / r.oversFaced : 0;
      const againstRate = r.oversBowled > 0 ? r.runsConceded / r.oversBowled : 0;
      return {
        ...r,
        nrr: forRate - againstRate
      };
    });

    return rows.sort((a, b) => {
      if (b.points !== a.points) return b.points - a.points;
      if (b.nrr !== a.nrr) return b.nrr - a.nrr;
      return b.wins - a.wins;
    });
  }

  public static calculateActivePartnership(match: Match): Partnership | null {
    if (!match.strikerId || !match.nonStrikerId) return null;

    const batTeam = match.battingTeamId === match.teamA.id ? match.teamA : match.teamB;
    const b1 = batTeam.players.find(p => p.id === match.strikerId);
    const b2 = batTeam.players.find(p => p.id === match.nonStrikerId);

    if (!b1 || !b2) return null;

    return {
      batter1Id: b1.id,
      batter1Name: b1.name,
      batter1Runs: b1.battingStats.runs,
      batter1Balls: b1.battingStats.balls,
      batter2Id: b2.id,
      batter2Name: b2.name,
      batter2Runs: b2.battingStats.runs,
      batter2Balls: b2.battingStats.balls,
      totalRuns: b1.battingStats.runs + b2.battingStats.runs,
      totalBalls: b1.battingStats.balls + b2.battingStats.balls
    };
  }
}
