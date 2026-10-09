-- Run this in Supabase -> SQL Editor. Safe to re-run (uses IF NOT EXISTS).
create extension if not exists pgcrypto;

create table if not exists students (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  guardian_email text,                      -- reset codes go ONLY here (set/approved by admin)
  approved boolean not null default true,   -- false = self-enrolled from the phone, awaiting admin review
  notes text,
  message text,                             -- announcement shown on the student's home screen
  pairing_code text unique,                 -- one-time code typed on the phone
  device_token_hash text,                   -- sha256 of the device bearer token
  device_name text,
  device_model text,
  os_version text,
  app_version text,
  setup_missing text[] not null default '{}', -- permissions the student has not granted yet
  offline_secret text not null,             -- used to derive offline unlock codes
  allowed_packages text[] not null default '{}',
  study_minutes int not null default 180,
  break_minutes int not null default 60,
  cycle_anchor timestamptz not null default now(),
  force_mode text check (force_mode in ('study','break')),
  unlock_password_hash text,                -- bcrypt of the secret removal password
  failed_attempts int not null default 0,
  locked_until timestamptz,
  release_requested boolean not null default false,
  last_seen timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists device_apps (
  student_id uuid not null references students(id) on delete cascade,
  package_name text not null,
  label text not null,
  primary key (student_id, package_name)
);

create table if not exists events (
  id bigserial primary key,
  student_id uuid not null references students(id) on delete cascade,
  type text not null,
  detail text,
  device_time timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists events_student_idx on events(student_id, created_at desc);
create index if not exists events_type_idx on events(type, created_at desc);

-- One row per student per local day, uploaded by the phone.
create table if not exists usage_daily (
  student_id uuid not null references students(id) on delete cascade,
  day date not null,
  screen_ms bigint not null default 0,      -- total foreground time of apps (launcher excluded)
  apps jsonb not null default '[]',         -- [{package,label,ms}] top apps
  focus_min int not null default 0,         -- pomodoro focus minutes
  pomodoros int not null default 0,
  tasks_done int not null default 0,
  updated_at timestamptz not null default now(),
  primary key (student_id, day)
);

create table if not exists password_resets (
  id bigserial primary key,
  student_id uuid not null references students(id) on delete cascade,
  code_hash text not null,
  expires_at timestamptz not null,
  attempts int not null default 0,
  used boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists password_resets_idx on password_resets(student_id, created_at desc);

-- Only the server (service role key) touches these tables.
alter table students enable row level security;
alter table device_apps enable row level security;
alter table events enable row level security;
alter table usage_daily enable row level security;
alter table password_resets enable row level security;
