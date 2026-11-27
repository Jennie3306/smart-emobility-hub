import { useId } from "react";

/**
 * The logo: a parking "P" with an amber charging badge — parking and charging, the two
 * things a hub offers. Same artwork as the app icon and the PWA icons
 * (mobile/assets, public/icons), so web, installed web app and Android app match.
 */
export function BrandMark({ className = "h-10 w-10" }: { className?: string }) {
  const gradient = `brand-${useId().replace(/:/g, "")}`;
  return (
    <svg aria-hidden viewBox="0 0 1024 1024" className={`shrink-0 ${className}`}>
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#2E75B6" />
          <stop offset="1" stopColor="#1F4E79" />
        </linearGradient>
      </defs>
      <rect width="1024" height="1024" rx="230" fill={`url(#${gradient})`} />
      <g transform="translate(-76.8 -76.8) scale(1.15)">
        <g transform="translate(-25.6 -25.6) scale(1.05)">
          <path d="M420 760 V290 H560 A 135 135 0 0 1 560 560 H420" fill="none" stroke="#fff"
            strokeWidth="92" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="650" cy="650" r="118" fill="#D9822B" />
          <polygon fill="#fff"
            points="667.36,575.6 612.8,662.4 648.76,662.4 632.64,724.4 687.2,634.5 651.24,634.5" />
        </g>
      </g>
    </svg>
  );
}
