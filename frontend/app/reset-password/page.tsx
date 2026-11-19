"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthCard, Field, FormError } from "../auth-widgets";
import { API_BASE, useShop } from "../providers";
import { btn } from "../ui";

// useSearchParams() is only known in the browser, so Next.js requires a <Suspense> boundary
// around it; the page is pre-rendered with the fallback and the form appears on the client.
export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<AuthCard title="Choose a new password">{null}</AuthCard>}>
      <ResetPasswordForm />
    </Suspense>
  );
}

function ResetPasswordForm() {
  const router = useRouter();
  const { notify } = useShop();
  const token = useSearchParams().get("token");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (!token) {
    return (
      <AuthCard title="Invalid reset link">
        <p className="text-sm text-slate-200">This reset link is invalid or has expired.</p>
        <Link href="/forgot-password" className={`${btn.primary} mt-6 w-full px-4 py-2.5`}>
          Request a new link
        </Link>
      </AuthCard>
    );
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const newPassword = String(form.get("newPassword"));
    const repeatPassword = String(form.get("repeatPassword"));
    if (newPassword !== repeatPassword) return setError("Passwords do not match.");

    setSubmitting(true);
    try {
      const res = await fetch(`${API_BASE}/reset-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword, repeatPassword }),
      });
      if (res.ok) {
        // the reset ended every session, so the user logs in again with the new password
        notify("Password changed - please log in", "success");
        router.push("/login");
        return;
      }
      const data = await res.json().catch(() => ({}));
      setError(data.message ?? `Request failed (${res.status})`);
    } catch {
      setError("Could not reach the server");
    }
    setSubmitting(false);
  }

  return (
    <AuthCard title="Choose a new password" subtitle="After this, you'll be logged out everywhere.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field label="New password" name="newPassword" type="password" required autoComplete="new-password" />
        <Field label="Repeat new password" name="repeatPassword" type="password" required autoComplete="new-password" />
        <FormError message={error} />
        <button type="submit" disabled={submitting} className={`${btn.primary} px-4 py-2.5`}>
          {submitting ? "Saving…" : "Change password"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Link expired?{" "}
        <Link href="/forgot-password" className="font-medium text-accent underline-offset-4 hover:underline">
          Request a new one
        </Link>
      </p>
    </AuthCard>
  );
}
