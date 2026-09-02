import { useState, type FormEvent } from 'react';
import { Navigate, useRouter } from '../routes/Router';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { utilisateur, chargement, connexion } = useAuth();
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreur, setErreur] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const { naviguer } = useRouter();

  if (!chargement && utilisateur) return <Navigate to="/" />;

  const soumettre = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setErreur(''); setEnvoi(true);
    try {
      await connexion({ email, motDePasse });
      setMotDePasse('');
      naviguer('/', true);
    } catch {
      setMotDePasse('');
      setErreur('Connexion impossible. Vérifiez vos informations puis réessayez.');
    } finally { setEnvoi(false); }
  };

  return <main className="login-page">
    <section className="login-panel" aria-labelledby="login-title">
      <div className="login-intro"><p className="eyebrow">Espace équipe</p><h1 id="login-title">Bienvenue</h1><p>Connectez-vous pour accéder à l’espace sécurisé du restaurant.</p></div>
      <form onSubmit={soumettre} className="login-form">
        <label htmlFor="email">Adresse e-mail</label>
        <input id="email" name="email" type="email" autoComplete="username" required maxLength={254} value={email} onChange={(e)=>setEmail(e.target.value)} />
        <label htmlFor="motDePasse">Mot de passe</label>
        <input id="motDePasse" name="motDePasse" type="password" autoComplete="current-password" required maxLength={128} value={motDePasse} onChange={(e)=>setMotDePasse(e.target.value)} />
        {erreur && <p className="form-error" role="alert">{erreur}</p>}
        <button className="button button-primary" type="submit" disabled={envoi}>{envoi?'Connexion…':'Se connecter'}</button>
      </form>
    </section>
  </main>;
}
