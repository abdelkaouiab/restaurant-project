import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { ApiError, apiUrl } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import type { PageResponse } from '../../types/api';
import { messageErreur } from '../utilisateurs/erreurs';
import { CONFIGURATIONS_REFERENTIELS } from './config';
import { ReferentielForm } from './ReferentielForm';
import { ACCEPT_IMAGE_PRODUIT, erreurValidationImageProduit } from './imageProduit';
import { referentielsApi } from './referentielsApi';
import type {
  Categorie,
  Client,
  DirectionTri,
  Employe,
  EntiteReferentiel,
  ModuleReferentielId,
  Produit,
  TableRestaurant,
} from './types';

const PAGE_VIDE: PageResponse<EntiteReferentiel> = { contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 };

function formatDate(valeur: string): string {
  return new Date(valeur).toLocaleString('fr-FR');
}

function formatPrix(valeur: number): string {
  return Number(valeur).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const LIBELLES_STATUT_TABLE: Record<TableRestaurant['statutOperationnel'], string> = {
  LIBRE: 'Libre',
  RESERVEE: 'Réservée',
  OCCUPEE: 'Occupée',
  HORS_SERVICE: 'Hors service',
};

function libelleStatut(valeur: string): string {
  return valeur.replaceAll('_', ' ').toLocaleLowerCase('fr-FR');
}

function identite(entite: EntiteReferentiel, module: ModuleReferentielId): string {
  switch (module) {
    case 'employes': return `${(entite as Employe).prenom} ${(entite as Employe).nom}`;
    case 'clients': return `${(entite as Client).prenom} ${(entite as Client).nom}`;
    case 'tables': return `Table ${(entite as TableRestaurant).numero}`;
    case 'categories': return (entite as Categorie).nom;
    case 'produits': return (entite as Produit).nom;
  }
}

function EnteteTable({ module }: { module: ModuleReferentielId }) {
  switch (module) {
    case 'employes': return <><th>Employé</th><th>Poste</th><th>Embauche</th><th>État</th></>;
    case 'clients': return <><th>Client</th><th>E-mail</th><th>Téléphone</th><th>État</th></>;
    case 'tables': return <><th>Numéro</th><th>Capacité</th><th>Statut</th><th>Disponibilité</th><th>Commande</th><th>Réservation</th></>;
    case 'categories': return <><th>Nom</th><th>Description</th><th>État</th></>;
    case 'produits': return <><th>Image</th><th>Produit</th><th>Catégorie</th><th>Prix</th><th>Disponibilité</th><th>État</th></>;
  }
}

function CellulesTable({ entite, module }: { entite: EntiteReferentiel; module: ModuleReferentielId }) {
  const statut = <span className={`status-pill ${entite.actif ? 'status-active' : 'status-inactive'}`}>{entite.actif ? 'Actif' : 'Inactif'}</span>;
  switch (module) {
    case 'employes': {
      const employe = entite as Employe;
      return <><td><strong>{employe.prenom} {employe.nom}</strong><small>{employe.matricule}</small></td><td>{employe.poste}</td><td>{new Date(employe.dateEmbauche).toLocaleDateString('fr-FR')}</td><td>{statut}</td></>;
    }
    case 'clients': {
      const client = entite as Client;
      return <><td><strong>{client.prenom} {client.nom}</strong></td><td>{client.email || '—'}</td><td>{client.telephone || '—'}</td><td>{statut}</td></>;
    }
    case 'tables': {
      const table = entite as TableRestaurant;
      return <>
        <td><strong>Table {table.numero}</strong></td>
        <td>{table.capacite} place{table.capacite > 1 ? 's' : ''}</td>
        <td><span className={`status-pill status-table-${table.statutOperationnel.toLocaleLowerCase()}`}>{LIBELLES_STATUT_TABLE[table.statutOperationnel]}</span></td>
        <td><span className={`status-pill ${table.disponible ? 'status-active' : 'status-inactive'}`}>{table.disponible ? 'Disponible' : 'Indisponible'}</span></td>
        <td>{table.commandeAssociee ? <><strong>{table.commandeAssociee.numero}</strong><small>{libelleStatut(table.commandeAssociee.statut)}</small></> : '—'}</td>
        <td>{table.reservationEnCours ? <><strong>{table.reservationEnCours.numero}</strong><small>{table.reservationEnCours.nombrePersonnes} personne{table.reservationEnCours.nombrePersonnes > 1 ? 's' : ''}</small></> : '—'}</td>
      </>;
    }
    case 'categories': {
      const categorie = entite as Categorie;
      return <><td><strong>{categorie.nom}</strong></td><td className="description-cell">{categorie.description || '—'}</td><td>{statut}</td></>;
    }
    case 'produits': {
      const produit = entite as Produit;
      return <>
        <td>{produit.imageUrl
          ? <img className="product-thumbnail" src={apiUrl(`${produit.imageUrl}?v=${produit.version}`)} crossOrigin="use-credentials" alt="" />
          : <span className="product-image-empty" aria-label="Aucune image">—</span>}</td>
        <td><strong>{produit.nom}</strong></td>
        <td>{produit.categorie.nom}</td>
        <td>{formatPrix(produit.prix)}</td>
        <td><span className={`status-pill ${produit.disponible ? 'status-active' : 'status-inactive'}`}>{produit.disponible ? 'Disponible' : 'Indisponible'}</span></td>
        <td>{statut}</td>
      </>;
    }
  }
}

function DetailValeurs({ entite, module, peutGerer }: { entite: EntiteReferentiel; module: ModuleReferentielId; peutGerer: boolean }) {
  const commun: ReactNode[] = [
    <div key="etat"><dt>État</dt><dd><span className={`status-pill ${entite.actif ? 'status-active' : 'status-inactive'}`}>{entite.actif ? 'Actif' : 'Inactif'}</span></dd></div>,
    <div key="creation"><dt>Créé le</dt><dd>{formatDate(entite.creeLe)}</dd></div>,
    <div key="modification"><dt>Modifié le</dt><dd>{formatDate(entite.modifieLe)}</dd></div>,
  ];
  let specifiques: ReactNode[] = [];
  switch (module) {
    case 'employes': {
      const employe = entite as Employe;
      specifiques = [
        <div key="matricule"><dt>Matricule</dt><dd>{employe.matricule}</dd></div>,
        <div key="poste"><dt>Poste</dt><dd>{employe.poste}</dd></div>,
        <div key="embauche"><dt>Date d’embauche</dt><dd>{new Date(employe.dateEmbauche).toLocaleDateString('fr-FR')}</dd></div>,
        ...(peutGerer ? [<div key="telephone"><dt>Téléphone professionnel</dt><dd>{employe.telephone || 'Non renseigné'}</dd></div>] : []),
      ];
      break;
    }
    case 'clients': {
      const client = entite as Client;
      specifiques = [<div key="email"><dt>E-mail</dt><dd>{client.email || 'Non renseigné'}</dd></div>, <div key="telephone"><dt>Téléphone</dt><dd>{client.telephone || 'Non renseigné'}</dd></div>];
      break;
    }
    case 'tables': {
      const table = entite as TableRestaurant;
      specifiques = [
        <div key="numero"><dt>Numéro</dt><dd>{table.numero}</dd></div>,
        <div key="capacite"><dt>Capacité</dt><dd>{table.capacite} place{table.capacite > 1 ? 's' : ''}</dd></div>,
        <div key="statut-operationnel"><dt>Statut opérationnel</dt><dd>{LIBELLES_STATUT_TABLE[table.statutOperationnel]}</dd></div>,
        <div key="disponibilite"><dt>Disponibilité</dt><dd>{table.disponible ? 'Disponible' : 'Indisponible'}</dd></div>,
        <div key="commande"><dt>Commande associée</dt><dd>{table.commandeAssociee ? `${table.commandeAssociee.numero} — ${libelleStatut(table.commandeAssociee.statut)}` : 'Aucune'}</dd></div>,
        <div key="reservation"><dt>Réservation en cours</dt><dd>{table.reservationEnCours ? `${table.reservationEnCours.numero} — ${table.reservationEnCours.nombrePersonnes} personne${table.reservationEnCours.nombrePersonnes > 1 ? 's' : ''}, de ${formatDate(table.reservationEnCours.debut)} à ${formatDate(table.reservationEnCours.fin)}` : 'Aucune'}</dd></div>,
      ];
      break;
    }
    case 'categories': {
      const categorie = entite as Categorie;
      specifiques = [<div key="description"><dt>Description</dt><dd>{categorie.description || 'Non renseignée'}</dd></div>];
      break;
    }
    case 'produits': {
      const produit = entite as Produit;
      specifiques = [
        <div key="categorie"><dt>Catégorie</dt><dd>{produit.categorie.nom}</dd></div>,
        <div key="prix"><dt>Prix</dt><dd>{formatPrix(produit.prix)}</dd></div>,
        <div key="disponibilite"><dt>Disponibilité métier</dt><dd><span className={`status-pill ${produit.disponible ? 'status-active' : 'status-inactive'}`}>{produit.disponible ? 'Disponible à la commande' : 'Indisponible à la commande'}</span></dd></div>,
        <div key="description"><dt>Description</dt><dd>{produit.description || 'Non renseignée'}</dd></div>,
      ];
      break;
    }
  }
  return <dl className="detail-grid referentiel-detail-grid">{specifiques}{commun}</dl>;
}

interface DetailProps {
  module: ModuleReferentielId;
  entite: EntiteReferentiel;
  peutGerer: boolean;
  onClose: () => void;
  onUpdated: (entite: EntiteReferentiel) => void;
}

function GestionImageProduit({ produit, peutGerer, onUpdated }: { produit: Produit; peutGerer: boolean; onUpdated: (produit: Produit) => void }) {
  const [fichier, setFichier] = useState<File | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState('');
  const [confirmationSuppression, setConfirmationSuppression] = useState(false);

  const selectionner = (selection: File | null) => {
    setErreur('');
    if (!selection) { setFichier(null); return; }
    const erreurValidation = erreurValidationImageProduit(selection);
    if (erreurValidation) {
      setFichier(null);
      setErreur(erreurValidation);
      return;
    }
    setFichier(selection);
  };

  const enregistrer = async () => {
    if (!fichier) return;
    setEnvoi(true); setErreur('');
    try {
      onUpdated(await referentielsApi.enregistrerImageProduit(produit.id, fichier) as Produit);
      setFichier(null);
    } catch (error: unknown) { setErreur(messageErreur(error)); }
    finally { setEnvoi(false); }
  };

  const supprimer = async () => {
    setEnvoi(true); setErreur('');
    try {
      onUpdated(await referentielsApi.supprimerImageProduit(produit.id) as Produit);
      setConfirmationSuppression(false);
    } catch (error: unknown) { setErreur(messageErreur(error)); setConfirmationSuppression(false); }
    finally { setEnvoi(false); }
  };

  return <section className="product-image-panel" aria-labelledby="product-image-title">
    <div>
      <h3 id="product-image-title">Image du produit</h3>
      {produit.imageUrl
        ? <img className="product-detail-image" src={apiUrl(`${produit.imageUrl}?v=${produit.version}`)} crossOrigin="use-credentials" alt={`Présentation de ${produit.nom}`} />
        : <div className="product-detail-image product-detail-image-empty">Aucune image</div>}
    </div>
    {peutGerer && <div className="product-image-actions">
      <label htmlFor={`product-image-${produit.id}`}>PNG, JPEG ou WebP — 5 Mo maximum</label>
      <input id={`product-image-${produit.id}`} type="file" accept={ACCEPT_IMAGE_PRODUIT} disabled={envoi} onChange={(event) => selectionner(event.target.files?.[0] ?? null)} />
      {fichier && <small>Fichier sélectionné : {fichier.name}</small>}
      <div className="detail-actions">
        <button className="button button-secondary" type="button" disabled={!fichier || envoi} onClick={() => void enregistrer()}>{envoi ? 'Envoi…' : produit.imageUrl ? 'Remplacer l’image' : 'Ajouter l’image'}</button>
        {produit.imageUrl && <button className="button button-danger" type="button" disabled={envoi} onClick={() => setConfirmationSuppression(true)}>Retirer l’image</button>}
      </div>
    </div>}
    {erreur && <p className="form-error" role="alert">{erreur}</p>}
    {confirmationSuppression && <ConfirmationDialog
      title="Retirer l’image du produit ?"
      message="L’image sera retirée du produit. Les autres informations seront conservées."
      confirmLabel="Confirmer le retrait"
      danger
      busy={envoi}
      onCancel={() => setConfirmationSuppression(false)}
      onConfirm={() => void supprimer()}
    />}
  </section>;
}

function ReferentielDetail({ module, entite, peutGerer, onClose, onUpdated }: DetailProps) {
  const configuration = CONFIGURATIONS_REFERENTIELS[module];
  const [edition, setEdition] = useState(false);
  const [confirmationStatut, setConfirmationStatut] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState('');

  const modifierStatut = async () => {
    setEnvoi(true);
    setErreur('');
    try {
      onUpdated(await referentielsApi.modifierStatut(configuration, entite.id, !entite.actif));
      setConfirmationStatut(false);
    } catch (error: unknown) {
      setErreur(error instanceof ApiError && error.status === 403 ? 'Vous n’avez pas l’autorisation d’effectuer cette action.' : messageErreur(error));
      setConfirmationStatut(false);
    } finally { setEnvoi(false); }
  };

  return <section className="admin-card detail-panel" aria-labelledby="referentiel-detail-title">
    <div className="card-heading"><div><p className="eyebrow">Détail {configuration.singulier}</p><h2 id="referentiel-detail-title">{identite(entite, module)}</h2></div><button className="button button-quiet" type="button" onClick={onClose}>Fermer</button></div>
    <DetailValeurs entite={entite} module={module} peutGerer={peutGerer} />
    {module === 'produits' && <GestionImageProduit produit={entite as Produit} peutGerer={peutGerer} onUpdated={onUpdated} />}
    {erreur && <p className="form-error" role="alert">{erreur}</p>}
    {peutGerer && !edition && <div className="detail-actions">
      <button className="button button-secondary" type="button" onClick={() => setEdition(true)}>Modifier</button>
      <button className={`button ${entite.actif ? 'button-danger' : 'button-secondary'}`} type="button" onClick={() => setConfirmationStatut(true)}>{entite.actif ? 'Désactiver' : 'Réactiver'}</button>
    </div>}
    {peutGerer && edition && <section className="edit-section"><h3>Modifier les informations</h3><ReferentielForm key={`${module}-${entite.id}-${entite.version}`} configuration={configuration} initial={entite} onCancel={() => setEdition(false)} onSaved={(modifie) => { setEdition(false); onUpdated(modifie); }} /></section>}
    {confirmationStatut && <ConfirmationDialog
      title={`${entite.actif ? 'Désactiver' : 'Réactiver'} ${configuration.demonstratif} ${configuration.singulier} ?`}
      message={entite.actif ? 'La donnée restera dans l’historique mais ne sera plus active.' : 'La donnée redeviendra active et utilisable.'}
      confirmLabel={entite.actif ? 'Confirmer la désactivation' : 'Confirmer la réactivation'}
      danger={entite.actif}
      busy={envoi}
      onCancel={() => setConfirmationStatut(false)}
      onConfirm={() => void modifierStatut()}
    />}
  </section>;
}

export function ReferentielsPage({ module }: { module: ModuleReferentielId }) {
  const { utilisateur } = useAuth();
  const configuration = CONFIGURATIONS_REFERENTIELS[module];
  const peutGerer = utilisateur?.permissions.includes(configuration.permissionGerer) ?? false;
  const [resultat, setResultat] = useState<PageResponse<EntiteReferentiel>>(PAGE_VIDE);
  const [rechercheSaisie, setRechercheSaisie] = useState('');
  const [recherche, setRecherche] = useState('');
  const [actif, setActif] = useState<'' | 'true' | 'false'>('');
  const [disponible, setDisponible] = useState<'' | 'true' | 'false'>('');
  const [categorieId, setCategorieId] = useState('');
  const [categories, setCategories] = useState<Categorie[]>([]);
  const [tri, setTri] = useState(configuration.triDefaut);
  const [direction, setDirection] = useState<DirectionTri>('asc');
  const [taille, setTaille] = useState(10);
  const [page, setPage] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [recharger, setRecharger] = useState(0);
  const [creationVisible, setCreationVisible] = useState(false);
  const [selection, setSelection] = useState<EntiteReferentiel | null>(null);
  const [chargementDetail, setChargementDetail] = useState(false);
  const [erreurDetail, setErreurDetail] = useState('');
  const [avertissementCreation, setAvertissementCreation] = useState('');

  useEffect(() => {
    setResultat(PAGE_VIDE); setRechercheSaisie(''); setRecherche(''); setActif(''); setDisponible(''); setCategorieId('');
    setTri(configuration.triDefaut); setDirection('asc'); setTaille(10); setPage(0); setSelection(null); setCreationVisible(false); setAvertissementCreation('');
  }, [configuration]);

  useEffect(() => {
    if (module !== 'produits') { setCategories([]); return; }
    const controleur = new AbortController();
    referentielsApi.listerCategoriesActives(controleur.signal).then(setCategories).catch(() => setCategories([]));
    return () => controleur.abort();
  }, [module]);

  const charger = useCallback(() => setRecharger((courant) => courant + 1), []);

  useEffect(() => {
    const controleur = new AbortController();
    setChargement(true); setErreur(''); setAccesRefuse(false);
    referentielsApi.rechercher(configuration, {
      recherche: recherche || undefined,
      actif: actif === '' ? undefined : actif === 'true',
      disponible: module === 'produits' && disponible !== '' ? disponible === 'true' : undefined,
      categorieId: module === 'produits' && categorieId ? Number(categorieId) : undefined,
      page, taille, tri, direction,
    }, controleur.signal)
      .then(setResultat)
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
        else setErreur(messageErreur(error));
      })
      .finally(() => { if (!controleur.signal.aborted) setChargement(false); });
    return () => controleur.abort();
  }, [actif, categorieId, configuration, direction, disponible, module, page, recherche, recharger, taille, tri]);

  const rechercher = (event: FormEvent) => {
    event.preventDefault(); setPage(0); setRecherche(rechercheSaisie.trim());
  };

  const consulter = async (entite: EntiteReferentiel) => {
    setChargementDetail(true); setErreurDetail(''); setSelection(null);
    try { setSelection(await referentielsApi.obtenir(configuration, entite.id)); }
    catch (error: unknown) { setErreurDetail(error instanceof ApiError && error.status === 403 ? 'Vous n’avez pas l’autorisation de consulter ce détail.' : messageErreur(error)); }
    finally { setChargementDetail(false); }
  };

  const actualiser = (entite: EntiteReferentiel) => {
    setSelection(entite);
    setResultat((courant) => ({ ...courant, contenu: courant.contenu.map((item) => item.id === entite.id ? entite : item) }));
    if (actif !== '' && entite.actif !== (actif === 'true')) charger();
    if (module === 'produits' && disponible !== '' && (entite as Produit).disponible !== (disponible === 'true')) charger();
  };

  const cree = (entite: EntiteReferentiel, avertissement?: string) => {
    setCreationVisible(false);
    setSelection(entite);
    setAvertissementCreation(avertissement ?? '');
    charger();
  };

  const nombreColonnes = useMemo(() => module === 'tables' || module === 'produits' ? 6 : module === 'categories' ? 3 : 4, [module]);
  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message={`Vous n’avez pas l’autorisation de consulter les ${configuration.titre.toLocaleLowerCase('fr-FR')}.`} /></section>;

  return <section className="section-page users-page referentiels-page">
    <header className="section-heading"><div><p className="eyebrow">Données du restaurant</p><h1>{configuration.titre}</h1><p>{configuration.description}</p></div>{peutGerer && <button className="button button-primary heading-action" type="button" onClick={() => { setAvertissementCreation(''); setCreationVisible((visible) => !visible); }}>{creationVisible ? 'Fermer la création' : `Créer ${configuration.article}`}</button>}</header>
    {creationVisible && peutGerer && <section className="admin-card" aria-labelledby="creation-referentiel-title"><div className="card-heading"><div><p className="eyebrow">Nouvelle donnée</p><h2 id="creation-referentiel-title">Créer {configuration.article}</h2></div></div><ReferentielForm configuration={configuration} onCancel={() => setCreationVisible(false)} onSaved={cree} /></section>}
    {avertissementCreation && <p className="operation-warning" role="alert">{avertissementCreation}</p>}

    <section className="admin-card" aria-labelledby="liste-referentiel-title">
      <div className="card-heading"><div><p className="eyebrow">Référentiel</p><h2 id="liste-referentiel-title">Liste des {configuration.titre.toLocaleLowerCase('fr-FR')}</h2></div><span className="result-count">{resultat.totalElements} résultat{resultat.totalElements > 1 ? 's' : ''}</span></div>
      <form className="list-toolbar referentiel-toolbar" onSubmit={rechercher}>
        <label className="search-field">Rechercher<span><input type="search" maxLength={configuration.rechercheMax} value={rechercheSaisie} onChange={(event) => setRechercheSaisie(event.target.value)} placeholder={configuration.placeholderRecherche} /><button className="button button-secondary" type="submit">Rechercher</button></span></label>
        <label>État<select value={actif} onChange={(event) => { setActif(event.target.value as '' | 'true' | 'false'); setPage(0); }}><option value="">Tous</option><option value="true">Actifs</option><option value="false">Inactifs</option></select></label>
        {module === 'produits' && <label>Disponibilité<select value={disponible} onChange={(event) => { setDisponible(event.target.value as '' | 'true' | 'false'); setPage(0); }}><option value="">Toutes</option><option value="true">Disponibles</option><option value="false">Indisponibles</option></select></label>}
        {module === 'produits' && categories.length > 0 && <label>Catégorie<select value={categorieId} onChange={(event) => { setCategorieId(event.target.value); setPage(0); }}><option value="">Toutes</option>{categories.map((categorie) => <option key={categorie.id} value={categorie.id}>{categorie.nom}</option>)}</select></label>}
        <label>Trier par<select value={tri} onChange={(event) => { setTri(event.target.value); setPage(0); }}>{configuration.tris.map((option) => <option key={option.valeur} value={option.valeur}>{option.libelle}</option>)}</select></label>
        <label>Ordre<select value={direction} onChange={(event) => { setDirection(event.target.value as DirectionTri); setPage(0); }}><option value="asc">Croissant</option><option value="desc">Décroissant</option></select></label>
        <label>Par page<select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label>
      </form>

      {chargement ? <ContentState type="loading" title={`Chargement des ${configuration.titre.toLocaleLowerCase('fr-FR')}`} message="Chargement de la liste en cours." />
        : erreur ? <ContentState type="error" title="Liste indisponible" message={erreur} retry={charger} />
          : resultat.contenu.length === 0 ? <ContentState type="empty" title={configuration.aucun} message="Aucune donnée ne correspond aux critères sélectionnés." />
            : <div className="table-scroll"><table className="data-table"><thead><tr><EnteteTable module={module} /><th><span className="visually-hidden">Action</span></th></tr></thead><tbody>{resultat.contenu.map((entite) => <tr key={entite.id}><CellulesTable entite={entite} module={module} /><td><button className="button button-quiet" type="button" onClick={() => void consulter(entite)}>Consulter</button></td></tr>)}</tbody></table></div>}
      {!chargement && !erreur && resultat.totalPages > 0 && <nav className="pagination" aria-label={`Pagination des ${configuration.titre.toLocaleLowerCase('fr-FR')}`}><button className="button button-secondary" type="button" disabled={page === 0} onClick={() => setPage((courante) => Math.max(0, courante - 1))}>Précédent</button><span>Page {resultat.page + 1} sur {resultat.totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages} onClick={() => setPage((courante) => courante + 1)}>Suivant</button></nav>}
      {resultat.contenu.length > 0 && <span className="visually-hidden">{nombreColonnes} colonnes de données et une colonne d’action</span>}
    </section>

    {chargementDetail && <ContentState type="loading" title="Chargement du détail" message="Chargement des informations en cours." />}
    {erreurDetail && <ContentState type="error" title="Détail indisponible" message={erreurDetail} />}
    {selection && <ReferentielDetail key={`${module}-${selection.id}`} module={module} entite={selection} peutGerer={peutGerer} onClose={() => setSelection(null)} onUpdated={actualiser} />}
  </section>;
}
