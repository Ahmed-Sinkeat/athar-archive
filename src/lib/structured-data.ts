// JSON-LD builders per entity type (URL Map §06). Each returns a schema.org
// @graph object ready to JSON.stringify into a <script type="application/ld+json">.

import { config } from "../../ahlalathar.config";

const ORIGIN = config.siteUrl;
export const absUrl = (path: string) => new URL(path, ORIGIN).href;

interface Crumb {
  name: string;
  path: string;
}

export function breadcrumbList(items: Crumb[]) {
  return {
    "@type": "BreadcrumbList",
    itemListElement: items.map((it, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: it.name,
      item: absUrl(it.path),
    })),
  };
}

function graph(...nodes: unknown[]) {
  return { "@context": "https://schema.org", "@graph": nodes.filter(Boolean) };
}

const personRef = (id?: string, name?: string) =>
  id ? { "@type": "Person", "@id": absUrl(`/person/${id}#person`), name } : undefined;

export function personLd(o: { id: string; name: string; bio?: string; born?: string; died?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "ProfilePage",
      "@id": absUrl(o.path),
      url: absUrl(o.path),
      mainEntity: {
        "@type": "Person",
        "@id": absUrl(`${o.path}#person`),
        name: o.name,
        ...(o.bio ? { description: o.bio } : {}),
        ...(o.born ? { birthDate: o.born } : {}),
        ...(o.died ? { deathDate: o.died } : {}),
      },
    },
    breadcrumbList(o.crumbs),
  );
}

export function bookLd(o: { title: string; authorId?: string; authorName?: string; description?: string; audioUrl?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "Book",
      "@id": absUrl(o.path),
      name: o.title,
      url: absUrl(o.path),
      inLanguage: "ar",
      ...(o.description ? { description: o.description } : {}),
      ...(o.authorName ? { author: personRef(o.authorId, o.authorName) } : {}),
      ...(o.audioUrl ? { audio: { "@type": "AudioObject", contentUrl: o.audioUrl } } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}

export function poemLd(o: { title: string; authorId?: string; authorName?: string; description?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": ["CreativeWork", "Poem"],
      "@id": absUrl(o.path),
      name: o.title,
      url: absUrl(o.path),
      inLanguage: "ar",
      ...(o.description ? { description: o.description } : {}),
      ...(o.authorName ? { author: personRef(o.authorId, o.authorName) } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}

export function seriesLd(o: { title: string; authorName?: string; description?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "Course",
      "@id": absUrl(o.path),
      name: o.title,
      url: absUrl(o.path),
      inLanguage: "ar",
      ...(o.description ? { description: o.description } : {}),
      ...(o.authorName ? { provider: { "@type": "Person", name: o.authorName } } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}

export function lessonLd(o: { title: string; seriesTitle: string; seriesPath: string; authorName?: string; audioUrl?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "LearningResource",
      "@id": absUrl(o.path),
      name: o.title,
      url: absUrl(o.path),
      inLanguage: "ar",
      isPartOf: { "@type": "Course", name: o.seriesTitle, url: absUrl(o.seriesPath) },
      ...(o.authorName ? { author: { "@type": "Person", name: o.authorName } } : {}),
      ...(o.audioUrl ? { audio: { "@type": "AudioObject", contentUrl: o.audioUrl } } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}

export function benefitLd(o: { text: string; authorName?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "Quotation",
      "@id": absUrl(o.path),
      text: o.text,
      inLanguage: "ar",
      ...(o.authorName ? { spokenByCharacter: o.authorName } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}

export function articleLd(o: { title: string; authorId?: string; authorName?: string; description?: string; audioUrl?: string; datePublished?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "Article",
      "@id": absUrl(o.path),
      headline: o.title,
      url: absUrl(o.path),
      inLanguage: "ar",
      ...(o.description ? { description: o.description } : {}),
      ...(o.datePublished ? { datePublished: o.datePublished } : {}),
      ...(o.authorName ? { author: personRef(o.authorId, o.authorName) } : {}),
      ...(o.audioUrl ? { audio: { "@type": "AudioObject", contentUrl: o.audioUrl } } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}

export function questionLd(o: { title: string; answerText: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "QAPage",
      "@id": absUrl(o.path),
      mainEntity: {
        "@type": "Question",
        name: o.title,
        acceptedAnswer: { "@type": "Answer", text: o.answerText },
      },
    },
    breadcrumbList(o.crumbs),
  );
}

export function websiteLd() {
  return graph(
    {
      "@type": "WebSite",
      url: ORIGIN,
      name: "أهل الأثر",
      inLanguage: "ar",
      publisher: { "@id": absUrl("/#organization") },
      potentialAction: {
        "@type": "SearchAction",
        target: { "@type": "EntryPoint", urlTemplate: absUrl("/search?q={query}") },
        "query-input": "required name=query",
      },
    },
    {
      "@type": "Organization",
      "@id": absUrl("/#organization"),
      url: ORIGIN,
      name: "أهل الأثر",
      logo: absUrl("/favicon.svg"),
      description: "الأرشيف العلمي للمتون والمنظومات الإسلامية",
    }
  );
}

export function collectionLd(o: { title: string; description?: string; path: string; crumbs: Crumb[] }) {
  return graph(
    {
      "@type": "CollectionPage",
      "@id": absUrl(o.path),
      name: o.title,
      url: absUrl(o.path),
      inLanguage: "ar",
      ...(o.description ? { description: o.description } : {}),
    },
    breadcrumbList(o.crumbs),
  );
}
