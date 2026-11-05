import { createFileRoute } from "@tanstack/react-router";
import { LegalPage } from "@/components/site/LegalPage";

export const Route = createFileRoute("/privacy")({
  head: () => ({ meta: [{ title: "Privacy Policy — SunBorn &Co" }, { name: "description", content: "How SunBorn &Co handles your personal information under POPIA." }, { property: "og:title", content: "Privacy Policy — SunBorn &Co" }, { property: "og:description", content: "POPIA-friendly privacy policy." }] }),
  component: () => (
    <LegalPage title="Privacy Policy" items={[
      ["What we collect", "Only what you give us in our forms: your name, contact details and business information."],
      ["Why", "To reply to you, set up your plan and send invoices. We never sell your information."],
      ["Your rights (POPIA)", "You may ask to see, correct or delete your personal information at any time by emailing hello@sunbornco.com."],
      ["Security", "We store data with reputable providers and limit access to the people who need it."],
    ]} />
  ),
});
