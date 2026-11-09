import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../services/authApi';
import { SESSION_EVENT } from '../services/session';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem('fclinic_user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [token, setToken] = useState(() => localStorage.getItem('fclinic_token') || null);
  const [isLoading, setIsLoading] = useState(true);

  const saveSession = useCallback((data) => {
    if (data.accessToken) {
      localStorage.setItem('fclinic_token', data.accessToken);
      setToken(data.accessToken);
    }
    if (data.refreshToken) {
      localStorage.setItem('fclinic_refresh', data.refreshToken);
    }
    if (data.user) {
      localStorage.setItem('fclinic_user', JSON.stringify(data.user));
      setUser(data.user);
    }
  }, []);

  const clearSession = useCallback(() => {
    localStorage.removeItem('fclinic_token');
    localStorage.removeItem('fclinic_refresh');
    localStorage.removeItem('fclinic_user');
  for (const key of Object.keys(sessionStorage)) if (key.startsWith('fclinic.pending.')) sessionStorage.removeItem(key);
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const sync = () => {
      setToken(localStorage.getItem('fclinic_token'));
      try { setUser(JSON.parse(localStorage.getItem('fclinic_user') || 'null')); } catch { clearSession(); }
    };
    window.addEventListener(SESSION_EVENT, sync);
    return () => window.removeEventListener(SESSION_EVENT, sync);
  }, [clearSession]);

  // Verify initial token on mount
  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem('fclinic_token');
      if (!storedToken) {
        setIsLoading(false);
        return;
      }
      try {
        const currentUser = await authApi.getMe();
        const currentToken = localStorage.getItem('fclinic_token');
        const currentIdentity = JSON.parse(localStorage.getItem('fclinic_user') || 'null');
        if (!currentToken || (currentIdentity?.id && currentIdentity.id !== currentUser.id)) return;
        setUser(currentUser);
        localStorage.setItem('fclinic_user', JSON.stringify(currentUser));
      } catch (err) {
        clearSession();
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();
  }, [clearSession, saveSession]);

  const login = async (username, password) => {
    const data = await authApi.login(username, password);
    saveSession(data);
    return data;
  };

  const register = async (registerData) => {
    const data = await authApi.register(registerData);
    saveSession(data);
    return data;
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('fclinic_refresh');
    clearSession();
    if (refreshToken) await authApi.logout(refreshToken);
  };

  const [capabilities, setCapabilities] = useState({});
  useEffect(() => {
    let current = true; setCapabilities({});
    if (token && user?.id) authApi.getPatientCapabilities().then(value => { if (current) setCapabilities(value); }).catch(() => {});
    return () => { current = false; };
  }, [token, user?.id, user?.patientId]);
  const hasRole = useCallback((roles) => {
    if (!user || !user.role) return false;
    if (Array.isArray(roles)) {
      return roles.includes(user.role);
    }
    return user.role === roles;
  }, [user]);

  const updateUserSession = useCallback((newUser) => {
    const current = JSON.parse(localStorage.getItem('fclinic_user') || 'null');
    if (!localStorage.getItem('fclinic_token') || current?.id !== newUser?.id) return false;
    localStorage.setItem('fclinic_user', JSON.stringify(newUser));
    setUser(newUser);
    return true;
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        capabilities,
        token,
        isAuthenticated: !!user && !!token,
        isLoading,
        login,
        register,
        logout,
        hasRole,
        updateUserSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

