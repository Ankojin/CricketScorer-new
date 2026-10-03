import React, { useState } from 'react';
import { X, Cloud, Lock, UserPlus, Mail, Key, ShieldAlert } from 'lucide-react';
import { useAuth } from '../../state/AuthContext';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  messagePrompt?: string | null;
}

export const AuthModal: React.FC<AuthModalProps> = ({ isOpen, onClose, messagePrompt }) => {
  const { user, login, register, logout } = useAuth();
  const [mode, setMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      if (mode === 'LOGIN') {
        if (!email.trim() || !password) {
          setError('Email and password are required.');
          setIsSubmitting(false);
          return;
        }
        await login(email, password);
      } else {
        if (!name.trim() || !email.trim() || !password) {
          setError('Name, email, and password are required.');
          setIsSubmitting(false);
          return;
        }
        await register(name, email, password);
      }
      setIsSubmitting(false);
      onClose();
    } catch (err: any) {
      setError(err.message || 'Authentication failed. Please check your credentials.');
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 animate-fadeIn">
      <div className="bg-slate-900 border border-slate-800 text-white rounded-3xl p-6 sm:p-8 max-w-md w-full space-y-5 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2 text-xs font-black uppercase tracking-wider text-emerald-400">
            <Cloud className="w-4 h-4" />
            <span>{user && !user.isGuest ? 'Cloud Account' : 'Cloud Sign-In'}</span>
          </div>
          <button onClick={onClose} className="p-1 text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Message Prompt Banner */}
        {messagePrompt && (
          <div className="p-3.5 bg-amber-500/20 border border-amber-500/40 rounded-2xl text-amber-200 text-xs font-bold flex items-center space-x-2.5">
            <ShieldAlert className="w-5 h-5 text-amber-400 shrink-0" />
            <span>{messagePrompt}</span>
          </div>
        )}

        {/* Error Banner */}
        {error && (
          <div className="p-3.5 bg-red-500/20 border border-red-500/40 rounded-2xl text-red-200 text-xs font-bold">
            {error}
          </div>
        )}

        {/* Signed In State */}
        {user && !user.isGuest ? (
          <div className="space-y-4 py-2">
            <div className="p-4 rounded-2xl bg-slate-800 border border-slate-700 space-y-1">
              <div className="text-xs text-slate-400 font-bold uppercase">Active User Account</div>
              <div className="text-lg font-black text-white">{user.name}</div>
              <div className="text-xs font-mono text-emerald-400">{user.email}</div>
            </div>

            <button
              onClick={async () => {
                await logout();
                onClose();
              }}
              className="w-full py-3.5 bg-rose-600 hover:bg-rose-700 text-white font-black rounded-2xl text-xs uppercase tracking-wider shadow-lg"
            >
              Sign Out
            </button>
          </div>
        ) : (
          /* Sign-In / Register Form */
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="flex bg-slate-800 p-1 rounded-2xl">
              <button
                type="button"
                onClick={() => { setMode('LOGIN'); setError(null); }}
                className={`flex-1 py-2 rounded-xl text-xs font-black transition-all ${
                  mode === 'LOGIN' ? 'bg-emerald-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => { setMode('REGISTER'); setError(null); }}
                className={`flex-1 py-2 rounded-xl text-xs font-black transition-all ${
                  mode === 'REGISTER' ? 'bg-emerald-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                Create Account
              </button>
            </div>

            {mode === 'REGISTER' && (
              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase mb-1">Full Name</label>
                <input
                  type="text"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder="e.g. Rohit Sharma"
                  className="w-full px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-sm font-bold text-white outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            )}

            <div>
              <label className="block text-[11px] font-bold text-slate-400 uppercase mb-1">Email Address</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="scorer@cricscore.in"
                className="w-full px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-sm font-bold text-white outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label className="block text-[11px] font-bold text-slate-400 uppercase mb-1">Password</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-sm font-bold text-white outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3.5 bg-emerald-600 hover:bg-emerald-500 text-white font-black rounded-2xl text-xs uppercase tracking-wider shadow-lg transition-transform active:scale-98 disabled:opacity-50"
            >
              {isSubmitting ? 'Processing...' : (mode === 'LOGIN' ? 'Sign In Now' : 'Create Free Account')}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
