import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { ContentState } from '../../components/ContentState';
import { messageErreur } from '../utilisateurs/erreurs';
import { cuisineApi } from './cuisineApi';
import type { CommandeCuisine, EtatCommandeCuisine } from './types';

const ACTUALISATION_MS = 30_000;

const COLONNES: { etat: EtatCommandeCuisine; titre: string; description: string }[] = [
  { etat: 'CONFIRMEE', titre: 'À démarrer', description: 'Commandes confirmées en attente de prise en charge.' },
  { etat: 'EN_PREPARATION', titre: 'En préparation', description: 'Commandes actuellement préparées en cuisine.' },
  { etat: 'PRETE', titre: 'Prêtes récemment', description: 'Commandes terminées au cours des deux dernières heures.' },
];

const LIBELLES_ETATS: Record<EtatCommandeCuisine, string> = {
  CONFIRMEE: 'Confirmée',
  EN_PREPARATION: 'En préparation',
  PRETE: 'Prête',
};

function tempsEcoule(heure: string, maintenant: number): string {
  const origine = new Date(heure).getTime();
  if (Number.isNaN(origine)) return 'Heure inconnue';
  const minutes = Math.max(0, Math.floor((maintenant - origine) / 60_000));
  if (minutes < 1) return 'À l’instant';
  if (minutes < 60) return `Depuis ${minutes} min`;
  const heures = Math.floor(minutes / 60);
  const reste = minutes % 60;
  return `Depuis ${heures} h${reste ? ` ${reste} min` : ''}`;
}

function messageTransition(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) return `Cette commande vient d’être modifiée : ${error.message}`;
  if (error instanceof ApiError && error.status === 403) return 'Vous n’avez pas l’autorisation d’effectuer cette action.';
  return messageErreur(error);
}

interface CarteCommandeProps {
  commande: CommandeCuisine;
  nouvelle: boolean;
  maintenant: number;
  traitement: string | null;
  onTransition: (commande: CommandeCuisine) => void;
}

function CarteCommande({ commande, nouvelle, maintenant, traitement, onTransition }: CarteCommandeProps) {
  const action = commande.etat === 'CONFIRMEE' ? 'Démarrer la préparation' : 'Marquer prête';
  const transitionAutorisee = commande.etat !== 'PRETE';
  return <article className={`kitchen-ticket${nouvelle ? ' kitchen-ticket-new' : ''}`} aria-label={`Commande ${commande.numero}`}>
    <header className="kitchen-ticket-header"><div><p className="eyebrow">{commande.numero}</p><h3>Table {commande.table}</h3></div>{nouvelle && <span className="new-order-badge">Nouvelle</span>}</header>
    <div className="kitchen-time"><time dateTime={commande.heure}>{new Date(commande.heure).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</time><strong>{tempsEcoule(commande.heure, maintenant)}</strong></div>
    <ul className="kitchen-products">{commande.produits.map((produit, index) => <li key={`${produit.produit}-${index}`}><span className="kitchen-quantity">{produit.quantite}×</span><div><strong>{produit.produit}</strong>{produit.remarque && <p>{produit.remarque}</p>}</div></li>)}</ul>
    <footer><span className={`status-pill status-${commande.etat.toLowerCase()}`}>{LIBELLES_ETATS[commande.etat]}</span>{transitionAutorisee && <button className="button kitchen-action" type="button" disabled={traitement !== null} onClick={() => onTransition(commande)}>{traitement === commande.numero ? 'Traitement…' : action}</button>}</footer>
  </article>;
}

export function CuisinePage() {
  const [commandes, setCommandes] = useState<CommandeCuisine[]>([]);
  const [chargementInitial, setChargementInitial] = useState(true);
  const [actualisation, setActualisation] = useState(false);
  const [erreur, setErreur] = useState('');
  const [erreurTransition, setErreurTransition] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [traitement, setTraitement] = useState<string | null>(null);
  const [nouvelles, setNouvelles] = useState<Set<string>>(new Set());
  const [derniereActualisation, setDerniereActualisation] = useState<Date | null>(null);
  const [maintenant, setMaintenant] = useState(Date.now());
  const numerosConnus = useRef<Set<string> | null>(null);
  const controleur = useRef<AbortController | null>(null);

  const charger = useCallback(async (initial = false) => {
    controleur.current?.abort();
    const suivant = new AbortController();
    controleur.current = suivant;
    if (initial) setChargementInitial(true); else setActualisation(true);
    setErreur('');
    try {
      const resultat = await cuisineApi.lister(suivant.signal);
      const numeros = new Set(resultat.map((commande) => commande.numero));
      if (numerosConnus.current) {
        const arrives = resultat.filter((commande) => commande.etat === 'CONFIRMEE' && !numerosConnus.current?.has(commande.numero)).map((commande) => commande.numero);
        if (arrives.length > 0) setNouvelles((courantes) => new Set([...courantes, ...arrives]));
      }
      numerosConnus.current = numeros;
      setNouvelles((courantes) => new Set([...courantes].filter((numero) => numeros.has(numero))));
      setCommandes(resultat);
      setDerniereActualisation(new Date());
      setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
      else setErreur(messageErreur(error));
    } finally {
      if (!suivant.signal.aborted) {
        setChargementInitial(false);
        setActualisation(false);
      }
    }
  }, []);

  useEffect(() => {
    void charger(true);
    const intervalle = window.setInterval(() => {
      setMaintenant(Date.now());
      void charger(false);
    }, ACTUALISATION_MS);
    return () => { window.clearInterval(intervalle); controleur.current?.abort(); };
  }, [charger]);

  const transition = async (commande: CommandeCuisine) => {
    setTraitement(commande.numero); setErreurTransition('');
    try {
      const modifie = commande.etat === 'CONFIRMEE'
        ? await cuisineApi.demarrer(commande.numero)
        : await cuisineApi.marquerPrete(commande.numero);
      setNouvelles((courantes) => { const suivantes = new Set(courantes); suivantes.delete(commande.numero); return suivantes; });
      setCommandes((courantes) => courantes.map((item) => item.numero === modifie.numero ? modifie : item));
    } catch (error: unknown) {
      setErreurTransition(messageTransition(error));
      if (error instanceof ApiError && error.status === 409) await charger(false);
    } finally { setTraitement(null); }
  };

  const parEtat = useMemo(() => Object.fromEntries(COLONNES.map((colonne) => [colonne.etat, commandes.filter((commande) => commande.etat === colonne.etat)])) as Record<EtatCommandeCuisine, CommandeCuisine[]>, [commandes]);
  const nombreActives = useMemo(() => commandes.filter((commande) => commande.etat !== 'PRETE').length, [commandes]);

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les commandes en cuisine." /></section>;

  return <section className="kitchen-page">
    <header className="kitchen-heading"><div><p className="eyebrow">Tableau de bord cuisine</p><h1>Commandes à préparer</h1><p>Vue limitée aux informations nécessaires à la préparation.</p></div><div className="kitchen-refresh"><button className="button button-secondary" type="button" disabled={actualisation || chargementInitial} onClick={() => void charger(false)}>{actualisation ? 'Actualisation…' : 'Actualiser'}</button><small>{derniereActualisation ? `Mis à jour à ${derniereActualisation.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}` : 'Première actualisation en cours'}</small></div></header>
    <div className="kitchen-live-summary" aria-live="polite"><span>{nombreActives} commande{nombreActives > 1 ? 's' : ''} active{nombreActives > 1 ? 's' : ''}</span>{nouvelles.size > 0 && <strong>{nouvelles.size} nouvelle{nouvelles.size > 1 ? 's' : ''} commande{nouvelles.size > 1 ? 's' : ''}</strong>}<small>Actualisation automatique toutes les 30 secondes</small></div>
    {erreurTransition && <p className="form-error operation-error" role="alert">{erreurTransition}</p>}
    {chargementInitial ? <ContentState type="loading" title="Chargement du flux cuisine" message="Chargement des commandes en cours." />
      : erreur && commandes.length === 0 ? <ContentState type="error" title="Flux cuisine indisponible" message={erreur} retry={() => void charger(false)} />
        : commandes.length === 0 ? <ContentState type="empty" title="Aucune commande à préparer" message="Les nouvelles commandes confirmées apparaîtront à la prochaine actualisation." />
          : <>{erreur && <p className="form-error operation-error" role="alert">Actualisation impossible : {erreur}</p>}<div className="kitchen-board">{COLONNES.map((colonne) => <section className={`kitchen-column kitchen-column-${colonne.etat.toLowerCase()}`} key={colonne.etat} aria-labelledby={`kitchen-${colonne.etat}`}><header><div><h2 id={`kitchen-${colonne.etat}`}>{colonne.titre}</h2><p>{colonne.description}</p></div><span>{parEtat[colonne.etat].length}</span></header><div className="kitchen-column-content">{parEtat[colonne.etat].length === 0 ? <p className="kitchen-column-empty">Aucune commande dans cet état.</p> : parEtat[colonne.etat].map((commande) => <CarteCommande key={commande.numero} commande={commande} nouvelle={nouvelles.has(commande.numero)} maintenant={maintenant} traitement={traitement} onTransition={(item) => void transition(item)} />)}</div></section>)}</div></>}
  </section>;
}
