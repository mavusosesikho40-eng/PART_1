import { useRef } from "react";
import type { BillingCycle } from "@/data/plans";
import { cn } from "@/lib/utils";

const options: { value: BillingCycle; label: string }[] = [
  { value: "monthly", label: "Monthly" },
  { value: "annual", label: "Annual" },
];

export function BillingToggle({
  value,
  onChange,
  className,
}: {
  value: BillingCycle;
  onChange: (v: BillingCycle) => void;
  className?: string | undefined;
}) {
  return (
    <SegmentedControl
      label="Billing cycle"
      value={value}
      onChange={onChange}
      className={className}
      options={options.map((o) => ({
        ...o,
        extra:
          o.value === "annual" ? (
            <span className="ml-2 rounded-full bg-primary px-2 py-0.5 text-[0.7rem] font-semibold text-primary-foreground">
              2 months free
            </span>
          ) : null,
      }))}
    />
  );
}

export function SegmentedControl<T extends string>({
  label,
  value,
  onChange,
  options,
  className,
  itemClassName,
}: {
  label: string;
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: string; extra?: React.ReactNode }[];
  className?: string | undefined;
  /** Extra classes for every option, e.g. to let long labels wrap in a narrow grid. */
  itemClassName?: string | undefined;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const onKey = (e: React.KeyboardEvent, i: number) => {
    let n = -1;
    if (e.key === "ArrowRight" || e.key === "ArrowDown") n = (i + 1) % options.length;
    if (e.key === "ArrowLeft" || e.key === "ArrowUp") n = (i - 1 + options.length) % options.length;
    const next = options[n];
    if (!next) return;
    e.preventDefault();
    onChange(next.value);
    refs.current[n]?.focus();
  };
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn(
        "inline-flex max-w-full rounded-full border border-border bg-card p-1",
        className,
      )}
    >
      {options.map((o, i) => {
        const checked = o.value === value;
        return (
          <button
            key={o.value}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            onClick={() => onChange(o.value)}
            onKeyDown={(e) => onKey(e, i)}
            className={cn(
              "inline-flex flex-1 items-center justify-center whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold transition-colors",
              checked
                ? "bg-foreground text-background"
                : "text-muted-foreground hover:text-foreground",
              itemClassName,
            )}
          >
            {o.label}
            {o.extra}
          </button>
        );
      })}
    </div>
  );
}
