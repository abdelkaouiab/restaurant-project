import { useEffect, useRef, type KeyboardEvent } from 'react';

interface ConfirmationDialogProps {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmationDialog({
  title,
  message,
  confirmLabel,
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmationDialogProps) {
  const dialogueRef = useRef<HTMLElement>(null);
  const annulerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const elementPrecedent = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    annulerRef.current?.focus();
    return () => elementPrecedent?.focus();
  }, []);

  const gererClavier = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape' && !busy) {
      event.preventDefault();
      onCancel();
      return;
    }
    if (event.key !== 'Tab') return;
    const elements = dialogueRef.current?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    );
    if (!elements?.length) return;
    const premier = elements[0];
    const dernier = elements[elements.length - 1];
    if (event.shiftKey && document.activeElement === premier) {
      event.preventDefault();
      dernier.focus();
    } else if (!event.shiftKey && document.activeElement === dernier) {
      event.preventDefault();
      premier.focus();
    }
  };

  return <div className="dialog-backdrop" role="presentation">
    <section ref={dialogueRef} className="confirmation-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirmation-title" aria-describedby="confirmation-message" onKeyDown={gererClavier}>
      <p className="eyebrow">Confirmation requise</p>
      <h2 id="confirmation-title">{title}</h2>
      <p id="confirmation-message">{message}</p>
      <div className="dialog-actions">
        <button ref={annulerRef} className="button button-secondary" type="button" disabled={busy} onClick={onCancel}>Annuler</button>
        <button className={`button ${danger ? 'button-danger' : 'button-primary'}`} type="button" disabled={busy} onClick={onConfirm}>
          {busy ? 'Traitement…' : confirmLabel}
        </button>
      </div>
    </section>
  </div>;
}
