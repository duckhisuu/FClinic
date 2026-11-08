import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../services/authApi';

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
    setToken(null);
    setUser(null);
  }, []);

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
        setUser(currentUser);
        localStorage.setItem('fclinic_user', JSON.stringify(currentUser));
      } catch (err) {
        // Try refreshing if token expired
        const refreshToken = localStorage.getItem('fclinic_refresh');
        if (refreshToken) {
          try {
            const refreshData = await authApi.refresh(refreshToken);
            saveSession(refreshData);
          } catch {
            clearSession();
          }
        } else {
          clearSession();
        }
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
    if (refreshToken) {
      await authApi.logout(refreshToken);
    }
    clearSession();
  };

  const hasRole = useCallback((roles) => {
    if (!user || !user.role) return false;
    if (Array.isArray(roles)) {
      return roles.includes(user.role);
    }
    return user.role === roles;
  }, [user]);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token,
        isLoading,
        login,
        register,
        logout,
        hasRole,
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

