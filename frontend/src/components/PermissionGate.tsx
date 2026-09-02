import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import { possedePermission } from '../utils/permissions';

interface PermissionGateProps {
  permission: string;
  children: ReactNode;
  fallback?: ReactNode;
}

export function PermissionGate({ permission, children, fallback = null }: PermissionGateProps) {
  const { utilisateur } = useAuth();
  return possedePermission(utilisateur, permission) ? children : fallback;
}
