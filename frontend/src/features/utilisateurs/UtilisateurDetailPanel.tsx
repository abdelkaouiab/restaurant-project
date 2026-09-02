import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import type { DroitsAdministrationUtilisateurs } from './droitsUtilisateur';
import { erreursValidation, messageErreur } from './erreurs';
import { LIBELLES_ROLES, NOMS_ROLES, type NomRole, type UtilisateurAdministration } from './types';
import { utilisateurApi } from './utilisateurApi';

interface UtilisateurDetailPanelProps {
  utilisateur: UtilisateurAdministration;
  droits: DroitsAdministrationUtilisateurs;
  onClose: () => void;
  onUpdated: (utilisateur: UtilisateurAdministration) => void;
}

type ActionConfirmee = 'roles' | 'statut' | 'mot-de-passe' | null;

export function UtilisateurDetailPanel({ utilisateur, droits, onClose, onUpdated }: UtilisateurDetailPanelProps) {
  const [email, setEmail] = useState(utilisateur.email);
  const [roles, setRoles] = useState<NomRole[]>(utilisateur.roles);
  const [nouveauMotDePasse, setNouveauMotDePasse] = useState('');
  const [confirmationMotDePasse, setConfirmationMotDePasse] = useState('');
  const [action, setAction] = useState<ActionConfirmee>(null);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState('');
  const [succes, setSucces] = useState('');
  const [erreursChamps, setErreursChamps] = useState<Record<string, string>>({});
  const [verificationAdmin, setVerificationAdmin] = useState(false);
  const [erreurVerificationAdmin, setErreurVerificationAdmin] = useState(false);
  const [dernierAdministrateur, setDernierAdministrateur] = useState(false);

  const estAdministrateurActif = utilisateur.actif && utilisateur.roles.includes('ADMINISTRATEUR');

  useEffect(() => {
    setEmail(utilisateur.email);
    setRoles(utilisateur.roles);
    setNouveauMotDePasse('');
    setConfirmationMotDePasse('');
    setErreur('');
    setSucces('');
  }, [utilisateur]);

  useEffect(() => {
    let monte = true;
    if (!estAdministrateurActif || (!droits.peutModifier && !droits.peutModifierRoles)) {
      setVerificationAdmin(false);
      setErreurVerificationAdmin(false);
      setDernierAdministrateur(false);
      return () => { monte = false; };
    }
    setVerificationAdmin(true);
    setErreurVerificationAdmin(false);
    utilisateurApi.compterAdministrateursActifs()
      .then((nombre) => { if (monte) setDernierAdministrateur(nombre <= 1); })
      .catch(() => { if (monte) setErreurVerificationAdmin(true); })
      .finally(() => { if (monte) setVerificationAdmin(false); });
    return () => { monte = false; };
  }, [droits.peutModifier, droits.peutModifierRoles, estAdministrateurActif, utilisateur.id]);

  const executer = async () => {
    if (!action) return;
    setEnvoi(true);
    setErreur('');
    setSucces('');
    try {
      if (action === 'roles') {
        const modifie = await utilisateurApi.modifierRoles(utilisateur.id, roles);
        onUpdated(modifie);
        setSucces('Les rôles ont été mis à jour.');
      } else if (action === 'statut') {
        const modifie = await utilisateurApi.modifierStatut(utilisateur.id, !utilisateur.actif);
        onUpdated(modifie);
        setSucces(modifie.actif ? 'Le compte a été réactivé.' : 'Le compte a été désactivé.');
      } else {
        await utilisateurApi.reinitialiserMotDePasse(utilisateur.id, nouveauMotDePasse);
        setNouveauMotDePasse('');
        setConfirmationMotDePasse('');
        setSucces('Le mot de passe a été réinitialisé.');
      }
      setAction(null);
    } catch (error: unknown) {
      setErreursChamps(erreursValidation(error));
      setErreur(error instanceof ApiError && error.status === 403
        ? 'Vous n’avez pas l’autorisation d’effectuer cette action.'
        : messageErreur(error));
      setAction(null);
    } finally {
      setEnvoi(false);
    }
  };

  const modifierEmail = async (event: FormEvent) => {
    event.preventDefault();
    const normalise = email.trim();
    if (!normalise || normalise.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalise)) {
      setErreursChamps({ email: 'Saisissez une adresse e-mail valide de 254 caractères maximum.' });
      return;
    }
    setEnvoi(true);
    setErreur('');
    setSucces('');
    try {
      const modifie = await utilisateurApi.modifier(utilisateur.id, normalise);
      onUpdated(modifie);
      setSucces('Les informations ont été mises à jour.');
      setErreursChamps({});
    } catch (error: unknown) {
      setErreursChamps(erreursValidation(error));
      setErreur(messageErreur(error));
    } finally {
      setEnvoi(false);
    }
  };

  const demanderRoles = (event: FormEvent) => {
    event.preventDefault();
    if (roles.length === 0) {
      setErreursChamps({ roles: 'Sélectionnez au moins un rôle.' });
      return;
    }
    if (estAdministrateurActif && !roles.includes('ADMINISTRATEUR')
      && (verificationAdmin || erreurVerificationAdmin || dernierAdministrateur)) return;
    setErreursChamps({});
    setAction('roles');
  };

  const demanderReinitialisation = (event: FormEvent) => {
    event.preventDefault();
    const suivantes: Record<string, string> = {};
    if (nouveauMotDePasse.length < 12 || nouveauMotDePasse.length > 128) {
      suivantes.nouveauMotDePasse = 'Le mot de passe doit contenir entre 12 et 128 caractères.';
    }
    if (nouveauMotDePasse !== confirmationMotDePasse) {
      suivantes.confirmationMotDePasse = 'Les deux mots de passe ne correspondent pas.';
    }
    setErreursChamps(suivantes);
    if (Object.keys(suivantes).length === 0) setAction('mot-de-passe');
  };

  const basculerRole = (role: NomRole) => {
    if (role === 'ADMINISTRATEUR' && estAdministrateurActif
      && (verificationAdmin || erreurVerificationAdmin || dernierAdministrateur)) return;
    setRoles((courants) => courants.includes(role)
      ? courants.filter((courant) => courant !== role)
      : [...courants, role]);
  };

  const actionStatutBloquee = estAdministrateurActif
    && (verificationAdmin || erreurVerificationAdmin || dernierAdministrateur);

  return <section className="admin-card detail-panel" aria-labelledby="detail-utilisateur-title">
    <div className="card-heading">
      <div>
        <p className="eyebrow">Détail du compte</p>
        <h2 id="detail-utilisateur-title">{utilisateur.employe.prenom} {utilisateur.employe.nom}</h2>
      </div>
      <button className="button button-quiet" type="button" onClick={onClose}>Fermer</button>
    </div>

    <dl className="detail-grid">
      <div><dt>Matricule</dt><dd>{utilisateur.employe.matricule}</dd></div>
      <div><dt>État</dt><dd><span className={`status-pill ${utilisateur.actif ? 'status-active' : 'status-inactive'}`}>{utilisateur.actif ? 'Actif' : 'Inactif'}</span></dd></div>
      <div><dt>Créé le</dt><dd>{new Date(utilisateur.creeLe).toLocaleString('fr-FR')}</dd></div>
      <div><dt>Modifié le</dt><dd>{new Date(utilisateur.modifieLe).toLocaleString('fr-FR')}</dd></div>
    </dl>

    {succes && <p className="form-success" role="status">{succes}</p>}
    {erreur && <p className="form-error" role="alert">{erreur}</p>}
    {estAdministrateurActif && verificationAdmin && <p className="field-hint" role="status">Vérification de la protection du dernier administrateur…</p>}
    {estAdministrateurActif && erreurVerificationAdmin && <p className="form-warning" role="alert">La protection n’a pas pu être vérifiée. Les actions qui retirent le dernier accès administrateur sont désactivées.</p>}
    {dernierAdministrateur && <p className="form-warning" role="status">Ce compte est le dernier administrateur actif : sa désactivation et le retrait de son rôle administrateur sont bloqués.</p>}

    <div className="detail-sections">
      <section>
        <h3>Informations</h3>
        {droits.peutModifier ? <form className="admin-form compact-form" onSubmit={modifierEmail} noValidate>
          <label htmlFor={`email-${utilisateur.id}`}>Adresse e-mail</label>
          <input id={`email-${utilisateur.id}`} type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} />
          {erreursChamps.email && <p className="field-error">{erreursChamps.email}</p>}
          <button className="button button-secondary" type="submit" disabled={envoi || email.trim() === utilisateur.email}>Enregistrer</button>
        </form> : <p>{utilisateur.email}</p>}
      </section>

      <section>
        <h3>Rôles</h3>
        {droits.peutModifierRoles ? <form className="admin-form compact-form" onSubmit={demanderRoles}>
          <div className="choice-grid">
            {NOMS_ROLES.map((role) => {
              const bloque = role === 'ADMINISTRATEUR' && estAdministrateurActif
                && (verificationAdmin || erreurVerificationAdmin || dernierAdministrateur);
              return <label className={`choice-card ${bloque ? 'choice-disabled' : ''}`} key={role}>
                <input type="checkbox" checked={roles.includes(role)} disabled={bloque} onChange={() => basculerRole(role)} />
                <span>{LIBELLES_ROLES[role]}</span>
              </label>;
            })}
          </div>
          {erreursChamps.roles && <p className="field-error">{erreursChamps.roles}</p>}
          <button className="button button-secondary" type="submit" disabled={envoi || roles.join('|') === utilisateur.roles.join('|')}>Enregistrer les rôles</button>
        </form> : <div className="role-list compact-role-list">{utilisateur.roles.map((role) => <span key={role}>{LIBELLES_ROLES[role]}</span>)}</div>}
      </section>

      {droits.peutModifier && <section>
        <h3>État du compte</h3>
        <button className={`button ${utilisateur.actif ? 'button-danger' : 'button-secondary'}`} type="button" disabled={envoi || actionStatutBloquee} onClick={() => setAction('statut')}>
          {utilisateur.actif ? 'Désactiver le compte' : 'Réactiver le compte'}
        </button>
      </section>}

      {droits.peutModifier && <section>
        <h3>Réinitialiser le mot de passe</h3>
        <form className="admin-form compact-form" onSubmit={demanderReinitialisation} noValidate>
          <label htmlFor={`mot-de-passe-${utilisateur.id}`}>Nouveau mot de passe</label>
          <input id={`mot-de-passe-${utilisateur.id}`} type="password" autoComplete="new-password" minLength={12} maxLength={128} value={nouveauMotDePasse} onChange={(event) => setNouveauMotDePasse(event.target.value)} />
          {erreursChamps.nouveauMotDePasse && <p className="field-error">{erreursChamps.nouveauMotDePasse}</p>}
          <label htmlFor={`confirmation-mot-de-passe-${utilisateur.id}`}>Confirmer le mot de passe</label>
          <input id={`confirmation-mot-de-passe-${utilisateur.id}`} type="password" autoComplete="new-password" minLength={12} maxLength={128} value={confirmationMotDePasse} onChange={(event) => setConfirmationMotDePasse(event.target.value)} />
          {erreursChamps.confirmationMotDePasse && <p className="field-error">{erreursChamps.confirmationMotDePasse}</p>}
          <button className="button button-secondary" type="submit" disabled={envoi}>Réinitialiser</button>
        </form>
      </section>}
    </div>

    {action && <ConfirmationDialog
      title={action === 'roles' ? 'Modifier les rôles ?' : action === 'statut' ? `${utilisateur.actif ? 'Désactiver' : 'Réactiver'} ce compte ?` : 'Réinitialiser le mot de passe ?'}
      message={action === 'roles' ? 'Les droits effectifs de cet utilisateur pourront changer immédiatement.' : action === 'statut' ? 'Cette action modifie immédiatement la possibilité de connexion.' : 'Le mot de passe actuel ne permettra plus de se connecter.'}
      confirmLabel="Confirmer"
      busy={envoi}
      onCancel={() => setAction(null)}
      onConfirm={executer}
    />}
  </section>;
}
