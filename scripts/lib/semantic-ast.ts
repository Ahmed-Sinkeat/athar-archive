export type NodeType =
  | "Book"
  | "Metadata"
  | "Volume"
  | "Chapter"
  | "Section"
  | "Heading"
  | "Paragraph"
  | "Quote"
  | "QuranVerse"
  | "Hadith"
  | "Footnote"
  | "Table"
  | "List"
  | "Poetry"
  | "Image"
  | "PageBreak"
  | "BookReference"
  | "ScholarMention"
  | "PlaceMention"
  | "SectMention"
  | "Topic"
  | "Publisher"
  | "Edition";

export interface SemanticNode {
  type: NodeType;
  content?: string;             // Raw text or specific node value (e.g., text, reference ID)
  attributes?: Record<string, any>; // Arbitrary semantic attributes (e.g. { level: 1 } for Heading, or { number: 5 } for Volume)
  children: SemanticNode[];
}

export interface BookMetadata {
  title?: string;
  author?: string;
  editor?: string;       // Muhaqqiq
  publisher?: string;
  publicationYear?: string;
  edition?: string;
  volumes?: number;
  language?: string;
  category?: string;
  topics?: string[];
  [key: string]: any;
}

export interface SemanticBook {
  metadata: BookMetadata;
  ast: SemanticNode;
  statistics?: Record<string, any>;
}

export function createNode(type: NodeType, content?: string, attributes?: Record<string, any>, children: SemanticNode[] = []): SemanticNode {
  return {
    type,
    content,
    attributes,
    children
  };
}

export function traverseAST(node: SemanticNode, callback: (node: SemanticNode, parent?: SemanticNode) => void, parent?: SemanticNode): void {
  callback(node, parent);
  for (const child of node.children) {
    traverseAST(child, callback, node);
  }
}
