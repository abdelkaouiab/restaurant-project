interface ContentStateProps {
  type: 'loading' | 'empty' | 'error';
  title: string;
  message: string;
  retry?: () => void;
}

export function ContentState({ type, title, message, retry }: ContentStateProps) {
  return <div className={`content-state content-state-${type}`} role={type === 'error' ? 'alert' : 'status'}>
    {type === 'loading' ? <div className="spinner" aria-hidden="true" /> : <span className="content-state-mark" aria-hidden="true">{type === 'error' ? '!' : '—'}</span>}
    <div>
      <h2>{title}</h2>
      <p>{message}</p>
      {retry && <button className="button button-secondary" type="button" onClick={retry}>Réessayer</button>}
    </div>
  </div>;
}

