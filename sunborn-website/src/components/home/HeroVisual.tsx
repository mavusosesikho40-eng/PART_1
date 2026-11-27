import { useId } from "react";
import {
  BatteryFull,
  Bookmark,
  CalendarDays,
  Clapperboard,
  Ellipsis,
  Heart,
  House,
  MessageCircle,
  Search,
  Send,
  Signal,
  SquarePlus,
  TrendingUp,
  User,
  Wifi,
} from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * The hero's picture: a phone showing a client's post, with cards for its reach, this week's
 * content calendar and a new follower floating around it. It's drawn in code, and read out
 * as one image.
 */
export function HeroVisual() {
  return (
    <div
      role="img"
      aria-label="A sample post we manage for a coffee shop, with its reach up 312%, this week's content calendar and a new follower."
      className="relative mx-auto h-[592px] w-full max-w-[520px] sm:h-[660px]"
    >
      {/*
        The cards sit over the parts of the phone that matter least: the status bar, the
        empty sky in the photo and the tab bar.
      */}
      <div className="absolute left-1/2 top-[88px] -translate-x-1/2 sm:top-16">
        <div
          aria-hidden
          className="sun-glow pointer-events-none absolute left-1/2 top-1/2 h-[540px] w-[540px] -translate-x-1/2 -translate-y-1/2"
        />
        <SunRays className="pointer-events-none absolute left-1/2 top-1/2 h-[600px] w-[600px] -translate-x-1/2 -translate-y-1/2 animate-spin-slow text-primary/45" />
        <Phone className="relative" />
      </div>
      <ReachCard className="absolute left-0 top-0" />
      <CalendarCard className="absolute right-0 top-[172px] sm:top-[150px]" />
      <FollowerCard className="absolute bottom-1 left-0 sm:bottom-5 sm:left-4" />
    </div>
  );
}

function SunRays({ className }: { className?: string }) {
  const rays = Array.from({ length: 24 }, (_, i) => {
    const angle = (i * Math.PI * 2) / 24;
    const inner = 64;
    const outer = i % 2 ? 84 : 96;
    // Rounded so the server and the browser write identical attributes.
    const at = (r: number, f: (a: number) => number) => (100 + r * f(angle)).toFixed(2);
    return {
      x1: at(inner, Math.cos),
      y1: at(inner, Math.sin),
      x2: at(outer, Math.cos),
      y2: at(outer, Math.sin),
    };
  });
  return (
    <svg viewBox="0 0 200 200" aria-hidden className={className}>
      <g stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
        {rays.map((r, i) => (
          <line key={i} {...r} />
        ))}
      </g>
    </svg>
  );
}

function Phone({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "w-[236px] rounded-[2.6rem] bg-night p-[9px] shadow-[var(--shadow-warm)] sm:w-[272px]",
        className,
      )}
    >
      <div className="relative flex h-[466px] flex-col overflow-hidden rounded-[2.1rem] bg-card sm:h-[540px]">
        <div className="flex items-center justify-between px-6 pt-3 text-[11px] font-semibold">
          <span>9:41</span>
          <span className="absolute left-1/2 top-2.5 h-[22px] w-[76px] -translate-x-1/2 rounded-full bg-night" />
          <span className="flex items-center gap-1">
            <Signal className="h-3 w-3" />
            <Wifi className="h-3 w-3" />
            <BatteryFull className="h-3.5 w-3.5" />
          </span>
        </div>

        <div className="mt-4 flex items-center gap-2.5 px-3.5">
          <span className="rounded-full bg-sunrise p-[2px]">
            <span className="grid h-8 w-8 place-items-center rounded-full border-2 border-card bg-sun-soft text-[11px] font-bold">
              EB
            </span>
          </span>
          <span className="min-w-0 flex-1 leading-tight">
            <span className="block text-[13px] font-semibold">emberandbean</span>
            <span className="block text-[11px] text-muted-foreground">Johannesburg</span>
          </span>
          <Ellipsis className="h-4 w-4 text-muted-foreground" />
        </div>

        <div className="relative mt-2.5 aspect-square w-full overflow-hidden">
          <PostArt />
          <p className="absolute bottom-3 left-3.5 font-serif text-[21px] italic leading-[0.95] text-white sm:text-[24px]">
            Golden hour
            <br />
            flat whites
          </p>
        </div>

        <div className="flex items-center gap-3.5 px-3.5 pt-2.5">
          <Heart className="h-5 w-5 fill-ember text-ember" />
          <MessageCircle className="h-5 w-5" />
          <Send className="h-5 w-5" />
          <Bookmark className="ml-auto h-5 w-5" />
        </div>
        <div className="px-3.5 pt-1.5 text-[12px] leading-snug">
          <p className="font-semibold">2,481 likes</p>
          <p className="mt-0.5 line-clamp-2">
            <span className="font-semibold">emberandbean</span> Slow mornings, golden pours. Our new
            single-origin flat white is here ☀️
          </p>
          <p className="mt-1 text-muted-foreground">View all 128 comments</p>
        </div>

        <div className="mt-auto flex items-center justify-around border-t border-border px-3 pb-4 pt-2.5">
          <House className="h-5 w-5" />
          <Search className="h-5 w-5" />
          <SquarePlus className="h-5 w-5" />
          <Clapperboard className="h-5 w-5" />
          <User className="h-5 w-5" />
        </div>
      </div>
    </div>
  );
}

/** A flat white on a café table at sunset. */
function PostArt() {
  const id = useId().replace(/:/g, "");
  return (
    <svg viewBox="0 0 300 300" aria-hidden className="h-full w-full">
      <defs>
        <linearGradient id={`sky-${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FFD27A" />
          <stop offset="0.55" stopColor="#FF8A3D" />
          <stop offset="1" stopColor="#F0546E" />
        </linearGradient>
        <radialGradient id={`sun-${id}`} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#FFF6D8" />
          <stop offset="1" stopColor="#FFE08A" />
        </radialGradient>
      </defs>
      <rect width="300" height="300" fill={`url(#sky-${id})`} />
      <circle cx="96" cy="128" r="58" fill={`url(#sun-${id})`} />
      <rect y="214" width="300" height="86" fill="#3A2216" />
      <rect y="214" width="300" height="4" fill="#24130C" opacity="0.7" />
      <ellipse cx="196" cy="248" rx="58" ry="11" fill="#F3E6D6" />
      <ellipse cx="196" cy="246" rx="46" ry="7" fill="#E4D2BE" />
      <path
        d="M236 190c24 0 24 30-3 30"
        fill="none"
        stroke="#FFF8F0"
        strokeWidth="8"
        strokeLinecap="round"
      />
      <path d="M154 178h84l-8 52a10 10 0 0 1-10 9h-48a10 10 0 0 1-10-9Z" fill="#FFF8F0" />
      <ellipse cx="196" cy="179" rx="42" ry="8" fill="#7A4A2B" />
      <path
        d="M196 185c-7-5-11-7-11-10.5a4.5 4.5 0 0 1 11-2.5a4.5 4.5 0 0 1 11 2.5c0 3.5-4 5.5-11 10.5Z"
        fill="#F6E3C8"
      />
      <g fill="none" stroke="#FFF8F0" strokeWidth="4" strokeLinecap="round" opacity="0.8">
        <path d="M181 162c-7-10 7-16 0-28" />
        <path d="M196 158c-7-10 7-16 0-30" />
        <path d="M211 162c-7-10 7-16 0-28" />
      </g>
    </svg>
  );
}

const floatingCard =
  "animate-float rounded-2xl border border-border bg-card shadow-[var(--shadow-lift)]";

function ReachCard({ className }: { className?: string }) {
  const id = useId().replace(/:/g, "");
  const points = "0,32 12,30 24,31 36,26 48,27 60,21 72,22 84,15 96,12 108,7 120,3";
  return (
    <div className={cn(floatingCard, "w-[150px] p-3 sm:w-[176px] sm:p-4", className)}>
      <p className="flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
        <TrendingUp className="h-3.5 w-3.5 text-success" />
        Reach
      </p>
      <p className="mt-1 font-display text-2xl font-bold leading-none sm:text-[1.75rem]">+312%</p>
      <svg viewBox="0 0 120 36" preserveAspectRatio="none" className="mt-2 h-8 w-full">
        <defs>
          <linearGradient id={`reach-${id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FF8A3D" stopOpacity="0.35" />
            <stop offset="1" stopColor="#FF8A3D" stopOpacity="0" />
          </linearGradient>
        </defs>
        <polygon points={`${points} 120,36 0,36`} fill={`url(#reach-${id})`} />
        <polyline
          points={points}
          fill="none"
          stroke="#E8552B"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <p className="mt-1 text-[11px] text-muted-foreground">Last 90 days</p>
    </div>
  );
}

const postKinds = {
  reel: { label: "Reel", dot: "bg-primary" },
  carousel: { label: "Carousel", dot: "bg-ember" },
  story: { label: "Story", dot: "bg-chart-3" },
} as const;
type PostKind = keyof typeof postKinds;

const week: { day: string; posts: PostKind[] }[] = [
  { day: "M", posts: ["reel"] },
  { day: "T", posts: [] },
  { day: "W", posts: ["carousel"] },
  { day: "T", posts: ["story", "reel"] },
  { day: "F", posts: ["carousel"] },
  { day: "S", posts: ["story"] },
  { day: "S", posts: [] },
];

function CalendarCard({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        floatingCard,
        "w-[164px] p-3 [animation-delay:-2s] sm:w-[196px] sm:p-4",
        className,
      )}
    >
      <p className="flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
        <CalendarDays className="h-3.5 w-3.5 text-ember" />
        This week
      </p>
      <div className="mt-2 grid grid-cols-7 gap-1 text-center">
        {week.map((d, i) => (
          <div key={i}>
            <p className="text-[10px] font-semibold text-muted-foreground">{d.day}</p>
            <div className="mt-1 flex h-7 flex-col items-center gap-0.5 rounded-md bg-muted pt-1.5">
              {d.posts.map((kind, j) => (
                <span key={j} className={cn("h-1.5 w-1.5 rounded-full", postKinds[kind].dot)} />
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="mt-2 flex flex-wrap gap-x-2 gap-y-0.5 text-[10px] text-muted-foreground">
        {Object.values(postKinds).map((k) => (
          <span key={k.label} className="inline-flex items-center gap-1">
            <span className={cn("h-1.5 w-1.5 rounded-full", k.dot)} />
            {k.label}
          </span>
        ))}
      </div>
    </div>
  );
}

function FollowerCard({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        floatingCard,
        "flex w-[212px] items-center gap-2.5 p-3 [animation-delay:-4s] sm:w-[240px]",
        className,
      )}
    >
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-sun-soft text-[12px] font-bold">
        TM
      </span>
      <p className="min-w-0 text-[12px] leading-snug">
        <span className="font-semibold">@thandi.m</span> started following you
      </p>
      <span className="ml-auto self-start text-[11px] text-muted-foreground">2m</span>
    </div>
  );
}
