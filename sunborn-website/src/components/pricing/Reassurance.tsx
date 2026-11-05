import { ArrowUpDown, BadgeCheck, LockOpen, ShieldCheck } from "lucide-react";

const items = [
  {
    icon: BadgeCheck,
    title: "No setup fees",
    body: "Your first invoice is your plan and any add-ons. Nothing else.",
  },
  {
    icon: LockOpen,
    title: "No lock-in on monthly plans",
    body: "Cancel with 30 days' notice, whenever you like.",
  },
  {
    icon: ArrowUpDown,
    title: "Upgrade or downgrade anytime",
    body: "Upgrades are pro-rated; downgrades start on your next billing date.",
  },
  {
    icon: ShieldCheck,
    title: "Secure invoicing",
    body: "Pay by card or instant EFT through a secure payment link.",
  },
];

export function Reassurance() {
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {items.map(({ icon: Icon, title, body }) => (
        <li key={title} className="flex gap-3 rounded-2xl border border-border bg-card p-5">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-sun-soft">
            <Icon aria-hidden className="h-5 w-5 text-ember" />
          </span>
          <span>
            <span className="block font-semibold">{title}</span>
            <span className="mt-1 block text-sm text-muted-foreground">{body}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}
