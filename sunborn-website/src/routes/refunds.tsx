import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/site/LegalPage";

export const Route = createFileRoute("/refunds")({
  head: () => ({
    meta: [
      { title: "Refund Policy — SunBorn &Co" },
      { name: "description", content: "Cancellations and refunds for SunBorn &Co plans." },
      { property: "og:title", content: "Refund Policy — SunBorn &Co" },
      { property: "og:description", content: "Plain-English refund policy." },
    ],
  }),
  component: () => (
    <LegalPage
      title="Refund Policy"
      items={[
        ["Monthly plans", "Cancel with 30 days' notice. No refunds for months already started."],
        [
          "Annual plans",
          "Paid upfront and non-refundable once onboarding starts, except where the law requires.",
        ],
        ["Once-off add-ons", "Refundable only if we haven't started the work."],
      ]}
    />
  ),
});
