"use client";

import { useEffect, useRef, useState } from "react";
import { useShop } from "./providers";
import { btn, euro, ProductImage } from "./ui";

// Slide-in cart panel on the right. Built on a native modal <dialog>: the browser traps focus
// inside it and closes it on Escape; a click on the dimmed backdrop closes it too.
export default function CartDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const { cart, cartCount, cartTotal, setQuantity, removeFromCart } = useShop();
  const [checkoutNotice, setCheckoutNotice] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  function close() {
    dialogRef.current?.close(); // fires onClose, which tells the header the drawer is shut
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={() => {
        setCheckoutNotice(false);
        onClose();
      }}
      // a click on the dialog element itself (not its content) is a click on the backdrop
      onClick={(e) => e.target === dialogRef.current && close()}
      aria-labelledby="cart-title"
      className="animate-slide-in-right my-0 ml-auto mr-0 h-dvh max-h-none w-full max-w-md bg-raised text-slate-100 shadow-2xl shadow-black/50 ring-1 ring-white/10 backdrop:bg-black/50 backdrop:backdrop-blur-sm"
    >
      <div className="flex h-full flex-col">
        <div className="flex items-center justify-between border-b border-white/10 px-6 py-5">
          <h2 id="cart-title" className="text-lg font-semibold text-white">
            Your cart <span className="font-normal text-muted">({cartCount})</span>
          </h2>
          <button onClick={close} aria-label="Close cart" className={`${btn.ghost} h-9 w-9 text-xl`}>
            ×
          </button>
        </div>

        {cart.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="text-muted" aria-hidden>
              <path d="M6 7h12l-1 13H7L6 7Z" />
              <path d="M9 7a3 3 0 0 1 6 0" />
            </svg>
            <p className="text-lg font-medium text-white">Your cart is empty</p>
            <p className="text-sm text-muted">Find something you like - it will show up here.</p>
            <button onClick={close} className={`${btn.secondary} mt-2 px-5 py-2.5 text-sm`}>
              Continue shopping
            </button>
          </div>
        ) : (
          <>
            <ul className="flex-1 divide-y divide-white/10 overflow-y-auto px-6">
              {cart.map(({ product, quantity }) => (
                <li key={product.productId} className="flex gap-4 py-4">
                  <ProductImage src={product.imageUrl} alt="" className="h-20 w-20 shrink-0 rounded-xl" />
                  <div className="flex min-w-0 flex-1 flex-col gap-2">
                    <div className="flex justify-between gap-3">
                      <div className="min-w-0">
                        <p className="line-clamp-2 text-sm font-medium text-white">{product.name}</p>
                        <p className="text-xs text-muted">{euro.format(product.price)} each</p>
                      </div>
                      <p className="shrink-0 text-sm font-semibold text-white">{euro.format(product.price * quantity)}</p>
                    </div>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center rounded-full bg-field ring-1 ring-white/10">
                        <button
                          onClick={() => setQuantity(product.productId, quantity - 1)}
                          disabled={quantity <= 1}
                          aria-label={`Decrease quantity of ${product.name}`}
                          className="h-8 w-8 rounded-full text-lg transition enabled:hover:bg-white/15 disabled:opacity-40"
                        >
                          −
                        </button>
                        <span className="w-8 text-center text-sm tabular-nums" aria-live="polite">
                          <span className="sr-only">Quantity </span>
                          {quantity}
                        </span>
                        <button
                          onClick={() => setQuantity(product.productId, quantity + 1)}
                          disabled={quantity >= product.stockQuantity}
                          aria-label={`Increase quantity of ${product.name}`}
                          className="h-8 w-8 rounded-full text-lg transition enabled:hover:bg-white/15 disabled:opacity-40"
                        >
                          +
                        </button>
                      </div>
                      <button
                        onClick={() => removeFromCart(product.productId)}
                        className="rounded-full px-2 py-1 text-xs text-muted transition hover:bg-white/10 hover:text-white"
                      >
                        Remove
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>

            <div className="space-y-3 border-t border-white/10 px-6 py-5">
              <div className="flex items-baseline justify-between">
                <span className="text-muted">Subtotal</span>
                <span className="text-xl font-semibold text-white">{euro.format(cartTotal)}</span>
              </div>
              <p className="text-xs text-muted">VAT included. Shipping is calculated at checkout.</p>
              {checkoutNotice && (
                <p role="status" className="rounded-xl bg-white/10 px-3 py-2 text-sm text-slate-100">
                  Checkout is coming soon - your cart is saved for this visit.
                </p>
              )}
              {/* checkout itself is Project 2 */}
              <button onClick={() => setCheckoutNotice(true)} className={`${btn.primary} w-full px-6 py-3.5 text-base`}>
                Checkout
              </button>
              <button onClick={close} className={`${btn.secondary} w-full px-6 py-3 text-sm`}>
                Continue shopping
              </button>
            </div>
          </>
        )}
      </div>
    </dialog>
  );
}
