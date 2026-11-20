"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { API_BASE, type Product, useShop } from "./providers";
import { btn, euro, ProductImage } from "./ui";

const PAGE_SIZE = 20;
const LOW_STOCK_THRESHOLD = 5; // real stock only - never fake scarcity
const KG_TO_LB = 2.20462;

type PageResponse = {
  content: Product[];
  number: number;
  totalPages: number;
  totalElements: number;
};

type Category = { categoryId: number; name: string };

export default function Home() {
  return (
    <Suspense>
      <Shop />
    </Suspense>
  );
}

// the header's search field navigates to /?q=..., the category tabs to /?category=<id>;
// keying the grid on both resets paging/filters whenever either changes
function Shop() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { recentlyViewed, markViewed } = useShop();
  const query = searchParams.get("q") ?? "";
  const categoryId = searchParams.get("category") ?? "";
  const [categories, setCategories] = useState<Category[]>([]);
  const [selected, setSelected] = useState<Product | null>(null);

  // fetched here (not in ProductGrid) so switching tabs doesn't refetch the category list
  useEffect(() => {
    fetch(`${API_BASE}/categories`)
      .then((res) => (res.ok ? res.json() : []))
      .then(setCategories)
      .catch(() => setCategories([])); // tabs just don't show; the grid reports backend errors
  }, []);

  function selectCategory(id: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (id) params.set("category", id);
    else params.delete("category");
    router.push(`/?${params.toString()}`);
  }

  function openProduct(product: Product) {
    setSelected(product);
    markViewed(product);
  }

  const showHero = !query && !categoryId;
  const tabs = [{ id: "", name: "All" }, ...categories.map((c) => ({ id: String(c.categoryId), name: c.name }))];

  return (
    <main className="mx-auto max-w-7xl px-4 pt-6 sm:px-6">
      {showHero && <Hero />}

      {categories.length > 0 && (
        <nav aria-label="Categories" className="mt-6 flex flex-wrap gap-2">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => selectCategory(tab.id)}
              aria-pressed={categoryId === tab.id}
              className={`${categoryId === tab.id ? `${btn.primary} shadow-lg shadow-accent/20` : btn.secondary} px-5 py-2 text-sm`}
            >
              {tab.name}
            </button>
          ))}
        </nav>
      )}

      {recentlyViewed.length > 0 && <RecentlyViewed products={recentlyViewed} onOpen={openProduct} />}

      <ProductGrid
        key={`${query}|${categoryId}`}
        query={query}
        categoryId={categoryId}
        heading={query ? `Results for "${query}"` : (categories.find((c) => String(c.categoryId) === categoryId)?.name ?? "All products")}
        headingIsPageTitle={!showHero}
        onOpen={openProduct}
      />

      {selected && <QuickView key={selected.productId} product={selected} onClose={() => setSelected(null)} />}
    </main>
  );
}

function Hero() {
  return (
    <section className="animate-fade-up relative overflow-hidden rounded-3xl bg-linear-to-br from-surface via-surface to-accent/25 px-6 py-10 ring-1 ring-white/10 sm:px-12 sm:py-14">
      <div aria-hidden className="pointer-events-none absolute -right-20 -top-24 h-72 w-72 rounded-full bg-accent/25 blur-3xl" />
      <p className="text-sm font-medium text-accent">Laptops &amp; phones</p>
      <h1 className="mt-2 max-w-xl text-3xl font-semibold tracking-tight text-white sm:text-5xl">
        Tech that keeps up with you.
      </h1>
      <p className="mt-4 max-w-lg text-slate-200">
        Compare specs, filter in seconds and find your next device from the brands you trust.
      </p>
    </section>
  );
}

function RecentlyViewed({ products, onOpen }: { products: Product[]; onOpen: (p: Product) => void }) {
  return (
    <section aria-labelledby="recently-viewed" className="mt-8">
      <h2 id="recently-viewed" className="text-xs font-semibold uppercase tracking-wider text-muted">
        Recently viewed
      </h2>
      <ul className="mt-3 flex snap-x gap-3 overflow-x-auto pb-2">
        {products.map((product) => (
          <li key={product.productId} className="animate-fade-up shrink-0 snap-start">
            <button
              onClick={() => onOpen(product)}
              className="group flex w-60 items-center gap-3 rounded-2xl bg-surface p-2 pr-4 text-left ring-1 ring-white/10 transition hover:bg-raised hover:ring-white/20"
            >
              <ProductImage src={product.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded-xl" />
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium text-slate-100">{product.name}</span>
                <span className="text-sm text-accent">{euro.format(product.price)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Rating({ value, count }: { value: number; count: number }) {
  return (
    <span className="flex items-center gap-1 text-xs text-muted">
      <span aria-hidden className="text-accent">
        ★
      </span>
      <span aria-hidden className="font-medium text-slate-100">
        {value.toFixed(1)}
      </span>
      <span aria-hidden>({count})</span>
      <span className="sr-only">
        Rated {value.toFixed(1)} out of 5 from {count} reviews
      </span>
    </span>
  );
}

function StockLabel({ stock }: { stock: number }) {
  if (stock <= 0) return <span className="text-sm text-muted">Out of stock</span>;
  const low = stock <= LOW_STOCK_THRESHOLD;
  return (
    <span className={`flex items-center gap-2 text-sm ${low ? "text-amber-300" : "text-emerald-300"}`}>
      <span aria-hidden className={`h-2 w-2 rounded-full ${low ? "bg-amber-400" : "bg-emerald-400"}`} />
      {low ? `Only ${stock} left` : "In stock"}
    </span>
  );
}

// Add-to-cart with a short "added" checkmark so the click feels acknowledged
function AddToCartButton({ product, large = false }: { product: Product; large?: boolean }) {
  const { addToCart } = useShop();
  const [added, setAdded] = useState(false);
  const outOfStock = product.stockQuantity <= 0;

  useEffect(() => {
    if (!added) return;
    const timeout = setTimeout(() => setAdded(false), 1200);
    return () => clearTimeout(timeout);
  }, [added]);

  const icon = added ? (
    <path d="m5 12 5 5 9-10" />
  ) : (
    <>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </>
  );

  return (
    <button
      onClick={() => setAdded(addToCart(product))}
      disabled={outOfStock}
      aria-label={large ? undefined : `Add ${product.name} to cart`}
      // two utilities for the same property (bg-accent + bg-emerald-500) would conflict,
      // so the "added" state swaps the whole style instead of adding to it
      className={`${added ? "inline-flex items-center justify-center gap-2 rounded-full bg-emerald-500 font-semibold text-white transition" : btn.primary} ${
        large ? "w-full px-6 py-3.5 text-base" : "h-10 w-10 shrink-0 enabled:active:scale-90"
      }`}
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
        {icon}
      </svg>
      {large && (outOfStock ? "Out of stock" : added ? "Added" : "Add to cart")}
    </button>
  );
}

function ProductCard({ product, index, onOpen }: { product: Product; index: number; onOpen: (p: Product) => void }) {
  const lowStock = product.stockQuantity > 0 && product.stockQuantity <= LOW_STOCK_THRESHOLD;
  return (
    <article
      // staggered reveal: each card starts a little after the previous one (capped so late cards don't lag)
      style={{ animationDelay: `${Math.min(index, 12) * 45}ms` }}
      className="animate-fade-up group flex flex-col rounded-2xl bg-surface p-3 ring-1 ring-white/10 transition duration-300 hover:-translate-y-1 hover:bg-raised hover:shadow-2xl hover:shadow-black/30 hover:ring-white/20"
    >
      <button
        onClick={() => onOpen(product)}
        className="flex flex-col gap-1 rounded-xl text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      >
        <span className="relative block">
          <ProductImage src={product.imageUrl} alt="" className="aspect-4/3 w-full rounded-xl" />
          <span
            aria-hidden
            className="absolute inset-x-3 bottom-3 translate-y-2 rounded-full bg-chrome/85 py-2 text-center text-xs font-medium text-white opacity-0 backdrop-blur transition duration-300 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100"
          >
            Quick view
          </span>
          {lowStock && (
            <span className="absolute left-3 top-3 rounded-full bg-accent px-2.5 py-0.5 text-xs font-semibold text-on-accent">
              Only {product.stockQuantity} left
            </span>
          )}
        </span>
        <span className="mt-2 px-1 text-xs text-muted">{product.brandName}</span>
        <span className="line-clamp-2 px-1 text-sm font-medium text-slate-100">{product.name}</span>
        <span className="px-1">
          <Rating value={product.averageRating} count={product.ratingCount} />
        </span>
      </button>
      <div className="mt-auto flex items-center justify-between gap-2 px-1 pt-3">
        <span className="text-lg font-semibold text-white">{euro.format(product.price)}</span>
        <AddToCartButton product={product} />
      </div>
    </article>
  );
}

const fieldClass =
  "rounded-xl bg-field px-3 py-2 text-sm text-slate-100 ring-1 ring-white/10 transition focus:outline-none focus:ring-2 focus:ring-accent";

function FilterField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-muted">{label}</span>
      <input
        type="number"
        min="0"
        inputMode="decimal"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`${fieldClass} w-28`}
      />
    </label>
  );
}

function ProductGrid({
  query,
  categoryId,
  heading,
  headingIsPageTitle,
  onOpen,
}: {
  query: string;
  categoryId: string;
  heading: string;
  headingIsPageTitle: boolean;
  onOpen: (p: Product) => void;
}) {
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [minWeight, setMinWeight] = useState("");
  const [maxWeight, setMaxWeight] = useState("");
  // always a real sort (+ productId as tie-breaker below): without a full order the database may
  // return equal rows in a different order per query, so a product could show on two pages
  const [sort, setSort] = useState("price,asc");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse | null>(null);
  const [loadError, setLoadError] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const timeout = setTimeout(() => {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (query.trim()) params.set("name", query.trim());
      if (categoryId) params.set("categoryId", categoryId);
      if (minPrice) params.set("minPrice", minPrice);
      if (maxPrice) params.set("maxPrice", maxPrice);
      if (minWeight) params.set("minWeight", minWeight);
      if (maxWeight) params.set("maxWeight", maxWeight);
      params.append("sort", sort);
      params.append("sort", "productId,asc");
      fetch(`${API_BASE}/search?${params.toString()}`)
        .then((res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          return res.json();
        })
        .then((page: PageResponse) => {
          setData(page);
          setLoadError(false);
        })
        // network failure (backend down, CORS) or a non-2xx response
        .catch(() => setLoadError(true));
    }, 300);
    return () => clearTimeout(timeout);
  }, [query, categoryId, minPrice, maxPrice, minWeight, maxWeight, sort, page]);

  function resetToFirstPage<T extends string>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const hasFilters = Boolean(minPrice || maxPrice || minWeight || maxWeight);
  function clearFilters() {
    setMinPrice("");
    setMaxPrice("");
    setMinWeight("");
    setMaxWeight("");
    setPage(0);
  }

  function goToPage(next: number) {
    setPage(next);
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    sectionRef.current?.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
  }

  const Heading = headingIsPageTitle ? "h1" : "h2";
  const stillLoading = data === null && !loadError;

  return (
    <section ref={sectionRef} className="scroll-mt-24 pt-8">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <Heading className="text-2xl font-semibold tracking-tight text-white">{heading}</Heading>
        {data && (
          <p className="text-sm text-muted">
            {data.totalElements} product{data.totalElements === 1 ? "" : "s"}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-end gap-3 rounded-2xl bg-surface p-4 ring-1 ring-white/10">
        <FilterField label="Min price (€)" value={minPrice} onChange={resetToFirstPage(setMinPrice)} />
        <FilterField label="Max price (€)" value={maxPrice} onChange={resetToFirstPage(setMaxPrice)} />
        <FilterField label="Min weight (kg)" value={minWeight} onChange={resetToFirstPage(setMinWeight)} />
        <FilterField label="Max weight (kg)" value={maxWeight} onChange={resetToFirstPage(setMaxWeight)} />
        {hasFilters && (
          <button onClick={clearFilters} className={`${btn.secondary} px-4 py-2 text-sm`}>
            Clear filters
          </button>
        )}
        <label className="flex flex-col gap-1.5 sm:ml-auto">
          <span className="text-xs font-medium text-muted">Sort by</span>
          <select className={fieldClass} value={sort} onChange={(e) => resetToFirstPage(setSort)(e.target.value)}>
            <option value="price,asc">Price: Low to High</option>
            <option value="price,desc">Price: High to Low</option>
            <option value="averageRating,desc">Top rated</option>
            <option value="weightKg,asc">Weight: Light to Heavy</option>
            <option value="weightKg,desc">Weight: Heavy to Light</option>
          </select>
        </label>
      </div>

      {loadError && (
        <p role="alert" className="mt-6 rounded-2xl bg-red-500/15 p-4 text-sm text-red-100 ring-1 ring-red-400/40">
          Could not load products. Check that the backend is running on {API_BASE}.
        </p>
      )}

      <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3 sm:gap-5 lg:grid-cols-4">
        {stillLoading &&
          Array.from({ length: 8 }, (_, i) => (
            <div key={i} className="flex flex-col gap-3 rounded-2xl bg-surface p-3 ring-1 ring-white/10">
              <div className="skeleton aspect-4/3 w-full rounded-xl" />
              <div className="skeleton h-3 w-1/3 rounded-full" />
              <div className="skeleton h-4 w-3/4 rounded-full" />
              <div className="skeleton h-5 w-1/4 rounded-full" />
            </div>
          ))}
        {/* keyed by product id, so a new page / new results replay the reveal animation */}
        {data?.content.map((product, i) => (
          <ProductCard key={product.productId} product={product} index={i} onOpen={onOpen} />
        ))}
      </div>

      {data && data.content.length === 0 && (
        <div className="animate-fade-up mt-6 rounded-2xl bg-surface p-10 text-center ring-1 ring-white/10">
          <p className="text-lg font-medium text-white">No products match</p>
          <p className="mt-1 text-sm text-muted">Try a different search or loosen the filters.</p>
          {hasFilters && (
            <button onClick={clearFilters} className={`${btn.primary} mt-4 px-5 py-2 text-sm`}>
              Clear filters
            </button>
          )}
        </div>
      )}

      {data && data.totalPages > 1 && (
        <nav aria-label="Pagination" className="mt-10 flex items-center justify-center gap-3">
          <button disabled={page === 0} onClick={() => goToPage(page - 1)} className={`${btn.secondary} px-5 py-2 text-sm`}>
            ← Previous
          </button>
          <span className="text-sm text-muted">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <button
            disabled={page >= data.totalPages - 1}
            onClick={() => goToPage(page + 1)}
            className={`${btn.secondary} px-5 py-2 text-sm`}
          >
            Next →
          </button>
        </nav>
      )}
    </section>
  );
}

// Native <dialog> + showModal(): the browser handles focus trapping, Escape and the backdrop
function QuickView({ product, onClose }: { product: Product; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialogRef.current?.showModal();
  }, []);

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      // a click on the dialog element itself (not its content) is a click on the backdrop
      onClick={(e) => e.target === dialogRef.current && dialogRef.current.close()}
      aria-labelledby="quick-view-title"
      className="animate-pop-in m-auto max-h-[calc(100%-2rem)] w-[min(56rem,calc(100%-2rem))] overflow-y-auto rounded-3xl bg-raised text-slate-100 shadow-2xl ring-1 ring-white/10 backdrop:bg-black/50 backdrop:backdrop-blur-sm"
    >
      <div className="relative grid md:grid-cols-2">
        <ProductImage src={product.imageUrl} alt={product.name} className="aspect-square w-full md:aspect-auto md:h-full" />
        <div className="flex flex-col gap-4 p-6 sm:p-8">
          <button
            onClick={() => dialogRef.current?.close()}
            aria-label="Close"
            className={`${btn.ghost} absolute right-4 top-4 h-9 w-9 bg-chrome/80 text-lg ring-1 ring-white/15`}
          >
            ×
          </button>
          <p className="text-sm text-muted">
            {product.brandName} · {product.categoryName}
          </p>
          <h2 id="quick-view-title" className="-mt-2 text-2xl font-semibold tracking-tight text-white">
            {product.name}
          </h2>
          <Rating value={product.averageRating} count={product.ratingCount} />
          <p className="text-3xl font-semibold text-white">{euro.format(product.price)}</p>
          <StockLabel stock={product.stockQuantity} />
          <p className="text-sm leading-relaxed text-slate-200">{product.description}</p>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <div className="rounded-xl bg-white/5 p-3 ring-1 ring-white/10">
              <dt className="text-xs text-muted">Weight</dt>
              <dd className="mt-0.5 font-medium text-slate-100">
                {product.weightKg} kg <span className="text-muted">({(product.weightKg * KG_TO_LB).toFixed(2)} lb)</span>
              </dd>
            </div>
            <div className="rounded-xl bg-white/5 p-3 ring-1 ring-white/10">
              <dt className="text-xs text-muted">Category</dt>
              <dd className="mt-0.5 font-medium text-slate-100">{product.categoryName}</dd>
            </div>
          </dl>
          <div className="mt-auto pt-2">
            <AddToCartButton product={product} large />
          </div>
        </div>
      </div>
    </dialog>
  );
}
