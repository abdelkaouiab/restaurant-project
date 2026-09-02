import type { UtilisateurAuthentifie } from '../../types/auth';

export const PERMISSIONS_ADMINISTRATION_UTILISATEURS = {
  consulter: 'UTILISATEUR_CONSULTER',
  gerer: 'UTILISATEUR_GERER',
  consulterRoles: 'ROLE_CONSULTER',
  gererRoles: 'ROLE_GERER',
} as const;

export interface DroitsAdministrationUtilisateurs {
  peutConsulter: boolean;
  peutModifier: boolean;
  peutModifierRoles: boolean;
  peutCreer: boolean;
  peutConsulterRoles: boolean;
}

export function droitsAdministrationUtilisateurs(
  utilisateur: UtilisateurAuthentifie | null,
): DroitsAdministrationUtilisateurs {
  const permissions = new Set(utilisateur?.permissions ?? []);
  const peutModifier = permissions.has(PERMISSIONS_ADMINISTRATION_UTILISATEURS.gerer);
  const peutModifierRoles = permissions.has(PERMISSIONS_ADMINISTRATION_UTILISATEURS.gererRoles);
  return {
    peutConsulter: permissions.has(PERMISSIONS_ADMINISTRATION_UTILISATEURS.consulter),
    peutModifier,
    peutModifierRoles,
    peutCreer: peutModifier && peutModifierRoles,
    peutConsulterRoles: permissions.has(PERMISSIONS_ADMINISTRATION_UTILISATEURS.consulterRoles),
  };
}

