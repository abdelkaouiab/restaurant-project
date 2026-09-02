import type { ReactNode } from 'react';
import { Link, useRouter } from '../routes/Router';
import { useAuth } from '../auth/AuthContext';
import { ROUTES_SECTIONS, utilisateurAutorisePourRoute } from '../routes/routeConfig';
import { LIBELLES_ROLES, type NomRole } from '../features/utilisateurs/types';

function libelleRole(role: string): string {
  return LIBELLES_ROLES[role as NomRole] ?? role.toLocaleLowerCase('fr-FR').replaceAll('_', ' ');
}

export function ProtectedLayout({children}:{children:ReactNode}) {
  const { utilisateur, deconnexion } = useAuth();
  const { chemin, naviguer } = useRouter();
  const routesVisibles = ROUTES_SECTIONS.filter((route) => utilisateurAutorisePourRoute(utilisateur, route));
  const groupes = [...new Set(routesVisibles.map((route) => route.groupe))];
  const nomRestaurant = import.meta.env.VITE_RESTAURANT_NAME?.trim() || 'Le Restaurant';
  const quitter = async () => {
    try { await deconnexion(); } finally { naviguer('/connexion', true); }
  };
  return <div className="app-shell">
    <aside className="sidebar" aria-label="Navigation principale">
      <Link className="brand" to="/"><span>R</span><strong>{nomRestaurant}</strong></Link>
      <div className="identity-card">
        <span className="avatar" aria-hidden="true">{utilisateur?.employe.prenom.charAt(0)}{utilisateur?.employe.nom.charAt(0)}</span>
        <div>
          <strong>{utilisateur?.employe.prenom} {utilisateur?.employe.nom}</strong>
          <small>{utilisateur?.roles.map(libelleRole).join(' · ')}</small>
        </div>
      </div>
      <nav className="sidebar-nav" aria-label="Navigation">
        {groupes.map((groupe) => <div className="nav-group" key={groupe}>
          <p>{groupe}</p>
          {routesVisibles.filter((route) => route.groupe === groupe).map((route) => <Link
            className={`nav-link${chemin === route.path ? ' nav-link-active' : ''}`}
            key={route.id}
            to={route.path}
          >
            <span aria-hidden="true">{route.repere}</span>{route.label}
          </Link>)}
        </div>)}
      </nav>
      <button className="logout-button" type="button" onClick={quitter}>Se déconnecter</button>
    </aside>
    <main className="protected-content">{children}</main>
  </div>;
}
