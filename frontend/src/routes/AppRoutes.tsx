import { RequireAuth } from '../auth/RequireAuth';
import { useAuth } from '../auth/AuthContext';
import { ProtectedLayout } from '../layouts/ProtectedLayout';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { HomePage } from '../pages/HomePage';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { SectionPlaceholderPage } from '../pages/SectionPlaceholderPage';
import { UtilisateursPage } from '../features/utilisateurs/UtilisateursPage';
import { ReferentielsPage } from '../features/referentiels/ReferentielsPage';
import type { ModuleReferentielId } from '../features/referentiels/types';
import { CommandesPage } from '../features/service/CommandesPage';
import { ReservationsPage } from '../features/service/ReservationsPage';
import { CuisinePage } from '../features/cuisine/CuisinePage';
import { CaissePage } from '../features/caisse/CaissePage';
import { AchatsPage } from '../features/stock/AchatsPage';
import { FournisseursPage } from '../features/stock/FournisseursPage';
import { StockPage } from '../features/stock/StockPage';
import { JournalPage } from '../features/dashboard/JournalPage';
import { ReportsPage } from '../features/dashboard/ReportsPage';
import { RoleDashboardPage } from '../features/dashboard/RoleDashboardPage';
import { ConsommationPage } from '../features/consommation/ConsommationPage';
import { Navigate, useRouter } from './Router';
import { trouverRouteSection, utilisateurAutorisePourRoute, type RouteSection } from './routeConfig';

function RouteSectionProtegee({ route }: { route: RouteSection }) {
  const { utilisateur } = useAuth();
  if (!utilisateurAutorisePourRoute(utilisateur, route)) return <ForbiddenPage />;
  const referentiels: RouteSection['id'][] = ['employes', 'clients', 'tables', 'categories', 'produits'];
  let contenu = <SectionPlaceholderPage route={route} />;
  if (route.id === 'utilisateurs') contenu = <UtilisateursPage />;
  else if (referentiels.includes(route.id)) contenu = <ReferentielsPage key={route.id} module={route.id as ModuleReferentielId} />;
  else if (route.id === 'commandes') contenu = <CommandesPage />;
  else if (route.id === 'reservations') contenu = <ReservationsPage />;
  else if (route.id === 'cuisine') contenu = <CuisinePage />;
  else if (route.id === 'caisse') contenu = <CaissePage />;
  else if (route.id === 'stock') contenu = <StockPage />;
  else if (route.id === 'fournisseurs') contenu = <FournisseursPage />;
  else if (route.id === 'achats') contenu = <AchatsPage />;
  else if (route.id === 'rapports') contenu = <ReportsPage />;
  else if (route.id === 'consommation') contenu = <ConsommationPage />;
  else if (route.id === 'journal-activite') contenu = <JournalPage />;
  else if (route.id === 'tableau-de-bord') contenu = <RoleDashboardPage />;
  return <ProtectedLayout>{contenu}</ProtectedLayout>;
}

export function AppRoutes() {
  const { chemin } = useRouter();
  if (chemin === '/connexion') return <LoginPage />;
  if (chemin === '/401') return <Navigate to="/connexion" />;
  if (chemin === '/403') return <ForbiddenPage />;
  if (chemin === '/') return <RequireAuth><ProtectedLayout><HomePage /></ProtectedLayout></RequireAuth>;
  const route = trouverRouteSection(chemin);
  if (route) return <RequireAuth><RouteSectionProtegee route={route} /></RequireAuth>;
  return <NotFoundPage />;
}
