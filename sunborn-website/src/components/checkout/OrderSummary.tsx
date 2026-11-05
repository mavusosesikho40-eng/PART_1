import { site } from "@/config/site";
import { formatZAR, type Order } from "@/lib/pricing";
import { cn } from "@/lib/utils";

const vatPercent = Math.round(site.vatRate * 100);

/** The order's lines, subtotal, VAT, first-invoice total and what it renews at. */
export function OrderSummary({
  order,
  className,
}: {
  order: Order;
  className?: string | undefined;
}) {
  return (
    <div className={className}>
      <ul className="space-y-3 text-sm">
        {order.lines.map((l) => (
          <li key={l.id} className="flex justify-between gap-4">
            <span>
              <span className="font-medium">{l.label}</span>
              <span className="block text-muted-foreground">{l.detail}</span>
            </span>
            <span className="tabular-nums">{formatZAR(l.amount)}</span>
          </li>
        ))}
      </ul>
      <dl className="mt-4 space-y-1.5 border-t border-border pt-4 text-sm">
        <SummaryRow label="Subtotal" value={formatZAR(order.subtotal)} />
        <SummaryRow label={`VAT (${vatPercent}%)`} value={formatZAR(order.vat)} />
        <SummaryRow
          label="Total first invoice"
          value={formatZAR(order.total)}
          className="pt-1 text-base font-semibold"
        />
      </dl>
      <p className="mt-2 text-sm text-muted-foreground">{order.renewalLabel}</p>
    </div>
  );
}

function SummaryRow({
  label,
  value,
  className,
}: {
  label: string;
  value: string;
  className?: string | undefined;
}) {
  return (
    <div className={cn("flex justify-between gap-4", className)}>
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}
