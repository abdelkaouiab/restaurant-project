import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import { messageErreur } from '../utilisateurs/erreurs';
import { caisseApi } from './caisseApi';
import type { CommandeAPayer, Facture, ModePaiement, PaiementHistorique, PaiementResponse } from './types';

const MODES: { valeur: ModePaiement; libelle: string }[] = [
  { valeur: 'ESPECES', libelle: 'Espèces' },
  { valeur: 'CARTE', libelle: 'Carte' },
  { valeur: 'CHEQUE', libelle: 'Chèque' },
  { valeur: 'TITRE_RESTAURANT', libelle: 'Titre-restaurant' },
];

const formatMontant = (montant: number) => new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
}).format(montant);

function messageOperation(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) {
    return `Cette opération a déjà été traitée ou la commande a changé : ${error.message}`;
  }
  if (error instanceof ApiError && error.status === 403) {
    return 'Vous n’avez pas l’autorisation d’effectuer cette opération.';
  }
  return messageErreur(error);
}

function nouvelleCleIdempotence(): string {
  return globalThis.crypto.randomUUID();
}

function enregistrerPdf(fichier: Blob, nom: string): void {
  const url = URL.createObjectURL(fichier);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nom;
  lien.click();
  URL.revokeObjectURL(url);
}

function validerMontant(saisie: string, montantRestant: number): string {
  const normalisee = saisie.trim().replace(',', '.');
  if (!normalisee) return 'Le montant est obligatoire.';
  if (!/^\d{1,10}(?:\.\d{1,2})?$/.test(normalisee)) {
    return 'Saisissez un montant positif avec deux décimales au maximum.';
  }
  const montant = Number(normalisee);
  if (!Number.isFinite(montant) || montant <= 0) return 'Le montant doit être strictement positif.';
  if (montant > montantRestant) return 'Le montant ne peut pas dépasser le montant restant à payer.';
  return '';
}

function resumeCommandeDepuisPaiement(
  commande: CommandeAPayer,
  paiement: PaiementResponse,
): CommandeAPayer {
  return {
    ...commande,
    statutCommande: paiement.statutCommande,
    montantPaye: paiement.montantPaye,
    montantRestant: paiement.montantRestant,
  };
}

interface DetailCommandeProps {
  commande: CommandeAPayer;
  onFermer: () => void;
  onPaiement: (commande: CommandeAPayer, facture: Facture | null) => void;
}

function DetailCommande({ commande, onFermer, onPaiement }: DetailCommandeProps) {
  const [montant, setMontant] = useState('');
  const [mode, setMode] = useState<ModePaiement>('ESPECES');
  const [erreurMontant, setErreurMontant] = useState('');
  const [erreurOperation, setErreurOperation] = useState('');
  const [succes, setSucces] = useState('');
  const [confirmation, setConfirmation] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [facture, setFacture] = useState<Facture | null>(null);
  const [chargementFacture, setChargementFacture] = useState(false);
  const [telechargementPdf, setTelechargementPdf] = useState(false);
  const [paiements, setPaiements] = useState<PaiementHistorique[]>([]);
  const [chargementPaiements, setChargementPaiements] = useState(true);
  const [erreurPaiements, setErreurPaiements] = useState('');
  const cleIdempotence = useRef<string | null>(null);

  useEffect(() => {
    setMontant('');
    setErreurMontant('');
    setErreurOperation('');
    setSucces('');
    setConfirmation(false);
    setEnvoi(false);
    setFacture(null);
    setChargementFacture(false);
    setTelechargementPdf(false);
    cleIdempotence.current = null;
  }, [commande.numeroCommande]);

  const chargerPaiements = useCallback(async (signal?: AbortSignal) => {
    setChargementPaiements(true);
    setErreurPaiements('');
    try {
      const page = await caisseApi.listerPaiementsCommande(commande.numeroCommande, signal);
      setPaiements(page.contenu);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      setPaiements([]);
      setErreurPaiements(messageOperation(error));
    } finally {
      if (!signal?.aborted) setChargementPaiements(false);
    }
  }, [commande.numeroCommande]);

  useEffect(() => {
    const controleur = new AbortController();
    void chargerPaiements(controleur.signal);
    return () => controleur.abort();
  }, [chargerPaiements]);

  const modifierMontant = (valeur: string) => {
    setMontant(valeur);
    setErreurMontant('');
    setErreurOperation('');
    cleIdempotence.current = null;
  };

  const modifierMode = (valeur: ModePaiement) => {
    setMode(valeur);
    setErreurOperation('');
    cleIdempotence.current = null;
  };

  const demanderConfirmation = (event: FormEvent) => {
    event.preventDefault();
    const erreur = validerMontant(montant, commande.montantRestant);
    setErreurMontant(erreur);
    if (erreur) return;
    if (!cleIdempotence.current) cleIdempotence.current = nouvelleCleIdempotence();
    setConfirmation(true);
  };

  const confirmerPaiement = async () => {
    if (envoi || !cleIdempotence.current) return;
    setEnvoi(true);
    setErreurOperation('');
    setSucces('');
    try {
      const resultat = await caisseApi.enregistrerPaiement(
        commande.numeroCommande,
        cleIdempotence.current,
        { montant: Number(montant.replace(',', '.')), mode },
      );
      const commandeMiseAJour = resumeCommandeDepuisPaiement(commande, resultat);
      onPaiement(commandeMiseAJour, resultat.facture);
      setFacture(resultat.facture);
      setSucces(resultat.statutCommande === 'PAYEE'
        ? 'Paiement validé. La commande est payée.'
        : 'Paiement validé. Un montant reste à payer.');
      setMontant('');
      cleIdempotence.current = null;
      setConfirmation(false);
      void chargerPaiements();
    } catch (error: unknown) {
      setErreurOperation(messageOperation(error));
      setConfirmation(false);
    } finally {
      setEnvoi(false);
    }
  };

  const telechargerPdf = async () => {
    if (!facture || telechargementPdf) return;
    setTelechargementPdf(true);
    setErreurOperation('');
    try {
      const fichier = await caisseApi.telechargerFacturePdf(commande.numeroCommande);
      enregistrerPdf(fichier, `${facture.numero}.pdf`);
    } catch (error: unknown) {
      setErreurOperation(messageOperation(error));
    } finally {
      setTelechargementPdf(false);
    }
  };

  const consulterFacture = async () => {
    if (chargementFacture) return;
    setChargementFacture(true);
    setErreurOperation('');
    try {
      setFacture(await caisseApi.consulterFacture(commande.numeroCommande));
    } catch (error: unknown) {
      setFacture(null);
      setErreurOperation(messageOperation(error));
    } finally {
      setChargementFacture(false);
    }
  };

  return <article className="admin-card cashier-detail" aria-label={`Détail de la commande ${commande.numeroCommande}`}>
    <header className="card-heading">
      <div><p className="eyebrow">Encaissement</p><h2>{commande.numeroCommande}</h2></div>
      <button className="button button-quiet" type="button" onClick={onFermer}>Fermer</button>
    </header>
    <dl className="cashier-amounts">
      <div><dt>Table</dt><dd>{commande.table}</dd></div>
      <div><dt>Total</dt><dd>{formatMontant(commande.montantTotal)}</dd></div>
      <div><dt>Déjà payé</dt><dd>{formatMontant(commande.montantPaye)}</dd></div>
      <div className="cashier-remaining"><dt>Reste à payer</dt><dd>{formatMontant(commande.montantRestant)}</dd></div>
    </dl>
    <div className="cashier-status-row">
      <span className={`status-pill status-${commande.statutCommande.toLowerCase()}`}>{commande.statutCommande === 'PAYEE' ? 'Payée' : 'Prête à payer'}</span>
      <time dateTime={commande.heure}>{new Date(commande.heure).toLocaleString('fr-FR')}</time>
    </div>
    {erreurOperation && <p className="form-error operation-error" role="alert">{erreurOperation}</p>}
    {succes && <p className="form-success" role="status">{succes}</p>}
    {commande.statutCommande !== 'PAYEE' && <form className="admin-form cashier-payment-form" onSubmit={demanderConfirmation} noValidate>
      <h3>Ajouter un paiement</h3>
      <label htmlFor="cashier-amount">Montant</label>
      <input id="cashier-amount" name="montant" inputMode="decimal" autoComplete="off" value={montant} onChange={(event) => modifierMontant(event.target.value)} aria-invalid={Boolean(erreurMontant)} />
      {erreurMontant && <p className="field-error" role="alert">{erreurMontant}</p>}
      <label htmlFor="cashier-mode">Mode de paiement</label>
      <select id="cashier-mode" name="mode" value={mode} onChange={(event) => modifierMode(event.target.value as ModePaiement)}>
        {MODES.map((option) => <option key={option.valeur} value={option.valeur}>{option.libelle}</option>)}
      </select>
      <p className="field-hint">Aucune donnée bancaire n’est demandée ou conservée par cet écran.</p>
      <button className="button button-primary" type="submit" disabled={envoi}>Vérifier et confirmer</button>
    </form>}
    <section className="cashier-invoice">
      <div><h3>Facture</h3><p>La facture est disponible après le paiement complet de la commande.</p></div>
      <button className="button button-secondary" type="button" disabled={chargementFacture || envoi} onClick={() => void consulterFacture()}>{chargementFacture ? 'Chargement…' : 'Consulter la facture'}</button>
      {facture && <dl className="invoice-card" aria-label={`Facture ${facture.numero}`}>
        <div><dt>Numéro</dt><dd>{facture.numero}</dd></div>
        <div><dt>Commande</dt><dd>{facture.numeroCommande}</dd></div>
        <div><dt>Total facturé</dt><dd>{formatMontant(facture.montantTotal)}</dd></div>
        <div><dt>Émise le</dt><dd>{new Date(facture.emiseLe).toLocaleString('fr-FR')}</dd></div>
      </dl>}
      {facture && <button className="button button-secondary" type="button" disabled={telechargementPdf} onClick={() => void telechargerPdf()}>{telechargementPdf ? 'Téléchargement…' : 'Télécharger le PDF'}</button>}
    </section>
    <section className="cashier-invoice" aria-labelledby="payment-history-title">
      <div><h3 id="payment-history-title">Historique des paiements</h3><p>Retrouvez les paiements enregistrés pour cette commande.</p></div>
      {chargementPaiements ? <ContentState type="loading" title="Chargement des paiements" message="Chargement de l’historique en cours." />
        : erreurPaiements ? <ContentState type="error" title="Historique indisponible" message={erreurPaiements} retry={() => void chargerPaiements()} />
          : paiements.length === 0 ? <ContentState type="empty" title="Aucun paiement" message="Aucun paiement validé n’existe pour cette commande." />
            : <ul className="indicator-list">{paiements.map((paiement) => <li key={paiement.id}><span>{new Date(paiement.creeLe).toLocaleString('fr-FR')} · {MODES.find((item) => item.valeur === paiement.mode)?.libelle ?? paiement.mode}</span><strong>{formatMontant(paiement.montant)}</strong></li>)}</ul>}
    </section>
    {confirmation && <ConfirmationDialog
      title="Confirmer le paiement"
      message={`Enregistrer ${formatMontant(Number(montant.replace(',', '.')))} par ${MODES.find((item) => item.valeur === mode)?.libelle ?? mode} pour ${commande.numeroCommande} ?`}
      confirmLabel="Confirmer le paiement"
      busy={envoi}
      onCancel={() => { if (!envoi) setConfirmation(false); }}
      onConfirm={() => void confirmerPaiement()}
    />}
  </article>;
}

export function CaissePage() {
  const [commandes, setCommandes] = useState<CommandeAPayer[]>([]);
  const [selection, setSelection] = useState<CommandeAPayer | null>(null);
  const [chargement, setChargement] = useState(true);
  const [actualisation, setActualisation] = useState(false);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [factures, setFactures] = useState<Facture[]>([]);
  const [erreurFactures, setErreurFactures] = useState('');
  const [erreurTelechargement, setErreurTelechargement] = useState('');
  const [factureEnTelechargement, setFactureEnTelechargement] = useState('');
  const controleur = useRef<AbortController | null>(null);

  const charger = useCallback(async (initial = false) => {
    controleur.current?.abort();
    const suivant = new AbortController();
    controleur.current = suivant;
    if (initial) setChargement(true); else setActualisation(true);
    setErreur('');
    try {
      const [resultat, pageFactures] = await Promise.all([
        caisseApi.listerCommandesAPayer(suivant.signal),
        caisseApi.listerFactures(suivant.signal),
      ]);
      setCommandes(resultat);
      setFactures(pageFactures.contenu);
      setErreurFactures('');
      setSelection((courante) => courante
        ? resultat.find((commande) => commande.numeroCommande === courante.numeroCommande) ?? courante
        : null);
      setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
      else {
        const message = messageErreur(error);
        setErreur(message);
        setErreurFactures(message);
      }
    } finally {
      if (!suivant.signal.aborted) {
        setChargement(false);
        setActualisation(false);
      }
    }
  }, []);

  useEffect(() => {
    void charger(true);
    return () => controleur.current?.abort();
  }, [charger]);

  const paiementEnregistre = (miseAJour: CommandeAPayer, factureEmise: Facture | null) => {
    setSelection(miseAJour);
    setCommandes((courantes) => miseAJour.statutCommande === 'PAYEE'
      ? courantes.filter((commande) => commande.numeroCommande !== miseAJour.numeroCommande)
      : courantes.map((commande) => commande.numeroCommande === miseAJour.numeroCommande ? miseAJour : commande));
    if (factureEmise) {
      setFactures((courantes) => [
        factureEmise,
        ...courantes.filter((facture) => facture.numero !== factureEmise.numero),
      ].slice(0, 10));
    }
  };

  const telechargerFactureHistorique = async (facture: Facture) => {
    if (factureEnTelechargement) return;
    setFactureEnTelechargement(facture.numero);
    setErreurTelechargement('');
    try {
      const fichier = await caisseApi.telechargerFacturePdf(facture.numeroCommande);
      enregistrerPdf(fichier, `${facture.numero}.pdf`);
    } catch (error: unknown) {
      setErreurTelechargement(messageOperation(error));
    } finally {
      setFactureEnTelechargement('');
    }
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation d’accéder à la caisse." /></section>;

  return <section className="service-page cashier-page">
    <header className="section-heading"><div><p className="eyebrow">Tableau de bord caissier</p><h1>Commandes à payer</h1><p>Consultez les commandes prêtes et enregistrez leurs paiements.</p></div><button className="button button-secondary" type="button" disabled={chargement || actualisation} onClick={() => void charger(false)}>{actualisation ? 'Actualisation…' : 'Actualiser'}</button></header>
    {!chargement && commandes.length > 0 && <div className="cashier-summary" aria-label="Résumé de la caisse"><div><span>Commandes en attente</span><strong>{commandes.length}</strong></div></div>}
    {chargement ? <ContentState type="loading" title="Chargement de la caisse" message="Chargement des commandes prêtes à payer." />
      : erreur && commandes.length === 0 ? <ContentState type="error" title="Caisse indisponible" message={erreur} retry={() => void charger(false)} />
        : commandes.length === 0 && !selection ? <ContentState type="empty" title="Aucune commande à payer" message="Les commandes prêtes apparaîtront ici lorsqu’elles seront disponibles." />
          : <>{erreur && <p className="form-error operation-error" role="alert">Actualisation impossible : {erreur}</p>}<div className="cashier-layout"><section className="admin-card cashier-orders" aria-labelledby="cashier-orders-title"><header className="card-heading"><div><p className="eyebrow">File d’encaissement</p><h2 id="cashier-orders-title">Commandes prêtes</h2></div><span className="result-count">{commandes.length} résultat{commandes.length > 1 ? 's' : ''}</span></header>{commandes.length === 0 ? <ContentState type="empty" title="File vidée" message="La commande sélectionnée vient d’être payée. Aucune autre commande n’attend." /> : <div className="cashier-order-list">{commandes.map((commande) => <button className={`cashier-order-card${selection?.numeroCommande === commande.numeroCommande ? ' cashier-order-selected' : ''}`} type="button" key={commande.numeroCommande} onClick={() => setSelection(commande)}><span><strong>{commande.numeroCommande}</strong><small>Table {commande.table} · {new Date(commande.heure).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</small></span><span><small>Reste à payer</small><strong>{formatMontant(commande.montantRestant)}</strong></span></button>)}</div>}</section>{selection ? <DetailCommande commande={selection} onFermer={() => setSelection(null)} onPaiement={paiementEnregistre} /> : <div className="cashier-selection-empty" role="status"><span aria-hidden="true">M</span><h2>Sélectionnez une commande</h2><p>Son total, les paiements enregistrés et le montant restant seront affichés ici.</p></div>}</div></>}
    {!chargement && <section className="admin-card dashboard-panel" aria-labelledby="invoice-history-title"><header className="card-heading"><div><p className="eyebrow">Historique</p><h2 id="invoice-history-title">Factures récentes</h2></div><span className="result-count">{factures.length} affichée{factures.length > 1 ? 's' : ''}</span></header>{erreurTelechargement && <p className="form-error operation-error" role="alert">{erreurTelechargement}</p>}{erreurFactures ? <ContentState type="error" title="Factures indisponibles" message={erreurFactures} retry={() => void charger(false)} /> : factures.length === 0 ? <ContentState type="empty" title="Aucune facture" message="Aucune facture n’a encore été émise après paiement complet." /> : <ul className="indicator-list">{factures.map((item) => <li key={item.numero}><span>{item.numero} · {item.numeroCommande} · {new Date(item.emiseLe).toLocaleString('fr-FR')}</span><strong>{formatMontant(item.montantTotal)}</strong><button className="button button-quiet" type="button" disabled={Boolean(factureEnTelechargement)} onClick={() => void telechargerFactureHistorique(item)}>{factureEnTelechargement === item.numero ? 'Téléchargement…' : 'PDF'}</button></li>)}</ul>}</section>}
  </section>;
}
