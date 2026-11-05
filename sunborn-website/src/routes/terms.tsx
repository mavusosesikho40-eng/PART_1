import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/site/LegalPage";

export const Route = createFileRoute("/terms")({
  head: () => ({
    meta: [
      { title: "Terms of Service — SunBorn &Co" },
      { name: "description", content: "Plain-English terms for SunBorn &Co plans." },
      { property: "og:title", content: "Terms of Service — SunBorn &Co" },
      { property: "og:description", content: "Plain-English terms." },
    ],
  }),
  component: () => (
    <LegalPage
      title="Terms of Service"
      items={[
        [
          "Plans",
          "Monthly plans run month-to-month and can be cancelled with 30 days' notice. Annual plans are paid upfront for 12 months.",
        ],
        [
          "Payment",
          "Prices are in ZAR and exclude 15% VAT. Ad spend is paid directly to the platforms.",
        ],
        [
          "Content ownership",
          "Everything we create for you belongs to you once the relevant invoice is paid.",
        ],
        ["Changes", "Upgrade anytime (pro-rated); downgrades apply from your next billing date."],
      ]}
    />
  ),
});
