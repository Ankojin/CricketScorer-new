import { Tournament, Match, Team, Player, GullyRules, createDefaultGullyRules } from '../domain/models';

const STORAGE_KEYS = {
  TOURNAMENTS: 'cricscore_v2_tournaments',
  ACTIVE_MATCH_ID: 'cricscore_v2_active_match_id',
  GLOBAL_PLAYERS: 'cricscore_v2_global_players',
  GULLY_RULES: 'cricscore_v2_gully_rules',
  IS_DARK_MODE: 'cricscore_v2_dark_mode'
};

const PROFILE_SCOPE_KEY = 'cricleague_active_profile';
const LEGACY_KEYS = Object.values(STORAGE_KEYS);

class InMemoryStorage {
  private store = new Map<string, string>();

  getItem(key: string): string | null {
    return this.store.get(key) || null;
  }

  setItem(key: string, value: string): void {
    this.store.set(key, value);
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  clear(): void {
    this.store.clear();
  }
}

const inMemoryFallback = new InMemoryStorage();

function getStorage(): Storage | InMemoryStorage {
  if (typeof window !== 'undefined' && window.localStorage) {
    return window.localStorage;
  }
  if (typeof localStorage !== 'undefined') {
    return localStorage;
  }
  return inMemoryFallback;
}

export class StorageAdapter {
  public static setProfileScope(profileId: string | null): void {
    getStorage().setItem(PROFILE_SCOPE_KEY, profileId ? `account:${profileId}` : 'guest');
  }

  public static getProfileScope(): string {
    return getStorage().getItem(PROFILE_SCOPE_KEY) || 'guest';
  }

  public static hasLegacyUnscopedData(): boolean {
    return LEGACY_KEYS.some(key => getStorage().getItem(key) !== null);
  }

  public static migrateLegacyDataToCurrentScope(): void {
    const storage = getStorage();
    LEGACY_KEYS.forEach(key => {
      const legacy = storage.getItem(key);
      const scoped = this.scopedKey(key);
      if (legacy !== null && storage.getItem(scoped) === null) storage.setItem(scoped, legacy);
    });
  }

  public static clear(): void {
    getStorage().clear();
  }

  public static getItem(key: string): string | null {
    return getStorage().getItem(key);
  }

  public static setItem(key: string, value: string): void {
    getStorage().setItem(key, value);
  }

  public static removeItem(key: string): void {
    getStorage().removeItem(key);
  }

  public static getTournaments(): Tournament[] {
    try {
      const raw = getStorage().getItem(this.scopedKey(STORAGE_KEYS.TOURNAMENTS));
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      console.error('Failed to load tournaments from storage', e);
      return [];
    }
  }

  public static saveTournaments(tournaments: Tournament[]): void {
    try {
      getStorage().setItem(this.scopedKey(STORAGE_KEYS.TOURNAMENTS), JSON.stringify(tournaments));
    } catch (e) {
      console.error('Failed to save tournaments to storage', e);
    }
  }

  public static getActiveMatchId(): string | null {
    return getStorage().getItem(this.scopedKey(STORAGE_KEYS.ACTIVE_MATCH_ID));
  }

  public static setActiveMatchId(id: string | null): void {
    if (id) {
      getStorage().setItem(this.scopedKey(STORAGE_KEYS.ACTIVE_MATCH_ID), id);
    } else {
      getStorage().removeItem(this.scopedKey(STORAGE_KEYS.ACTIVE_MATCH_ID));
    }
  }

  public static getGlobalPlayers(): Player[] {
    try {
      const raw = getStorage().getItem(this.scopedKey(STORAGE_KEYS.GLOBAL_PLAYERS));
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  public static saveGlobalPlayers(players: Player[]): void {
    try {
      getStorage().setItem(this.scopedKey(STORAGE_KEYS.GLOBAL_PLAYERS), JSON.stringify(players));
    } catch (e) {
      console.error('Failed to save global players', e);
    }
  }

  public static getGullyRules(): GullyRules {
    try {
      const raw = getStorage().getItem(this.scopedKey(STORAGE_KEYS.GULLY_RULES));
      return raw ? JSON.parse(raw) : createDefaultGullyRules();
    } catch (e) {
      return createDefaultGullyRules();
    }
  }

  public static saveGullyRules(rules: GullyRules): void {
    try {
      getStorage().setItem(this.scopedKey(STORAGE_KEYS.GULLY_RULES), JSON.stringify(rules));
    } catch (e) {
      console.error('Failed to save gully rules', e);
    }
  }

  public static getDarkMode(): boolean | null {
    const raw = getStorage().getItem(this.scopedKey(STORAGE_KEYS.IS_DARK_MODE));
    if (raw === null) return null;
    return raw === 'true';
  }

  public static saveDarkMode(isDark: boolean | null): void {
    if (isDark === null) {
      getStorage().removeItem(this.scopedKey(STORAGE_KEYS.IS_DARK_MODE));
    } else {
      getStorage().setItem(this.scopedKey(STORAGE_KEYS.IS_DARK_MODE), isDark ? 'true' : 'false');
    }
  }

  private static scopedKey(key: string): string {
    return `cricleague:${this.getProfileScope()}:${key}`;
  }
}
