import type { ReactNode } from "react";

/**
 * Shared building blocks of the design system (TV5). Every module page uses these so that
 * headers, cards, figures and status badges look and behave the same across M1-M8.
 */

export function PageHeader({ module, eyebrow, title, description, actions }: {
  module?: string;
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {(module || eyebrow) && <p className="eyebrow">{[module, eyebrow].filter(Boolean).join(" · ")}</p>}
        <h1 className="mt-1 text-2xl font-bold leading-tight text-slate-900 md:text-[28px]">{title}</h1>
        {description && <p className="mt-1.5 max-w-3xl text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function Card({ title, subtitle, actions, children, className = "", bodyClassName = "", flush = false, as = "section" }: {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  bodyClassName?: string;
  /** No inner padding, e.g. for a table that runs to the card edges. */
  flush?: boolean;
  as?: "section" | "article" | "div";
}) {
  const Tag = as;
  const hasHead = title != null || actions != null;
  return (
    <Tag className={`card ${className}`}>
      {hasHead && (
        <div className="flex flex-wrap items-start justify-between gap-3 px-5 pt-4">
          <div className="min-w-0">
            {title != null && <h2 className="text-[15px] font-semibold text-slate-900">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
          </div>
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={`${flush ? (hasHead ? "mt-3" : "") : hasHead ? "px-5 pb-5 pt-3" : "p-5"} ${bodyClassName}`}>{children}</div>
    </Tag>
  );
}

type Tone = "brand" | "green" | "amber" | "red" | "slate" | "orange";

const iconTone: Record<Tone, string> = {
  brand: "bg-brand-100 text-brand",
  green: "bg-emerald-50 text-emerald-700",
  amber: "bg-amber-50 text-amber-700",
  orange: "bg-orange-50 text-accent",
  red: "bg-red-50 text-red-600",
  slate: "bg-slate-100 text-slate-600",
};

export function StatCard({ label, value, detail, icon, tone = "brand" }: {
  label: ReactNode;
  value: ReactNode;
  detail?: ReactNode;
  icon?: ReactNode;
  tone?: Tone;
}) {
  return (
    <div className="card flex items-start justify-between gap-3 p-4">
      <div className="min-w-0">
        <p className="text-xs font-medium text-slate-500">{label}</p>
        <p className="mt-1.5 text-[26px] font-bold leading-none tabular-nums text-slate-900">{value}</p>
        {detail && <p className="mt-2 text-xs text-slate-500">{detail}</p>}
      </div>
      {icon && <span aria-hidden className={`grid h-9 w-9 shrink-0 place-items-center rounded-lg text-base font-semibold ${iconTone[tone]}`}>{icon}</span>}
    </div>
  );
}

/** Badge colours follow the meaning of a state, not the module (design system, section 3). */
const GROUPS: { tone: string; states: string[] }[] = [
  { tone: "bg-amber-50 text-amber-800 ring-amber-200", states: ["PENDING", "REQUESTED", "QUEUED", "CREATED", "REPORTED", "DRAFT", "WAITING"] },
  { tone: "bg-sky-50 text-sky-800 ring-sky-200", states: ["CONFIRMED", "SCHEDULED", "RESERVED", "ASSIGNED", "ACKNOWLEDGED", "HELD", "PARTIAL"] },
  { tone: "bg-indigo-50 text-indigo-700 ring-indigo-200", states: ["IN_USE", "OCCUPIED", "CHARGING", "IN_PROGRESS", "RUNNING", "IN_TRANSIT", "CHECKED_IN", "ACTIVE"] },
  { tone: "bg-emerald-50 text-emerald-700 ring-emerald-200", states: ["COMPLETED", "DONE", "RESOLVED", "CLOSED", "APPLIED", "AVAILABLE", "FREE", "APPROVED", "SUCCEEDED", "NORMAL"] },
  { tone: "bg-orange-50 text-orange-700 ring-orange-200", states: ["LOW_BATTERY", "UNDER_REPAIR", "HIGH", "OPEN", "MEDIUM", "MAINTENANCE"] },
  { tone: "bg-red-50 text-red-700 ring-red-200", states: ["FAULTY", "INTERRUPTED", "FAILED", "BLOCKED", "URGENT", "NO_SHOW", "CRITICAL"] },
  { tone: "bg-slate-100 text-slate-600 ring-slate-200", states: ["CANCELLED", "EXPIRED", "DISCARDED", "REJECTED", "DISMISSED", "INACTIVE", "IDLE", "LOW"] },
];

export function toneOf(state: string): string {
  const key = state.toUpperCase().replace(/[\s-]/g, "_");
  return GROUPS.find(g => g.states.includes(key))?.tone ?? "bg-slate-100 text-slate-700 ring-slate-200";
}

/** A coloured pill; the text is always shown, so colour is never the only signal. */
export function StatusBadge({ status, children, className = "" }: { status: string; children?: ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${toneOf(status)} ${className}`}>
      <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {children ?? status}
    </span>
  );
}

export function Tabs<T extends string>({ tabs, value, onChange, label }: {
  tabs: { id: T; label: ReactNode }[];
  value: T;
  onChange: (id: T) => void;
  label?: string;
}) {
  return (
    <nav aria-label={label} className="mb-5 inline-flex flex-wrap gap-1 rounded-xl border border-slate-200 bg-white p-1 shadow-sm">
      {tabs.map(t => (
        <button key={t.id} type="button" aria-pressed={value === t.id} onClick={() => onChange(t.id)}
          className={`rounded-lg px-3.5 py-1.5 text-sm font-semibold transition-colors ${value === t.id ? "bg-brand-100 text-brand" : "text-slate-500 hover:bg-slate-50 hover:text-slate-800"}`}>
          {t.label}
        </button>
      ))}
    </nav>
  );
}

const noticeTone = {
  error: "border-red-200 bg-red-50 text-red-800",
  success: "border-emerald-200 bg-emerald-50 text-emerald-800",
  warning: "border-amber-200 bg-amber-50 text-amber-900",
  info: "border-sky-200 bg-sky-50 text-sky-900",
};

export function Notice({ kind = "info", children, onClose, action, className = "" }: {
  kind?: keyof typeof noticeTone;
  children: ReactNode;
  onClose?: () => void;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div role={kind === "error" ? "alert" : "status"}
      className={`flex items-start justify-between gap-4 rounded-xl border px-4 py-3 text-sm ${noticeTone[kind]} ${className}`}>
      <div className="min-w-0">{children}</div>
      <div className="flex shrink-0 items-center gap-3">
        {action}
        {onClose && <button type="button" className="text-xs font-semibold underline" onClick={onClose}>Dismiss</button>}
      </div>
    </div>
  );
}

export function EmptyState({ title, children, action }: { title?: ReactNode; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center">
      <div aria-hidden className="mx-auto mb-3 grid h-10 w-10 place-items-center rounded-full bg-slate-100 text-slate-400">◎</div>
      {title && <p className="font-semibold text-slate-700">{title}</p>}
      {children && <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: ReactNode; onRetry?: () => void }) {
  return (
    <Notice kind="error" action={onRetry && <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>Retry</button>}>
      {message}
    </Notice>
  );
}

export function PageSkeleton({ blocks = 3 }: { blocks?: number }) {
  return (
    <div className="space-y-4" aria-busy="true">
      <div className="h-4 w-40 animate-pulse rounded bg-slate-200" />
      <div className="h-8 w-72 animate-pulse rounded bg-slate-200" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => <div key={i} className="card h-24 animate-pulse bg-slate-100" />)}
      </div>
      {Array.from({ length: blocks - 1 }).map((_, i) => <div key={i} className="card h-48 animate-pulse bg-slate-100" />)}
    </div>
  );
}

/** Battery level with the design-system thresholds: below 20 % fault, below 50 % busy. */
export function BatteryMeter({ level, width = "w-24" }: { level: number; width?: string }) {
  const color = level < 20 ? "bg-state-fault" : level < 50 ? "bg-state-busy" : "bg-state-available";
  return (
    <div className="flex items-center gap-2">
      <div className={`h-1.5 ${width} overflow-hidden rounded-full bg-slate-200`}>
        <div className={`h-full rounded-full ${color}`} style={{ width: `${Math.max(0, Math.min(100, level))}%` }} />
      </div>
      <span className="text-xs tabular-nums text-slate-600">{level}%</span>
    </div>
  );
}
