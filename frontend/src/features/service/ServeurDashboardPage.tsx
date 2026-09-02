import { useAuth } from '../../auth/AuthContext';
import { Link } from '../../routes/Router';

const RACCOURCIS = [
  { path: '/tables', label: 'Tables', description: 'Consulter les tables actives et leurs capacités.', permission: 'TABLE_CONSULTER', repere: 'TA' },
  { path: '/clients', label: 'Clients', description: 'Rechercher ou enregistrer un client.', permission: 'CLIENT_CONSULTER', repere: 'CL' },
  { path: '/reservations', label: 'Réservations', description: 'Créer une réservation et suivre son arrivée.', permission: 'RESERVATION_GERER', repere: 'RE' },
  { path: '/commandes', label: 'Commandes', description: 'Créer et faire évoluer vos commandes.', permission: 'COMMANDE_CONSULTER', repere: 'CO' },
] as const;

export function ServeurDashboardPage() {
  const { utilisateur } = useAuth();
  const raccourcis = RACCOURCIS.filter((item) => utilisateur?.permissions.includes(item.permission));
  return <section className="section-page server-dashboard">
    <header className="section-heading"><div><p className="eyebrow">Espace serveur</p><h1>Bonjour {utilisateur?.employe.prenom}</h1><p>Accédez rapidement aux opérations de salle.</p></div><span className="section-badge">SERVEUR</span></header>
    <div className="shortcut-grid" aria-label="Raccourcis serveur">
      {raccourcis.map((item) => <Link className="shortcut-card" to={item.path} key={item.path}><span aria-hidden="true">{item.repere}</span><div><h2>{item.label}</h2><p>{item.description}</p></div><strong aria-hidden="true">→</strong></Link>)}
    </div>
  </section>;
}
