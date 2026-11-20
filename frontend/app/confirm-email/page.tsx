"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, FormError } from "../auth-widgets";
import { API_BASE, refreshSession, useShop } from "../providers";
import { btn } from "../ui";

// Same Suspense reason as reset-password/page.tsx: useSearchParams() only exists in the browser.
export default function ConfirmEmailPage() {
  return (
    <Suspense fallback={<AuthCard title="Confirm your new email">{null}</AuthCard>}>
      <ConfirmEmail />
    </Suspense>
  );
}

// Confirming needs a click, not just opening the page: mail scanners "open" links to check them,
// and that must not change anyone's email.
function ConfirmEmail() {
  const { email, setSession } = useShop();
  const token = useSearchParams().get("token");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  if (!token) {
    return (
      <AuthCard title="Invalid confirmation link">
        <p className="text-sm text-slate-200">This confirmation link is invalid or has expired.</p>
      </AuthCard>
    );
  }

  async function confirm() {
    setSubmitting(true);
    try {
      const res = await fetch(`${API_BASE}/confirm-email`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setError(data.message ?? `Request failed (${res.status})`);
        setSubmitting(false);
        return;
      }
      // logged in in this browser? the session is still valid, only the email shown in the header is stale.
      // /refresh answers with the email from the database, i.e. the new one.
      if (email) {
        const auth = await refreshSession();
        if (auth) setSession(auth.token, auth.email);
      }
      setDone(true);
    } catch {
      setError("Could not reach the server");
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <AuthCard title="Email changed">
        <p role="status" className="text-sm text-slate-200">
          Your account now uses the new address. Use it next time you log in.
        </p>
        <Link href={email ? "/account" : "/login"} className={`${btn.primary} mt-6 w-full px-4 py-2.5`}>
          {email ? "Back to my account" : "Log in"}
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Confirm your new email" subtitle="Click below to make this your account's email address.">
      <div className="flex flex-col gap-4">
        <FormError message={error} />
        <button type="button" onClick={confirm} disabled={submitting} className={`${btn.primary} px-4 py-2.5`}>
          {submitting ? "Confirming…" : "Confirm email change"}
        </button>
      </div>
    </AuthCard>
  );
}
