import type { ReactNode } from "react";

export function PageHero({ eyebrow, title, intro, children }: { eyebrow?: string; title: ReactNode; intro?: ReactNode; children?: ReactNode }) {
  return (
    <section className="relative overflow-hidden">
      <div aria-hidden className="sun-glow pointer-events-none absolute -top-40 left-1/2 h-[28rem] w-[44rem] max-w-[140vw] -translate-x-1/2 opacity-60" />
      <div className="container-site relative py-14 text-center md:py-20">
        {eyebrow && <p className="eyebrow mb-4">{eyebrow}</p>}
        <h1 className="mx-auto max-w-3xl text-4xl sm:text-5xl md:text-6xl">{title}</h1>
        {intro && <p className="mx-auto mt-5 max-w-2xl text-lg text-muted-foreground">{intro}</p>}
        {children}
      </div>
    </section>
  );
}
