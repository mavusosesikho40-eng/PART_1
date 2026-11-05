import { useEffect, useRef, useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { zodValidator, fallback } from "@tanstack/zod-adapter";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { ArrowRight, Clock, Loader2, Mail, MapPin, MessageCircle, Phone } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { PageHero } from "@/components/site/PageHero";
import { Button } from "@/components/ui/button";
import { Field, Honeypot } from "@/components/forms/Field";
import { describedBy, inputClass } from "@/components/forms/field-helpers";
import { site, telHref, waHref } from "@/config/site";

const topics = [
  { value: "strategy-call", label: "Free strategy call" },
  { value: "pricing", label: "Pricing question" },
  { value: "enterprise", label: "Enterprise (Solstice plan)" },
  { value: "partnership", label: "Partnership" },
  { value: "other", label: "Something else" },
] as const;
type Topic = (typeof topics)[number]["value"];
const topicValues = topics.map((t) => t.value) as [Topic, ...Topic[]];
const isTopic = (value: string): value is Topic => (topicValues as string[]).includes(value);

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
  name: z.string().trim().min(1, "Enter your name").max(120, "Keep your name under 120 characters"),
  email: z
    .string()
    .trim()
    .min(1, "Enter your email address")
    .max(254, "Enter a shorter email address")
    .email("Enter an email like name@business.co.za"),
  company: z.string().trim().max(120, "Keep this under 120 characters"),
  topic: z.enum(topicValues),
  message: z
    .string()
    .trim()
    .min(10, "Add a little more detail, at least 10 characters")
    .max(5000, "Keep your message under 5,000 characters"),
  company_website: z.string(),
});
type ContactInput = z.input<typeof schema>;
type ContactValues = z.output<typeof schema>;
type TextFieldName = "name" | "email" | "company" | "message";
const fieldOrder = [
  "name",
  "email",
  "company",
  "topic",
  "message",
] as const satisfies readonly (keyof ContactInput)[];

const messageHint = "A few lines about your brand and what you'd like help with.";

function Contact() {
  const { topic } = Route.useSearch();
  const initialTopic: Topic = isTopic(topic) ? topic : "strategy-call";
  const [sent, setSent] = useState(false);
  const [serverError, setServerError] = useState(false);
  const saving = useRef(false);
  const emptyValues: ContactInput = {
    name: "",
    email: "",
    company: "",
    topic: initialTopic,
    message: "",
    company_website: "",
  };

  // A field's error shows when it's left, and clears as soon as someone starts fixing it.
  const {
    register,
    handleSubmit,
    clearErrors,
    setFocus,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ContactInput, unknown, ContactValues>({
    resolver: zodResolver(schema),
    mode: "onBlur",
    reValidateMode: "onBlur",
    defaultValues: emptyValues,
    shouldFocusError: false,
  });

  // Following another "contact us about…" link while on this page updates the topic.
  useEffect(() => {
    setValue("topic", initialTopic);
  }, [initialTopic, setValue]);

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
  const focusFirstError = (found: Partial<Record<keyof ContactInput, unknown>>) => {
    const first = fieldOrder.find((name) => found[name]);
    if (first) setFocus(first);
  };

  const onSubmit = handleSubmit(async (values) => {
    if (saving.current) return;
    // Only bots fill the hidden field: let them think it worked, and save nothing.
    if (values.company_website) {
      setSent(true);
      return;
    }
    saving.current = true;
    setServerError(false);
    const { error } = await supabase.from("contact_messages").insert({
      name: values.name,
      email: values.email,
      company: values.company || null,
      topic: values.topic,
      message: values.message,
    });
    saving.current = false;
    if (error) {
      setServerError(true);
      toast.error("We couldn't send that just now. Please try again.");
      return;
    }
    setSent(true);
    toast.success("Message sent. We'll be in touch soon.");
  }, focusFirstError);

  return (
    <>
      <PageHero
        title={
          <>
            Let's make something worth <span className="accent-serif">stopping for</span>.
          </>
        }
        intro="Book a free 30-minute strategy call, ask about a plan, or just say hello. We reply within one business day."
      />
      <section className="container-site grid gap-8 pb-20 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] lg:items-start">
        <div className="rounded-2xl border border-border bg-card p-6 sm:p-8">
          {sent ? (
            <div role="status">
              <h2 className="text-3xl">Thanks, message received.</h2>
              <p className="mt-3 text-muted-foreground">
                We reply within one business day. In the meantime, have a look at our payment plans.
              </p>
              <div className="mt-6 flex flex-wrap gap-3">
                <Button asChild variant="sun" size="pill">
                  <Link to="/pricing">See payment plans</Link>
                </Button>
                <Button
                  type="button"
                  variant="soft"
                  size="pill"
                  onClick={() => {
                    reset(emptyValues);
                    setSent(false);
                  }}
                >
                  Send another message
                </Button>
              </div>
            </div>
          ) : (
            <form onSubmit={onSubmit} noValidate className="grid gap-5">
              <Honeypot {...register("company_website")} />
              <div className="grid gap-5 sm:grid-cols-2">
                <Field id="name" label="Name" error={errors.name?.message}>
                  <input
                    {...textField("name")}
                    autoComplete="name"
                    required
                    aria-describedby={describedBy("name", errors.name?.message)}
                    className={inputClass}
                  />
                </Field>
                <Field id="email" label="Email" error={errors.email?.message}>
                  <input
                    {...textField("email")}
                    type="email"
                    inputMode="email"
                    autoComplete="email"
                    spellCheck={false}
                    required
                    aria-describedby={describedBy("email", errors.email?.message)}
                    className={inputClass}
                  />
                </Field>
              </div>
              <Field id="company" label="Company" optional error={errors.company?.message}>
                <input
                  {...textField("company")}
                  autoComplete="organization"
                  aria-describedby={describedBy("company", errors.company?.message)}
                  className={inputClass}
                />
              </Field>
              <Field id="topic" label="What's it about?">
                <select id="topic" {...register("topic")} className={inputClass}>
                  {topics.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field
                id="message"
                label="Message"
                hint={messageHint}
                error={errors.message?.message}
              >
                <textarea
                  {...textField("message")}
                  rows={6}
                  required
                  aria-describedby={describedBy("message", errors.message?.message, messageHint)}
                  className={inputClass}
                />
              </Field>
              {serverError && (
                <div
                  role="alert"
                  className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive"
                >
                  We couldn't send your message just now. Your words are still here, so please try
                  again in a moment, or email{" "}
                  <a className="font-semibold underline" href={`mailto:${site.email}`}>
                    {site.email}
                  </a>
                  .
                </div>
              )}
              <Button
                type="submit"
                variant="sun"
                size="pill"
                aria-busy={isSubmitting || undefined}
                className="w-full sm:w-fit"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 aria-hidden className="animate-spin" />
                    Sending…
                  </>
                ) : (
                  "Send message"
                )}
              </Button>
            </form>
          )}
        </div>

        <aside className="rounded-2xl border border-border bg-card p-6 lg:sticky lg:top-28">
          <h2 className="text-xl">Get in touch</h2>
          <ul className="mt-5 space-y-4">
            <ContactItem icon={Mail}>
              <a href={`mailto:${site.email}`} className="underline-offset-4 hover:underline">
                {site.email}
              </a>
            </ContactItem>
            <ContactItem icon={Phone}>
              <a href={telHref(site.phone)} className="underline-offset-4 hover:underline">
                {site.phone}
              </a>
            </ContactItem>
            <ContactItem icon={MessageCircle}>
              <a
                href={waHref(site.whatsapp)}
                target="_blank"
                rel="noreferrer"
                className="underline-offset-4 hover:underline"
              >
                Chat on WhatsApp
              </a>
            </ContactItem>
            <ContactItem icon={MapPin}>{site.location}</ContactItem>
            <ContactItem icon={Clock}>{site.hours}</ContactItem>
          </ul>
          <div className="mt-6 border-t border-border pt-5">
            <p className="text-sm text-muted-foreground">Prefer to jump straight in?</p>
            <Link
              to="/pricing"
              className="mt-1 inline-flex items-center gap-1.5 font-semibold underline-offset-4 hover:underline"
            >
              See payment plans
              <ArrowRight aria-hidden className="h-4 w-4 text-ember" />
            </Link>
          </div>
        </aside>
      </section>
    </>
  );
}

function ContactItem({
  icon: Icon,
  children,
}: {
  icon: React.ComponentType<{ className?: string; "aria-hidden"?: boolean }>;
  children: React.ReactNode;
}) {
  return (
    <li className="flex items-start gap-3">
      <Icon aria-hidden className="mt-0.5 h-5 w-5 shrink-0 text-ember" />
      <span>{children}</span>
    </li>
  );
}
