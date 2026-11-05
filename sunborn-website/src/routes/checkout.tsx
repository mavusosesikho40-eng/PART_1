import { useEffect, useRef, useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { zodValidator, fallback } from "@tanstack/zod-adapter";
import { z } from "zod";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Check, ChevronDown, Loader2 } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { BillingToggle } from "@/components/pricing/BillingToggle";
import { OrderSummary } from "@/components/checkout/OrderSummary";
import { Field, FieldError, Honeypot } from "@/components/forms/Field";
import { describedBy, errorId, inputClass } from "@/components/forms/field-helpers";
import { site } from "@/config/site";
import {
  addOns,
  getPlan,
  purchasablePlanIds,
  type AddOnId,
  type BillingCycle,
  type PurchasablePlanId,
} from "@/data/plans";
import {
  computeOrder,
  describeAddOnPrice,
  formatZAR,
  generateReference,
  getPlanPrice,
  type Order,
} from "@/lib/pricing";
import { cn } from "@/lib/utils";

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
      {
        name: "description",
        content: "Confirm your SunBorn &Co plan. No payment is taken on this page.",
      },
      { property: "og:title", content: "Checkout — SunBorn &Co" },
      { property: "og:description", content: "Confirm your social media plan." },
    ],
  }),
  component: Checkout,
});

const PLATFORMS = [
  "Instagram",
  "TikTok",
  "Facebook",
  "LinkedIn",
  "YouTube",
  "X",
  "Pinterest",
] as const;

const detailsSchema = z.object({
  full_name: z
    .string()
    .trim()
    .min(1, "Enter your full name")
    .max(120, "Keep your name under 120 characters"),
  business_name: z
    .string()
    .trim()
    .min(1, "Enter your business name")
    .max(120, "Keep the business name under 120 characters"),
  email: z
    .string()
    .trim()
    .min(1, "Enter your email address")
    .max(254, "Enter a shorter email address")
    .email("Enter an email like name@business.co.za"),
  phone: z.string().trim().max(40, "Enter a shorter phone number"),
  website: z.string().trim().max(200, "Keep this under 200 characters"),
  platforms: z.array(z.enum(PLATFORMS)).min(1, "Choose at least one platform"),
  notes: z.string().trim().max(2000, "Keep your notes under 2,000 characters"),
  agree: z.boolean().refine((v) => v, "Tick the box to agree to the terms"),
  company_website: z.string(),
});
type DetailsInput = z.input<typeof detailsSchema>;
type Details = z.output<typeof detailsSchema>;
type TextFieldName = "full_name" | "business_name" | "email" | "phone" | "website" | "notes";

const emptyDetails: DetailsInput = {
  full_name: "",
  business_name: "",
  email: "",
  phone: "",
  website: "",
  platforms: [],
  notes: "",
  agree: false,
  company_website: "",
};

const fieldOrder = [
  "full_name",
  "business_name",
  "email",
  "phone",
  "website",
  "platforms",
  "notes",
  "agree",
] as const satisfies readonly (keyof DetailsInput)[];

const hints = {
  email: "We'll send your invoice here.",
  website: "For example emberandbean.co.za or @emberandbean",
  notes: "Goals, launch dates, brands you love…",
};

const paymentNote =
  "No payment is taken on this page. We'll email your invoice with a secure payment link (card or instant EFT) within 1 business day.";

interface Confirmation {
  reference: string;
  firstName: string;
  planName: string;
  billing: BillingCycle;
  order: Order;
}

function Checkout() {
  const search = Route.useSearch();
  const navigate = useNavigate({ from: "/checkout" });
  const planId: PurchasablePlanId = (purchasablePlanIds as string[]).includes(search.plan)
    ? (search.plan as PurchasablePlanId)
    : "golden-hour";
  const billing: BillingCycle = search.billing === "monthly" ? "monthly" : "annual";
  const [addOnIds, setAddOnIds] = useState<AddOnId[]>([]);
  const [serverError, setServerError] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const saving = useRef(false);
  const order = computeOrder(planId, billing, addOnIds);

  // A field's error shows when it's left, and clears as soon as someone starts fixing it.
  const {
    register,
    control,
    handleSubmit,
    clearErrors,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<DetailsInput, unknown, Details>({
    resolver: zodResolver(detailsSchema),
    mode: "onBlur",
    reValidateMode: "onBlur",
    defaultValues: emptyDetails,
    shouldFocusError: false,
  });

  const textField = (name: TextFieldName) => {
    const field = register(name);
    return {
      ...field,
      id: name,
      onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        if (errors[name]) clearErrors(name);
        return field.onChange(event);
      },
      "aria-invalid": errors[name] ? true : undefined,
    };
  };

  // On a failed submit, take people to the first problem in the order the page shows them.
  const focusFirstError = (found: Partial<Record<keyof DetailsInput, unknown>>) => {
    const first = fieldOrder.find((name) => found[name]);
    if (first) setFocus(first);
  };

  const setSearch = (next: Partial<{ plan: string; billing: string }>) =>
    navigate({ search: (prev) => ({ ...prev, ...next }), replace: true });

  const toggleAddOn = (id: AddOnId, on: boolean) =>
    setAddOnIds((current) => (on ? [...current, id] : current.filter((x) => x !== id)));

  const onSubmit = handleSubmit(async (values) => {
    if (saving.current) return;
    const reference = generateReference();
    const confirmed: Confirmation = {
      reference,
      firstName: values.full_name.split(/\s+/)[0] ?? values.full_name,
      planName: getPlan(planId).name,
      billing,
      order,
    };
    // Only bots fill the hidden field: let them think it worked, and save nothing.
    if (values.company_website) {
      setConfirmation(confirmed);
      return;
    }
    saving.current = true;
    setServerError(false);
    const { error } = await supabase.from("plan_orders").insert({
      reference,
      plan_id: planId,
      billing_cycle: billing,
      add_ons: addOnIds,
      platforms: values.platforms,
      full_name: values.full_name,
      business_name: values.business_name,
      email: values.email,
      phone: values.phone || null,
      website: values.website || null,
      notes: values.notes || null,
      subtotal: order.subtotal,
      vat: order.vat,
      total: order.total,
      renewal_amount: order.renewal,
    });
    saving.current = false;
    if (error) {
      setServerError(true);
      return;
    }
    setConfirmation(confirmed);
  }, focusFirstError);

  if (search.plan === "solstice") return <SolsticeNotice />;
  if (confirmation) return <ConfirmationView confirmation={confirmation} />;

  return (
    <section className="container-site py-10 md:py-14">
      <div className="max-w-2xl">
        <p className="eyebrow">Checkout</p>
        <h1 className="mt-2 text-4xl sm:text-5xl">Confirm your plan</h1>
        <p className="mt-3 text-lg text-muted-foreground">
          Choose your plan and any add-ons, then tell us about your business. No payment is taken
          today.
        </p>
      </div>

      <div className="mt-8 grid gap-10 lg:mt-10 lg:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)] lg:items-start">
        <div className="min-w-0">
          {/* Below the desktop layout, the summary sits above the form, folded to its total. */}
          <details className="group mb-8 rounded-2xl border border-border bg-card lg:hidden">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 rounded-2xl p-4 [&::-webkit-details-marker]:hidden">
              <span className="font-display text-lg font-semibold">Order summary</span>
              <span className="flex items-center gap-2">
                <span className="sr-only">Total first invoice</span>
                <span className="font-semibold tabular-nums">{formatZAR(order.total)}</span>
                <ChevronDown
                  aria-hidden
                  className="h-5 w-5 transition-transform group-open:rotate-180"
                />
              </span>
            </summary>
            <div className="border-t border-border p-4">
              <OrderSummary order={order} />
            </div>
          </details>

          <form onSubmit={onSubmit} noValidate className="space-y-12">
            <fieldset>
              <legend className="font-display text-xl font-semibold">1. Your plan</legend>
              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                {purchasablePlanIds.map((id) => {
                  const plan = getPlan(id);
                  const price = getPlanPrice(id, billing);
                  const checked = id === planId;
                  return (
                    <label
                      key={id}
                      className={cn(
                        "relative flex cursor-pointer flex-col rounded-2xl p-4 transition-shadow has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring has-[:focus-visible]:ring-offset-2 has-[:focus-visible]:ring-offset-background",
                        checked
                          ? "sunrise-border shadow-[var(--shadow-lift)]"
                          : "border border-border bg-card hover:border-foreground/40",
                      )}
                    >
                      <input
                        type="radio"
                        name="plan"
                        value={id}
                        checked={checked}
                        onChange={() => setSearch({ plan: id })}
                        className="sr-only"
                      />
                      <span className="flex items-start justify-between gap-3">
                        <span className="font-display text-lg font-semibold leading-tight">
                          {plan.name}
                        </span>
                        <span
                          aria-hidden
                          className={cn(
                            "mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border-2",
                            checked
                              ? "border-ember bg-ember text-white"
                              : "border-muted-foreground",
                          )}
                        >
                          {checked && <Check className="h-3 w-3" strokeWidth={3} />}
                        </span>
                      </span>
                      <span className="mt-2 text-sm">
                        <span className="font-semibold tabular-nums">
                          {formatZAR(price.perMonth)}/mo
                        </span>
                        <span className="text-muted-foreground">
                          {" "}
                          · billed {billing === "annual" ? "yearly" : "monthly"}
                        </span>
                      </span>
                      {plan.badge && (
                        <span className="mt-3 w-fit rounded-full bg-sun-soft px-2.5 py-0.5 text-xs font-semibold">
                          {plan.badge}
                        </span>
                      )}
                    </label>
                  );
                })}
              </div>
              <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2">
                <BillingToggle value={billing} onChange={(b) => setSearch({ billing: b })} />
                <p className="text-sm text-muted-foreground">
                  {billing === "annual"
                    ? "Pay for 10 months, get 12. Billed upfront."
                    : "Month to month. Cancel with 30 days' notice."}
                </p>
              </div>
            </fieldset>

            <fieldset>
              <legend className="font-display text-xl font-semibold">
                2. Add-ons{" "}
                <span className="font-sans text-base font-normal text-muted-foreground">
                  (optional)
                </span>
              </legend>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {addOns.map((a) => {
                  const checked = addOnIds.includes(a.id);
                  const { price, note } = describeAddOnPrice(a.id, billing);
                  const id = `addon-${a.id}`;
                  return (
                    <label
                      key={a.id}
                      htmlFor={id}
                      className={cn(
                        "flex cursor-pointer gap-3 rounded-2xl border p-4 transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring has-[:focus-visible]:ring-offset-2 has-[:focus-visible]:ring-offset-background",
                        checked
                          ? "border-primary bg-sun-soft/60"
                          : "border-border bg-card hover:border-foreground/40",
                      )}
                    >
                      <Checkbox
                        id={id}
                        checked={checked}
                        onCheckedChange={(v) => toggleAddOn(a.id, v === true)}
                        className="mt-0.5 focus-visible:ring-0 focus-visible:ring-offset-0"
                      />
                      <span className="min-w-0">
                        <span className="block font-semibold">{a.name}</span>
                        <span className="mt-0.5 block text-sm text-muted-foreground">
                          {a.description}
                        </span>
                        <span className="mt-2 block text-sm">
                          <span className="font-semibold tabular-nums">{price}</span>
                          {note && <span className="text-muted-foreground"> · {note}</span>}
                        </span>
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>

            <fieldset className="space-y-5">
              <legend className="mb-4 font-display text-xl font-semibold">3. Your details</legend>
              <Honeypot {...register("company_website")} />
              <div className="grid gap-5 sm:grid-cols-2">
                <Field id="full_name" label="Full name" error={errors.full_name?.message}>
                  <input
                    {...textField("full_name")}
                    autoComplete="name"
                    required
                    aria-describedby={describedBy("full_name", errors.full_name?.message)}
                    className={inputClass}
                  />
                </Field>
                <Field
                  id="business_name"
                  label="Business name"
                  error={errors.business_name?.message}
                >
                  <input
                    {...textField("business_name")}
                    autoComplete="organization"
                    required
                    aria-describedby={describedBy("business_name", errors.business_name?.message)}
                    className={inputClass}
                  />
                </Field>
                <Field id="email" label="Email" hint={hints.email} error={errors.email?.message}>
                  <input
                    {...textField("email")}
                    type="email"
                    inputMode="email"
                    autoComplete="email"
                    spellCheck={false}
                    required
                    aria-describedby={describedBy("email", errors.email?.message, hints.email)}
                    className={inputClass}
                  />
                </Field>
                <Field id="phone" label="Phone" optional error={errors.phone?.message}>
                  <input
                    {...textField("phone")}
                    type="tel"
                    inputMode="tel"
                    autoComplete="tel"
                    aria-describedby={describedBy("phone", errors.phone?.message)}
                    className={inputClass}
                  />
                </Field>
              </div>
              <Field
                id="website"
                label="Website or main social handle"
                optional
                hint={hints.website}
                error={errors.website?.message}
              >
                <input
                  {...textField("website")}
                  autoComplete="url"
                  spellCheck={false}
                  aria-describedby={describedBy("website", errors.website?.message, hints.website)}
                  className={inputClass}
                />
              </Field>

              <fieldset
                aria-describedby={errors.platforms?.message ? errorId("platforms") : undefined}
              >
                <legend className="font-medium">Platforms you'd like us to manage</legend>
                <Controller
                  control={control}
                  name="platforms"
                  render={({ field }) => (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {PLATFORMS.map((p, i) => {
                        const on = field.value.includes(p);
                        return (
                          <button
                            key={p}
                            type="button"
                            ref={i === 0 ? field.ref : undefined}
                            aria-pressed={on}
                            onClick={() => {
                              const next = on
                                ? field.value.filter((x) => x !== p)
                                : [...field.value, p];
                              field.onChange(next);
                              if (next.length > 0) clearErrors("platforms");
                            }}
                            className={cn(
                              "inline-flex items-center gap-1.5 rounded-full border px-4 py-2 text-sm font-medium transition-colors",
                              on
                                ? "border-foreground bg-foreground text-background"
                                : "border-input bg-card hover:border-foreground/50",
                            )}
                          >
                            {on && <Check aria-hidden className="h-3.5 w-3.5" strokeWidth={3} />}
                            {p}
                          </button>
                        );
                      })}
                    </div>
                  )}
                />
                {errors.platforms?.message && (
                  <FieldError id="platforms">{errors.platforms.message}</FieldError>
                )}
              </fieldset>

              <Field
                id="notes"
                label="Anything we should know?"
                optional
                hint={hints.notes}
                error={errors.notes?.message}
              >
                <textarea
                  {...textField("notes")}
                  rows={4}
                  aria-describedby={describedBy("notes", errors.notes?.message, hints.notes)}
                  className={inputClass}
                />
              </Field>

              <div>
                <Controller
                  control={control}
                  name="agree"
                  render={({ field }) => (
                    <div className="flex items-start gap-3">
                      <Checkbox
                        id="agree"
                        ref={field.ref}
                        checked={field.value}
                        onCheckedChange={(v) => {
                          field.onChange(v === true);
                          if (v === true) clearErrors("agree");
                        }}
                        aria-required
                        aria-invalid={errors.agree ? true : undefined}
                        aria-describedby={errors.agree ? errorId("agree") : undefined}
                        className="mt-0.5"
                      />
                      <label htmlFor="agree" className="text-sm leading-6">
                        I agree to the{" "}
                        <Link to="/terms" className="font-medium underline underline-offset-4">
                          Terms of Service
                        </Link>{" "}
                        and{" "}
                        <Link to="/refunds" className="font-medium underline underline-offset-4">
                          Refund Policy
                        </Link>
                        .
                      </label>
                    </div>
                  )}
                />
                {errors.agree?.message && (
                  <FieldError id="agree">{errors.agree.message}</FieldError>
                )}
              </div>
            </fieldset>

            <div className="space-y-4 border-t border-border pt-8">
              {serverError && (
                <div
                  role="alert"
                  className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive"
                >
                  We couldn't save your order just now. Your details are still here, so please try
                  again in a moment, or email{" "}
                  <a className="font-semibold underline" href={`mailto:${site.email}`}>
                    {site.email}
                  </a>
                  .
                </div>
              )}
              <p className="text-sm text-muted-foreground">{paymentNote}</p>
              <Button
                type="submit"
                variant="sun"
                size="pill"
                aria-busy={isSubmitting || undefined}
                className="w-full sm:w-auto"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 aria-hidden className="animate-spin" />
                    Confirming…
                  </>
                ) : (
                  "Confirm my plan"
                )}
              </Button>
            </div>
          </form>
        </div>

        <aside
          aria-labelledby="summary-heading"
          className="hidden rounded-2xl border border-border bg-card p-6 lg:sticky lg:top-28 lg:block"
        >
          <h2 id="summary-heading" className="text-xl">
            Order summary
          </h2>
          <OrderSummary order={order} className="mt-4" />
          <p className="mt-5 border-t border-border pt-4 text-xs text-muted-foreground">
            {paymentNote}
          </p>
        </aside>
      </div>
    </section>
  );
}

const nextSteps = [
  "We email your invoice with a secure payment link (card or instant EFT) within 1 business day.",
  "You book your kick-off call, so we can get to know your brand.",
  "Your first content goes live about 10 days after payment.",
];

function ConfirmationView({ confirmation: c }: { confirmation: Confirmation }) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    window.scrollTo({ top: 0 });
    headingRef.current?.focus();
  }, []);

  return (
    <section className="container-site max-w-2xl py-14 md:py-20">
      <div className="rounded-3xl border border-border bg-card p-6 shadow-[var(--shadow-warm)] sm:p-10">
        <p className="eyebrow">Order {c.reference}</p>
        <h1 ref={headingRef} tabIndex={-1} className="mt-3 text-4xl outline-none sm:text-5xl">
          You're in. Welcome to the <span className="accent-serif">sunny side</span>.
        </h1>
        <p className="mt-4 text-lg text-muted-foreground">
          Thanks, {c.firstName}. We've got your order for the {c.planName} plan, billed{" "}
          {c.billing === "annual" ? "yearly" : "monthly"}. Keep your reference handy.
        </p>
        <div className="mt-8 rounded-2xl border border-border bg-background p-5">
          <OrderSummary order={c.order} />
        </div>
        <h2 className="mt-10 text-2xl">What happens next</h2>
        <ol className="mt-5 space-y-4">
          {nextSteps.map((step, i) => (
            <li key={step} className="flex gap-4">
              <span
                aria-hidden
                className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-sun-soft font-display font-bold"
              >
                {i + 1}
              </span>
              <span className="pt-1">{step}</span>
            </li>
          ))}
        </ol>
        <div className="mt-10 flex flex-wrap gap-3">
          <Button asChild variant="sun" size="pill">
            <Link to="/contact" search={{ topic: "strategy-call" }}>
              Book a kick-off call
            </Link>
          </Button>
          <Button asChild variant="soft" size="pill">
            <Link to="/">Back to home</Link>
          </Button>
        </div>
      </div>
    </section>
  );
}

function SolsticeNotice() {
  return (
    <section className="container-site max-w-2xl py-20 text-center md:py-28">
      <p className="eyebrow">Solstice</p>
      <h1 className="mt-3 text-4xl sm:text-5xl">
        Solstice is <span className="accent-serif">made to measure</span>
      </h1>
      <p className="mt-4 text-lg text-muted-foreground">
        It's built around your brands and team, so let's talk first. Tell us what you need and we'll
        put a proposal together.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Button asChild variant="sun" size="pill">
          <Link to="/contact" search={{ topic: "enterprise" }}>
            Talk to sales
          </Link>
        </Button>
        <Button asChild variant="soft" size="pill">
          <Link to="/pricing">Compare plans</Link>
        </Button>
      </div>
    </section>
  );
}
