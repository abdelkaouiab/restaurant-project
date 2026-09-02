import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import type { PageResponse } from '../../types/api';
import type { Client, TableRestaurantSimple } from '../referentiels/types';
import { messageErreur } from '../utilisateurs/erreurs';
import { serviceApi } from './serviceApi';
import type { Reservation, StatutReservation } from './types';

const PAGE_VIDE: PageResponse<Reservation> = { contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 };

const LIBELLES_STATUTS: Record<StatutReservation, string> = {
  EN_ATTENTE: 'En attente', CONFIRMEE: 'Confirmée', ARRIVEE: 'Arrivée',
  TERMINEE: 'Terminée', ANNULEE: 'Annulée', ABSENTE: 'Absente',
};

type ActionReservation = 'confirmation' | 'arrivee' | 'terminaison' | 'annulation' | 'absence' | 'commande';

const LIBELLES_ACTIONS: Record<ActionReservation, string> = {
  confirmation: 'Confirmer', arrivee: 'Enregistrer l’arrivée', terminaison: 'Terminer',
  annulation: 'Annuler', absence: 'Marquer absente', commande: 'Créer la commande',
};

function actionsDisponibles(reservation: Reservation, peutCreerCommande: boolean): ActionReservation[] {
  if (reservation.statut === 'EN_ATTENTE') return ['confirmation', 'annulation'];
  if (reservation.statut === 'CONFIRMEE') return ['arrivee', 'annulation', 'absence'];
  if (reservation.statut === 'ARRIVEE') return [...(!reservation.numeroCommande && peutCreerCommande ? ['commande' as const] : []), 'terminaison'];
  return [];
}

function messageOperation(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) return `Cette réservation vient d’être modifiée : ${error.message}`;
  if (error instanceof ApiError && error.status === 403) return 'Vous n’avez pas l’autorisation d’effectuer cette action.';
  return messageErreur(error);
}

export function ReservationsPage() {
  const { utilisateur } = useAuth();
  const peutCreerCommande = utilisateur?.permissions.includes('COMMANDE_GERER') ?? false;
  const [resultat, setResultat] = useState(PAGE_VIDE);
  const [page, setPage] = useState(0);
  const [taille, setTaille] = useState(10);
  const [dateFiltre, setDateFiltre] = useState('');
  const [statutFiltre, setStatutFiltre] = useState<'' | StatutReservation>('');
  const [recharger, setRecharger] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [tables, setTables] = useState<TableRestaurantSimple[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [chargementOptions, setChargementOptions] = useState(true);
  const [erreurOptions, setErreurOptions] = useState('');
  const [creationVisible, setCreationVisible] = useState(false);
  const [tableId, setTableId] = useState('');
  const [clientId, setClientId] = useState('');
  const [nombrePersonnes, setNombrePersonnes] = useState('');
  const [debut, setDebut] = useState('');
  const [remarques, setRemarques] = useState('');
  const [erreursFormulaire, setErreursFormulaire] = useState<Record<string, string>>({});
  const [erreurOperation, setErreurOperation] = useState('');
  const [action, setAction] = useState<{ reservation: Reservation; action: ActionReservation } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const actualiser = useCallback(() => setRecharger((courant) => courant + 1), []);

  useEffect(() => {
    const controleur = new AbortController();
    Promise.all([
      serviceApi.listerTablesActives(controleur.signal),
      serviceApi.listerClientsActifs(controleur.signal),
    ]).then(([tablesActives, clientsActifs]) => { setTables(tablesActives); setClients(clientsActifs); })
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) setErreurOptions(messageErreur(error));
      }).finally(() => { if (!controleur.signal.aborted) setChargementOptions(false); });
    return () => controleur.abort();
  }, []);

  useEffect(() => {
    const controleur = new AbortController();
    setChargement(true); setErreur(''); setAccesRefuse(false);
    serviceApi.listerReservations({
      date: dateFiltre || undefined,
      statut: statutFiltre || undefined,
      page,
      taille,
    }, controleur.signal)
      .then(setResultat)
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
        else setErreur(messageErreur(error));
      })
      .finally(() => { if (!controleur.signal.aborted) setChargement(false); });
    return () => controleur.abort();
  }, [dateFiltre, page, recharger, statutFiltre, taille]);

  const creer = async (event: FormEvent) => {
    event.preventDefault();
    const suivantes: Record<string, string> = {};
    if (!tableId) suivantes.tableId = 'Sélectionnez une table active.';
    if (!clientId) suivantes.clientId = 'Sélectionnez un client actif.';
    const personnes = Number(nombrePersonnes);
    if (!Number.isInteger(personnes) || personnes < 1 || personnes > 100) suivantes.nombrePersonnes = 'Le nombre de personnes doit être compris entre 1 et 100.';
    const dateDebut = new Date(debut);
    if (!debut || Number.isNaN(dateDebut.getTime()) || dateDebut.getTime() <= Date.now()) suivantes.debut = 'Choisissez une date et une heure futures.';
    if (remarques.length > 500) suivantes.remarques = 'Les remarques ne doivent pas dépasser 500 caractères.';
    setErreursFormulaire(suivantes);
    if (Object.keys(suivantes).length > 0) return;
    setEnvoi(true); setErreurOperation('');
    try {
      await serviceApi.creerReservation({ tableId: Number(tableId), clientId: Number(clientId), nombrePersonnes: personnes, debut: dateDebut.toISOString(), remarques: remarques.trim() || null });
      setCreationVisible(false); setTableId(''); setClientId(''); setNombrePersonnes(''); setDebut(''); setRemarques(''); actualiser();
    } catch (error: unknown) { setErreurOperation(messageOperation(error)); }
    finally { setEnvoi(false); }
  };

  const executerAction = async () => {
    if (!action) return;
    setEnvoi(true); setErreurOperation('');
    try {
      const modifie = action.action === 'commande'
        ? await serviceApi.creerCommandeReservation(action.reservation.id)
        : await serviceApi.transitionReservation(action.reservation.id, action.action);
      setResultat((courant) => ({ ...courant, contenu: courant.contenu.map((item) => item.id === modifie.id ? modifie : item) }));
      setAction(null);
      actualiser();
    } catch (error: unknown) { setErreurOperation(messageOperation(error)); setAction(null); }
    finally { setEnvoi(false); }
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les réservations." /></section>;

  return <section className="section-page users-page service-page">
    <header className="section-heading"><div><p className="eyebrow">Service en salle</p><h1>Réservations</h1><p>Créez les réservations et suivez leur progression.</p></div><button className="button button-primary heading-action" type="button" disabled={chargementOptions || Boolean(erreurOptions)} onClick={() => setCreationVisible((visible) => !visible)}>{creationVisible ? 'Fermer la création' : 'Créer une réservation'}</button></header>
    {chargementOptions && <p className="field-hint" role="status">Chargement des tables et clients actifs…</p>}
    {!creationVisible && erreurOptions && <p className="form-error operation-error" role="alert">Données de sélection indisponibles : {erreurOptions}</p>}

    {creationVisible && <section className="admin-card" aria-labelledby="creation-reservation-title"><div className="card-heading"><div><p className="eyebrow">Nouveau créneau</p><h2 id="creation-reservation-title">Créer une réservation</h2></div></div>
      <form className="admin-form service-form" onSubmit={(event) => void creer(event)} noValidate>
        <label htmlFor="reservation-table">Table</label><select id="reservation-table" value={tableId} onChange={(event) => setTableId(event.target.value)}><option value="">Sélectionner une table</option>{tables.map((table) => <option key={table.id} value={table.id}>Table {table.numero} — {table.capacite} places</option>)}</select>{erreursFormulaire.tableId && <p className="field-error">{erreursFormulaire.tableId}</p>}
        <label htmlFor="reservation-client">Client</label><select id="reservation-client" value={clientId} onChange={(event) => setClientId(event.target.value)}><option value="">Sélectionner un client</option>{clients.map((client) => <option key={client.id} value={client.id}>{client.prenom} {client.nom}</option>)}</select>{erreursFormulaire.clientId && <p className="field-error">{erreursFormulaire.clientId}</p>}
        <label htmlFor="reservation-personnes">Nombre de personnes</label><input id="reservation-personnes" type="number" min="1" max="100" value={nombrePersonnes} onChange={(event) => setNombrePersonnes(event.target.value)} />{erreursFormulaire.nombrePersonnes && <p className="field-error">{erreursFormulaire.nombrePersonnes}</p>}
        <label htmlFor="reservation-debut">Date et heure de début</label><input id="reservation-debut" type="datetime-local" value={debut} onChange={(event) => setDebut(event.target.value)} />{erreursFormulaire.debut && <p className="field-error">{erreursFormulaire.debut}</p>}<p className="field-hint">Chaque réservation dure deux heures.</p>
        <label htmlFor="reservation-remarques">Remarques (facultatives)</label><textarea id="reservation-remarques" maxLength={500} value={remarques} onChange={(event) => setRemarques(event.target.value)} />{erreursFormulaire.remarques && <p className="field-error">{erreursFormulaire.remarques}</p>}
        {erreurOptions && <p className="form-error" role="alert">Données de sélection indisponibles : {erreurOptions}</p>}
        {erreurOperation && <p className="form-error" role="alert">{erreurOperation}</p>}
        <div className="form-actions"><button className="button button-secondary" type="button" onClick={() => setCreationVisible(false)}>Annuler</button><button className="button button-primary" type="submit" disabled={envoi || chargementOptions || Boolean(erreurOptions)}>{envoi ? 'Création…' : 'Créer la réservation'}</button></div>
      </form>
    </section>}

    {!creationVisible && erreurOperation && <p className="form-error operation-error" role="alert">{erreurOperation}</p>}
    <section className="admin-card" aria-labelledby="liste-reservations-title"><div className="card-heading"><div><p className="eyebrow">Planning</p><h2 id="liste-reservations-title">Liste des réservations</h2></div><label className="inline-size">Par page<select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label></div>
      <div className="list-toolbar reservation-filters" aria-label="Filtres des réservations">
        <label htmlFor="reservation-filter-date">Date<input id="reservation-filter-date" type="date" value={dateFiltre} onChange={(event) => { setDateFiltre(event.target.value); setPage(0); }} /></label>
        <label htmlFor="reservation-filter-status">Statut<select id="reservation-filter-status" value={statutFiltre} onChange={(event) => { setStatutFiltre(event.target.value as '' | StatutReservation); setPage(0); }}><option value="">Tous les statuts</option>{Object.entries(LIBELLES_STATUTS).map(([valeur, libelle]) => <option value={valeur} key={valeur}>{libelle}</option>)}</select></label>
        <button className="button button-secondary" type="button" disabled={!dateFiltre && !statutFiltre} onClick={() => { setDateFiltre(''); setStatutFiltre(''); setPage(0); }}>Effacer les filtres</button>
      </div>
      {chargement ? <ContentState type="loading" title="Chargement des réservations" message="Chargement du planning en cours." /> : erreur ? <ContentState type="error" title="Réservations indisponibles" message={erreur} retry={actualiser} /> : resultat.contenu.length === 0 ? <ContentState type="empty" title="Aucune réservation" message={dateFiltre || statutFiltre ? 'Aucune réservation ne correspond aux filtres sélectionnés.' : 'Aucune réservation n’est enregistrée.'} /> : <div className="reservation-list">{resultat.contenu.map((reservation) => {
        const client = clients.find((item) => item.id === reservation.clientId);
        return <article className="reservation-card" key={reservation.id}><div className="reservation-main"><div><p className="eyebrow">{reservation.numero}</p><h3>Table {reservation.numeroTable}</h3><p>{new Date(reservation.debut).toLocaleString('fr-FR')} – {new Date(reservation.fin).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</p></div><span className={`status-pill status-${reservation.statut.toLowerCase()}`}>{LIBELLES_STATUTS[reservation.statut]}</span></div><dl className="reservation-meta"><div><dt>Client</dt><dd>{client ? `${client.prenom} ${client.nom}` : 'Client non disponible'}</dd></div><div><dt>Personnes</dt><dd>{reservation.nombrePersonnes}</dd></div>{reservation.numeroCommande && <div><dt>Commande</dt><dd>{reservation.numeroCommande}</dd></div>}</dl>{reservation.remarques && <p className="reservation-note">{reservation.remarques}</p>}<div className="row-actions">{actionsDisponibles(reservation, peutCreerCommande).map((item) => <button className={`button ${item === 'annulation' || item === 'absence' ? 'button-danger' : 'button-secondary'}`} type="button" key={item} onClick={() => setAction({ reservation, action: item })}>{LIBELLES_ACTIONS[item]}</button>)}</div></article>;
      })}</div>}
      {!chargement && !erreur && resultat.totalPages > 0 && <nav className="pagination" aria-label="Pagination des réservations"><button className="button button-secondary" type="button" disabled={page === 0} onClick={() => setPage((courante) => Math.max(0, courante - 1))}>Précédent</button><span>Page {resultat.page + 1} sur {resultat.totalPages}</span><button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages} onClick={() => setPage((courante) => courante + 1)}>Suivant</button></nav>}
    </section>
    {action && <ConfirmationDialog title={`${LIBELLES_ACTIONS[action.action]} ?`} message="Cette action sera appliquée si l’état actuel de la réservation le permet." confirmLabel={LIBELLES_ACTIONS[action.action]} danger={action.action === 'annulation' || action.action === 'absence'} busy={envoi} onCancel={() => setAction(null)} onConfirm={() => void executerAction()} />}
  </section>;
}
