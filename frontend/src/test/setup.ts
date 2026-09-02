import { GlobalRegistrator } from '@happy-dom/global-registrator';
import { afterAll, afterEach, expect } from 'vitest';

GlobalRegistrator.register({ url: 'http://localhost:5173' });

expect.extend({
  toBeInTheDocument(recu: unknown) {
    const pass = recu instanceof Node && document.documentElement.contains(recu);
    return {
      pass,
      message: () => pass
        ? 'L’élément est présent dans le document.'
        : 'L’élément attendu est absent du document.',
    };
  },
  toHaveFocus(recu: unknown) {
    const pass = recu instanceof HTMLElement && document.activeElement === recu;
    return {
      pass,
      message: () => pass
        ? 'L’élément possède le focus.'
        : 'L’élément attendu ne possède pas le focus.',
    };
  },
  toHaveValue(recu: unknown, attendu: unknown) {
    const estControle = recu instanceof HTMLInputElement
      || recu instanceof HTMLSelectElement
      || recu instanceof HTMLTextAreaElement;
    const valeur = estControle ? recu.value : undefined;
    const pass = estControle && Object.is(valeur, attendu);
    return {
      pass,
      message: () => pass
        ? `Le champ possède la valeur ${String(attendu)}.`
        : `Valeur attendue : ${String(attendu)} ; valeur reçue : ${String(valeur)}.`,
    };
  },
});

afterEach(() => {
  document.body.replaceChildren();
  localStorage.clear();
  sessionStorage.clear();
});
afterAll(() => GlobalRegistrator.unregister());
