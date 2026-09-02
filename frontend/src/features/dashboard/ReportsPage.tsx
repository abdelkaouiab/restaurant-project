import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { ContentState } from '../../components/ContentState';
import { messageErreur } from '../utilisateurs/erreurs';
import { dashboardApi } from './dashboardApi';
import { PeriodeForm, validerPeriode } from './PeriodeForm';
import { ManagerDashboard } from './RoleDashboardPage';
import type { TableauBordFinancier, TableauBordOperations } from './types';

function dateIso(decalage: number): string { const date = new Date(); date.setUTCDate(date.getUTCDate() + decalage); return date.toISOString().slice(0, 10); }

export function ReportsPage() {
  const [debut, setDebut] = useState(() => dateIso(-29));
  const [fin, setFin] = useState(() => dateIso(0));
  const [periode, setPeriode] = useState({ debut, fin });
  const [operations, setOperations] = useState<TableauBordOperations | null>(null);
  const [finances, setFinances] = useState<TableauBordFinancier | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [erreurPeriode, setErreurPeriode] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const controleur = useRef<AbortController | null>(null);

  const charger = useCallback(async () => {
    controleur.current?.abort(); const suivant = new AbortController(); controleur.current = suivant;
    setChargement(true); setErreur('');
    try {
      const [operationsRecues, financesRecues] = await Promise.all([dashboardApi.operations(periode, suivant.signal), dashboardApi.finances(periode, suivant.signal)]);
      setOperations(operationsRecues); setFinances(financesRecues); setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true); else setErreur(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargement(false); }
  }, [periode]);
  useEffect(() => { void charger(); return () => controleur.current?.abort(); }, [charger]);
  const appliquer = () => { const validation = validerPeriode(debut, fin); setErreurPeriode(validation); if (!validation) setPeriode({ debut, fin }); };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les rapports financiers." /></section>;
  return <section className="dashboard-page reports-page"><header className="section-heading"><div><p className="eyebrow">Pilotage</p><h1>Rapports</h1><p>Analysez l’activité du restaurant sur une période allant jusqu’à 366 jours.</p></div></header><PeriodeForm debut={debut} fin={fin} busy={chargement} onDebut={setDebut} onFin={setFin} onSubmit={appliquer} />{erreurPeriode && <p className="form-error operation-error" role="alert">{erreurPeriode}</p>}{chargement ? <ContentState type="loading" title="Chargement des rapports" message="Les rapports sont en cours de préparation." /> : erreur ? <ContentState type="error" title="Rapports indisponibles" message={erreur} retry={() => void charger()} /> : operations && finances ? <ManagerDashboard operations={operations} finances={finances} /> : <ContentState type="empty" title="Aucune donnée" message="Aucune donnée n’est disponible pour cette période." />}</section>;
}
