// Store information pages. TEMPLATE TEXT: replace with the real business's policies
// (and have the legal pages checked) before a shop goes live.

export type InfoPage = {
  title: string;
  intro: string;
  sections: { heading: string; body: string }[];
  // FAQ entries render as expandable questions instead of plain sections
  faq?: boolean;
};

export const INFO_PAGES: Record<string, InfoPage> = {
  shipping: {
    title: "Shipping & delivery",
    intro: "Fast, tracked delivery across the EU.",
    sections: [
      { heading: "Delivery times", body: "Orders placed before 14:00 on a working day leave our warehouse the same day. Delivery usually takes 1–3 working days." },
      { heading: "Costs", body: "Delivery is free for orders over €50. Smaller orders cost €4.90. The final price is always shown before you pay." },
      { heading: "Tracking", body: "You get a tracking link by email as soon as your parcel is on its way." },
    ],
  },
  returns: {
    title: "Returns & refunds",
    intro: "Changed your mind? You have 30 days to return an item.",
    sections: [
      { heading: "30-day returns", body: "Return any item within 30 days of delivery, as long as it is complete and undamaged. This is on top of your 14-day legal right of withdrawal in the EU." },
      { heading: "How to return", body: "Contact us with your order number and we will email you a free return label." },
      { heading: "Refunds", body: "We refund to your original payment method within 14 days of receiving the item." },
    ],
  },
  warranty: {
    title: "Warranty",
    intro: "Every device comes with at least a 2-year guarantee.",
    sections: [
      { heading: "Legal guarantee", body: "All products are covered by the 2-year legal guarantee for consumers in the EU. If a device is faulty, we repair or replace it, or refund you." },
      { heading: "Manufacturer warranty", body: "Many brands add their own warranty on top. Details are in the box with your device." },
      { heading: "Making a claim", body: "Contact us with your order number and a short description of the problem. We arrange collection for free." },
    ],
  },
  faq: {
    title: "Frequently asked questions",
    intro: "Quick answers to common questions.",
    faq: true,
    sections: [
      { heading: "Do I need an account to order?", body: "An account lets you track orders and keeps your cart across devices. Creating one takes less than a minute." },
      { heading: "Which payment methods do you accept?", body: "Card payments (Visa, Mastercard) and bank links. Payment is handled by a certified payment provider - we never store your card details." },
      { heading: "Are prices shown with VAT?", body: "Yes, all prices include VAT." },
      { heading: "Can I change or cancel my order?", body: "Contact us as soon as possible. If the order has not left the warehouse, we can change or cancel it." },
    ],
  },
  contact: {
    title: "Contact us",
    intro: "We are happy to help. We usually reply within one working day.",
    sections: [
      { heading: "Email", body: "support@technix.example" },
      { heading: "Phone", body: "+372 5555 0000 (Mon–Fri, 9:00–17:00)" },
      { heading: "Address", body: "Technix OÜ, Example Street 1, 10111 Tallinn, Estonia" },
    ],
  },
  privacy: {
    title: "Privacy policy",
    intro: "We collect only the data needed to run your account and orders, and we never sell it.",
    sections: [
      { heading: "What we collect", body: "Your email address and a securely hashed password (or your Google account email if you sign in with Google), plus the orders you place." },
      { heading: "Why we collect it", body: "To create and secure your account, process orders and contact you about them. The legal basis is the contract with you (GDPR Art. 6(1)(b))." },
      { heading: "Security", body: "Passwords are stored only as hashes. Sign-in sessions use short-lived tokens and a secure, HttpOnly cookie that scripts cannot read." },
      { heading: "Your rights", body: "You can ask to see, correct, export or delete your data at any time, and you can complain to your data protection authority. Email privacy@technix.example." },
      { heading: "How long we keep it", body: "Account data is kept while your account is active. Order records are kept as long as accounting law requires." },
    ],
  },
  terms: {
    title: "Terms of service",
    intro: "The rules for using the Technix shop. Please read them before ordering.",
    sections: [
      { heading: "Orders", body: "Your order is a request to buy. A contract is made when we confirm the order by email." },
      { heading: "Prices", body: "Prices are in euros and include VAT. If a price is clearly wrong, we will contact you before shipping." },
      { heading: "Your account", body: "Keep your password secret. You are responsible for orders placed with your account." },
      { heading: "Liability", body: "We are not liable for indirect losses. Nothing in these terms limits your rights as a consumer." },
      { heading: "Changes", body: "We may update these terms. The version shown when you order applies to that order." },
    ],
  },
  cookies: {
    title: "Cookie policy",
    intro: "We use only the cookies needed to run the shop - no advertising or tracking cookies.",
    sections: [
      { heading: "Essential cookies", body: "A secure sign-in cookie (refreshToken) keeps you logged in. It is HttpOnly, sent only to our servers, and expires after a few days." },
      { heading: "No tracking", body: "We do not use analytics or advertising cookies, so there is no cookie banner to click through." },
    ],
  },
};
