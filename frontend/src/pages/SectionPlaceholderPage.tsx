import { ContentState } from '../components/ContentState';
import type { RouteSection } from '../routes/routeConfig';

export function SectionPlaceholderPage({ route }: { route: RouteSection }) {
  return <section className="section-page" aria-labelledby={`titre-${route.id}`}>
    <header className="section-heading">
      <div>
        <p className="eyebrow">Section</p>
        <h1 id={`titre-${route.id}`}>{route.label}</h1>
        <p>{route.description}</p>
      </div>
    </header>
    <ContentState
      type="empty"
      title="Section indisponible"
      message="Cette section n’est pas disponible pour le moment."
    />
  </section>;
}
