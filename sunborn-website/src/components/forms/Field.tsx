import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { errorId, hintId } from "./field-helpers";

export function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={errorId(id)} className="mt-1.5 text-sm text-destructive">
      {children}
    </p>
  );
}

/**
 * A label, the control passed as children, and one message slot below it: the error when
 * there is one, otherwise the hint. Give the control the same `id`, plus
 * `aria-describedby={describedBy(id, error, hint)}` and `aria-invalid`.
 */
export function Field({
  id,
  label,
  optional = false,
  hint,
  error,
  className,
  children,
}: {
  id: string;
  label: ReactNode;
  optional?: boolean;
  hint?: ReactNode;
  error?: string | undefined;
  className?: string | undefined;
  children: ReactNode;
}) {
  return (
    <div className={cn("min-w-0", className)}>
      <label htmlFor={id} className="font-medium">
        {label}
        {optional && <span className="font-normal text-muted-foreground"> (optional)</span>}
      </label>
      {children}
      {error ? (
        <FieldError id={id}>{error}</FieldError>
      ) : hint ? (
        <p id={hintId(id)} className="mt-1.5 text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** A visually hidden field that people never see or reach, but form-filling bots do. */
export function Honeypot(props: React.ComponentProps<"input">) {
  return (
    <div aria-hidden="true" className="sr-only">
      <label>
        Leave this empty
        <input type="text" tabIndex={-1} autoComplete="off" {...props} />
      </label>
    </div>
  );
}
