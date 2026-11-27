import { describe, expect, it } from "vitest";
import {
  computeOrder,
  describeAddOnPrice,
  formatZAR,
  generateReference,
  getAddOnPrice,
  getPlanPrice,
} from "@/lib/pricing";

describe("getPlanPrice", () => {
  it("monthly Golden Hour is R5,999", () => {
    expect(getPlanPrice("golden-hour", "monthly")).toEqual({
      charge: 5999,
      perMonth: 5999,
      savings: 0,
    });
  });
  it("annual Golden Hour is R59,990, R4,999/mo, saves R11,998", () => {
    expect(getPlanPrice("golden-hour", "annual")).toEqual({
      charge: 59990,
      perMonth: 4999,
      savings: 11998,
    });
  });
  it("annual Dawn shows R2,083/mo and Zenith R9,999/mo", () => {
    expect(getPlanPrice("dawn", "annual").perMonth).toBe(2083);
    expect(getPlanPrice("zenith", "annual").perMonth).toBe(9999);
    expect(getPlanPrice("zenith", "annual").savings).toBe(23998);
  });
});

describe("add-ons", () => {
  it("recurring add-ons cost monthly × 10 on annual", () => {
    expect(getAddOnPrice("extra-platform", "annual")).toBe(12000);
    expect(getAddOnPrice("reels-boost", "monthly")).toBe(1800);
  });
  it("once-off add-ons don't change with billing", () => {
    expect(getAddOnPrice("photoshoot", "annual")).toBe(4500);
  });
  it("labels recurring add-ons for the billing cycle", () => {
    expect(describeAddOnPrice("extra-platform", "monthly")).toEqual({ price: "R1,200/mo" });
    expect(describeAddOnPrice("extra-platform", "annual")).toEqual({
      price: "R12,000/yr",
      note: "R1,200/mo × 10",
    });
  });
  it("labels once-off add-ons the same on either cycle", () => {
    expect(describeAddOnPrice("influencer-campaign", "annual")).toEqual({
      price: "R7,500 once-off",
    });
    expect(describeAddOnPrice("influencer-campaign", "monthly")).toEqual({
      price: "R7,500 once-off",
    });
  });
});

describe("computeOrder", () => {
  it("adds 15% VAT to the subtotal", () => {
    const o = computeOrder("dawn", "monthly", []);
    expect(o.subtotal).toBe(2499);
    expect(o.vat).toBe(374.85);
    expect(o.total).toBe(2873.85);
  });
  it("renewal includes plan + recurring add-ons only", () => {
    const o = computeOrder("golden-hour", "annual", ["extra-platform", "photoshoot"]);
    expect(o.subtotal).toBe(59990 + 12000 + 4500);
    expect(o.renewal).toBe(71990);
    expect(o.renewalLabel).toBe("Then R71,990 + VAT per year");
  });
  it("monthly renewal label", () => {
    const o = computeOrder("golden-hour", "monthly", ["extra-platform"]);
    expect(o.renewalLabel).toBe("Then R7,199 + VAT per month");
  });
});

describe("formatZAR", () => {
  it("formats whole and fractional amounts", () => {
    expect(formatZAR(5999)).toBe("R5,999");
    expect(formatZAR(11473.5)).toBe("R11,473.50");
  });
});

describe("generateReference", () => {
  it("uses SB- plus 6 unambiguous characters", () => {
    for (let i = 0; i < 50; i++) expect(generateReference()).toMatch(/^SB-[A-HJ-NP-Z2-9]{6}$/);
  });
});
