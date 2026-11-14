"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";

export const API_BASE = "http://localhost:8080";

type AuthResponse = { token: string; email: string; id: number };

type ShopContextValue = {
  token: string | null;
  email: string | null;
  setSession: (token: string | null, email: string | null) => void;
  login: (email: string, password: string) => Promise<string | null>; // returns an error message, or null on success
  logout: () => Promise<void>;
  cartCount: number;
  addToCart: () => void;
};

const ShopContext = createContext<ShopContextValue | null>(null);

// Refresh tokens are single-use: two parallel /refresh calls with the same cookie would look
// like token reuse and revoke the whole session, so concurrent callers share one request.
let refreshInFlight: Promise<AuthResponse | null> | null = null;
function refreshSession(): Promise<AuthResponse | null> {
  refreshInFlight ??= fetch(`${API_BASE}/refresh`, { method: "POST", credentials: "include" })
    .then((res) => (res.ok ? (res.json() as Promise<AuthResponse>) : null))
    .catch(() => null)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

export function Providers({ children }: { children: React.ReactNode }) {
  // access token lives in memory only (React state), never in local/session storage
  const [session, setSessionState] = useState<{ token: string; email: string } | null>(null);
  const [cartCount, setCartCount] = useState(0);

  const setSession = useCallback((token: string | null, email: string | null) => {
    setSessionState(token && email ? { token, email } : null);
  }, []);

  // on page load, the HttpOnly refresh cookie (if any) restores the session
  useEffect(() => {
    refreshSession().then((auth) => {
      if (auth) setSession(auth.token, auth.email);
    });
  }, [setSession]);

  const login = useCallback(
    async (email: string, password: string) => {
      try {
        const res = await fetch(`${API_BASE}/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ email, password }),
        });
        const data = await res.json();
        if (!res.ok) return (data.message as string) ?? `Login failed (${res.status})`;
        setSession(data.token, data.email);
        return null;
      } catch {
        return "Could not reach the server";
      }
    },
    [setSession],
  );

  const logout = useCallback(async () => {
    // the backend revokes the refresh token and clears the cookie even if this access token
    // has already expired, so there is no need to refresh first
    try {
      await fetch(`${API_BASE}/logout`, {
        method: "POST",
        headers: session ? { Authorization: `Bearer ${session.token}` } : undefined,
        credentials: "include",
      });
    } catch {
      // backend unreachable: still clear the local session below
    } finally {
      setSessionState(null);
    }
  }, [session]);

  const addToCart = useCallback(() => setCartCount((count) => count + 1), []);

  return (
    <ShopContext.Provider
      value={{
        token: session?.token ?? null,
        email: session?.email ?? null,
        setSession,
        login,
        logout,
        cartCount,
        addToCart,
      }}
    >
      {children}
    </ShopContext.Provider>
  );
}

export function useShop() {
  const context = useContext(ShopContext);
  if (!context) throw new Error("useShop must be used inside <Providers>");
  return context;
}
