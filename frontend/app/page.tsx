"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { API_BASE, useShop } from "./providers";

const PAGE_SIZE = 20;

type Product = {
  productId: number;
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  categoryName: string;
  brandName: string;
  imageUrl: string | null;
  weightKg: number;
  averageRating: number;
  ratingCount: number;
};

type PageResponse = {
  content: Product[];
  number: number;
  totalPages: number;
  totalElements: number;
};

export default function Home() {
  return (
    <Suspense>
      <Shop />
    </Suspense>
  );
}

// the header's search field navigates to /?q=...; keying on it resets paging for each new search
function Shop() {
  const query = useSearchParams().get("q") ?? "";
  return <ProductGrid key={query} query={query} />;
}

function ProductGrid({ query }: { query: string }) {
  const { addToCart } = useShop();
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [minWeight, setMinWeight] = useState("");
  const [maxWeight, setMaxWeight] = useState("");
  const [sort, setSort] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    const timeout = setTimeout(() => {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (query.trim()) params.set("name", query.trim());
      if (minPrice) params.set("minPrice", minPrice);
      if (maxPrice) params.set("maxPrice", maxPrice);
      if (minWeight) params.set("minWeight", minWeight);
      if (maxWeight) params.set("maxWeight", maxWeight);
      if (sort) params.set("sort", sort);
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
  }, [query, minPrice, maxPrice, minWeight, maxWeight, sort, page]);

  function resetToFirstPage<T extends string>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  // fixed-size slots so the grid never changes shape while loading/populating -
  // missing slots render as skeleton placeholders instead of real cards, but only while
  // we don't yet know there's nothing more coming (data === null means "still loading").
  // A response shorter than PAGE_SIZE is always the last page, so those trailing slots
  // are known-empty for good, not "still loading" - they shouldn't keep blinking.
  const stillLoading = data === null && !loadError;
  const slots: (Product | null)[] = Array.from(
    { length: PAGE_SIZE },
    (_, i) => data?.content?.[i] ?? null
  );

  return (
    <main className="max-w-6xl mx-auto p-6 space-y-6 font-sans">
      <h1 className="text-xl font-semibold">
        {query ? `Results for "${query}"` : "All products"}
      </h1>

      <div className="flex flex-wrap gap-4 items-end text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-gray-700">Min price (€)</span>
          <input
            type="number"
            className="border bg-white text-black px-2 py-1 w-28"
            value={minPrice}
            onChange={(e) => resetToFirstPage(setMinPrice)(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-gray-700">Max price (€)</span>
          <input
            type="number"
            className="border bg-white text-black px-2 py-1 w-28"
            value={maxPrice}
            onChange={(e) => resetToFirstPage(setMaxPrice)(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-gray-700">Min weight (kg)</span>
          <input
            type="number"
            className="border bg-white text-black px-2 py-1 w-28"
            value={minWeight}
            onChange={(e) => resetToFirstPage(setMinWeight)(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-gray-700">Max weight (kg)</span>
          <input
            type="number"
            className="border bg-white text-black px-2 py-1 w-28"
            value={maxWeight}
            onChange={(e) => resetToFirstPage(setMaxWeight)(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-gray-700">Sort by</span>
          <select
            className="border bg-white text-black px-2 py-1"
            value={sort}
            onChange={(e) => resetToFirstPage(setSort)(e.target.value)}
          >
            <option value="">Relevance</option>
            <option value="price,asc">Price: Low to High</option>
            <option value="price,desc">Price: High to Low</option>
            <option value="averageRating,desc">Rating: High to Low</option>
            <option value="weightKg,asc">Weight: Low to High</option>
            <option value="weightKg,desc">Weight: High to Low</option>
          </select>
        </label>
      </div>

      {loadError && (
        <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          Could not load products. Check that the backend is running on {API_BASE}.
        </p>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
        {slots.map((product, i) =>
          product ? (
            <div
              key={i}
              className="rounded-lg border border-gray-200 bg-white p-3 flex flex-col gap-2 shadow-sm hover:shadow-md"
            >
              <button
                onClick={() => setSelectedProduct(product)}
                className="flex flex-col gap-1 text-left cursor-pointer"
              >
                {product.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={product.imageUrl}
                    alt=""
                    className="w-full h-40 object-cover rounded"
                  />
                ) : (
                  <div className="w-full h-40 rounded bg-gray-200 flex items-center justify-center text-xs text-gray-600">
                    No image
                  </div>
                )}
                <span className="font-medium text-sm text-black">{product.name}</span>
                <span className="text-xs text-gray-700">
                  {product.brandName} · {product.categoryName}
                </span>
              </button>
              <span className="mt-auto text-lg font-bold text-black">
                €{product.price.toFixed(2)}
              </span>
              <button
                onClick={addToCart}
                disabled={product.stockQuantity <= 0}
                className="rounded bg-amber-400 px-3 py-2 text-sm font-medium text-black hover:bg-amber-500 disabled:bg-gray-200 disabled:text-gray-600"
              >
                {product.stockQuantity > 0 ? "Add to cart" : "Out of stock"}
              </button>
            </div>
          ) : stillLoading ? (
            <div key={i} className="border bg-white p-3 flex flex-col gap-2 animate-pulse">
              <div className="w-full h-40 bg-gray-300" />
              <div className="h-4 bg-gray-300 w-3/4" />
              <div className="h-3 bg-gray-300 w-1/2" />
              <div className="h-4 bg-gray-300 w-1/4" />
            </div>
          ) : null
        )}
      </div>

      {data && data.content.length === 0 && (
        <p className="text-sm text-gray-700">No products found.</p>
      )}

      {data && data.content.length > 0 && data.content.length < PAGE_SIZE && (
        <p className="text-sm text-gray-700">
          Everything found — {data.totalElements} result{data.totalElements === 1 ? "" : "s"}.
        </p>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex gap-2 items-center">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="border px-3 py-1 disabled:opacity-50"
          >
            Prev
          </button>
          <span className="text-sm">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <button
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="border px-3 py-1 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}

      {selectedProduct && (
        <div
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-6"
          onClick={() => setSelectedProduct(null)}
        >
          <div
            className="bg-white max-w-md w-full p-6 space-y-2 relative"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={() => setSelectedProduct(null)}
              className="absolute top-2 right-3 text-lg text-black"
              aria-label="Close"
            >
              ×
            </button>
            {selectedProduct.imageUrl && (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={selectedProduct.imageUrl}
                alt={selectedProduct.name}
                className="w-full h-48 object-cover"
              />
            )}
            <h2 className="font-semibold text-lg text-black">{selectedProduct.name}</h2>
            <p className="text-sm text-gray-700">
              {selectedProduct.brandName} · {selectedProduct.categoryName}
            </p>
            <p className="text-sm text-black">{selectedProduct.description}</p>
            <p className="font-semibold text-black">€{selectedProduct.price.toFixed(2)}</p>
            <p className="text-sm text-black">Stock: {selectedProduct.stockQuantity}</p>
            <p className="text-sm text-black">Weight: {selectedProduct.weightKg} kg</p>
            <p className="text-sm text-black">
              Rating: {selectedProduct.averageRating} ({selectedProduct.ratingCount} reviews)
            </p>
          </div>
        </div>
      )}
    </main>
  );
}
