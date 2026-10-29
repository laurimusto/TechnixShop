"use client";

import { useState } from "react";

const API_BASE = "http://localhost:8080";

type AuthResponse = { token: string; email: string; id: number };

export default function ApiTestConsole() {
  // access token kept in memory only (React state), never written to local/session storage
  const [token, setToken] = useState<string | null>(null);
  const [loggedInEmail, setLoggedInEmail] = useState<string | null>(null);
  const [authMessage, setAuthMessage] = useState("");

  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");
  const [registerRepeatPassword, setRegisterRepeatPassword] = useState("");

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");

  const [method, setMethod] = useState("GET");
  const [path, setPath] = useState("/search?categoryId=1");
  const [body, setBody] = useState("");
  const [status, setStatus] = useState<number | null>(null);
  const [response, setResponse] = useState("");

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    const res = await fetch(`${API_BASE}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({
        email: registerEmail,
        password: registerPassword,
        repeatPassword: registerRepeatPassword,
      }),
    });
    const data = await res.json();
    if (res.ok) {
      const auth = data as AuthResponse;
      setToken(auth.token);
      setLoggedInEmail(auth.email);
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
      setToken(auth.token);
      setLoggedInEmail(auth.email);
      setAuthMessage(`Logged in as ${auth.email}`);
    } else {
      setAuthMessage(`Error ${res.status}: ${data.message ?? JSON.stringify(data)}`);
    }
  }

  function handleLogout() {
    setToken(null);
    setLoggedInEmail(null);
    setAuthMessage("Logged out (token cleared from memory)");
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
    <main className="max-w-3xl mx-auto p-6 space-y-8 font-sans">
      <h1 className="text-xl font-semibold">API test console</h1>

      <section className="space-y-2">
        <h2 className="font-medium">Auth</h2>
        <p className="text-sm">
          {loggedInEmail ? `Logged in as ${loggedInEmail}` : "Not logged in"}
          {token && (
            <button onClick={handleLogout} className="ml-3 underline">
              Logout
            </button>
          )}
        </p>
        {authMessage && <p className="text-sm text-gray-600">{authMessage}</p>}

        <div className="flex gap-6">
          <form onSubmit={handleRegister} className="flex flex-col gap-2 border p-3 w-64">
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
