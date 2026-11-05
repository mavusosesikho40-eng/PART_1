import { Link } from "@tanstack/react-router";
import { FaFacebookF, FaInstagram, FaLinkedinIn, FaTiktok, FaYoutube } from "react-icons/fa6";
import { Logo } from "./Logo";
import { site, telHref } from "@/config/site";

const socialIcons = {
  Instagram: FaInstagram,
  TikTok: FaTiktok,
  Facebook: FaFacebookF,
  LinkedIn: FaLinkedinIn,
  YouTube: FaYoutube,
};

const linkCls = "text-night-muted transition-colors hover:text-night-foreground";

export function SiteFooter() {
  return (
    <footer className="bg-night text-night-foreground">
      <div className="container-site grid gap-10 py-16 md:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr_1.3fr]">
        <div className="max-w-xs">
          <Logo tone="light" />
          <p className="mt-4 text-night-muted">
            Social media that shows up, warms up and grows your brand — on simple monthly plans.
          </p>
          <ul className="mt-6 flex gap-2" aria-label="Social media">
            {site.socials.map((s) => {
              const Icon = socialIcons[s.name];
              return (
                <li key={s.name}>
                  <a
                    href={s.href}
                    aria-label={`SunBorn &Co on ${s.name}`}
                    className="grid h-10 w-10 place-items-center rounded-full border border-night-border text-night-muted transition-colors hover:border-primary hover:text-primary"
                  >
                    <Icon className="h-4 w-4" />
                  </a>
                </li>
              );
            })}
          </ul>
        </div>
        <FooterCol title="Company">
          <Link to="/" hash="services" className={linkCls}>
            Services
          </Link>
          <Link to="/" hash="work" className={linkCls}>
            Work
          </Link>
          <Link to="/" hash="about" className={linkCls}>
            About
          </Link>
          <Link to="/" hash="faq" className={linkCls}>
            FAQ
          </Link>
          <Link to="/contact" className={linkCls}>
            Contact
          </Link>
        </FooterCol>
        <FooterCol title="Plans">
          <Link to="/checkout" search={{ plan: "dawn", billing: "annual" }} className={linkCls}>
            Dawn
          </Link>
          <Link
            to="/checkout"
            search={{ plan: "golden-hour", billing: "annual" }}
            className={linkCls}
          >
            Golden Hour
          </Link>
          <Link to="/checkout" search={{ plan: "zenith", billing: "annual" }} className={linkCls}>
            Zenith
          </Link>
          <Link to="/contact" search={{ topic: "enterprise" }} className={linkCls}>
            Solstice
          </Link>
          <Link to="/pricing" className={linkCls}>
            Compare plans
          </Link>
        </FooterCol>
        <FooterCol title="Legal">
          <Link to="/terms" className={linkCls}>
            Terms of Service
          </Link>
          <Link to="/privacy" className={linkCls}>
            Privacy Policy
          </Link>
          <Link to="/refunds" className={linkCls}>
            Refund Policy
          </Link>
        </FooterCol>
        <div>
          <h2 className="font-display text-base font-semibold">Say hello</h2>
          <address className="mt-4 flex flex-col gap-2 not-italic">
            <a href={`mailto:${site.email}`} className={linkCls}>
              {site.email}
            </a>
            <a href={telHref(site.phone)} className={linkCls}>
              {site.phone}
            </a>
            <span className="text-night-muted">{site.location}</span>
            <span className="text-night-muted">{site.hours}</span>
          </address>
        </div>
      </div>
      <div className="border-t border-night-border">
        <p className="container-site py-6 text-sm text-night-muted">
          © {new Date().getFullYear()} SunBorn &amp;Co. All rights reserved.
        </p>
      </div>
    </footer>
  );
}

function FooterCol({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h2 className="font-display text-base font-semibold">{title}</h2>
      <div className="mt-4 flex flex-col gap-2">{children}</div>
    </div>
  );
}
