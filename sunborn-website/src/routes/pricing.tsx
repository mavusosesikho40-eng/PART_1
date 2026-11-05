import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Check, Minus } from "lucide-react";
import { PageHero } from "@/components/site/PageHero";
import { BillingToggle } from "@/components/pricing/BillingToggle";
import { PricingCards } from "@/components/pricing/PricingCards";
import { FaqList } from "@/components/site/FaqList";
import { addOns, compareGroups, plans, type BillingCycle } from "@/data/plans";
import { faqs } from "@/data/content";
import { formatZAR } from "@/lib/pricing";

export const Route = createFileRoute("/pricing")({
  head: () => ({
    meta: [
      { title: "Payment plans — SunBorn &Co" },
      {
        name: "description",
        content:
          "Simple monthly or annual social media plans in ZAR. Compare Dawn, Golden Hour, Zenith and Solstice.",
      },
      { property: "og:title", content: "Payment plans — SunBorn &Co" },
      { property: "og:description", content: "Simple payment plans that grow with you." },
    ],
  }),
  component: Pricing,
});

const cell = (v: string | boolean) =>
  v === true ? (
    <Check className="mx-auto h-4 w-4 text-ember" aria-label="Included" />
  ) : v === false ? (
    <Minus className="mx-auto h-4 w-4 text-muted-foreground" aria-label="Not included" />
  ) : (
    v
  );

function Pricing() {
  const [billing, setBilling] = useState<BillingCycle>("annual");
  return (
    <>
      <PageHero
        title={
          <>
            Simple payment plans that <span className="accent-serif">grow with you</span>
          </>
        }
      >
        <div className="mt-8 flex justify-center">
          <BillingToggle value={billing} onChange={setBilling} />
        </div>
      </PageHero>
      <section className="container-site pb-20">
        <PricingCards billing={billing} />
      </section>
      <section className="container-site pb-20">
        <h2 className="text-center text-3xl sm:text-4xl">Compare every feature</h2>
        <table className="mt-10 w-full border-collapse text-sm">
          <thead className="sticky top-16 bg-background md:top-20">
            <tr>
              <th className="p-3 text-left">Feature</th>
              {plans.map((p) => (
                <th key={p.id} className="p-3">
                  {p.name}
                </th>
              ))}
            </tr>
          </thead>
          {compareGroups.map((g) => (
            <tbody key={g.title}>
              <tr>
                <th colSpan={5} className="bg-muted p-3 text-left font-display">
                  {g.title}
                </th>
              </tr>
              {g.rows.map((r) => (
                <tr key={r.label} className="border-b border-border">
                  <th scope="row" className="p-3 text-left font-medium">
                    {r.label}
                  </th>
                  {r.values.map((v, i) => (
                    <td key={i} className="p-3 text-center">
                      {cell(v)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          ))}
        </table>
      </section>
      <section className="container-site pb-20">
        <h2 className="text-center text-3xl">Add-ons</h2>
        <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {addOns.map((a) => (
            <div key={a.id} className="rounded-2xl border border-border bg-card p-6">
              <h3 className="text-lg">{a.name}</h3>
              <p className="mt-2 text-muted-foreground">{a.description}</p>
              <p className="mt-4 font-semibold">
                {formatZAR(a.price)} {a.recurring ? "/month" : "once-off"}
              </p>
            </div>
          ))}
        </div>
      </section>
      <section className="container-site max-w-3xl pb-20">
        <h2 className="mb-8 text-center text-3xl">Billing questions</h2>
        <FaqList items={faqs.filter((f) => f.billing)} />
      </section>
    </>
  );
}
