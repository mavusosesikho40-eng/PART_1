export function LegalPage({ title, items }: { title: string; items: [string, string][] }) {
  return (
    <article className="container-site max-w-3xl py-16">
      <h1 className="text-4xl">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Template policy — please have it reviewed before launch.
      </p>
      {items.map(([h, b]) => (
        <section key={h} className="mt-8">
          <h2 className="text-xl">{h}</h2>
          <p className="mt-2 text-muted-foreground">{b}</p>
        </section>
      ))}
    </article>
  );
}
