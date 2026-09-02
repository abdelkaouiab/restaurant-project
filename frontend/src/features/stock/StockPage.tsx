import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ContentState } from '../../components/ContentState';
import { formatQuantite, MESSAGE_QUANTITE_UNITAIRE, pasQuantite, quantiteCompatibleAvecUnite } from '../../utils/quantites';
import { messageErreur } from '../utilisateurs/erreurs';
import { stockApi } from './stockApi';
import type { Ingredient, MouvementStock, PageMouvementsStock, RecetteIngredient, TypeMouvement, UniteMesure } from './types';

const UNITES: { valeur: UniteMesure; libelle: string }[] = [
  { valeur: 'GRAMME', libelle: 'Gramme' }, { valeur: 'KILOGRAMME', libelle: 'Kilogramme' },
  { valeur: 'MILLILITRE', libelle: 'Millilitre' }, { valeur: 'LITRE', libelle: 'Litre' },
  { valeur: 'UNITE', libelle: 'Unité' },
];
const MOUVEMENTS: { valeur: TypeMouvement; libelle: string }[] = [
  { valeur: 'ENTREE', libelle: 'Entrée' }, { valeur: 'SORTIE', libelle: 'Sortie' },
  { valeur: 'AJUSTEMENT', libelle: 'Ajustement' }, { valeur: 'PERTE', libelle: 'Perte' },
  { valeur: 'RETOUR', libelle: 'Retour' },
];
const dateHeure = (valeur: string) => new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(valeur));
const messageOperation = (error: unknown) => error instanceof ApiError && error.status === 409
  ? `Conflit : ${error.message}` : messageErreur(error);

export function StockPage() {
  const { utilisateur } = useAuth();
  const peutGerer = utilisateur?.permissions.includes('STOCK_GERER') ?? false;
  const peutConsulterProduits = utilisateur?.permissions.includes('PRODUIT_CONSULTER') ?? false;
  const [ingredients, setIngredients] = useState<Ingredient[]>([]);
  const [produits, setProduits] = useState<{ id: number; nom: string; actif: boolean }[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [recherche, setRecherche] = useState('');
  const [seulementAlertes, setSeulementAlertes] = useState(false);
  const controleur = useRef<AbortController | null>(null);

  const [ingredientEdite, setIngredientEdite] = useState<Ingredient | null>(null);
  const [nom, setNom] = useState('');
  const [unite, setUnite] = useState<UniteMesure>('KILOGRAMME');
  const [seuil, setSeuil] = useState('0');
  const [erreurIngredient, setErreurIngredient] = useState('');
  const [envoiIngredient, setEnvoiIngredient] = useState(false);
  const nomIngredientRef = useRef<HTMLInputElement | null>(null);

  const [produitId, setProduitId] = useState('');
  const [recette, setRecette] = useState<RecetteIngredient[]>([]);
  const [chargementRecette, setChargementRecette] = useState(false);
  const [ingredientRecette, setIngredientRecette] = useState('');
  const [quantiteRecette, setQuantiteRecette] = useState('');
  const [erreurRecette, setErreurRecette] = useState('');
  const [envoiRecette, setEnvoiRecette] = useState(false);
  const [associationASupprimer, setAssociationASupprimer] = useState<RecetteIngredient | null>(null);
  const [suppressionRecette, setSuppressionRecette] = useState(false);

  const [ingredientMouvement, setIngredientMouvement] = useState('');
  const [typeMouvement, setTypeMouvement] = useState<TypeMouvement>('ENTREE');
  const [quantiteMouvement, setQuantiteMouvement] = useState('');
  const [motif, setMotif] = useState('');
  const [erreurMouvement, setErreurMouvement] = useState('');
  const [dernierMouvement, setDernierMouvement] = useState<MouvementStock | null>(null);
  const [confirmationMouvement, setConfirmationMouvement] = useState(false);
  const [envoiMouvement, setEnvoiMouvement] = useState(false);
  const cleMouvement = useRef<string | null>(null);
  const mouvementEnCours = useRef(false);
  const [historique, setHistorique] = useState<PageMouvementsStock>({ contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0 });
  const [pageHistorique, setPageHistorique] = useState(0);
  const [ingredientHistorique, setIngredientHistorique] = useState('');
  const [typeHistorique, setTypeHistorique] = useState('');
  const [chargementHistorique, setChargementHistorique] = useState(true);
  const [erreurHistorique, setErreurHistorique] = useState('');
  const controleurHistorique = useRef<AbortController | null>(null);

  const charger = useCallback(async () => {
    controleur.current?.abort();
    const suivant = new AbortController();
    controleur.current = suivant;
    setChargement(true); setErreur('');
    try {
      const [listeIngredients, listeProduits] = await Promise.all([
        stockApi.listerIngredients(suivant.signal),
        peutConsulterProduits ? stockApi.listerProduits(suivant.signal) : Promise.resolve([]),
      ]);
      setIngredients(listeIngredients); setProduits(listeProduits); setAccesRefuse(false);
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
      else setErreur(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargement(false); }
  }, [peutConsulterProduits]);

  useEffect(() => { void charger(); return () => controleur.current?.abort(); }, [charger]);

  const chargerHistorique = useCallback(async () => {
    controleurHistorique.current?.abort();
    const suivant = new AbortController();
    controleurHistorique.current = suivant;
    setChargementHistorique(true); setErreurHistorique('');
    try {
      setHistorique(await stockApi.listerMouvements({
        ingredientId: ingredientHistorique ? Number(ingredientHistorique) : undefined,
        type: typeHistorique ? typeHistorique as TypeMouvement : undefined,
        page: pageHistorique,
        taille: 10,
      }, suivant.signal));
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      setErreurHistorique(messageErreur(error));
    } finally { if (!suivant.signal.aborted) setChargementHistorique(false); }
  }, [ingredientHistorique, pageHistorique, typeHistorique]);

  useEffect(() => {
    void chargerHistorique();
    return () => controleurHistorique.current?.abort();
  }, [chargerHistorique]);

  const ingredientsFiltres = useMemo(() => ingredients.filter((item) => {
    const correspond = item.nom.toLocaleLowerCase('fr').includes(recherche.trim().toLocaleLowerCase('fr'));
    return correspond && (!seulementAlertes || item.stockFaible);
  }), [ingredients, recherche, seulementAlertes]);
  const alertes = useMemo(() => ingredients.filter((item) => item.actif && item.stockFaible), [ingredients]);
  const actifs = useMemo(() => ingredients.filter((item) => item.actif), [ingredients]);

  const preparerIngredient = (item?: Ingredient) => {
    setIngredientEdite(item ?? null); setNom(item?.nom ?? ''); setUnite(item?.unite ?? 'KILOGRAMME');
    setSeuil(item ? String(item.seuilAlerte) : '0'); setErreurIngredient('');
  };
  const afficherFormulaireIngredient = (item?: Ingredient) => {
    preparerIngredient(item);
    requestAnimationFrame(() => nomIngredientRef.current?.focus());
  };
  const enregistrerIngredient = async (event: FormEvent) => {
    event.preventDefault();
    const valeurSeuil = Number(seuil.replace(',', '.'));
    if (!nom.trim() || !Number.isFinite(valeurSeuil) || valeurSeuil < 0) {
      setErreurIngredient('Le nom et un seuil positif ou nul sont obligatoires.'); return;
    }
    if (!quantiteCompatibleAvecUnite(valeurSeuil, unite)
      || (ingredientEdite && !quantiteCompatibleAvecUnite(ingredientEdite.quantiteDisponible, unite))) {
      setErreurIngredient(MESSAGE_QUANTITE_UNITAIRE); return;
    }
    setEnvoiIngredient(true); setErreurIngredient('');
    try {
      const payload = { nom: nom.trim(), unite, seuilAlerte: valeurSeuil };
      const sauvegarde = ingredientEdite
        ? await stockApi.modifierIngredient(ingredientEdite.id, payload)
        : await stockApi.creerIngredient(payload);
      setIngredients((courants) => ingredientEdite
        ? courants.map((item) => item.id === sauvegarde.id ? sauvegarde : item)
        : [...courants, sauvegarde].sort((a, b) => a.nom.localeCompare(b.nom, 'fr')));
      preparerIngredient();
    } catch (error: unknown) { setErreurIngredient(messageOperation(error)); }
    finally { setEnvoiIngredient(false); }
  };

  const chargerRecette = async (valeur: string) => {
    setProduitId(valeur); setRecette([]); setErreurRecette('');
    if (!valeur) return;
    setChargementRecette(true);
    try { setRecette(await stockApi.lireRecette(Number(valeur))); }
    catch (error: unknown) { setErreurRecette(messageOperation(error)); }
    finally { setChargementRecette(false); }
  };
  const enregistrerRecette = async (event: FormEvent) => {
    event.preventDefault();
    const valeur = Number(quantiteRecette.replace(',', '.'));
    if (!produitId || !ingredientRecette || !Number.isFinite(valeur) || valeur <= 0) {
      setErreurRecette('Sélectionnez un produit, un ingrédient et une quantité strictement positive.'); return;
    }
    const uniteRecette = ingredients.find((item) => item.id === Number(ingredientRecette))?.unite;
    if (!quantiteCompatibleAvecUnite(valeur, uniteRecette)) {
      setErreurRecette(MESSAGE_QUANTITE_UNITAIRE); return;
    }
    setEnvoiRecette(true); setErreurRecette('');
    try {
      const association = await stockApi.definirRecette(Number(produitId), Number(ingredientRecette), valeur);
      setRecette((courante) => courante.some((item) => item.ingredientId === association.ingredientId)
        ? courante.map((item) => item.ingredientId === association.ingredientId ? association : item)
        : [...courante, association]);
      setIngredientRecette(''); setQuantiteRecette('');
    } catch (error: unknown) { setErreurRecette(messageOperation(error)); }
    finally { setEnvoiRecette(false); }
  };
  const supprimerIngredientRecette = async () => {
    if (!produitId || !associationASupprimer || suppressionRecette) return;
    setSuppressionRecette(true); setErreurRecette('');
    try {
      await stockApi.supprimerIngredientRecette(Number(produitId), associationASupprimer.ingredientId, associationASupprimer.version);
      setRecette((courante) => courante.filter((item) => item.ingredientId !== associationASupprimer.ingredientId));
      setAssociationASupprimer(null);
    } catch (error: unknown) {
      setErreurRecette(messageOperation(error)); setAssociationASupprimer(null);
    } finally { setSuppressionRecette(false); }
  };

  const changerMouvement = () => { setErreurMouvement(''); cleMouvement.current = null; };
  const demanderMouvement = (event: FormEvent) => {
    event.preventDefault();
    const valeur = Number(quantiteMouvement.replace(',', '.'));
    const motifObligatoire = typeMouvement === 'AJUSTEMENT' || typeMouvement === 'PERTE';
    if (!ingredientMouvement || !Number.isFinite(valeur) || valeur === 0) {
      setErreurMouvement('Sélectionnez un ingrédient et une quantité non nulle.'); return;
    }
    const uniteMouvement = ingredients.find((item) => item.id === Number(ingredientMouvement))?.unite;
    if (!quantiteCompatibleAvecUnite(valeur, uniteMouvement)) {
      setErreurMouvement(MESSAGE_QUANTITE_UNITAIRE); return;
    }
    if (typeMouvement !== 'AJUSTEMENT' && valeur < 0) {
      setErreurMouvement('La quantité doit être positive pour ce type de mouvement.'); return;
    }
    if (motifObligatoire && !motif.trim()) {
      setErreurMouvement('Un motif est obligatoire pour les ajustements et les pertes.'); return;
    }
    if (!cleMouvement.current) cleMouvement.current = globalThis.crypto.randomUUID();
    setConfirmationMouvement(true);
  };
  const enregistrerMouvement = async () => {
    if (mouvementEnCours.current || !cleMouvement.current) return;
    mouvementEnCours.current = true;
    setEnvoiMouvement(true); setErreurMouvement('');
    try {
      const resultat = await stockApi.creerMouvement(cleMouvement.current, {
        ingredientId: Number(ingredientMouvement), type: typeMouvement,
        quantite: Number(quantiteMouvement.replace(',', '.')), motif: motif.trim() || null,
      });
      setDernierMouvement(resultat);
      setIngredients(await stockApi.listerIngredients());
      setQuantiteMouvement(''); setMotif(''); cleMouvement.current = null; setConfirmationMouvement(false);
      if (pageHistorique !== 0) setPageHistorique(0); else await chargerHistorique();
    } catch (error: unknown) { setErreurMouvement(messageOperation(error)); setConfirmationMouvement(false); }
    finally { mouvementEnCours.current = false; setEnvoiMouvement(false); }
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter le stock." /></section>;
  if (chargement) return <section className="section-page"><ContentState type="loading" title="Chargement du stock" message="Chargement des ingrédients et des produits." /></section>;
  if (erreur) return <section className="section-page"><ContentState type="error" title="Stock indisponible" message={erreur} retry={() => void charger()} /></section>;

  return <section className="service-page stock-page">
    <header className="section-heading"><div><p className="eyebrow">Gestion du stock</p><h1>Ingrédients et mouvements</h1><p>La quantité disponible n’est jamais modifiable directement depuis l’interface.</p></div><span className="section-badge">{alertes.length} alerte{alertes.length > 1 ? 's' : ''}</span></header>
    <section className="admin-card" aria-labelledby="ingredients-title">
      <header className="card-heading"><div><p className="eyebrow">Disponibilités</p><h2 id="ingredients-title">Ingrédients</h2></div>{peutGerer && <button className="button button-primary heading-action" type="button" aria-controls="ingredient-form" onClick={() => afficherFormulaireIngredient()}>Nouvel ingrédient</button>}</header>
      <div className="stock-toolbar"><label>Rechercher<input type="search" value={recherche} onChange={(event) => setRecherche(event.target.value)} placeholder="Nom de l’ingrédient" /></label><label className="choice-card"><input type="checkbox" checked={seulementAlertes} onChange={(event) => setSeulementAlertes(event.target.checked)} /> Alertes uniquement</label></div>
      {ingredientsFiltres.length === 0 ? <ContentState type="empty" title="Aucun ingrédient" message="Aucun ingrédient ne correspond au filtre sélectionné." /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Ingrédient</th><th>Unité</th><th>Disponible</th><th>Seuil</th><th>État</th>{peutGerer && <th><span className="visually-hidden">Action</span></th>}</tr></thead><tbody>{ingredientsFiltres.map((item) => <tr key={item.id}><td><strong>{item.nom}</strong><small>{item.actif ? 'Actif' : 'Inactif'}</small></td><td>{UNITES.find((uniteItem) => uniteItem.valeur === item.unite)?.libelle}</td><td><strong>{formatQuantite(item.quantiteDisponible, item.unite)}</strong></td><td>{formatQuantite(item.seuilAlerte, item.unite)}</td><td><span className={`status-pill ${item.stockFaible ? 'status-inactive' : 'status-active'}`}>{item.stockFaible ? 'Stock faible' : 'Stock suffisant'}</span></td>{peutGerer && <td><button className="button button-quiet" type="button" aria-controls="ingredient-form" onClick={() => afficherFormulaireIngredient(item)}>Modifier</button></td>}</tr>)}</tbody></table></div>}
      {peutGerer && <form id="ingredient-form" className="admin-form stock-inline-form" onSubmit={(event) => void enregistrerIngredient(event)} noValidate><h3>{ingredientEdite ? `Modifier ${ingredientEdite.nom}` : 'Créer un ingrédient'}</h3><label htmlFor="ingredient-name">Nom</label><input ref={nomIngredientRef} id="ingredient-name" maxLength={150} value={nom} onChange={(event) => setNom(event.target.value)} /><label htmlFor="ingredient-unit">Unité</label><select id="ingredient-unit" value={unite} onChange={(event) => setUnite(event.target.value as UniteMesure)}>{UNITES.map((item) => <option value={item.valeur} key={item.valeur}>{item.libelle}</option>)}</select><label htmlFor="ingredient-threshold">Seuil d’alerte</label><input id="ingredient-threshold" step={pasQuantite(unite)} inputMode={unite === 'UNITE' ? 'numeric' : 'decimal'} value={seuil} onChange={(event) => setSeuil(event.target.value)} /><p className="field-hint">La quantité disponible est exclue de ce formulaire et reste gérée par les mouvements.</p>{erreurIngredient && <p className="form-error" role="alert">{erreurIngredient}</p>}<div className="form-actions"><button className="button button-secondary" type="button" onClick={() => preparerIngredient()}>Effacer</button><button className="button button-primary" disabled={envoiIngredient} type="submit">{envoiIngredient ? 'Enregistrement…' : 'Enregistrer'}</button></div></form>}
    </section>

    <div className="stock-two-columns">
      <section className="admin-card" aria-labelledby="alerts-title"><header className="card-heading"><div><p className="eyebrow">Surveillance</p><h2 id="alerts-title">Alertes de stock faible</h2></div></header>{alertes.length === 0 ? <ContentState type="empty" title="Aucune alerte" message="Aucun ingrédient actif ne se trouve sous son seuil d’alerte." /> : <ul className="stock-alert-list">{alertes.map((item) => <li key={item.id}><strong>{item.nom}</strong><span>{formatQuantite(item.quantiteDisponible, item.unite)} / seuil {formatQuantite(item.seuilAlerte, item.unite)} {item.unite.toLowerCase()}</span></li>)}</ul>}</section>
      <section className="admin-card" aria-labelledby="movement-title"><header className="card-heading"><div><p className="eyebrow">Opération métier</p><h2 id="movement-title">Nouveau mouvement</h2></div></header>{peutGerer ? <form className="admin-form" onSubmit={demanderMouvement} noValidate><label htmlFor="movement-ingredient">Ingrédient</label><select id="movement-ingredient" value={ingredientMouvement} onChange={(event) => { setIngredientMouvement(event.target.value); changerMouvement(); }}><option value="">Sélectionner</option>{actifs.map((item) => <option value={item.id} key={item.id}>{item.nom}</option>)}</select><label htmlFor="movement-type">Type</label><select id="movement-type" value={typeMouvement} onChange={(event) => { setTypeMouvement(event.target.value as TypeMouvement); changerMouvement(); }}><option value="ENTREE">Entrée</option><option value="SORTIE">Sortie</option><option value="AJUSTEMENT">Ajustement</option><option value="PERTE">Perte</option><option value="RETOUR">Retour</option></select><label htmlFor="movement-quantity">Quantité</label><input id="movement-quantity" step={pasQuantite(ingredients.find((item) => item.id === Number(ingredientMouvement))?.unite)} inputMode={ingredients.find((item) => item.id === Number(ingredientMouvement))?.unite === 'UNITE' ? 'numeric' : 'decimal'} value={quantiteMouvement} onChange={(event) => { setQuantiteMouvement(event.target.value); changerMouvement(); }} /><label htmlFor="movement-reason">Motif {typeMouvement === 'AJUSTEMENT' || typeMouvement === 'PERTE' ? '(obligatoire)' : '(facultatif)'}</label><textarea id="movement-reason" maxLength={500} value={motif} onChange={(event) => { setMotif(event.target.value); changerMouvement(); }} />{erreurMouvement && <p className="form-error" role="alert">{erreurMouvement}</p>}<button className="button button-primary" disabled={envoiMouvement} type="submit">Vérifier le mouvement</button></form> : <p className="api-limit-note">Vous n’avez pas l’autorisation d’enregistrer un mouvement.</p>}{dernierMouvement && <div className="last-operation" role="status"><strong>Dernier mouvement enregistré</strong><span>{MOUVEMENTS.find((item) => item.valeur === dernierMouvement.type)?.libelle} · nouveau solde {formatQuantite(dernierMouvement.quantiteApres, ingredients.find((item) => item.id === dernierMouvement.ingredientId)?.unite)}</span></div>}</section>
    </div>

    <section className="admin-card" aria-labelledby="stock-history-title">
      <header className="card-heading"><div><p className="eyebrow">Traçabilité</p><h2 id="stock-history-title">Historique des mouvements</h2></div><span>{historique.totalElements} mouvement{historique.totalElements > 1 ? 's' : ''}</span></header>
      <div className="stock-history-toolbar">
        <label htmlFor="history-ingredient">Ingrédient<select id="history-ingredient" value={ingredientHistorique} onChange={(event) => { setIngredientHistorique(event.target.value); setPageHistorique(0); }}><option value="">Tous</option>{ingredients.map((item) => <option value={item.id} key={item.id}>{item.nom}</option>)}</select></label>
        <label htmlFor="history-type">Type<select id="history-type" value={typeHistorique} onChange={(event) => { setTypeHistorique(event.target.value); setPageHistorique(0); }}><option value="">Tous</option>{MOUVEMENTS.map((item) => <option value={item.valeur} key={item.valeur}>{item.libelle}</option>)}</select></label>
      </div>
      {chargementHistorique ? <ContentState type="loading" title="Chargement de l’historique" message="Chargement des mouvements en cours." />
        : erreurHistorique ? <ContentState type="error" title="Historique indisponible" message={erreurHistorique} retry={() => void chargerHistorique()} />
          : historique.contenu.length === 0 ? <ContentState type="empty" title="Aucun mouvement" message="Aucun mouvement ne correspond aux filtres sélectionnés." />
            : <><div className="table-scroll"><table className="data-table"><thead><tr><th>Date</th><th>Ingrédient</th><th>Type</th><th>Quantité</th><th>Variation</th><th>Solde après</th><th>Motif</th></tr></thead><tbody>{historique.contenu.map((item) => { const uniteHistorique = ingredients.find((ingredient) => ingredient.id === item.ingredientId)?.unite; return <tr key={item.id}><td>{dateHeure(item.creeLe)}</td><td><strong>{item.ingredient}</strong></td><td>{MOUVEMENTS.find((mouvementItem) => mouvementItem.valeur === item.type)?.libelle}</td><td>{formatQuantite(item.quantite, uniteHistorique)}</td><td className={item.variation < 0 ? 'stock-negative' : 'stock-positive'}>{item.variation > 0 ? '+' : ''}{formatQuantite(item.variation, uniteHistorique)}</td><td>{formatQuantite(item.quantiteApres, uniteHistorique)}</td><td>{item.motif || '—'}</td></tr>; })}</tbody></table></div><div className="pagination"><button className="button button-secondary" type="button" disabled={pageHistorique === 0} onClick={() => setPageHistorique((page) => page - 1)}>Précédent</button><span>Page {historique.totalPages === 0 ? 0 : historique.page + 1} sur {historique.totalPages}</span><button className="button button-secondary" type="button" disabled={historique.page + 1 >= historique.totalPages} onClick={() => setPageHistorique((page) => page + 1)}>Suivant</button></div></>}
    </section>

    {peutConsulterProduits && <section className="admin-card" aria-labelledby="recipes-title"><header className="card-heading"><div><p className="eyebrow">Associations produit-ingrédient</p><h2 id="recipes-title">Recettes</h2></div></header><label className="stock-product-select" htmlFor="recipe-product">Produit<select id="recipe-product" value={produitId} onChange={(event) => void chargerRecette(event.target.value)}><option value="">Sélectionner un produit</option>{produits.map((produit) => <option value={produit.id} key={produit.id}>{produit.nom}</option>)}</select></label>{erreurRecette && <p className="form-error operation-error" role="alert">{erreurRecette}</p>}{chargementRecette ? <ContentState type="loading" title="Chargement de la recette" message="Chargement des ingrédients de la recette." /> : produitId && recette.length === 0 && !erreurRecette ? <ContentState type="empty" title="Recette vide" message="Ce produit ne possède encore aucune association ingrédient." /> : recette.length > 0 && <div className="table-scroll"><table className="data-table"><thead><tr><th>Ingrédient</th><th>Unité</th><th>Quantité par produit</th>{peutGerer && <th><span className="visually-hidden">Action</span></th>}</tr></thead><tbody>{recette.map((item) => <tr key={item.ingredientId}><td>{item.ingredient}</td><td>{item.unite}</td><td>{formatQuantite(item.quantite, item.unite)}</td>{peutGerer && <td><button className="button button-danger" type="button" disabled={suppressionRecette} onClick={() => setAssociationASupprimer(item)}>Retirer {item.ingredient}</button></td>}</tr>)}</tbody></table></div>}{peutGerer && produitId && <form className="admin-form recipe-form" onSubmit={(event) => void enregistrerRecette(event)}><label htmlFor="recipe-ingredient">Ingrédient</label><select id="recipe-ingredient" value={ingredientRecette} onChange={(event) => setIngredientRecette(event.target.value)}><option value="">Sélectionner</option>{actifs.map((item) => <option value={item.id} key={item.id}>{item.nom}</option>)}</select><label htmlFor="recipe-quantity">Quantité par produit</label><input id="recipe-quantity" step={pasQuantite(ingredients.find((item) => item.id === Number(ingredientRecette))?.unite)} inputMode={ingredients.find((item) => item.id === Number(ingredientRecette))?.unite === 'UNITE' ? 'numeric' : 'decimal'} value={quantiteRecette} onChange={(event) => setQuantiteRecette(event.target.value)} /><button className="button button-primary" disabled={envoiRecette} type="submit">{envoiRecette ? 'Enregistrement…' : 'Ajouter ou mettre à jour'}</button></form>}</section>}

    {confirmationMouvement && <ConfirmationDialog title="Confirmer le mouvement de stock" message="Cette opération modifiera le solde de l’ingrédient sélectionné." confirmLabel="Confirmer le mouvement" busy={envoiMouvement} onCancel={() => { if (!envoiMouvement) setConfirmationMouvement(false); }} onConfirm={() => void enregistrerMouvement()} />}
    {associationASupprimer && <ConfirmationDialog title="Retirer un ingrédient de la recette" message={`Retirer ${associationASupprimer.ingredient} modifiera la consommation de stock des prochaines commandes confirmées. L’historique existant sera conservé.`} confirmLabel="Retirer de la recette" danger busy={suppressionRecette} onCancel={() => { if (!suppressionRecette) setAssociationASupprimer(null); }} onConfirm={() => void supprimerIngredientRecette()} />}
  </section>;
}
