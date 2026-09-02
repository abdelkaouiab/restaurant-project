export interface EmployeAuthentifie {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
  poste: string;
}

export interface UtilisateurAuthentifie {
  id: number;
  email: string;
  employe: EmployeAuthentifie;
  roles: string[];
  permissions: string[];
}

export interface IdentifiantsConnexion {
  email: string;
  motDePasse: string;
}

export interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}
