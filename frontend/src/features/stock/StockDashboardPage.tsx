import { useAuth } from '../../auth/AuthContext';
import { Link } from '../../routes/Router';

const RACCOURCIS = [
  { path: '/stock', label: 'Ingrédients et stock', description: 'Consulter les soldes, alertes, recettes et enregistrer les mouvements.', permission: 'STOCK_CONSULTER', repere: 'ST' },
  { path: '/fournisseurs', label: 'Fournisseurs', description: 'Rechercher et gérer les fournisseurs.', permission: 'ACHAT_CONSULTER', repere: 'FO' },
  { path: '/achats', label: 'Achats et réceptions', description: 'Préparer, commander et réceptionner les achats.', permission: 'ACHAT_CONSULTER', repere: 'AC' },
] as const;

export function StockDashboardPage() {
  const { utilisateur } = useAuth();
  const raccourcis = RACCOURCIS.filter((item) => utilisateur?.permissions.includes(item.permission));
  return <section className="section-page server-dashboard stock-dashboard">
    <header className="section-heading"><div><p className="eyebrow">Espace stock</p><h1>Bonjour {utilisateur?.employe.prenom}</h1><p>Surveillez les disponibilités et gérez les approvisionnements.</p></div><span className="section-badge">RESPONSABLE STOCK</span></header>
    <div className="shortcut-grid stock-shortcuts" aria-label="Raccourcis stock">
      {raccourcis.map((item) => <Link className="shortcut-card" to={item.path} key={item.path}><span aria-hidden="true">{item.repere}</span><div><h2>{item.label}</h2><p>{item.description}</p></div><strong aria-hidden="true">→</strong></Link>)}
    </div>
  </section>;
}
