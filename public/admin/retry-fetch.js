// GitHub's GraphQL API occasionally 502/504s under load even when nothing is
// wrong on our end (see: intermittent errors loading Sveltia's site data).
// Retry those, but only for read-only "query" operations — a 502/504 on a
// mutation (e.g. committing an entry) doesn't prove the write didn't land,
// so retrying it could double-commit.
(function () {
  const originalFetch = window.fetch.bind(window);
  window.fetch = async function (input, init) {
    const url = typeof input === "string" ? input : input.url;
    const method = (init && init.method) || (input instanceof Request ? input.method : "GET");
    const body = (init && init.body) || undefined;
    if (url !== "https://api.github.com/graphql" || method.toUpperCase() !== "POST" || typeof body !== "string") {
      return originalFetch(input, init);
    }
    let isQuery = false;
    try {
      isQuery = JSON.parse(body).query?.trim().startsWith("query");
    } catch {}
    const attempts = isQuery ? 4 : 1;
    let res;
    for (let i = 0; i < attempts; i++) {
      res = await originalFetch(input, init);
      if (res.status < 500 || i === attempts - 1) return res;
      await new Promise((r) => setTimeout(r, 400 * 2 ** i));
    }
    return res;
  };
})();
