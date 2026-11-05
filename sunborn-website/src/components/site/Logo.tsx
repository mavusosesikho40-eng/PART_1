import { useId } from "react";
import { cn } from "@/lib/utils";

export function SunMark({ className }: { className?: string }) {
  const id = useId().replace(/:/g, "");
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true" className={className}>
      <defs>
        <linearGradient id={`sb-${id}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#FFC94D" />
          <stop offset="0.55" stopColor="#FF8A3D" />
          <stop offset="1" stopColor="#F0546E" />
        </linearGradient>
      </defs>
      <g fill={`url(#sb-${id})`} stroke={`url(#sb-${id})`} strokeLinecap="round">
        <path d="M12 34a12 12 0 0 1 24 0Z" stroke="none" />
        <g strokeWidth="3">
          <line x1="24" y1="9" x2="24" y2="15" />
          <line x1="9.5" y1="19.5" x2="13.5" y2="23.5" />
          <line x1="38.5" y1="19.5" x2="34.5" y2="23.5" />
          <line x1="4" y1="31" x2="8" y2="31" />
          <line x1="40" y1="31" x2="44" y2="31" />
          <line x1="5" y1="38.5" x2="43" y2="38.5" />
        </g>
      </g>
    </svg>
  );
}

export function Logo({ className, tone = "ink" }: { className?: string; tone?: "ink" | "light" }) {
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <SunMark className="h-9 w-9" />
      <span className={cn("text-xl leading-none", tone === "light" ? "text-night-foreground" : "text-foreground")}>
        <span className="font-display font-bold tracking-tight">SunBorn</span>{" "}
        <span className="font-serif italic">&amp;Co</span>
      </span>
    </span>
  );
}
