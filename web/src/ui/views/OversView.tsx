import React from 'react';
import { useMatch } from '../../state/MatchContext';
import { Ball, ExtrasType, WicketType, isPhysicalBall } from '../../domain/models';

export const OversView: React.FC = () => {
  const { match } = useMatch();

  if (!match || match.ballHistory.length === 0) {
    return <div className="text-center py-12 text-slate-500">No balls recorded yet for this match.</div>;
  }

  // Group balls by overs
  const oversGrouped: { overNumber: number; balls: Ball[] }[] = [];
  let currentOverBalls: Ball[] = [];
  let physicalCount = 0;
  let overNum = 1;

  match.ballHistory.forEach(b => {
    currentOverBalls.push(b);
    if (isPhysicalBall(b)) {
      physicalCount++;
      if (physicalCount === 6) {
        oversGrouped.push({ overNumber: overNum, balls: currentOverBalls });
        overNum++;
        currentOverBalls = [];
        physicalCount = 0;
      }
    }
  });

  if (currentOverBalls.length > 0) {
    oversGrouped.push({ overNumber: overNum, balls: currentOverBalls });
  }

  return (
    <div className="max-w-4xl mx-auto space-y-4 pb-12">
      <h2 className="font-extrabold text-xl text-slate-900 dark:text-white mb-2">Over-by-Over Timeline</h2>

      <div className="space-y-3">
        {oversGrouped.slice().reverse().map(og => {
          const runsInOver = og.balls.reduce((acc, b) => acc + b.runs + b.extraRuns, 0);
          const wicketsInOver = og.balls.filter(b => b.wicketType !== WicketType.NONE && b.wicketType !== WicketType.RETIRED_HURT).length;

          return (
            <div key={og.overNumber} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 shadow-sm space-y-3">
              <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2">
                <span className="font-extrabold text-sm text-slate-900 dark:text-white">Over {og.overNumber}</span>
                <span className="text-xs font-bold text-emerald-600 bg-emerald-50 dark:bg-emerald-950 px-2.5 py-1 rounded-full">
                  {runsInOver} Runs, {wicketsInOver} Wkts
                </span>
              </div>

              <div className="flex flex-wrap gap-2">
                {og.balls.map((b, idx) => {
                  let label = `${b.runs}`;
                  let bg = 'bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-white';

                  if (b.wicketType !== WicketType.NONE) {
                    label = 'W';
                    bg = 'bg-red-600 text-white font-extrabold';
                  } else if (b.extrasType === ExtrasType.WIDE) {
                    label = `${b.extraRuns}wd`;
                    bg = 'bg-amber-500 text-white font-bold';
                  } else if (b.extrasType === ExtrasType.NO_BALL) {
                    label = `${b.runs + b.extraRuns}nb`;
                    bg = 'bg-amber-600 text-white font-bold';
                  } else if (b.runs === 4) {
                    bg = 'bg-blue-600 text-white font-extrabold';
                  } else if (b.runs === 6) {
                    bg = 'bg-purple-600 text-white font-extrabold';
                  }

                  return (
                    <span key={idx} className={`w-9 h-9 rounded-xl flex items-center justify-center text-xs shadow-xs ${bg}`}>
                      {label}
                    </span>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
