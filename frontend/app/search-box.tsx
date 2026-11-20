"use client";

import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { API_BASE } from "./providers";

// one row per distinct product name (the backend groups variants that share a name)
type Suggestion = { name: string; brandName: string };

const MIN_LENGTH = 2; // same rule as the backend: 1 letter matches half the catalog
const DEBOUNCE_MS = 200; // wait for a typing pause instead of one request per keystroke

// Header search with a suggestion dropdown, built as the WAI-ARIA "combobox with listbox popup":
// focus never leaves the input; arrow keys move a highlight (aria-activedescendant tells screen
// readers which option it is on), Enter picks it, Escape closes the list.
export default function SearchBox() {
  const router = useRouter();
  const listId = useId();
  const [text, setText] = useState("");
  // results remember which text they belong to, so a slow reply for "mac" never shows under "macbook"
  const [result, setResult] = useState<{ q: string; items: Suggestion[] }>({ q: "", items: [] });
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  const q = text.trim();
  useEffect(() => {
    if (q.length < MIN_LENGTH) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetch(`${API_BASE}/search/suggestions?q=${encodeURIComponent(q)}`, { signal: controller.signal })
        .then((res) => (res.ok ? res.json() : []))
        .then((items: Suggestion[]) => setResult({ q, items }))
        .catch(() => {}); // aborted, or backend down: no dropdown, the search itself still works
    }, DEBOUNCE_MS);
    // the next keystroke cancels both the pending timer and a request already in flight
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [q]);

  const items = q.length >= MIN_LENGTH && result.q === q ? result.items : [];
  const expanded = open && items.length > 0;
  const activeIndex = active < items.length ? active : -1;
  const optionId = (i: number) => `${listId}-option-${i}`;

  function search(value: string) {
    setOpen(false);
    setActive(-1);
    const trimmed = value.trim();
    router.push(trimmed ? `/?q=${encodeURIComponent(trimmed)}` : "/");
  }

  function choose(suggestion: Suggestion) {
    setText(suggestion.name);
    search(suggestion.name);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (items.length === 0) return;
      e.preventDefault(); // stop the cursor jumping to the start/end of the text
      setOpen(true);
      if (e.key === "ArrowDown") setActive(activeIndex + 1 >= items.length ? 0 : activeIndex + 1);
      else setActive(activeIndex <= 0 ? items.length - 1 : activeIndex - 1);
    } else if (e.key === "Enter" && expanded && activeIndex >= 0) {
      e.preventDefault(); // pick the highlighted suggestion instead of submitting the typed text
      choose(items[activeIndex]);
    } else if (e.key === "Escape" && expanded) {
      e.preventDefault(); // first Escape only closes the list; the next one clears the field as usual
      setOpen(false);
      setActive(-1);
    }
  }

  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        search(text);
      }}
      className="relative order-last w-full sm:order-none sm:w-auto sm:flex-1"
    >
      <svg
        width={22}
        height={22}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        aria-hidden
        className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <input
        name="q"
        type="search"
        role="combobox"
        aria-label="Search products"
        aria-autocomplete="list"
        aria-expanded={expanded}
        aria-controls={listId}
        aria-activedescendant={expanded && activeIndex >= 0 ? optionId(activeIndex) : undefined}
        autoComplete="off" // the browser's own history dropdown would cover ours
        placeholder="Search laptops, phones, brands…"
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={handleKeyDown}
        className="w-full rounded-full bg-white/5 py-2.5 pl-11 pr-4 text-sm text-slate-100 ring-1 ring-white/10 transition placeholder:text-muted focus:bg-white/10 focus:outline-none focus:ring-2 focus:ring-accent"
      />
      <button type="submit" className="sr-only">
        Search
      </button>

      {/* always in the DOM so aria-controls points at a real element; hidden when there is nothing to show */}
      <ul
        id={listId}
        role="listbox"
        aria-label="Suggestions"
        hidden={!expanded}
        className="animate-pop-in absolute inset-x-0 top-full z-40 mt-2 overflow-hidden rounded-2xl bg-raised py-2 shadow-2xl shadow-black/50 ring-1 ring-white/10"
      >
        {items.map((suggestion, i) => (
          <li
            key={`${suggestion.brandName}|${suggestion.name}`}
            id={optionId(i)}
            role="option"
            aria-selected={i === activeIndex}
            // mousedown would blur the input first and close the list before the click lands
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => choose(suggestion)}
            onMouseMove={() => setActive(i)}
            className={`flex cursor-pointer items-baseline justify-between gap-4 px-4 py-2.5 text-sm ${
              i === activeIndex ? "bg-white/10" : ""
            }`}
          >
            <span className="truncate text-slate-200">
              <Highlight text={suggestion.name} match={q} />
            </span>
            <span className="shrink-0 text-xs text-muted">{suggestion.brandName}</span>
          </li>
        ))}
      </ul>

      {/* screen readers can't see the list appear, so say it */}
      <p role="status" className="sr-only">
        {expanded ? `${items.length} suggestion${items.length === 1 ? "" : "s"}, use the up and down arrows to choose` : ""}
      </p>
    </form>
  );
}

// bold the part of the name that matches what was typed
function Highlight({ text, match }: { text: string; match: string }) {
  const start = text.toLowerCase().indexOf(match.toLowerCase());
  if (start < 0) return text; // matched on the brand, not the name
  const end = start + match.length;
  return (
    <>
      {text.slice(0, start)}
      <mark className="bg-transparent font-semibold text-white">{text.slice(start, end)}</mark>
      {text.slice(end)}
    </>
  );
}
