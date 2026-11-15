"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import CartDrawer from "./cart-drawer";
import { useShop } from "./providers";
import { btn, Logo } from "./ui";

const iconProps = {
  width: 22,
  height: 22,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};

const inputClass =
  "w-full rounded-xl bg-field px-3 py-2.5 text-sm text-slate-100 ring-1 ring-white/10 placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-accent";

export default function Header() {
  const router = useRouter();
  const { email, login, logout, cartCount } = useShop();
  const [accountOpen, setAccountOpen] = useState(false);
  const [cartOpen, setCartOpen] = useState(false);
  const [error, setError] = useState("");
  const accountRef = useRef<HTMLDivElement>(null);

  // close the account popover on outside click or Escape
  useEffect(() => {
    if (!accountOpen) return;
    function onPointerDown(e: PointerEvent) {
      if (!accountRef.current?.contains(e.target as Node)) setAccountOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setAccountOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [accountOpen]);

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
    <header className="sticky top-0 z-30 border-b border-white/10 bg-chrome/85 backdrop-blur-lg">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-3 sm:gap-5 sm:px-6">
        <Link href="/" aria-label="Technix home" className="rounded-xl">
          <Logo />
        </Link>

        <form
          onSubmit={handleSearch}
          role="search"
          className="relative order-last w-full sm:order-none sm:w-auto sm:flex-1"
        >
          <svg {...iconProps} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
          <input
            name="q"
            type="search"
            aria-label="Search products"
            placeholder="Search laptops, phones, brands…"
            className="w-full rounded-full bg-white/5 py-2.5 pl-11 pr-4 text-sm text-slate-100 ring-1 ring-white/10 transition placeholder:text-muted focus:bg-white/10 focus:outline-none focus:ring-2 focus:ring-accent"
          />
          <button type="submit" className="sr-only">
            Search
          </button>
        </form>

        <div className="ml-auto flex items-center gap-1 sm:ml-0">
          <div ref={accountRef} className="relative">
            <button
              onClick={() => setAccountOpen((open) => !open)}
              aria-expanded={accountOpen}
              className={`${btn.ghost} px-3 py-2 text-sm`}
            >
              <svg {...iconProps}>
                <circle cx="12" cy="8" r="4" />
                <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
              </svg>
              <span className="hidden max-w-40 truncate sm:inline">{email ?? "Sign in"}</span>
              <span className="sr-only sm:hidden">{email ? "Account" : "Sign in"}</span>
            </button>

            {accountOpen && (
              <div className="animate-pop-in absolute right-0 z-10 mt-2 w-72 rounded-2xl bg-raised p-5 text-sm text-slate-200 shadow-2xl shadow-black/50 ring-1 ring-white/10">
                {email ? (
                  <div className="flex flex-col gap-4">
                    <p className="truncate">
                      Signed in as <span className="font-medium text-white">{email}</span>
                    </p>
                    <button
                      onClick={() => logout().then(() => setAccountOpen(false))}
                      className={`${btn.secondary} px-4 py-2.5`}
                    >
                      Log out
                    </button>
                  </div>
                ) : (
                  <form onSubmit={handleLogin} className="flex flex-col gap-3">
                    <p className="text-base font-semibold text-white">Welcome back</p>
                    <input
                      name="email"
                      type="email"
                      required
                      aria-label="Email"
                      placeholder="Email"
                      autoComplete="email"
                      className={inputClass}
                    />
                    <input
                      name="password"
                      type="password"
                      required
                      aria-label="Password"
                      placeholder="Password"
                      autoComplete="current-password"
                      className={inputClass}
                    />
                    {error && (
                      <p role="alert" className="rounded-lg bg-red-500/10 px-3 py-2 text-red-200 ring-1 ring-red-500/30">
                        {error}
                      </p>
                    )}
                    <button
                      type="submit"
                      className={`${btn.primary} px-4 py-2.5`}
                    >
                      Log in
                    </button>
                    <Link
                      href="/api"
                      onClick={() => setAccountOpen(false)}
                      className="text-center text-muted underline-offset-4 hover:text-white hover:underline"
                    >
                      Create an account or sign in with Google
                    </Link>
                  </form>
                )}
              </div>
            )}
          </div>

          <button
            onClick={() => setCartOpen(true)}
            aria-label={`Cart, ${cartCount} items`}
            aria-haspopup="dialog"
            className={`${btn.ghost} relative p-2.5`}
          >
            <svg {...iconProps}>
              <path d="M6 7h12l-1 13H7L6 7Z" />
              <path d="M9 7a3 3 0 0 1 6 0" />
            </svg>
            {cartCount > 0 && (
              // keyed on the count so the badge "pops" on every add
              <span
                key={cartCount}
                className="animate-pop absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1 text-[11px] font-bold text-on-accent"
              >
                {cartCount}
              </span>
            )}
          </button>
          <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} />
        </div>
      </div>
    </header>
  );
}
