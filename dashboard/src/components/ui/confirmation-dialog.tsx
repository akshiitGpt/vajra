/**
 * Action sheet confirmation dialog.
 */

interface ConfirmationDialogProps {
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  destructive?: boolean;
}

export function ConfirmationDialog({
  message,
  confirmLabel,
  cancelLabel = "Cancel",
  onConfirm,
  onCancel,
  destructive = false,
}: ConfirmationDialogProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center pb-6">
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      <div className="relative w-[calc(100%-24px)] max-w-xs">
        <div className="bg-white/95 backdrop-blur overflow-hidden mb-2">
          <div className="px-4 py-3 text-center border-b border-[#C5C5C2]">
            <p className="text-[13px] text-[#888888]">{message}</p>
          </div>
          <button
            onClick={onConfirm}
            className={`w-full py-3.5 text-[17px] font-normal ${
              destructive ? "text-[#D32F2F]" : "text-[#111111]"
            }`}
          >
            {confirmLabel}
          </button>
        </div>
        <button
          onClick={onCancel}
          className="w-full py-3.5 text-[17px] font-semibold text-[#111111] bg-white"
        >
          {cancelLabel}
        </button>
      </div>
    </div>
  );
}
