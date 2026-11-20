"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AuthCard, Field, FormError, GoogleButton, OrDivider } from "../auth-widgets";
import { useShop } from "../providers";
import { btn } from "../ui";

export default function LoginPage() {
  const router = useRouter();
  const { email, login, googleLogin, twoFactorPending } = useShop();
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // once a session exists (after login here, or restored from the refresh cookie), leave this page.
  // replace (not push) so the Back button doesn't return to the login form.
  useEffect(() => {
    if (email) router.replace("/");
  }, [email, router]);

  // password was right but the account has 2FA: second step on the same page
  if (twoFactorPending) return <TwoFactorStep />;

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setSubmitting(true);
    setError((await login(String(form.get("email")), String(form.get("password")))) ?? "");
    setSubmitting(false);
  }

  async function handleGoogle(credential: string) {
    setError((await googleLogin(credential)) ?? "");
  }

  return (
    <AuthCard title="Log in" subtitle="Welcome back to Technix.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field label="Email" name="email" type="email" required autoComplete="email" />
        <div className="flex flex-col gap-1.5">
          <Field label="Password" name="password" type="password" required autoComplete="current-password" />
          <Link
            href="/forgot-password"
            className="self-end text-sm text-muted underline-offset-4 hover:text-white hover:underline"
          >
            Forgot password?
          </Link>
        </div>
        <FormError message={error} />
        <button type="submit" disabled={submitting} className={`${btn.primary} px-4 py-2.5`}>
          {submitting ? "Logging in…" : "Log in"}
        </button>
        <OrDivider />
        <GoogleButton onCredential={handleGoogle} />
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        No account yet?{" "}
        <Link href="/register" className="font-medium text-accent underline-offset-4 hover:underline">
          Register
        </Link>
      </p>
    </AuthCard>
  );
}

function TwoFactorStep() {
  const { verifyTwoFactor, cancelTwoFactor } = useShop();
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const code = String(new FormData(e.currentTarget).get("code")).replace(/\s/g, "");
    setSubmitting(true);
    setError((await verifyTwoFactor(code)) ?? "");
    setSubmitting(false);
  }

  return (
    <AuthCard title="Two-factor authentication" subtitle="Enter the 6-digit code from your authenticator app.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {/* numeric keyboard on phones; "one-time-code" lets browsers/OS offer the code */}
        <Field
          label="Code"
          name="code"
          required
          autoFocus
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="\d{6}"
          maxLength={6}
          title="6 digits"
        />
        <FormError message={error} />
        <button type="submit" disabled={submitting} className={`${btn.primary} px-4 py-2.5`}>
          {submitting ? "Checking…" : "Verify"}
        </button>
        <button
          type="button"
          onClick={cancelTwoFactor}
          className="text-sm text-muted underline-offset-4 hover:text-white hover:underline"
        >
          Back to log in
        </button>
      </form>
    </AuthCard>
  );
}
