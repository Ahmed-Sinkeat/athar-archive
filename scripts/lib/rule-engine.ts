import * as fs from "fs";
import * as path from "path";
import { parseYaml } from "./yaml";

export interface RuleDecision {
  ruleId: string;
  category: string;
  confidence: number;
  extractedValue: any;
  reason: string;
  origin?: any;
}

export class RuleEngine {
  private profileName: string;
  private rulesDir: string;
  private cache: Record<string, any> = {};

  constructor(profileName: string = "generic") {
    this.profileName = profileName;
    this.rulesDir = path.join(process.cwd(), "rules", "profiles");
  }

  private loadRulesFile(category: string): any {
    if (this.cache[category]) return this.cache[category];
    
    let filePath = path.join(this.rulesDir, this.profileName, `${category}.yaml`);
    if (!fs.existsSync(filePath)) {
      // fallback to generic
      filePath = path.join(this.rulesDir, "generic", `${category}.yaml`);
    }

    if (!fs.existsSync(filePath)) {
      return { rules: [] };
    }

    try {
      const content = fs.readFileSync(filePath, "utf-8");
      const parsed = parseYaml(content);
      this.cache[category] = parsed;
      return parsed;
    } catch (e) {
      console.error(`Failed to parse rules file for category ${category}:`, e);
      return { rules: [] };
    }
  }

  // Match metadata rule definitions
  matchMetadata(paragraphs: string[], fieldId: string, book: any): string | undefined {
    const data = this.loadRulesFile("metadata");
    const rules = data.rules || [];
    const rule = rules.find((r: any) => r.id === fieldId && r.enabled !== false);
    if (!rule) return undefined;

    const patterns = rule.patterns || [];
    for (const p of paragraphs.slice(0, 15)) {
      for (const pat of patterns) {
        const regex = new RegExp(`${pat}\\s*:\\s*([^\\n]+)`, "i");
        const m = p.match(regex);
        if (m && m[1]) {
          const val = m[1].trim();
          
          book.ruleDecisions = book.ruleDecisions || [];
          book.ruleDecisions.push({
            ruleId: rule.id,
            category: "metadata",
            confidence: rule.confidence || 0.90,
            extractedValue: val,
            reason: `Matched pattern "${pat}" on text: "${p.slice(0, 50)}..."`
          });

          return val;
        }
      }
    }
    return undefined;
  }

  // Heading level matcher
  matchHeadingLevel(text: string, book: any): { level: number; confidence: number; ruleId: string } | null {
    const data = this.loadRulesFile("heading");
    const rules = data.rules || [];
    
    const sorted = [...rules].sort((a, b) => (b.priority || 0) - (a.priority || 0));

    for (const rule of sorted) {
      if (rule.enabled === false) continue;
      const keywords = rule.keywords || [];
      for (const kw of keywords) {
        if (text.startsWith(kw) || text.includes(kw)) {
          const lvl = rule.id === "level1" ? 1 : rule.id === "level2" ? 2 : 3;
          
          book.ruleDecisions = book.ruleDecisions || [];
          book.ruleDecisions.push({
            ruleId: rule.id,
            category: "heading",
            confidence: rule.confidence || 0.90,
            extractedValue: lvl,
            reason: `Matched heading keyword "${kw}" in text "${text.slice(0, 30)}"`
          });

          return { level: lvl, confidence: rule.confidence || 0.90, ruleId: rule.id };
        }
      }
    }
    return null;
  }

  // Quran verse references rules
  matchQuranVerses(text: string, nodeOrigin: any, book: any): any[] {
    const data = this.loadRulesFile("quran");
    const rules = data.rules || [];
    const rule = rules.find((r: any) => r.id === "quran_verse" && r.enabled !== false);
    if (!rule) return [];

    const found: any[] = [];
    const patterns = rule.surah_patterns || [];
    for (const pat of patterns) {
      const regex = new RegExp(pat, "g");
      const matches = [...text.matchAll(regex)];
      for (const m of matches) {
        const matchedText = m[0];
        const inner = matchedText.slice(1, -1);
        const parts = inner.split(":");
        if (parts.length === 2) {
          const surahCandidate = parts[0].replace(/^سورة\s+/, "").trim();
          const ayah = parseInt(parts[1]);
          if (!isNaN(ayah)) {
            const startIdx = m.index || 0;
            const endIdx = startIdx + matchedText.length;
            const origin = nodeOrigin ? {
              ...nodeOrigin,
              offsetStart: (nodeOrigin.offsetStart || 0) + startIdx,
              offsetEnd: (nodeOrigin.offsetStart || 0) + endIdx
            } : undefined;

            found.push({
              matchedText,
              surahCandidate,
              ayah,
              confidence: rule.confidence || 0.95,
              ruleId: rule.id,
              origin
            });

            book.ruleDecisions = book.ruleDecisions || [];
            book.ruleDecisions.push({
              ruleId: rule.id,
              category: "quran",
              confidence: rule.confidence || 0.95,
              extractedValue: { surahCandidate, ayah },
              reason: `Matched Quran pattern "${pat}" on text "${matchedText}"`,
              origin
            });
          }
        }
      }
    }
    return found;
  }

  // Hadith isnad matcher
  matchHadith(text: string, nodeOrigin: any, book: any): any | null {
    const data = this.loadRulesFile("hadith");
    const rules = data.rules || [];
    const rule = rules.find((r: any) => r.id === "hadith_patterns" && r.enabled !== false);
    if (!rule) return null;

    const patterns = rule.patterns || [];
    for (const pat of patterns) {
      const regex = new RegExp(pat, "u");
      const match = text.match(regex);
      if (match) {
        const startIdx = match.index || 0;
        const endIdx = startIdx + match[0].length;
        const origin = nodeOrigin ? {
          ...nodeOrigin,
          offsetStart: (nodeOrigin.offsetStart || 0) + startIdx,
          offsetEnd: (nodeOrigin.offsetStart || 0) + endIdx
        } : undefined;

        book.ruleDecisions = book.ruleDecisions || [];
        book.ruleDecisions.push({
          ruleId: rule.id,
          category: "hadith",
          confidence: rule.confidence || 0.90,
          extractedValue: text,
          reason: `Matched Hadith pattern "${pat}"`,
          origin
        });

        return {
          confidence: rule.confidence || 0.90,
          ruleId: rule.id,
          origin
        };
      }
    }
    return null;
  }

  // Scholar names rule lookup
  matchScholars(text: string, nodeOrigin: any, book: any): any[] {
    const data = this.loadRulesFile("scholar");
    const rules = data.rules || [];
    const rule = rules.find((r: any) => r.id === "scholar_names" && r.enabled !== false);
    if (!rule) return [];

    const found: any[] = [];
    const entities = rule.entities || [];
    for (const scholar of entities) {
      const idx = text.indexOf(scholar);
      if (idx !== -1) {
        const origin = nodeOrigin ? {
          ...nodeOrigin,
          offsetStart: (nodeOrigin.offsetStart || 0) + idx,
          offsetEnd: (nodeOrigin.offsetStart || 0) + idx + scholar.length
        } : undefined;

        found.push({
          scholar,
          confidence: rule.confidence || 0.90,
          ruleId: rule.id,
          origin
        });

        book.ruleDecisions = book.ruleDecisions || [];
        book.ruleDecisions.push({
          ruleId: rule.id,
          category: "scholar",
          confidence: rule.confidence || 0.90,
          extractedValue: scholar,
          reason: `Matched scholar entity "${scholar}"`,
          origin
        });
      }
    }
    return found;
  }

  // Book reference rule lookup
  matchBooks(text: string, nodeOrigin: any, book: any): any[] {
    const data = this.loadRulesFile("book");
    const rules = data.rules || [];
    const rule = rules.find((r: any) => r.id === "book_names" && r.enabled !== false);
    if (!rule) return [];

    const found: any[] = [];
    const entities = rule.entities || [];
    for (const bName of entities) {
      const idx = text.indexOf(bName);
      if (idx !== -1) {
        const origin = nodeOrigin ? {
          ...nodeOrigin,
          offsetStart: (nodeOrigin.offsetStart || 0) + idx,
          offsetEnd: (nodeOrigin.offsetStart || 0) + idx + bName.length
        } : undefined;

        found.push({
          bookName: bName,
          confidence: rule.confidence || 0.85,
          ruleId: rule.id,
          origin
        });

        book.ruleDecisions = book.ruleDecisions || [];
        book.ruleDecisions.push({
          ruleId: rule.id,
          category: "book",
          confidence: rule.confidence || 0.85,
          extractedValue: bName,
          reason: `Matched book reference entity "${bName}"`,
          origin
        });
      }
    }
    return found;
  }

  // Topics rule engine
  matchTopics(title: string, book: any): string[] {
    const data = this.loadRulesFile("topic");
    const rules = data.rules || [];
    const topics: string[] = [];

    for (const rule of rules) {
      if (rule.enabled === false) continue;
      const pattern = rule.pattern;
      if (pattern) {
        const regex = new RegExp(pattern, "i");
        if (regex.test(title)) {
          topics.push(rule.topic);

          book.ruleDecisions = book.ruleDecisions || [];
          book.ruleDecisions.push({
            ruleId: rule.id,
            category: "topic",
            confidence: rule.confidence || 0.90,
            extractedValue: rule.topic,
            reason: `Matched topic rule pattern "${pattern}"`
          });
        }
      }
    }
    return topics;
  }
}
