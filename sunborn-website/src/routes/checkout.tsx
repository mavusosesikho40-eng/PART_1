import { useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { zodValidator, fallback } from "@tanstack/zod-adapter";
import { z } from "zod";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { BillingToggle } from "@/components/pricing/BillingToggle";
import { addOns, getPlan, purchasablePlanIds, type AddOnId, type BillingCycle, type PurchasablePlanId } from "@/data/plans";
import { computeOrder, formatZAR, generateReference } from "@/lib/pricing";

export const Route = createFileRoute("/checkout")({
  validateSearch: zodValidator(
    z.object({
      plan: fallback(z.string(), "golden-hour").default("golden-hour"),
      billing: fallback(z.string(), "annual").default("annual"),
    }),
  ),
  head: () => ({
    meta: [
      { title: "Choose your plan — SunBorn &Co" },
      { name: "description", content: "Confirm your SunBorn &Co plan. No payment is taken on this page." },
      { property: "og:title", content: "Checkout — SunBorn &Co" },
      { property: "og:description", content: "Confirm your social media plan." },
    ],
  }),
  component: Checkout,
});

const platformsList = ["Instagram", "TikTok", "Facebook", "LinkedIn", "YouTube", "X", "Pinterest"];
const schema = z.object({
  full_name: z.string().trim().min(1, "Please add your name").max(120),
  business_name: z.string().trim().min(1, "Please add your business name").max(120),
  email: z.string().trim().email("That email doesn't look quite right").max(254),
  phone: z.string().trim().max(40).optional(),
  website: z.string().trim().max(200).optional(),
  notes: z.string().trim().max(2000).optional(),
});

function Checkout() {
  const search = Route.useSearch();
  const navigate = useNavigate({ from: "/checkout" });
  const planId = (purchasablePlanIds as string[]).includes(search.plan) ? (search.plan as PurchasablePlanId) : "golden-hour";
  const billing: BillingCycle = search.billing === "monthly" ? "monthly" : "annual";
  const [selected, setSelected] = useState<AddOnId[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [status, setStatus] = useState<"idle" | "sending" | "error">("idle");
  const [done, setDone] = useState<string | null>(null);
  const order = computeOrder(planId, billing, selected);
  const set = (p: Partial<{ plan: string; billing: string }>) => navigate({ search: (prev) => ({ ...prev, ...p }), replace: true });

  if (search.plan === "solstice")
    return (
      <section className="container-site py-24 text-center">
        <h1 className="text-4xl">Solstice is tailored to you</h1>
        <p className="mt-4 text-muted-foreground">Let's chat about your brands and team first.</p>
        <Button asChild variant="sun" size="pill" className="mt-8"><Link to="/contact" search={{ topic: "enterprise" }}>Talk to sales</Link></Button>
      </section>
    );

  if (done)
    return (
      <section className="container-site max-w-2xl py-20">
        <p className="eyebrow">Order {done}</p>
        <h1 className="mt-3 text-4xl">You're in. Welcome to the sunny side.</h1>
        <p className="mt-4 text-muted-foreground">{getPlan(planId).name} · {billing} · first invoice {formatZAR(order.total)} incl. VAT</p>
        <ol className="mt-8 list-decimal space-y-2 pl-5">
          <li>Invoice + secure payment link by email within 1 business day.</li>
          <li>Book your kick-off call.</li>
          <li>First content live in about 10 days.</li>
        </ol>
        <div className="mt-8 flex flex-wrap gap-3">
          <Button asChild variant="soft" size="pill"><Link to="/">Back to home</Link></Button>
          <Button asChild variant="sun" size="pill"><Link to="/contact" search={{ topic: "strategy-call" }}>Book a kick-off call</Link></Button>
        </div>
      </section>
    );

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const ref = generateReference();
    if (fd.get("company_website")) return setDone(ref);
    const parsed = schema.safeParse(Object.fromEntries(fd));
    const errs: Record<string, string> = {};
    if (!parsed.success) parsed.error.issues.forEach((i) => (errs[String(i.path[0])] = i.message));
    if (!platforms.length) errs.platforms = "Pick at least one platform";
    if (!fd.get("agree")) errs.agree = "Please agree to the terms to continue";
    setErrors(errs);
    if (Object.keys(errs).length || !parsed.success) return;
    setStatus("sending");
    const d = parsed.data;
    const { error } = await supabase.from("plan_orders").insert({
      reference: ref, plan_id: planId, billing_cycle: billing, add_ons: selected, platforms,
      full_name: d.full_name, business_name: d.business_name, email: d.email,
      phone: d.phone || null, website: d.website || null, notes: d.notes || null,
      subtotal: order.subtotal, vat: order.vat, total: order.total, renewal_amount: order.renewal,
    });
    if (error) return setStatus("error");
    setStatus("idle");
    setDone(ref);
  }

  const field = "mt-1 w-full rounded-xl border border-input bg-card px-4 py-3";
  return (
    <section className="container-site grid gap-8 py-12 lg:grid-cols-[1.6fr_1fr]">
      <form onSubmit={submit} noValidate className="space-y-10">
        <h1 className="text-4xl">Confirm your plan</h1>
        <fieldset>
          <legend className="font-display text-xl font-semibold">Your plan</legend>
          <div className="mt-4 grid gap-3 sm:grid-cols-3">
            {purchasablePlanIds.map((id) => (
              <label key={id} className={`cursor-pointer rounded-2xl border p-4 ${id === planId ? "sunrise-border" : "border-border bg-card"}`}>
                <input type="radio" name="plan" className="sr-only" checked={id === planId} onChange={() => set({ plan: id })} />
                <span className="block font-semibold">{getPlan(id).name}</span>
              </label>
            ))}
          </div>
          <BillingToggle className="mt-4" value={billing} onChange={(b) => set({ billing: b })} />
        </fieldset>
        <fieldset>
          <legend className="font-display text-xl font-semibold">Add-ons</legend>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {addOns.map((a) => (
              <label key={a.id} className="flex gap-3 rounded-2xl border border-border bg-card p-4">
                <input type="checkbox" checked={selected.includes(a.id)} onChange={(e) => setSelected((s) => (e.target.checked ? [...s, a.id] : s.filter((x) => x !== a.id)))} />
                <span><span className="block font-semibold">{a.name}</span><span className="text-sm text-muted-foreground">{formatZAR(a.price)} {a.recurring ? "/month" : "once-off"}</span></span>
              </label>
            ))}
          </div>
        </fieldset>
        <fieldset className="grid gap-4">
          <legend className="mb-2 font-display text-xl font-semibold">Your details</legend>
          <input type="text" name="company_website" tabIndex={-1} autoComplete="off" className="sr-only" aria-hidden />
          {[["full_name", "Full name*"], ["business_name", "Business name*"], ["email", "Email*"], ["phone", "Phone (optional)"], ["website", "Website or main social handle (optional)"]].map(([n, l]) => (
            <label key={n} className="font-medium">{l}
              <input name={n} className={field} aria-invalid={!!errors[n]} />
              {errors[n] && <span className="text-sm text-destructive">{errors[n]}</span>}
            </label>
          ))}
          <div role="group" aria-label="Platforms to manage">
            <p className="font-medium">Platforms to manage*</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {platformsList.map((p) => {
                const on = platforms.includes(p);
                return <button key={p} type="button" aria-pressed={on} onClick={() => setPlatforms((s) => (on ? s.filter((x) => x !== p) : [...s, p]))} className={`rounded-full border px-4 py-2 text-sm font-medium ${on ? "border-foreground bg-foreground text-background" : "border-border bg-card"}`}>{p}</button>;
              })}
            </div>
            {errors.platforms && <span className="text-sm text-destructive">{errors.platforms}</span>}
          </div>
          <label className="font-medium">Anything we should know?<textarea name="notes" rows={4} className={field} /></label>
          <label className="flex gap-2">
            <input type="checkbox" name="agree" />
            <span>I agree to the <Link to="/terms" className="underline">Terms of Service</Link> and <Link to="/refunds" className="underline">Refund Policy</Link></span>
          </label>
          {errors.agree && <span className="text-sm text-destructive">{errors.agree}</span>}
        </fieldset>
        {status === "error" && <p role="alert" className="text-destructive">Something went wrong on our side. Please try again.</p>}
        <Button type="submit" variant="sun" size="pill" disabled={status === "sending"}>{status === "sending" ? "Confirming…" : "Confirm my plan"}</Button>
      </form>
      <aside className="h-fit rounded-2xl border border-border bg-card p-6 lg:sticky lg:top-24">
        <h2 className="text-xl">Order summary</h2>
        <ul className="mt-4 space-y-3 text-sm">
          {order.lines.map((l) => <li key={l.id} className="flex justify-between gap-4"><span>{l.label}<span className="block text-muted-foreground">{l.detail}</span></span><span>{formatZAR(l.amount)}</span></li>)}
        </ul>
        <dl className="mt-4 space-y-1 border-t border-border pt-4 text-sm">
          <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatZAR(order.subtotal)}</dd></div>
          <div className="flex justify-between"><dt>VAT (15%)</dt><dd>{formatZAR(order.vat)}</dd></div>
          <div className="flex justify-between text-lg font-semibold"><dt>Total first invoice</dt><dd>{formatZAR(order.total)}</dd></div>
        </dl>
        <p className="mt-2 text-sm text-muted-foreground">{order.renewalLabel}</p>
        <p className="mt-4 text-xs text-muted-foreground">No payment is taken on this page. We'll email your invoice with a secure payment link (card or instant EFT) within 1 business day.</p>
      </aside>
    </section>
  );
}
