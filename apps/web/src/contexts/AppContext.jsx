import { createContext, useContext, useState, useEffect } from 'react';

const AppContext = createContext();

export function AppProvider({ children }) {
    const [settings, setSettings] = useState(() => {
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

    // Fetch settings from API on mount
    useEffect(() => {
        const fetchSettings = async () => {
            try {
                // Try to use a relative path first (Vite proxy should handle this)
                const response = await fetch('/api/v1/status');
                if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
                
                const res = await response.json();
                console.log('Public Status Sync:', res); // Log to help debug
                
                if (res) {
                    const mapped = {
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
        
        const interval = setInterval(fetchSettings, 30000); // Check every 30s
        return () => clearInterval(interval);
    }, []);

    // Lưu state xuống Local Storage ngay khi có sự thay đổi
    useEffect(() => {
        localStorage.setItem('appSettings', JSON.stringify(settings));
    }, [settings]);

    const updateSettings = (updates) => {
        setSettings(prev => ({ ...prev, ...updates }));
    };

    return (
        <AppContext.Provider value={{ settings, updateSettings }}>
            {children}
        </AppContext.Provider>
    );
}

export const useAppContext = () => useContext(AppContext);
