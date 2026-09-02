import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { ContentState } from '../../components/ContentState';
import { messageErreur } from '../utilisateurs/erreurs';
import { dashboardApi } from './dashboardApi';
import { ACTIONS_JOURNAL, libelleActionJournal, libelleRessourceJournal, RESSOURCES_JOURNAL } from './journalOptions';
import { validerPeriode } from './PeriodeForm';
import type { FiltresJournal, JournalActivite, PageJournal, ResultatActivite, UtilisateurJournal } from './types';

const VIDE: PageJournal = { contenu: [], page: 0, taille: 20, totalElements: 0, totalPages: 0 };
function dateIso(decalage: number): string { const date = new Date(); date.setUTCDate(date.getUTCDate() + decalage); return date.toISOString().slice(0, 10); }

export function JournalPage() {
  const [resultat, setResultat] = useState(VIDE);
  const [debut, setDebut] = useState(() => dateIso(-29));
  const [fin, setFin] = useState(() => dateIso(0));
  const [action, setAction] = useState('');
  const [typeRessource, setTypeRessource] = useState('');
  const [resultatFiltre, setResultatFiltre] = useState<'' | ResultatActivite>('');
  const [utilisateurId, setUtilisateurId] = useState('');
  const [page, setPage] = useState(0);
  const [taille, setTaille] = useState(20);
  const [tri, setTri] = useState<FiltresJournal['tri']>('creeLe');
  const [direction, setDirection] = useState<FiltresJournal['direction']>('desc');
  const [criteres, setCriteres] = useState<Omit<FiltresJournal, 'page' | 'taille' | 'tri' | 'direction'>>({ debut, fin });
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [erreurFiltre, setErreurFiltre] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [utilisateurs, setUtilisateurs] = useState<UtilisateurJournal[]>([]);
  const [chargementUtilisateurs, setChargementUtilisateurs] = useState(true);
  const [avertissementUtilisateurs, setAvertissementUtilisateurs] = useState('');
  const controleur = useRef<AbortController | null>(null);

  const charger = useCallback(async () => {
    controleur.current?.abort(); const suivant = new AbortController(); controleur.current = suivant;
    setChargement(true); setErreur('');
    try {
      setResultat(await dashboardApi.journal({ ...criteres, page, taille, tri, direction }, suivant.signal)); setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true); else setErreur(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargement(false); }
  }, [criteres, direction, page, taille, tri]);
  useEffect(() => { void charger(); return () => controleur.current?.abort(); }, [charger]);
  useEffect(() => {
    const suivant = new AbortController();
    const chargerUtilisateurs = async () => {
      setChargementUtilisateurs(true);
      setAvertissementUtilisateurs('');
      try {
        setUtilisateurs(await dashboardApi.utilisateursJournal(suivant.signal));
      } catch (error: unknown) {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setAvertissementUtilisateurs('La liste des utilisateurs du journal est momentanément indisponible.');
      } finally {
        if (!suivant.signal.aborted) setChargementUtilisateurs(false);
      }
    };
    void chargerUtilisateurs();
    return () => suivant.abort();
  }, []);

  const rechercher = (event: FormEvent) => {
    event.preventDefault();
    const validation = validerPeriode(debut, fin);
    setErreurFiltre(validation); if (validation) return;
    setPage(0); setCriteres({ debut, fin, action: action || undefined, typeRessource: typeRessource || undefined, resultat: resultatFiltre || undefined, utilisateurId: utilisateurId ? Number(utilisateurId) : undefined });
  };
  const nomUtilisateur = (evenement: JournalActivite): string => {
    if (evenement.utilisateurId === null) return 'Système';
    const utilisateur = utilisateurs.find((item) => item.id === evenement.utilisateurId);
    return utilisateur ? `${utilisateur.prenom} ${utilisateur.nom}` : evenement.utilisateurNom ?? `Utilisateur #${evenement.utilisateurId}`;
  };
  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter le journal d’activité." /></section>;

  return <section className="service-page audit-page"><header className="section-heading"><div><p className="eyebrow">Suivi des activités</p><h1>Journal d’activité</h1><p>Consultez l’historique des opérations réalisées dans l’application.</p></div><span className="section-badge">{resultat.totalElements} événement{resultat.totalElements > 1 ? 's' : ''}</span></header><form className="audit-filters" onSubmit={rechercher}><label>Du<input type="date" value={debut} onChange={(event) => setDebut(event.target.value)} /></label><label>Au<input type="date" value={fin} onChange={(event) => setFin(event.target.value)} /></label><label>Action<select value={action} onChange={(event) => setAction(event.target.value)}><option value="">Toutes les actions</option>{ACTIONS_JOURNAL.map((option) => <option key={option.valeur} value={option.valeur}>{option.libelle}</option>)}</select></label><label>Ressource<select value={typeRessource} onChange={(event) => setTypeRessource(event.target.value)}><option value="">Toutes les ressources</option>{RESSOURCES_JOURNAL.map((option) => <option key={option.valeur} value={option.valeur}>{option.libelle}</option>)}</select></label><label>Résultat<select value={resultatFiltre} onChange={(event) => setResultatFiltre(event.target.value as '' | ResultatActivite)}><option value="">Tous</option><option value="SUCCES">Succès</option><option value="ECHEC">Échec</option></select></label><label>Utilisateur<select value={utilisateurId} disabled={chargementUtilisateurs} onChange={(event) => setUtilisateurId(event.target.value)}><option value="">{chargementUtilisateurs ? 'Chargement des utilisateurs…' : 'Tous les utilisateurs'}</option>{utilisateurs.map((utilisateur) => <option key={utilisateur.id} value={utilisateur.id}>{utilisateur.prenom} {utilisateur.nom} — {utilisateur.email}{utilisateur.actif ? '' : ' — inactif'}</option>)}</select></label><label>Trier par<select value={tri} onChange={(event) => { setTri(event.target.value as FiltresJournal['tri']); setPage(0); }}><option value="creeLe">Date</option><option value="action">Action</option><option value="typeRessource">Ressource</option><option value="resultat">Résultat</option></select></label><label>Ordre<select value={direction} onChange={(event) => { setDirection(event.target.value as FiltresJournal['direction']); setPage(0); }}><option value="desc">Décroissant</option><option value="asc">Croissant</option></select></label><label>Par page<select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label><button className="button button-secondary" type="submit" disabled={chargement}>Rechercher</button></form>{avertissementUtilisateurs && <p className="form-warning" role="status">{avertissementUtilisateurs}</p>}{erreurFiltre && <p className="form-error operation-error" role="alert">{erreurFiltre}</p>}{chargement ? <ContentState type="loading" title="Chargement du journal" message="Chargement des événements en cours." /> : erreur ? <ContentState type="error" title="Journal indisponible" message={erreur} retry={() => void charger()} /> : resultat.contenu.length === 0 ? <ContentState type="empty" title="Aucun événement" message="Aucun événement ne correspond aux filtres sélectionnés." /> : <div className="table-scroll"><table className="data-table audit-table"><thead><tr><th>Date</th><th>Action</th><th>Ressource</th><th>Utilisateur</th><th>Résultat</th></tr></thead><tbody>{resultat.contenu.map((item) => <tr key={item.id}><td><time dateTime={item.creeLe}>{new Date(item.creeLe).toLocaleString('fr-FR')}</time></td><td><strong>{libelleActionJournal(item.action)}</strong></td><td>{libelleRessourceJournal(item.typeRessource)}</td><td>{nomUtilisateur(item)}</td><td><span className={`status-pill ${item.resultat === 'SUCCES' ? 'status-active' : 'status-inactive'}`}>{item.resultat === 'SUCCES' ? 'Succès' : 'Échec'}</span></td></tr>)}</tbody></table></div>}<div className="pagination"><button className="button button-secondary" type="button" disabled={page === 0 || chargement} onClick={() => setPage((courante) => courante - 1)}>Précédent</button><span>Page {resultat.totalPages === 0 ? 0 : page + 1} sur {resultat.totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages || chargement} onClick={() => setPage((courante) => courante + 1)}>Suivant</button></div></section>;
}
