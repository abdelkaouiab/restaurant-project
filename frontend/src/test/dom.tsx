import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach } from 'vitest';

type Correspondance = string | RegExp;
interface OptionsNom { name?: Correspondance; }
interface OptionsLabel { selector?: string; }

const racines: Root[] = [];
(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean })
  .IS_REACT_ACT_ENVIRONMENT = true;

function correspond(valeur: string, attendu?: Correspondance): boolean {
  if (attendu === undefined) return true;
  const normalisee = valeur.replace(/\s+/g, ' ').trim();
  return typeof attendu === 'string' ? normalisee === attendu : attendu.test(normalisee);
}

function texteAccessible(element: Element): string {
  const etiquette = element.getAttribute('aria-label');
  if (etiquette) return etiquette;
  if (element instanceof HTMLImageElement) return element.alt;
  const reference = element.getAttribute('aria-labelledby');
  if (reference) {
    return reference.split(/\s+/).map((id) => document.getElementById(id)?.textContent ?? '').join(' ');
  }
  const parcourir = (noeud: Node): string => {
    if (noeud.nodeType === Node.TEXT_NODE) return noeud.textContent ?? '';
    if (!(noeud instanceof Element) || noeud.getAttribute('aria-hidden') === 'true') return '';
    return Array.from(noeud.childNodes).map(parcourir).join(' ');
  };
  return parcourir(element);
}

function texteEtiquette(label: Element): string {
  const parcourir = (noeud: Node): string => {
    if (noeud.nodeType === Node.TEXT_NODE) return noeud.textContent ?? '';
    if (!(noeud instanceof Element) || noeud.getAttribute('aria-hidden') === 'true') return '';
    if (noeud.matches('input,select,textarea,button')) return '';
    return Array.from(noeud.childNodes).map(parcourir).join(' ');
  };
  return parcourir(label);
}

function elementsPourRole(conteneur: ParentNode, role: string): Element[] {
  const selecteurs: Record<string, string> = {
    alertdialog: '[role="alertdialog"]',
    button: 'button,input[type="button"],input[type="submit"],[role="button"]',
    heading: 'h1,h2,h3,h4,h5,h6,[role="heading"]',
    img: 'img,[role="img"]',
    link: 'a[href],[role="link"]',
    navigation: 'nav,[role="navigation"]',
  };
  const selecteur = selecteurs[role] ?? `[role="${role}"]`;
  return Array.from(conteneur.querySelectorAll(selecteur));
}

function creerRequetes(conteneur: ParentNode) {
  const queryByRole = (role: string, options: OptionsNom = {}): HTMLElement | null =>
    elementsPourRole(conteneur, role).find((element) => correspond(texteAccessible(element), options.name)) as HTMLElement | undefined ?? null;

  const getByRole = (role: string, options: OptionsNom = {}): HTMLElement => {
    const resultat = queryByRole(role, options);
    if (!resultat) throw new Error(`Élément role=${role} introuvable.`);
    return resultat;
  };

  const candidatsTexte = (): HTMLElement[] => Array.from(conteneur.querySelectorAll('*')) as HTMLElement[];
  const queryByText = (texte: Correspondance): HTMLElement | null => {
    const candidats = candidatsTexte().filter((element) => correspond(element.textContent ?? '', texte));
    return candidats.find((element) => !Array.from(element.children).some((enfant) => correspond(enfant.textContent ?? '', texte))) ?? null;
  };
  const getByText = (texte: Correspondance): HTMLElement => {
    const resultat = queryByText(texte);
    if (!resultat) throw new Error(`Texte ${String(texte)} introuvable.`);
    return resultat;
  };

  const queryByLabelText = (texte: Correspondance, options: OptionsLabel = {}): HTMLElement | null => {
    const labels = Array.from(conteneur.querySelectorAll('label'));
    for (const label of labels) {
      if (!correspond(texteEtiquette(label), texte)) continue;
      const identifiant = label.getAttribute('for');
      const controle = identifiant
        ? document.getElementById(identifiant)
        : label.querySelector('input,select,textarea,button');
      if (controle instanceof HTMLElement && (!options.selector || controle.matches(options.selector))) return controle;
    }
    return null;
  };
  const getByLabelText = (texte: Correspondance, options: OptionsLabel = {}): HTMLElement => {
    const resultat = queryByLabelText(texte, options);
    if (!resultat) throw new Error(`Champ ${String(texte)} introuvable.`);
    return resultat;
  };

  const queryByDisplayValue = (valeur: string): HTMLElement | null =>
    Array.from(conteneur.querySelectorAll('input,select,textarea'))
      .find((element) => (element as HTMLInputElement).value === valeur) as HTMLElement | undefined ?? null;

  return {
    getByRole,
    queryByRole,
    getByText,
    queryByText,
    getByLabelText,
    queryByLabelText,
    queryByDisplayValue,
    findByRole: (role: string, options: OptionsNom = {}) => waitFor(() => getByRole(role, options)),
    findByText: (texte: Correspondance) => waitFor(() => getByText(texte)),
  };
}

export const screen = creerRequetes(document);
export const within = (conteneur: HTMLElement) => creerRequetes(conteneur);

export function render(contenu: ReactNode) {
  const conteneur = document.createElement('div');
  document.body.append(conteneur);
  const racine = createRoot(conteneur);
  racines.push(racine);
  act(() => racine.render(contenu));
  return {
    unmount: () => {
      const position = racines.indexOf(racine);
      if (position >= 0) racines.splice(position, 1);
      act(() => racine.unmount());
      conteneur.remove();
    },
  };
}

export async function waitFor<T>(verification: () => T, delai = 2_000): Promise<T> {
  const limite = Date.now() + delai;
  let derniereErreur: unknown;
  while (Date.now() < limite) {
    try { return verification(); } catch (error: unknown) { derniereErreur = error; }
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 10)); });
  }
  throw derniereErreur ?? new Error('Condition non satisfaite.');
}

async function actualiserReact() {
  await act(async () => { await Promise.resolve(); });
}

export const userEvent = {
  setup: () => ({
    click: async (element: HTMLElement) => {
      await act(async () => { element.click(); });
      await actualiserReact();
    },
    type: async (element: HTMLElement, texte: string) => {
      if (!(element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement)) {
        throw new Error('La saisie nécessite un champ texte.');
      }
      await act(async () => {
        const prototype = element instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
        const definir = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
        definir?.call(element, `${element.value}${texte}`);
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
      });
      await actualiserReact();
    },
    clear: async (element: HTMLElement) => {
      if (!(element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement)) {
        throw new Error('L’effacement nécessite un champ texte.');
      }
      await act(async () => {
        const prototype = element instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
        const definir = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
        definir?.call(element, '');
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
      });
      await actualiserReact();
    },
    selectOptions: async (element: HTMLElement, valeur: string) => {
      if (!(element instanceof HTMLSelectElement)) throw new Error('La sélection nécessite une liste.');
      await act(async () => {
        element.value = valeur;
        element.dispatchEvent(new Event('change', { bubbles: true }));
      });
      await actualiserReact();
    },
    upload: async (element: HTMLElement, fichier: File) => {
      if (!(element instanceof HTMLInputElement) || element.type !== 'file') {
        throw new Error('Le téléversement nécessite un champ fichier.');
      }
      await act(async () => {
        Object.defineProperty(element, 'files', { configurable: true, value: [fichier] });
        element.dispatchEvent(new Event('change', { bubbles: true }));
      });
      await actualiserReact();
    },
  }),
};

afterEach(() => {
  act(() => {
    for (const racine of racines.splice(0)) racine.unmount();
  });
  document.body.replaceChildren();
});

export { act };
