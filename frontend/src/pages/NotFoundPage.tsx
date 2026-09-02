import { Link } from '../routes/Router';
export function NotFoundPage(){return <main className="status-page"><h1>Page introuvable</h1><p>L’adresse demandée n’existe pas.</p><Link className="button button-primary" to="/">Retour à l’accueil</Link></main>;}
