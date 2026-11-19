"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AuthCard, Captcha, Field, FormError, GoogleButton, OrDivider, ProfileFields, readProfile } from "../auth-widgets";
import { useShop } from "../providers";
import { btn } from "../ui";

export default function RegisterPage() {
  const router = useRouter();
  const { email, register, googleLogin } = useShop();
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  // changing this key remounts <Captcha>, giving a fresh unticked checkbox
  const [captchaKey, setCaptchaKey] = useState(0);

  useEffect(() => {
    if (email) router.replace("/");
  }, [email, router]);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const password = String(form.get("password"));
    const repeatPassword = String(form.get("repeatPassword"));

    // quick checks in the browser for fast feedback; the backend still validates everything
    if (password !== repeatPassword) return setError("Passwords do not match.");
    if (!captchaToken) return setError("Please tick the CAPTCHA box first.");

    setSubmitting(true);
    setError(
      (await register(String(form.get("email")), password, repeatPassword, captchaToken, readProfile(form))) ?? "",
    );
    setSubmitting(false);

    // Google accepts each CAPTCHA token only once, so a retry needs a fresh tick
    setCaptchaToken(null);
    setCaptchaKey((key) => key + 1);
  }

  async function handleGoogle(credential: string) {
    setError((await googleLogin(credential)) ?? "");
  }

  return (
    <AuthCard title="Create account" subtitle="Register with your email, or continue with Google.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field label="Email" name="email" type="email" required autoComplete="email" />
        <Field label="Password" name="password" type="password" required autoComplete="new-password" />
        <Field label="Repeat password" name="repeatPassword" type="password" required autoComplete="new-password" />
        {/* fieldset + legend tells screen readers these inputs belong together */}
        <fieldset className="flex flex-col gap-3 rounded-2xl p-4 ring-1 ring-white/10">
          <legend className="px-1 text-sm font-medium text-slate-200">Your details (optional)</legend>
          <p className="text-xs text-muted">Needed only when you order. You can add or change them later in My account.</p>
          <ProfileFields />
        </fieldset>
        <Captcha key={captchaKey} onToken={setCaptchaToken} />
        <FormError message={error} />
        <button type="submit" disabled={submitting} className={`${btn.primary} px-4 py-2.5`}>
          {submitting ? "Creating account…" : "Create account"}
        </button>
        <OrDivider />
        <GoogleButton onCredential={handleGoogle} />
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-accent underline-offset-4 hover:underline">
          Log in
        </Link>
      </p>
    </AuthCard>
  );
}
