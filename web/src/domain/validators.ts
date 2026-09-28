import { Match, Team, Player, GullyRules } from './models';

export interface ValidationResult {
  isValid: boolean;
  errors: string[];
}

export class Validators {
  public static validateQuickMatchSetup(
    teamA: Team,
    teamB: Team,
    overs: number
  ): ValidationResult {
    const errors: string[] = [];

    if (!teamA.name.trim()) errors.push('Team A name cannot be empty');
    if (!teamB.name.trim()) errors.push('Team B name cannot be empty');
    if (teamA.name.trim().toLowerCase() === teamB.name.trim().toLowerCase()) {
      errors.push('Team names must be distinct');
    }

    if (teamA.players.length === 0) errors.push('Team A must have at least 1 player');
    if (teamB.players.length === 0) errors.push('Team B must have at least 1 player');

    if (overs <= 0 || overs > 50) {
      errors.push('Overs per innings must be between 1 and 50');
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }

  public static validateToss(
    match: Match,
    winnerId: string,
    decision: 'BAT' | 'BOWL'
  ): ValidationResult {
    const errors: string[] = [];

    if (winnerId !== match.teamA.id && winnerId !== match.teamB.id) {
      errors.push('Toss winner must be one of the participating teams');
    }

    if (decision !== 'BAT' && decision !== 'BOWL') {
      errors.push('Toss decision must be BAT or BOWL');
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }

  public static validatePlayerRemoval(
    match: Match,
    playerId: string
  ): ValidationResult {
    const errors: string[] = [];

    if (
      playerId === match.strikerId ||
      playerId === match.nonStrikerId ||
      playerId === match.currentBowlerId
    ) {
      errors.push('Cannot remove an active on-field player (Striker, Non-Striker, or Bowler)');
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }
}
