import { site } from "@/config/site";
import { addOns, getPlan, type AddOnId, type BillingCycle, type PurchasablePlanId } from "@/data/plans";

const round2 = (n: number) => Math.round(n * 100) / 100;

/** "R5,999" when whole, "R11,473.50" otherwise. */
export function formatZAR(amount: number): string {
  const r = round2(amount);
  const whole = Number.isInteger(r);
  const s = r.toLocaleString("en-US", {
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  });
  return `${site.currencySymbol}${s}`;
}

export interface PlanPrice {
  /** Amount charged per billing period */
  charge: number;
  /** Effective per-month price for display */
  perMonth: number;
  /** Savings vs paying monthly for 12 months (annual only, else 0) */
  savings: number;
}

export function getPlanPrice(planId: PurchasablePlanId, billing: BillingCycle): PlanPrice {
  const plan = getPlan(planId);
  const monthly = plan.monthly!;
  const annual = plan.annual!;
  if (billing === "monthly") return { charge: monthly, perMonth: monthly, savings: 0 };
  return { charge: annual, perMonth: Math.round(annual / 12), savings: monthly * 12 - annual };
}

/** Recurring add-ons follow the billing cycle (annual = monthly × 10). */
export function getAddOnPrice(id: AddOnId, billing: BillingCycle): number {
  const a = addOns.find((x) => x.id === id)!;
  if (!a.recurring) return a.price;
  return billing === "annual" ? a.price * 10 : a.price;
}

export interface LineItem {
  id: string;
  label: string;
  detail: string;
  amount: number;
}

export interface Order {
  lines: LineItem[];
  subtotal: number;
  vat: number;
  total: number;
  renewal: number;
  renewalLabel: string;
}

export function computeOrder(planId: PurchasablePlanId, billing: BillingCycle, selected: AddOnId[]): Order {
  const plan = getPlan(planId);
  const price = getPlanPrice(planId, billing);
  const period = billing === "annual" ? "per year" : "per month";
  const lines: LineItem[] = [
    {
      id: plan.id,
      label: `${plan.name} plan`,
      detail: billing === "annual" ? "Annual · billed upfront" : "Monthly",
      amount: price.charge,
    },
  ];
  let renewal = price.charge;
  for (const a of addOns) {
    if (!selected.includes(a.id)) continue;
    const amount = getAddOnPrice(a.id, billing);
    lines.push({ id: a.id, label: a.name, detail: a.recurring ? (billing === "annual" ? "Recurring · yearly" : "Recurring · monthly") : "Once-off", amount });
    if (a.recurring) renewal += amount;
  }
  const subtotal = round2(lines.reduce((s, l) => s + l.amount, 0));
  const vat = round2(subtotal * site.vatRate);
  const total = round2(subtotal + vat);
  return {
    lines,
    subtotal,
    vat,
    total,
    renewal: round2(renewal),
    renewalLabel: `Then ${formatZAR(renewal)} + VAT ${period}`,
  };
}

const REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export function generateReference(): string {
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  return "SB-" + Array.from(bytes, (b) => REF_CHARS[b % REF_CHARS.length]).join("");
}
