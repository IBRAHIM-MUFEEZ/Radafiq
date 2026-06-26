import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { onAuthStateChanged, signInWithPopup, signInWithRedirect, getRedirectResult, signOut as firebaseSignOut, User } from 'firebase/auth';
import { auth, googleProvider } from '../firebase';
import {
  CardSummary,
  CustomerSummary,
  SavingsEntry,
  SettlementHistoryEntry,
  UserProfile,
  AppSettings,
  AppSecurityState,
  FirestoreBackupPayload,
  AccountKind,
  SplitEntry,
  defaultSelectedAccountIds,
} from '../types/models';
import * as repo from '../services/firebaseRepository';
import { currentTimestampLabel, todayString } from '../utils/format';
import { backupFromJson, backupToJson, downloadJsonFile, readJsonFile } from '../utils/backup';
import {
  loadSecurityStorage,
  saveSecurityStorage,
  clearSecurityStorage,
  clearSecurityStorageForOtherUser,
  hashPasscode,
  hashRecoveryAnswer,
  generateSalt,
  isPasscodeLockedOut,
  recordPasscodeFailure,
  clearPasscodeFailures,
  getPasskeyCredentialId,
  savePasskeyStorage,
  clearPasskeyStorage,
} from '../utils/security';
import {
  registerPasskey as webAuthnRegister,
  authenticatePasskey as webAuthnAuthenticate,
  isPlatformAuthenticatorAvailable,
} from '../utils/passkey';

// ── Settings storage ──────────────────────────────────────────────────────────

const SETTINGS_KEY = 'radafiq_settings';

function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return { themeMode: 'DARK', selectedAccountIds: defaultSelectedAccountIds(), knownAccountIds: defaultSelectedAccountIds(), lastDriveBackupTime: null, lastDriveRestoreTime: null };
    const parsed = JSON.parse(raw);
    const saved = new Set<string>(parsed.selectedAccountIds ?? []);
    const savedKnown = new Set<string>(parsed.knownAccountIds ?? []);
    // Use savedKnown as baseline; for backward compat (no knownAccountIds), fall back to saved selections
    const baselineKnown = savedKnown.size > 0 ? savedKnown : new Set(saved);
    // Only add accounts that are genuinely new (didn't exist when user last saved settings)
    const allDefaults = defaultSelectedAccountIds();
    const genuinelyNew = new Set([...allDefaults].filter(id => !baselineKnown.has(id)));
    const merged = new Set([...saved, ...genuinelyNew]);
    // Update knownAccountIds to current full set so future merges are correct
    return {
      themeMode: parsed.themeMode ?? 'DARK',
      selectedAccountIds: merged,
      knownAccountIds: allDefaults,
      lastDriveBackupTime: parsed.lastDriveBackupTime ?? null,
      lastDriveRestoreTime: parsed.lastDriveRestoreTime ?? null,
    };
  } catch {
    return { themeMode: 'DARK', selectedAccountIds: defaultSelectedAccountIds(), knownAccountIds: defaultSelectedAccountIds(), lastDriveBackupTime: null, lastDriveRestoreTime: null };
  }
}

function saveSettings(settings: AppSettings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify({
    themeMode: settings.themeMode,
    selectedAccountIds: Array.from(settings.selectedAccountIds),
    knownAccountIds: Array.from(settings.knownAccountIds),
    lastDriveBackupTime: settings.lastDriveBackupTime,
    lastDriveRestoreTime: settings.lastDriveRestoreTime,
  }));
}

// ── Security helpers ──────────────────────────────────────────────────────────

function loadSecurityState(): AppSecurityState {
  const s = loadSecurityStorage();
  const hasPasscode = s.passcodeHash !== '';
  const hasRecoveryQuestion = s.recoveryQuestion !== '' && s.recoveryAnswerHash !== '';
  return {
    lockEnabled: s.lockEnabled && hasPasscode,
    hasPasscode,
    recoveryQuestion: s.recoveryQuestion,
    hasRecoveryQuestion,
    isUnlocked: !(s.lockEnabled && hasPasscode),
  };
}

// ── Context types ─────────────────────────────────────────────────────────────

interface AppContextValue {
  // Auth
  user: User | null;
  authLoading: boolean;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;

  // Profile
  profile: UserProfile | null;
  profileLoading: boolean;
  saveProfile: (displayName: string, businessName: string, email: string, photoUrl?: string) => Promise<void>;

  // Data
  cards: CardSummary[];
  customers: CustomerSummary[];
  deletedCustomers: CustomerSummary[];
  dataLoading: boolean;

  // Settings
  settings: AppSettings;
  setThemeMode: (mode: 'LIGHT' | 'DARK') => void;
  setAccountSelected: (id: string, selected: boolean) => void;

  // Security
  security: AppSecurityState;
  setPasscode: (passcode: string, recoveryQuestion: string, recoveryAnswer: string) => Promise<void>;
  updatePasscode: (current: string, newPasscode: string, recoveryQuestion: string, recoveryAnswer: string) => Promise<boolean>;
  clearPasscode: () => void;
  setLockEnabled: (enabled: boolean) => void;
  verifyPasscode: (passcode: string) => Promise<boolean>;
  resetPasscodeWithRecovery: (recoveryAnswer: string, newPasscode: string) => Promise<boolean>;
  unlock: () => void;
  lock: () => void;

  // Passkey (WebAuthn)
  hasPasskey: boolean;
  registerPasskey: () => Promise<void>;
  authenticateWithPasskey: () => Promise<boolean>;
  removePasskey: () => void;

  // Customer operations
  addCustomer: (name: string) => Promise<string>;
  deleteCustomer: (id: string, name: string) => Promise<void>;
  restoreCustomer: (id: string) => Promise<void>;
  permanentlyDeleteCustomer: (id: string, name: string) => Promise<void>;
  updateCustomerDueAmount: (id: string, name: string, amount: string) => Promise<void>;

  // Transaction operations
  addTransaction: (params: {
    customerId: string;
    transactionName: string;
    customerName: string;
    accountId: string;
    accountName: string;
    accountKind: AccountKind;
    amount: string;
    transactionDate: string;
    personName?: string;
  }) => Promise<void>;
  addEmiTransactions: (params: {
    customerId: string;
    transactionName: string;
    customerName: string;
    accountId: string;
    accountName: string;
    accountKind?: AccountKind;
    totalAmount: number;
    transactionDate: string;
    months: number;
    firstMonthOverride?: number;
    dateOverrides?: Record<number, string>;
  }) => Promise<void>;
  addSplitTransactions: (params: {
    customerId: string;
    customerName: string;
    transactionName: string;
    transactionDate: string;
    splits: SplitEntry[];
  }) => Promise<void>;
  convertEmiInstallmentToSplit: (params: {
    originalTransactionId: string;
    customerId: string;
    customerName: string;
    transactionName: string;
    transactionDate: string;
    emiGroupId: string;
    emiIndex: number;
    emiTotal: number;
    splits: SplitEntry[];
  }) => Promise<void>;
  updateTransaction: (params: {
    transactionId: string;
    transactionName: string;
    accountId: string;
    accountName: string;
    accountKind: AccountKind;
    amount: string;
    transactionDate: string;
    personName?: string;
  }) => Promise<void>;
  deleteTransaction: (id: string) => Promise<void>;
  addPartialPayment: (transactionId: string, amount: string) => Promise<void>;
  toggleTransactionSettled: (transactionId: string, isSettled: boolean) => Promise<void>;

  // Account operations
  updateCreditCardDue: (params: {
    accountId: string;
    accountName: string;
    amount: string;
    dueDate: string;
    remindersEnabled: boolean;
    reminderEmail: string;
    reminderWhatsApp: string;
  }) => Promise<void>;

  // Payment operations
  addPayment: (accountId: string, accountName: string, accountKind: AccountKind, amount: string) => Promise<void>;

  // Savings operations
  addSavingsDeposit: (customerId: string, customerName: string, amount: string, note: string, bankAccountId?: string, bankAccountName?: string, date?: string) => Promise<void>;
  addSavingsWithdrawal: (customerId: string, customerName: string, amount: string, note: string, bankAccountId?: string, bankAccountName?: string, date?: string) => Promise<void>;
  deleteSavingsEntry: (entryId: string) => Promise<void>;

  // Settlement History
  settlementHistory: SettlementHistoryEntry[];
  settlementHistoryLoading: boolean;
  loadSettlementHistory: (transactionId: string) => Promise<void>;

  // Backup / Restore
  exportBackupToFile: () => Promise<void>;
  importBackupFromFile: (file: File) => Promise<void>;
  backupStatusMessage: string;
  backupInProgress: boolean;

  // Sync status
  syncStatus: { state: 'IDLE' | 'SYNCING' | 'SUCCESS' | 'ERROR'; message: string };
  triggerSync: () => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}

// ── Provider ──────────────────────────────────────────────────────────────────

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [cards, setCards] = useState<CardSummary[]>([]);
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [deletedCustomers, setDeletedCustomers] = useState<CustomerSummary[]>([]);
  const [dataLoading, setDataLoading] = useState(true);
  const [settings, setSettingsState] = useState<AppSettings>(loadSettings);
  const [security, setSecurityState] = useState<AppSecurityState>(loadSecurityState);
  const [hasPasskey, setHasPasskey] = useState(false);
  const [backupStatusMessage, setBackupStatusMessage] = useState('');
  const [backupInProgress, setBackupInProgress] = useState(false);
  const [syncStatus, setSyncStatus] = useState<{ state: 'IDLE' | 'SYNCING' | 'SUCCESS' | 'ERROR'; message: string }>({ state: 'IDLE', message: '' });
  const [settlementHistory, setSettlementHistory] = useState<SettlementHistoryEntry[]>([]);
  const [settlementHistoryLoading, setSettlementHistoryLoading] = useState(false);

  const unsubscribeDataRef = useRef<(() => void)[]>([]);
  const unsubscribeProfileRef = useRef<(() => void) | null>(null);
  const syncResetTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── Auth listener ─────────────────────────────────────────────────────────

  useEffect(() => {
    // Handle redirect result on page load (from signInWithRedirect)
    getRedirectResult(auth).then((result) => {
      if (result?.user) {
        // User signed in via redirect — auth state listener will handle the rest
      }
    }).catch((e: unknown) => {
      const err = e as { code?: string };
      if (err.code && err.code !== 'auth/no-auth-event') {
        console.error('Redirect sign-in error:', err.code);
      }
    });

    const unsub = onAuthStateChanged(auth, async (firebaseUser) => {
      setUser(firebaseUser);
      setAuthLoading(false);

      if (firebaseUser) {
        clearSecurityStorageForOtherUser(firebaseUser.uid);

        // Sync security between cloud and localStorage
        try {
          const cloudData = await repo.loadSecurityFromCloud(firebaseUser.uid);
          const localData = loadSecurityStorage();
          if (cloudData && cloudData.passcodeHash) {
            // Cloud has passcode — sync to local if local is empty or different user
            if (!localData.passcodeHash || localData.ownerUid !== firebaseUser.uid) {
              saveSecurityStorage({ ...cloudData, ownerUid: firebaseUser.uid });
            }
          } else if (localData.passcodeHash && localData.ownerUid === firebaseUser.uid) {
            // Local has passcode but cloud doesn't — migrate to cloud
            await repo.saveSecurityToCloud(firebaseUser.uid, {
              passcodeHash: localData.passcodeHash,
              passcodeSalt: localData.passcodeSalt,
              lockEnabled: localData.lockEnabled,
              recoveryQuestion: localData.recoveryQuestion,
              recoveryAnswerHash: localData.recoveryAnswerHash,
            });
          }
        } catch (e) {
          console.error('Security cloud sync error:', e);
        }

        refreshSecurity();
        // Refresh passkey state for this user
        setHasPasskey(!!getPasskeyCredentialId(firebaseUser.uid));

        // Start profile listener
        setProfileLoading(true);
        unsubscribeProfileRef.current?.();
        unsubscribeProfileRef.current = repo.listenProfile(firebaseUser.uid, (p) => {
          setProfile(p ? { ...p } : null);
          setProfileLoading(false);
        });

        // Start data listeners
        setDataLoading(true);
        unsubscribeDataRef.current.forEach(u => u());
        let firstSnapshot = true;
        const unsubs = repo.listenAllData(firebaseUser.uid, (data) => {
          setCards(data.accounts);
          setCustomers(data.customers);
          setDeletedCustomers(data.deletedCustomers);
          if (firstSnapshot) {
            firstSnapshot = false;
            setDataLoading(false);
          } else {
            // Live update received — briefly show synced
            setSyncStatus({ state: 'SUCCESS', message: 'Synced.' });
            if (syncResetTimerRef.current) clearTimeout(syncResetTimerRef.current);
            syncResetTimerRef.current = setTimeout(() => {
              setSyncStatus({ state: 'IDLE', message: '' });
            }, 2000);
          }
        });
        unsubscribeDataRef.current = unsubs;
      } else {
        // Signed out
        unsubscribeProfileRef.current?.();
        unsubscribeDataRef.current.forEach(u => u());
        unsubscribeDataRef.current = [];
        setProfile(null);
        setProfileLoading(false);
        setCards([]);
        setCustomers([]);
        setDeletedCustomers([]);
        setDataLoading(false);
        clearSecurityStorage();
        setHasPasskey(false);
        refreshSecurity();
      }
    });
    return () => unsub();
  }, []);

  // ── Settings helpers ──────────────────────────────────────────────────────

  const updateSettings = useCallback((updater: (prev: AppSettings) => AppSettings) => {
    setSettingsState(prev => {
      const next = updater(prev);
      saveSettings(next);
      return next;
    });
  }, []);

  const setThemeMode = useCallback((mode: 'LIGHT' | 'DARK') => {
    updateSettings(s => ({ ...s, themeMode: mode }));
  }, [updateSettings]);

  const setAccountSelected = useCallback((id: string, selected: boolean) => {
    updateSettings(s => {
      const ids = new Set(s.selectedAccountIds);
      if (selected) ids.add(id);
      else if (ids.size > 1) ids.delete(id);
      return { ...s, selectedAccountIds: ids };
    });
  }, [updateSettings]);

  // ── Security helpers ──────────────────────────────────────────────────────

  const refreshSecurity = useCallback(() => {
    setSecurityState(loadSecurityState());
  }, []);

  const setPasscode = useCallback(async (passcode: string, recoveryQuestion: string, recoveryAnswer: string) => {
    const salt = generateSalt();
    const hash = await hashPasscode(passcode.trim(), salt);
    const answerHash = await hashRecoveryAnswer(recoveryAnswer, salt);
    const secData = {
      passcodeHash: hash,
      passcodeSalt: salt,
      lockEnabled: true,
      recoveryQuestion: recoveryQuestion.trim(),
      recoveryAnswerHash: answerHash,
      ownerUid: user?.uid ?? '',
      failedAttempts: 0,
      lockoutUntil: 0,
    };
    saveSecurityStorage(secData);
    refreshSecurity();
    if (user?.uid) {
      repo.saveSecurityToCloud(user.uid, {
        passcodeHash: secData.passcodeHash,
        passcodeSalt: secData.passcodeSalt,
        lockEnabled: secData.lockEnabled,
        recoveryQuestion: secData.recoveryQuestion,
        recoveryAnswerHash: secData.recoveryAnswerHash,
      }).catch(console.error);
    }
  }, [refreshSecurity, user]);

  const updatePasscode = useCallback(async (current: string, newPasscode: string, recoveryQuestion: string, recoveryAnswer: string): Promise<boolean> => {
    const s = loadSecurityStorage();
    const currentHash = await hashPasscode(current.trim(), s.passcodeSalt);
    if (currentHash !== s.passcodeHash) return false;
    const newHash = await hashPasscode(newPasscode.trim(), s.passcodeSalt);
    const answerHash = await hashRecoveryAnswer(recoveryAnswer, s.passcodeSalt);
    const secData = {
      passcodeHash: newHash,
      passcodeSalt: s.passcodeSalt,
      lockEnabled: s.lockEnabled,
      recoveryQuestion: recoveryQuestion.trim(),
      recoveryAnswerHash: answerHash,
      ownerUid: user?.uid ?? '',
      failedAttempts: 0,
      lockoutUntil: 0,
    };
    saveSecurityStorage(secData);
    refreshSecurity();
    if (user?.uid) {
      repo.saveSecurityToCloud(user.uid, {
        passcodeHash: secData.passcodeHash,
        passcodeSalt: secData.passcodeSalt,
        lockEnabled: secData.lockEnabled,
        recoveryQuestion: secData.recoveryQuestion,
        recoveryAnswerHash: secData.recoveryAnswerHash,
      }).catch(console.error);
    }
    return true;
  }, [refreshSecurity, user]);

  const clearPasscode = useCallback(() => {
    clearSecurityStorage();
    refreshSecurity();
    if (user?.uid) {
      repo.clearSecurityFromCloud(user.uid).catch(console.error);
    }
  }, [refreshSecurity, user]);

  const setLockEnabled = useCallback((enabled: boolean) => {
    saveSecurityStorage({ lockEnabled: enabled });
    refreshSecurity();
    if (user?.uid) {
      const s = loadSecurityStorage();
      repo.saveSecurityToCloud(user.uid, {
        passcodeHash: s.passcodeHash,
        passcodeSalt: s.passcodeSalt,
        lockEnabled: enabled,
        recoveryQuestion: s.recoveryQuestion,
        recoveryAnswerHash: s.recoveryAnswerHash,
      }).catch(console.error);
    }
  }, [refreshSecurity, user]);

  const verifyPasscode = useCallback(async (passcode: string): Promise<boolean> => {
    if (isPasscodeLockedOut()) return false;
    const s = loadSecurityStorage();
    const hash = await hashPasscode(passcode.trim(), s.passcodeSalt);
    const matches = hash === s.passcodeHash;
    if (matches) {
      clearPasscodeFailures();
      setSecurityState(prev => ({ ...prev, isUnlocked: true }));
    } else {
      recordPasscodeFailure();
    }
    return matches;
  }, []);

  const resetPasscodeWithRecovery = useCallback(async (recoveryAnswer: string, newPasscode: string): Promise<boolean> => {
    const s = loadSecurityStorage();
    const answerHash = await hashRecoveryAnswer(recoveryAnswer, s.passcodeSalt);
    if (answerHash !== s.recoveryAnswerHash) return false;
    const newHash = await hashPasscode(newPasscode.trim(), s.passcodeSalt);
    saveSecurityStorage({ passcodeHash: newHash, lockEnabled: true, failedAttempts: 0, lockoutUntil: 0 });
    setSecurityState(prev => ({ ...prev, isUnlocked: true }));
    if (user?.uid) {
      const full = loadSecurityStorage();
      repo.saveSecurityToCloud(user.uid, {
        passcodeHash: full.passcodeHash,
        passcodeSalt: full.passcodeSalt,
        lockEnabled: full.lockEnabled,
        recoveryQuestion: full.recoveryQuestion,
        recoveryAnswerHash: full.recoveryAnswerHash,
      }).catch(console.error);
    }
    return true;
  }, [user]);

  const unlock = useCallback(() => {
    setSecurityState(prev => ({ ...prev, isUnlocked: true }));
  }, []);

  const lock = useCallback(() => {
    setSecurityState(prev => {
      if (prev.lockEnabled && prev.hasPasscode) return { ...prev, isUnlocked: false };
      return prev;
    });
  }, []);

  // ── Passkey (WebAuthn) helpers ────────────────────────────────────────────

  const registerPasskey = useCallback(async () => {
    if (!user) throw new Error('Must be signed in to register a passkey.');
    const displayName = profile?.displayName || user.email || user.uid;
    const { credentialId } = await webAuthnRegister(user.uid, displayName);
    savePasskeyStorage(credentialId, user.uid);
    setHasPasskey(true);
  }, [user, profile]);

  const authenticateWithPasskey = useCallback(async (): Promise<boolean> => {
    if (!user) return false;
    const credentialId = getPasskeyCredentialId(user.uid);
    if (!credentialId) return false;
    try {
      const ok = await webAuthnAuthenticate(credentialId);
      if (ok) setSecurityState(prev => ({ ...prev, isUnlocked: true }));
      return ok;
    } catch {
      return false;
    }
  }, [user]);

  const removePasskey = useCallback(() => {
    clearPasskeyStorage();
    setHasPasskey(false);
  }, []);

  useEffect(() => {
    // ── Lock-on-idle strategy ─────────────────────────────────────────────
    // We want to lock when the PC is locked/slept/shut down, but NOT when the
    // user simply switches tabs or alt-tabs to another window.
    //
    // Approach:
    //   • On `visibilitychange → hidden`: record the timestamp but do NOT lock yet.
    //   • On `visibilitychange → visible`: if the page was hidden for longer than
    //     LOCK_AFTER_MS (5 minutes), lock. Short absences (tab switch, alt-tab)
    //     are ignored.
    //   • On `pagehide` with persisted=false: the page is being fully unloaded
    //     (close tab, navigate away, PC shutdown). Lock immediately so the next
    //     load starts locked.
    //   • `blur` is removed — it fires on every alt-tab and is too aggressive.
    //
    // PC lock/sleep causes the browser to hide the page for a long time, so
    // when the user unlocks the PC and returns to the tab, LOCK_AFTER_MS will
    // have elapsed and the app locks correctly.

    const LOCK_AFTER_MS = 5 * 60 * 1000; // 5 minutes
    const HIDDEN_TS_KEY = 'radafiq_hidden_at';

    const lockIfProtected = () => {
      setSecurityState(prev => {
        if (prev.lockEnabled && prev.hasPasscode) return { ...prev, isUnlocked: false };
        return prev;
      });
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        // Record when the page was hidden
        sessionStorage.setItem(HIDDEN_TS_KEY, String(Date.now()));
      } else {
        // Page became visible again — check how long it was hidden
        const hiddenAt = parseInt(sessionStorage.getItem(HIDDEN_TS_KEY) ?? '0', 10);
        sessionStorage.removeItem(HIDDEN_TS_KEY);
        if (hiddenAt > 0 && Date.now() - hiddenAt >= LOCK_AFTER_MS) {
          lockIfProtected();
        }
      }
    };

    const handlePageHide = (e: PageTransitionEvent) => {
      // persisted=false means the page is being fully unloaded (not bfcache)
      if (!e.persisted) {
        lockIfProtected();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('pagehide', handlePageHide);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('pagehide', handlePageHide);
    };
  }, []);

  // ── Auth operations ───────────────────────────────────────────────────────

  const signInWithGoogle = useCallback(async () => {
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (e: unknown) {
      const err = e as { code?: string; message?: string };
      // If popup blocked, fall back to redirect
      if (err.code === 'auth/popup-blocked' || err.code === 'auth/popup-closed-by-user') {
        await signInWithRedirect(auth, googleProvider);
      } else {
        console.error('Sign-in error:', err.code, err.message);
        throw e;
      }
    }
  }, []);

  const signOut = useCallback(async () => {
    await firebaseSignOut(auth);
  }, []);

  // ── Profile ───────────────────────────────────────────────────────────────

  const saveProfileFn = useCallback(async (displayName: string, businessName: string, email: string, photoUrl: string = '') => {
    if (!user) return;
    await repo.saveProfile(user.uid, displayName, businessName, email, photoUrl);
  }, [user]);

  // ── Customer operations ───────────────────────────────────────────────────

  const addCustomer = useCallback(async (name: string): Promise<string> => {
    if (!user) return '';
    return repo.addCustomer(user.uid, name.trim());
  }, [user]);

  const deleteCustomer = useCallback(async (id: string, name: string) => {
    if (!user) return;
    await repo.deleteCustomer(user.uid, id, name);
  }, [user]);

  const restoreCustomer = useCallback(async (id: string) => {
    if (!user) return;
    await repo.restoreCustomer(user.uid, id);
  }, [user]);

  const permanentlyDeleteCustomer = useCallback(async (id: string, name: string) => {
    if (!user) return;
    await repo.permanentlyDeleteCustomer(user.uid, id, name);
  }, [user]);

  const updateCustomerDueAmount = useCallback(async (id: string, name: string, amount: string) => {
    if (!user) return;
    const parsed = parseFloat(amount);
    if (isNaN(parsed)) return;
    await repo.updateCustomerDueAmount(user.uid, id, name, parsed);
  }, [user]);

  // ── Transaction operations ────────────────────────────────────────────────

  const addTransaction = useCallback(async (params: {
    customerId: string;
    transactionName: string;
    customerName: string;
    accountId: string;
    accountName: string;
    accountKind: AccountKind;
    amount: string;
    transactionDate: string;
    personName?: string;
  }) => {
    if (!user) return;
    const amount = parseFloat(params.amount);
    if (isNaN(amount)) return;
    await repo.addTransaction(user.uid, {
      ...params,
      amount,
      transactionDate: params.transactionDate || todayString(),
    });
  }, [user]);

  function addMonths(isoDate: string, months: number): string {
    const [y, m, d] = isoDate.split('-').map(Number);
    const totalM = y * 12 + (m - 1) + months;
    let emiYear = Math.floor(totalM / 12);
    let emiMonth = (totalM % 12) + 1;
    if (emiMonth <= 0) { emiMonth += 12; emiYear -= 1; }
    const lastDay = new Date(emiYear, emiMonth, 0).getDate();
    const emiDay = Math.min(d, lastDay);
    return `${emiYear}-${String(emiMonth).padStart(2, '0')}-${String(emiDay).padStart(2, '0')}`;
  }

  const addEmiTransactions = useCallback(async (params: {
    customerId: string;
    transactionName: string;
    customerName: string;
    accountId: string;
    accountName: string;
    accountKind?: AccountKind;
    totalAmount: number;
    transactionDate: string;
    months: number;
    firstMonthOverride?: number;
    dateOverrides?: Record<number, string>;
  }) => {
    if (!user || params.months <= 0 || params.totalAmount <= 0) return;
    const baseDateIso = params.transactionDate || todayString();
    const baseEmi = params.totalAmount / params.months;
    const firstEmi = params.firstMonthOverride && params.firstMonthOverride > 0 ? params.firstMonthOverride : baseEmi;
    const groupId = crypto.randomUUID();

    const instalments = Array.from({ length: params.months }, (_, i) => {
      const emiAmount = i === 0 ? firstEmi : baseEmi;
      const emiDateIso = params.dateOverrides?.[i] ?? addMonths(baseDateIso, i);
      const dueDateIso = addMonths(emiDateIso, 1);
      return {
        customerId: params.customerId,
        transactionName: `${params.transactionName.trim()} — EMI ${i + 1}/${params.months}`,
        accountId: params.accountId,
        accountName: params.accountName,
        accountType: params.accountKind ?? 'credit_card',
        customerName: params.customerName.trim(),
        amount: emiAmount,
        transactionDate: emiDateIso,
        givenDate: emiDateIso,
        dueDate: dueDateIso,
        emiGroupId: groupId,
        emiIndex: i,
        emiTotal: params.months,
      };
    });

    await repo.addEmiTransactionsBatch(user.uid, instalments);
  }, [user]);

  function slugify(text: string): string {
    return text.replace(/\s+/g, ' ').toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '');
  }

  const addSplitTransactions = useCallback(async (params: {
    customerId: string;
    customerName: string;
    transactionName: string;
    transactionDate: string;
    splits: SplitEntry[];
  }) => {
    if (!user || params.splits.length === 0) return;
    const groupId = crypto.randomUUID();
    const date = params.transactionDate || todayString();

    const docs = params.splits.flatMap(split => {
      const amount = parseFloat(split.amount);
      if (isNaN(amount) || amount <= 0) return [];
      const isPerson = split.accountKind === 'person';
      const rawName = isPerson ? split.personName : (split.accountName || split.accountId || '');
      const normalized = rawName.replace(/\s+/g, ' ').trim();
      const accountId = isPerson
        ? `person_${slugify(normalized)}`
        : split.accountId || slugify(normalized);
      const accountName = isPerson ? split.personName.trim() : split.accountName.trim();
      if (!accountName) return [];

      const doc: Record<string, unknown> = {
        customerId: params.customerId,
        customerName: params.customerName,
        transactionName: params.transactionName,
        accountId,
        accountName,
        accountType: split.accountKind,
        amount,
        transactionDate: date,
        givenDate: date,
        splitGroupId: groupId,
      };
      if (isPerson && split.personName) doc.personName = split.personName.trim();
      return [doc];
    });

    if (docs.length > 0) await repo.addSplitTransactionsBatch(user.uid, docs);
  }, [user]);

  const convertEmiInstallmentToSplit = useCallback(async (params: {
    originalTransactionId: string;
    customerId: string;
    customerName: string;
    transactionName: string;
    transactionDate: string;
    emiGroupId: string;
    emiIndex: number;
    emiTotal: number;
    splits: SplitEntry[];
  }) => {
    if (!user || params.splits.length === 0) return;
    const splitGroupId = crypto.randomUUID();

    const docs = params.splits.flatMap(split => {
      const amount = parseFloat(split.amount);
      if (isNaN(amount) || amount <= 0) return [];
      const isPerson = split.accountKind === 'person';
      const rawName = isPerson ? split.personName : (split.accountName || split.accountId || '');
      const normalized = rawName.replace(/\s+/g, ' ').trim();
      const accountId = isPerson
        ? `person_${slugify(normalized)}`
        : split.accountId || slugify(normalized);
      const accountName = isPerson ? split.personName.trim() : split.accountName.trim();
      if (!accountName) return [];

      const doc: Record<string, unknown> = {
        customerId: params.customerId,
        customerName: params.customerName,
        transactionName: params.transactionName,
        accountId,
        accountName,
        accountType: split.accountKind,
        amount,
        transactionDate: params.transactionDate,
        givenDate: params.transactionDate,
        splitGroupId,
        // Preserve EMI group membership so this installment still belongs to the plan
        emiGroupId: params.emiGroupId,
        emiIndex: params.emiIndex,
        emiTotal: params.emiTotal,
      };
      if (isPerson && split.personName) doc.personName = split.personName.trim();
      return [doc];
    });

    if (docs.length > 0) {
      await repo.convertEmiInstallmentToSplit(user.uid, params.originalTransactionId, docs);
    }
  }, [user]);

  const updateTransaction = useCallback(async (params: {
    transactionId: string;
    transactionName: string;
    accountId: string;
    accountName: string;
    accountKind: AccountKind;
    amount: string;
    transactionDate: string;
    personName?: string;
  }) => {
    if (!user) return;
    const amount = parseFloat(params.amount);
    if (isNaN(amount)) return;
    await repo.updateTransaction(user.uid, params.transactionId, {
      ...params,
      amount,
      transactionDate: params.transactionDate || todayString(),
    });
  }, [user]);

  const deleteTransaction = useCallback(async (id: string) => {
    if (!user) return;
    await repo.deleteTransaction(user.uid, id);
  }, [user]);

  const addPartialPayment = useCallback(async (transactionId: string, amount: string) => {
    if (!user) return;
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed <= 0) return;
    await repo.addPartialPayment(user.uid, transactionId, parsed, todayString());
  }, [user]);

  const toggleTransactionSettled = useCallback(async (transactionId: string, isSettled: boolean) => {
    if (!user) return;
    await repo.toggleTransactionSettled(user.uid, transactionId, isSettled, isSettled ? todayString() : '');
  }, [user]);

  const loadSettlementHistory = useCallback(async (transactionId: string) => {
    if (!user) return;
    setSettlementHistoryLoading(true);
    try {
      const history = await repo.getSettlementHistory(user.uid, transactionId);
      setSettlementHistory(history);
    } catch (e) {
      console.error('Failed to load settlement history:', e);
      setSettlementHistory([]);
    } finally {
      setSettlementHistoryLoading(false);
    }
  }, [user]);

  // ── Account operations ────────────────────────────────────────────────────

  const updateCreditCardDue = useCallback(async (params: {
    accountId: string;
    accountName: string;
    amount: string;
    dueDate: string;
    remindersEnabled: boolean;
    reminderEmail: string;
    reminderWhatsApp: string;
  }) => {
    if (!user) return;
    const amount = parseFloat(params.amount);
    if (isNaN(amount)) return;
    await repo.updateCreditCardDue(
      user.uid,
      params.accountId,
      params.accountName,
      amount,
      params.dueDate,
      params.remindersEnabled,
      params.reminderEmail.trim(),
      params.reminderWhatsApp.trim()
    );
  }, [user]);

  // ── Payment operations ────────────────────────────────────────────────────

  const addPayment = useCallback(async (accountId: string, accountName: string, accountKind: AccountKind, amount: string) => {
    if (!user) return;
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed <= 0) return;
    await repo.addPayment(user.uid, accountId, accountName, accountKind, parsed, todayString());
  }, [user]);

  // ── Savings operations ────────────────────────────────────────────────────

  const addSavingsDeposit = useCallback(async (customerId: string, customerName: string, amount: string, note: string, bankAccountId: string = '', bankAccountName: string = '', date: string = todayString()) => {
    if (!user) return;
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed <= 0) return;
    const optimisticId = `opt_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
    setCustomers(prev => prev.map(c => {
      if (c.id !== customerId) return c;
      const newEntry: SavingsEntry = {
        id: optimisticId,
        customerId,
        customerName,
        amount: parsed,
        type: 'deposit',
        note: note.trim(),
        date,
        bankAccountId,
        bankAccountName,
      };
      return { ...c, savingsEntries: [...c.savingsEntries, newEntry], savingsBalance: c.savingsBalance + parsed };
    }));
    await repo.addSavingsEntry(user.uid, customerId, customerName, parsed, 'deposit', note.trim(), date, bankAccountId, bankAccountName);
  }, [user]);

  const addSavingsWithdrawal = useCallback(async (customerId: string, customerName: string, amount: string, note: string, bankAccountId: string = '', bankAccountName: string = '', date: string = todayString()) => {
    if (!user) return;
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed <= 0) return;
    const optimisticId = `opt_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
    setCustomers(prev => prev.map(c => {
      if (c.id !== customerId) return c;
      const newEntry: SavingsEntry = {
        id: optimisticId,
        customerId,
        customerName,
        amount: parsed,
        type: 'withdrawal',
        note: note.trim(),
        date,
        bankAccountId,
        bankAccountName,
      };
      return { ...c, savingsEntries: [...c.savingsEntries, newEntry], savingsBalance: c.savingsBalance - parsed };
    }));
    await repo.addSavingsEntry(user.uid, customerId, customerName, parsed, 'withdrawal', note.trim(), date, bankAccountId, bankAccountName);
  }, [user]);

  const deleteSavingsEntry = useCallback(async (entryId: string) => {
    if (!user) return;
    setCustomers(prev => prev.map(c => {
      const entry = c.savingsEntries.find(e => e.id === entryId);
      if (!entry) return c;
      const balanceDelta = entry.type === 'deposit' ? -entry.amount : entry.amount;
      return {
        ...c,
        savingsEntries: c.savingsEntries.filter(e => e.id !== entryId),
        savingsBalance: c.savingsBalance + balanceDelta,
      };
    }));
    await repo.deleteSavingsEntry(user.uid, entryId);
  }, [user]);

  // ── Backup / Restore ──────────────────────────────────────────────────────

  const exportBackupToFile = useCallback(async () => {
    if (!user) return;
    setBackupInProgress(true);
    setBackupStatusMessage('Exporting backup...');
    try {
      const payload = await repo.exportBackup(user.uid, {}, {
        app: {
          themeMode: settings.themeMode,
          selectedAccountIds: Array.from(settings.selectedAccountIds),
          knownAccountIds: Array.from(settings.knownAccountIds),
        },
      });
      const json = backupToJson(payload);
      const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
      downloadJsonFile(json, `radafiq_backup_${ts}.json`);
      setBackupStatusMessage('Backup exported successfully.');
    } catch (e: unknown) {
      setBackupStatusMessage(`Export failed: ${e instanceof Error ? e.message : 'Unknown error'}`);
    } finally {
      setBackupInProgress(false);
    }
  }, [user, settings]);

  const importBackupFromFile = useCallback(async (file: File) => {
    if (!user) return;
    setBackupInProgress(true);
    setBackupStatusMessage('Importing backup...');
    try {
      const json = await readJsonFile(file);
      const payload = backupFromJson(json);
      await repo.restoreBackup(user.uid, payload);

      // BUG-17 fix: merge both settings updates into a single call
      updateSettings(s => {
        const appSettings = payload.settings?.app as Record<string, unknown> | undefined;
        const restoredIds = appSettings?.selectedAccountIds
          ? new Set(appSettings.selectedAccountIds as string[])
          : null;
        const restoredKnown = appSettings?.knownAccountIds
          ? new Set(appSettings.knownAccountIds as string[])
          : null;
        return {
          ...s,
          themeMode: (appSettings?.themeMode as 'LIGHT' | 'DARK') ?? s.themeMode,
          selectedAccountIds: restoredIds ?? s.selectedAccountIds,
          knownAccountIds: restoredKnown ?? s.knownAccountIds,
          lastDriveRestoreTime: currentTimestampLabel(),
        };
      });
      setBackupStatusMessage('Backup restored successfully.');
    } catch (e: unknown) {
      setBackupStatusMessage(`Import failed: ${e instanceof Error ? e.message : 'Unknown error'}`);
    } finally {
      setBackupInProgress(false);
    }
  }, [user, updateSettings]);

  // ── Sync status ───────────────────────────────────────────────────────────

  const triggerSync = useCallback(() => {
    if (!user) {
      setSyncStatus({ state: 'ERROR', message: 'Not signed in.' });
      return;
    }
    if (syncStatus.state === 'SYNCING') return;

    setSyncStatus({ state: 'SYNCING', message: 'Refreshing...' });
    if (syncResetTimerRef.current) clearTimeout(syncResetTimerRef.current);

    // Re-subscribe Firestore listeners to force a fresh pull
    unsubscribeDataRef.current.forEach(u => u());
    let firstSnapshot = true;
    const unsubs = repo.listenAllData(user.uid, (data) => {
      setCards(data.accounts);
      setCustomers(data.customers);
      setDeletedCustomers(data.deletedCustomers);
      if (firstSnapshot) {
        firstSnapshot = false;
        setSyncStatus({ state: 'SUCCESS', message: 'Data refreshed.' });
        syncResetTimerRef.current = setTimeout(() => {
          setSyncStatus({ state: 'IDLE', message: '' });
        }, 4000);
      }
    });
    unsubscribeDataRef.current = unsubs;
  }, [user, syncStatus.state]);

  // BUG-12 fix: cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (syncResetTimerRef.current) clearTimeout(syncResetTimerRef.current);
    };
  }, []);

  // ── Context value ─────────────────────────────────────────────────────────

  const value: AppContextValue = {
    user,
    authLoading,
    signInWithGoogle,
    signOut,
    profile,
    profileLoading,
    saveProfile: saveProfileFn,
    cards,
    customers,
    deletedCustomers,
    dataLoading,
    settings,
    setThemeMode,
    setAccountSelected,
    security,
    setPasscode,
    updatePasscode,
    clearPasscode,
    setLockEnabled,
    verifyPasscode,
    resetPasscodeWithRecovery,
    unlock,
    lock,
    hasPasskey,
    registerPasskey,
    authenticateWithPasskey,
    removePasskey,
    addCustomer,
    deleteCustomer,
    restoreCustomer,
    permanentlyDeleteCustomer,
    updateCustomerDueAmount,
    addTransaction,
    addEmiTransactions,
    addSplitTransactions,
    convertEmiInstallmentToSplit,
    updateTransaction,
    deleteTransaction,
    addPartialPayment,
    toggleTransactionSettled,
    updateCreditCardDue,
    addPayment,
    addSavingsDeposit,
    addSavingsWithdrawal,
    deleteSavingsEntry,
    settlementHistory,
    settlementHistoryLoading,
    loadSettlementHistory,
    exportBackupToFile,
    importBackupFromFile,
    backupStatusMessage,
    backupInProgress,
    syncStatus,
    triggerSync,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}
