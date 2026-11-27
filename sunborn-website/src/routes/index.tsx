import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowRight,
  BarChart3,
  Camera,
  Eye,
  HeartHandshake,
  Megaphone,
  MessageCircleHeart,
  PenLine,
  PenTool,
  Users,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Reveal, SectionHeading } from "@/components/site/Reveal";
import { BillingToggle } from "@/components/pricing/BillingToggle";
import { PricingCards } from "@/components/pricing/PricingCards";
import { FaqList } from "@/components/site/FaqList";
import { HeroVisual } from "@/components/home/HeroVisual";
import { PlatformsStrip } from "@/components/home/PlatformsStrip";
import { CaseStudyVisual } from "@/components/home/CaseStudyVisual";
import { caseStudies, faqs, stats, testimonials } from "@/data/content";
import type { BillingCycle } from "@/data/plans";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "SunBorn &Co — Social media management & marketing agency" },
      {
        name: "description",
        content:
          "Strategy, content, community and ads on one simple monthly plan. South Africa & worldwide.",
      },
      { property: "og:title", content: "SunBorn &Co — Social media management & marketing agency" },
      { property: "og:description", content: "Make your brand impossible to scroll past." },
    ],
  }),
  component: Home,
});

const services = [
  {
    icon: PenTool,
    title: "Strategy & audits",
    body: "Know exactly who you're talking to and what to post next.",
  },
  {
    icon: Camera,
    title: "Content creation",
    body: "Posts, carousels, Reels and TikToks people stop for.",
  },
  {
    icon: MessageCircleHeart,
    title: "Community management",
    body: "Real replies that turn followers into fans.",
  },
  { icon: Megaphone, title: "Paid social ads", body: "Ads that earn back more than they cost." },
  { icon: Users, title: "Influencer & UGC", body: "Creators who make your brand feel trusted." },
  {
    icon: BarChart3,
    title: "Analytics & reporting",
    body: "Plain-English reports on what's working.",
  },
];

const steps = [
  { n: "01", title: "Free discovery call", body: "30 minutes to learn your brand and goals." },
  {
    n: "02",
    title: "Strategy & onboarding",
    body: "Brand voice, content pillars and calendar within 7 days.",
  },
  { n: "03", title: "Create & publish", body: "We produce, you approve in one click." },
  { n: "04", title: "Grow & report", body: "A monthly report and strategy session." },
];

const values = [
  {
    icon: HeartHandshake,
    title: "Warmth",
    body: "Real conversations, not bots. We talk to your audience the way you would.",
  },
  {
    icon: PenLine,
    title: "Craft",
    body: "Every post is designed and written by people who care how it looks and reads.",
  },
  {
    icon: Eye,
    title: "Clarity",
    body: "Honest reporting and plain pricing. You always know what's working and what it costs.",
  },
];

const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0] ?? "")
    .join("");

function Home() {
  const [billing, setBilling] = useState<BillingCycle>("annual");
  return (
    <>
      <section className="relative overflow-hidden">
        <div className="container-site grid items-center gap-10 py-14 md:py-20 lg:grid-cols-[1.05fr_1fr] lg:gap-12">
          <div>
            <p className="inline-flex rounded-full border border-border bg-card px-3 py-1 text-xs font-medium sm:text-sm">
              Social media studio · South Africa &amp; worldwide
            </p>
            <h1 className="mt-6 text-5xl sm:text-6xl">
              Make your brand impossible to <span className="accent-serif">scroll past</span>.
            </h1>
            <p className="mt-6 max-w-xl text-lg text-muted-foreground">
              SunBorn &amp;Co plans, creates and grows your social media — strategy, content,
              community and ads — on one simple monthly plan.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Button asChild variant="sun" size="pill">
                <Link to="/pricing">See payment plans</Link>
              </Button>
              <Button asChild variant="soft" size="pill">
                <Link to="/contact" search={{ topic: "strategy-call" }}>
                  Book a free strategy call
                </Link>
              </Button>
            </div>
            <p className="mt-4 text-sm text-muted-foreground">
              No setup fees · Cancel monthly plans anytime
            </p>
          </div>
          <HeroVisual />
        </div>
      </section>

      <PlatformsStrip />

      <section className="border-y border-border bg-card">
        <div className="container-site grid grid-cols-2 gap-6 py-10 md:grid-cols-4">
          {stats.map((s) => (
            <div key={s.label} className="text-center">
              <p className="font-display text-3xl font-bold">{s.value}</p>
              <p className="text-muted-foreground">{s.label}</p>
            </div>
          ))}
        </div>
      </section>

      <section id="services" className="container-site py-20">
        <Reveal>
          <SectionHeading
            eyebrow="Services"
            title={
              <>
                Everything your feed needs to <span className="accent-serif">shine</span>
              </>
            }
          />
        </Reveal>
        <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {services.map((s, i) => (
            <Reveal key={s.title} delay={i * 60}>
              <div className="lift h-full rounded-2xl border border-border bg-card p-6">
                <s.icon className="h-7 w-7 text-ember" aria-hidden />
                <h3 className="mt-4 text-xl">{s.title}</h3>
                <p className="mt-2 text-muted-foreground">{s.body}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="container-site pb-20">
        <Reveal>
          <SectionHeading
            eyebrow="How it works"
            title="From first call to first post in about 10 days"
          />
        </Reveal>
        <Reveal delay={80}>
          <ol className="mt-12 grid gap-6 md:grid-cols-4">
            {steps.map((s) => (
              <li key={s.n} className="rounded-2xl border border-border bg-card p-6">
                <p className="font-display text-3xl font-bold text-ember">{s.n}</p>
                <h3 className="mt-3 text-lg">{s.title}</h3>
                <p className="mt-2 text-muted-foreground">{s.body}</p>
              </li>
            ))}
          </ol>
        </Reveal>
      </section>

      <section id="work" className="bg-night py-20">
        <div className="container-site">
          <Reveal>
            <SectionHeading tone="light" eyebrow="Work" title="Results that rise" />
          </Reveal>
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {caseStudies.map((c, i) => (
              <Reveal key={c.client} delay={i * 80}>
                <article className="h-full rounded-2xl border border-night-border bg-night-card p-6">
                  <CaseStudyVisual kind={c.visual} />
                  <p className="mt-6 text-sm text-night-muted">{c.industry}</p>
                  <h3 className="mt-1 text-xl text-night-foreground">{c.client}</h3>
                  <p className="mt-5 font-display text-5xl font-bold text-primary">{c.metric}</p>
                  <p className="mt-2 text-night-muted">{c.headline}</p>
                </article>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      <section id="pricing" className="container-site py-20">
        <Reveal>
          <SectionHeading eyebrow="Payment plans" title="Simple plans, warm results" />
        </Reveal>
        <div className="mt-8 flex justify-center">
          <BillingToggle value={billing} onChange={setBilling} />
        </div>
        <div className="mt-12">
          <PricingCards billing={billing} />
        </div>
        <p className="mt-6 text-center">
          <Link
            to="/pricing"
            hash="compare"
            className="inline-flex items-center gap-1.5 font-semibold underline-offset-4 hover:underline"
          >
            Compare every feature
            <ArrowRight aria-hidden className="h-4 w-4 text-ember" />
          </Link>
        </p>
      </section>

      <section aria-label="What clients say" className="container-site pb-20">
        <div className="grid gap-6 md:grid-cols-3">
          {testimonials.map((t, i) => (
            <Reveal key={t.name} delay={i * 80}>
              <figure className="h-full rounded-2xl border border-border bg-card p-6">
                <blockquote className="text-lg">“{t.quote}”</blockquote>
                <figcaption className="mt-6 flex items-center gap-3">
                  <span
                    aria-hidden
                    className="grid h-10 w-10 place-items-center rounded-full bg-sun-soft font-semibold"
                  >
                    {initials(t.name)}
                  </span>
                  <span>
                    <span className="block font-semibold">{t.name}</span>
                    <span className="text-sm text-muted-foreground">{t.role}</span>
                  </span>
                </figcaption>
              </figure>
            </Reveal>
          ))}
        </div>
      </section>

      <section id="about" className="container-site pb-20">
        <Reveal>
          <SectionHeading
            eyebrow="About"
            title={
              <>
                Every brand deserves to <span className="accent-serif">shine</span>
              </>
            }
            intro="We're a small team of strategists, creators and community managers who believe every brand deserves its moment in the sun."
          />
        </Reveal>
        <div className="mt-12 grid gap-6 md:grid-cols-3">
          {values.map((v, i) => (
            <Reveal key={v.title} delay={i * 80}>
              <div className="h-full rounded-2xl border border-border bg-card p-6">
                <span className="grid h-11 w-11 place-items-center rounded-full bg-sun-soft">
                  <v.icon aria-hidden className="h-5 w-5 text-ember" />
                </span>
                <h3 className="mt-4 text-xl">{v.title}</h3>
                <p className="mt-2 text-muted-foreground">{v.body}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      <section id="faq" className="container-site max-w-3xl pb-20">
        <Reveal>
          <SectionHeading eyebrow="FAQ" title="Questions, answered" />
        </Reveal>
        <div className="mt-10">
          <FaqList items={faqs} />
        </div>
      </section>

      <section className="relative overflow-hidden bg-night py-20 text-center">
        <div
          aria-hidden
          className="sun-glow absolute -bottom-40 left-1/2 h-80 w-[40rem] max-w-[140vw] -translate-x-1/2"
        />
        <Reveal className="container-site relative">
          <h2 className="text-4xl text-night-foreground sm:text-5xl">
            Ready to <span className="accent-serif">rise</span>?
          </h2>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button asChild variant="sun" size="pill">
              <Link to="/pricing">Choose your plan</Link>
            </Button>
            <Button asChild variant="night" size="pill">
              <Link to="/contact">Talk to us</Link>
            </Button>
          </div>
        </Reveal>
      </section>
    </>
  );
}
