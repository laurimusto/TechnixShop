"use client";

import Link from "next/link";
import { useState } from "react";
import { AuthCard, Field, FormError } from "../auth-widgets";
import { API_BASE } from "../providers";
import { btn } from "../ui";

export default function ForgotPasswordPage() {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setSubmitting(true);
    try {
      const res = await fetch(`${API_BASE}/forgot-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: form.get("email") }),
      });
      if (res.ok) {
        setSent(true);
      } else {
        // only input errors (e.g. a malformed email) end up here - never "user not found"
        const data = await res.json().catch(() => ({}));
        setError(data.message ?? `Request failed (${res.status})`);
      }
    } catch {
      setError("Could not reach the server");
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <AuthCard title="Check your email">
        {/* same message whether or not the account exists, so this page can't reveal who is registered */}
        <p role="status" className="text-sm text-slate-200">
          If an account exists for that email, we&apos;ve sent a link to reset your password. The link
          expires in 30 minutes.
        </p>
        <Link href="/login" className={`${btn.secondary} mt-6 w-full px-4 py-2.5`}>
          Back to log in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Forgot password?" subtitle="Enter your email and we'll send you a reset link.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field label="Email" name="email" type="email" required autoComplete="email" />
        <FormError message={error} />
        <button type="submit" disabled={submitting} className={`${btn.primary} px-4 py-2.5`}>
          {submitting ? "Sending…" : "Send reset link"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Remembered it?{" "}
        <Link href="/login" className="font-medium text-accent underline-offset-4 hover:underline">
          Log in
        </Link>
      </p>
    </AuthCard>
  );
}
