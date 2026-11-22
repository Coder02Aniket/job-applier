// Minimal inline SVG icon set. Stroke icons inherit currentColor.
// Each icon is decorative by default (aria-hidden) because it sits next to a label.
import React from 'react';

const base = {
  width: 16,
  height: 16,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
};

export const Radar = (p) => (
  <svg {...base} {...p}>
    <circle cx="12" cy="12" r="9" />
    <circle cx="12" cy="12" r="4.5" />
    <path d="M12 12 19 7" />
  </svg>
);

export const Bolt = (p) => (
  <svg {...base} {...p}>
    <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
  </svg>
);

export const Search = (p) => (
  <svg {...base} {...p}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.2-3.2" />
  </svg>
);

export const Doc = (p) => (
  <svg {...base} {...p}>
    <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z" />
    <path d="M14 3v5h5" />
    <path d="M9 13h6M9 17h6" />
  </svg>
);

export const Mail = (p) => (
  <svg {...base} {...p}>
    <rect x="3" y="5" width="18" height="14" rx="2" />
    <path d="m3 7 9 6 9-6" />
  </svg>
);

export const Check = (p) => (
  <svg {...base} {...p}>
    <path d="m5 13 4 4L19 7" />
  </svg>
);

export const X = (p) => (
  <svg {...base} {...p}>
    <path d="M6 6 18 18M18 6 6 18" />
  </svg>
);

export const Shield = (p) => (
  <svg {...base} {...p}>
    <path d="M12 3 5 6v6c0 4 3 7 7 9 4-2 7-5 7-9V6l-7-3Z" />
    <path d="m9 12 2 2 4-4" />
  </svg>
);

export const Pin = (p) => (
  <svg {...base} {...p}>
    <path d="M12 21s-6-5.2-6-10a6 6 0 1 1 12 0c0 4.8-6 10-6 10Z" />
    <circle cx="12" cy="11" r="2" />
  </svg>
);

export const ArrowUpRight = (p) => (
  <svg {...base} {...p}>
    <path d="M7 17 17 7M9 7h8v8" />
  </svg>
);

export const Inbox = (p) => (
  <svg {...base} {...p}>
    <path d="M4 13h4l2 3h4l2-3h4" />
    <path d="M5 5h14l1 8v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-4l1-8Z" />
  </svg>
);

export const Lock = (p) => (
  <svg {...base} {...p}>
    <rect x="5" y="11" width="14" height="9" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </svg>
);

export const Layers = (p) => (
  <svg {...base} {...p}>
    <path d="m12 3 9 5-9 5-9-5 9-5Z" />
    <path d="m3 13 9 5 9-5" />
  </svg>
);
