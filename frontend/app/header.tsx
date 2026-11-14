"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useShop } from "./providers";

const iconProps = {
  width: 24,
  height: 24,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};

export default function Header() {
  const router = useRouter();
  const { email, login, logout, cartCount } = useShop();
  const [accountOpen, setAccountOpen] = useState(false);
  const [error, setError] = useState("");

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const q = String(new FormData(e.currentTarget).get("q") ?? "").trim();
    router.push(q ? `/?q=${encodeURIComponent(q)}` : "/");
  }

  async function handleLogin(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const message = await login(String(form.get("email")), String(form.get("password")));
    setError(message ?? "");
    if (!message) setAccountOpen(false);
  }

  return (
    <header className="bg-slate-900 text-white">
      <div className="max-w-6xl mx-auto px-6 py-3 flex flex-wrap items-center gap-4">
        <Link href="/" className="text-2xl font-bold tracking-tight">
          Technix<span className="text-amber-400">.</span>
        </Link>

        <form onSubmit={handleSearch} role="search" className="flex flex-1 min-w-48">
          <input
            name="q"
            type="search"
            aria-label="Search products"
            placeholder="Search products..."
            className="flex-1 min-w-0 rounded-l bg-white text-black px-3 py-2"
          />
          <button type="submit" className="rounded-r bg-amber-400 text-black px-4 font-medium">
            Search
          </button>
        </form>

        <div className="relative">
          <button
            onClick={() => setAccountOpen((open) => !open)}
            aria-expanded={accountOpen}
            className="flex items-center gap-2 text-sm"
          >
            <svg {...iconProps}>
              <circle cx="12" cy="8" r="4" />
              <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
            </svg>
            <span className="max-w-40 truncate">{email ?? "Sign in"}</span>
          </button>

          {accountOpen && (
            <div className="absolute right-0 z-10 mt-2 w-64 rounded border bg-white p-4 text-sm text-black shadow-lg">
              {email ? (
                <div className="flex flex-col gap-3">
                  <p className="truncate">Signed in as {email}</p>
                  <button
                    onClick={() => logout().then(() => setAccountOpen(false))}
                    className="rounded bg-slate-900 px-3 py-2 text-white"
                  >
                    Log out
                  </button>
                </div>
              ) : (
                <form onSubmit={handleLogin} className="flex flex-col gap-2">
                  <input
                    name="email"
                    type="email"
                    required
                    aria-label="Email"
                    placeholder="Email"
                    autoComplete="email"
                    className="rounded border px-2 py-1"
                  />
                  <input
                    name="password"
                    type="password"
                    required
                    aria-label="Password"
                    placeholder="Password"
                    autoComplete="current-password"
                    className="rounded border px-2 py-1"
                  />
                  {error && (
                    <p role="alert" className="text-red-700">
                      {error}
                    </p>
                  )}
                  <button type="submit" className="rounded bg-slate-900 px-3 py-2 text-white">
                    Log in
                  </button>
                  <Link href="/api" onClick={() => setAccountOpen(false)} className="underline">
                    Register or sign in with Google
                  </Link>
                </form>
              )}
            </div>
          )}
        </div>

        <button aria-label={`Cart, ${cartCount} items`} className="relative">
          <svg {...iconProps}>
            <circle cx="9" cy="20" r="1.5" />
            <circle cx="18" cy="20" r="1.5" />
            <path d="M2 3h3l3 12h11l2-8H6.5" />
          </svg>
          {cartCount > 0 && (
            <span className="absolute -right-2 -top-2 rounded-full bg-amber-400 px-1.5 text-xs font-bold text-black">
              {cartCount}
            </span>
          )}
        </button>
      </div>
    </header>
  );
}
