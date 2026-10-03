import React, { useState } from 'react';
import { Shield, Mail, CheckCircle2, ArrowLeft, Smartphone, Globe } from 'lucide-react';

interface PrivacyScreenProps {
  onBack?: () => void;
}

export const PrivacyScreen: React.FC<PrivacyScreenProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<'WEB' | 'ANDROID'>('WEB');

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-16">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-4">
        {onBack && (
          <button
            onClick={onBack}
            className="flex items-center space-x-1.5 text-xs font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back</span>
          </button>
        )}
        <div className="text-right">
          <span className="text-[10px] font-extrabold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">CricLeague Policies</span>
          <h1 className="text-lg font-extrabold text-slate-900 dark:text-white">Privacy Policy</h1>
        </div>
      </div>

      {/* Platform Selector Tabs */}
      <div className="flex bg-slate-100 dark:bg-slate-800 p-1.5 rounded-2xl border border-slate-200 dark:border-slate-700">
        <button
          onClick={() => setActiveTab('WEB')}
          className={`flex-1 py-3 rounded-xl font-extrabold text-xs sm:text-sm flex items-center justify-center space-x-2 transition-all ${
            activeTab === 'WEB'
              ? 'bg-emerald-600 text-white shadow-md'
              : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
          }`}
        >
          <Globe className="w-4 h-4" />
          <span>Web App Privacy Policy</span>
        </button>

        <button
          onClick={() => setActiveTab('ANDROID')}
          className={`flex-1 py-3 rounded-xl font-extrabold text-xs sm:text-sm flex items-center justify-center space-x-2 transition-all ${
            activeTab === 'ANDROID'
              ? 'bg-emerald-600 text-white shadow-md'
              : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
          }`}
        >
          <Smartphone className="w-4 h-4" />
          <span>Android App Privacy Policy</span>
        </button>
      </div>

      {/* Main Privacy Banner */}
      <div className="bg-gradient-to-r from-emerald-600 via-teal-700 to-slate-900 text-white rounded-3xl p-6 sm:p-8 shadow-xl space-y-3">
        <div className="flex items-center space-x-2 text-emerald-200 text-xs font-bold uppercase tracking-wider">
          <Shield className="w-4 h-4" />
          <span>Privacy-First Commitment</span>
        </div>
        <h2 className="text-2xl sm:text-3xl font-black">
          {activeTab === 'WEB' ? 'Web App Privacy Policy' : 'Android Mobile App Privacy Policy'}
        </h2>
        <p className="text-emerald-100/90 text-sm font-medium">
          {activeTab === 'WEB'
            ? 'Effective date: September 30, 2026 • Policy Type: Web App (cricleague.nrkmart.in)'
            : 'Effective date: January 1, 2025 • Policy Type: Android App (in.nrkmart.cricscore)'}
        </p>
      </div>

      {/* WEB PRIVACY POLICY CONTENT */}
      {activeTab === 'WEB' && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 sm:p-8 shadow-sm space-y-6 text-slate-800 dark:text-slate-200">
          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>1. What We Collect</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6">
              We do not collect personal data through the CricLeague Web App. We do not run advertising trackers or third-party analytics scripts for user profiling.
            </p>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>2. How Data Is Used in the Web App</span>
            </h3>
            <ul className="list-disc pl-10 text-xs sm:text-sm text-slate-600 dark:text-slate-300 space-y-1.5">
              <li>Match progress, score state, and temporary session details are stored locally in your browser for scoring functionality.</li>
              <li>This data is used only to provide scoring features and improve your in-app experience.</li>
              <li>We do not sell or rent any user data.</li>
            </ul>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>3. Third-Party Access</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6">
              We do not share personal data with third-party advertisers, data brokers, or external tracking services.
            </p>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>4. Children’s Privacy</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6">
              The CricLeague Web App is intended for cricket scoring use and does not knowingly collect personal data from children.
            </p>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>5. Contact & Support</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6 flex items-center space-x-1.5">
              <span>Contact us at</span>
              <a href="mailto:support@nrkmart.in" className="text-emerald-600 font-bold hover:underline inline-flex items-center space-x-1">
                <Mail className="w-3.5 h-3.5" />
                <span>support@nrkmart.in</span>
              </a>
            </p>
          </div>
        </div>
      )}

      {/* ANDROID APP PRIVACY POLICY CONTENT */}
      {activeTab === 'ANDROID' && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl p-6 sm:p-8 shadow-sm space-y-6 text-slate-800 dark:text-slate-200">
          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>1. Information Collection & Usage</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6">
              Cric League is a <strong>privacy-first, offline-capable application</strong>. We do NOT collect, store, transmit, or sell any personal data to external servers or third-party advertising networks. All match data, player stats, rosters, and scoring histories remain strictly on your local device storage using Android Room Database.
            </p>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>2. Permissions & Local Nearby Sync</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6">
              Cric League offers an optional local score sharing feature using Google Play Services Nearby Connections API:
            </p>
            <ul className="list-disc pl-10 text-xs sm:text-sm text-slate-600 dark:text-slate-300 space-y-1.5">
              <li><strong>Bluetooth & Nearby Wi-Fi Permissions</strong> (<code className="bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">BLUETOOTH_SCAN</code>, <code className="bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">BLUETOOTH_CONNECT</code>, <code className="bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">NEARBY_WIFI_DEVICES</code>): Used exclusively to discover and connect to nearby scoring devices locally.</li>
              <li><strong>Location Permission</strong> (<code className="bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">ACCESS_FINE_LOCATION</code>): Required by the Android operating system framework to execute Bluetooth and Wi-Fi Direct scanning. Location data is never recorded, tracked, or shared.</li>
            </ul>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>3. Children's Privacy</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6">
              Cric League does not address anyone under the age of 13 and does not knowingly collect personally identifiable information from children.
            </p>
          </div>

          <div className="space-y-2">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>4. Contact Us</span>
            </h3>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed pl-6 flex items-center space-x-1.5">
              <span>Contact us at</span>
              <a href="mailto:support@nrkmart.in" className="text-emerald-600 font-bold hover:underline inline-flex items-center space-x-1">
                <Mail className="w-3.5 h-3.5" />
                <span>support@nrkmart.in</span>
              </a>
            </p>
          </div>
        </div>
      )}
    </div>
  );
};
