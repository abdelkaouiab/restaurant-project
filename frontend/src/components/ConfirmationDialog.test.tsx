import { describe, expect, it, vi } from 'vitest';
import { render, screen, userEvent } from '../test/dom';
import { ConfirmationDialog } from './ConfirmationDialog';

describe('dialogue de confirmation accessible', () => {
  it('place le focus sur l’annulation et se ferme avec Échap', async () => {
    const annuler = vi.fn();
    render(<ConfirmationDialog
      title="Confirmer l’action"
      message="Cette action est sensible."
      confirmLabel="Confirmer"
      onCancel={annuler}
      onConfirm={vi.fn()}
    />);

    const boutonAnnuler = screen.getByRole('button', { name: 'Annuler' });
    expect(document.activeElement).toBe(boutonAnnuler);
    boutonAnnuler.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(annuler).toHaveBeenCalledTimes(1);
  });

  it('désactive les deux actions pendant le traitement', async () => {
    render(<ConfirmationDialog
      title="Confirmer l’action"
      message="Cette action est sensible."
      confirmLabel="Confirmer"
      busy
      onCancel={vi.fn()}
      onConfirm={vi.fn()}
    />);

    const annuler = screen.getByRole('button', { name: 'Annuler' }) as HTMLButtonElement;
    const confirmer = screen.getByRole('button', { name: 'Traitement…' }) as HTMLButtonElement;
    expect(annuler.disabled).toBe(true);
    expect(confirmer.disabled).toBe(true);
    await userEvent.setup().click(confirmer);
  });
});
