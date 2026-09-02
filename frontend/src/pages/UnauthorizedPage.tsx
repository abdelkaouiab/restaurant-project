import { Link } from '../routes/Router';
export function UnauthorizedPage(){return <main className="status-page"><h1>Connexion requise</h1><p>Veuillez vous connecter pour continuer.</p><Link className="button button-primary" to="/connexion">Se connecter</Link></main>;}
