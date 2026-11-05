import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { zodValidator, fallback } from "@tanstack/zod-adapter";
import { z } from "zod";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { PageHero } from "@/components/site/PageHero";
import { Button } from "@/components/ui/button";
import { site } from "@/config/site";

const topics = [
  ["strategy-call", "Free strategy call"],
  ["pricing", "Pricing question"],
  ["enterprise", "Enterprise (Solstice plan)"],
  ["partnership", "Partnership"],
  ["other", "Something else"],
] as const;

export const Route = createFileRoute("/contact")({
  validateSearch: zodValidator(
    z.object({ topic: fallback(z.string(), "strategy-call").default("strategy-call") }),
  ),
  head: () => ({
    meta: [
      { title: "Contact — SunBorn &Co" },
      { name: "description", content: "Book a free strategy call or ask us anything." },
      { property: "og:title", content: "Contact — SunBorn &Co" },
      { property: "og:description", content: "Let's make something worth stopping for." },
    ],
  }),
  component: Contact,
});

const schema = z.object({
  name: z.string().trim().min(1, "Please add your name").max(120),
  email: z.string().trim().email("That email doesn't look quite right").max(254),
  company: z.string().trim().max(120).optional(),
  topic: z.enum(["strategy-call", "pricing", "enterprise", "partnership", "other"]),
  message: z.string().trim().min(10, "Tell us a little more (10 characters or so)").max(5000),
});

function Contact() {
  const { topic } = Route.useSearch();
  const [state, setState] = useState<"idle" | "sending" | "done">("idle");
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    if (fd.get("company_website")) return setState("done");
    const parsed = schema.safeParse(Object.fromEntries(fd));
    if (!parsed.success) {
      setErrors(Object.fromEntries(parsed.error.issues.map((i) => [i.path[0], i.message])));
      return;
    }
    setErrors({});
    setState("sending");
    const { error } = await supabase
      .from("contact_messages")
      .insert({ ...parsed.data, company: parsed.data.company || null });
    if (error) {
      setState("idle");
      toast.error("We couldn't send that just now. Please try again.");
      return;
    }
    setState("done");
    toast.success("Message sent — we'll be in touch soon.");
  }

  const field = "mt-1 w-full rounded-xl border border-input bg-card px-4 py-3";
  return (
    <>
      <PageHero
        title={
          <>
            Let's make something worth <span className="accent-serif">stopping for</span>.
          </>
        }
      />
      <section className="container-site grid gap-8 pb-20 lg:grid-cols-[1.6fr_1fr]">
        <div className="rounded-2xl border border-border bg-card p-6 sm:p-8">
          {state === "done" ? (
            <div role="status">
              <h2 className="text-3xl">Thanks — message received.</h2>
              <p className="mt-3 text-muted-foreground">We reply within one business day.</p>
            </div>
          ) : (
            <form onSubmit={submit} noValidate className="grid gap-5">
              <input
                type="text"
                name="company_website"
                tabIndex={-1}
                autoComplete="off"
                className="sr-only"
                aria-hidden
              />
              {(
                [
                  ["name", "Name*", "text"],
                  ["email", "Email*", "email"],
                  ["company", "Company (optional)", "text"],
                ] as const
              ).map(([n, l, t]) => (
                <label key={n} className="font-medium">
                  {l}
                  <input name={n} type={t} className={field} aria-invalid={!!errors[n]} />
                  {errors[n] && <span className="text-sm text-destructive">{errors[n]}</span>}
                </label>
              ))}
              <label className="font-medium">
                Topic*
                <select
                  name="topic"
                  defaultValue={topics.some((t) => t[0] === topic) ? topic : "strategy-call"}
                  className={field}
                >
                  {topics.map(([v, l]) => (
                    <option key={v} value={v}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="font-medium">
                Message*
                <textarea
                  name="message"
                  rows={5}
                  className={field}
                  aria-invalid={!!errors["message"]}
                />
                {errors["message"] && (
                  <span className="text-sm text-destructive">{errors["message"]}</span>
                )}
              </label>
              <Button type="submit" variant="sun" size="pill" disabled={state === "sending"}>
                {state === "sending" ? "Sending…" : "Send message"}
              </Button>
            </form>
          )}
        </div>
        <aside className="h-fit rounded-2xl border border-border bg-card p-6">
          <h2 className="text-xl">Get in touch</h2>
          <ul className="mt-4 space-y-2 text-muted-foreground">
            <li>
              <a href={`mailto:${site.email}`}>{site.email}</a>
            </li>
            <li>{site.phone} (phone & WhatsApp)</li>
            <li>{site.location}</li>
            <li>{site.hours}</li>
          </ul>
        </aside>
      </section>
    </>
  );
}
