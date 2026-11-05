import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { PageHero } from "@/components/site/PageHero";
import { BillingToggle } from "@/components/pricing/BillingToggle";
import { ComparisonTable } from "@/components/pricing/ComparisonTable";
import { PricingCards } from "@/components/pricing/PricingCards";
import { Reassurance } from "@/components/pricing/Reassurance";
import { FaqList } from "@/components/site/FaqList";
import { addOns, type BillingCycle } from "@/data/plans";
import { faqs } from "@/data/content";
import { describeAddOnPrice } from "@/lib/pricing";

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
      <section id="compare" className="container-site pb-20">
        <h2 className="text-center text-3xl sm:text-4xl">Compare every feature</h2>
        <div className="mt-10">
          <ComparisonTable />
        </div>
      </section>
      <section className="container-site pb-16">
        <h2 className="text-center text-3xl">Add-ons</h2>
        <p className="mx-auto mt-3 max-w-xl text-center text-muted-foreground">
          Add them at checkout. Monthly add-ons follow your plan's billing, so on an annual plan
          they're 10 months' price for 12.
        </p>
        <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {addOns.map((a) => {
            const { price, note } = describeAddOnPrice(a.id, billing);
            return (
              <div
                key={a.id}
                className="flex flex-col rounded-2xl border border-border bg-card p-6"
              >
                <h3 className="text-lg">{a.name}</h3>
                <p className="mt-2 text-muted-foreground">{a.description}</p>
                <p className="mt-auto pt-4">
                  <span className="font-semibold tabular-nums">{price}</span>
                  {note && <span className="block text-sm text-muted-foreground">{note}</span>}
                </p>
              </div>
            );
          })}
        </div>
      </section>
      <section aria-label="Why it's easy to start" className="container-site pb-20">
        <Reassurance />
      </section>
      <section className="container-site max-w-3xl pb-20">
        <h2 className="mb-8 text-center text-3xl">Billing questions</h2>
        <FaqList items={faqs.filter((f) => f.billing)} />
      </section>
    </>
  );
}
