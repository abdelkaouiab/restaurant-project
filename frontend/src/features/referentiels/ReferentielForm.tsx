import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { erreursValidation, messageErreur } from '../utilisateurs/erreurs';
import { ACCEPT_IMAGE_PRODUIT, erreurValidationImageProduit } from './imageProduit';
import { referentielsApi } from './referentielsApi';
import type {
  Categorie,
  Client,
  ConfigurationReferentiel,
  Employe,
  EntiteReferentiel,
  Produit,
  ReferentielPayload,
  TableRestaurant,
} from './types';

interface ReferentielFormProps {
  configuration: ConfigurationReferentiel;
  initial?: EntiteReferentiel;
  onCancel: () => void;
  onSaved: (entite: EntiteReferentiel, avertissement?: string) => void;
}

type Valeurs = Record<string, string>;

function valeursInitiales(configuration: ConfigurationReferentiel, initial?: EntiteReferentiel): Valeurs {
  if (!initial) return configuration.id === 'produits' ? { prix: '', categorieId: '', disponible: 'true' } : {};
  switch (configuration.id) {
    case 'employes': {
      const employe = initial as Employe;
      return { matricule: employe.matricule, nom: employe.nom, prenom: employe.prenom, telephone: employe.telephone ?? '', poste: employe.poste, dateEmbauche: employe.dateEmbauche };
    }
    case 'clients': {
      const client = initial as Client;
      return { nom: client.nom, prenom: client.prenom, email: client.email ?? '', telephone: client.telephone ?? '' };
    }
    case 'tables': {
      const table = initial as TableRestaurant;
      return { numero: String(table.numero), capacite: String(table.capacite) };
    }
    case 'categories': {
      const categorie = initial as Categorie;
      return { nom: categorie.nom, description: categorie.description ?? '' };
    }
    case 'produits': {
      const produit = initial as Produit;
      return { nom: produit.nom, description: produit.description ?? '', prix: String(produit.prix), categorieId: String(produit.categorie.id), disponible: String(produit.disponible) };
    }
  }
}

function nettoyerNullable(valeur: string): string | null {
  const nettoyee = valeur.trim();
  return nettoyee || null;
}

function valider(configuration: ConfigurationReferentiel, valeurs: Valeurs): Record<string, string> {
  const erreurs: Record<string, string> = {};
  const requis = (champ: string, libelle: string, max: number) => {
    const valeur = (valeurs[champ] ?? '').trim();
    if (!valeur) erreurs[champ] = `${libelle} est obligatoire.`;
    else if (valeur.length > max) erreurs[champ] = `${libelle} ne doit pas dépasser ${max} caractères.`;
  };
  const telephone = (valeurs.telephone ?? '').trim();
  const verifierTelephone = () => {
    if (telephone && (telephone.length > 30 || !/^\+?[0-9 ()-]{7,30}$/.test(telephone))) {
      erreurs.telephone = 'Le téléphone doit contenir 7 à 30 chiffres ou séparateurs autorisés.';
    }
  };

  switch (configuration.id) {
    case 'employes':
      requis('matricule', 'Le matricule', 30);
      if (valeurs.matricule?.trim() && !/^[A-Za-z0-9_-]+$/.test(valeurs.matricule.trim())) erreurs.matricule = 'Le matricule accepte uniquement lettres, chiffres, tiret et soulignement.';
      requis('nom', 'Le nom', 100);
      requis('prenom', 'Le prénom', 100);
      requis('poste', 'Le poste', 100);
      verifierTelephone();
      if (!valeurs.dateEmbauche) erreurs.dateEmbauche = 'La date d’embauche est obligatoire.';
      else if (valeurs.dateEmbauche > new Date().toISOString().slice(0, 10)) erreurs.dateEmbauche = 'La date d’embauche ne peut pas être future.';
      break;
    case 'clients':
      requis('nom', 'Le nom', 100);
      requis('prenom', 'Le prénom', 100);
      if (valeurs.email?.trim() && (valeurs.email.trim().length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valeurs.email.trim()))) erreurs.email = 'Saisissez une adresse e-mail valide de 254 caractères maximum.';
      verifierTelephone();
      break;
    case 'tables': {
      const numero = Number(valeurs.numero);
      const capacite = Number(valeurs.capacite);
      if (!Number.isInteger(numero) || numero < 1 || numero > 10000) erreurs.numero = 'Le numéro doit être un entier compris entre 1 et 10 000.';
      if (!Number.isInteger(capacite) || capacite < 1 || capacite > 1000) erreurs.capacite = 'La capacité doit être un entier compris entre 1 et 1 000.';
      break;
    }
    case 'categories':
      requis('nom', 'Le nom', 100);
      if ((valeurs.description ?? '').length > 500) erreurs.description = 'La description ne doit pas dépasser 500 caractères.';
      break;
    case 'produits': {
      requis('nom', 'Le nom', 150);
      if ((valeurs.description ?? '').length > 1000) erreurs.description = 'La description ne doit pas dépasser 1 000 caractères.';
      if (!/^\d{1,10}([.,]\d{1,2})?$/.test(valeurs.prix ?? '') || Number((valeurs.prix ?? '').replace(',', '.')) < 0.01) erreurs.prix = 'Le prix doit être compris entre 0,01 et 9 999 999 999,99 avec deux décimales maximum.';
      if (!/^\d+$/.test(valeurs.categorieId ?? '') || Number(valeurs.categorieId) <= 0) erreurs.categorieId = 'Sélectionnez une catégorie active.';
      break;
    }
  }
  return erreurs;
}

function construirePayload(configuration: ConfigurationReferentiel, valeurs: Valeurs): ReferentielPayload {
  switch (configuration.id) {
    case 'employes': return { matricule: valeurs.matricule.trim(), nom: valeurs.nom.trim(), prenom: valeurs.prenom.trim(), telephone: nettoyerNullable(valeurs.telephone ?? ''), poste: valeurs.poste.trim(), dateEmbauche: valeurs.dateEmbauche };
    case 'clients': return { nom: valeurs.nom.trim(), prenom: valeurs.prenom.trim(), email: nettoyerNullable(valeurs.email ?? ''), telephone: nettoyerNullable(valeurs.telephone ?? '') };
    case 'tables': return { numero: Number(valeurs.numero), capacite: Number(valeurs.capacite) };
    case 'categories': return { nom: valeurs.nom.trim(), description: nettoyerNullable(valeurs.description ?? '') };
    case 'produits': return { nom: valeurs.nom.trim(), description: nettoyerNullable(valeurs.description ?? ''), prix: Number(valeurs.prix.replace(',', '.')), categorieId: Number(valeurs.categorieId), disponible: valeurs.disponible !== 'false' };
  }
}

export function ReferentielForm({ configuration, initial, onCancel, onSaved }: ReferentielFormProps) {
  const [valeurs, setValeurs] = useState(() => valeursInitiales(configuration, initial));
  const [erreurs, setErreurs] = useState<Record<string, string>>({});
  const [erreurServeur, setErreurServeur] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [confirmationPrix, setConfirmationPrix] = useState(false);
  const [categories, setCategories] = useState<Categorie[]>([]);
  const [chargementCategories, setChargementCategories] = useState(configuration.id === 'produits');
  const [erreurCategories, setErreurCategories] = useState('');
  const [imageProduit, setImageProduit] = useState<File | null>(null);
  const [erreurImageProduit, setErreurImageProduit] = useState('');

  useEffect(() => {
    if (configuration.id !== 'produits') return;
    const controleur = new AbortController();
    referentielsApi.listerCategoriesActives(controleur.signal)
      .then(setCategories)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) setErreurCategories(messageErreur(error));
      })
      .finally(() => { if (!controleur.signal.aborted) setChargementCategories(false); });
    return () => controleur.abort();
  }, [configuration.id]);

  const prixModifie = useMemo(() => configuration.id === 'produits' && initial
    && Number((valeurs.prix ?? '').replace(',', '.')) !== Number((initial as Produit).prix), [configuration.id, initial, valeurs.prix]);

  const changer = (champ: string, valeur: string) => {
    setValeurs((courantes) => ({ ...courantes, [champ]: valeur }));
    setErreurs((courantes) => ({ ...courantes, [champ]: '' }));
  };

  const enregistrer = async () => {
    setEnvoi(true);
    setErreurServeur('');
    try {
      const payload = construirePayload(configuration, valeurs);
      const resultat = initial
        ? await referentielsApi.modifier(configuration, initial.id, payload)
        : await referentielsApi.creer(configuration, payload);
      setConfirmationPrix(false);
      if (!initial && configuration.id === 'produits' && imageProduit) {
        try {
          const produitAvecImage = await referentielsApi.enregistrerImageProduit(resultat.id, imageProduit);
          onSaved(produitAvecImage);
        } catch (error: unknown) {
          onSaved(
            resultat,
            `Le produit a été créé, mais l’image n’a pas pu être enregistrée : ${messageErreur(error)} Vous pouvez réessayer depuis « Consulter ».`,
          );
        }
      } else {
        onSaved(resultat);
      }
    } catch (error: unknown) {
      setErreurs((courantes) => ({ ...courantes, ...erreursValidation(error) }));
      setErreurServeur(error instanceof ApiError && error.status === 403
        ? 'Vous n’avez pas l’autorisation d’effectuer cette action.'
        : messageErreur(error));
      setConfirmationPrix(false);
    } finally {
      setEnvoi(false);
    }
  };

  const soumettre = (event: FormEvent) => {
    event.preventDefault();
    setErreurServeur('');
    const suivantes = valider(configuration, valeurs);
    setErreurs(suivantes);
    if (Object.keys(suivantes).length > 0 || erreurCategories || erreurImageProduit) return;
    if (prixModifie) setConfirmationPrix(true);
    else void enregistrer();
  };

  const champ = (nom: string, libelle: string, type = 'text', maxLength?: number) => <>
    <label htmlFor={`${configuration.id}-${initial?.id ?? 'nouveau'}-${nom}`}>{libelle}</label>
    <input id={`${configuration.id}-${initial?.id ?? 'nouveau'}-${nom}`} type={type} maxLength={maxLength} value={valeurs[nom] ?? ''} onChange={(event) => changer(nom, event.target.value)} aria-invalid={Boolean(erreurs[nom])} />
    {erreurs[nom] && <p className="field-error">{erreurs[nom]}</p>}
  </>;

  return <form className="admin-form referentiel-form" onSubmit={soumettre} noValidate>
    {configuration.id === 'employes' && <>
      {champ('matricule', 'Matricule', 'text', 30)}
      {champ('nom', 'Nom', 'text', 100)}
      {champ('prenom', 'Prénom', 'text', 100)}
      {champ('poste', 'Poste', 'text', 100)}
      {champ('dateEmbauche', 'Date d’embauche', 'date')}
      {champ('telephone', 'Téléphone professionnel (facultatif)', 'tel', 30)}
    </>}
    {configuration.id === 'clients' && <>
      {champ('nom', 'Nom', 'text', 100)}
      {champ('prenom', 'Prénom', 'text', 100)}
      {champ('email', 'Adresse e-mail (facultative)', 'email', 254)}
      {champ('telephone', 'Téléphone (facultatif)', 'tel', 30)}
    </>}
    {configuration.id === 'tables' && <>
      {champ('numero', 'Numéro', 'number')}
      {champ('capacite', 'Capacité', 'number')}
    </>}
    {configuration.id === 'categories' && <>
      {champ('nom', 'Nom', 'text', 100)}
      <label htmlFor={`${configuration.id}-${initial?.id ?? 'nouveau'}-description`}>Description (facultative)</label>
      <textarea id={`${configuration.id}-${initial?.id ?? 'nouveau'}-description`} maxLength={500} value={valeurs.description ?? ''} onChange={(event) => changer('description', event.target.value)} aria-invalid={Boolean(erreurs.description)} />
      {erreurs.description && <p className="field-error">{erreurs.description}</p>}
    </>}
    {configuration.id === 'produits' && <>
      {champ('nom', 'Nom', 'text', 150)}
      <label htmlFor={`${configuration.id}-${initial?.id ?? 'nouveau'}-description`}>Description (facultative)</label>
      <textarea id={`${configuration.id}-${initial?.id ?? 'nouveau'}-description`} maxLength={1000} value={valeurs.description ?? ''} onChange={(event) => changer('description', event.target.value)} aria-invalid={Boolean(erreurs.description)} />
      {erreurs.description && <p className="field-error">{erreurs.description}</p>}
      {champ('prix', 'Prix', 'text')}
      <label htmlFor={`${configuration.id}-${initial?.id ?? 'nouveau'}-categorieId`}>Catégorie</label>
      {chargementCategories ? <p className="field-hint" role="status">Chargement des catégories…</p> : <select id={`${configuration.id}-${initial?.id ?? 'nouveau'}-categorieId`} value={valeurs.categorieId ?? ''} onChange={(event) => changer('categorieId', event.target.value)} aria-invalid={Boolean(erreurs.categorieId)}>
        <option value="">Sélectionner une catégorie active</option>
        {categories.map((categorie) => <option key={categorie.id} value={categorie.id}>{categorie.nom}</option>)}
      </select>}
      {erreurCategories && <p className="form-error" role="alert">Catégories indisponibles : {erreurCategories}</p>}
      {!chargementCategories && !erreurCategories && categories.length === 0 && <p className="field-hint">Aucune catégorie active n’est disponible.</p>}
      {erreurs.categorieId && <p className="field-error">{erreurs.categorieId}</p>}
      <label htmlFor={`${configuration.id}-${initial?.id ?? 'nouveau'}-disponible`}>Disponibilité métier</label>
      <select id={`${configuration.id}-${initial?.id ?? 'nouveau'}-disponible`} value={valeurs.disponible ?? 'true'} onChange={(event) => changer('disponible', event.target.value)}>
        <option value="true">Disponible à la commande</option>
        <option value="false">Indisponible à la commande</option>
      </select>
      {!initial && <>
        <label htmlFor="produits-nouveau-image">Image du produit (facultative)</label>
        <input
          id="produits-nouveau-image"
          className="product-file-input"
          type="file"
          accept={ACCEPT_IMAGE_PRODUIT}
          disabled={envoi}
          aria-describedby="produits-nouveau-image-aide"
          aria-invalid={Boolean(erreurImageProduit)}
          onChange={(event) => {
            const fichier = event.target.files?.[0] ?? null;
            const erreur = fichier ? erreurValidationImageProduit(fichier) : '';
            setErreurImageProduit(erreur);
            setImageProduit(erreur ? null : fichier);
          }}
        />
        <p id="produits-nouveau-image-aide" className="field-hint">PNG, JPEG ou WebP — 5 Mo maximum.</p>
        {imageProduit && <p className="field-hint">Fichier sélectionné : {imageProduit.name}</p>}
        {erreurImageProduit && <p className="field-error">{erreurImageProduit}</p>}
      </>}
    </>}

    {erreurServeur && <p className="form-error" role="alert">{erreurServeur}</p>}
    <div className="form-actions">
      <button className="button button-secondary" type="button" disabled={envoi} onClick={onCancel}>Annuler</button>
      <button className="button button-primary" type="submit" disabled={envoi || chargementCategories || Boolean(erreurCategories) || (configuration.id === 'produits' && categories.length === 0)}>{envoi ? 'Enregistrement…' : initial ? 'Enregistrer les modifications' : `Créer ${configuration.article}`}</button>
    </div>

    {confirmationPrix && <ConfirmationDialog
      title="Confirmer la modification du prix ?"
      message={`Le prix passera de ${Number((initial as Produit).prix).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} à ${Number(valeurs.prix.replace(',', '.')).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}.`}
      confirmLabel="Modifier le prix"
      busy={envoi}
      onCancel={() => setConfirmationPrix(false)}
      onConfirm={() => void enregistrer()}
    />}
  </form>;
}
