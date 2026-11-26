export function consumeResetToken(location: Location, history: History): string {
  const url = new URL(location.href);
  const fragmentToken = new URLSearchParams(url.hash.slice(1)).get('token');
  const token = fragmentToken ?? url.searchParams.get('token') ?? '';
  if (token) {
    url.hash = '';
    url.searchParams.delete('token');
    history.replaceState(history.state, '', url.pathname + url.search);
  }
  return token;
}
