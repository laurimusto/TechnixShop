"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";

export const API_BASE = "http://localhost:8080";

const RECENTLY_VIEWED_LIMIT = 8;

export type Product = {
  productId: number;
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  categoryName: string;
  brandName: string;
  imageUrl: string | null;
  weightKg: number;
  averageRating: number;
  ratingCount: number;
};

export type CartItem = { product: Product; quantity: number };

type ToastTone = "success" | "info";

type AuthResponse = { token: string; email: string; id: number };

// all optional; checkout (Project 2) will require them
export type Profile = {
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  street: string | null;
  postalCode: string | null;
  city: string | null;
  country: string | null;
};

type ShopContextValue = {
  token: string | null;
  email: string | null;
  // false until the startup refresh has finished - before that, "no email" doesn't yet mean "logged out"
  authReady: boolean;
  setSession: (token: string | null, email: string | null) => void;
  // fetch with the access token; on 401 it refreshes the token once and retries
  authFetch: (path: string, init?: RequestInit) => Promise<Response>;
  // the three sign-in functions return an error message, or null on success
  login: (email: string, password: string) => Promise<string | null>;
  register: (
    email: string,
    password: string,
    repeatPassword: string,
    captchaToken: string,
    profile?: Profile,
  ) => Promise<string | null>;
  googleLogin: (idToken: string) => Promise<string | null>;
  logout: () => Promise<void>;
  // cart is in memory for now; Project 2 moves it to the backend
  cart: CartItem[];
  cartCount: number;
  cartTotal: number;
  addToCart: (product: Product) => boolean; // false when the stock limit is already reached
  setQuantity: (productId: number, quantity: number) => void;
  removeFromCart: (productId: number) => void;
  notify: (message: React.ReactNode, tone?: ToastTone) => void;
  // in memory for now; later this can be loaded from / saved to the backend per user
  recentlyViewed: Product[];
  markViewed: (product: Product) => void;
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
  const [cart, setCart] = useState<CartItem[]>([]);

  const setSession = useCallback((token: string | null, email: string | null) => {
    setSessionState(token && email ? { token, email } : null);
  }, []);

  // on page load, the HttpOnly refresh cookie (if any) restores the session
  const [authReady, setAuthReady] = useState(false);
  useEffect(() => {
    refreshSession().then((auth) => {
      if (auth) setSession(auth.token, auth.email);
      setAuthReady(true);
    });
  }, [setSession]);

  // Access tokens are short-lived. When one has expired the API answers 401; instead of
  // logging the user out, get a new one with the refresh cookie and repeat the request once.
  const authFetch = useCallback(
    async (path: string, init: RequestInit = {}) => {
      const send = (accessToken: string | undefined) =>
        fetch(`${API_BASE}${path}`, {
          ...init,
          credentials: "include",
          headers: { ...init.headers, ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) },
        });

      const res = await send(session?.token);
      if (res.status !== 401) return res;

      const auth = await refreshSession();
      if (!auth) {
        setSessionState(null); // refresh cookie gone or revoked: really logged out
        return res;
      }
      setSession(auth.token, auth.email);
      return send(auth.token);
    },
    [session, setSession],
  );

  // /login, /register and /googlelogin all answer with the same AuthResponse, so they share one
  // request function. credentials: "include" lets the browser store the HttpOnly refresh cookie.
  const authenticate = useCallback(
    async (path: string, body: object) => {
      try {
        const res = await fetch(`${API_BASE}${path}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify(body),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) return (data.message as string | undefined) ?? `Request failed (${res.status})`;
        setSession(data.token, data.email);
        return null;
      } catch {
        return "Could not reach the server";
      }
    },
    [setSession],
  );

  const login = useCallback(
    (email: string, password: string) => authenticate("/login", { email, password }),
    [authenticate],
  );
  const register = useCallback(
    (email: string, password: string, repeatPassword: string, captchaToken: string, profile?: Profile) =>
      authenticate("/register", { email, password, repeatPassword, captchaToken, profile }),
    [authenticate],
  );
  const googleLogin = useCallback((idToken: string) => authenticate("/googlelogin", { idToken }), [authenticate]);

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

  // toast message; `id` makes a repeated message re-trigger the animation
  const [toast, setToast] = useState<{ id: number; message: React.ReactNode; tone: ToastTone } | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const notify = useCallback((message: React.ReactNode, tone: ToastTone = "info") => {
    setToast({ id: Date.now(), message, tone });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 2500);
  }, []);

  const addToCart = useCallback(
    (product: Product) => {
      const inCart = cart.find((item) => item.product.productId === product.productId)?.quantity ?? 0;
      if (inCart >= product.stockQuantity) {
        notify(`Only ${product.stockQuantity} in stock - all of them are in your cart`);
        return false;
      }
      setCart((items) =>
        inCart > 0
          ? items.map((item) =>
              item.product.productId === product.productId ? { ...item, quantity: item.quantity + 1 } : item,
            )
          : [...items, { product, quantity: 1 }],
      );
      notify(
        <>
          <strong className="font-semibold">{product.name}</strong> added to cart
        </>,
        "success",
      );
      return true;
    },
    [cart, notify],
  );

  const removeFromCart = useCallback((productId: number) => {
    setCart((items) => items.filter((item) => item.product.productId !== productId));
  }, []);

  const setQuantity = useCallback((productId: number, quantity: number) => {
    setCart((items) =>
      items
        .map((item) =>
          item.product.productId === productId
            ? { ...item, quantity: Math.min(quantity, item.product.stockQuantity) }
            : item,
        )
        .filter((item) => item.quantity > 0),
    );
  }, []);

  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0);
  const cartTotal = cart.reduce((sum, item) => sum + item.quantity * item.product.price, 0);

  const [recentlyViewed, setRecentlyViewed] = useState<Product[]>([]);
  const markViewed = useCallback((product: Product) => {
    setRecentlyViewed((list) =>
      [product, ...list.filter((p) => p.productId !== product.productId)].slice(0, RECENTLY_VIEWED_LIMIT),
    );
  }, []);

  return (
    <ShopContext.Provider
      value={{
        token: session?.token ?? null,
        email: session?.email ?? null,
        authReady,
        setSession,
        authFetch,
        login,
        register,
        googleLogin,
        logout,
        cart,
        cartCount,
        cartTotal,
        addToCart,
        setQuantity,
        removeFromCart,
        notify,
        recentlyViewed,
        markViewed,
      }}
    >
      {children}
      {/* live region stays mounted so screen readers announce each new toast */}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4"
      >
        {toast && (
          <div
            key={toast.id}
            className="animate-pop-in flex items-center gap-3 rounded-full bg-slate-100 py-3 pl-3 pr-5 text-sm text-slate-900 shadow-2xl shadow-black/50"
          >
            <span
              aria-hidden
              className={`flex h-6 w-6 items-center justify-center rounded-full text-white ${
                toast.tone === "success" ? "bg-emerald-500" : "bg-slate-600"
              }`}
            >
              {toast.tone === "success" ? "✓" : "i"}
            </span>
            <span>{toast.message}</span>
          </div>
        )}
      </div>
    </ShopContext.Provider>
  );
}

export function useShop() {
  const context = useContext(ShopContext);
  if (!context) throw new Error("useShop must be used inside <Providers>");
  return context;
}
