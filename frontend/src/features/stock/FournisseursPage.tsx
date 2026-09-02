import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import type { PageResponse } from '../../types/api';
import { messageErreur } from '../utilisateurs/erreurs';
import { stockApi } from './stockApi';
import type { Fournisseur, FournisseurPayload } from './types';

const VIDE: PageResponse<Fournisseur> = { contenu: [], page: 0, taille: 20, totalElements: 0, totalPages: 0 };
const videVersNull = (valeur: string) => valeur.trim() || null;

export function FournisseursPage() {
  const { utilisateur } = useAuth();
  const peutGerer = utilisateur?.permissions.includes('ACHAT_GERER') ?? false;
  const [resultat, setResultat] = useState(VIDE);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [recherche, setRecherche] = useState('');
  const [rechercheAppliquee, setRechercheAppliquee] = useState('');
  const [page, setPage] = useState(0);
  const [taille, setTaille] = useState(20);
  const [tri, setTri] = useState<'nom' | 'creeLe'>('nom');
  const [direction, setDirection] = useState<'asc' | 'desc'>('asc');
  const controleur = useRef<AbortController | null>(null);
  const [edition, setEdition] = useState<Fournisseur | null>(null);
  const [formulaireVisible, setFormulaireVisible] = useState(false);
  const [formulaire, setFormulaire] = useState({ nom: '', email: '', telephone: '', adresse: '' });
  const [erreurFormulaire, setErreurFormulaire] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const nomFournisseurRef = useRef<HTMLInputElement | null>(null);
  const [statutAChanger, setStatutAChanger] = useState<Fournisseur | null>(null);
  const [envoiStatut, setEnvoiStatut] = useState(false);
  const [erreurStatut, setErreurStatut] = useState('');

  const charger = useCallback(async () => {
    controleur.current?.abort(); const suivant = new AbortController(); controleur.current = suivant;
    setChargement(true); setErreur('');
    try {
      setResultat(await stockApi.rechercherFournisseurs({ recherche: rechercheAppliquee, page, taille, tri, direction }, suivant.signal));
      setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
      else setErreur(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargement(false); }
  }, [direction, page, rechercheAppliquee, taille, tri]);
  useEffect(() => { void charger(); return () => controleur.current?.abort(); }, [charger]);

  const preparer = (item?: Fournisseur) => {
    setEdition(item ?? null); setFormulaire({ nom: item?.nom ?? '', email: item?.email ?? '', telephone: item?.telephone ?? '', adresse: item?.adresse ?? '' }); setErreurFormulaire('');
  };
  const ouvrirFormulaire = (item?: Fournisseur) => {
    preparer(item);
    setFormulaireVisible(true);
    requestAnimationFrame(() => nomFournisseurRef.current?.focus());
  };
  const enregistrer = async (event: FormEvent) => {
    event.preventDefault();
    if (!formulaire.nom.trim()) { setErreurFormulaire('Le nom du fournisseur est obligatoire.'); return; }
    if (formulaire.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formulaire.email)) { setErreurFormulaire('L’adresse e-mail est invalide.'); return; }
    const payload: FournisseurPayload = { nom: formulaire.nom.trim(), email: videVersNull(formulaire.email), telephone: videVersNull(formulaire.telephone), adresse: videVersNull(formulaire.adresse) };
    setEnvoi(true); setErreurFormulaire('');
    try {
      const sauvegarde = edition ? await stockApi.modifierFournisseur(edition.id, payload) : await stockApi.creerFournisseur(payload);
      setResultat((courant) => ({ ...courant, contenu: edition ? courant.contenu.map((item) => item.id === sauvegarde.id ? sauvegarde : item) : [sauvegarde, ...courant.contenu], totalElements: edition ? courant.totalElements : courant.totalElements + 1 }));
      preparer(); setFormulaireVisible(false);
    } catch (error: unknown) { setErreurFormulaire(messageErreur(error)); }
    finally { setEnvoi(false); }
  };
  const modifierStatut = async () => {
    if (!statutAChanger || envoiStatut) return;
    setEnvoiStatut(true); setErreurStatut('');
    try {
      const modifie = await stockApi.modifierStatutFournisseur(statutAChanger.id, !statutAChanger.actif, statutAChanger.version);
      setResultat((courant) => ({ ...courant, contenu: courant.contenu.map((item) => item.id === modifie.id ? modifie : item) }));
      if (edition?.id === modifie.id) setEdition(modifie);
      setStatutAChanger(null);
    } catch (error: unknown) {
      setErreurStatut(messageErreur(error)); setStatutAChanger(null);
    } finally { setEnvoiStatut(false); }
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les fournisseurs." /></section>;
  return <section className="service-page suppliers-page">
    <header className="section-heading"><div><p className="eyebrow">Approvisionnement</p><h1>Fournisseurs</h1><p>Répertoire réel utilisé lors de la création des achats.</p></div>{peutGerer && <button className="button button-primary heading-action" type="button" aria-controls="supplier-form" aria-expanded={formulaireVisible} onClick={() => ouvrirFormulaire()}>Nouveau fournisseur</button>}</header>
    {peutGerer && formulaireVisible && <form id="supplier-form" className="admin-card admin-form supplier-form" onSubmit={(event) => void enregistrer(event)}><header className="card-heading"><div><p className="eyebrow">{edition ? 'Modification' : 'Création'}</p><h2>{edition ? edition.nom : 'Fournisseur'}</h2></div></header><label htmlFor="supplier-name">Nom</label><input ref={nomFournisseurRef} id="supplier-name" maxLength={150} value={formulaire.nom} onChange={(event) => setFormulaire({ ...formulaire, nom: event.target.value })} /><label htmlFor="supplier-email">E-mail</label><input id="supplier-email" type="email" maxLength={254} value={formulaire.email} onChange={(event) => setFormulaire({ ...formulaire, email: event.target.value })} /><label htmlFor="supplier-phone">Téléphone</label><input id="supplier-phone" maxLength={30} value={formulaire.telephone} onChange={(event) => setFormulaire({ ...formulaire, telephone: event.target.value })} /><label htmlFor="supplier-address">Adresse</label><textarea id="supplier-address" maxLength={500} value={formulaire.adresse} onChange={(event) => setFormulaire({ ...formulaire, adresse: event.target.value })} />{erreurFormulaire && <p className="form-error" role="alert">{erreurFormulaire}</p>}<div className="form-actions"><button className="button button-secondary" type="button" onClick={() => preparer()}>Effacer</button><button className="button button-primary" type="submit" disabled={envoi}>{envoi ? 'Enregistrement…' : 'Enregistrer'}</button></div></form>}
    <section className="admin-card"><form className="list-toolbar supplier-toolbar" onSubmit={(event) => { event.preventDefault(); setPage(0); setRechercheAppliquee(recherche.trim()); }}><label className="search-field">Rechercher<span><input type="search" maxLength={100} value={recherche} onChange={(event) => setRecherche(event.target.value)} /><button className="button button-secondary" type="submit">Rechercher</button></span></label><label>Trier par<select value={tri} onChange={(event) => { setTri(event.target.value as 'nom' | 'creeLe'); setPage(0); }}><option value="nom">Nom</option><option value="creeLe">Date de création</option></select></label><label>Ordre<select value={direction} onChange={(event) => { setDirection(event.target.value as 'asc' | 'desc'); setPage(0); }}><option value="asc">Croissant</option><option value="desc">Décroissant</option></select></label><label>Par page<select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label></form>
      {erreurStatut && <p className="form-error operation-error" role="alert">{erreurStatut}</p>}
      {chargement ? <ContentState type="loading" title="Chargement des fournisseurs" message="Chargement de la liste en cours." /> : erreur ? <ContentState type="error" title="Fournisseurs indisponibles" message={erreur} retry={() => void charger()} /> : resultat.contenu.length === 0 ? <ContentState type="empty" title="Aucun fournisseur" message="Aucun fournisseur ne correspond à la recherche." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Nom</th><th>Contact</th><th>Adresse</th><th>Statut</th>{peutGerer && <th><span className="visually-hidden">Actions</span></th>}</tr></thead><tbody>{resultat.contenu.map((item) => <tr key={item.id}><td><strong>{item.nom}</strong></td><td>{item.email ?? '—'}<small>{item.telephone ?? 'Aucun téléphone'}</small></td><td>{item.adresse ?? '—'}</td><td><span className={`status-pill ${item.actif ? 'status-active' : 'status-inactive'}`}>{item.actif ? 'Actif' : 'Inactif'}</span></td>{peutGerer && <td><div className="table-actions"><button className="button button-quiet" type="button" aria-controls="supplier-form" onClick={() => ouvrirFormulaire(item)}>Modifier</button><button className={`button ${item.actif ? 'button-danger' : 'button-secondary'}`} type="button" disabled={envoiStatut} onClick={() => { setErreurStatut(''); setStatutAChanger(item); }}>{item.actif ? `Désactiver ${item.nom}` : `Réactiver ${item.nom}`}</button></div></td>}</tr>)}</tbody></table></div>}
      <div className="pagination"><button className="button button-secondary" type="button" disabled={page === 0 || chargement} onClick={() => setPage((courante) => courante - 1)}>Précédent</button><span>Page {resultat.totalPages === 0 ? 0 : page + 1} sur {resultat.totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages || chargement} onClick={() => setPage((courante) => courante + 1)}>Suivant</button></div>
    </section>
    {statutAChanger && <ConfirmationDialog title={`${statutAChanger.actif ? 'Désactiver' : 'Réactiver'} le fournisseur ?`} message={statutAChanger.actif ? 'Le fournisseur ne pourra plus être sélectionné pour un nouvel achat. Les achats existants seront conservés.' : 'Le fournisseur redeviendra sélectionnable pour les nouveaux achats.'} confirmLabel={statutAChanger.actif ? 'Confirmer la désactivation' : 'Confirmer la réactivation'} danger={statutAChanger.actif} busy={envoiStatut} onCancel={() => { if (!envoiStatut) setStatutAChanger(null); }} onConfirm={() => void modifierStatut()} />}
  </section>;
}
