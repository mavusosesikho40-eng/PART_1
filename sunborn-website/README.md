# SunBorn &Co — Website

The website for **SunBorn &Co**, a social media management and marketing agency: the home page,
payment plans, a plan checkout, a contact form and the legal pages. It was built with
[Lovable](https://lovable.dev) and this folder is a copy of that project's code.

- **Lovable editor:** https://lovable.dev/projects/64ed2554-6b6d-499e-99e8-5bf0902cb4ed
- **Preview** (sign in to Lovable to open it): https://id-preview--64ed2554-6b6d-499e-99e8-5bf0902cb4ed.lovable.app

Changes made in the Lovable editor don't appear here by themselves, and changes made here don't go
back to Lovable. Copy them across by hand, or pick one place to keep working in.

## Pages

| Route | What it is |
| --- | --- |
| `/` | Home: hero, platforms, services, how it works, results, payment plans, testimonials, about, FAQ |
| `/pricing` | The payment plans in full: monthly/annual switch, every feature compared, add-ons, billing FAQ |
| `/checkout?plan=golden-hour&billing=annual` | Choose a plan, billing cycle and add-ons, enter your details, see the order total with VAT |
| `/contact?topic=strategy-call` | Contact form; `topic` can be `strategy-call`, `pricing`, `enterprise`, `partnership` or `other` |
| `/terms`, `/privacy`, `/refunds` | Plain-English policy templates |

## Payment plans

Prices are in South African Rand (ZAR) and exclude 15% VAT. Annual billing means paying for
10 months and getting 12.

| Plan | Monthly | Annual |
| --- | --- | --- |
| Dawn | R2,499/month | R24,990/year (R2,083/month) |
| Golden Hour (most popular) | R5,999/month | R59,990/year (R4,999/month) |
| Zenith | R11,999/month | R119,990/year (R9,999/month) |
| Solstice | Custom: "Talk to sales" goes to the contact form | |

Add-ons: extra platform (R1,200/month), Reels boost (R1,800/month), brand photoshoot
(R4,500 once-off) and influencer campaign (R7,500 once-off). Monthly add-ons follow the plan's
billing cycle, so on an annual plan they cost the monthly price × 10.

**No payment is taken on the website yet.** The checkout saves the order as `pending_payment` and
tells the customer an invoice with a secure payment link follows within one business day. To take
card payments on the site, connect a payment provider in Lovable. Both options need the business
owner's own details:

- **Lovable's built-in payments (Paddle):** no API keys, but Lovable asks for the business's legal
  name and acceptance of Paddle's policies, and it needs a paid Lovable plan.
- **Stripe:** paste your own Stripe secret key into Lovable's "Connect Stripe" form, never into chat.
  Subscriptions need customers to sign in.

## Changing things

| To change | Edit |
| --- | --- |
| Plans, prices, features, the comparison table, add-ons | `src/data/plans.ts` |
| Price maths: per-month prices, savings, VAT, totals, renewal, rand formatting | `src/lib/pricing.ts` (tested in `src/test/pricing.test.ts`) |
| Business name, email, phone/WhatsApp, location, hours, social links, VAT rate | `src/config/site.ts` |
| Stats, case studies and testimonials | `src/data/content.ts` |
| Colours and fonts | `src/styles.css` |

**Before going live, replace the sample content:** the stats, case studies and testimonials in
`src/data/content.ts` are made up, the contact details in `src/config/site.ts` are placeholders
(including `hello@sunbornco.com` and `+27 10 000 0000`), the social links point nowhere (`#`), and
the policy pages are templates to have checked.

## Form submissions

Both forms save to the project's Lovable Cloud database (Supabase). Open the project in Lovable and
look under Cloud → Database to read them.

| Table | Filled by |
| --- | --- |
| `plan_orders` | The checkout: reference (e.g. `SB-74D829`), plan, billing cycle, add-ons, contact details, platforms, notes, subtotal, VAT, total, renewal amount, status |
| `contact_messages` | The contact form: name, email, company, topic, message |

Row-level security only lets visitors *add* rows; nobody can read, change or delete submissions
through the website. The order reference is made in the browser for that reason. Each form also
has a hidden "honeypot" field that bots fill in and people don't, and those submissions are
quietly dropped. The tables are defined in `drizzle/migrations/0000_create_forms.sql`.

`.env` holds the database's address and *publishable* key. These are meant to be public: they're
in every visitor's browser anyway, and row-level security is what protects the data. Never put a
`sb_secret_…` or service-role key in this folder.

## Running it

It's a [TanStack Start](https://tanstack.com/start) app (React, TypeScript, Tailwind CSS,
shadcn/ui) and uses [Bun](https://bun.sh):

```bash
bun install
```

```bash
bun run dev
```

then open the address it prints. Other commands:

| Command | Does |
| --- | --- |
| `bun run test` | Runs the unit tests (Vitest) |
| `bun run build` | Builds the site for production into `.output/` (a Cloudflare Worker) |
| `bun run lint` | Checks the code with ESLint and Prettier |

The `sunborn-website` GitHub workflow runs the tests and the build on every change to this folder.

## Publishing

Use **Publish** in the Lovable editor, which hosts the site and can add a custom domain. To host it
yourself, `bun run build` produces a Cloudflare Worker in `.output/`; deploy it with
`npx nitro deploy --prebuilt`.
