import type { Metadata } from "next";
import NotFoundContent from "./not-found-content";

// Next.js renders this for every unknown URL (and whenever notFound() is called),
// inside the root layout, so header and footer stay in place. Responds with HTTP 404.
// Server component only so it can set the page title (WCAG 2.4.2: pages need a title).
export const metadata: Metadata = {
  title: "Page not found · Technix",
};

export default function NotFound() {
  return <NotFoundContent />;
}
