import {
  Match,
  Team,
  Ball,
  WicketType,
  ExtrasType,
  MatchStatus,
  PendingAction,
  WicketRecord,
  InningsSummary,
  Player,
  isPhysicalBall
} from './models';

export class ScoringEngine {
  private static overSnapshots: Map<string, Match[]> = new Map();

  public static clearCache(matchId?: string | null): void {
    if (matchId) {
      this.overSnapshots.delete(matchId);
    } else {
      this.overSnapshots.clear();
    }
  }

  public static healLegacyId(id: string | null | undefined, team: Team): string | null | undefined {
    if (!id) return id;
    const isLikelyUuid = id.length >= 32 && !id.includes(' ');
    if (isLikelyUuid) return id;
    const found = team.players.find(p => p.name.trim().toLowerCase() === id.trim().toLowerCase());
    return found ? found.id : id;
  }

  public static isTeamA(idOrName: string | null | undefined, m: Match): boolean {
    if (!idOrName) return false;
    return idOrName === m.teamA.id || idOrName.trim().toLowerCase() === m.teamA.name.trim().toLowerCase();
  }

  public static recalculateMatchFromHistory(match: Match): Match {
    if (!match.tossWinnerId) {
      return { ...match, pendingAction: PendingAction.TOSS_REQUIRED };
    }

    let snapshots = this.overSnapshots.get(match.id);
    if (!snapshots) {
      snapshots = [];
      this.overSnapshots.set(match.id, snapshots);
    }

    var startState: Match | null = null;
    var startIndex = 0;

    for (let i = snapshots.length - 1; i >= 0; i--) {
      const snapshot = snapshots[i];
      const snapBalls = snapshot.ballHistory.length;
      if (snapBalls <= match.ballHistory.length) {
        let isMatch = true;
        for (let j = 0; j < snapBalls; j++) {
          if (JSON.stringify(match.ballHistory[j]) !== JSON.stringify(snapshot.ballHistory[j])) {
            isMatch = false;
            break;
          }
        }
        if (isMatch) {
          startState = snapshot;
          startIndex = snapBalls;
          snapshots.splice(i + 1);
          break;
        }
      }
    }

    let current: Match;
    if (startState) {
      current = { ...startState };
    } else {
      const teamABatsFirst = match.tossWinnerId === match.teamA.id ? match.tossDecision === 'BAT' : match.tossDecision === 'BOWL';
      const innings1BattingTeamId = teamABatsFirst ? match.teamA.id : match.teamB.id;
      const innings1BowlingTeamId = innings1BattingTeamId === match.teamA.id ? match.teamB.id : match.teamA.id;

      current = {
        ...match,
        totalRuns: 0,
        totalWickets: 0,
        totalBalls: 0,
        wideCount: 0,
        noBallCount: 0,
        byeCount: 0,
        legByeCount: 0,
        wicketHistory: [],
        battingOrder: [],
        teamA: this.resetTeamStats(match.teamA),
        teamB: this.resetTeamStats(match.teamB),
        status: MatchStatus.LIVE,
        currentInnings: 1,
        battingTeamId: innings1BattingTeamId,
        bowlingTeamId: innings1BowlingTeamId,
        strikerId: match.ballHistory.length === 0 ? match.strikerId : null,
        nonStrikerId: match.ballHistory.length === 0 ? match.nonStrikerId : null,
        currentBowlerId: match.ballHistory.length === 0 ? match.currentBowlerId : null,
        lastBowlerId: null,
        pendingAction: PendingAction.NONE
      };
      snapshots.length = 0;
    }

    let ballsInOver = current.totalBalls % 6;
    let itemsProcessed = startIndex;

    for (let i = startIndex; i < match.ballHistory.length; i++) {
      const ball = match.ballHistory[i];
      itemsProcessed++;
      if (current.status === MatchStatus.COMPLETED) break;

      const isBattingA = this.isTeamA(current.battingTeamId, current);
      const battingTeam = isBattingA ? current.teamA : current.teamB;
      const bowlingTeam = isBattingA ? current.teamB : current.teamA;

      const healedBall: Ball = {
        ...ball,
        strikerId: this.healLegacyId(ball.strikerId, battingTeam) ?? null,
        nonStrikerId: this.healLegacyId(ball.nonStrikerId, battingTeam) ?? null,
        bowlerId: this.healLegacyId(ball.bowlerId, bowlingTeam) ?? null,
        outPlayerId: this.healLegacyId(ball.outPlayerId, battingTeam) ?? null,
      };

      const newBattingOrder = [...current.battingOrder];
      if (healedBall.strikerId && !newBattingOrder.includes(healedBall.strikerId)) {
        newBattingOrder.push(healedBall.strikerId);
      }
      if (healedBall.nonStrikerId && !newBattingOrder.includes(healedBall.nonStrikerId)) {
        newBattingOrder.push(healedBall.nonStrikerId);
      }
      const outId = healedBall.outPlayerId ?? (healedBall.wicketType !== WicketType.NONE && healedBall.wicketType !== WicketType.RETIRED_HURT ? healedBall.strikerId : null);
      if (outId && !newBattingOrder.includes(outId)) {
        newBattingOrder.push(outId);
      }

      current = {
        ...current,
        totalRuns: current.totalRuns + healedBall.runs + healedBall.extraRuns,
        totalWickets: current.totalWickets + (healedBall.wicketType !== WicketType.NONE && healedBall.wicketType !== WicketType.RETIRED_HURT ? 1 : 0),
        totalBalls: current.totalBalls + (isPhysicalBall(healedBall) ? 1 : 0),
        wideCount: current.wideCount + (healedBall.extrasType === ExtrasType.WIDE ? healedBall.extraRuns : 0),
        noBallCount: current.noBallCount + (healedBall.extrasType === ExtrasType.NO_BALL ? healedBall.extraRuns : 0),
        byeCount: current.byeCount + (healedBall.extrasType === ExtrasType.BYE ? healedBall.extraRuns : 0),
        legByeCount: current.legByeCount + (healedBall.extrasType === ExtrasType.LEG_BYE ? healedBall.extraRuns : 0),
        battingOrder: newBattingOrder,
        teamA: this.updateTeamStats(current.teamA, healedBall, isBattingA, !isBattingA),
        teamB: this.updateTeamStats(current.teamB, healedBall, !isBattingA, isBattingA),
        ballHistory: match.ballHistory.slice(0, itemsProcessed)
      };

      if (healedBall.wicketType !== WicketType.NONE && healedBall.wicketType !== WicketType.RETIRED_HURT) {
        const outName = battingTeam.players.find(p => p.id === outId)?.name ?? 'Unknown';
        const bName = bowlingTeam.players.find(p => p.id === healedBall.bowlerId)?.name;
        const fName = bowlingTeam.players.find(p => p.id === healedBall.fielderId)?.name;
        const record: WicketRecord = {
          wicketNumber: current.totalWickets,
          batterName: `☝️ ${outName}`,
          totalRuns: current.totalRuns,
          over: `${Math.floor(current.totalBalls / 6)}.${current.totalBalls % 6}`,
          wicketType: healedBall.wicketType,
          bowlerName: bName,
          fielderName: fName,
          dismissalReason: healedBall.dismissalReason
        };
        current.wicketHistory = [...current.wicketHistory, record];
      }

      if (isPhysicalBall(healedBall)) ballsInOver++;

      let sId = current.strikerId;
      let nsId = current.nonStrikerId;
      let activeBId = current.currentBowlerId;
      let lbId = current.lastBowlerId;

      if (!healedBall.isAdjustment) {
        const victimId = healedBall.outPlayerId ?? (healedBall.wicketType !== WicketType.NONE ? healedBall.strikerId : null);
        if (!sId && healedBall.strikerId && healedBall.strikerId !== victimId && !this.isPlayerUnavailable(healedBall.strikerId, current)) {
          sId = healedBall.strikerId;
        }
        if (!nsId && healedBall.nonStrikerId && healedBall.nonStrikerId !== victimId && !this.isPlayerUnavailable(healedBall.nonStrikerId, current)) {
          nsId = healedBall.nonStrikerId;
        }
        if (!activeBId) activeBId = healedBall.bowlerId;
      }

      if (healedBall.isAdjustment) {
        switch (healedBall.adjustmentSlot) {
          case 'STRIKER':
            if (!sId || healedBall.isReplacement) sId = healedBall.adjustmentPlayerId;
            break;
          case 'NON_STRIKER':
            if (!nsId || healedBall.isReplacement) nsId = healedBall.adjustmentPlayerId;
            break;
          case 'BOWLER':
            if (!activeBId || healedBall.isReplacement) activeBId = healedBall.adjustmentPlayerId;
            break;
          case 'SWAP':
            const temp = sId;
            sId = nsId;
            nsId = temp;
            break;
        }
        current = { ...current, strikerId: sId, nonStrikerId: nsId, currentBowlerId: activeBId };

        if (ballsInOver === 0 && current.totalBalls > 0 && current.totalBalls % 6 === 0) {
          if (!snapshots.some(s => s.ballHistory.length === current.ballHistory.length)) {
            snapshots.push(current);
          }
        }
        continue;
      }

      let physicalRuns = 0;
      if (healedBall.extrasType === ExtrasType.WIDE) {
        physicalRuns = current.gullyRules.noExtraRunsForWidesNoBalls ? healedBall.extraRuns : Math.max(0, healedBall.extraRuns - 1);
      } else if (healedBall.extrasType === ExtrasType.BYE || healedBall.extrasType === ExtrasType.LEG_BYE) {
        physicalRuns = healedBall.extraRuns;
      } else {
        physicalRuns = healedBall.runs + (healedBall.extrasType === ExtrasType.GRANTED ? healedBall.extraRuns : 0);
      }

      const shouldRotate = (physicalRuns % 2 !== 0 !== healedBall.hadCrossed) && healedBall.rotateStrike && healedBall.extrasType !== ExtrasType.GRANTED;
      if (shouldRotate) {
        const t = sId;
        sId = nsId;
        nsId = t;
      }

      if (healedBall.wicketType !== WicketType.NONE) {
        const victimId = healedBall.outPlayerId ?? healedBall.strikerId;
        if (healedBall.wicketType === WicketType.CAUGHT) {
          sId = null;
        } else {
          if (sId === victimId) sId = null;
          else if (nsId === victimId) nsId = null;
        }
      }

      let overJustFinished = false;
      if (ballsInOver === 6) {
        const t = sId;
        sId = nsId;
        nsId = t;
        lbId = activeBId;
        ballsInOver = 0;
        overJustFinished = true;
      }

      const squadSize = current.gullyRules.unequalTeams ? battingTeam.players.length : Math.max(1, Math.min(current.teamA.players.length, current.teamB.players.length));
      const maxWickets = current.gullyRules.lastManStanding ? squadSize : Math.max(1, squadSize - 1);
      const needsNonStriker = current.gullyRules.lastManStanding ? current.totalWickets < squadSize - 1 : true;

      if (!sId && nsId && !needsNonStriker) {
        sId = nsId;
        nsId = null;
      }

      current = {
        ...current,
        strikerId: sId,
        nonStrikerId: nsId,
        currentBowlerId: overJustFinished ? null : activeBId,
        lastBowlerId: lbId
      };

      const inningsEnded = current.totalWickets >= maxWickets || current.totalBalls >= current.oversPerInnings * 6;

      if (current.currentInnings === 1 && inningsEnded) {
        const i1EndTime = match.innings1EndTimeMillis ?? Date.now();
        const i1StartTime = match.startTimeMillis ?? i1EndTime;
        const i1Duration = Math.max(0, Math.floor((i1EndTime - i1StartTime) / 60000));

        const innings1Data: InningsSummary = {
          runs: current.totalRuns,
          wickets: current.totalWickets,
          balls: current.totalBalls,
          teamId: current.battingTeamId,
          wicketHistory: current.wicketHistory,
          wideCount: current.wideCount,
          noBallCount: current.noBallCount,
          byeCount: current.byeCount,
          legByeCount: current.legByeCount,
          recordedBallsCount: itemsProcessed,
          durationMinutes: i1Duration,
          battingOrder: current.battingOrder
        };

        current = {
          ...current,
          innings1Data,
          currentInnings: 2,
          target: current.totalRuns + 1,
          battingTeamId: current.bowlingTeamId,
          bowlingTeamId: current.battingTeamId,
          totalRuns: 0,
          totalWickets: 0,
          totalBalls: 0,
          wideCount: 0,
          noBallCount: 0,
          byeCount: 0,
          legByeCount: 0,
          wicketHistory: [],
          battingOrder: [],
          strikerId: null,
          nonStrikerId: null,
          currentBowlerId: null,
          lastBowlerId: null,
          pendingAction: match.isSecondInningsStarted ? PendingAction.NONE : PendingAction.START_SECOND_INNINGS
        };
        ballsInOver = 0;
        overJustFinished = true;
      } else if (current.currentInnings === 2 && current.status === MatchStatus.LIVE && current.target != null && (current.totalBalls > 0 || current.totalWickets > 0)) {
        const targetValue = current.target;
        if (current.totalRuns >= targetValue) {
          current = { ...current, status: MatchStatus.COMPLETED, winnerId: current.battingTeamId };
          overJustFinished = true;
        } else if (inningsEnded) {
          current = {
            ...current,
            status: MatchStatus.COMPLETED,
            winnerId: current.totalRuns < targetValue - 1 ? current.bowlingTeamId : null
          };
          overJustFinished = true;
        }
      }

      if (overJustFinished) {
        if (!snapshots.some(s => s.ballHistory.length === current.ballHistory.length)) {
          snapshots.push(current);
        }
      }
    }

    if (current.status === MatchStatus.LIVE) {
      const batTeam = this.isTeamA(current.battingTeamId, current) ? current.teamA : current.teamB;
      const squadSize = current.gullyRules.unequalTeams ? batTeam.players.length : Math.max(1, Math.min(current.teamA.players.length, current.teamB.players.length));
      const maxWickets = current.gullyRules.lastManStanding ? squadSize : Math.max(1, squadSize - 1);
      const needsNonStriker = current.gullyRules.lastManStanding ? current.totalWickets < squadSize - 1 : true;
      const inningsEnded = current.totalWickets >= maxWickets || current.totalBalls >= current.oversPerInnings * 6;

      if (current.currentInnings === 1 && inningsEnded) {
        const i1EndTime = match.innings1EndTimeMillis ?? Date.now();
        const i1StartTime = match.startTimeMillis ?? i1EndTime;
        const i1Duration = Math.max(0, Math.floor((i1EndTime - i1StartTime) / 60000));

        const innings1Data: InningsSummary = {
          runs: current.totalRuns,
          wickets: current.totalWickets,
          balls: current.totalBalls,
          teamId: current.battingTeamId,
          wicketHistory: current.wicketHistory,
          wideCount: current.wideCount,
          noBallCount: current.noBallCount,
          byeCount: current.byeCount,
          legByeCount: current.legByeCount,
          recordedBallsCount: itemsProcessed,
          durationMinutes: i1Duration,
          battingOrder: current.battingOrder
        };

        current = {
          ...current,
          innings1Data,
          currentInnings: 2,
          target: current.totalRuns + 1,
          battingTeamId: current.bowlingTeamId,
          bowlingTeamId: current.battingTeamId,
          totalRuns: 0,
          totalWickets: 0,
          totalBalls: 0,
          wideCount: 0,
          noBallCount: 0,
          byeCount: 0,
          legByeCount: 0,
          wicketHistory: [],
          battingOrder: [],
          strikerId: null,
          nonStrikerId: null,
          currentBowlerId: null,
          lastBowlerId: null,
          pendingAction: match.isSecondInningsStarted ? PendingAction.NONE : PendingAction.START_SECOND_INNINGS
        };
      }

      if (match.pendingAction === PendingAction.SELECT_MATCH_SETTINGS || match.pendingAction === PendingAction.TOSS_REQUIRED) {
        current = { ...current, pendingAction: match.pendingAction };
      } else if (current.pendingAction === PendingAction.NONE) {
        if (!inningsEnded || current.currentInnings === 2) {
          if (!current.strikerId) {
            current = { ...current, pendingAction: PendingAction.SELECT_STRIKER };
          } else if (needsNonStriker && !current.nonStrikerId) {
            current = { ...current, pendingAction: PendingAction.SELECT_NON_STRIKER };
          } else if (!current.currentBowlerId) {
            current = { ...current, pendingAction: PendingAction.SELECT_BOWLER };
          }
        }
      }
    }

    return current;
  }

  public static isPlayerOut(pId: string | null | undefined, m: Match): boolean {
    if (!pId) return false;
    const p = m.teamA.players.find(x => x.id === pId) ?? m.teamB.players.find(x => x.id === pId);
    return p?.battingStats.isOut === true;
  }

  public static isPlayerUnavailable(pId: string | null | undefined, m: Match): boolean {
    if (!pId) return false;
    const p = m.teamA.players.find(x => x.id === pId) ?? m.teamB.players.find(x => x.id === pId);
    return p?.battingStats.isOut === true || p?.battingStats.isRetiredHurt === true;
  }

  public static resetTeamStats(team: Team): Team {
    return {
      ...team,
      players: team.players.map(p => ({
        ...p,
        battingStats: {
          runs: 0, balls: 0, fours: 0, sixes: 0, isOut: false, isRetiredHurt: false,
          wicketType: WicketType.NONE, dismissalBowlerId: null, dismissalFielderId: null
        },
        bowlingStats: {
          overs: 0, balls: 0, maidens: 0, runsConceded: 0, wickets: 0, dotBalls: 0, wides: 0, noBalls: 0
        },
        fieldingStats: {
          catches: 0, runOuts: 0, stumpings: 0, droppedCatches: 0
        }
      }))
    };
  }

  public static updateTeamStats(team: Team, ball: Ball, isBat: boolean, isBowl: boolean): Team {
    return {
      ...team,
      players: team.players.map(p => {
        let np = { ...p };
        if (isBat) {
          const outId = ball.outPlayerId ?? (ball.wicketType !== WicketType.NONE ? ball.strikerId : null);
          const isOut = p.id === outId;

          if (p.id === ball.strikerId) {
            const actualRuns = ball.runs + (ball.extrasType === ExtrasType.GRANTED ? ball.extraRuns : 0);
            np.battingStats = {
              ...p.battingStats,
              runs: p.battingStats.runs + actualRuns,
              balls: p.battingStats.balls + (ball.isLegalBall || ball.extrasType === ExtrasType.NO_BALL ? 1 : 0),
              fours: p.battingStats.fours + (actualRuns === 4 ? 1 : 0),
              sixes: p.battingStats.sixes + (actualRuns === 6 ? 1 : 0),
              isOut: p.battingStats.isOut || (isOut && ball.wicketType !== WicketType.RETIRED_HURT),
              isRetiredHurt: ball.wicketType === WicketType.RETIRED_HURT,
              wicketType: isOut ? ball.wicketType : p.battingStats.wicketType,
              dismissalBowlerId: isOut && ball.wicketType !== WicketType.RUN_OUT && ball.wicketType !== WicketType.RETIRED_HURT ? ball.bowlerId : p.battingStats.dismissalBowlerId,
              dismissalFielderId: isOut ? ball.fielderId : p.battingStats.dismissalFielderId
            };
          } else if (p.id === ball.nonStrikerId) {
            if (isOut) {
              np.battingStats = {
                ...p.battingStats,
                isOut: ball.wicketType !== WicketType.RETIRED_HURT,
                isRetiredHurt: ball.wicketType === WicketType.RETIRED_HURT,
                wicketType: ball.wicketType,
                dismissalFielderId: ball.fielderId
              };
            } else {
              np.battingStats = { ...p.battingStats, isRetiredHurt: false };
            }
          }
        }

        if (isBowl && p.id === ball.bowlerId) {
          let nb = p.bowlingStats.balls;
          let no = p.bowlingStats.overs;
          if (ball.isLegalBall) {
            nb++;
            if (nb === 6) {
              no++;
              nb = 0;
            }
          }

          const runsToBowler = (ball.extrasType === ExtrasType.BYE || ball.extrasType === ExtrasType.LEG_BYE) ? ball.runs : (ball.runs + ball.extraRuns);

          np.bowlingStats = {
            ...p.bowlingStats,
            runsConceded: p.bowlingStats.runsConceded + runsToBowler,
            balls: nb,
            overs: no,
            wickets: p.bowlingStats.wickets + (ball.wicketType !== WicketType.NONE && ball.wicketType !== WicketType.RUN_OUT && ball.wicketType !== WicketType.RETIRED_HURT ? 1 : 0),
            dotBalls: p.bowlingStats.dotBalls + (ball.runs === 0 && ball.extraRuns === 0 ? 1 : 0),
            wides: p.bowlingStats.wides + (ball.extrasType === ExtrasType.WIDE ? 1 : 0),
            noBalls: p.bowlingStats.noBalls + (ball.extrasType === ExtrasType.NO_BALL ? 1 : 0)
          };
        }

        if (!isBat && p.id === ball.fielderId) {
          np.fieldingStats = {
            ...p.fieldingStats,
            catches: p.fieldingStats.catches + (ball.wicketType === WicketType.CAUGHT ? 1 : 0),
            runOuts: p.fieldingStats.runOuts + (ball.wicketType === WicketType.RUN_OUT ? 1 : 0),
            stumpings: p.fieldingStats.stumpings + (ball.wicketType === WicketType.STUMPED ? 1 : 0),
            droppedCatches: p.fieldingStats.droppedCatches + (ball.isDroppedCatch ? 1 : 0)
          };
        }

        return np;
      })
    };
  }
}
