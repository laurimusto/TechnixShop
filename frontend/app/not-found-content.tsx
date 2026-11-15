"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { btn } from "./ui";

const isDev = process.env.NODE_ENV !== "production";

// document/window only exist in the browser; the server snapshot ("") is used during SSR,
// so server and client HTML match (no hydration mismatch)
const noSubscribe = () => () => {};
function useReferrer() {
  return useSyncExternalStore(noSubscribe, () => document.referrer, () => "");
}
function useOrigin() {
  return useSyncExternalStore(noSubscribe, () => window.location.origin, () => "");
}

export default function NotFoundContent() {
  const router = useRouter();
  const pathname = usePathname();
  const referrer = useReferrer();
  const origin = useOrigin();
  // came here by clicking a link inside the shop -> that link is broken
  const internalReferrer = origin !== "" && referrer.startsWith(origin);

  // Dev logging: shows in the browser console which URL is missing and which page linked to it.
  // In production this is where you'd report to a logging service or a backend endpoint instead.
  useEffect(() => {
    if (!isDev) return;
    console.warn(`[404] ${window.location.pathname}${window.location.search}`, {
      linkedFrom: document.referrer || "(typed URL, bookmark or external site)",
      time: new Date().toISOString(),
    });
  }, [pathname]);

  function goBack() {
    // only step back if the previous page was ours; otherwise "back" would leave the shop
    if (internalReferrer && window.history.length > 1) router.back();
    else router.push("/");
  }

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const q = String(new FormData(e.currentTarget).get("q") ?? "").trim();
    router.push(q ? `/?q=${encodeURIComponent(q)}` : "/");
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col items-center px-4 py-16 text-center sm:py-24">
      {/* "4 [tilted logo tile] 4" - the tile has come loose, like the link */}
      <div aria-hidden className="animate-fade-up flex items-center gap-3 text-8xl font-bold tracking-tight text-white sm:text-9xl">
        <span>4</span>
        <span className="flex h-20 w-20 rotate-12 items-center justify-center rounded-3xl bg-linear-to-br from-amber-300 to-amber-500 text-5xl text-on-accent shadow-2xl shadow-accent/30 transition duration-500 hover:rotate-0 sm:h-24 sm:w-24 sm:text-6xl">
          ?
        </span>
        <span>4</span>
      </div>

      <h1 className="animate-fade-up mt-10 text-3xl font-semibold tracking-tight text-white [animation-delay:80ms] sm:text-4xl">
        Oopsie! This link is missing or broken.
      </h1>
      <p className="animate-fade-up mt-4 max-w-md text-slate-200 [animation-delay:140ms]">
        The page you were looking for doesn&apos;t exist or has moved. No worries - let&apos;s get you back to
        whatever you were doing.
      </p>

      <div className="animate-fade-up mt-8 flex flex-wrap justify-center gap-3 [animation-delay:200ms]">
        <button onClick={goBack} className={`${btn.primary} px-6 py-3`}>
          ← Take me back
        </button>
        <Link href="/" className={`${btn.secondary} px-6 py-3`}>
          Browse the shop
        </Link>
      </div>

      <form onSubmit={handleSearch} role="search" className="animate-fade-up mt-10 w-full max-w-sm [animation-delay:260ms]">
        <label htmlFor="not-found-search" className="text-sm text-muted">
          Or search for what you need
        </label>
        <input
          id="not-found-search"
          name="q"
          type="search"
          placeholder="Search laptops, phones, brands…"
          className="mt-2 w-full rounded-full bg-field px-5 py-3 text-sm text-slate-100 ring-1 ring-white/10 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-accent"
        />
      </form>

      {/* dev-only panel: never rendered in a production build */}
      {isDev && (
        <div className="mt-12 w-full rounded-2xl bg-field p-4 text-left font-mono text-xs text-muted border border-dashed border-white/20">
          <p className="mb-2 font-sans font-semibold text-slate-200">Dev info (hidden in production)</p>
          <p>
            path: <span className="text-slate-100">{pathname}</span>
          </p>
          <p>
            linked from:{" "}
            <span className="text-slate-100">{referrer || "(typed URL, bookmark or external site)"}</span>
          </p>
          <p>
            {internalReferrer ? "→ a link inside the shop is broken - fix it on the page above" : "→ not caused by an internal link"}
          </p>
        </div>
      )}
    </main>
  );
}
