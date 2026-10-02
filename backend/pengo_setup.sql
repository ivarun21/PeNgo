-- =====================================================================
-- PeNgo app: database update script
-- Run once in the SQL editor (Dashboard > SQL Editor > New query > Run).
-- Safe to re-run: every policy/trigger is dropped and recreated.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Catalog: anyone can read stores, products, images, categories
-- ---------------------------------------------------------------------
alter table public.categories     enable row level security;
alter table public.stores         enable row level security;
alter table public.products       enable row level security;
alter table public.product_images enable row level security;
alter table public.coupons        enable row level security;
alter table public.reviews        enable row level security;

drop policy if exists "pengo read categories" on public.categories;
create policy "pengo read categories" on public.categories for select to anon, authenticated using (true);

drop policy if exists "pengo read stores" on public.stores;
create policy "pengo read stores" on public.stores for select to anon, authenticated using (true);

drop policy if exists "pengo read products" on public.products;
create policy "pengo read products" on public.products for select to anon, authenticated using (true);

drop policy if exists "pengo read product_images" on public.product_images;
create policy "pengo read product_images" on public.product_images for select to anon, authenticated using (true);

drop policy if exists "pengo read coupons" on public.coupons;
create policy "pengo read coupons" on public.coupons for select to authenticated using (is_active is not false);

-- Reviews: everyone reads, users write/delete only their own
drop policy if exists "pengo read reviews" on public.reviews;
create policy "pengo read reviews" on public.reviews for select to anon, authenticated using (true);
drop policy if exists "pengo own reviews insert" on public.reviews;
create policy "pengo own reviews insert" on public.reviews for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "pengo own reviews delete" on public.reviews;
create policy "pengo own reviews delete" on public.reviews for delete to authenticated using (user_id = auth.uid());


-- ---------------------------------------------------------------------
-- 2. Profiles: each user sees/edits only their own row
--    (right now anyone with the app key can read every profile,
--     including plain_password)
-- ---------------------------------------------------------------------
alter table public.profiles enable row level security;

drop policy if exists "pengo own profile select" on public.profiles;
create policy "pengo own profile select" on public.profiles for select to authenticated using (id = auth.uid());
drop policy if exists "pengo own profile insert" on public.profiles;
create policy "pengo own profile insert" on public.profiles for insert to authenticated with check (id = auth.uid());
drop policy if exists "pengo own profile update" on public.profiles;
create policy "pengo own profile update" on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- Passwords are handled only by the auth service. Wipe stored plain-text
-- passwords and make sure nothing can store them again.
update public.profiles set plain_password = null where plain_password is not null;

create or replace function public.pengo_block_plain_password() returns trigger
language plpgsql as $$
begin
  new.plain_password := null;
  return new;
end $$;

drop trigger if exists pengo_block_plain_password on public.profiles;
create trigger pengo_block_plain_password before insert or update on public.profiles
  for each row execute function public.pengo_block_plain_password();


-- ---------------------------------------------------------------------
-- 3. Cart, wishlist, scan history, restock requests
-- ---------------------------------------------------------------------
alter table public.cart            enable row level security;
alter table public.wishlist        enable row level security;
alter table public.scan_history    enable row level security;
alter table public.demand_requests enable row level security;

drop policy if exists "pengo own cart" on public.cart;
create policy "pengo own cart" on public.cart for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "pengo own wishlist" on public.wishlist;
create policy "pengo own wishlist" on public.wishlist for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "pengo own scans" on public.scan_history;
create policy "pengo own scans" on public.scan_history for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- demand_requests.clerk_user_id holds the app user's id as text
drop policy if exists "pengo own demand insert" on public.demand_requests;
create policy "pengo own demand insert" on public.demand_requests for insert to authenticated
  with check (clerk_user_id = auth.uid()::text);
drop policy if exists "pengo own demand select" on public.demand_requests;
create policy "pengo own demand select" on public.demand_requests for select to authenticated
  using (clerk_user_id = auth.uid()::text);

-- Remove duplicate wishlist rows, then block new duplicates
delete from public.wishlist a using public.wishlist b
 where a.user_id = b.user_id and a.product_id = b.product_id and a.id > b.id;
create unique index if not exists wishlist_user_product_uniq on public.wishlist (user_id, product_id);


-- ---------------------------------------------------------------------
-- 4. Orders, order items, exit passes
-- ---------------------------------------------------------------------
alter table public.orders      enable row level security;
alter table public.order_items enable row level security;
alter table public.exit_passes enable row level security;

-- Customers can only READ their orders. Orders, items, payments and exit passes
-- are written exclusively by the server functions in section 9, so prices,
-- totals and payment status can't be faked from a modified app.
drop policy if exists "pengo own orders select" on public.orders;
create policy "pengo own orders select" on public.orders for select to authenticated using (user_id = auth.uid());
drop policy if exists "pengo own orders insert" on public.orders;
drop policy if exists "pengo own orders update" on public.orders;

drop policy if exists "pengo own order items select" on public.order_items;
create policy "pengo own order items select" on public.order_items for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id and o.user_id = auth.uid()));
drop policy if exists "pengo own order items insert" on public.order_items;

drop policy if exists "pengo own exit passes select" on public.exit_passes;
create policy "pengo own exit passes select" on public.exit_passes for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id and o.user_id = auth.uid()));
drop policy if exists "pengo own exit passes insert" on public.exit_passes;

alter table public.exit_passes add column if not exists expires_at  timestamptz;
alter table public.exit_passes add column if not exists verified_by uuid;

create index if not exists orders_user_created_idx  on public.orders (user_id, created_at desc);
create index if not exists orders_store_created_idx on public.orders (store_id, created_at desc);
create index if not exists order_items_order_idx    on public.order_items (order_id);
create index if not exists products_store_idx       on public.products (store_id);
create index if not exists products_barcode_idx     on public.products (barcode);


-- ---------------------------------------------------------------------
-- 5. Wallet & transactions (read-only for users, written by triggers)
-- ---------------------------------------------------------------------
alter table public.wallet       enable row level security;
alter table public.transactions enable row level security;

drop policy if exists "pengo own wallet" on public.wallet;
create policy "pengo own wallet" on public.wallet for select to authenticated using (user_id = auth.uid());
drop policy if exists "pengo own transactions" on public.transactions;
create policy "pengo own transactions" on public.transactions for select to authenticated using (user_id = auth.uid());


-- ---------------------------------------------------------------------
-- 6. Triggers
-- ---------------------------------------------------------------------

-- 6a. Reduce stock when order items are created
create or replace function public.pengo_reduce_stock() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  update public.products
     set stock_quantity = greatest(coalesce(stock_quantity, 0) - new.quantity, 0)
   where id = new.product_id;
  return new;
end $$;

drop trigger if exists pengo_reduce_stock on public.order_items;
create trigger pengo_reduce_stock after insert on public.order_items
  for each row execute function public.pengo_reduce_stock();

-- 6b. Create a wallet for every new profile
create or replace function public.pengo_create_wallet() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.wallet (user_id) values (new.id) on conflict (user_id) do nothing;
  return new;
end $$;

drop trigger if exists pengo_create_wallet on public.profiles;
create trigger pengo_create_wallet after insert on public.profiles
  for each row execute function public.pengo_create_wallet();

-- Wallets for users who signed up before this script
insert into public.wallet (user_id) select id from public.profiles on conflict (user_id) do nothing;

-- 6c. Reward points: 1 point per Rs 10 when an order becomes paid
create or replace function public.pengo_reward_points() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  pts integer;
begin
  if new.payment_status = 'paid'
     and (tg_op = 'INSERT' or coalesce(old.payment_status, '') <> 'paid')
     and new.user_id is not null then
    pts := floor(coalesce(new.total_amount, 0) / 10);
    if pts > 0 then
      insert into public.wallet (user_id, reward_points, updated_at)
      values (new.user_id, pts, now())
      on conflict (user_id) do update
        set reward_points = coalesce(public.wallet.reward_points, 0) + excluded.reward_points,
            updated_at = now();
      insert into public.transactions (user_id, amount, type, description)
      values (new.user_id, pts, 'reward', 'Earned ' || pts || ' points on order #' || upper(left(new.id::text, 8)));
    end if;
  end if;
  return new;
end $$;

drop trigger if exists pengo_reward_points on public.orders;
create trigger pengo_reward_points after insert or update of payment_status on public.orders
  for each row execute function public.pengo_reward_points();

-- 6d. Keep products.rating equal to the average review rating
create or replace function public.pengo_update_rating() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  pid uuid := coalesce(new.product_id, old.product_id);
begin
  update public.products
     set rating = coalesce((select round(avg(rating)::numeric, 1) from public.reviews where product_id = pid), 0)
   where id = pid;
  return null;
end $$;

drop trigger if exists pengo_update_rating on public.reviews;
create trigger pengo_update_rating after insert or update or delete on public.reviews
  for each row execute function public.pengo_update_rating();


-- ---------------------------------------------------------------------
-- 7. Store locations for the Explore map
--    All your stores currently have empty latitude/longitude, so the map
--    can't show them. Replace the numbers with each shop's real location
--    (Google Maps > long-press on the shop > copy the two numbers).
-- ---------------------------------------------------------------------
-- update public.stores set latitude = 28.6139, longitude = 77.2090 where code = 'ALPHA';
-- update public.stores set latitude = 28.6150, longitude = 77.2100 where code = 'BETA';
-- update public.stores set latitude = 28.6160, longitude = 77.2110 where code = 'GAMMA';
-- update public.stores set latitude = 28.6170, longitude = 77.2120 where code = 'DELTA';


-- ---------------------------------------------------------------------
-- 8. Optional sample coupon to test the cart
-- ---------------------------------------------------------------------
insert into public.coupons (code, discount_percent, max_discount, expiry_date, is_active)
values ('PENGO10', 10, 50, now() + interval '1 year', true)
on conflict (code) do nothing;


-- ---------------------------------------------------------------------
-- 9. Store staff (manager / cashier / guard) for the in-app Staff mode.
--    Separate from the Clerk-based store_staff table so both can coexist.
-- ---------------------------------------------------------------------
create table if not exists public.pengo_staff (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references auth.users (id) on delete cascade,
  store_id   uuid not null references public.stores (id) on delete cascade,
  role       text not null check (role in ('manager', 'cashier', 'guard')),
  created_at timestamptz not null default now(),
  unique (user_id, store_id)
);
alter table public.pengo_staff enable row level security;
drop policy if exists "pengo staff own rows" on public.pengo_staff;
create policy "pengo staff own rows" on public.pengo_staff for select to authenticated using (user_id = auth.uid());

create or replace function public.pengo_has_role(p_store uuid, p_roles text[]) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.pengo_staff
     where user_id = auth.uid() and store_id = p_store and role = any (p_roles)
  );
$$;

-- Managers can add, edit and remove products of their own store
drop policy if exists "pengo staff insert products" on public.products;
create policy "pengo staff insert products" on public.products for insert to authenticated
  with check (public.pengo_has_role(store_id, array['manager']));
drop policy if exists "pengo staff update products" on public.products;
create policy "pengo staff update products" on public.products for update to authenticated
  using (public.pengo_has_role(store_id, array['manager']))
  with check (public.pengo_has_role(store_id, array['manager']));
drop policy if exists "pengo staff delete products" on public.products;
create policy "pengo staff delete products" on public.products for delete to authenticated
  using (public.pengo_has_role(store_id, array['manager']));

-- Managers can upload product photos into product-images/<store_id>/...
drop policy if exists "pengo staff upload product images" on storage.objects;
create policy "pengo staff upload product images" on storage.objects for insert to authenticated
  with check (
    bucket_id = 'product-images'
    and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
    and public.pengo_has_role(((storage.foldername(name))[1])::uuid, array['manager'])
  );

create or replace function public.pengo_my_staff_stores() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
           'store_id', s.store_id, 'role', s.role, 'store_name', st.name, 'store_code', st.code)
           order by st.name), '[]'::jsonb)
    from public.pengo_staff s join public.stores st on st.id = s.store_id
   where s.user_id = auth.uid();
$$;

create or replace function public.pengo_list_staff(p_store_id uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.pengo_has_role(p_store_id, array['manager']) then
    raise exception 'PENGO: Only the store manager can see staff';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'user_id', s.user_id, 'role', s.role,
             'name', p.full_name, 'email', coalesce(p.email, u.email)) order by s.created_at)
      from public.pengo_staff s
      left join public.profiles p on p.id = s.user_id
      left join auth.users u on u.id = s.user_id
     where s.store_id = p_store_id), '[]'::jsonb);
end $$;

create or replace function public.pengo_add_staff(p_store_id uuid, p_email text, p_role text) returns void
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid;
begin
  if not public.pengo_has_role(p_store_id, array['manager']) then
    raise exception 'PENGO: Only the store manager can add staff';
  end if;
  if p_role not in ('manager', 'cashier', 'guard') then
    raise exception 'PENGO: Unknown role';
  end if;
  select id into v_uid from auth.users where lower(email) = lower(trim(p_email)) limit 1;
  if v_uid is null then
    raise exception 'PENGO: No PeNgo account uses this email. Ask them to sign up in the app first.';
  end if;
  insert into public.pengo_staff (user_id, store_id, role) values (v_uid, p_store_id, p_role)
  on conflict (user_id, store_id) do update set role = excluded.role;
end $$;

create or replace function public.pengo_remove_staff(p_store_id uuid, p_user_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.pengo_has_role(p_store_id, array['manager']) then
    raise exception 'PENGO: Only the store manager can remove staff';
  end if;
  if p_user_id = auth.uid() then
    raise exception 'PENGO: You can''t remove yourself';
  end if;
  delete from public.pengo_staff where store_id = p_store_id and user_id = p_user_id;
end $$;


-- ---------------------------------------------------------------------
-- 10. Secure checkout, payments and exit passes
--     * Prices, stock and coupon are checked on the server.
--     * Unpaid orders reserve stock for 30 minutes, then auto-cancel.
--     * Exit passes are single-use and valid for 3 hours.
-- ---------------------------------------------------------------------

-- Internal: create (or return) the exit pass of a paid order
create or replace function public.pengo_issue_exit_pass(p_order_id uuid) returns text
language plpgsql security definer set search_path = public as $$
declare
  v_id     text;
  v_store  uuid;
  v_bytes  bytea := substring(uuid_send(gen_random_uuid()) from 1 for 6)
                 || substring(uuid_send(gen_random_uuid()) from 10 for 6);
  alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  i        int;
begin
  select id into v_id from public.exit_passes where order_id = p_order_id;
  if v_id is not null then return v_id; end if;

  select store_id into v_store from public.orders where id = p_order_id;
  if v_store is null then return null; end if;

  v_id := 'PNG-';
  for i in 0..11 loop
    v_id := v_id || substr(alphabet, 1 + (get_byte(v_bytes, i) % 32), 1);
  end loop;

  insert into public.exit_passes (id, order_id, store_id, status, expires_at)
  values (v_id, p_order_id, v_store, 'active', now() + interval '3 hours');
  return v_id;
end $$;

-- Internal: put stock back on hold for an order that expired but was paid anyway
create or replace function public.pengo_revive_order(p_order_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  update public.products p
     set stock_quantity = greatest(coalesce(p.stock_quantity, 0) - oi.quantity, 0)
    from public.order_items oi
   where oi.order_id = p_order_id and oi.product_id = p.id;
end $$;

revoke execute on function public.pengo_issue_exit_pass(uuid) from public, anon, authenticated;
revoke execute on function public.pengo_revive_order(uuid)    from public, anon, authenticated;

-- Cancel unpaid app orders older than 30 minutes and return their stock
create or replace function public.pengo_expire_unpaid_orders() returns integer
language plpgsql security definer set search_path = public as $$
declare
  n int := 0;
  r record;
begin
  for r in
    select id from public.orders
     where status = 'pending'
       and payment_status is distinct from 'paid'
       and user_id is not null
       and created_at < now() - interval '30 minutes'
     for update skip locked
  loop
    update public.products p
       set stock_quantity = coalesce(p.stock_quantity, 0) + oi.quantity
      from public.order_items oi
     where oi.order_id = r.id and oi.product_id = p.id;
    update public.orders set status = 'cancelled' where id = r.id;
    n := n + 1;
  end loop;
  return n;
end $$;

-- Place the whole cart. Creates one order per store and returns
-- {"order_ids": [...], "total": 123.45}
create or replace function public.pengo_checkout(
  p_items  jsonb,
  p_coupon text default null,
  p_method text default 'upi'
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  uid        uuid := auth.uid();
  v_coupon   public.coupons%rowtype;
  v_subtotal numeric := 0;
  v_discount numeric := 0;
  v_share    numeric;
  v_total    numeric := 0;
  v_order_id uuid;
  v_ids      jsonb := '[]'::jsonb;
  r          record;
begin
  if uid is null then raise exception 'PENGO: Please log in again'; end if;
  if p_method not in ('upi', 'counter') then raise exception 'PENGO: Unknown payment method'; end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'PENGO: Your cart is empty';
  end if;

  perform public.pengo_expire_unpaid_orders();

  create temp table if not exists pengo_lines (
    product_id uuid, store_id uuid, name text, price numeric, stock int, qty int
  ) on commit drop;
  truncate pengo_lines;

  insert into pengo_lines
  select p.id, p.store_id, p.name, p.price, coalesce(p.stock_quantity, 0), sum((i ->> 'quantity')::int)
    from jsonb_array_elements(p_items) i
    join public.products p on p.id = (i ->> 'product_id')::uuid
   group by p.id, p.store_id, p.name, p.price, p.stock_quantity
  having sum((i ->> 'quantity')::int) > 0;

  for r in select * from pengo_lines loop
    if r.qty > r.stock then
      if r.stock <= 0 then raise exception 'PENGO: % is out of stock now', r.name; end if;
      raise exception 'PENGO: Only % left of %', r.stock, r.name;
    end if;
    v_subtotal := v_subtotal + r.price * r.qty;
  end loop;
  if v_subtotal <= 0 then raise exception 'PENGO: Your cart is empty'; end if;

  if coalesce(trim(p_coupon), '') <> '' then
    select * into v_coupon from public.coupons
     where code = upper(trim(p_coupon))
       and is_active is not false
       and (expiry_date is null or expiry_date > now());
    if not found then raise exception 'PENGO: Coupon is invalid or expired'; end if;
    v_discount := v_subtotal * coalesce(v_coupon.discount_percent, 0) / 100.0;
    if v_coupon.max_discount is not null then v_discount := least(v_discount, v_coupon.max_discount); end if;
    v_discount := round(v_discount, 2);
  end if;

  for r in select store_id, sum(price * qty) as sub from pengo_lines group by store_id loop
    v_share := round(v_discount * r.sub / v_subtotal, 2);
    insert into public.orders (user_id, total_amount, status, payment_method, payment_status, store_id, delivery_address)
    values (uid, greatest(r.sub - v_share, 0), 'pending', p_method, 'unpaid', r.store_id, 'In-store pickup')
    returning id into v_order_id;

    insert into public.order_items (order_id, product_id, quantity, price_at_purchase)
    select v_order_id, l.product_id, l.qty, l.price
      from pengo_lines l where l.store_id is not distinct from r.store_id;

    v_total := v_total + greatest(r.sub - v_share, 0);
    v_ids := v_ids || to_jsonb(v_order_id);
  end loop;

  return jsonb_build_object('order_ids', v_ids, 'total', round(v_total, 2));
end $$;

-- Customer confirms a UPI payment (reference from the UPI app or the UTR)
create or replace function public.pengo_confirm_upi(p_order_id uuid, p_ref text) returns text
language plpgsql security definer set search_path = public as $$
declare
  o public.orders%rowtype;
begin
  select * into o from public.orders where id = p_order_id and user_id = auth.uid() for update;
  if not found then raise exception 'PENGO: Order not found'; end if;
  if o.payment_status = 'paid' then return public.pengo_issue_exit_pass(o.id); end if;
  if coalesce(trim(p_ref), '') = '' then raise exception 'PENGO: Payment reference is missing'; end if;
  if o.status = 'cancelled' then perform public.pengo_revive_order(o.id); end if;

  update public.orders
     set payment_status = 'paid', status = 'completed', payment_method = 'upi', payment_id = left(trim(p_ref), 64)
   where id = o.id;
  return public.pengo_issue_exit_pass(o.id);
end $$;

-- Cashier collects cash/card for a "pay at counter" order
create or replace function public.pengo_counter_mark_paid(p_order_id uuid, p_method text) returns text
language plpgsql security definer set search_path = public as $$
declare
  o public.orders%rowtype;
begin
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'PENGO: Order not found'; end if;
  if not public.pengo_has_role(o.store_id, array['manager', 'cashier']) then
    raise exception 'PENGO: You are not a cashier at this store';
  end if;
  if o.payment_status = 'paid' then return public.pengo_issue_exit_pass(o.id); end if;
  if o.status = 'cancelled' then perform public.pengo_revive_order(o.id); end if;

  update public.orders
     set payment_status = 'paid', status = 'completed',
         payment_method = case when p_method = 'card' then 'card' else 'cash' end,
         payment_id = 'COUNTER-' || upper(left(auth.uid()::text, 8))
   where id = o.id;
  return public.pengo_issue_exit_pass(o.id);
end $$;

-- Staff scans an exit pass (PNG-XXXX) or an order QR (PENGO-ORDER:<id>)
create or replace function public.pengo_staff_lookup(p_code text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_code  text := trim(coalesce(p_code, ''));
  v_pass  public.exit_passes%rowtype;
  v_order public.orders%rowtype;
  v_kind  text;
begin
  perform public.pengo_expire_unpaid_orders();

  if upper(v_code) like 'PNG-%' then
    v_kind := 'pass';
    select * into v_pass from public.exit_passes where id = upper(v_code);
    if not found then return jsonb_build_object('verdict', 'not_found'); end if;
    select * into v_order from public.orders where id = v_pass.order_id;
  else
    v_kind := 'order';
    v_code := regexp_replace(v_code, '^PENGO-ORDER:', '', 'i');
    if v_code !~* '^[0-9a-f-]{36}$' then return jsonb_build_object('verdict', 'not_found'); end if;
    select * into v_order from public.orders where id = v_code::uuid;
    if not found then return jsonb_build_object('verdict', 'not_found'); end if;
    select * into v_pass from public.exit_passes where order_id = v_order.id;
  end if;

  if not public.pengo_has_role(v_order.store_id, array['manager', 'cashier', 'guard']) then
    return jsonb_build_object('verdict', 'wrong_store');
  end if;

  return jsonb_build_object(
    'kind', v_kind,
    'verdict', case
      when v_order.payment_status is distinct from 'paid' and v_order.status = 'cancelled' then 'cancelled'
      when v_order.payment_status is distinct from 'paid' then 'unpaid'
      when v_pass.id is null then 'no_pass'
      when v_pass.status = 'used' or v_pass.verified_at is not null then 'used'
      when v_pass.expires_at is not null and v_pass.expires_at < now() then 'expired'
      else 'valid' end,
    'pass_id', v_pass.id,
    'verified_at', v_pass.verified_at,
    'expires_at', v_pass.expires_at,
    'order_id', v_order.id,
    'total', v_order.total_amount,
    'payment_method', v_order.payment_method,
    'payment_status', v_order.payment_status,
    'payment_ref', v_order.payment_id,
    'created_at', v_order.created_at,
    'customer', (select full_name from public.profiles where id = v_order.user_id),
    'store_name', (select name from public.stores where id = v_order.store_id),
    'items', coalesce((
      select jsonb_agg(jsonb_build_object(
               'name', coalesce(p.name, 'Item'), 'quantity', oi.quantity,
               'price', oi.price_at_purchase, 'image', p.barcode_image_url))
        from public.order_items oi left join public.products p on p.id = oi.product_id
       where oi.order_id = v_order.id), '[]'::jsonb)
  );
end $$;

-- Guard lets the customer out. Atomic, so a pass can never be used twice.
create or replace function public.pengo_staff_verify_exit(p_pass_id text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_id text;
begin
  update public.exit_passes ep
     set status = 'used', verified_at = now(), verified_by = auth.uid()
   where ep.id = upper(trim(p_pass_id))
     and ep.status = 'active'
     and ep.verified_at is null
     and (ep.expires_at is null or ep.expires_at > now())
     and public.pengo_has_role(ep.store_id, array['manager', 'cashier', 'guard'])
     and exists (select 1 from public.orders o where o.id = ep.order_id and o.payment_status = 'paid')
  returning ep.id into v_id;

  return jsonb_build_object('ok', v_id is not null) || public.pengo_staff_lookup(p_pass_id);
end $$;

-- Optional: auto-cancel expired reservations every 5 minutes even when nobody
-- checks out. Enable the "pg_cron" extension (Database > Extensions) first.
-- select cron.schedule('pengo-expire-orders', '*/5 * * * *', 'select public.pengo_expire_unpaid_orders()');


-- ---------------------------------------------------------------------
-- 11. Store dashboard (sales, top sellers, low / dead stock, requests)
-- ---------------------------------------------------------------------
create or replace function public.pengo_store_dashboard(p_store_id uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_today timestamptz := date_trunc('day', now() at time zone 'Asia/Kolkata') at time zone 'Asia/Kolkata';
begin
  if not public.pengo_has_role(p_store_id, array['manager', 'cashier']) then
    raise exception 'PENGO: You are not staff at this store';
  end if;

  return jsonb_build_object(
    'today_sales',  (select coalesce(sum(total_amount), 0) from public.orders
                      where store_id = p_store_id and payment_status = 'paid' and created_at >= v_today),
    'today_orders', (select count(*) from public.orders
                      where store_id = p_store_id and payment_status = 'paid' and created_at >= v_today),
    'week_sales',   (select coalesce(sum(total_amount), 0) from public.orders
                      where store_id = p_store_id and payment_status = 'paid' and created_at >= v_today - interval '6 days'),
    'month_sales',  (select coalesce(sum(total_amount), 0) from public.orders
                      where store_id = p_store_id and payment_status = 'paid' and created_at >= now() - interval '30 days'),
    'month_orders', (select count(*) from public.orders
                      where store_id = p_store_id and payment_status = 'paid' and created_at >= now() - interval '30 days'),
    'customers_30d',(select count(distinct coalesce(user_id::text, clerk_user_id)) from public.orders
                      where store_id = p_store_id and payment_status = 'paid' and created_at >= now() - interval '30 days'),
    'pending_orders',(select count(*) from public.orders
                      where store_id = p_store_id and status = 'pending' and payment_status is distinct from 'paid'),
    'daily', (
      select jsonb_agg(jsonb_build_object('day', to_char(d, 'Dy'), 'total', coalesce(t, 0)) order by d)
        from (
          select gs::date as d,
                 (select sum(o.total_amount) from public.orders o
                   where o.store_id = p_store_id and o.payment_status = 'paid'
                     and (o.created_at at time zone 'Asia/Kolkata')::date = gs::date) as t
            from generate_series((now() at time zone 'Asia/Kolkata')::date - 6,
                                 (now() at time zone 'Asia/Kolkata')::date, interval '1 day') gs
        ) x),
    'top_products', coalesce((
      select jsonb_agg(x) from (
        select p.id, p.name, sum(oi.quantity)::int as qty,
               sum(oi.quantity * oi.price_at_purchase) as revenue,
               coalesce(p.stock_quantity, 0) as stock
          from public.order_items oi
          join public.orders o on o.id = oi.order_id
          join public.products p on p.id = oi.product_id
         where o.store_id = p_store_id and o.payment_status = 'paid' and o.created_at >= now() - interval '30 days'
         group by p.id, p.name, p.stock_quantity
         order by qty desc limit 5) x), '[]'::jsonb),
    'low_stock', coalesce((
      select jsonb_agg(x) from (
        select id, name, coalesce(stock_quantity, 0) as stock from public.products
         where store_id = p_store_id and coalesce(stock_quantity, 0) <= 5
         order by coalesce(stock_quantity, 0) limit 20) x), '[]'::jsonb),
    'dead_stock', coalesce((
      select jsonb_agg(x) from (
        select p.id, p.name, coalesce(p.stock_quantity, 0) as stock from public.products p
         where p.store_id = p_store_id and coalesce(p.stock_quantity, 0) > 0
           and p.created_at < now() - interval '14 days'
           and not exists (select 1 from public.order_items oi join public.orders o on o.id = oi.order_id
                            where oi.product_id = p.id and o.payment_status = 'paid'
                              and o.created_at >= now() - interval '30 days')
         limit 20) x), '[]'::jsonb),
    'requests', coalesce((
      select jsonb_agg(x) from (
        select p.id, p.name, count(*)::int as n
          from public.demand_requests d join public.products p on p.id::text = d.product_id::text
         where d.store_id::text = p_store_id::text
         group by p.id, p.name order by n desc limit 10) x), '[]'::jsonb),
    'recent_orders', coalesce((
      select jsonb_agg(x) from (
        select o.id, o.total_amount as total, o.payment_status, o.payment_method, o.status, o.created_at,
               (select full_name from public.profiles where id = o.user_id) as customer
          from public.orders o where o.store_id = p_store_id
         order by o.created_at desc limit 15) x), '[]'::jsonb)
  );
end $$;


-- ---------------------------------------------------------------------
-- 12. Make yourself manager of every store (change the email if needed).
--     Add cashiers/guards later from the app: Profile > Staff mode > Team.
-- ---------------------------------------------------------------------
insert into public.pengo_staff (user_id, store_id, role)
select u.id, s.id, 'manager'
  from auth.users u cross join public.stores s
 where lower(u.email) = 'varunkumarx15@gmail.com'
on conflict (user_id, store_id) do nothing;
