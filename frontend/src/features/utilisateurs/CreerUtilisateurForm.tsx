import { useEffect, useState, type FormEvent } from 'react';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ApiError } from '../../api/client';
import { erreursValidation, messageErreur } from './erreurs';
import { LIBELLES_ROLES, NOMS_ROLES, type EmployeDisponible, type NomRole, type UtilisateurAdministration } from './types';
import { utilisateurApi } from './utilisateurApi';

interface CreerUtilisateurFormProps {
  onCancel: () => void;
  onCreated: (utilisateur: UtilisateurAdministration) => void;
}

interface ErreursFormulaire {
  email?: string;
  motDePasse?: string;
  confirmationMotDePasse?: string;
  employeId?: string;
  roles?: string;
}

export function CreerUtilisateurForm({ onCancel, onCreated }: CreerUtilisateurFormProps) {
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [confirmationMotDePasse, setConfirmationMotDePasse] = useState('');
  const [employeId, setEmployeId] = useState('');
  const [roles, setRoles] = useState<NomRole[]>([]);
  const [employes, setEmployes] = useState<EmployeDisponible[]>([]);
  const [chargementEmployes, setChargementEmployes] = useState(true);
  const [erreurEmployes, setErreurEmployes] = useState('');
  const [erreurs, setErreurs] = useState<ErreursFormulaire>({});
  const [erreurServeur, setErreurServeur] = useState('');
  const [confirmation, setConfirmation] = useState(false);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    let actif = true;
    utilisateurApi.listerEmployesActifs()
      .then((resultat) => { if (actif) setEmployes(resultat); })
      .catch((error: unknown) => { if (actif) setErreurEmployes(messageErreur(error)); })
      .finally(() => { if (actif) setChargementEmployes(false); });
    return () => { actif = false; };
  }, []);

  const valider = (): boolean => {
    const suivantes: ErreursFormulaire = {};
    const emailNormalise = email.trim();
    if (!emailNormalise || emailNormalise.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailNormalise)) {
      suivantes.email = 'Saisissez une adresse e-mail valide de 254 caractères maximum.';
    }
    if (motDePasse.length < 12 || motDePasse.length > 128) {
      suivantes.motDePasse = 'Le mot de passe doit contenir entre 12 et 128 caractères.';
    }
    if (confirmationMotDePasse !== motDePasse) {
      suivantes.confirmationMotDePasse = 'Les deux mots de passe ne correspondent pas.';
    }
    if (!employeId) suivantes.employeId = 'Sélectionnez un employé.';
    if (roles.length === 0) suivantes.roles = 'Sélectionnez au moins un rôle.';
    setErreurs(suivantes);
    return Object.keys(suivantes).length === 0;
  };

  const demanderConfirmation = (event: FormEvent) => {
    event.preventDefault();
    setErreurServeur('');
    if (valider()) setConfirmation(true);
  };

  const creer = async () => {
    setEnvoi(true);
    setErreurServeur('');
    try {
      const cree = await utilisateurApi.creer({
        email: email.trim(),
        motDePasse,
        employeId: Number(employeId),
        roles,
      });
      setMotDePasse('');
      setConfirmationMotDePasse('');
      setConfirmation(false);
      onCreated(cree);
    } catch (error: unknown) {
      const validation = erreursValidation(error);
      setErreurs((courantes) => ({ ...courantes, ...validation }));
      setErreurServeur(messageErreur(error));
      setConfirmation(false);
      if (error instanceof ApiError && error.status === 403) {
        setErreurServeur('Vous n’avez pas l’autorisation de créer ce compte.');
      }
    } finally {
      setEnvoi(false);
    }
  };

  const basculerRole = (role: NomRole) => {
    setRoles((courants) => courants.includes(role)
      ? courants.filter((courant) => courant !== role)
      : [...courants, role]);
  };

  return <section className="admin-card" aria-labelledby="creation-utilisateur-title">
    <div className="card-heading">
      <div><p className="eyebrow">Nouveau compte</p><h2 id="creation-utilisateur-title">Créer un utilisateur</h2></div>
      <button className="button button-quiet" type="button" onClick={onCancel}>Fermer</button>
    </div>
    <form className="admin-form" onSubmit={demanderConfirmation} noValidate>
      <label htmlFor="nouvel-email">Adresse e-mail</label>
      <input id="nouvel-email" type="email" autoComplete="off" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} aria-invalid={Boolean(erreurs.email)} />
      {erreurs.email && <p className="field-error">{erreurs.email}</p>}

      <label htmlFor="nouvel-employe">Employé</label>
      {chargementEmployes && <p className="field-hint" role="status">Chargement des employés…</p>}
      {erreurEmployes && <p className="form-error" role="alert">{erreurEmployes}</p>}
      {!chargementEmployes && !erreurEmployes && <select id="nouvel-employe" value={employeId} onChange={(event) => setEmployeId(event.target.value)} aria-invalid={Boolean(erreurs.employeId)}>
        <option value="">Sélectionner un employé</option>
        {employes.map((employe) => <option value={employe.id} key={employe.id}>{employe.prenom} {employe.nom} — {employe.matricule}</option>)}
      </select>}
      {!chargementEmployes && !erreurEmployes && employes.length === 0 && <p className="field-hint">Aucun employé actif n’est disponible.</p>}
      {erreurs.employeId && <p className="field-error">{erreurs.employeId}</p>}

      <fieldset>
        <legend>Rôles</legend>
        <div className="choice-grid">
          {NOMS_ROLES.map((role) => <label className="choice-card" key={role}>
            <input type="checkbox" checked={roles.includes(role)} onChange={() => basculerRole(role)} />
            <span>{LIBELLES_ROLES[role]}</span>
          </label>)}
        </div>
      </fieldset>
      {erreurs.roles && <p className="field-error">{erreurs.roles}</p>}

      <label htmlFor="nouveau-mot-de-passe">Mot de passe initial</label>
      <input id="nouveau-mot-de-passe" type="password" autoComplete="new-password" minLength={12} maxLength={128} value={motDePasse} onChange={(event) => setMotDePasse(event.target.value)} aria-invalid={Boolean(erreurs.motDePasse)} />
      {erreurs.motDePasse && <p className="field-error">{erreurs.motDePasse}</p>}

      <label htmlFor="confirmation-nouveau-mot-de-passe">Confirmer le mot de passe</label>
      <input id="confirmation-nouveau-mot-de-passe" type="password" autoComplete="new-password" minLength={12} maxLength={128} value={confirmationMotDePasse} onChange={(event) => setConfirmationMotDePasse(event.target.value)} aria-invalid={Boolean(erreurs.confirmationMotDePasse)} />
      {erreurs.confirmationMotDePasse && <p className="field-error">{erreurs.confirmationMotDePasse}</p>}

      {erreurServeur && <p className="form-error" role="alert">{erreurServeur}</p>}
      <div className="form-actions">
        <button className="button button-secondary" type="button" onClick={onCancel}>Annuler</button>
        <button className="button button-primary" type="submit" disabled={chargementEmployes || employes.length === 0}>Créer le compte</button>
      </div>
    </form>
    {confirmation && <ConfirmationDialog
      title="Créer ce compte utilisateur ?"
      message="Le compte sera créé avec l’employé et les rôles sélectionnés."
      confirmLabel="Confirmer la création"
      busy={envoi}
      onCancel={() => setConfirmation(false)}
      onConfirm={creer}
    />}
  </section>;
}
