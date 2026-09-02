import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ContentState } from '../../components/ContentState';
import type { PageResponse } from '../../types/api';
import { CreerUtilisateurForm } from './CreerUtilisateurForm';
import { droitsAdministrationUtilisateurs } from './droitsUtilisateur';
import { messageErreur } from './erreurs';
import { LIBELLES_ROLES, type ChampTriUtilisateur, type DirectionTri, type UtilisateurAdministration } from './types';
import { UtilisateurDetailPanel } from './UtilisateurDetailPanel';
import { utilisateurApi } from './utilisateurApi';

const PAGE_VIDE: PageResponse<UtilisateurAdministration> = {
  contenu: [], page: 0, taille: 10, totalElements: 0, totalPages: 0,
};

export function UtilisateursPage() {
  const { utilisateur } = useAuth();
  const droits = droitsAdministrationUtilisateurs(utilisateur);
  const [resultat, setResultat] = useState(PAGE_VIDE);
  const [rechercheSaisie, setRechercheSaisie] = useState('');
  const [recherche, setRecherche] = useState('');
  const [actif, setActif] = useState<'' | 'true' | 'false'>('');
  const [tri, setTri] = useState<ChampTriUtilisateur>('email');
  const [direction, setDirection] = useState<DirectionTri>('asc');
  const [taille, setTaille] = useState(10);
  const [page, setPage] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [accesRefuse, setAccesRefuse] = useState(false);
  const [recharger, setRecharger] = useState(0);
  const [creationVisible, setCreationVisible] = useState(false);
  const [selection, setSelection] = useState<UtilisateurAdministration | null>(null);

  const charger = useCallback(() => {
    setRecharger((courant) => courant + 1);
  }, []);

  useEffect(() => {
    const controleur = new AbortController();
    setChargement(true);
    setErreur('');
    setAccesRefuse(false);
    utilisateurApi.rechercher({
      recherche: recherche || undefined,
      actif: actif === '' ? undefined : actif === 'true',
      page,
      taille,
      tri,
      direction,
    }, controleur.signal)
      .then(setResultat)
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        if (error instanceof ApiError && error.status === 403) setAccesRefuse(true);
        else setErreur(messageErreur(error));
      })
      .finally(() => { if (!controleur.signal.aborted) setChargement(false); });
    return () => controleur.abort();
  }, [actif, direction, page, recherche, recharger, taille, tri]);

  const rechercher = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setRecherche(rechercheSaisie.trim());
  };

  const actualiserUtilisateur = (modifie: UtilisateurAdministration) => {
    setResultat((courant) => ({
      ...courant,
      contenu: courant.contenu.map((item) => item.id === modifie.id ? modifie : item),
    }));
    setSelection(modifie);
    if (actif !== '' && modifie.actif !== (actif === 'true')) charger();
  };

  const utilisateurCree = (cree: UtilisateurAdministration) => {
    setCreationVisible(false);
    setSelection(cree);
    charger();
  };

  if (accesRefuse) return <section className="section-page"><ContentState type="error" title="Accès interdit" message="Vous n’avez pas l’autorisation de consulter les utilisateurs." /></section>;

  return <section className="section-page users-page">
    <header className="section-heading">
      <div><p className="eyebrow">Administration</p><h1>Utilisateurs</h1><p>Gérez les comptes, leur état et leurs rôles.</p></div>
      {droits.peutCreer && <button className="button button-primary heading-action" type="button" onClick={() => setCreationVisible((visible) => !visible)}>{creationVisible ? 'Fermer la création' : 'Créer un utilisateur'}</button>}
    </header>

    {creationVisible && droits.peutCreer && <CreerUtilisateurForm onCancel={() => setCreationVisible(false)} onCreated={utilisateurCree} />}

    <section className="admin-card" aria-labelledby="liste-utilisateurs-title">
      <div className="card-heading"><div><p className="eyebrow">Comptes</p><h2 id="liste-utilisateurs-title">Liste des utilisateurs</h2></div><span className="result-count">{resultat.totalElements} résultat{resultat.totalElements > 1 ? 's' : ''}</span></div>
      <form className="list-toolbar" onSubmit={rechercher}>
        <label className="search-field">Rechercher
          <span><input type="search" value={rechercheSaisie} onChange={(event) => setRechercheSaisie(event.target.value)} placeholder="E-mail, nom ou matricule" /><button className="button button-secondary" type="submit">Rechercher</button></span>
        </label>
        <label>État
          <select value={actif} onChange={(event) => { setActif(event.target.value as '' | 'true' | 'false'); setPage(0); }}>
            <option value="">Tous</option><option value="true">Actifs</option><option value="false">Inactifs</option>
          </select>
        </label>
        <label>Trier par
          <select value={tri} onChange={(event) => { setTri(event.target.value as ChampTriUtilisateur); setPage(0); }}>
            <option value="email">E-mail</option><option value="nomEmploye">Nom de l’employé</option><option value="creeLe">Date de création</option><option value="modifieLe">Dernière modification</option>
          </select>
        </label>
        <label>Ordre
          <select value={direction} onChange={(event) => { setDirection(event.target.value as DirectionTri); setPage(0); }}><option value="asc">Croissant</option><option value="desc">Décroissant</option></select>
        </label>
        <label>Par page
          <select value={taille} onChange={(event) => { setTaille(Number(event.target.value)); setPage(0); }}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select>
        </label>
      </form>

      {chargement ? <ContentState type="loading" title="Chargement des utilisateurs" message="Chargement de la liste en cours." />
        : erreur ? <ContentState type="error" title="Liste indisponible" message={erreur} retry={charger} />
          : resultat.contenu.length === 0 ? <ContentState type="empty" title="Aucun utilisateur" message="Aucun compte ne correspond aux critères sélectionnés." />
            : <div className="table-scroll"><table className="data-table">
              <thead><tr><th>Employé</th><th>E-mail</th><th>Rôles</th><th>État</th><th><span className="visually-hidden">Action</span></th></tr></thead>
              <tbody>{resultat.contenu.map((item) => <tr key={item.id}>
                <td><strong>{item.employe.prenom} {item.employe.nom}</strong><small>{item.employe.matricule}</small></td>
                <td>{item.email}</td>
                <td><div className="table-roles">{item.roles.map((role) => <span key={role}>{LIBELLES_ROLES[role]}</span>)}</div></td>
                <td><span className={`status-pill ${item.actif ? 'status-active' : 'status-inactive'}`}>{item.actif ? 'Actif' : 'Inactif'}</span></td>
                <td><button className="button button-quiet" type="button" onClick={() => setSelection(item)}>Consulter</button></td>
              </tr>)}</tbody>
            </table></div>}

      {!chargement && !erreur && resultat.totalPages > 0 && <nav className="pagination" aria-label="Pagination des utilisateurs">
        <button className="button button-secondary" type="button" disabled={page === 0} onClick={() => setPage((courante) => Math.max(0, courante - 1))}>Précédent</button>
        <span>Page {resultat.page + 1} sur {resultat.totalPages}</span>
        <button className="button button-secondary" type="button" disabled={page + 1 >= resultat.totalPages} onClick={() => setPage((courante) => courante + 1)}>Suivant</button>
      </nav>}
    </section>

    {selection && <UtilisateurDetailPanel utilisateur={selection} droits={droits} onClose={() => setSelection(null)} onUpdated={actualiserUtilisateur} />}
  </section>;
}
