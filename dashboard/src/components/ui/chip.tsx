/**
 * Chip component for tool calls and action buttons.
 */

import { ReactNode } from "react";

interface ChipProps {
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
  variant?: "default" | "action";
}

export function Chip({ label, icon, onClick, variant = "default" }: ChipProps) {
  const isButton = !!onClick || variant === "action";
  const Component = isButton ? "button" : "span";

  return (
    <Component
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] bg-[#DDDDD9] ${
        isButton
          ? "text-[#111111] hover:bg-[#C5C5C2] transition-colors cursor-pointer"
          : "text-[#888888]"
      }`}
    >
      {icon}
      {label}
    </Component>
  );
}
