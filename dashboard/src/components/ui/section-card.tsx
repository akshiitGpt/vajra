"use client";

/**
 * Section card with optional header.
 */

import { ReactNode } from "react";

interface SectionCardProps {
  children: ReactNode;
  header?: ReactNode;
}

export function SectionCard({ children, header }: SectionCardProps) {
  return (
    <div className="bg-white overflow-hidden">
      {header && (
        <div className="px-3 py-2.5 border-b border-[#C5C5C2]/50">
          {header}
        </div>
      )}
      {children}
    </div>
  );
}
