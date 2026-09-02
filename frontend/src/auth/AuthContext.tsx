import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/authApi';
import { definirGestionnaireExpirationSession } from '../api/client';
import type { IdentifiantsConnexion, UtilisateurAuthentifie } from '../types/auth';

interface AuthContextValue {
  utilisateur: UtilisateurAuthentifie | null;
  chargement: boolean;
  sessionExpiree: boolean;
  connexion: (identifiants: IdentifiantsConnexion) => Promise<void>;
  deconnexion: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [utilisateur, setUtilisateur] = useState<UtilisateurAuthentifie | null>(null);
  const [chargement, setChargement] = useState(true);
  const [sessionExpiree, setSessionExpiree] = useState(false);

  useEffect(() => {
    definirGestionnaireExpirationSession(() => {
      setUtilisateur(null);
      setSessionExpiree(true);
      setChargement(false);
    });
    return () => definirGestionnaireExpirationSession(null);
  }, []);

  useEffect(() => {
    let actif = true;
    authApi.utilisateurCourant()
      .then((courant) => { if (actif) setUtilisateur(courant); })
      .catch(() => { if (actif) setUtilisateur(null); })
      .finally(() => { if (actif) setChargement(false); });
    return () => { actif = false; };
  }, []);

  const connexion = useCallback(async (identifiants: IdentifiantsConnexion) => {
    setUtilisateur(await authApi.connexion(identifiants));
    setSessionExpiree(false);
  }, []);

  const deconnexion = useCallback(async () => {
    try { await authApi.deconnexion(); } finally {
      setUtilisateur(null);
      setSessionExpiree(false);
    }
  }, []);

  const value = useMemo(
    () => ({ utilisateur, chargement, sessionExpiree, connexion, deconnexion }),
    [utilisateur, chargement, sessionExpiree, connexion, deconnexion],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const contexte = useContext(AuthContext);
  if (!contexte) throw new Error('useAuth doit être utilisé sous AuthProvider.');
  return contexte;
}
