export interface ApiErrorBody {
  status?: number;
  code?: string;
  message?: string;
  path?: string;
  validationErrors?: Record<string, string>;
}

export interface PageResponse<T> {
  contenu: T[];
  page: number;
  taille: number;
  totalElements: number;
  totalPages: number;
}
