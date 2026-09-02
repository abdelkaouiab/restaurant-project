import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ContentState } from '../../components/ContentState';
import { Link } from '../../routes/Router';
import { formatQuantite } from '../../utils/quantites';
import { messageErreur } from '../utilisateurs/erreurs';
import { dashboardApi } from './dashboardApi';
import { libelleActionJournal } from './journalOptions';
import { PeriodeForm, validerPeriode } from './PeriodeForm';
import type { DonneesDashboard, IndicateurValeur, Periode } from './types';

type RoleDashboard = 'ADMINISTRATEUR' | 'GERANT' | 'SERVEUR' | 'CAISSIER' | 'CUISINE' | 'RESPONSABLE_STOCK';
const ORDRE_ROLES: RoleDashboard[] = ['ADMINISTRATEUR', 'GERANT', 'SERVEUR', 'CAISSIER', 'CUISINE', 'RESPONSABLE_STOCK'];
const LIBELLES_ROLES: Record<RoleDashboard, string> = { ADMINISTRATEUR: 'Administrateur', GERANT: 'Gérant', SERVEUR: 'Serveur', CAISSIER: 'Caissier', CUISINE: 'Cuisine', RESPONSABLE_STOCK: 'Responsable stock' };

function dateIso(decalage: number): string {
  const date = new Date(); date.setUTCDate(date.getUTCDate() + decalage);
  return date.toISOString().slice(0, 10);
}
const valeur = (liste: IndicateurValeur[] | undefined, libelle: string) => liste?.find((item) => item.libelle === libelle)?.valeur ?? 0;
const nombre = (valeurNombre: number, decimales = 0) => Number(valeurNombre).toLocaleString('fr-FR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales });
const LIBELLES_INDICATEURS: Record<string, string> = {
  BROUILLON: 'Brouillon', CONFIRMEE: 'Confirmée', EN_PREPARATION: 'En préparation', PRETE: 'Prête',
  PAYEE: 'Payée', ANNULEE: 'Annulée', EN_ATTENTE: 'En attente', ARRIVEE: 'Arrivée', TERMINEE: 'Terminée',
  ABSENTE: 'Absente', ESPECES: 'Espèces', CARTE: 'Carte', CHEQUE: 'Chèque', TITRE_RESTAURANT: 'Titre-restaurant',
};
function libelleIndicateur(valeurBrute: string): string {
  const libelle = LIBELLES_INDICATEURS[valeurBrute];
  if (libelle) return libelle;
  const texte = valeurBrute.replaceAll('_', ' ').toLocaleLowerCase('fr-FR');
  return texte.charAt(0).toLocaleUpperCase('fr-FR') + texte.slice(1);
}

function Metric({ titre, valeurAffichee }: { titre: string; valeurAffichee: string | number }) {
  return <article className="dashboard-metric"><span>{titre}</span><strong>{valeurAffichee}</strong></article>;
}

function ListeIndicateurs({ titre, indicateurs }: { titre: string; indicateurs: IndicateurValeur[] }) {
  return <section className="dashboard-panel"><h2>{titre}</h2>{indicateurs.length === 0 ? <ContentState type="empty" title="Aucune donnée" message="Aucune donnée n’est disponible pour cette période." /> : <ul className="indicator-list">{indicateurs.map((item) => <li key={item.libelle}><span>{libelleIndicateur(item.libelle)}</span><strong>{nombre(item.valeur, Number(item.valeur) % 1 === 0 ? 0 : 2)}</strong></li>)}</ul>}</section>;
}

export function RoleDashboardPage() {
  const { utilisateur } = useAuth();
  const role = ORDRE_ROLES.find((item) => utilisateur?.roles.includes(item));
  const periodeVisible = role === 'ADMINISTRATEUR' || role === 'GERANT' || role === 'SERVEUR';
  const [debut, setDebut] = useState(() => role === 'SERVEUR' ? dateIso(0) : dateIso(-29));
  const [fin, setFin] = useState(() => dateIso(0));
  const [periode, setPeriode] = useState<Periode>({ debut, fin });
  const [donnees, setDonnees] = useState<DonneesDashboard>({});
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [erreurPeriode, setErreurPeriode] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const controleur = useRef<AbortController | null>(null);
  const permissions = useMemo(() => new Set(utilisateur?.permissions ?? []), [utilisateur?.permissions]);

  const charger = useCallback(async () => {
    if (!role) { setChargement(false); return; }
    controleur.current?.abort(); const suivant = new AbortController(); controleur.current = suivant;
    setChargement(true); setErreur('');
    try {
      const suivantes: DonneesDashboard = {};
      if (role === 'ADMINISTRATEUR') {
        const appels: Promise<void>[] = [];
        if (permissions.has('UTILISATEUR_CONSULTER')) {
          appels.push(Promise.all([dashboardApi.compterUtilisateurs(true, suivant.signal), dashboardApi.compterUtilisateurs(false, suivant.signal)]).then(([actifs, inactifs]) => { suivantes.utilisateursActifs = actifs; suivantes.utilisateursInactifs = inactifs; }));
        }
        if (permissions.has('JOURNAL_ACTIVITE_CONSULTER')) {
          appels.push(dashboardApi.journal({ ...periode, typeRessource: 'AUTHENTIFICATION', page: 0, taille: 5, tri: 'creeLe', direction: 'desc' }, suivant.signal).then((page) => { suivantes.evenementsSecurite = page.contenu; }));
        }
        await Promise.all(appels);
      } else if (role === 'GERANT') {
        const [operations, finances] = await Promise.all([
          dashboardApi.operations(periode, suivant.signal), dashboardApi.finances(periode, suivant.signal),
        ]);
        suivantes.operations = operations; suivantes.finances = finances;
      } else if (role === 'SERVEUR') {
        suivantes.operations = await dashboardApi.operations(periode, suivant.signal);
      } else if (role === 'CAISSIER' && permissions.has('CAISSE_PAIEMENTS_GERER')) {
        const [commandes, paiements, factures] = await Promise.all([
          dashboardApi.commandesAPayer(suivant.signal),
          dashboardApi.paiementsDuJour(dateIso(0), suivant.signal),
          dashboardApi.facturesRecentes(suivant.signal),
        ]);
        suivantes.commandesAPayer = commandes;
        suivantes.paiementsDuJour = paiements;
        suivantes.facturesRecentes = factures;
      } else if (role === 'CUISINE' && permissions.has('CUISINE_COMMANDES_GERER')) {
        suivantes.commandesCuisine = await dashboardApi.commandesCuisine(suivant.signal);
      } else if (role === 'RESPONSABLE_STOCK') {
        const appels: Promise<void>[] = [];
        if (permissions.has('TABLEAU_BORD_CONSULTER')) appels.push(dashboardApi.operations({}, suivant.signal).then((operations) => { suivantes.operations = operations; }));
        if (permissions.has('ACHAT_CONSULTER')) appels.push(Promise.all([dashboardApi.compterAchats('COMMANDE', suivant.signal), dashboardApi.compterAchats('PARTIELLEMENT_RECU', suivant.signal)]).then(([commandes, partiels]) => { suivantes.achatsCommandes = commandes; suivantes.achatsPartiellementRecus = partiels; }));
        await Promise.all(appels);
      }
      setDonnees(suivantes); setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true); else setErreur(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargement(false); }
  }, [periode, permissions, role]);

  useEffect(() => { void charger(); return () => controleur.current?.abort(); }, [charger]);
  const appliquerPeriode = () => {
    const validation = validerPeriode(debut, fin); setErreurPeriode(validation);
    if (!validation) setPeriode({ debut, fin });
  };

  if (!role) return <section className="section-page"><ContentState type="empty" title="Aucun tableau de bord associé" message="Aucun rôle pris en charge n’est associé à cette session." /></section>;
  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter ce tableau de bord." /></section>;

  return <section className="dashboard-page">
    <header className="section-heading"><div><p className="eyebrow">Tableau de bord</p><h1>Bonjour {utilisateur?.employe.prenom}</h1><p>Retrouvez les informations essentielles à votre activité.</p></div><span className="section-badge">{LIBELLES_ROLES[role].toUpperCase()}</span></header>
    {periodeVisible && <><PeriodeForm debut={debut} fin={fin} busy={chargement} onDebut={setDebut} onFin={setFin} onSubmit={appliquerPeriode} />{erreurPeriode && <p className="form-error operation-error" role="alert">{erreurPeriode}</p>}</>}
    {chargement ? <ContentState type="loading" title="Chargement du tableau de bord" message="Les indicateurs sont en cours de préparation." /> : erreur ? <ContentState type="error" title="Tableau de bord indisponible" message={erreur} retry={() => void charger()} /> : <>
      {role === 'ADMINISTRATEUR' && <AdminDashboard donnees={donnees} journalAutorise={permissions.has('JOURNAL_ACTIVITE_CONSULTER')} />}
      {role === 'GERANT' && donnees.operations && donnees.finances && <ManagerDashboard operations={donnees.operations} finances={donnees.finances} />}
      {role === 'SERVEUR' && donnees.operations && <ServerDashboard operations={donnees.operations} />}
      {role === 'CAISSIER' && <CashierDashboard donnees={donnees} />}
      {role === 'CUISINE' && <KitchenDashboard commandes={donnees.commandesCuisine} />}
      {role === 'RESPONSABLE_STOCK' && <StockRoleDashboard donnees={donnees} />}
    </>}
  </section>;
}

function AdminDashboard({ donnees, journalAutorise }: { donnees: DonneesDashboard; journalAutorise: boolean }) {
  return <><div className="dashboard-metrics"><Metric titre="Utilisateurs actifs" valeurAffichee={donnees.utilisateursActifs ?? 'Indisponible'} /><Metric titre="Comptes désactivés" valeurAffichee={donnees.utilisateursInactifs ?? 'Indisponible'} /><Metric titre="Événements récents" valeurAffichee={donnees.evenementsSecurite?.length ?? 'Indisponible'} /></div><section className="dashboard-panel"><header className="card-heading"><div><p className="eyebrow">Activité</p><h2>Événements récents</h2></div>{journalAutorise && <Link className="button button-secondary" to="/journal-activite">Ouvrir le journal</Link>}</header>{donnees.evenementsSecurite === undefined ? <ContentState type="empty" title="Journal inaccessible" message="Vous n’avez pas accès au journal d’activité." /> : donnees.evenementsSecurite.length === 0 ? <ContentState type="empty" title="Aucun événement récent" message="Aucun événement n’existe sur la période." /> : <ul className="audit-preview">{donnees.evenementsSecurite.map((item) => <li key={item.id}><span className={`status-pill ${item.resultat === 'SUCCES' ? 'status-active' : 'status-inactive'}`}>{item.resultat === 'SUCCES' ? 'Succès' : 'Échec'}</span><strong>{libelleActionJournal(item.action)}</strong><time dateTime={item.creeLe}>{new Date(item.creeLe).toLocaleString('fr-FR')}</time></li>)}</ul>}</section></>;
}

export function ManagerDashboard({ operations, finances }: { operations: NonNullable<DonneesDashboard['operations']>; finances: NonNullable<DonneesDashboard['finances']> }) {
  return <><div className="dashboard-metrics"><Metric titre="Chiffre d’affaires" valeurAffichee={nombre(finances.chiffreAffaires, 2)} /><Metric titre="Commandes" valeurAffichee={nombre(operations.nombreCommandes)} /><Metric titre="Dépenses d’achat" valeurAffichee={nombre(finances.depensesAchat, 2)} /><Metric titre="Stocks faibles" valeurAffichee={operations.stocksFaibles.length} /></div><div className="dashboard-panels"><ListeIndicateurs titre="Commandes par état" indicateurs={operations.commandesParEtat} /><ListeIndicateurs titre="Réservations" indicateurs={operations.reservations} /><ListeIndicateurs titre="Paiements par mode" indicateurs={finances.paiementsParMode} /><section className="dashboard-panel"><h2>Produits les plus vendus</h2>{operations.produitsLesPlusVendus.length === 0 ? <ContentState type="empty" title="Aucune vente" message="Aucun produit vendu sur la période." /> : <ol className="ranked-list">{operations.produitsLesPlusVendus.map((item) => <li key={item.produitId}><span>{item.produit}</span><strong>{item.quantite}</strong></li>)}</ol>}</section><section className="dashboard-panel"><h2>Stocks faibles</h2>{operations.stocksFaibles.length === 0 ? <ContentState type="empty" title="Aucun stock faible" message="Aucune alerte de stock pour le moment." /> : <ul className="indicator-list">{operations.stocksFaibles.map((item) => <li key={item.ingredientId}><span>{item.ingredient}</span><strong>{formatQuantite(item.quantiteDisponible, item.unite)} / {formatQuantite(item.seuilAlerte, item.unite)}</strong></li>)}</ul>}</section></div></>;
}

function ServerDashboard({ operations }: { operations: NonNullable<DonneesDashboard['operations']> }) {
  return <><div className="dashboard-metrics"><Metric titre="Tables disponibles" valeurAffichee={nombre(operations.nombreTablesDisponibles)} /><Metric titre="Commandes en préparation" valeurAffichee={nombre(valeur(operations.commandesParEtat, 'EN_PREPARATION'))} /><Metric titre="Commandes prêtes" valeurAffichee={nombre(valeur(operations.commandesParEtat, 'PRETE'))} /><Metric titre="Commandes du jour" valeurAffichee={nombre(operations.nombreCommandes)} /></div><div className="dashboard-panels"><ListeIndicateurs titre="Réservations du jour" indicateurs={operations.reservations} /><section className="dashboard-panel"><h2>Accès rapides</h2><div className="dashboard-links"><Link to="/tables">Tables</Link><Link to="/reservations">Réservations</Link><Link to="/commandes">Commandes</Link></div></section></div></>;
}

function CashierDashboard({ donnees }: { donnees: DonneesDashboard }) {
  const commandes = donnees.commandesAPayer;
  const paiements = donnees.paiementsDuJour;
  const factures = donnees.facturesRecentes;
  return <><div className="dashboard-metrics"><Metric titre="Commandes à payer" valeurAffichee={commandes?.length ?? 'Indisponible'} /><Metric titre="Paiements du jour" valeurAffichee={paiements?.totalElements ?? 'Indisponible'} /><Metric titre="Factures récentes" valeurAffichee={factures?.totalElements ?? 'Indisponible'} /></div><div className="dashboard-panels"><section className="dashboard-panel"><h2>Encaissements</h2>{commandes === undefined ? <ContentState type="empty" title="Caisse inaccessible" message="Vous n’avez pas accès aux encaissements." /> : commandes.length === 0 ? <ContentState type="empty" title="Aucune commande à payer" message="Aucune commande prête n’attend un paiement." /> : <ul className="indicator-list">{commandes.slice(0, 5).map((item) => <li key={item.numeroCommande}><span>{item.numeroCommande} · Table {item.table}</span><strong>{nombre(item.montantRestant, 2)}</strong></li>)}</ul>}<Link className="button button-secondary dashboard-action" to="/caisse">Ouvrir la caisse</Link></section><section className="dashboard-panel"><h2>Paiements du jour</h2>{paiements?.contenu.length ? <ul className="indicator-list">{paiements.contenu.map((item) => <li key={item.id}><span>{item.numeroCommande} · {libelleIndicateur(item.mode)}</span><strong>{nombre(item.montant, 2)}</strong></li>)}</ul> : <ContentState type="empty" title="Aucun paiement aujourd’hui" message="Aucun paiement validé n’a été enregistré aujourd’hui." />}</section><section className="dashboard-panel"><h2>Factures récentes</h2>{factures?.contenu.length ? <ul className="indicator-list">{factures.contenu.map((item) => <li key={item.numero}><span>{item.numero} · {item.numeroCommande}</span><strong>{nombre(item.montantTotal, 2)}</strong></li>)}</ul> : <ContentState type="empty" title="Aucune facture récente" message="Aucune facture n’a encore été émise." />}</section></div></>;
}

function KitchenDashboard({ commandes }: { commandes?: NonNullable<DonneesDashboard['commandesCuisine']> }) {
  const enAttente = commandes?.filter((item) => item.etat === 'CONFIRMEE').length;
  const enPreparation = commandes?.filter((item) => item.etat === 'EN_PREPARATION').length;
  const pretes = commandes?.filter((item) => item.etat === 'PRETE').length;
  return <><div className="dashboard-metrics"><Metric titre="Commandes en attente" valeurAffichee={enAttente ?? 'Indisponible'} /><Metric titre="Commandes en préparation" valeurAffichee={enPreparation ?? 'Indisponible'} /><Metric titre="Commandes prêtes récemment" valeurAffichee={pretes ?? 'Indisponible'} /></div><section className="dashboard-panel"><h2>Flux cuisine</h2>{commandes && commandes.length === 0 ? <ContentState type="empty" title="Aucune commande active ou prête récemment" message="Le flux cuisine est vide." /> : <Link className="button button-secondary dashboard-action" to="/cuisine">Ouvrir le flux cuisine</Link>}</section></>;
}

function StockRoleDashboard({ donnees }: { donnees: DonneesDashboard }) {
  return <><div className="dashboard-metrics"><Metric titre="Stocks faibles" valeurAffichee={donnees.operations?.stocksFaibles.length ?? 'Indisponible'} /><Metric titre="Achats en attente" valeurAffichee={donnees.achatsCommandes ?? 'Indisponible'} /><Metric titre="Réceptions à terminer" valeurAffichee={donnees.achatsPartiellementRecus ?? 'Indisponible'} /></div><div className="dashboard-panels"><section className="dashboard-panel"><h2>Alertes de stock</h2>{donnees.operations?.stocksFaibles.length ? <ul className="indicator-list">{donnees.operations.stocksFaibles.map((item) => <li key={item.ingredientId}><span>{item.ingredient}</span><strong>{formatQuantite(item.quantiteDisponible, item.unite)}</strong></li>)}</ul> : <ContentState type="empty" title="Aucune alerte" message="Aucun stock faible pour le moment." />}</section><section className="dashboard-panel"><h2>Approvisionnement</h2><div className="dashboard-links"><Link to="/stock">Stock</Link><Link to="/achats">Achats</Link><Link to="/fournisseurs">Fournisseurs</Link></div></section></div></>;
}
