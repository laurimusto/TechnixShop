import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Link from "next/link";
import "./globals.css";
import Header from "./header";
import { Providers } from "./providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

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
          <footer className="bg-slate-900 text-slate-300 text-sm">
            <div className="max-w-6xl mx-auto px-6 py-6 flex flex-wrap justify-between gap-2">
              <span>© {new Date().getFullYear()} Technix web shop</span>
              <Link href="/api" className="underline">
                API test console
              </Link>
            </div>
          </footer>
        </Providers>
      </body>
    </html>
  );
}
