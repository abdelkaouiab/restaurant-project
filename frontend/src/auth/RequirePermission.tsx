import type { ReactNode } from 'react';
import { Navigate } from '../routes/Router';
import { useAuth } from './AuthContext';
import { possedePermission } from '../utils/permissions';

export function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { utilisateur } = useAuth();
  return possedePermission(utilisateur, permission) ? children : <Navigate to="/403" />;
}
