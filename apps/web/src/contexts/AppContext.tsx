import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { AppContext } from './appContextStore';
import type { AppSettings } from './appContextStore';
import { apiClient } from '../utils/apiClient';

interface StatusResponse {
    maintenance_web?: string | boolean;
    maintenance_mobile?: string | boolean;
    feature_graph?: string | boolean;
    feature_rag?: string | boolean;
    feature_chat?: string | boolean;
}

export function AppProvider({ children }: { children: ReactNode }) {
    const [settings, setSettings] = useState<AppSettings>(() => {
        const saved = localStorage.getItem('appSettings');
        if (saved) return JSON.parse(saved);

        // Mặc định cho phép Website hoạt động
        return {
            isWebMaintenance: false,
            isAppMaintenance: false,
            isChatEnabled: true,
            isGraphEnabled: true
        };
    });

    // Fetch settings from API on mount, then poll while the tab is visible.
    useEffect(() => {
        const fetchSettings = async () => {
            try {
                const res = await apiClient<StatusResponse>('/status');
                if (res) {
                    const mapped: AppSettings = {
                        isWebMaintenance: res.maintenance_web === 'true' || res.maintenance_web === true,
                        isAppMaintenance: res.maintenance_mobile === 'true' || res.maintenance_mobile === true,
                        isGraphEnabled: res.feature_graph === 'true' || res.feature_graph === true,
                        // Block AI Chat if either feature_chat or feature_rag is disabled
                        isChatEnabled: (res.feature_rag !== undefined ? (res.feature_rag === 'true' || res.feature_rag === true) : true) &&
                                       (res.feature_chat !== undefined ? (res.feature_chat === 'true' || res.feature_chat === true) : true),
                    };
                    setSettings(mapped);
                }
            } catch (error) {
                console.error('Failed to sync settings with server status:', error);
            }
        };

        fetchSettings();

        let interval: number | null = null;
        const startPolling = () => {
            if (interval) return;
            interval = setInterval(fetchSettings, 30000);
        };
        const stopPolling = () => {
            if (interval) clearInterval(interval);
            interval = null;
        };
        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                fetchSettings();
                startPolling();
            } else {
                stopPolling();
            }
        };

        startPolling();
        document.addEventListener('visibilitychange', handleVisibilityChange);
        return () => {
            stopPolling();
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, []);

    // Lưu state xuống Local Storage ngay khi có sự thay đổi
    useEffect(() => {
        localStorage.setItem('appSettings', JSON.stringify(settings));
    }, [settings]);

    const updateSettings = (updates: Partial<AppSettings>) => {
        setSettings(prev => ({ ...prev, ...updates }));
    };

    return (
        <AppContext.Provider value={{ settings, updateSettings }}>
            {children}
        </AppContext.Provider>
    );
}
