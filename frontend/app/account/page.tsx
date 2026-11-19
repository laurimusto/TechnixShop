"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { FormError, ProfileFields, readProfile } from "../auth-widgets";
import { type Profile, useShop } from "../providers";
import { btn } from "../ui";

type Me = { email: string; profile: Profile; hasPassword: boolean; readyForOrdering: boolean };

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
        {me && tab === "security" && <SecurityTab me={me} />}
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

// The four security features come next, one step at a time.
function SecurityTab({ me }: { me: Me }) {
  const items = [
    {
      title: "Change password",
      text: me.hasPassword ? "Update the password you log in with." : "You sign in with Google, so there is no password.",
    },
    { title: "Two-factor authentication", text: "Require a code from an authenticator app when you log in." },
    { title: "Change email", text: "We'll send a confirmation link to the new address." },
    { title: "Delete account", text: "Permanently remove your account and personal data." },
  ];
  return (
    <ul className="flex flex-col gap-3">
      {items.map((item) => (
        <li key={item.title} className="flex items-center justify-between gap-4 rounded-2xl bg-raised p-5 ring-1 ring-white/10">
          <div>
            <h2 className="font-semibold text-white">{item.title}</h2>
            <p className="mt-1 text-sm text-muted">{item.text}</p>
          </div>
          <span className="shrink-0 rounded-full bg-white/5 px-3 py-1 text-xs text-muted">Coming soon</span>
        </li>
      ))}
    </ul>
  );
}
