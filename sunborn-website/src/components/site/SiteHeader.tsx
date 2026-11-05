import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";
import { Menu } from "lucide-react";
import { Logo } from "./Logo";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

type NavItem = { label: string; to: "/" | "/pricing" | "/contact"; hash?: string };
export const navItems: NavItem[] = [
  { label: "Services", to: "/", hash: "services" },
  { label: "Work", to: "/", hash: "work" },
  { label: "Pricing", to: "/pricing" },
  { label: "About", to: "/", hash: "about" },
  { label: "FAQ", to: "/", hash: "faq" },
  { label: "Contact", to: "/contact" },
];

export function SiteHeader() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 8);
    on();
    window.addEventListener("scroll", on, { passive: true });
    return () => window.removeEventListener("scroll", on);
  }, []);

  return (
    <header
      className={cn(
        "sticky top-0 z-40 bg-background transition-shadow",
        scrolled
          ? "border-b border-border shadow-[var(--shadow-lift)]"
          : "border-b border-transparent",
      )}
    >
      <div className="container-site flex h-16 items-center justify-between gap-4 md:h-20">
        <Link to="/" aria-label="SunBorn &Co home" className="rounded-md">
          <Logo />
        </Link>
        <nav aria-label="Main" className="hidden lg:block">
          <ul className="flex items-center gap-1">
            {navItems.map((n) => (
              <li key={n.label}>
                <Link
                  to={n.to}
                  {...(n.hash
                    ? { hash: n.hash }
                    : { activeProps: { className: "text-foreground" } })}
                  className="rounded-full px-3 py-2 text-[0.95rem] font-medium text-muted-foreground transition-colors hover:text-foreground"
                  activeOptions={{ includeHash: true }}
                >
                  {n.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className="flex items-center gap-2">
          <Button asChild variant="sun" className="hidden h-10 px-5 sm:inline-flex">
            <Link to="/pricing">See plans</Link>
          </Button>
          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger asChild>
              <Button
                variant="soft"
                size="icon"
                className="h-10 w-10 lg:hidden"
                aria-label="Open menu"
              >
                <Menu />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-[85vw] max-w-sm bg-background">
              <SheetTitle className="sr-only">Menu</SheetTitle>
              <Logo />
              <nav aria-label="Mobile" className="mt-8">
                <ul className="flex flex-col gap-1">
                  {navItems.map((n) => (
                    <li key={n.label}>
                      <Link
                        to={n.to}
                        {...(n.hash ? { hash: n.hash } : {})}
                        onClick={() => setOpen(false)}
                        className="block rounded-xl px-3 py-3 font-display text-xl font-semibold hover:bg-muted"
                      >
                        {n.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
              <Button asChild variant="sun" size="pill" className="mt-8 w-full">
                <Link to="/pricing" onClick={() => setOpen(false)}>
                  See plans
                </Link>
              </Button>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
}
