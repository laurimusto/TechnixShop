import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Link from "next/link";
import "./globals.css";
import Header from "./header";
import { Providers } from "./providers";
import { Logo } from "./ui";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const FOOTER_LINKS = [
  {
    title: "Shop",
    links: [
      { label: "All products", href: "/" },
      { label: "Create account", href: "/register" },
    ],
  },
  {
    title: "Help",
    links: [
      { label: "Shipping & delivery", href: "/shipping" },
      { label: "Returns & refunds", href: "/returns" },
      { label: "Warranty", href: "/warranty" },
      { label: "FAQ", href: "/faq" },
      { label: "Contact us", href: "/contact" },
    ],
  },
  {
    title: "Legal",
    links: [
      { label: "Privacy policy", href: "/privacy" },
      { label: "Terms of service", href: "/terms" },
      { label: "Cookie policy", href: "/cookies" },
    ],
  },
];

export const metadata: Metadata = {
  title: "Technix web shop",
  description: "Get all your Technix here with best prices!",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <Providers>
          <Header />
          <div className="flex-1">{children}</div>
          <footer className="mt-16 border-t border-white/10 bg-chrome text-sm text-muted">
            <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-[1.4fr_repeat(3,1fr)]">
              <div className="space-y-3">
                <Logo />
                <p className="text-balance">Trusted tech, honest prices.</p>
                <p className="text-xs">Free delivery over €50 · 30-day returns</p>
              </div>
              {FOOTER_LINKS.map((group) => (
                <nav key={group.title} aria-label={group.title}>
                  <h2 className="text-xs font-semibold uppercase tracking-wider text-white">{group.title}</h2>
                  <ul className="mt-4 space-y-2.5">
                    {group.links.map((link) => (
                      <li key={link.href}>
                        <Link href={link.href} className="transition hover:text-white">
                          {link.label}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </nav>
              ))}
            </div>
            <div className="border-t border-white/10">
              <div className="mx-auto flex max-w-7xl flex-wrap justify-between gap-2 px-4 py-5 text-xs sm:px-6">
                <span>© {new Date().getFullYear()} Technix. All rights reserved.</span>
                <span>
                  Prices include VAT ·{" "}
                  <Link href="/api" className="transition hover:text-white">
                    API test console
                  </Link>
                </span>
              </div>
            </div>
          </footer>
        </Providers>
      </body>
    </html>
  );
}
