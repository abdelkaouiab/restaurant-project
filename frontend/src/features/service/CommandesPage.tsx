import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import type { PageResponse } from '../../types/api';
import type { Client, Produit, TableRestaurantSimple } from '../referentiels/types';
import { messageErreur } from '../utilisateurs/erreurs';
import { serviceApi } from './serviceApi';
import type { Commande, LigneCommande, StatutCommande } from './types';

const PAGE_VIDE: PageResponse<Commande> = { contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 };
const STATUTS: StatutCommande[] = ['BROUILLON', 'CONFIRMEE', 'EN_PREPARATION', 'PRETE', 'PAYEE', 'ANNULEE'];
const LIBELLES_STATUTS: Record<StatutCommande, string> = {
  BROUILLON: 'Brouillon', CONFIRMEE: 'Confirmée', EN_PREPARATION: 'En préparation',
  PRETE: 'Prête', PAYEE: 'Payée', ANNULEE: 'Annulée',
};

function prix(valeur: number): string {
  return Number(valeur).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function messageOperation(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) return `Cette commande vient d’être modifiée : ${error.message}`;
  if (error instanceof ApiError && error.status === 403) return 'Vous n’avez pas l’autorisation d’effectuer cette action.';
  return messageErreur(error);
}

interface CommandeDetailProps {
  commande: Commande;
  tables: TableRestaurantSimple[];
  clients: Client[];
  produits: Produit[];
  peutGerer: boolean;
  onClose: () => void;
  onUpdated: (commande: Commande) => void;
}

function CommandeDetail({ commande, tables, clients, produits, peutGerer, onClose, onUpdated }: CommandeDetailProps) {
  const modifiable = peutGerer && commande.statut === 'BROUILLON';
  const annulable = peutGerer && (commande.statut === 'BROUILLON' || commande.statut === 'CONFIRMEE');
  const [tableId, setTableId] = useState(String(commande.table.id));
  const [clientId, setClientId] = useState(commande.client ? String(commande.client.id) : '');
  const [produitId, setProduitId] = useState('');
  const [quantiteAjout, setQuantiteAjout] = useState('1');
  const [remarqueAjout, setRemarqueAjout] = useState('');
  const [quantites, setQuantites] = useState<Record<number, string>>(() => Object.fromEntries(commande.lignes.map((ligne) => [ligne.id, String(ligne.quantite)])));
  const [remarques, setRemarques] = useState<Record<number, string>>({});
  const [erreur, setErreur] = useState('');
  const [succes, setSucces] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [confirmation, setConfirmation] = useState<'commande' | 'annulation' | { ligne: LigneCommande } | null>(null);

  const appliquer = (modifie: Commande, message: string) => {
    onUpdated(modifie); setSucces(message); setErreur('');
    setQuantites(Object.fromEntries(modifie.lignes.map((ligne) => [ligne.id, String(ligne.quantite)])));
  };

  const executer = async (operation: () => Promise<Commande>, message: string) => {
    setEnvoi(true); setErreur(''); setSucces('');
    try { appliquer(await operation(), message); }
    catch (error: unknown) { setErreur(messageOperation(error)); }
    finally { setEnvoi(false); }
  };

  const modifierAssociation = (event: FormEvent) => {
    event.preventDefault();
    if (!tableId) { setErreur('Sélectionnez une table active.'); return; }
    void executer(() => serviceApi.modifierAssociation(commande.id, { tableId: Number(tableId), ...(clientId ? { clientId: Number(clientId) } : {}) }), 'La table et le client ont été mis à jour.');
  };

  const ajouterLigne = (event: FormEvent) => {
    event.preventDefault();
    const quantite = Number(quantiteAjout);
    if (!produitId) { setErreur('Sélectionnez un produit actif.'); return; }
    if (!Number.isInteger(quantite) || quantite < 1 || quantite > 999) { setErreur('La quantité doit être comprise entre 1 et 999.'); return; }
    if (remarqueAjout.length > 500) { setErreur('La remarque ne doit pas dépasser 500 caractères.'); return; }
    void executer(
      () => serviceApi.ajouterLigne(commande.id, { produitId: Number(produitId), quantite, ...(remarqueAjout.trim() ? { remarque: remarqueAjout.trim() } : {}) }),
      'Le produit a été ajouté et les montants ont été mis à jour.',
    ).then(() => { setProduitId(''); setQuantiteAjout('1'); setRemarqueAjout(''); });
  };

  const modifierLigne = (event: FormEvent, ligne: LigneCommande) => {
    event.preventDefault();
    const quantite = Number(quantites[ligne.id]);
    const remarque = remarques[ligne.id] ?? '';
    if (!Number.isInteger(quantite) || quantite < 1 || quantite > 999) { setErreur('La quantité doit être comprise entre 1 et 999.'); return; }
    if (remarque.length > 500) { setErreur('La remarque ne doit pas dépasser 500 caractères.'); return; }
    void executer(
      () => serviceApi.modifierLigne(commande.id, ligne.id, { quantite, ...(remarque.trim() ? { remarque: remarque.trim() } : {}) }),
      'La ligne et les montants ont été mis à jour.',
    );
  };

  const executerConfirmation = async () => {
    if (!confirmation) return;
    if (typeof confirmation === 'object') {
      await executer(() => serviceApi.supprimerLigne(commande.id, confirmation.ligne.id), 'La ligne a été retirée.');
    } else if (confirmation === 'commande') {
      await executer(() => serviceApi.confirmerCommande(commande.id), 'La commande a été confirmée.');
    } else {
      await executer(() => serviceApi.annulerCommande(commande.id), 'La commande a été annulée.');
    }
    setConfirmation(null);
  };

  return <section className="admin-card detail-panel order-detail" aria-labelledby="commande-detail-title">
    <div className="card-heading"><div><p className="eyebrow">Détail de la commande</p><h2 id="commande-detail-title">{commande.numero}</h2></div><button className="button button-quiet" type="button" onClick={onClose}>Fermer</button></div>
    <dl className="detail-grid"><div><dt>Table</dt><dd>Table {commande.table.numero}</dd></div><div><dt>Client</dt><dd>{commande.client ? `${commande.client.prenom} ${commande.client.nom}` : 'Aucun'}</dd></div><div><dt>État</dt><dd><span className={`status-pill status-${commande.statut.toLowerCase()}`}>{LIBELLES_STATUTS[commande.statut]}</span></dd></div><div><dt>Total</dt><dd><strong>{prix(commande.montantTotal)}</strong></dd></div></dl>
    {commande.statut !== 'BROUILLON' && <p className="form-warning" role="status">Cette commande n’est plus un brouillon. Les associations et les lignes sont en lecture seule.</p>}
    {erreur && <p className="form-error operation-error" role="alert">{erreur}</p>}
    {succes && <p className="form-success" role="status">{succes}</p>}

    {modifiable && <div className="order-editor-grid"><section><h3>Table et client</h3><form className="admin-form compact-form" onSubmit={modifierAssociation}><label htmlFor={`commande-table-${commande.id}`}>Table</label><select id={`commande-table-${commande.id}`} value={tableId} onChange={(event) => setTableId(event.target.value)}>{tables.map((table) => <option key={table.id} value={table.id}>Table {table.numero} — {table.capacite} places</option>)}</select><label htmlFor={`commande-client-${commande.id}`}>Client (facultatif)</label><select id={`commande-client-${commande.id}`} value={clientId} onChange={(event) => setClientId(event.target.value)}><option value="">Aucun client</option>{clients.map((client) => <option key={client.id} value={client.id}>{client.prenom} {client.nom}</option>)}</select><button className="button button-secondary" type="submit" disabled={envoi}>Mettre à jour l’association</button></form></section>
      <section><h3>Ajouter un produit</h3><form className="admin-form compact-form" onSubmit={ajouterLigne}><label htmlFor={`commande-produit-${commande.id}`}>Produit</label><select id={`commande-produit-${commande.id}`} value={produitId} onChange={(event) => setProduitId(event.target.value)}><option value="">Sélectionner un produit</option>{produits.filter((produit) => !commande.lignes.some((ligne) => ligne.produitId === produit.id)).map((produit) => <option key={produit.id} value={produit.id}>{produit.nom}</option>)}</select><label htmlFor={`commande-quantite-ajout-${commande.id}`}>Quantité</label><input id={`commande-quantite-ajout-${commande.id}`} type="number" min="1" max="999" value={quantiteAjout} onChange={(event) => setQuantiteAjout(event.target.value)} /><label htmlFor={`commande-remarque-ajout-${commande.id}`}>Remarque (facultative)</label><textarea id={`commande-remarque-ajout-${commande.id}`} maxLength={500} value={remarqueAjout} onChange={(event) => setRemarqueAjout(event.target.value)} /><button className="button button-secondary" type="submit" disabled={envoi}>Ajouter à la commande</button></form></section></div>}

    <section className="order-lines" aria-labelledby="lignes-commande-title"><div className="card-heading"><div><h3 id="lignes-commande-title">Produits</h3></div></div>
      {commande.lignes.length === 0 ? <ContentState type="empty" title="Commande vide" message="Ajoutez au moins un produit avant de demander la confirmation." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Produit</th><th>Prix unitaire</th><th>Quantité</th><th>Sous-total</th>{modifiable && <th>Actions</th>}</tr></thead><tbody>{commande.lignes.map((ligne) => <tr key={ligne.id}><td><strong>{ligne.produitNom}</strong></td><td>{prix(ligne.prixUnitaire)}</td><td>{modifiable ? <form className="inline-line-form" onSubmit={(event) => modifierLigne(event, ligne)}><label className="visually-hidden" htmlFor={`quantite-ligne-${ligne.id}`}>Quantité de {ligne.produitNom}</label><input id={`quantite-ligne-${ligne.id}`} type="number" min="1" max="999" value={quantites[ligne.id] ?? ligne.quantite} onChange={(event) => setQuantites((courantes) => ({ ...courantes, [ligne.id]: event.target.value }))} /><label className="visually-hidden" htmlFor={`remarque-ligne-${ligne.id}`}>Nouvelle remarque pour {ligne.produitNom}</label><input id={`remarque-ligne-${ligne.id}`} type="text" maxLength={500} placeholder="Nouvelle remarque" value={remarques[ligne.id] ?? ''} onChange={(event) => setRemarques((courantes) => ({ ...courantes, [ligne.id]: event.target.value }))} /><button className="button button-secondary" type="submit" disabled={envoi}>Modifier</button></form> : ligne.quantite}</td><td><strong>{prix(ligne.sousTotal)}</strong></td>{modifiable && <td><button className="button button-danger" type="button" disabled={envoi} onClick={() => setConfirmation({ ligne })}>Retirer</button></td>}</tr>)}</tbody></table></div>}
      <div className="order-total"><span>Total</span><strong>{prix(commande.montantTotal)}</strong></div>
    </section>
    {peutGerer && <div className="detail-actions">{modifiable && <button className="button button-primary" type="button" disabled={envoi} onClick={() => setConfirmation('commande')}>Confirmer la commande</button>}{annulable && <button className="button button-danger" type="button" disabled={envoi} onClick={() => setConfirmation('annulation')}>Annuler la commande</button>}</div>}
    {confirmation && <ConfirmationDialog title={typeof confirmation === 'object' ? 'Retirer cette ligne ?' : confirmation === 'commande' ? 'Confirmer cette commande ?' : 'Annuler cette commande ?'} message={typeof confirmation === 'object' ? 'Le total sera mis à jour après le retrait.' : confirmation === 'commande' ? 'Une commande confirmée ne pourra plus être modifiée librement.' : 'L’annulation sera effectuée si l’état actuel de la commande le permet.'} confirmLabel={typeof confirmation === 'object' ? 'Retirer la ligne' : confirmation === 'commande' ? 'Confirmer la commande' : 'Annuler la commande'} danger={confirmation !== 'commande'} busy={envoi} onCancel={() => setConfirmation(null)} onConfirm={() => void executerConfirmation()} />}
  </section>;
}

export function CommandesPage() {
  const { utilisateur } = useAuth();
  const peutGerer = utilisateur?.permissions.includes('COMMANDE_GERER') ?? false;
  const [resultat, setResultat] = useState(PAGE_VIDE);
  const [rechercheSaisie, setRechercheSaisie] = useState('');
  const [recherche, setRecherche] = useState('');
  const [statut, setStatut] = useState<'' | StatutCommande>('');
  const [tri, setTri] = useState('creeLe');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [page, setPage] = useState(0);
  const [taille, setTaille] = useState(10);
  const [recharger, setRecharger] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [tables, setTables] = useState<TableRestaurantSimple[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [produits, setProduits] = useState<Produit[]>([]);
  const [chargementOptions, setChargementOptions] = useState(peutGerer);
  const [erreurOptions, setErreurOptions] = useState('');
  const [creationVisible, setCreationVisible] = useState(false);
  const [nouvelleTable, setNouvelleTable] = useState('');
  const [nouveauClient, setNouveauClient] = useState('');
  const [erreurOperation, setErreurOperation] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [selection, setSelection] = useState<Commande | null>(null);
  const [chargementDetail, setChargementDetail] = useState(false);

  const actualiser = useCallback(() => setRecharger((courant) => courant + 1), []);

  useEffect(() => {
    if (!peutGerer) return;
    const controleur = new AbortController();
    Promise.all([serviceApi.listerTablesActives(controleur.signal), serviceApi.listerClientsActifs(controleur.signal), serviceApi.listerProduitsActifs(controleur.signal)])
      .then(([tablesActives, clientsActifs, produitsActifs]) => { setTables(tablesActives); setClients(clientsActifs); setProduits(produitsActifs); })
      .catch((error: unknown) => { if (!(error instanceof DOMException && error.name === 'AbortError')) setErreurOptions(messageErreur(error)); })
      .finally(() => { if (!controleur.signal.aborted) setChargementOptions(false); });
    return () => controleur.abort();
  }, [peutGerer]);

  useEffect(() => {
    const controleur = new AbortController();
    setChargement(true); setErreur(''); setAccesRefuse(false);
    serviceApi.listerCommandes({ recherche: recherche || undefined, statut: statut || undefined, page, taille, tri, direction }, controleur.signal)
      .then(setResultat)
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
        else setErreur(messageErreur(error));
      }).finally(() => { if (!controleur.signal.aborted) setChargement(false); });
    return () => controleur.abort();
  }, [direction, page, recherche, recharger, statut, taille, tri]);

  const rechercher = (event: FormEvent) => { event.preventDefault(); setPage(0); setRecherche(rechercheSaisie.trim()); };
  const creer = async (event: FormEvent) => {
    event.preventDefault();
    if (!nouvelleTable) { setErreurOperation('Sélectionnez une table active.'); return; }
    setEnvoi(true); setErreurOperation('');
    try {
      const cree = await serviceApi.creerCommande({ tableId: Number(nouvelleTable), ...(nouveauClient ? { clientId: Number(nouveauClient) } : {}) });
      setCreationVisible(false); setNouvelleTable(''); setNouveauClient(''); setSelection(cree); actualiser();
    } catch (error: unknown) { setErreurOperation(messageOperation(error)); }
    finally { setEnvoi(false); }
  };

  const consulter = async (commande: Commande) => {
    setChargementDetail(true); setErreurOperation(''); setSelection(null);
    try { setSelection(await serviceApi.obtenirCommande(commande.id)); }
    catch (error: unknown) { setErreurOperation(messageOperation(error)); }
    finally { setChargementDetail(false); }
  };

  const mettreAJour = (commande: Commande) => {
    setSelection(commande);
    setResultat((courant) => ({ ...courant, contenu: courant.contenu.map((item) => item.id === commande.id ? commande : item) }));
    if (statut && commande.statut !== statut) actualiser();
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les commandes." /></section>;

  return <section className="section-page users-page service-page">
    <header className="section-heading"><div><p className="eyebrow">Service en salle</p><h1>Commandes</h1><p>Créez et faites évoluer les commandes du service.</p></div>{peutGerer && <button className="button button-primary heading-action" type="button" disabled={chargementOptions || Boolean(erreurOptions)} onClick={() => setCreationVisible((visible) => !visible)}>{creationVisible ? 'Fermer la création' : 'Créer un brouillon'}</button>}</header>
    {peutGerer && chargementOptions && <p className="field-hint" role="status">Chargement des tables, clients et produits actifs…</p>}
    {peutGerer && !creationVisible && erreurOptions && <p className="form-error operation-error" role="alert">Données de sélection indisponibles : {erreurOptions}</p>}
    {creationVisible && peutGerer && <section className="admin-card" aria-labelledby="creation-commande-title"><div className="card-heading"><div><p className="eyebrow">Nouvelle commande</p><h2 id="creation-commande-title">Créer un brouillon</h2></div></div><form className="admin-form service-form" onSubmit={(event) => void creer(event)}><label htmlFor="nouvelle-commande-table">Table</label><select id="nouvelle-commande-table" value={nouvelleTable} onChange={(event) => setNouvelleTable(event.target.value)}><option value="">Sélectionner une table active</option>{tables.map((table) => <option key={table.id} value={table.id}>Table {table.numero} — {table.capacite} places</option>)}</select><label htmlFor="nouvelle-commande-client">Client (facultatif)</label><select id="nouvelle-commande-client" value={nouveauClient} onChange={(event) => setNouveauClient(event.target.value)}><option value="">Aucun client</option>{clients.map((client) => <option key={client.id} value={client.id}>{client.prenom} {client.nom}</option>)}</select>{erreurOptions && <p className="form-error" role="alert">Données de sélection indisponibles : {erreurOptions}</p>}{erreurOperation && <p className="form-error" role="alert">{erreurOperation}</p>}<div className="form-actions"><button className="button button-secondary" type="button" onClick={() => setCreationVisible(false)}>Annuler</button><button className="button button-primary" type="submit" disabled={envoi || chargementOptions || Boolean(erreurOptions)}>{envoi ? 'Création…' : 'Créer le brouillon'}</button></div></form></section>}
    {!creationVisible && erreurOperation && <p className="form-error operation-error" role="alert">{erreurOperation}</p>}
    <section className="admin-card" aria-labelledby="liste-commandes-title"><div className="card-heading"><div><p className="eyebrow">Suivi</p><h2 id="liste-commandes-title">Liste des commandes</h2></div><span className="result-count">{resultat.totalElements} résultat{resultat.totalElements > 1 ? 's' : ''}</span></div><form className="list-toolbar order-toolbar" onSubmit={rechercher}><label className="search-field">Rechercher<span><input type="search" maxLength={100} placeholder="Numéro de commande" value={rechercheSaisie} onChange={(event) => setRechercheSaisie(event.target.value)} /><button className="button button-secondary" type="submit">Rechercher</button></span></label><label>État<select value={statut} onChange={(event) => { setStatut(event.target.value as '' | StatutCommande); setPage(0); }}><option value="">Tous</option>{STATUTS.map((item) => <option value={item} key={item}>{LIBELLES_STATUTS[item]}</option>)}</select></label><label>Trier par<select value={tri} onChange={(event) => { setTri(event.target.value); setPage(0); }}><option value="creeLe">Date de création</option><option value="modifieLe">Dernière modification</option><option value="numero">Numéro</option><option value="statut">État</option><option value="montantTotal">Total</option></select></label><label>Ordre<select value={direction} onChange={(event) => { setDirection(event.target.value as 'asc' | 'desc'); setPage(0); }}><option value="desc">Décroissant</option><option value="asc">Croissant</option></select></label><label>Par page<select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label></form>
      {chargement ? <ContentState type="loading" title="Chargement des commandes" message="Chargement des commandes en cours." /> : erreur ? <ContentState type="error" title="Commandes indisponibles" message={erreur} retry={actualiser} /> : resultat.contenu.length === 0 ? <ContentState type="empty" title="Aucune commande" message="Aucune commande ne correspond aux critères sélectionnés." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Numéro</th><th>Table</th><th>État</th><th>Lignes</th><th>Total</th><th><span className="visually-hidden">Action</span></th></tr></thead><tbody>{resultat.contenu.map((commande) => <tr key={commande.id}><td><strong>{commande.numero}</strong><small>{new Date(commande.creeLe).toLocaleString('fr-FR')}</small></td><td>Table {commande.table.numero}</td><td><span className={`status-pill status-${commande.statut.toLowerCase()}`}>{LIBELLES_STATUTS[commande.statut]}</span></td><td>{commande.lignes.length}</td><td><strong>{prix(commande.montantTotal)}</strong></td><td><button className="button button-quiet" type="button" onClick={() => void consulter(commande)}>Consulter</button></td></tr>)}</tbody></table></div>}
      {!chargement && !erreur && resultat.totalPages > 0 && <nav className="pagination" aria-label="Pagination des commandes"><button className="button button-secondary" type="button" disabled={page === 0} onClick={() => setPage((courante) => Math.max(0, courante - 1))}>Précédent</button><span>Page {resultat.page + 1} sur {resultat.totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages} onClick={() => setPage((courante) => courante + 1)}>Suivant</button></nav>}
    </section>
    {chargementDetail && <ContentState type="loading" title="Chargement de la commande" message="Chargement des lignes et des montants." />}
    {selection && <CommandeDetail key={`${selection.id}-${selection.version}-${selection.statut}`} commande={selection} tables={tables} clients={clients} produits={produits} peutGerer={peutGerer} onClose={() => setSelection(null)} onUpdated={mettreAJour} />}
  </section>;
}
