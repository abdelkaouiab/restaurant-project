import type { ReactNode } from 'react';
import { Navigate } from '../routes/Router';
import { useAuth } from './AuthContext';
import { LoadingScreen } from '../components/LoadingScreen';

export function RequireAuth({children}:{children:ReactNode}) {
  const { utilisateur, chargement } = useAuth();
  if (chargement) return <LoadingScreen />;
  if (!utilisateur) return <Navigate to="/connexion" />;
  return children;
}
