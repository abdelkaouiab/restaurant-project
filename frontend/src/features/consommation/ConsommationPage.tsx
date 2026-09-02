import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { ContentState } from '../../components/ContentState';
import { PeriodeForm, validerPeriode } from '../dashboard/PeriodeForm';
import { messageErreur } from '../utilisateurs/erreurs';
import { consommationApi } from './consommationApi';
import type { Consommation } from './types';

const LIBELLES_UNITES: Record<string, string> = {
  GRAMME: 'g',
  KILOGRAMME: 'kg',
  MILLILITRE: 'ml',
  LITRE: 'L',
  UNITE: 'unité',
};

function dateIso(decalage: number): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + decalage);
  return date.toISOString().slice(0, 10);
}

function formatQuantite(quantite: number): string {
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 3 }).format(quantite);
}

function enregistrerPdf(fichier: Blob, nom: string): void {
  const url = URL.createObjectURL(fichier);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nom;
  lien.click();
  URL.revokeObjectURL(url);
}

export function ConsommationPage() {
  const [debut, setDebut] = useState(() => dateIso(-29));
  const [fin, setFin] = useState(() => dateIso(0));
  const [periode, setPeriode] = useState({ debut, fin });
  const [consommation, setConsommation] = useState<Consommation | null>(null);
  const [chargement, setChargement] = useState(true);
  const [telechargement, setTelechargement] = useState(false);
  const [erreur, setErreur] = useState('');
  const [erreurPeriode, setErreurPeriode] = useState('');
  const [erreurPdf, setErreurPdf] = useState('');
  const controleur = useRef<AbortController | null>(null);

  const charger = useCallback(async () => {
    controleur.current?.abort();
    const suivant = new AbortController();
    controleur.current = suivant;
    setChargement(true);
    setErreur('');
    try {
      setConsommation(await consommationApi.consulter(periode.debut, periode.fin, suivant.signal));
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      setConsommation(null);
      setErreur(error instanceof ApiError && error.status === 403
        ? 'Vous n’avez pas l’autorisation de consulter les consommations.'
        : messageErreur(error));
    } finally {
      if (!suivant.signal.aborted) setChargement(false);
    }
  }, [periode]);

  useEffect(() => {
    void charger();
    return () => controleur.current?.abort();
  }, [charger]);

  const appliquer = () => {
    const validation = validerPeriode(debut, fin);
    setErreurPeriode(validation);
    setErreurPdf('');
    if (!validation) setPeriode({ debut, fin });
  };

  const telecharger = async () => {
    if (!consommation || telechargement) return;
    setTelechargement(true);
    setErreurPdf('');
    try {
      const fichier = await consommationApi.telechargerPdf(consommation.debut, consommation.fin);
      enregistrerPdf(
        fichier,
        `rapport-consommation-${consommation.debut}-au-${consommation.fin}.pdf`,
      );
    } catch (error: unknown) {
      setErreurPdf(error instanceof ApiError && error.status === 403
        ? 'Vous n’avez pas l’autorisation de télécharger ce rapport.'
        : messageErreur(error));
    } finally {
      setTelechargement(false);
    }
  };

  const aucuneDonnee = consommation
    && consommation.produits.length === 0
    && consommation.ingredients.length === 0;

  return <section className="dashboard-page consumption-page">
    <header className="section-heading">
      <div>
        <p className="eyebrow">Pilotage</p>
        <h1>Consommation</h1>
        <p>Consultez les produits consommés et les ingrédients utilisés pendant une période.</p>
      </div>
    </header>

    <PeriodeForm
      debut={debut}
      fin={fin}
      busy={chargement}
      onDebut={setDebut}
      onFin={setFin}
      onSubmit={appliquer}
    />
    {erreurPeriode && <p className="form-error operation-error" role="alert">{erreurPeriode}</p>}

    {chargement
      ? <ContentState type="loading" title="Chargement des consommations" message="Les consommations sont en cours de calcul." />
      : erreur
        ? <ContentState type="error" title="Consommations indisponibles" message={erreur} retry={() => void charger()} />
        : aucuneDonnee
          ? <ContentState type="empty" title="Aucune consommation" message="Aucune consommation enregistrée pour cette période." />
          : consommation && <div className="consumption-results">
            <article className="admin-card">
              <header className="card-heading"><h2>Produits consommés</h2></header>
              <div className="table-scroll">
                <table className="data-table">
                  <thead><tr><th>Produit</th><th>Quantité consommée</th></tr></thead>
                  <tbody>{consommation.produits.map((produit) => <tr key={produit.produitId}>
                    <td><strong>{produit.produit}</strong></td>
                    <td>{produit.quantite}</td>
                  </tr>)}</tbody>
                </table>
              </div>
            </article>

            <article className="admin-card">
              <header className="card-heading"><h2>Ingrédients utilisés</h2></header>
              <div className="table-scroll">
                <table className="data-table">
                  <thead><tr><th>Ingrédient</th><th>Quantité utilisée</th><th>Unité</th></tr></thead>
                  <tbody>{consommation.ingredients.map((ingredient) => <tr key={ingredient.ingredientId}>
                    <td><strong>{ingredient.ingredient}</strong></td>
                    <td>{formatQuantite(ingredient.quantite)}</td>
                    <td>{LIBELLES_UNITES[ingredient.unite] ?? ingredient.unite}</td>
                  </tr>)}</tbody>
                </table>
              </div>
            </article>
          </div>}

    {consommation && !chargement && !erreur && <div className="consumption-export">
      {erreurPdf && <p className="form-error operation-error" role="alert">{erreurPdf}</p>}
      <button className="button button-primary" type="button" disabled={telechargement} onClick={() => void telecharger()}>
        {telechargement ? 'Téléchargement…' : 'Télécharger le rapport PDF'}
      </button>
    </div>}
  </section>;
}
