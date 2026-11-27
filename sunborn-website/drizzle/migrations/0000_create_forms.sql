CREATE TABLE public.plan_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reference text UNIQUE NOT NULL CHECK (char_length(reference) <= 20),
  plan_id text NOT NULL CHECK (plan_id IN ('dawn','golden-hour','zenith')),
  billing_cycle text NOT NULL CHECK (billing_cycle IN ('monthly','annual')),
  add_ons text[] NOT NULL DEFAULT '{}',
  full_name text NOT NULL CHECK (char_length(full_name) BETWEEN 1 AND 120),
  business_name text NOT NULL CHECK (char_length(business_name) BETWEEN 1 AND 120),
  email text NOT NULL CHECK (char_length(email) <= 254 AND position('@' in email) > 1),
  phone text CHECK (phone IS NULL OR char_length(phone) <= 40),
  website text CHECK (website IS NULL OR char_length(website) <= 200),
  notes text CHECK (notes IS NULL OR char_length(notes) <= 2000),
  platforms text[] NOT NULL CHECK (cardinality(platforms) >= 1),
  subtotal numeric(12,2) NOT NULL,
  vat numeric(12,2) NOT NULL,
  total numeric(12,2) NOT NULL,
  renewal_amount numeric(12,2) NOT NULL,
  currency text NOT NULL DEFAULT 'ZAR',
  status text NOT NULL DEFAULT 'pending_payment',
  created_at timestamptz NOT NULL DEFAULT now()
);
GRANT INSERT ON public.plan_orders TO anon, authenticated;
GRANT ALL ON public.plan_orders TO service_role;
ALTER TABLE public.plan_orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can place an order" ON public.plan_orders FOR INSERT TO anon, authenticated WITH CHECK (status = 'pending_payment');

CREATE TABLE public.contact_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
  email text NOT NULL CHECK (char_length(email) <= 254 AND position('@' in email) > 1),
  company text CHECK (company IS NULL OR char_length(company) <= 120),
  topic text NOT NULL CHECK (topic IN ('strategy-call','pricing','enterprise','partnership','other')),
  message text NOT NULL CHECK (char_length(message) BETWEEN 10 AND 5000),
  created_at timestamptz NOT NULL DEFAULT now()
);
GRANT INSERT ON public.contact_messages TO anon, authenticated;
GRANT ALL ON public.contact_messages TO service_role;
ALTER TABLE public.contact_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can send a message" ON public.contact_messages FOR INSERT TO anon, authenticated WITH CHECK (true);
