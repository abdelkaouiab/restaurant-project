import { Link } from '../routes/Router';
export function ForbiddenPage(){return <main className="status-page"><h1>Accès refusé</h1><p>Vous n’avez pas l’autorisation d’accéder à cette page.</p><Link className="button button-primary" to="/">Retour à l’accueil</Link></main>;}
