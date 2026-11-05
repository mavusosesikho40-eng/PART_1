import {
  FaFacebook,
  FaInstagram,
  FaLinkedin,
  FaPinterest,
  FaTiktok,
  FaXTwitter,
  FaYoutube,
} from "react-icons/fa6";

const platforms = [
  { name: "Instagram", icon: FaInstagram },
  { name: "TikTok", icon: FaTiktok },
  { name: "Facebook", icon: FaFacebook },
  { name: "LinkedIn", icon: FaLinkedin },
  { name: "YouTube", icon: FaYoutube },
  { name: "X", icon: FaXTwitter },
  { name: "Pinterest", icon: FaPinterest },
];

export function PlatformsStrip() {
  return (
    <section className="container-site pb-14 md:pb-16">
      <p id="platforms-label" className="text-center text-sm font-medium text-muted-foreground">
        We grow brands on
      </p>
      <ul
        aria-labelledby="platforms-label"
        className="mt-5 flex flex-wrap items-center justify-center gap-x-8 gap-y-4 sm:gap-x-10"
      >
        {platforms.map(({ name, icon: Icon }) => (
          <li key={name} className="flex items-center gap-2 text-foreground/75">
            <Icon aria-hidden className="h-5 w-5" />
            <span className="text-sm font-semibold">{name}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
