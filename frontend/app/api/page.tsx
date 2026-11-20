"use client";

import Link from "next/link";
import Script from "next/script";
import { useRef, useState } from "react";
import { renderGoogleButton, useGoogleCredential } from "../auth-widgets";
import { API_BASE, useShop } from "../providers";

const RECAPTCHA_SITE_KEY = process.env.NEXT_PUBLIC_RECAPTCHA_SITE_KEY ?? "";

type AuthResponse = { token: string; email: string; id: number };
// window.google / window.grecaptcha types are declared in ../auth-widgets.tsx

export default function ApiTestConsole() {
  // session is shared with the shop header; the access token is kept in memory only
  const { token, email: loggedInEmail, setSession } = useShop();
  const [authMessage, setAuthMessage] = useState("");

  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");
  const [registerRepeatPassword, setRegisterRepeatPassword] = useState("");
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const captchaContainerRef = useRef<HTMLDivElement>(null);
  const captchaWidgetIdRef = useRef<number | null>(null);

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const googleButtonRef = useRef<HTMLDivElement>(null);

  const [method, setMethod] = useState("GET");
  const [path, setPath] = useState("/search?categoryId=1");
  const [body, setBody] = useState("");
  const [status, setStatus] = useState<number | null>(null);
  const [response, setResponse] = useState("");

  function renderCaptcha() {
    const grecaptcha = window.grecaptcha;
    if (!grecaptcha) return;
    grecaptcha.ready(() => {
      if (!captchaContainerRef.current || captchaWidgetIdRef.current !== null) return;
      captchaWidgetIdRef.current = grecaptcha.render(captchaContainerRef.current, {
        sitekey: RECAPTCHA_SITE_KEY,
        callback: setCaptchaToken,
        "expired-callback": () => setCaptchaToken(null),
      });
    });
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    if (!captchaToken) {
      setAuthMessage("Please complete the CAPTCHA first");
      return;
    }
    const res = await fetch(`${API_BASE}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({
        email: registerEmail,
        password: registerPassword,
        repeatPassword: registerRepeatPassword,
        captchaToken,
      }),
    });
    // Google accepts each token only once, so a retry needs a fresh checkbox tick
    window.grecaptcha?.reset(captchaWidgetIdRef.current ?? undefined);
    setCaptchaToken(null);
    const data = await res.json();
    if (res.ok) {
      const auth = data as AuthResponse;
      setSession(auth.token, auth.email);
      setAuthMessage(`Registered as ${auth.email}`);
    } else {
      setAuthMessage(`Error ${res.status}: ${data.message ?? JSON.stringify(data)}`);
    }
  }

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    const res = await fetch(`${API_BASE}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ email: loginEmail, password: loginPassword }),
    });
    const data = await res.json();
    if (res.ok) {
      const auth = data as AuthResponse;
      setSession(auth.token, auth.email);
      setAuthMessage(`Logged in as ${auth.email}`);
    } else {
      setAuthMessage(`Error ${res.status}: ${data.message ?? JSON.stringify(data)}`);
    }
  }

  function renderGoogleSignIn() {
    if (!googleButtonRef.current) return;
    renderGoogleButton(googleButtonRef.current, {
      theme: "outline",
      size: "medium",
      text: "signin_with",
    });
  }

  // credential is Google's signed ID token; the backend verifies it and returns our own JWT
  async function handleGoogleCredential(response: { credential: string }) {
    const res = await fetch(`${API_BASE}/googlelogin`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ idToken: response.credential }),
    });
    const data = await res.json();
    if (res.ok) {
      const auth = data as AuthResponse;
      setSession(auth.token, auth.email);
      setAuthMessage(`Logged in with Google as ${auth.email}`);
    } else {
      setAuthMessage(`Error ${res.status}: ${data.message ?? JSON.stringify(data)}`);
    }
  }
  // shares the page-wide Google setup with the other Google buttons (initialize() runs only once)
  useGoogleCredential((credential) => handleGoogleCredential({ credential }));

  async function handleLogout() {
    try {
      // backend denylists the access token's jti, revokes the refresh token, and clears the cookie
      const res = await fetch(`${API_BASE}/logout`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        credentials: "include",
      });
      setAuthMessage(
        res.ok
          ? "Logged out (tokens revoked on the server)"
          : `Server logout failed (HTTP ${res.status}); token cleared from memory anyway`,
      );
    } catch {
      setAuthMessage("Backend unreachable; token cleared from memory anyway");
    } finally {
      setSession(null, null);
    }
  }

  async function handleSend(e: React.FormEvent) {
    e.preventDefault();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    if (method !== "GET" && body.trim()) headers["Content-Type"] = "application/json";

    const res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      credentials: "include",
      body: method !== "GET" && body.trim() ? body : undefined,
    });

    setStatus(res.status);
    const text = await res.text();
    try {
      setResponse(JSON.stringify(JSON.parse(text), null, 2));
    } catch {
      setResponse(text);
    }
  }

  return (
    // developer test page: kept on a light card so its plain light-theme styling stays readable
    <main className="max-w-3xl mx-auto my-8 p-6 space-y-8 font-sans rounded-3xl bg-white text-slate-900 [color-scheme:light]">
      <Script
        src="https://www.google.com/recaptcha/api.js?render=explicit"
        onReady={renderCaptcha}
      />
      <Script src="https://accounts.google.com/gsi/client" onReady={renderGoogleSignIn} />
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">API test console</h1>
        <Link href="/" className="border px-3 py-1 text-sm">
          ← Back to shop
        </Link>
      </div>

      <section className="space-y-2">
        <h2 className="font-medium">Auth</h2>
        <div className="flex items-center gap-3 text-sm">
          <span>{loggedInEmail ? `Logged in as ${loggedInEmail}` : "Not logged in"}</span>
          {token && (
            <button onClick={handleLogout} className="border px-3 py-1">
              Logout
            </button>
          )}
        </div>
        {authMessage && <p className="text-sm text-gray-600">{authMessage}</p>}

        <div className="flex gap-6">
          <form onSubmit={handleRegister} className="flex flex-col gap-2 border p-3 w-80">
            <span className="font-medium text-sm">Register</span>
            <input
              className="border px-2 py-1"
              placeholder="email"
              value={registerEmail}
              onChange={(e) => setRegisterEmail(e.target.value)}
            />
            <input
              className="border px-2 py-1"
              placeholder="password"
              type="password"
              value={registerPassword}
              onChange={(e) => setRegisterPassword(e.target.value)}
            />
            <input
              className="border px-2 py-1"
              placeholder="repeat password"
              type="password"
              value={registerRepeatPassword}
              onChange={(e) => setRegisterRepeatPassword(e.target.value)}
            />
            <div ref={captchaContainerRef} />
            <button type="submit" className="border px-2 py-1">
              Register
            </button>
          </form>

          <form onSubmit={handleLogin} className="flex flex-col gap-2 border p-3 w-64">
            <span className="font-medium text-sm">Login</span>
            <input
              className="border px-2 py-1"
              placeholder="email"
              value={loginEmail}
              onChange={(e) => setLoginEmail(e.target.value)}
            />
            <input
              className="border px-2 py-1"
              placeholder="password"
              type="password"
              value={loginPassword}
              onChange={(e) => setLoginPassword(e.target.value)}
            />
            <button type="submit" className="border px-2 py-1">
              Login
            </button>
            <span className="text-xs text-gray-600 text-center">or</span>
            <div ref={googleButtonRef} />
          </form>
        </div>
      </section>

      <section className="space-y-2">
        <h2 className="font-medium">Try an endpoint</h2>
        <p className="text-sm text-gray-600">
          Authorization header is attached automatically when logged in.
        </p>
        <form onSubmit={handleSend} className="flex flex-col gap-2">
          <div className="flex gap-2">
            <select
              className="border px-2 py-1"
              value={method}
              onChange={(e) => setMethod(e.target.value)}
            >
              <option>GET</option>
              <option>POST</option>
              <option>PUT</option>
              <option>DELETE</option>
            </select>
            <input
              className="border px-2 py-1 flex-1"
              value={path}
              onChange={(e) => setPath(e.target.value)}
              placeholder="/search?categoryId=1"
            />
            <button type="submit" className="border px-3 py-1">
              Send
            </button>
          </div>
          {method !== "GET" && (
            <textarea
              className="border px-2 py-1 font-mono text-sm"
              rows={4}
              placeholder='{"key": "value"}'
              value={body}
              onChange={(e) => setBody(e.target.value)}
            />
          )}
        </form>

        {status !== null && (
          <>
            <p className="text-sm font-medium">Status: {status}</p>
            <pre className="border p-3 text-sm overflow-auto max-h-[500px] whitespace-pre-wrap">
              {response}
            </pre>
          </>
        )}
      </section>
    </main>
  );
}
