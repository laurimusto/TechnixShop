"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

const API_BASE = "http://localhost:8080";
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
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);

  useEffect(() => {
    const timeout = setTimeout(() => {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (query.trim()) params.set("name", query.trim());
      fetch(`${API_BASE}/search?${params.toString()}`)
        .then((res) => res.json())
        .then(setData);
    }, 300);
    return () => clearTimeout(timeout);
  }, [query, page]);

  // fixed-size slots so the grid never changes shape while loading/populating -
  // missing slots render as skeleton placeholders instead of real cards, but only while
  // we don't yet know there's nothing more coming (data === null means "still loading").
  // A response shorter than PAGE_SIZE is always the last page, so those trailing slots
  // are known-empty for good, not "still loading" - they shouldn't keep blinking.
  const stillLoading = data === null;
  const slots: (Product | null)[] = Array.from(
    { length: PAGE_SIZE },
    (_, i) => data?.content?.[i] ?? null
  );

  return (
    <main className="max-w-6xl mx-auto p-6 space-y-6 font-sans">
      <div className="flex justify-between items-baseline">
        <h1 className="text-xl font-semibold">Technix</h1>
        <Link href="/api" className="text-sm underline">
          API test console
        </Link>
      </div>

      <input
        className="border px-3 py-2 w-full max-w-md"
        placeholder="Search products..."
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setPage(0);
        }}
      />

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
        {slots.map((product, i) =>
          product ? (
            <div
              key={i}
              role="button"
              tabIndex={0}
              onClick={() => setSelectedProduct(product)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") setSelectedProduct(product);
              }}
              className="border bg-white p-3 flex flex-col gap-1 cursor-pointer hover:border-black"
            >
              {product.imageUrl && (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={product.imageUrl}
                  alt={product.name}
                  className="w-full h-40 object-cover"
                />
              )}
              <span className="font-medium text-sm text-black">{product.name}</span>
              <span className="text-xs text-gray-700">
                {product.brandName} · {product.categoryName}
              </span>
              <span className="font-semibold text-black">€{product.price.toFixed(2)}</span>
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
