import { Link } from "@tanstack/react-router";
import { Check } from "lucide-react";
import { plans, type BillingCycle, type Plan, type PurchasablePlanId } from "@/data/plans";
import { formatZAR, getPlanPrice } from "@/lib/pricing";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function PricingCards({ billing }: { billing: BillingCycle }) {
  return (
    <div>
      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        {plans.map((p) => (
          <PlanCard key={p.id} plan={p} billing={billing} />
        ))}
      </div>
      <p className="mt-8 text-center text-sm text-muted-foreground">
        Prices in ZAR, excl. 15% VAT · No setup fees · Ad spend is paid directly to the platforms
      </p>
    </div>
  );
}

function PlanCard({ plan, billing }: { plan: Plan; billing: BillingCycle }) {
  const popular = !!plan.badge;
  const custom = plan.monthly === null;
  const price = custom ? null : getPlanPrice(plan.id as PurchasablePlanId, billing);

  return (
    <article
      aria-labelledby={`plan-${plan.id}`}
      className={cn(
        "relative flex flex-col rounded-2xl p-6 transition-transform",
        popular ? "sunrise-border shadow-[var(--shadow-warm)] xl:-translate-y-3" : "border border-border bg-card",
      )}
    >
      {popular && (
        <span className="absolute -top-3 left-6 rounded-full bg-sunrise px-3 py-1 text-xs font-semibold text-foreground">
          {plan.badge}
        </span>
      )}
      <h3 id={`plan-${plan.id}`} className="text-2xl">{plan.name}</h3>
      <p className="mt-1 min-h-12 text-sm text-muted-foreground">{plan.tagline}</p>

      <div className="mt-5 min-h-[5.5rem]">
        {price ? (
          <>
            <p className="flex items-baseline gap-1">
              <span className="font-display text-4xl font-bold tracking-tight">{formatZAR(price.perMonth)}</span>
              <span className="text-muted-foreground">/month</span>
            </p>
            <p className="mt-2 text-sm text-muted-foreground">
              {billing === "monthly"
                ? "Billed monthly · cancel anytime"
                : `${formatZAR(price.charge)} billed yearly · you save ${formatZAR(price.savings)}`}
            </p>
          </>
        ) : (
          <>
            <p className="font-display text-4xl font-bold tracking-tight">Custom</p>
            <p className="mt-2 text-sm text-muted-foreground">Tailored to your brands and team</p>
          </>
        )}
      </div>

      <Button asChild variant={popular ? "sun" : "soft"} size="pill" className="mt-4 w-full">
        {custom ? (
          <Link to="/contact" search={{ topic: "enterprise" }}>Talk to sales</Link>
        ) : (
          <Link to="/checkout" search={{ plan: plan.id, billing }}>Choose {plan.name}</Link>
        )}
      </Button>

      <div className="mt-6 border-t border-border pt-6">
        {plan.featuresIntro && <p className="mb-3 text-sm font-semibold">{plan.featuresIntro}</p>}
        <ul className="space-y-3 text-sm">
          {plan.features.map((f) => (
            <li key={f} className="flex gap-2.5">
              <Check className="mt-0.5 h-4 w-4 shrink-0 text-ember" aria-hidden />
              <span>{f}</span>
            </li>
          ))}
        </ul>
      </div>
    </article>
  );
}
