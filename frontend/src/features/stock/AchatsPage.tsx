import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import type { PageResponse } from '../../types/api';
import { formatQuantite, MESSAGE_QUANTITE_UNITAIRE, pasQuantite, quantiteCompatibleAvecUnite } from '../../utils/quantites';
import { messageErreur } from '../utilisateurs/erreurs';
import { stockApi } from './stockApi';
import type { Achat, Fournisseur, Ingredient, ReceptionAchatHistorique, StatutAchat } from './types';

const VIDE: PageResponse<Achat> = { contenu: [], page: 0, taille: 20, totalElements: 0, totalPages: 0 };
const STATUTS: StatutAchat[] = ['BROUILLON', 'COMMANDE', 'PARTIELLEMENT_RECU', 'RECU', 'ANNULE'];
const LIBELLES: Record<StatutAchat, string> = { BROUILLON: 'Brouillon', COMMANDE: 'Commandé', PARTIELLEMENT_RECU: 'Partiellement reçu', RECU: 'Reçu', ANNULE: 'Annulé' };
const nombre = (valeur: number, decimales = 3) => Number(valeur).toLocaleString('fr-FR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales });
const messageOperation = (error: unknown) => error instanceof ApiError && error.status === 409
  ? `Cet achat vient d’être modifié ou l’opération a déjà été traitée : ${error.message}` : messageErreur(error);

type Confirmation = { type: 'commande' } | { type: 'annulation' } | { type: 'reception'; lignes: { ligneAchatId: number; quantite: number }[] };

export function AchatsPage() {
  const { utilisateur } = useAuth();
  const peutGerer = utilisateur?.permissions.includes('ACHAT_GERER') ?? false;
  const [resultat, setResultat] = useState(VIDE);
  const [fournisseurs, setFournisseurs] = useState<Fournisseur[]>([]);
  const [ingredients, setIngredients] = useState<Ingredient[]>([]);
  const [selection, setSelection] = useState<Achat | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const controleur = useRef<AbortController | null>(null);
  const controleurReceptions = useRef<AbortController | null>(null);
  const [receptions, setReceptions] = useState<ReceptionAchatHistorique[]>([]);
  const [chargementReceptions, setChargementReceptions] = useState(false);
  const [erreurReceptions, setErreurReceptions] = useState('');
  const [recherche, setRecherche] = useState('');
  const [rechercheAppliquee, setRechercheAppliquee] = useState('');
  const [statut, setStatut] = useState<'' | StatutAchat>('');
  const [page, setPage] = useState(0);
  const [taille, setTaille] = useState(20);
  const [tri, setTri] = useState<'numero' | 'statut' | 'montantTotal' | 'creeLe'>('creeLe');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [fournisseurId, setFournisseurId] = useState('');
  const [ingredientId, setIngredientId] = useState('');
  const [quantiteLigne, setQuantiteLigne] = useState('');
  const [prixUnitaire, setPrixUnitaire] = useState('');
  const [ligneReceptionId, setLigneReceptionId] = useState('');
  const [quantiteReception, setQuantiteReception] = useState('');
  const [erreurOperation, setErreurOperation] = useState('');
  const [succes, setSucces] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const cleReception = useRef<string | null>(null);
  const operationEnCours = useRef(false);

  const charger = useCallback(async () => {
    controleur.current?.abort(); const suivant = new AbortController(); controleur.current = suivant;
    setChargement(true); setErreur('');
    try {
      const [achats, fournisseursPage, listeIngredients] = await Promise.all([
        stockApi.rechercherAchats({ recherche: rechercheAppliquee, statut: statut || undefined, page, taille, tri, direction }, suivant.signal),
        stockApi.rechercherFournisseurs({ page: 0, taille: 100, tri: 'nom', direction: 'asc' }, suivant.signal),
        stockApi.listerIngredients(suivant.signal),
      ]);
      setResultat(achats); setFournisseurs(fournisseursPage.contenu.filter((item) => item.actif)); setIngredients(listeIngredients.filter((item) => item.actif));
      setSelection((courante) => courante ? achats.contenu.find((item) => item.id === courante.id) ?? courante : null);
      setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true); else setErreur(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargement(false); }
  }, [direction, page, rechercheAppliquee, statut, taille, tri]);
  useEffect(() => { void charger(); return () => controleur.current?.abort(); }, [charger]);

  const chargerReceptions = useCallback(async (achatId: number) => {
    controleurReceptions.current?.abort();
    const suivant = new AbortController();
    controleurReceptions.current = suivant;
    setChargementReceptions(true);
    setErreurReceptions('');
    try {
      setReceptions(await stockApi.listerReceptionsAchat(achatId, suivant.signal));
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      setReceptions([]);
      setErreurReceptions(messageErreur(error));
    } finally {
      if (!suivant.signal.aborted) setChargementReceptions(false);
    }
  }, []);
  const selectionId = selection?.id;
  useEffect(() => {
    if (selectionId !== undefined) void chargerReceptions(selectionId);
    else { controleurReceptions.current?.abort(); setReceptions([]); setErreurReceptions(''); setChargementReceptions(false); }
    return () => controleurReceptions.current?.abort();
  }, [chargerReceptions, selectionId]);

  const appliquer = (achat: Achat, message: string) => {
    setSelection(achat); setResultat((courant) => ({ ...courant, contenu: courant.contenu.map((item) => item.id === achat.id ? achat : item) }));
    setSucces(message); setErreurOperation('');
  };
  const executer = async (operation: () => Promise<Achat>, message: string): Promise<boolean> => {
    if (operationEnCours.current) return false;
    operationEnCours.current = true;
    setEnvoi(true); setErreurOperation(''); setSucces('');
    try { appliquer(await operation(), message); setConfirmation(null); return true; }
    catch (error: unknown) { setErreurOperation(messageOperation(error)); setConfirmation(null); return false; }
    finally { operationEnCours.current = false; setEnvoi(false); }
  };
  const creerAchat = async (event: FormEvent) => {
    event.preventDefault();
    if (!fournisseurId) { setErreurOperation('Sélectionnez un fournisseur actif.'); return; }
    setEnvoi(true); setErreurOperation('');
    try {
      const achat = await stockApi.creerAchat(Number(fournisseurId));
      setResultat((courant) => ({ ...courant, contenu: [achat, ...courant.contenu], totalElements: courant.totalElements + 1 }));
      setSelection(achat); setFournisseurId(''); setSucces('Brouillon créé.');
    } catch (error: unknown) { setErreurOperation(messageOperation(error)); }
    finally { setEnvoi(false); }
  };
  const ajouterLigne = async (event: FormEvent) => {
    event.preventDefault(); if (!selection) return;
    const q = Number(quantiteLigne.replace(',', '.')); const prix = Number(prixUnitaire.replace(',', '.'));
    if (!ingredientId || !Number.isFinite(q) || q <= 0 || !Number.isFinite(prix) || prix <= 0) { setErreurOperation('Sélectionnez un ingrédient, une quantité et un prix unitaire strictement positifs.'); return; }
    if (!quantiteCompatibleAvecUnite(q, ingredients.find((item) => item.id === Number(ingredientId))?.unite)) { setErreurOperation(MESSAGE_QUANTITE_UNITAIRE); return; }
    const reussi = await executer(() => stockApi.ajouterLigneAchat(selection.id, { ingredientId: Number(ingredientId), quantite: q, prixUnitaire: prix }), 'Ligne ajoutée. Le total a été mis à jour.');
    if (reussi) { setIngredientId(''); setQuantiteLigne(''); setPrixUnitaire(''); }
  };
  const changerReception = () => { cleReception.current = null; setErreurOperation(''); };
  const demanderReceptionPartielle = (event: FormEvent) => {
    event.preventDefault(); if (!selection) return;
    const ligne = selection.lignes.find((item) => item.id === Number(ligneReceptionId));
    const q = Number(quantiteReception.replace(',', '.'));
    if (!ligne || !Number.isFinite(q) || q <= 0) { setErreurOperation('Sélectionnez une ligne et une quantité strictement positive.'); return; }
    if (!quantiteCompatibleAvecUnite(q, ligne.unite)) { setErreurOperation(MESSAGE_QUANTITE_UNITAIRE); return; }
    if (q > ligne.quantiteRestante) { setErreurOperation('La quantité reçue ne peut pas dépasser la quantité restante.'); return; }
    if (!cleReception.current) cleReception.current = globalThis.crypto.randomUUID();
    setConfirmation({ type: 'reception', lignes: [{ ligneAchatId: ligne.id, quantite: q }] });
  };
  const demanderReceptionComplete = () => {
    if (!selection) return;
    const lignes = selection.lignes.filter((item) => item.quantiteRestante > 0).map((item) => ({ ligneAchatId: item.id, quantite: item.quantiteRestante }));
    if (lignes.length === 0) { setErreurOperation('Aucune quantité ne reste à réceptionner.'); return; }
    cleReception.current = globalThis.crypto.randomUUID(); setConfirmation({ type: 'reception', lignes });
  };
  const confirmer = async () => {
    if (!selection || !confirmation) return;
    if (confirmation.type === 'commande') await executer(() => stockApi.commanderAchat(selection.id), 'Achat commandé.');
    else if (confirmation.type === 'annulation') await executer(() => stockApi.annulerAchat(selection.id), 'Achat annulé.');
    else if (cleReception.current) {
      const lignes = confirmation.lignes;
      const reussi = await executer(() => stockApi.recevoirAchat(selection.id, cleReception.current!, lignes), 'Réception enregistrée et stock mis à jour.');
      if (reussi) { cleReception.current = null; setLigneReceptionId(''); setQuantiteReception(''); await chargerReceptions(selection.id); }
    }
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les achats." /></section>;
  return <section className="service-page purchases-page">
    <header className="section-heading"><div><p className="eyebrow">Approvisionnement</p><h1>Achats et réceptions</h1><p>Préparez les achats et suivez les quantités reçues.</p></div></header>
    {erreurOperation && <p className="form-error operation-error" role="alert">{erreurOperation}</p>}{succes && <p className="form-success" role="status">{succes}</p>}
    {peutGerer && <form className="admin-card admin-form purchase-create" onSubmit={(event) => void creerAchat(event)}><header className="card-heading"><div><p className="eyebrow">Nouveau</p><h2>Créer un brouillon</h2></div></header><label htmlFor="purchase-supplier">Fournisseur</label><select id="purchase-supplier" value={fournisseurId} onChange={(event) => setFournisseurId(event.target.value)}><option value="">Sélectionner</option>{fournisseurs.map((item) => <option value={item.id} key={item.id}>{item.nom}</option>)}</select><button className="button button-primary" type="submit" disabled={envoi}>Créer le brouillon</button></form>}
    <section className="admin-card"><form className="list-toolbar purchase-toolbar" onSubmit={(event) => { event.preventDefault(); setPage(0); setRechercheAppliquee(recherche.trim()); }}><label className="search-field">Rechercher<span><input type="search" maxLength={100} placeholder="Numéro d’achat" value={recherche} onChange={(event) => setRecherche(event.target.value)} /><button className="button button-secondary" type="submit">Rechercher</button></span></label><label>Statut<select value={statut} onChange={(event) => { setStatut(event.target.value as '' | StatutAchat); setPage(0); }}><option value="">Tous</option>{STATUTS.map((item) => <option value={item} key={item}>{LIBELLES[item]}</option>)}</select></label><label>Trier par<select value={tri} onChange={(event) => { setTri(event.target.value as typeof tri); setPage(0); }}><option value="creeLe">Date</option><option value="numero">Numéro</option><option value="statut">Statut</option><option value="montantTotal">Total</option></select></label><label>Ordre<select value={direction} onChange={(event) => { setDirection(event.target.value as 'asc' | 'desc'); setPage(0); }}><option value="desc">Décroissant</option><option value="asc">Croissant</option></select></label><label>Par page<select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label></form>
      {chargement ? <ContentState type="loading" title="Chargement des achats" message="Chargement des achats en cours." /> : erreur ? <ContentState type="error" title="Achats indisponibles" message={erreur} retry={() => void charger()} /> : resultat.contenu.length === 0 ? <ContentState type="empty" title="Aucun achat" message="Aucun achat ne correspond aux filtres." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Numéro</th><th>Fournisseur</th><th>Statut</th><th>Lignes</th><th>Total</th><th><span className="visually-hidden">Action</span></th></tr></thead><tbody>{resultat.contenu.map((achat) => <tr key={achat.id}><td><strong>{achat.numero}</strong><small>{new Date(achat.creeLe).toLocaleString('fr-FR')}</small></td><td>{achat.fournisseur.nom}</td><td><span className={`status-pill status-${achat.statut.toLowerCase()}`}>{LIBELLES[achat.statut]}</span></td><td>{achat.lignes.length}</td><td><strong>{nombre(achat.montantTotal, 2)}</strong></td><td><button className="button button-quiet" type="button" onClick={() => { setSelection(achat); setErreurOperation(''); setSucces(''); }}>Consulter</button></td></tr>)}</tbody></table></div>}
      <div className="pagination"><button className="button button-secondary" type="button" disabled={page === 0 || chargement} onClick={() => setPage((courante) => courante - 1)}>Précédent</button><span>Page {resultat.totalPages === 0 ? 0 : page + 1} sur {resultat.totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages || chargement} onClick={() => setPage((courante) => courante + 1)}>Suivant</button></div>
    </section>
    {selection && <section className="admin-card purchase-detail" aria-label={`Achat ${selection.numero}`}><header className="card-heading"><div><p className="eyebrow">Détail de l’achat</p><h2>{selection.numero}</h2></div><button className="button button-quiet" type="button" onClick={() => setSelection(null)}>Fermer</button></header><dl className="detail-grid"><div><dt>Fournisseur</dt><dd>{selection.fournisseur.nom}</dd></div><div><dt>Statut</dt><dd>{LIBELLES[selection.statut]}</dd></div><div><dt>Total</dt><dd><strong>{nombre(selection.montantTotal, 2)}</strong></dd></div><div><dt>Créé le</dt><dd>{new Date(selection.creeLe).toLocaleString('fr-FR')}</dd></div></dl>
      {selection.lignes.length === 0 ? <ContentState type="empty" title="Brouillon vide" message="Ajoutez au moins une ligne avant de commander." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Ingrédient</th><th>Commandée</th><th>Déjà reçue</th><th>Restante</th><th>Prix unitaire</th><th>Sous-total</th></tr></thead><tbody>{selection.lignes.map((ligne) => <tr key={ligne.id}><td>{ligne.ingredient}<small>{ligne.unite}</small></td><td>{formatQuantite(ligne.quantiteCommandee, ligne.unite)}</td><td>{formatQuantite(ligne.quantiteRecue, ligne.unite)}</td><td><strong>{formatQuantite(ligne.quantiteRestante, ligne.unite)}</strong></td><td>{nombre(ligne.prixUnitaire, 2)}</td><td>{nombre(ligne.sousTotal, 2)}</td></tr>)}</tbody></table></div>}
      {peutGerer && selection.statut === 'BROUILLON' && <form className="admin-form purchase-line-form" onSubmit={(event) => void ajouterLigne(event)}><h3>Ajouter une ligne</h3><label htmlFor="purchase-ingredient">Ingrédient</label><select id="purchase-ingredient" value={ingredientId} onChange={(event) => setIngredientId(event.target.value)}><option value="">Sélectionner</option>{ingredients.map((item) => <option value={item.id} key={item.id}>{item.nom}</option>)}</select><label htmlFor="purchase-quantity">Quantité commandée</label><input id="purchase-quantity" step={pasQuantite(ingredients.find((item) => item.id === Number(ingredientId))?.unite)} inputMode={ingredients.find((item) => item.id === Number(ingredientId))?.unite === 'UNITE' ? 'numeric' : 'decimal'} value={quantiteLigne} onChange={(event) => setQuantiteLigne(event.target.value)} /><label htmlFor="purchase-price">Prix unitaire d’achat</label><input id="purchase-price" inputMode="decimal" value={prixUnitaire} onChange={(event) => setPrixUnitaire(event.target.value)} /><button className="button button-primary" disabled={envoi} type="submit">Ajouter la ligne</button></form>}
      {peutGerer && (selection.statut === 'COMMANDE' || selection.statut === 'PARTIELLEMENT_RECU') && <div className="purchase-reception"><form className="admin-form" onSubmit={demanderReceptionPartielle}><h3>Réception partielle</h3><label htmlFor="reception-line">Ligne</label><select id="reception-line" value={ligneReceptionId} onChange={(event) => { setLigneReceptionId(event.target.value); changerReception(); }}><option value="">Sélectionner</option>{selection.lignes.filter((item) => item.quantiteRestante > 0).map((item) => <option value={item.id} key={item.id}>{item.ingredient} — reste {formatQuantite(item.quantiteRestante, item.unite)}</option>)}</select><label htmlFor="reception-quantity">Quantité reçue</label><input id="reception-quantity" step={pasQuantite(selection.lignes.find((item) => item.id === Number(ligneReceptionId))?.unite)} inputMode={selection.lignes.find((item) => item.id === Number(ligneReceptionId))?.unite === 'UNITE' ? 'numeric' : 'decimal'} value={quantiteReception} onChange={(event) => { setQuantiteReception(event.target.value); changerReception(); }} /><button className="button button-primary" disabled={envoi} type="submit">Préparer la réception partielle</button></form><div className="full-reception"><h3>Réception complète</h3><p>Réceptionnez toutes les quantités encore attendues.</p><button className="button button-secondary" disabled={envoi} type="button" onClick={demanderReceptionComplete}>Réceptionner tout le restant</button></div></div>}
      {peutGerer && <div className="detail-actions">{selection.statut === 'BROUILLON' && <button className="button button-primary" disabled={envoi || selection.lignes.length === 0} type="button" onClick={() => setConfirmation({ type: 'commande' })}>Commander</button>}{(selection.statut === 'BROUILLON' || selection.statut === 'COMMANDE') && <button className="button button-danger" disabled={envoi} type="button" onClick={() => setConfirmation({ type: 'annulation' })}>Annuler l’achat</button>}</div>}
      <section className="purchase-history" aria-labelledby="purchase-history-title"><header className="card-heading"><div><p className="eyebrow">Traçabilité</p><h3 id="purchase-history-title">Historique des réceptions</h3></div>{!chargementReceptions && !erreurReceptions && <span>{receptions.length} réception{receptions.length > 1 ? 's' : ''}</span>}</header>
        {chargementReceptions ? <ContentState type="loading" title="Chargement des réceptions" message="Chargement de l’historique en cours." /> : erreurReceptions ? <ContentState type="error" title="Historique indisponible" message={erreurReceptions} retry={() => void chargerReceptions(selection.id)} /> : receptions.length === 0 ? <ContentState type="empty" title="Aucune réception" message="Aucune quantité n’a encore été réceptionnée pour cet achat." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Date</th><th>Ingrédient</th><th>Quantité reçue</th></tr></thead><tbody>{receptions.flatMap((reception) => reception.lignes.map((ligne) => <tr key={ligne.id}><td>{new Date(reception.creeLe).toLocaleString('fr-FR')}</td><td>{ligne.ingredient}<small>{ligne.unite}</small></td><td><strong>{formatQuantite(ligne.quantite, ligne.unite)}</strong></td></tr>))}</tbody></table></div>}
      </section>
    </section>}
    {confirmation && <ConfirmationDialog title={confirmation.type === 'commande' ? 'Commander cet achat' : confirmation.type === 'annulation' ? 'Annuler cet achat' : 'Confirmer la réception'} message={confirmation.type === 'reception' ? 'Les quantités reçues seront ajoutées au stock.' : 'Confirmez cette action pour poursuivre.'} confirmLabel={confirmation.type === 'commande' ? 'Commander' : confirmation.type === 'annulation' ? 'Annuler l’achat' : 'Confirmer la réception'} danger={confirmation.type === 'annulation'} busy={envoi} onCancel={() => { if (!envoi) setConfirmation(null); }} onConfirm={() => void confirmer()} />}
  </section>;
}
