"use client";

import { useId, useState } from "react";

// Shared UI building blocks, so every page uses the same buttons, logo and product images.

export const euro = new Intl.NumberFormat("en-IE", { style: "currency", currency: "EUR" });

// One button style per role; add size (padding/text) at the call site.
export const btn = {
  primary:
    "inline-flex items-center justify-center gap-2 rounded-full bg-accent font-semibold text-on-accent transition enabled:hover:bg-accent-hover enabled:active:scale-[0.98] disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-muted",
  secondary:
    "inline-flex items-center justify-center gap-2 rounded-full bg-white/10 font-medium text-slate-100 ring-1 ring-white/15 transition enabled:hover:bg-white/20 enabled:hover:text-white enabled:active:scale-[0.98] disabled:opacity-40",
  ghost:
    "inline-flex items-center justify-center gap-2 rounded-full text-slate-100 transition hover:bg-white/15 hover:text-white",
};

// Logo mark: a rounded amber tile with a "T" whose stem ends in a detached node -
// part circuit trace, part the amber full stop from the wordmark.
export function Logo({ size = "md" }: { size?: "md" | "lg" }) {
  const mark = size === "lg" ? 36 : 30;
  // the logo appears in both header and footer; a unique id keeps the HTML valid (WCAG 2.1 4.1.1)
  const gradientId = useId();
  return (
    <span className="inline-flex items-center gap-2.5">
      <svg width={mark} height={mark} viewBox="0 0 32 32" aria-hidden>
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#fcd34d" />
            <stop offset="1" stopColor="#f59e0b" />
          </linearGradient>
        </defs>
        <rect width="32" height="32" rx="9" fill={`url(#${gradientId})`} />
        <path d="M9.5 10.5h13M16 10.5v8.5" stroke="#1f2532" strokeWidth="3.2" strokeLinecap="round" />
        <circle cx="16" cy="24" r="2.3" fill="#1f2532" />
      </svg>
      <span className={`font-bold tracking-tight text-white ${size === "lg" ? "text-2xl" : "text-xl"}`}>
        Technix<span className="text-accent">.</span>
      </span>
    </span>
  );
}

// Product photo on a light "studio" backdrop so it stands out on the dark theme.
// Falls back to a "No image" box when there is no URL or the image fails to load.
export function ProductImage({ src, alt, className }: { src: string | null; alt: string; className: string }) {
  const [failed, setFailed] = useState(false);
  return (
    <span
      className={`flex items-center justify-center overflow-hidden bg-linear-to-b from-slate-100 to-slate-300 ${className}`}
    >
      {!src || failed ? (
        <span className="text-xs text-slate-600">No image</span>
      ) : (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={src}
          alt={alt}
          loading="lazy"
          onError={() => setFailed(true)}
          className="h-full w-full object-contain p-[8%] transition duration-500 group-hover:scale-105"
        />
      )}
    </span>
  );
}
