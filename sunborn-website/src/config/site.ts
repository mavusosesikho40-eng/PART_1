export const site = {
  name: "SunBorn &Co",
  tagline: "Social media management & marketing agency",
  email: "hello@sunbornco.com",
  phone: "+27 10 000 0000",
  whatsapp: "+27 10 000 0000",
  location: "South Africa · working worldwide",
  hours: "Mon–Fri, 08:00–17:00 SAST",
  currency: "ZAR",
  currencySymbol: "R",
  vatRate: 0.15,
  socials: [
    { name: "Instagram", href: "#" },
    { name: "TikTok", href: "#" },
    { name: "Facebook", href: "#" },
    { name: "LinkedIn", href: "#" },
    { name: "YouTube", href: "#" },
  ],
} as const;

export const telHref = (n: string) => `tel:${n.replace(/\s+/g, "")}`;
export const waHref = (n: string) => `https://wa.me/${n.replace(/\D/g, "")}`;
