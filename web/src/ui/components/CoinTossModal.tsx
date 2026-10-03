import React, { useState } from 'react';
import { X, RefreshCw, Sparkles } from 'lucide-react';

interface CoinTossModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CoinTossModal: React.FC<CoinTossModalProps> = ({ isOpen, onClose }) => {
  const [result, setResult] = useState<'HEADS' | 'TAILS' | null>(null);
  const [isFlipping, setIsFlipping] = useState(false);

  if (!isOpen) return null;

  const handleFlip = () => {
    if (isFlipping) return;
    setIsFlipping(true);
    setResult(null);

    setTimeout(() => {
      const outcome = Math.random() < 0.5 ? 'HEADS' : 'TAILS';
      setResult(outcome);
      setIsFlipping(false);
    }, 1200);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-sm w-full space-y-6 shadow-2xl text-white text-center">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2 text-xs font-bold text-amber-400 uppercase tracking-wider">
            <Sparkles className="w-4 h-4" />
            <span>3D Virtual Coin Toss</span>
          </div>
          <button onClick={onClose} className="p-1 text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 3D Coin Animation Area */}
        <div className="py-6 flex flex-col items-center justify-center space-y-4">
          <div
            className={`w-32 h-32 rounded-full border-4 border-amber-400 shadow-2xl overflow-hidden flex items-center justify-center bg-gradient-to-tr from-amber-600 via-yellow-500 to-amber-300 transition-all duration-700 ${
              isFlipping ? 'animate-bounce scale-110 rotate-180' : ''
            }`}
          >
            {result === 'TAILS' ? (
              <img src="/img/coin_tails.png" alt="Tails" className="w-full h-full object-cover" />
            ) : (
              <img src="/img/coin_heads.png" alt="Heads" className="w-full h-full object-cover" />
            )}
          </div>

          <div className="min-h-[32px]">
            {isFlipping ? (
              <div className="text-amber-400 font-extrabold text-sm animate-pulse">
                Flipping coin in the air... 🪙
              </div>
            ) : result ? (
              <div className="text-2xl font-black text-amber-300 uppercase tracking-wider">
                IT'S {result}! 🎉
              </div>
            ) : (
              <div className="text-xs text-slate-400 font-medium">
                Tap button below to flip the coin for your match toss.
              </div>
            )}
          </div>
        </div>

        {/* Flip Button */}
        <button
          onClick={handleFlip}
          disabled={isFlipping}
          className="w-full py-3.5 bg-gradient-to-r from-amber-500 to-yellow-600 hover:from-amber-600 hover:to-yellow-700 text-slate-950 font-black rounded-2xl text-sm flex items-center justify-center space-x-2 shadow-lg transition-transform active:scale-95 disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${isFlipping ? 'animate-spin' : ''}`} />
          <span>{isFlipping ? 'Flipping...' : 'Flip Coin Now'}</span>
        </button>
      </div>
    </div>
  );
};
