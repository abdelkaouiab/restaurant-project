import type { UtilisateurAuthentifie } from '../types/auth';

export function possedePermission(utilisateur: UtilisateurAuthentifie | null, permission: string): boolean {
  return utilisateur?.permissions.includes(permission) ?? false;
}

export function possedeRole(utilisateur: UtilisateurAuthentifie | null, role: string): boolean {
  return utilisateur?.roles.includes(role.replace(/^ROLE_/, '')) ?? false;
}
