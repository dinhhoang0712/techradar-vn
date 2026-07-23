import { createContext, useContext } from 'react';

export interface AppSettings {
    isWebMaintenance: boolean;
    isAppMaintenance: boolean;
    isChatEnabled: boolean;
    isGraphEnabled: boolean;
}

export interface AppContextValue {
    settings: AppSettings;
    updateSettings: (updates: Partial<AppSettings>) => void;
}

export const AppContext = createContext<AppContextValue | undefined>(undefined);

export const useAppContext = (): AppContextValue | undefined => useContext(AppContext);
