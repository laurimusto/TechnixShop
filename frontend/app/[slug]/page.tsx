import type { Metadata } from "next";
import Link from "next/link";
import { INFO_PAGES } from "./content";

// One route renders all info pages (/privacy, /terms, /faq ...) from content.ts.
// dynamicParams = false: any slug not listed below is a 404 instead of an empty page.
export const dynamicParams = false;

export function generateStaticParams() {
  return Object.keys(INFO_PAGES).map((slug) => ({ slug }));
}

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  return { title: `${INFO_PAGES[slug].title} · Technix` };
}

export default async function InfoPage({ params }: Props) {
  const { slug } = await params;
  const page = INFO_PAGES[slug];

  return (
    <main className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <Link href="/" className="text-sm text-muted hover:text-white">
        ← Back to shop
      </Link>
      <article className="animate-fade-up mt-4 rounded-3xl bg-surface p-6 ring-1 ring-white/10 sm:p-10">
        <h1 className="text-3xl font-semibold tracking-tight text-white">{page.title}</h1>
        <p className="mt-3 text-lg text-slate-200">{page.intro}</p>

        <div className="mt-8 space-y-3">
          {page.faq
            ? page.sections.map((section) => (
                <details key={section.heading} className="group rounded-2xl bg-white/5 ring-1 ring-white/10">
                  <summary className="flex list-none items-center [&::-webkit-details-marker]:hidden justify-between gap-4 px-5 py-4 font-medium text-white">
                    {section.heading}
                    <span aria-hidden className="text-muted transition group-open:rotate-45">
                      +
                    </span>
                  </summary>
                  <p className="px-5 pb-5 text-slate-200">{section.body}</p>
                </details>
              ))
            : page.sections.map((section) => (
                <section key={section.heading} className="pt-3">
                  <h2 className="text-lg font-semibold text-white">{section.heading}</h2>
                  <p className="mt-1 leading-relaxed text-slate-200">{section.body}</p>
                </section>
              ))}
        </div>
      </article>
    </main>
  );
}
