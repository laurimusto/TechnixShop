"use client";

import Script from "next/script";
import { useEffect, useRef } from "react";
import type { Profile } from "./providers";

// Shared pieces for the login / register / forgot / reset pages.

const RECAPTCHA_SITE_KEY = process.env.NEXT_PUBLIC_RECAPTCHA_SITE_KEY ?? "";
const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";

// Types for the two Google scripts, which attach themselves to `window`.
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (params: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (
            container: HTMLElement,
            options: { theme?: string; size?: string; text?: string; shape?: string },
          ) => void;
        };
      };
    };
    grecaptcha?: {
      ready: (cb: () => void) => void;
      render: (
        container: HTMLElement,
        params: {
          sitekey: string;
          theme?: string;
          callback: (token: string) => void;
          "expired-callback": () => void;
        },
      ) => number;
      reset: (widgetId?: number) => void;
    };
  }
}

export const inputClass =
  "w-full rounded-xl bg-field px-3 py-2.5 text-sm text-slate-100 ring-1 ring-white/10 placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-accent";

// Centered card every auth page sits in.
export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <main className="mx-auto flex w-full max-w-md flex-col px-4 py-12 sm:py-16">
      <div className="rounded-3xl bg-raised p-6 shadow-2xl shadow-black/40 ring-1 ring-white/10 sm:p-8">
        <h1 className="text-2xl font-bold tracking-tight text-white">{title}</h1>
        {subtitle && <p className="mt-1.5 text-sm text-muted">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
    </main>
  );
}

// A visible <label> wrapping its input: screen readers announce it and clicking it focuses the field.
export function Field({ label, ...inputProps }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="font-medium text-slate-200">{label}</span>
      <input {...inputProps} className={inputClass} />
    </label>
  );
}

// The profile inputs, shared by register/page.tsx and account/page.tsx.
// autoComplete tokens let the browser fill them in for the user.
export function ProfileFields({ defaults }: { defaults?: Profile }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="First name" name="firstName" autoComplete="given-name" maxLength={50} defaultValue={defaults?.firstName ?? ""} />
      <Field label="Last name" name="lastName" autoComplete="family-name" maxLength={50} defaultValue={defaults?.lastName ?? ""} />
      <Field label="Phone" name="phone" type="tel" autoComplete="tel" maxLength={20} defaultValue={defaults?.phone ?? ""} />
      <Field label="Street address" name="street" autoComplete="street-address" maxLength={100} defaultValue={defaults?.street ?? ""} />
      <Field label="Postal code" name="postalCode" autoComplete="postal-code" maxLength={20} defaultValue={defaults?.postalCode ?? ""} />
      <Field label="City" name="city" autoComplete="address-level2" maxLength={50} defaultValue={defaults?.city ?? ""} />
      <Field label="Country" name="country" autoComplete="country-name" maxLength={56} defaultValue={defaults?.country ?? ""} />
    </div>
  );
}

// Reads the ProfileFields values out of a submitted form. Empty fields become null.
export function readProfile(form: FormData): Profile {
  const value = (name: string) => String(form.get(name) ?? "").trim() || null;
  return {
    firstName: value("firstName"),
    lastName: value("lastName"),
    phone: value("phone"),
    street: value("street"),
    postalCode: value("postalCode"),
    city: value("city"),
    country: value("country"),
  };
}

// role="alert" makes screen readers read the error out as soon as it appears.
export function FormError({ message }: { message: string }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-200 ring-1 ring-red-500/30">
      {message}
    </p>
  );
}

export function OrDivider() {
  return (
    <div className="flex items-center gap-3 text-xs text-muted" aria-hidden>
      <span className="h-px flex-1 bg-white/10" />
      or
      <span className="h-px flex-1 bg-white/10" />
    </div>
  );
}

// Google's library keeps ONE configuration per page: initialize() is meant to run once, and every
// extra call logs "initialize() is called multiple times" and replaces the previous callback.
// So it runs once here, with a callback that forwards to whichever button is mounted right now.
let googleInitialized = false;
let activeGoogleReceiver: ((credential: string) => void) | null = null;

type GoogleButtonOptions = Parameters<NonNullable<Window["google"]>["accounts"]["id"]["renderButton"]>[1];

export function renderGoogleButton(container: HTMLElement, options: GoogleButtonOptions) {
  const google = window.google;
  if (!google) return;
  if (!googleInitialized) {
    google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: (response) => activeGoogleReceiver?.(response.credential),
    });
    googleInitialized = true;
  }
  google.accounts.id.renderButton(container, options);
}

// Makes the calling component the receiver of Google credentials while it is mounted.
export function useGoogleCredential(onCredential: (credential: string) => void) {
  // a ref so the receiver always calls the latest prop without re-registering
  const callbackRef = useRef(onCredential);
  useEffect(() => {
    callbackRef.current = onCredential;
  });
  useEffect(() => {
    const receiver = (credential: string) => callbackRef.current(credential);
    activeGoogleReceiver = receiver;
    return () => {
      // only clear it if a newer button hasn't already taken over
      if (activeGoogleReceiver === receiver) activeGoogleReceiver = null;
    };
  }, []);
}

// Google's own button. On click Google hands us a signed ID token ("credential"),
// which the backend verifies before issuing our own JWTs.
export function GoogleButton({ onCredential }: { onCredential: (credential: string) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  useGoogleCredential(onCredential);

  function render() {
    if (!containerRef.current) return;
    renderGoogleButton(containerRef.current, {
      theme: "filled_black",
      size: "large",
      shape: "pill",
      text: "continue_with",
    });
  }

  return (
    <>
      {/* onReady runs once the script is loaded, and again each time this component mounts */}
      <Script src="https://accounts.google.com/gsi/client" onReady={render} />
      <div ref={containerRef} className="flex min-h-10 justify-center" />
    </>
  );
}

// reCAPTCHA checkbox. To reset it after a submit, the parent changes this component's `key`:
// React then throws the old widget away and mounts a fresh, unticked one.
export function Captcha({ onToken }: { onToken: (token: string | null) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<number | null>(null);
  const onTokenRef = useRef(onToken);
  useEffect(() => {
    onTokenRef.current = onToken;
  });

  function render() {
    const grecaptcha = window.grecaptcha;
    if (!grecaptcha) return;
    grecaptcha.ready(() => {
      if (!containerRef.current || widgetIdRef.current !== null) return;
      widgetIdRef.current = grecaptcha.render(containerRef.current, {
        sitekey: RECAPTCHA_SITE_KEY,
        theme: "dark",
        callback: (token) => onTokenRef.current(token),
        "expired-callback": () => onTokenRef.current(null),
      });
    });
  }

  return (
    <>
      <Script src="https://www.google.com/recaptcha/api.js?render=explicit" onReady={render} />
      <div ref={containerRef} className="min-h-19.5" />
    </>
  );
}
