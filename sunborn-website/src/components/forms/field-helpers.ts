import type { ReactNode } from "react";

/** Classes shared by every text input, select and textarea. */
export const inputClass =
  "mt-1.5 block w-full rounded-xl border border-input bg-card px-4 py-3 text-base text-foreground transition-colors placeholder:text-muted-foreground hover:border-foreground/50 aria-[invalid=true]:border-destructive";

export const errorId = (id: string) => `${id}-error`;
export const hintId = (id: string) => `${id}-hint`;

/** The id an input should point aria-describedby at: its error if shown, otherwise its hint. */
export function describedBy(id: string, error: string | undefined, hint?: ReactNode) {
  if (error) return errorId(id);
  if (hint) return hintId(id);
  return undefined;
}
