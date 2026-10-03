import React, { useState } from 'react';
import { ArrowLeft, ArrowRight, Play, UserPlus, Trash2, AlertCircle } from 'lucide-react';
import { Team, Player, BattingStyle, createDefaultPlayer } from '../../domain/models';
import { Validators } from '../../domain/validators';
import { useTournament } from '../../state/TournamentContext';
import { useMatch } from '../../state/MatchContext';

interface QuickMatchSetupProps {
  onCancel: () => void;
  onComplete: () => void;
}

export const QuickMatchSetup: React.FC<QuickMatchSetupProps> = ({ onCancel, onComplete }) => {
  const { createQuickMatchTournament, addPlayerToGlobalList } = useTournament();
  const { loadMatch, handleToss } = useMatch();

  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Step 1: Teams
  const [teamAName, setTeamAName] = useState('Team Alpha');
  const [teamBName, setTeamBName] = useState('Team Beta');

  // Step 2: Players with stable unique IDs
  const [teamAPlayers, setTeamAPlayers] = useState<Player[]>([
    addPlayerToGlobalList('Player 1', BattingStyle.RHB),
    addPlayerToGlobalList('Player 2', BattingStyle.RHB),
    addPlayerToGlobalList('Player 3', BattingStyle.LHB),
    addPlayerToGlobalList('Player 4', BattingStyle.RHB)
  ]);
  const [teamBPlayers, setTeamBPlayers] = useState<Player[]>([
    addPlayerToGlobalList('Player A', BattingStyle.RHB),
    addPlayerToGlobalList('Player B', BattingStyle.LHB),
    addPlayerToGlobalList('Player C', BattingStyle.RHB),
    addPlayerToGlobalList('Player D', BattingStyle.RHB)
  ]);

  const [newPlayerA, setNewPlayerA] = useState('');
  const [newPlayerB, setNewPlayerB] = useState('');

  // Step 3: Match Settings
  const [overs, setOvers] = useState(5);

  // Step 4: Toss
  const [tossWinner, setTossWinner] = useState<'A' | 'B'>('A');
  const [tossDecision, setTossDecision] = useState<'BAT' | 'BOWL'>('BAT');

  const handleAddPlayerA = () => {
    if (!newPlayerA.trim()) return;
    const trimmed = newPlayerA.trim();
    if (teamAPlayers.some(p => p.name.trim().toLowerCase() === trimmed.toLowerCase())) {
      setErrorMessage(`Player "${trimmed}" is already in ${teamAName}'s squad.`);
      return;
    }
    setErrorMessage(null);
    const p = addPlayerToGlobalList(trimmed, BattingStyle.RHB);
    setTeamAPlayers([...teamAPlayers, p]);
    setNewPlayerA('');
  };

  const handleAddPlayerB = () => {
    if (!newPlayerB.trim()) return;
    const trimmed = newPlayerB.trim();
    if (teamBPlayers.some(p => p.name.trim().toLowerCase() === trimmed.toLowerCase())) {
      setErrorMessage(`Player "${trimmed}" is already in ${teamBName}'s squad.`);
      return;
    }
    setErrorMessage(null);
    const p = addPlayerToGlobalList(trimmed, BattingStyle.RHB);
    setTeamBPlayers([...teamBPlayers, p]);
    setNewPlayerB('');
  };

  const validateStep1 = () => {
    if (!teamAName.trim() || !teamBName.trim()) {
      setErrorMessage('Both team names are required.');
      return false;
    }
    if (teamAName.trim().toLowerCase() === teamBName.trim().toLowerCase()) {
      setErrorMessage('Team A and Team B must have distinct names.');
      return false;
    }
    setErrorMessage(null);
    return true;
  };

  const validateStep2 = () => {
    if (teamAPlayers.length === 0) {
      setErrorMessage(`${teamAName} must have at least 1 player.`);
      return false;
    }
    if (teamBPlayers.length === 0) {
      setErrorMessage(`${teamBName} must have at least 1 player.`);
      return false;
    }
    setErrorMessage(null);
    return true;
  };

  const validateStep3 = () => {
    if (overs < 1 || overs > 50) {
      setErrorMessage('Overs per innings must be between 1 and 50.');
      return false;
    }
    setErrorMessage(null);
    return true;
  };

  const handleFinishSetup = () => {
    const teamA: Team = {
      id: 'team_a_' + Date.now(),
      name: teamAName.trim() || 'Team A',
      players: teamAPlayers,
      matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0
    };

    const teamB: Team = {
      id: 'team_b_' + Date.now(),
      name: teamBName.trim() || 'Team B',
      players: teamBPlayers,
      matchesPlayed: 0, wins: 0, losses: 0, points: 0, nrr: 0
    };

    const validation = Validators.validateQuickMatchSetup(teamA, teamB, overs);
    if (!validation.isValid) {
      setErrorMessage(validation.errors[0]);
      return;
    }

    const { match } = createQuickMatchTournament(teamA, teamB, overs);
    loadMatch(match);

    const winnerId = tossWinner === 'A' ? teamA.id : teamB.id;
    handleToss(winnerId, tossDecision);

    onComplete();
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6 pb-12">
      {/* Wizard Header */}
      <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-4">
        <button onClick={onCancel} className="text-slate-500 hover:text-slate-800 flex items-center space-x-1 text-sm font-semibold">
          <ArrowLeft className="w-4 h-4" />
          <span>Cancel</span>
        </button>
        <div className="text-center">
          <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">Quick Match Setup</span>
          <h2 className="text-lg font-extrabold text-slate-900 dark:text-white">Step {step} of 4</h2>
        </div>
        <div className="w-12" />
      </div>

      {/* Progress Bar */}
      <div className="flex space-x-2">
        {[1, 2, 3, 4].map(s => (
          <div
            key={s}
            className={`h-2 flex-1 rounded-full transition-all ${
              s <= step ? 'bg-emerald-600' : 'bg-slate-200 dark:bg-slate-800'
            }`}
          />
        ))}
      </div>

      {/* Error Message Banner */}
      {errorMessage && (
        <div className="bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 rounded-2xl p-3.5 text-xs font-bold text-red-700 dark:text-red-300 flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Step 1: Teams */}
      {step === 1 && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 shadow-sm space-y-6">
          <h3 className="font-extrabold text-xl text-slate-900 dark:text-white">Select Team Names</h3>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase mb-2">Team A Name</label>
              <input
                type="text"
                value={teamAName}
                onChange={e => { setTeamAName(e.target.value); setErrorMessage(null); }}
                className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-white font-bold text-base focus:ring-2 focus:ring-emerald-500 outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase mb-2">Team B Name</label>
              <input
                type="text"
                value={teamBName}
                onChange={e => { setTeamBName(e.target.value); setErrorMessage(null); }}
                className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-white font-bold text-base focus:ring-2 focus:ring-emerald-500 outline-none"
              />
            </div>
          </div>

          <button
            onClick={() => {
              if (validateStep1()) setStep(2);
            }}
            className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-3.5 rounded-2xl flex items-center justify-center space-x-2 shadow-lg transition-transform active:scale-98"
          >
            <span>Next: Add Players</span>
            <ArrowRight className="w-5 h-5" />
          </button>
        </div>
      )}

      {/* Step 2: Players */}
      {step === 2 && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 shadow-sm space-y-6">
          <h3 className="font-extrabold text-xl text-slate-900 dark:text-white">Squad Players</h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Team A Players */}
            <div className="space-y-3">
              <h4 className="font-bold text-emerald-600 dark:text-emerald-400 text-sm">{teamAName} Squad ({teamAPlayers.length})</h4>
              <div className="flex space-x-2">
                <input
                  type="text"
                  placeholder="Add Player Name"
                  value={newPlayerA}
                  onChange={e => { setNewPlayerA(e.target.value); setErrorMessage(null); }}
                  onKeyDown={e => e.key === 'Enter' && handleAddPlayerA()}
                  className="flex-1 px-3 py-2 text-sm border rounded-xl border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-white"
                />
                <button onClick={handleAddPlayerA} className="px-3 py-2 bg-emerald-600 text-white rounded-xl text-xs font-bold flex items-center">
                  <UserPlus className="w-4 h-4" />
                </button>
              </div>
              <div className="space-y-1.5 max-h-48 overflow-y-auto">
                {teamAPlayers.map((p, idx) => (
                  <div key={p.id} className="flex items-center justify-between p-2 rounded-lg bg-slate-50 dark:bg-slate-800 text-xs font-semibold text-slate-800 dark:text-slate-200">
                    <span>{idx + 1}. {p.name}</span>
                    <button onClick={() => setTeamAPlayers(teamAPlayers.filter(x => x.id !== p.id))} className="text-red-500 hover:text-red-700">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            {/* Team B Players */}
            <div className="space-y-3">
              <h4 className="font-bold text-emerald-600 dark:text-emerald-400 text-sm">{teamBName} Squad ({teamBPlayers.length})</h4>
              <div className="flex space-x-2">
                <input
                  type="text"
                  placeholder="Add Player Name"
                  value={newPlayerB}
                  onChange={e => { setNewPlayerB(e.target.value); setErrorMessage(null); }}
                  onKeyDown={e => e.key === 'Enter' && handleAddPlayerB()}
                  className="flex-1 px-3 py-2 text-sm border rounded-xl border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-white"
                />
                <button onClick={handleAddPlayerB} className="px-3 py-2 bg-emerald-600 text-white rounded-xl text-xs font-bold flex items-center">
                  <UserPlus className="w-4 h-4" />
                </button>
              </div>
              <div className="space-y-1.5 max-h-48 overflow-y-auto">
                {teamBPlayers.map((p, idx) => (
                  <div key={p.id} className="flex items-center justify-between p-2 rounded-lg bg-slate-50 dark:bg-slate-800 text-xs font-semibold text-slate-800 dark:text-slate-200">
                    <span>{idx + 1}. {p.name}</span>
                    <button onClick={() => setTeamBPlayers(teamBPlayers.filter(x => x.id !== p.id))} className="text-red-500 hover:text-red-700">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="flex space-x-3 pt-2">
            <button onClick={() => { setErrorMessage(null); setStep(1); }} className="w-1/3 py-3 border border-slate-300 dark:border-slate-700 font-bold rounded-2xl text-slate-700 dark:text-slate-300">
              Back
            </button>
            <button onClick={() => { if (validateStep2()) setStep(3); }} className="w-2/3 bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-3 rounded-2xl flex items-center justify-center space-x-2">
              <span>Next: Match Settings</span>
              <ArrowRight className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}

      {/* Step 3: Settings */}
      {step === 3 && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 shadow-sm space-y-6">
          <h3 className="font-extrabold text-xl text-slate-900 dark:text-white">Match Overs</h3>

          <div className="space-y-4">
            <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase">Overs Per Innings</label>
            <div className="grid grid-cols-4 gap-3">
              {[2, 5, 10, 20].map(o => (
                <button
                  key={o}
                  onClick={() => { setOvers(o); setErrorMessage(null); }}
                  className={`py-3.5 rounded-2xl font-extrabold text-base border transition-all ${
                    overs === o
                      ? 'bg-emerald-600 text-white border-emerald-600 shadow-md'
                      : 'bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700'
                  }`}
                >
                  {o} Overs
                </button>
              ))}
            </div>
          </div>

          <div className="flex space-x-3 pt-2">
            <button onClick={() => { setErrorMessage(null); setStep(2); }} className="w-1/3 py-3 border border-slate-300 dark:border-slate-700 font-bold rounded-2xl text-slate-700 dark:text-slate-300">
              Back
            </button>
            <button onClick={() => { if (validateStep3()) setStep(4); }} className="w-2/3 bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-3 rounded-2xl flex items-center justify-center space-x-2">
              <span>Next: Coin Toss</span>
              <ArrowRight className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}

      {/* Step 4: Toss */}
      {step === 4 && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 shadow-sm space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="font-extrabold text-xl text-slate-900 dark:text-white">Toss & Decision</h3>
            <div className="flex space-x-2">
              <img src="/img/coin_heads.png" alt="Coin Heads" className="w-8 h-8 rounded-full shadow-sm" onError={e => (e.target as HTMLElement).style.display = 'none'} />
              <img src="/img/coin_tails.png" alt="Coin Tails" className="w-8 h-8 rounded-full shadow-sm" onError={e => (e.target as HTMLElement).style.display = 'none'} />
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase mb-2">Who won the toss?</label>
              <div className="grid grid-cols-2 gap-3">
                <button
                  onClick={() => setTossWinner('A')}
                  className={`py-3.5 rounded-2xl font-bold border ${
                    tossWinner === 'A' ? 'bg-emerald-600 text-white border-emerald-600' : 'bg-slate-50 dark:bg-slate-800 text-slate-800 dark:text-slate-200'
                  }`}
                >
                  {teamAName}
                </button>
                <button
                  onClick={() => setTossWinner('B')}
                  className={`py-3.5 rounded-2xl font-bold border ${
                    tossWinner === 'B' ? 'bg-emerald-600 text-white border-emerald-600' : 'bg-slate-50 dark:bg-slate-800 text-slate-800 dark:text-slate-200'
                  }`}
                >
                  {teamBName}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase mb-2">Elected to?</label>
              <div className="grid grid-cols-2 gap-3">
                <button
                  onClick={() => setTossDecision('BAT')}
                  className={`py-3.5 rounded-2xl font-bold border ${
                    tossDecision === 'BAT' ? 'bg-emerald-600 text-white border-emerald-600' : 'bg-slate-50 dark:bg-slate-800 text-slate-800 dark:text-slate-200'
                  }`}
                >
                  BAT FIRST
                </button>
                <button
                  onClick={() => setTossDecision('BOWL')}
                  className={`py-3.5 rounded-2xl font-bold border ${
                    tossDecision === 'BOWL' ? 'bg-emerald-600 text-white border-emerald-600' : 'bg-slate-50 dark:bg-slate-800 text-slate-800 dark:text-slate-200'
                  }`}
                >
                  BOWL FIRST
                </button>
              </div>
            </div>
          </div>

          <button
            onClick={handleFinishSetup}
            className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold py-4 rounded-2xl flex items-center justify-center space-x-2 shadow-xl transition-transform active:scale-98 text-base"
          >
            <Play className="w-5 h-5 fill-white" />
            <span>Start Live Match</span>
          </button>
        </div>
      )}
    </div>
  );
};
