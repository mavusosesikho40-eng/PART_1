export type BillingCycle = "monthly" | "annual";
export type PlanId = "dawn" | "golden-hour" | "zenith" | "solstice";
export type PurchasablePlanId = Exclude<PlanId, "solstice">;

export interface Plan {
  id: PlanId;
  name: string;
  tagline: string;
  /** Monthly price in ZAR excl. VAT; null = custom */
  monthly: number | null;
  /** Annual price billed upfront in ZAR excl. VAT; null = custom */
  annual: number | null;
  badge?: string;
  featuresIntro?: string;
  features: string[];
}

export const plans: Plan[] = [
  {
    id: "dawn",
    name: "Dawn",
    tagline: "For new brands finding their voice",
    monthly: 2499,
    annual: 24990,
    features: [
      "2 social platforms",
      "12 designed posts per month",
      "Captions, hashtags & a monthly content calendar",
      "Community replies Mon–Fri",
      "Monthly performance report",
      "Email support",
    ],
  },
  {
    id: "golden-hour",
    name: "Golden Hour",
    tagline: "For growing businesses ready to scale",
    monthly: 5999,
    annual: 59990,
    badge: "Most popular",
    featuresIntro: "Everything in Dawn, plus",
    features: [
      "3 social platforms",
      "20 posts + 4 Reels/TikToks per month",
      "Stories 3× a week",
      "Community management 7 days a week",
      "Paid ads management (1 campaign; ad spend billed separately)",
      "Bi-weekly reports + monthly strategy call",
      "Dedicated account manager",
    ],
  },
  {
    id: "zenith",
    name: "Zenith",
    tagline: "For brands that want to own their category",
    monthly: 11999,
    annual: 119990,
    featuresIntro: "Everything in Golden Hour, plus",
    features: [
      "5 social platforms",
      "30 posts + 8 Reels/TikToks per month",
      "Monthly half-day content shoot",
      "Influencer & UGC outreach (up to 3 creators a month)",
      "Multi-platform ads on Meta, TikTok & LinkedIn (up to R30,000/month ad spend managed)",
      "Weekly reports + live dashboard",
      "Priority WhatsApp support",
    ],
  },
  {
    id: "solstice",
    name: "Solstice",
    tagline: "For multi-brand teams and enterprises",
    monthly: null,
    annual: null,
    features: [
      "Unlimited platforms & brands",
      "Dedicated creative team",
      "Custom approval workflows & SLAs",
      "Crisis & reputation management",
      "Quarterly brand workshops",
    ],
  },
];

export const purchasablePlanIds: PurchasablePlanId[] = ["dawn", "golden-hour", "zenith"];

export const getPlan = (id: PlanId) => plans.find((p) => p.id === id)!;

export interface AddOn {
  id: "extra-platform" | "reels-boost" | "photoshoot" | "influencer-campaign";
  name: string;
  description: string;
  price: number;
  recurring: boolean;
}

export const addOns: AddOn[] = [
  {
    id: "extra-platform",
    name: "Extra platform",
    description: "Add one more social channel to your plan.",
    price: 1200,
    recurring: true,
  },
  {
    id: "reels-boost",
    name: "Reels boost",
    description: "4 extra Reels/TikToks every month.",
    price: 1800,
    recurring: true,
  },
  {
    id: "photoshoot",
    name: "Brand photoshoot",
    description: "A half-day shoot for fresh, on-brand content.",
    price: 4500,
    recurring: false,
  },
  {
    id: "influencer-campaign",
    name: "Influencer campaign",
    description: "A one-off creator campaign, planned and managed.",
    price: 7500,
    recurring: false,
  },
];

export type AddOnId = AddOn["id"];

export type CompareValue = string | boolean;
export interface CompareRow {
  label: string;
  values: [CompareValue, CompareValue, CompareValue, CompareValue];
}
export const compareGroups: { title: string; rows: CompareRow[] }[] = [
  {
    title: "Channels & content",
    rows: [
      { label: "Social platforms", values: ["2", "3", "5", "Unlimited"] },
      { label: "Feed posts per month", values: ["12", "20", "30", "Custom"] },
      { label: "Reels & TikToks per month", values: [false, "4", "8", "Custom"] },
      { label: "Stories", values: [false, "3× a week", "Daily", "Custom"] },
      { label: "Content shoot", values: [false, false, "Half day monthly", "Custom"] },
      { label: "Copywriting & design", values: [true, true, true, true] },
    ],
  },
  {
    title: "Community",
    rows: [
      {
        label: "Community management",
        values: ["Mon–Fri", "7 days", "7 days, priority", "24/7 with SLA"],
      },
    ],
  },
  {
    title: "Growth",
    rows: [
      {
        label: "Paid ads management",
        values: [false, "1 campaign", "Multi-platform, up to R30k spend", "Custom"],
      },
      { label: "Influencer & UGC", values: [false, false, "Up to 3 creators a month", "Custom"] },
    ],
  },
  {
    title: "Reporting & support",
    rows: [
      { label: "Reports", values: ["Monthly", "Bi-weekly", "Weekly + live dashboard", "Custom"] },
      { label: "Strategy calls", values: [false, "Monthly", "Bi-weekly", "Weekly"] },
      { label: "Account manager", values: [false, "Dedicated", "Dedicated", "Dedicated team"] },
      {
        label: "Support",
        values: ["Email", "Email & phone", "Priority WhatsApp", "Dedicated Slack channel"],
      },
    ],
  },
];
