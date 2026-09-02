import type { FormEvent } from 'react';

export function validerPeriode(debut: string, fin: string): string {
  if (!debut || !fin) return 'Les deux dates sont obligatoires.';
  const debutMs = Date.parse(`${debut}T00:00:00Z`);
  const finMs = Date.parse(`${fin}T00:00:00Z`);
  if (!Number.isFinite(debutMs) || !Number.isFinite(finMs)) return 'La période est invalide.';
  if (finMs < debutMs) return 'La date de fin doit être postérieure à la date de début.';
  if ((finMs - debutMs) / 86_400_000 > 365) return 'La période ne peut pas dépasser 366 jours.';
  return '';
}

interface PeriodeFormProps {
  debut: string;
  fin: string;
  busy: boolean;
  onDebut: (valeur: string) => void;
  onFin: (valeur: string) => void;
  onSubmit: () => void;
}

export function PeriodeForm({ debut, fin, busy, onDebut, onFin, onSubmit }: PeriodeFormProps) {
  const soumettre = (event: FormEvent) => { event.preventDefault(); onSubmit(); };
  return <form className="period-form" onSubmit={soumettre} aria-label="Filtrer par période">
    <label htmlFor="period-start">Du<input id="period-start" type="date" value={debut} onChange={(event) => onDebut(event.target.value)} /></label>
    <label htmlFor="period-end">Au<input id="period-end" type="date" value={fin} onChange={(event) => onFin(event.target.value)} /></label>
    <button className="button button-secondary" type="submit" disabled={busy}>{busy ? 'Actualisation…' : 'Appliquer'}</button>
  </form>;
}
