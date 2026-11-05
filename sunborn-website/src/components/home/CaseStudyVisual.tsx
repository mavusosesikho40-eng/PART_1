import { useId } from "react";
import { Heart, Play } from "lucide-react";
import { cn } from "@/lib/utils";

/** A small decorative picture for a case-study card on the dark Work band. */
export function CaseStudyVisual({ kind }: { kind: "chart" | "bars" | "grid" }) {
  return (
    <div
      aria-hidden
      className="h-28 overflow-hidden rounded-xl border border-night-border bg-night p-3"
    >
      {kind === "chart" && <ReachChart />}
      {kind === "bars" && <ReturnBars />}
      {kind === "grid" && <PostGrid />}
    </div>
  );
}

/** Reach climbing over 90 days. */
function ReachChart() {
  const id = useId().replace(/:/g, "");
  const points = "0,66 24,64 48,62 72,57 96,52 120,45 144,38 168,31 192,22 216,14 240,7";
  return (
    <svg viewBox="0 0 240 80" preserveAspectRatio="none" className="h-full w-full">
      <defs>
        <linearGradient id={`area-${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FFB020" stopOpacity="0.45" />
          <stop offset="1" stopColor="#FFB020" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[20, 40, 60].map((y) => (
        <line key={y} x1="0" x2="240" y1={y} y2={y} stroke="#3A2C22" strokeDasharray="3 4" />
      ))}
      <polygon points={`${points} 240,80 0,80`} fill={`url(#area-${id})`} />
      <polyline
        points={points}
        fill="none"
        stroke="#FFB020"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

/** Return on ad spend rising month by month. */
function ReturnBars() {
  const bars = [22, 30, 27, 40, 52, 66];
  return (
    <svg viewBox="0 0 240 80" preserveAspectRatio="none" className="h-full w-full">
      <line x1="0" x2="240" y1="79" y2="79" stroke="#3A2C22" />
      {bars.map((h, i) => (
        <rect
          key={i}
          x={8 + i * 39}
          y={79 - h}
          width="26"
          height={h}
          rx="4"
          fill={i === bars.length - 1 ? "#E8552B" : "#FFB020"}
          opacity={i === bars.length - 1 ? 1 : 0.35 + i * 0.12}
        />
      ))}
    </svg>
  );
}

const tiles = [
  "bg-sunrise",
  "bg-primary/80",
  "bg-ember/80",
  "bg-sun-soft/90",
  "bg-chart-3/80",
  "bg-sunrise",
  "bg-ember/70",
  "bg-sun-soft/80",
  "bg-primary/70",
  "bg-chart-3/70",
  "bg-sunrise",
  "bg-primary/90",
];

/** A grid of the posts that grew the account. */
function PostGrid() {
  return (
    <div className="grid h-full grid-cols-6 grid-rows-2 gap-1.5">
      {tiles.map((tile, i) => (
        <div key={i} className={cn("relative rounded-md", tile)}>
          {i === 1 && <Play className="absolute right-1 top-1 h-3 w-3 fill-white text-white" />}
          {i === 6 && <Play className="absolute right-1 top-1 h-3 w-3 fill-white text-white" />}
          {i === 10 && <Heart className="absolute bottom-1 left-1 h-3 w-3 fill-white text-white" />}
        </div>
      ))}
    </div>
  );
}
