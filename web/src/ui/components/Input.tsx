import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input: React.FC<InputProps> = ({ label, error, className = '', ...props }) => {
  return (
    <div className="space-y-1.5 w-full">
      {label && (
        <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase tracking-wider">
          {label}
        </label>
      )}
      <input
        className={`w-full px-4 py-3 rounded-2xl border bg-slate-50 dark:bg-slate-800/80 text-slate-900 dark:text-white font-semibold text-sm outline-none transition-all focus:ring-2 focus:ring-emerald-500 ${
          error ? 'border-red-500' : 'border-slate-200 dark:border-slate-700'
        } ${className}`}
        {...props}
      />
      {error && <p className="text-xs text-red-500 font-semibold">{error}</p>}
    </div>
  );
};
