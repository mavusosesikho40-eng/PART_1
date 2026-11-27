import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import type { Faq } from "@/data/content";

export function FaqList({ items }: { items: Faq[] }) {
  return (
    <Accordion
      type="single"
      collapsible
      className="rounded-2xl border border-border bg-card px-5 sm:px-8"
    >
      {items.map((f, i) => (
        <AccordionItem key={f.q} value={`f-${i}`} className="last:border-b-0">
          <AccordionTrigger className="py-5 text-left font-display text-lg font-semibold hover:no-underline">
            {f.q}
          </AccordionTrigger>
          <AccordionContent className="pb-5 text-base text-muted-foreground">
            {f.a}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  );
}
