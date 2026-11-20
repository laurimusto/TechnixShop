"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Field, FormError, GoogleButton, ProfileFields, readProfile } from "../auth-widgets";
import { type Profile, useShop } from "../providers";
import { btn } from "../ui";

type Me = {
  email: string;
  profile: Profile;
  hasPassword: boolean;
  readyForOrdering: boolean;
  twoFactorEnabled: boolean;
};

const TABS = [
  { id: "profile", label: "Profile" },
  { id: "security", label: "Security" },
] as const;
type TabId = (typeof TABS)[number]["id"];

export default function AccountPage() {
  const router = useRouter();
  const { email, authReady, authFetch } = useShop();
  const [me, setMe] = useState<Me | null>(null);
  const [loadError, setLoadError] = useState("");
  const [tab, setTab] = useState<TabId>("profile");

  // wait for the startup refresh: only then does "no email" really mean "not logged in"
  useEffect(() => {
    if (authReady && !email) router.replace("/login");
  }, [authReady, email, router]);

  useEffect(() => {
    if (!email) return;
    authFetch("/me")
      .then(async (res) => {
        if (res.ok) setMe(await res.json());
        else setLoadError(`Could not load your account (${res.status})`);
      })
      .catch(() => setLoadError("Could not reach the server"));
    // authFetch changes whenever the token is refreshed; reloading then is unnecessary
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [email]);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 py-10 sm:py-14">
      <h1 className="text-3xl font-bold tracking-tight text-white">My account</h1>
      {email && <p className="mt-1 text-sm text-muted">{email}</p>}

      {/* tabs pattern: role=tablist/tab/tabpanel + aria-selected let screen readers announce the tabs */}
      <div role="tablist" aria-label="Account sections" className="mt-8 flex gap-2 border-b border-white/10">
        {TABS.map((t) => (
          <button
            key={t.id}
            id={`tab-${t.id}`}
            role="tab"
            aria-selected={tab === t.id}
            aria-controls={`panel-${t.id}`}
            onClick={() => setTab(t.id)}
            className={`-mb-px border-b-2 px-4 py-2.5 text-sm font-medium transition ${
              tab === t.id ? "border-accent text-white" : "border-transparent text-muted hover:text-white"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div id={`panel-${tab}`} role="tabpanel" aria-labelledby={`tab-${tab}`} className="pt-6">
        <FormError message={loadError} />
        {!me && !loadError && <p className="text-sm text-muted">Loading…</p>}
        {me && tab === "profile" && <ProfileTab me={me} onSaved={setMe} />}
        {me && tab === "security" && <SecurityTab me={me} onChange={setMe} />}
      </div>
    </main>
  );
}

function ProfileTab({ me, onSaved }: { me: Me; onSaved: (me: Me) => void }) {
  const { authFetch, notify } = useShop();
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await authFetch("/me", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(readProfile(new FormData(e.currentTarget))),
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        onSaved(data as Me);
        setError("");
        notify("Profile saved", "success");
      } else {
        setError(data.message ?? `Could not save (${res.status})`);
      }
    } catch {
      setError("Could not reach the server");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5 rounded-3xl bg-raised p-6 ring-1 ring-white/10 sm:p-8">
      {!me.readyForOrdering && (
        <p className="rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-amber-100 ring-1 ring-amber-500/30">
          Fill in all fields before your first order - we need them for delivery.
        </p>
      )}
      <ProfileFields defaults={me.profile} />
      <FormError message={error} />
      <button type="submit" disabled={saving} className={`${btn.primary} self-start px-6 py-2.5`}>
        {saving ? "Saving…" : "Save changes"}
      </button>
    </form>
  );
}

type SecurityItemId = "email" | "password" | "two-factor" | "delete";

// Accordion: every setting is a row with a short status; clicking it reveals the form.
// Only one row is open at a time (the state lives here, not in each row). Closing a row unmounts
// its form, so typed passwords and a pending 2FA secret are thrown away too.
function SecurityTab({ me, onChange }: { me: Me; onChange: (me: Me) => void }) {
  const [open, setOpen] = useState<SecurityItemId | null>(null);
  const item = (id: SecurityItemId) => ({
    id,
    open: open === id,
    onToggle: () => setOpen((current) => (current === id ? null : id)),
  });

  return (
    <div className="flex flex-col gap-3">
      <SecurityItem {...item("email")} title="Email" summary={me.email}>
        <ChangeEmailCard hasPassword={me.hasPassword} />
      </SecurityItem>
      <SecurityItem
        {...item("password")}
        title="Password"
        summary={me.hasPassword ? "Change the password you log in with" : "You sign in with Google"}
      >
        <ChangePasswordCard hasPassword={me.hasPassword} />
      </SecurityItem>
      <SecurityItem
        {...item("two-factor")}
        title="Two-factor authentication"
        summary="Codes from an authenticator app when you log in"
        badge={
          <span
            className={`rounded-full px-3 py-1 text-xs ${
              me.twoFactorEnabled ? "bg-emerald-500/15 text-emerald-200" : "bg-white/5 text-muted"
            }`}
          >
            {me.twoFactorEnabled ? "On" : "Off"}
          </span>
        }
      >
        <TwoFactorCard
          enabled={me.twoFactorEnabled}
          onEnabledChange={(twoFactorEnabled) => onChange({ ...me, twoFactorEnabled })}
        />
      </SecurityItem>
      {/* danger zone last, away from everyday settings */}
      <SecurityItem {...item("delete")} title="Delete account" summary="Permanently remove your account and data" danger>
        <DeleteAccountCard hasPassword={me.hasPassword} />
      </SecurityItem>
    </div>
  );
}

// WAI-ARIA accordion pattern: a real <button> inside the heading (so heading navigation still works),
// aria-expanded tells screen readers open/closed, aria-controls links it to the panel it opens.
function SecurityItem({
  id,
  title,
  summary,
  badge,
  danger = false,
  open,
  onToggle,
  children,
}: {
  id: SecurityItemId;
  title: string;
  summary: string;
  badge?: React.ReactNode;
  danger?: boolean;
  open: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}) {
  const buttonId = `security-${id}-button`;
  const panelId = `security-${id}-panel`;

  // The form must stay on screen while the panel slides shut, so "mounted" lags behind "open":
  // it turns on at once when opening, and off only after the closing animation has finished.
  // (Updating state during render when a prop changed is React's recommended alternative to an effect.)
  const [mounted, setMounted] = useState(open);
  const reduceMotion =
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (open && !mounted) setMounted(true);
  // no animation -> no transitionend event will come, so unmount straight away
  if (!open && mounted && reduceMotion) setMounted(false);

  const ring = open
    ? danger
      ? "ring-red-500/60 shadow-lg shadow-red-950/40"
      : "ring-accent/50 shadow-lg shadow-black/30"
    : danger
      ? "ring-red-500/30"
      : "ring-white/10";

  return (
    <section
      className={`rounded-2xl bg-raised ring-1 motion-safe:transition-[box-shadow,transform] motion-safe:duration-300 ${ring} ${
        open ? "" : "motion-safe:hover:-translate-y-0.5"
      }`}
    >
      <h2>
        <button
          type="button"
          id={buttonId}
          aria-expanded={open}
          aria-controls={panelId}
          onClick={onToggle}
          className="group flex w-full items-center justify-between gap-4 rounded-2xl p-5 text-left transition-colors hover:bg-white/[0.03]"
        >
          <span className="min-w-0">
            <span className={`block font-semibold ${danger ? "text-red-200" : "text-white"}`}>{title}</span>
            <span className="mt-0.5 block truncate text-sm text-muted">{summary}</span>
          </span>
          <span className="flex shrink-0 items-center gap-3">
            {badge}
            {/* decorative: aria-expanded already says whether it's open */}
            <svg
              aria-hidden="true"
              viewBox="0 0 20 20"
              fill="currentColor"
              className={`h-5 w-5 transition-colors motion-safe:transition-transform motion-safe:duration-300 ${
                open ? "rotate-180 text-white" : "text-muted group-hover:text-white"
              }`}
            >
              <path
                fillRule="evenodd"
                d="M5.22 7.22a.75.75 0 0 1 1.06 0L10 10.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 8.28a.75.75 0 0 1 0-1.06Z"
                clipRule="evenodd"
              />
            </svg>
          </span>
        </button>
      </h2>
      {/* Height animation: CSS can't transition height to "auto", but a grid row can go from 0fr to 1fr.
          inert while closed: the collapsed content can't be focused or read by screen readers. */}
      <div
        id={panelId}
        role="region"
        aria-labelledby={buttonId}
        inert={!open}
        onTransitionEnd={(e) => {
          // only the panel's own height transition, not the ones bubbling up from the form inside it
          if (!open && e.target === e.currentTarget) setMounted(false);
        }}
        className={`grid motion-safe:transition-[grid-template-rows] motion-safe:duration-300 motion-safe:ease-out ${
          open ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
        }`}
      >
        <div className="overflow-hidden">
          {mounted && (
            // starting: = CSS @starting-style, the values the content fades and slides in from when it appears
            <div
              className={`border-t border-white/10 px-5 pb-5 pt-4 motion-safe:transition motion-safe:duration-300 starting:-translate-y-2 starting:opacity-0 ${
                open ? "translate-y-0 opacity-100" : "-translate-y-2 opacity-0"
              }`}
            >
              {children}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

// Nothing changes here yet: the backend mails a link to the new address and the email switches only when
// it is clicked (confirm-email/page.tsx). So success = "check your inbox", not a new email on screen.
function ChangeEmailCard({ hasPassword }: { hasPassword: boolean }) {
  const { authFetch } = useShop();
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const newEmail = String(form.get("newEmail") ?? "").trim();
    // typo guard only, so it lives in the browser: the link sent to the new address is the real proof
    // (a mistyped address never receives it). Case-insensitive, like email addresses everywhere else here.
    if (newEmail.toLowerCase() !== String(form.get("repeatEmail") ?? "").trim().toLowerCase()) {
      setError("Email addresses do not match.");
      return;
    }
    setSaving(true);
    try {
      const res = await authFetch("/me/email", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ newEmail, password: String(form.get("password") ?? "") }),
      });
      if (res.ok) {
        setError("");
        setSentTo(newEmail);
        return;
      }
      const data = await res.json().catch(() => ({}));
      setError(data.message ?? `Could not change email (${res.status})`);
    } catch {
      setError("Could not reach the server");
    } finally {
      setSaving(false);
    }
  }

  // the row header (SecurityItem) shows the title; these cards are only the panel content
  return (
    <>
      {!hasPassword ? (
        <p className="text-sm text-muted">You sign in with Google, so your email comes from your Google account.</p>
      ) : sentTo ? (
        // role=status: screen readers announce the confirmation without moving focus
        <div role="status" className="flex flex-col gap-3">
          <p className="text-sm text-slate-200">
            We sent a confirmation link to <span className="font-medium text-white">{sentTo}</span>. Your email changes
            once you click it (the link works for 1 hour).
          </p>
          <button type="button" onClick={() => setSentTo(null)} className={`${btn.ghost} self-start px-4 py-2`}>
            Use a different address
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <p className="text-sm text-muted">We&apos;ll send a confirmation link to the new address.</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="New email" name="newEmail" type="email" autoComplete="email" maxLength={50} required />
            {/* pasting stays allowed: blocking it hurts password-manager and assistive-tech users */}
            <Field label="Confirm new email" name="repeatEmail" type="email" autoComplete="email" maxLength={50} required />
          </div>
          <Field label="Current password" name="password" type="password" autoComplete="current-password" required />
          <FormError message={error} />
          <button type="submit" disabled={saving} className={`${btn.primary} self-start px-6 py-2.5`}>
            {saving ? "Sending…" : "Send confirmation link"}
          </button>
        </form>
      )}
    </>
  );
}

// The backend ends every session after a password change (this one too), so on success we log out locally
// and send the user to /login to sign in with the new password.
function ChangePasswordCard({ hasPassword }: { hasPassword: boolean }) {
  const router = useRouter();
  const { authFetch, notify, logout } = useShop();
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const body = {
      currentPassword: String(form.get("currentPassword") ?? ""),
      newPassword: String(form.get("newPassword") ?? ""),
      repeatPassword: String(form.get("repeatPassword") ?? ""),
    };
    // quick check in the browser for a faster message; the server checks again (it is the real guard)
    if (body.newPassword !== body.repeatPassword) {
      setError("Passwords do not match.");
      return;
    }
    setSaving(true);
    try {
      const res = await authFetch("/me/password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (res.ok) {
        await logout();
        notify("Password changed - please log in again", "success");
        router.replace("/login");
        return;
      }
      const data = await res.json().catch(() => ({}));
      setError(data.message ?? `Could not change password (${res.status})`);
    } catch {
      setError("Could not reach the server");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      {!hasPassword ? (
        <p className="text-sm text-muted">You sign in with Google, so there is no password to change.</p>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <p className="text-sm text-muted">You will be logged out on all devices afterwards.</p>
          {/* autoComplete tokens tell password managers which field is the old and which the new password */}
          <Field label="Current password" name="currentPassword" type="password" autoComplete="current-password" required />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="New password" name="newPassword" type="password" autoComplete="new-password" minLength={3} required />
            <Field label="Repeat new password" name="repeatPassword" type="password" autoComplete="new-password" minLength={3} required />
          </div>
          <FormError message={error} />
          <button type="submit" disabled={saving} className={`${btn.primary} self-start px-6 py-2.5`}>
            {saving ? "Saving…" : "Change password"}
          </button>
        </form>
      )}
    </>
  );
}

type TwoFactorSetup = { secret: string; otpauthUri: string };

// Off -> "Set up" fetches a new secret -> scan QR (or type the key) -> confirm with a code -> On.
// On  -> turning it off also needs a current code, so a stolen session alone can't remove 2FA.
function TwoFactorCard({ enabled, onEnabledChange }: { enabled: boolean; onEnabledChange: (enabled: boolean) => void }) {
  const { authFetch, notify } = useShop();
  const [setup, setSetup] = useState<TwoFactorSetup | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const setupHeading = useRef<HTMLHeadingElement>(null);

  // the QR code appears below the button: move focus there so keyboard/screen reader users follow
  useEffect(() => {
    if (setup) setupHeading.current?.focus();
  }, [setup]);

  // all three calls share error handling; returns the parsed body on success, null on failure
  async function call(path: string, body?: object) {
    setBusy(true);
    try {
      const res = await authFetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: body ? JSON.stringify(body) : undefined,
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(data.message ?? `Request failed (${res.status})`);
        return null;
      }
      setError("");
      return data;
    } catch {
      setError("Could not reach the server");
      return null;
    } finally {
      setBusy(false);
    }
  }

  function codeFrom(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    return String(new FormData(e.currentTarget).get("code") ?? "").replace(/\s/g, "");
  }

  async function startSetup() {
    const data = await call("/me/2fa/setup");
    if (data) setSetup(data as TwoFactorSetup);
  }

  async function confirmEnable(e: React.FormEvent<HTMLFormElement>) {
    if (await call("/me/2fa/enable", { code: codeFrom(e) })) {
      setSetup(null); // the secret leaves the screen (and memory) as soon as it's no longer needed
      onEnabledChange(true);
      notify("Two-factor authentication is on", "success");
    }
  }

  async function disable(e: React.FormEvent<HTMLFormElement>) {
    if (await call("/me/2fa/disable", { code: codeFrom(e) })) {
      onEnabledChange(false);
      notify("Two-factor authentication is off");
    }
  }

  const codeField = (
    <Field
      label="6-digit code"
      name="code"
      required
      inputMode="numeric"
      autoComplete="one-time-code"
      pattern="\d{6}"
      maxLength={6}
      title="6 digits"
    />
  );

  // the On/Off badge lives in the row header (SecurityTab), so it's visible while the row is closed
  return (
    <>
      {enabled ? (
        <form onSubmit={disable} className="flex flex-col gap-4">
          <p className="text-sm text-muted">
            Logging in asks for a code from your authenticator app. To turn this off, enter a current code.
          </p>
          <div className="max-w-xs">{codeField}</div>
          <FormError message={error} />
          <button type="submit" disabled={busy} className={`${btn.primary} self-start px-6 py-2.5`}>
            {busy ? "Checking…" : "Turn off"}
          </button>
        </form>
      ) : !setup ? (
        <div className="flex flex-col gap-4">
          <p className="text-sm text-muted">
            Require a code from an authenticator app (Google Authenticator, Authy, …) when you log in.
          </p>
          <FormError message={error} />
          <button type="button" onClick={startSetup} disabled={busy} className={`${btn.primary} self-start px-6 py-2.5`}>
            {busy ? "Preparing…" : "Set up"}
          </button>
        </div>
      ) : (
        <form onSubmit={confirmEnable} className="flex flex-col gap-4">
          <h3 ref={setupHeading} tabIndex={-1} className="text-sm font-medium text-slate-200 outline-none">
            1. Scan this QR code with your authenticator app
          </h3>
          {/* white background + margin: dark-mode QR codes don't scan reliably */}
          <div className="self-start rounded-xl bg-white p-3">
            <QRCodeSVG value={setup.otpauthUri} size={176} marginSize={1} title="QR code for your authenticator app" />
          </div>
          <p className="text-sm text-muted">
            Can&apos;t scan it? Enter this key instead:{" "}
            <code className="select-all break-all rounded bg-white/5 px-2 py-1 font-mono text-slate-100">
              {setup.secret.match(/.{1,4}/g)?.join(" ")}
            </code>
          </p>
          <h3 className="text-sm font-medium text-slate-200">2. Enter the code the app shows</h3>
          <div className="max-w-xs">{codeField}</div>
          <FormError message={error} />
          <div className="flex gap-3">
            <button type="submit" disabled={busy} className={`${btn.primary} px-6 py-2.5`}>
              {busy ? "Checking…" : "Turn on"}
            </button>
            <button
              type="button"
              onClick={() => {
                setSetup(null);
                setError("");
              }}
              className="text-sm text-muted underline-offset-4 hover:text-white hover:underline"
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </>
  );
}

const dangerBtn =
  "inline-flex items-center justify-center rounded-full bg-red-600 px-6 py-2.5 text-sm font-semibold text-white transition enabled:hover:bg-red-500 disabled:opacity-40";

// Easter egg for reviewers: "Are you sure?" plus three increasingly dramatic gates.
// The last one needs the word DELETE typed - the one gate a stray double-click can't pass.
// Purely front-end theatre: the real protection is the password / Google re-authentication the server checks.
const DELETE_GATES = [
  { title: "Are you sure?", text: "This permanently deletes your account.", yes: "Yes, I'm sure" },
  { title: "Really sure?", text: "Your cart has started to cry a little.", yes: "Yes, really" },
  { title: "Really, really sure?", text: "The laptops are pressing their screens against the window. The phones lost signal from sadness.", yes: "I have no heart" },
  { title: "Fine. Final gate.", text: "Type DELETE to prove you are a human and not a cat walking on the keyboard.", yes: "Continue", typeToConfirm: "DELETE" },
];

// step 0 = closed, 1..DELETE_GATES.length = the gates, after that = re-authentication.
// Re-authentication: password accounts type their password, Google-only accounts sign in with Google again.
function DeleteAccountCard({ hasPassword }: { hasPassword: boolean }) {
  const router = useRouter();
  const { authFetch, notify, logout } = useShop();
  const [step, setStep] = useState(0);
  const [typed, setTyped] = useState("");
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(false);

  const gate = step >= 1 && step <= DELETE_GATES.length ? DELETE_GATES[step - 1] : null;
  const reauthenticating = step > DELETE_GATES.length;

  // the button that was clicked disappears with each gate; move focus to the new gate instead of losing it
  const gateHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    gateHeading.current?.focus();
  }, [step]);

  function cancel() {
    setStep(0);
    setTyped("");
    setError("");
  }

  async function deleteAccount(proof: { password?: string; googleIdToken?: string }) {
    setDeleting(true);
    try {
      const res = await authFetch("/me", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(proof),
      });
      if (res.ok) {
        // the account and its refresh tokens are gone; logout() clears the in-memory access token.
        // /login, because the account page itself redirects there as soon as the session is cleared
        await logout();
        notify("Your account has been deleted", "success");
        router.replace("/login");
        return;
      }
      const data = await res.json().catch(() => ({}));
      setError(data.message ?? `Could not delete account (${res.status})`);
    } catch {
      setError("Could not reach the server");
    } finally {
      setDeleting(false);
    }
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    deleteAccount({ password: String(new FormData(e.currentTarget).get("password") ?? "") });
  }

  return (
    <>
      <p className="text-sm text-muted">
        Permanently removes your account and personal data. This cannot be undone.
      </p>

      {step === 0 && (
        <button type="button" onClick={() => setStep(1)} className={`${dangerBtn} mt-4`}>
          Delete account
        </button>
      )}

      {gate && (
        <div className="mt-4 flex flex-col gap-3 rounded-xl bg-red-500/5 p-4 ring-1 ring-red-500/20">
          <p className="text-xs font-medium uppercase tracking-wide text-red-300">
            Step {step} of {DELETE_GATES.length + 1}
          </p>
          {/* tabIndex=-1: focusable from code (not by Tab), so a screen reader reads each new gate's title */}
          <h3 ref={gateHeading} tabIndex={-1} className="font-semibold text-white focus:outline-none">{gate.title}</h3>
          <p className="text-sm text-slate-200">{gate.text}</p>
          {gate.typeToConfirm && (
            <Field label={`Type ${gate.typeToConfirm}`} value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" spellCheck={false} />
          )}
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              disabled={!!gate.typeToConfirm && typed !== gate.typeToConfirm}
              onClick={() => setStep(step + 1)}
              className={dangerBtn}
            >
              {gate.yes}
            </button>
            <button type="button" onClick={cancel} className={`${btn.secondary} px-6 py-2.5 text-sm`}>
              No, keep my account
            </button>
          </div>
        </div>
      )}

      {reauthenticating && (
        <div className="mt-4 flex flex-col gap-4">
          <p className="text-xs font-medium uppercase tracking-wide text-red-300">
            Step {step} of {DELETE_GATES.length + 1} - for real this time
          </p>
          {hasPassword ? (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <Field label="Confirm with your password" name="password" type="password" autoComplete="current-password" required autoFocus />
              <FormError message={error} />
              <div className="flex flex-wrap gap-3">
                <button type="submit" disabled={deleting} className={dangerBtn}>
                  {deleting ? "Deleting…" : "Delete permanently"}
                </button>
                <button type="button" onClick={cancel} className={`${btn.secondary} px-6 py-2.5 text-sm`}>
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              <p className="text-sm text-slate-200">Sign in with Google again to confirm. Your account is deleted right after.</p>
              <GoogleButton onCredential={(googleIdToken) => deleteAccount({ googleIdToken })} />
              <FormError message={error} />
              <button type="button" onClick={cancel} className={`${btn.secondary} self-start px-6 py-2.5 text-sm`}>
                Cancel
              </button>
            </>
          )}
        </div>
      )}
    </>
  );
}
