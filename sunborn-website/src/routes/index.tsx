import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { BarChart3, Camera, Megaphone, MessageCircleHeart, PenTool, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Reveal, SectionHeading } from "@/components/site/Reveal";
import { BillingToggle } from "@/components/pricing/BillingToggle";
import { PricingCards } from "@/components/pricing/PricingCards";
import { FaqList } from "@/components/site/FaqList";
import { caseStudies, faqs, stats, testimonials } from "@/data/content";
import type { BillingCycle } from "@/data/plans";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "SunBorn &Co — Social media management & marketing agency" },
      { name: "description", content: "Strategy, content, community and ads on one simple monthly plan. South Africa & worldwide." },
      { property: "og:title", content: "SunBorn &Co — Social media management & marketing agency" },
      { property: "og:description", content: "Make your brand impossible to scroll past." },
    ],
  }),
  component: Home,
});

const services = [
  { icon: PenTool, title: "Strategy & audits", body: "Know exactly who you're talking to and what to post next." },
  { icon: Camera, title: "Content creation", body: "Posts, carousels, Reels and TikToks people stop for." },
  { icon: MessageCircleHeart, title: "Community management", body: "Real replies that turn followers into fans." },
  { icon: Megaphone, title: "Paid social ads", body: "Ads that earn back more than they cost." },
  { icon: Users, title: "Influencer & UGC", body: "Creators who make your brand feel trusted." },
  { icon: BarChart3, title: "Analytics & reporting", body: "Plain-English reports on what's working." },
];
const steps = [
  ["01", "Free discovery call", "30 minutes to learn your brand and goals."],
  ["02", "Strategy & onboarding", "Brand voice, content pillars and calendar within 7 days."],
  ["03", "Create & publish", "We produce, you approve in one click."],
  ["04", "Grow & report", "A monthly report and strategy session."],
];

function Home() {
  const [billing, setBilling] = useState<BillingCycle>("annual");
  return (
    <>
      <section className="relative overflow-hidden">
        <div className="container-site grid items-center gap-12 py-16 md:py-24 lg:grid-cols-2">
          <div>
            <p className="inline-flex rounded-full border border-border bg-card px-3 py-1 text-sm font-medium">Social media studio · South Africa &amp; worldwide</p>
            <h1 className="mt-6 text-5xl sm:text-6xl">Make your brand impossible to <span className="accent-serif">scroll past</span>.</h1>
            <p className="mt-6 max-w-xl text-lg text-muted-foreground">SunBorn &amp;Co plans, creates and grows your social media — strategy, content, community and ads — on one simple monthly plan.</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Button asChild variant="sun" size="pill"><Link to="/pricing">See payment plans</Link></Button>
              <Button asChild variant="soft" size="pill"><Link to="/contact" search={{ topic: "strategy-call" }}>Book a free strategy call</Link></Button>
            </div>
            <p className="mt-4 text-sm text-muted-foreground">No setup fees · Cancel monthly plans anytime</p>
          </div>
          <div className="relative mx-auto w-full max-w-sm">
            <div aria-hidden className="sun-glow absolute -inset-16" />
            <div className="relative rounded-[2.5rem] border-8 border-foreground bg-card p-4 shadow-[var(--shadow-warm)]">
              <p className="text-sm font-semibold">@emberandbean</p>
              <div className="mt-3 aspect-square rounded-2xl bg-sunrise" />
              <p className="mt-3 text-sm">♥ 2,481 likes · Golden hour flat whites ☕</p>
            </div>
            <div className="animate-float absolute -left-6 top-10 rounded-2xl border border-border bg-card p-3 text-sm font-semibold shadow-[var(--shadow-lift)]">Reach <span className="text-ember">+312%</span></div>
            <div className="animate-float absolute -right-4 bottom-12 rounded-2xl border border-border bg-card p-3 text-sm shadow-[var(--shadow-lift)]">New follower 🎉</div>
          </div>
        </div>
      </section>

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
        <SectionHeading eyebrow="Services" title={<>Everything your feed needs to <span className="accent-serif">shine</span></>} />
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
        <SectionHeading eyebrow="How it works" title="From first call to first post in about 10 days" />
        <ol className="mt-12 grid gap-6 md:grid-cols-4">
          {steps.map(([n, t, b]) => (
            <li key={n} className="rounded-2xl border border-border bg-card p-6">
              <p className="font-display text-3xl font-bold text-ember">{n}</p>
              <h3 className="mt-3 text-lg">{t}</h3>
              <p className="mt-2 text-muted-foreground">{b}</p>
            </li>
          ))}
        </ol>
      </section>

      <section id="work" className="bg-night py-20">
        <div className="container-site">
          <SectionHeading tone="light" eyebrow="Work" title="Results that rise" />
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {caseStudies.map((c) => (
              <article key={c.client} className="rounded-2xl border border-night-border bg-night-card p-6">
                <p className="text-sm text-night-muted">{c.industry}</p>
                <h3 className="mt-1 text-xl text-night-foreground">{c.client}</h3>
                <p className="mt-6 font-display text-5xl font-bold text-primary">{c.metric}</p>
                <p className="mt-2 text-night-muted">{c.headline}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="pricing" className="container-site py-20">
        <SectionHeading eyebrow="Payment plans" title="Simple plans, warm results" />
        <div className="mt-8 flex justify-center"><BillingToggle value={billing} onChange={setBilling} /></div>
        <div className="mt-12"><PricingCards billing={billing} /></div>
        <p className="mt-6 text-center"><Link to="/pricing" className="font-semibold text-ember underline-offset-4 hover:underline">Compare every feature →</Link></p>
      </section>

      <section className="container-site pb-20">
        <div className="grid gap-6 md:grid-cols-3">
          {testimonials.map((t) => (
            <figure key={t.name} className="rounded-2xl border border-border bg-card p-6">
              <blockquote className="text-lg">“{t.quote}”</blockquote>
              <figcaption className="mt-6 flex items-center gap-3">
                <span className="grid h-10 w-10 place-items-center rounded-full bg-sun-soft font-semibold">{t.name.split(" ").map((x) => x[0]).join("")}</span>
                <span><span className="block font-semibold">{t.name}</span><span className="text-sm text-muted-foreground">{t.role}</span></span>
              </figcaption>
            </figure>
          ))}
        </div>
      </section>

      <section id="about" className="container-site pb-20">
        <SectionHeading eyebrow="About" title={<>Every brand deserves to <span className="accent-serif">shine</span></>} intro="We're a small team of strategists, creators and community managers. Warmth, craft and clarity in everything we post." />
      </section>

      <section id="faq" className="container-site max-w-3xl pb-20">
        <SectionHeading eyebrow="FAQ" title="Questions, answered" />
        <div className="mt-10"><FaqList items={faqs} /></div>
      </section>

      <section className="relative overflow-hidden bg-night py-20 text-center">
        <div aria-hidden className="sun-glow absolute -bottom-40 left-1/2 h-80 w-[40rem] max-w-[140vw] -translate-x-1/2" />
        <div className="container-site relative">
          <h2 className="text-4xl text-night-foreground sm:text-5xl">Ready to <span className="accent-serif">rise</span>?</h2>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button asChild variant="sun" size="pill"><Link to="/pricing">Choose your plan</Link></Button>
            <Button asChild variant="night" size="pill"><Link to="/contact">Talk to us</Link></Button>
          </div>
        </div>
      </section>
    </>
  );
}
