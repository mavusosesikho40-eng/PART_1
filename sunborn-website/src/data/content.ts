// Sample content — replace with real figures, case studies and testimonials.

export const stats = [
  { value: "120+", label: "brands grown" },
  { value: "48M+", label: "organic impressions" },
  { value: "6.4%", label: "average engagement" },
  { value: "4.9/5", label: "client rating" },
];

export const caseStudies = [
  {
    client: "Ember & Bean Coffee",
    industry: "Hospitality",
    metric: "+412%",
    headline: "Instagram reach in 90 days",
    visual: "chart" as const,
  },
  {
    client: "Luma Skincare",
    industry: "Beauty",
    metric: "6.8×",
    headline: "return on ad spend on TikTok",
    visual: "bars" as const,
  },
  {
    client: "Stride Athletics",
    industry: "Fitness",
    metric: "18k",
    headline: "new followers and 240 new members",
    visual: "grid" as const,
  },
];

export const testimonials = [
  {
    name: "Thandi M.",
    role: "Owner, Ember & Bean Coffee",
    quote:
      "SunBorn took us from posting whenever we remembered to a feed people actually wait for. Bookings are up and I've got my evenings back.",
  },
  {
    name: "Daniel K.",
    role: "Founder, Luma Skincare",
    quote: "Their TikTok strategy paid for our whole retainer in the first quarter.",
  },
  {
    name: "Aisha P.",
    role: "Marketing Lead, Stride Athletics",
    quote: "Clear reports, fast replies, zero fluff. It feels like an in-house team.",
  },
];

export interface Faq {
  q: string;
  a: string;
  billing?: boolean;
}
export const faqs: Faq[] = [
  {
    q: "How do your payment plans work?",
    a: "Pick a plan and pay monthly (month-to-month) or annually (pay for 10 months, get 12). Prices exclude VAT.",
    billing: true,
  },
  {
    q: "Is there a contract or lock-in?",
    a: "Monthly plans have no lock-in; cancel with 30 days' notice. Annual plans run for 12 months and are paid upfront.",
    billing: true,
  },
  {
    q: "Can I switch plans later?",
    a: "Yes. Upgrade anytime (we pro-rate the difference); downgrades apply from your next billing date.",
    billing: true,
  },
  {
    q: "Is ad spend included?",
    a: "No. Ad spend is paid directly to Meta, TikTok or LinkedIn; your plan covers our strategy and management.",
    billing: true,
  },
  {
    q: "How do I pay?",
    a: "After you confirm your plan we email an invoice with a secure payment link. Pay by card or instant EFT.",
    billing: true,
  },
  {
    q: "What happens after I sign up?",
    a: "A kick-off call within 2 business days, your strategy and content calendar within 7 days, and first posts live in about 10 days.",
  },
  {
    q: "Who owns the content?",
    a: "You do. Everything we create for you is yours once your invoice is paid.",
  },
  {
    q: "Do you work with brands outside South Africa?",
    a: "Yes. We work remotely with brands worldwide and can invoice in USD on request.",
    billing: true,
  },
];
