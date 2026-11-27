import { useState } from "react";
import { Check, Minus } from "lucide-react";
import { SegmentedControl } from "@/components/pricing/BillingToggle";
import { compareGroups, plans, type CompareValue, type PlanId } from "@/data/plans";
import { cn } from "@/lib/utils";

const highlighted: PlanId = "golden-hour";

/**
 * Every plan's features side by side. From 768px it's a table; on phones, where four
 * columns don't fit, you pick a plan and see its features as a list.
 */
export function ComparisonTable() {
  return (
    <>
      <DesktopTable />
      <PhoneList />
    </>
  );
}

function Value({ value, centered = false }: { value: CompareValue; centered?: boolean }) {
  if (typeof value === "string") return <>{value}</>;
  const Icon = value ? Check : Minus;
  return (
    <>
      <Icon
        aria-hidden
        className={cn(
          "inline-block h-4 w-4 align-[-2px]",
          value ? "text-ember" : "text-muted-foreground",
          centered && "mx-auto block",
        )}
      />
      <span className="sr-only">{value ? "Included" : "Not included"}</span>
    </>
  );
}

function DesktopTable() {
  return (
    <div className="hidden md:block">
      <table className="w-full table-fixed border-collapse text-sm">
        <caption className="sr-only">Every feature in each SunBorn &amp;Co plan</caption>
        <colgroup>
          <col className="w-[28%]" />
          {plans.map((p) => (
            <col key={p.id} />
          ))}
        </colgroup>
        <thead>
          <tr>
            <th
              scope="col"
              className="sticky top-16 z-10 bg-background p-3 text-left align-bottom font-display text-base md:top-20"
            >
              Feature
            </th>
            {plans.map((p) => (
              <th
                key={p.id}
                scope="col"
                className={cn(
                  "sticky top-16 z-10 p-3 text-center align-bottom font-display text-base md:top-20",
                  p.id === highlighted ? "rounded-t-2xl bg-sun-soft" : "bg-background",
                )}
              >
                {p.badge && (
                  <span className="mx-auto mb-1.5 block w-fit rounded-full bg-primary px-2 py-0.5 font-sans text-[0.7rem] font-semibold text-primary-foreground">
                    {p.badge}
                  </span>
                )}
                {p.name}
              </th>
            ))}
          </tr>
        </thead>
        {compareGroups.map((g) => (
          <tbody key={g.title}>
            <tr>
              <th
                scope="colgroup"
                colSpan={plans.length + 1}
                className="bg-muted px-3 py-2.5 text-left font-display text-sm font-semibold"
              >
                {g.title}
              </th>
            </tr>
            {g.rows.map((r) => (
              <tr key={r.label} className="border-b border-border">
                <th scope="row" className="p-3 text-left font-medium">
                  {r.label}
                </th>
                {r.values.map((v, i) => (
                  <td
                    key={plans[i]?.id ?? i}
                    className={cn(
                      "p-3 text-center",
                      plans[i]?.id === highlighted && "bg-sun-soft/50",
                    )}
                  >
                    <Value value={v} centered />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        ))}
      </table>
    </div>
  );
}

function PhoneList() {
  const [planId, setPlanId] = useState<PlanId>(highlighted);
  const index = Math.max(
    0,
    plans.findIndex((p) => p.id === planId),
  );
  return (
    <div className="md:hidden">
      <SegmentedControl
        label="Plan to compare"
        value={planId}
        onChange={setPlanId}
        options={plans.map((p) => ({ value: p.id, label: p.name }))}
        className="grid w-full grid-cols-4 rounded-2xl"
        itemClassName="whitespace-normal rounded-xl px-1 py-2.5 text-center text-[0.8125rem] leading-tight"
      />
      <div className="mt-6 space-y-6">
        {compareGroups.map((g) => (
          <div key={g.title}>
            <h3 className="font-display text-base font-semibold">{g.title}</h3>
            <dl className="mt-2 divide-y divide-border rounded-2xl border border-border bg-card">
              {g.rows.map((r) => (
                <div key={r.label} className="flex items-start justify-between gap-4 px-4 py-3">
                  <dt className="text-sm text-muted-foreground">{r.label}</dt>
                  <dd className="text-right text-sm font-medium">
                    <Value value={r.values[index] ?? false} />
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        ))}
      </div>
    </div>
  );
}
