# -*- coding: utf-8 -*-
import os
import sys
import re
import json
import time
import tempfile
import subprocess
import tracemalloc
import difflib

# Arabic Surah names for Quran reference detection
SURAH_NAMES = [
    "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
    "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
    "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
    "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
    "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان",
    "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم",
    "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف",
    "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
    "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات",
    "النبأ", "النازعات", "عبس", "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج",
    "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى",
    "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة",
    "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون",
    "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
]

# Named Entities dictionary for matching
ENTITY_PATTERNS = {
    "people": [
        r"أبو حنيفة", r"النعمان بن ثابت", r"محمد بن عبد الرحمن الخميس", r"ابن عباس", r"عائشة",
        r"سيد قطب", r"ابن تيمية", r"الحسن البصري", r"ابن كثير", r"خالد بن الوليد", r"وهب بن منبه",
        r"النووي", r"الباقلاني", r"الغزالي", r"ابن حزم", r"أحمد بن حنبل", r"أبي بكر الخلال", r"البربهاري"
    ],
    "books": [
        r"الفقه الأبسط", r"الفقه الأكبر", r"البداية والنهاية", r"صحيح مسلم", r"صحيح البخاري",
        r"آكام المرجان", r"لقط المرجان", r"لوامع الأنوار", r"التدمرية", r"الحموية", r"الواسطية"
    ],
    "places": [
        r"الإمارات", r"بغداد", r"الكوفة", r"البصرة", r"مكة", r"المدينة", r"الشام", r"مصر", r"لاهور"
    ],
    "sects": [
        r"الجهمية", r"المعتزلة", r"الرافضة", r"الروافض", r"الشيعة", r"النصارى", r"اليهود", r"أهل السنة",
        r"الباطنية", r"الأشاعرة", r"المرجئة", r"القدرية", r"الخوارج"
    ],
    "madhhab": [
        r"الحنفية", r"الشافعية", r"المالكية", r"الحنابلة", r"حنيف"
    ]
}

def clean_arabic_text(text):
    """Normalize Arabic text by removing tashkeel, standardizing spaces, and letter forms."""
    if not text:
        return ""
    # Remove tashkeel (diacritics)
    tashkeel_pattern = re.compile(r"[\u064B-\u0652\u0653\u0670]")
    text = tashkeel_pattern.sub("", text)
    
    # Normalize Alef forms
    text = re.sub(r"[إأآ]", "ا", text)
    # Normalize Ta Marbuta and Ha
    text = re.sub(r"ة\b", "ه", text)
    # Normalize Ya and Alef Maksura
    text = re.sub(r"ى\b", "ي", text)
    
    # Standardize presentation forms
    # Remove punctuation & brackets
    text = re.sub(r"[^\w\s]", " ", text)
    # Standardize spaces
    text = re.sub(r"\s+", " ", text).strip()
    return text

def parse_meta_inline(meta_inline_list):
    """Recursively convert Pandoc AST metadata structure to string."""
    text_list = []
    def walk(node):
        if isinstance(node, list):
            for item in node:
                walk(item)
        elif isinstance(node, dict):
            node_type = node.get("t")
            if node_type in ("Str", "Space", "RawInline"):
                if node_type == "Space":
                    text_list.append(" ")
                else:
                    text_list.append(node.get("c", ""))
            else:
                walk(node.get("c", ""))
    walk(meta_inline_list)
    return "".join(text_list).strip()

class PandocASTParser:
    """Parses Pandoc JSON AST into structured layout, content, and metadata."""
    def __init__(self, json_data):
        self.json_data = json_data
        self.metadata = {}
        self.headings = []
        self.paragraphs = []
        self.footnotes = []
        self.lists_count = 0
        self.tables_count = 0
        self.blockquotes_count = 0
        
        # Formatting counts
        self.bold_count = 0
        self.italic_count = 0
        self.poetry_lines_count = 0
        
        # Difficulty indicators
        self.styles_seen = set()
        self.classes_seen = set()
        self.warnings = []
        
        self._parse()

    def _parse(self):
        # 1. Parse Metadata
        meta_block = self.json_data.get("meta", {})
        for key, val in meta_block.items():
            if isinstance(val, dict):
                meta_type = val.get("t")
                if meta_type == "MetaInlines":
                    self.metadata[key] = parse_meta_inline(val.get("c", []))
                elif meta_type == "MetaString":
                    self.metadata[key] = val.get("c", "")
                elif meta_type == "MetaList":
                    self.metadata[key] = ", ".join([parse_meta_inline(item.get("c", [])) for item in val.get("c", []) if isinstance(item, dict)])
            else:
                self.metadata[key] = str(val)

        # 2. Parse Blocks recursively
        blocks = self.json_data.get("blocks", [])
        self._walk_blocks(blocks)

    def _walk_blocks(self, blocks):
        if not isinstance(blocks, list):
            return
        for block in blocks:
            if not isinstance(block, dict):
                continue
            block_type = block.get("t")
            block_content = block.get("c")

            if block_type == "Header":
                # Header content: [level, [id, classes, keyvals], [inline_elements]]
                level = block_content[0]
                classes = block_content[1][1]
                for c in classes:
                    self.classes_seen.add(c)
                header_text = self._inline_to_string(block_content[2])
                self.headings.append({"level": level, "text": header_text})
                
            elif block_type in ("Para", "Plain"):
                para_text = self._inline_to_string(block_content)
                if para_text:
                    # Detect potential poetry in paragraphs (e.g. split by tab or multiple spaces)
                    if "  " in para_text or "\t" in para_text:
                        self.poetry_lines_count += 1
                    self.paragraphs.append(para_text)
                    
            elif block_type == "BlockQuote":
                self.blockquotes_count += 1
                self._walk_blocks(block_content)
                
            elif block_type in ("BulletList", "OrderedList"):
                self.lists_count += 1
                # Walk lists items which are lists of blocks
                list_items = block_content[1] if block_type == "OrderedList" else block_content
                for item in list_items:
                    self._walk_blocks(item)
                    
            elif block_type == "Table":
                self.tables_count += 1
                # Table structures can be complex, walk rows/cells blocks
                # We do simple recursive walk of the table dictionary to capture any paragraphs or notes inside cells
                self._walk_recursive_dict(block_content)
                
            else:
                # Walk recursively for any embedded blocks
                self._walk_recursive_dict(block_content)

    def _walk_recursive_dict(self, node):
        if isinstance(node, list):
            for item in node:
                self._walk_recursive_dict(item)
        elif isinstance(node, dict):
            if "t" in node and "c" in node:
                # Check if it's a block or inline we care about
                if node["t"] in ("Para", "Plain"):
                    para_text = self._inline_to_string(node["c"])
                    if para_text:
                        self.paragraphs.append(para_text)
                else:
                    self._walk_recursive_dict(node["c"])
            else:
                for v in node.values():
                    self._walk_recursive_dict(v)

    def _inline_to_string(self, inlines):
        """Convert inline elements list to raw string and capture formatting/notes."""
        if not isinstance(inlines, list):
            return ""
        text_list = []
        for node in inlines:
            if not isinstance(node, dict):
                continue
            node_type = node.get("t")
            node_content = node.get("c")

            if node_type == "Str":
                text_list.append(node_content)
            elif node_type == "Space":
                text_list.append(" ")
            elif node_type == "SoftBreak":
                text_list.append(" ")
            elif node_type == "LineBreak":
                text_list.append("\n")
            elif node_type == "Strong":
                self.bold_count += 1
                text_list.append(self._inline_to_string(node_content))
            elif node_type == "Emph":
                self.italic_count += 1
                text_list.append(self._inline_to_string(node_content))
            elif node_type == "Note":
                # Note content is a list of blocks representing the footnote text
                note_parser = PandocASTParser({"meta": {}, "blocks": node_content})
                note_text = " ".join(note_parser.paragraphs).strip()
                self.footnotes.append({
                    "index": len(self.footnotes) + 1,
                    "text": note_text
                })
                # Add superscript placeholder in text
                text_list.append(f"[{len(self.footnotes)}]")
            elif node_type in ("Span", "Div"):
                # Captures styles and classes
                attrs = node_content[0]
                classes = attrs[1]
                for c in classes:
                    self.classes_seen.add(c)
                keyvals = attrs[2]
                for k, v in keyvals:
                    if k == "style":
                        self.styles_seen.add(v)
                text_list.append(self._inline_to_string(node_content[1]))
            else:
                # Walk down recursively if content is a list
                if isinstance(node_content, list):
                    text_list.append(self._inline_to_string(node_content))
        return "".join(text_list)

class DocumentBenchmark:
    def __init__(self, doc_parser, epub_parser, parse_perf):
        self.doc = doc_parser
        self.epub = epub_parser
        self.perf = parse_perf
        self.comparison = {}
        self.scores = {}
        self.diffs = []
        
    def run(self):
        self._compare_metadata()
        self._compare_structure()
        self._compare_content()
        self._compare_footnotes()
        self._compare_quran()
        self._compare_hadith()
        self._compare_entities()
        self._compare_formatting()
        self._compare_difficulty()
        self._calculate_final_score()

    def _compare_metadata(self):
        doc_meta = self.doc.metadata
        epub_meta = self.epub.metadata
        
        # Standard metadata keys to check
        keys = ["title", "author", "publisher", "language", "identifier"]
        meta_diff = {}
        matched = 0
        total = len(keys)
        
        # Attempt to find other metadata inside text block heuristically
        def find_in_text(parser, keyword):
            pattern = re.compile(rf"{keyword}[:\s]+([^\n]+)", re.UNICODE)
            for p in parser.paragraphs[:15]:  # search first 15 paragraphs
                m = pattern.search(p)
                if m:
                    return m.group(1).strip()
            return None

        # Heuristic metadata extraction
        heuristics = {
            "editor": (r"المحقق", r"تحقيق"),
            "publication_year": (r"سنة النشر", r"سنة الطبع", r"تاريخ الطبع"),
            "edition": (r"الطبعة", r"طبعة"),
            "volumes": (r"عدد الأجزاء", r"أجزاء")
        }
        
        for k in keys:
            doc_val = doc_meta.get(k)
            epub_val = epub_meta.get(k)
            if not doc_val:
                doc_val = find_in_text(self.doc, k.replace("_", " ").capitalize())
            if not epub_val:
                epub_val = find_in_text(self.epub, k.replace("_", " ").capitalize())
                
            meta_diff[k] = {"doc": doc_val, "epub": epub_val}
            if doc_val and epub_val and clean_arabic_text(doc_val) == clean_arabic_text(epub_val):
                matched += 1

        for k, patterns in heuristics.items():
            doc_val = None
            epub_val = None
            for pat in patterns:
                doc_val = find_in_text(self.doc, pat)
                if doc_val:
                    break
            for pat in patterns:
                epub_val = find_in_text(self.epub, pat)
                if epub_val:
                    break
            meta_diff[k] = {"doc": doc_val, "epub": epub_val}
            total += 1
            if doc_val and epub_val and clean_arabic_text(doc_val) == clean_arabic_text(epub_val):
                matched += 1
                
        self.comparison["metadata"] = meta_diff
        self.scores["metadata"] = int((matched / total) * 100)

    def _compare_structure(self):
        doc_headings = self.doc.headings
        epub_headings = self.epub.headings
        
        h_counts = {"doc": {1: 0, 2: 0, 3: 0, "other": 0}, "epub": {1: 0, 2: 0, 3: 0, "other": 0}}
        for h in doc_headings:
            lvl = h["level"]
            if lvl in (1, 2, 3):
                h_counts["doc"][lvl] += 1
            else:
                h_counts["doc"]["other"] += 1
                
        for h in epub_headings:
            lvl = h["level"]
            if lvl in (1, 2, 3):
                h_counts["epub"][lvl] += 1
            else:
                h_counts["epub"]["other"] += 1
                
        struct_diff = {
            "headings_lvl1": {"doc": h_counts["doc"][1], "epub": h_counts["epub"][1]},
            "headings_lvl2": {"doc": h_counts["doc"][2], "epub": h_counts["epub"][2]},
            "headings_lvl3": {"doc": h_counts["doc"][3], "epub": h_counts["epub"][3]},
            "paragraphs": {"doc": len(self.doc.paragraphs), "epub": len(self.epub.paragraphs)},
            "lists": {"doc": self.doc.lists_count, "epub": self.epub.lists_count},
            "tables": {"doc": self.doc.tables_count, "epub": self.epub.tables_count},
            "blockquotes": {"doc": self.doc.blockquotes_count, "epub": self.epub.blockquotes_count}
        }
        self.comparison["structure"] = struct_diff
        
        # Calculate structural structural similarity score
        matched = 0
        total = len(struct_diff)
        for key, val in struct_diff.items():
            d = val["doc"]
            e = val["epub"]
            if d == e:
                matched += 1
            elif max(d, e) > 0:
                matched += 1 - (abs(d - e) / max(d, e))
        self.scores["structure"] = int((matched / total) * 100)

    def _compare_content(self):
        # Concatenate and normalize content paragraphs
        doc_normalized_paras = [clean_arabic_text(p) for p in self.doc.paragraphs if p.strip()]
        epub_normalized_paras = [clean_arabic_text(p) for p in self.epub.paragraphs if p.strip()]
        
        doc_normalized_text = " ".join(doc_normalized_paras)
        epub_normalized_text = " ".join(epub_normalized_paras)
        
        # Gestalt Pattern Matching Similarity
        char_similarity = difflib.SequenceMatcher(None, doc_normalized_text, epub_normalized_text).ratio()
        
        # Word Similarity
        doc_words = doc_normalized_text.split()
        epub_words = epub_normalized_text.split()
        word_similarity = difflib.SequenceMatcher(None, doc_words, epub_words).ratio()
        
        # Paragraph similarity
        para_similarity = difflib.SequenceMatcher(None, doc_normalized_paras, epub_normalized_paras).ratio()
        
        self.comparison["content"] = {
            "char_similarity": char_similarity,
            "word_similarity": word_similarity,
            "para_similarity": para_similarity,
            "doc_length_chars": len(doc_normalized_text),
            "epub_length_chars": len(epub_normalized_text),
            "doc_word_count": len(doc_words),
            "epub_word_count": len(epub_words)
        }
        self.scores["content"] = int(char_similarity * 100)
        
        # Generate paragraph-level diff of normalized text
        diff = list(difflib.unified_diff(
            doc_normalized_paras,
            epub_normalized_paras,
            fromfile='doc_content',
            tofile='epub_content',
            n=1
        ))
        
        # Extract a snippet diff (first 30 lines of differences)
        self.diffs = diff[:50]
        
        # Diff Classification logic
        diff_classes = {
            "formatting_only": 0, "missing_heading": 0, "missing_paragraph": 0,
            "missing_footnote": 0, "encoding_issue": 0, "ocr_issue": 0,
            "parsing_issue": 0, "unknown": 0
        }
        for line in diff:
            if line.startswith("-") and not line.startswith("---"):
                clean_line = line[1:].strip()
                if not clean_line:
                    diff_classes["formatting_only"] += 1
                elif len(clean_line.split()) < 5:
                    diff_classes["missing_heading"] += 1
                elif "[" in clean_line and "]" in clean_line:
                    diff_classes["missing_footnote"] += 1
                else:
                    diff_classes["missing_paragraph"] += 1
            elif line.startswith("+") and not line.startswith("+++"):
                clean_line = line[1:].strip()
                if not clean_line:
                    diff_classes["formatting_only"] += 1
                elif len(clean_line.split()) < 5:
                    diff_classes["missing_heading"] += 1
                elif "[" in clean_line and "]" in clean_line:
                    diff_classes["missing_footnote"] += 1
                else:
                    diff_classes["missing_paragraph"] += 1
                    
        self.comparison["diff_classification"] = diff_classes

    def _compare_footnotes(self):
        doc_notes = self.doc.footnotes
        epub_notes = self.epub.footnotes
        
        missing = []
        broken = []
        matched = 0
        
        for idx, e_note in enumerate(epub_notes):
            # Check if there is a matching note in DOC
            e_text = clean_arabic_text(e_note["text"])
            found = False
            for d_note in doc_notes:
                d_text = clean_arabic_text(d_note["text"])
                if d_text == e_text:
                    found = True
                    if d_note["index"] != e_note["index"]:
                        broken.append({
                            "type": "ordering_difference",
                            "text": e_note["text"],
                            "doc_idx": d_note["index"],
                            "epub_idx": e_note["index"]
                        })
                    else:
                        matched += 1
                    break
            if not found:
                missing.append({"source": "epub", "text": e_note["text"], "idx": e_note["index"]})
                
        for idx, d_note in enumerate(doc_notes):
            d_text = clean_arabic_text(d_note["text"])
            found = any(clean_arabic_text(e_note["text"]) == d_text for e_note in epub_notes)
            if not found:
                missing.append({"source": "doc", "text": d_note["text"], "idx": d_note["index"]})

        self.comparison["footnotes"] = {
            "doc_count": len(doc_notes),
            "epub_count": len(epub_notes),
            "missing": missing,
            "ordering_issues": broken
        }
        
        # Calculate footnote similarity score
        total_notes = max(len(doc_notes), len(epub_notes))
        if total_notes == 0:
            self.scores["footnotes"] = 100
        else:
            self.scores["footnotes"] = int((matched / total_notes) * 100)

    def _compare_quran(self):
        # Standard Quran reference pattern: [Surah: Ayah] or (Surah: Ayah)
        # Matches formats like [الأعراف: 27] or [سورة البقرة: 12] or (الرحمن: 15)
        pattern = re.compile(r"[\[\(]\s*([^\d\]\):]+?)\s*:\s*(\d+)\s*[\]\)]")
        
        def extract_quran_refs(paragraphs):
            refs = []
            for p in paragraphs:
                for match in pattern.finditer(p):
                    surah_candidate = match.group(1).strip()
                    # Strip "سورة" prefix if exists
                    surah_candidate = re.sub(r"^سورة\s+", "", surah_candidate).strip()
                    ayah = int(match.group(2))
                    # Validate Surah candidate
                    matched_surah = None
                    for sn in SURAH_NAMES:
                        if clean_arabic_text(sn) == clean_arabic_text(surah_candidate) or clean_arabic_text(sn) in clean_arabic_text(surah_candidate):
                            matched_surah = sn
                            break
                    if matched_surah:
                        refs.append(f"{matched_surah}:{ayah}")
            return refs

        doc_refs = extract_quran_refs(self.doc.paragraphs)
        epub_refs = extract_quran_refs(self.epub.paragraphs)
        
        doc_set = set(doc_refs)
        epub_set = set(epub_refs)
        
        overlap = doc_set.intersection(epub_set)
        missing_in_doc = epub_set - doc_set
        missing_in_epub = doc_set - epub_set
        
        self.comparison["quran"] = {
            "doc_ref_count": len(doc_refs),
            "epub_ref_count": len(epub_refs),
            "doc_unique_count": len(doc_set),
            "epub_unique_count": len(epub_set),
            "overlap_count": len(overlap),
            "missing_in_doc": list(missing_in_doc),
            "missing_in_epub": list(missing_in_epub)
        }
        
        total_unique = len(doc_set.union(epub_set))
        if total_unique == 0:
            self.scores["quran"] = 100
        else:
            self.scores["quran"] = int((len(overlap) / total_unique) * 100)

    def _compare_hadith(self):
        # Hadith indicators: قال رسول الله, عن ..., حدثنا, أخبرنا
        hadith_indicators = [
            r"قال رسول الله صلى الله عليه وسلم",
            r"عن\s+([أبإ]\w+\s+){1,3}قال",
            r"حدثنا\s+\w+",
            r"أخبرنا\s+\w+"
        ]
        
        def count_hadiths(paragraphs):
            count = 0
            for p in paragraphs:
                matched = False
                for ind in hadith_indicators:
                    if re.search(ind, p):
                        matched = True
                        break
                if matched:
                    count += 1
            return count
            
        doc_hadith_count = count_hadiths(self.doc.paragraphs)
        epub_hadith_count = count_hadiths(self.epub.paragraphs)
        
        self.comparison["hadith"] = {
            "doc_count_estimate": doc_hadith_count,
            "epub_count_estimate": epub_hadith_count
        }

    def _compare_entities(self):
        # Simple regex matcher for named entities in clean text
        def extract_entities(paragraphs):
            extracted = {cat: set() for cat in ENTITY_PATTERNS}
            full_text = " ".join(paragraphs)
            for cat, patterns in ENTITY_PATTERNS.items():
                for pat in patterns:
                    if re.search(pat, full_text):
                        extracted[cat].add(pat)
            return extracted

        doc_ents = extract_entities(self.doc.paragraphs)
        epub_ents = extract_entities(self.epub.paragraphs)
        
        entities_diff = {}
        matched_sum = 0
        total_sum = 0
        
        for cat in ENTITY_PATTERNS:
            doc_set = doc_ents[cat]
            epub_set = epub_ents[cat]
            overlap = doc_set.intersection(epub_set)
            
            entities_diff[cat] = {
                "doc_count": len(doc_set),
                "epub_count": len(epub_set),
                "overlap": list(overlap),
                "missing_in_doc": list(epub_set - doc_set),
                "missing_in_epub": list(doc_set - epub_set)
            }
            
            matched_sum += len(overlap)
            total_sum += len(doc_set.union(epub_set))
            
        self.comparison["entities"] = entities_diff
        if total_sum == 0:
            self.scores["entities"] = 100
        else:
            self.scores["entities"] = int((matched_sum / total_sum) * 100)

    def _compare_formatting(self):
        format_diff = {
            "bold": {"doc": self.doc.bold_count, "epub": self.epub.bold_count},
            "italic": {"doc": self.doc.italic_count, "epub": self.epub.italic_count},
            "blockquotes": {"doc": self.doc.blockquotes_count, "epub": self.epub.blockquotes_count},
            "poetry_lines": {"doc": self.doc.poetry_lines_count, "epub": self.epub.poetry_lines_count},
            "tables": {"doc": self.doc.tables_count, "epub": self.epub.tables_count}
        }
        self.comparison["formatting"] = format_diff
        
        matched = 0
        total = len(format_diff)
        for key, val in format_diff.items():
            d = val["doc"]
            e = val["epub"]
            if d == e:
                matched += 1
            elif max(d, e) > 0:
                matched += 1 - (abs(d - e) / max(d, e))
        self.scores["formatting"] = int((matched / total) * 100)

    def _compare_difficulty(self):
        self.comparison["difficulty"] = {
            "doc": {
                "unique_styles": len(self.doc.styles_seen),
                "unique_classes": len(self.doc.classes_seen),
                "warnings": len(self.doc.warnings)
            },
            "epub": {
                "unique_styles": len(self.epub.styles_seen),
                "unique_classes": len(self.epub.classes_seen),
                "warnings": len(self.epub.warnings)
            }
        }

    def _calculate_final_score(self):
        # Weighted Quality Score
        weights = {
            "metadata": 0.1,
            "structure": 0.15,
            "content": 0.4,
            "footnotes": 0.15,
            "entities": 0.1,
            "formatting": 0.1
        }
        final_score = 0
        for category, w in weights.items():
            final_score += self.scores.get(category, 0) * w
            
        self.scores["final_quality_score"] = int(final_score)

    def generate_report(self):
        # 1. Generate JSON Report
        json_report = {
            "performance": self.perf,
            "scores": self.scores,
            "comparison": self.comparison,
            "diff_snippet": self.diffs
        }
        
        # 2. Generate Markdown Report
        md_report = f"""# 📊 Importer Benchmark Report: DOC vs EPUB

This report evaluates the information preservation and quality scores of the same book parsed from **DOC** and **EPUB** formats.

## 🏆 Final Quality Score

| Metric | DOC Score | EPUB Score | Difference / Target |
| :--- | :---: | :---: | :---: |
| **Metadata Accuracy** | - | - | {self.scores['metadata']}% |
| **Structure Preservation** | - | - | {self.scores['structure']}% |
| **Content Similarity** | - | - | {self.scores['content']}% |
| **Footnotes Integrity** | - | - | {self.scores['footnotes']}% |
| **Entity Matching** | - | - | {self.scores['entities']}% |
| **Formatting Retention** | - | - | {self.scores['formatting']}% |
| 👑 **OVERALL QUALITY SCORE** | - | - | **{self.scores['final_quality_score']}%** |

---

## ⏱️ Performance Benchmarks

| Metric | DOC | EPUB |
| :--- | :---: | :---: |
| **Parsing & Conversion Time** | {self.perf['doc']['conversion_time'] + self.perf['doc']['parse_time']:.2f}s | {self.perf['epub']['parse_time']:.2f}s |
| **Peak Memory Usage** | {self.perf['doc']['peak_memory'] / 1024 / 1024:.2f} MB | {self.perf['epub']['peak_memory'] / 1024 / 1024:.2f} MB |

---

## 📂 Structural counts

| Element | DOC Count | EPUB Count | Difference |
| :--- | :---: | :---: | :---: |
| **Heading Level 1** | {self.comparison['structure']['headings_lvl1']['doc']} | {self.comparison['structure']['headings_lvl1']['epub']} | {abs(self.comparison['structure']['headings_lvl1']['doc'] - self.comparison['structure']['headings_lvl1']['epub'])} |
| **Heading Level 2** | {self.comparison['structure']['headings_lvl2']['doc']} | {self.comparison['structure']['headings_lvl2']['epub']} | {abs(self.comparison['structure']['headings_lvl2']['doc'] - self.comparison['structure']['headings_lvl2']['epub'])} |
| **Heading Level 3** | {self.comparison['structure']['headings_lvl3']['doc']} | {self.comparison['structure']['headings_lvl3']['epub']} | {abs(self.comparison['structure']['headings_lvl3']['doc'] - self.comparison['structure']['headings_lvl3']['epub'])} |
| **Paragraphs** | {self.comparison['structure']['paragraphs']['doc']} | {self.comparison['structure']['paragraphs']['epub']} | {abs(self.comparison['structure']['paragraphs']['doc'] - self.comparison['structure']['paragraphs']['epub'])} |
| **Footnotes** | {self.comparison['footnotes']['doc_count']} | {self.comparison['footnotes']['epub_count']} | {abs(self.comparison['footnotes']['doc_count'] - self.comparison['footnotes']['epub_count'])} |

---

## 📖 Citations & Entities Matching

* **Quran Verses Extracted:** DOC unique: {self.comparison['quran']['doc_unique_count']} | EPUB unique: {self.comparison['quran']['epub_unique_count']} | Overlap: {self.comparison['quran']['overlap_count']}
* **Hadith Count (Estimate):** DOC: {self.comparison['hadith']['doc_count_estimate']} | EPUB: {self.comparison['hadith']['epub_count_estimate']}
* **Entity Match (People):** {self.comparison['entities']['people']['doc_count']} matched

---

## 🗂️ Difference Classification

Every detected textual difference has been analyzed and classified:

* **Formatting Only:** {self.comparison['diff_classification']['formatting_only']}
* **Missing Headings:** {self.comparison['diff_classification']['missing_heading']}
* **Missing Paragraphs:** {self.comparison['diff_classification']['missing_paragraph']}
* **Missing Footnotes:** {self.comparison['diff_classification']['missing_footnote']}
* **Encoding Issues:** {self.comparison['diff_classification']['encoding_issue']}
* **OCR Issues:** {self.comparison['diff_classification']['ocr_issue']}
* **Parsing Issues:** {self.comparison['diff_classification']['parsing_issue']}
* **Unknown:** {self.comparison['diff_classification']['unknown']}

---

## 📝 Differences Diff Snippet (Top 15 lines)
```diff
"""
        for line in self.diffs[:15]:
            md_report += f"{line}\n"
        md_report += "```\n"
        
        return json_report, md_report

def main():
    if len(sys.argv) < 5:
        print("Usage: python3 benchmark.py --epub <epub_file> --doc <doc_or_docx_file> --out <output_path>")
        sys.exit(1)
        
    epub_file = None
    doc_file = None
    out_path = None
    
    # Simple CLI argument parser
    for i in range(len(sys.argv)):
        if sys.argv[i] == "--epub":
            epub_file = sys.argv[i+1]
        elif sys.argv[i] == "--doc":
            doc_file = sys.argv[i+1]
        elif sys.argv[i] == "--out":
            out_path = sys.argv[i+1]
            
    if not (epub_file and doc_file and out_path):
        print("Missing required arguments. --epub, --doc, and --out are all required.")
        sys.exit(1)

    print(f"Reading EPUB: {epub_file}")
    print(f"Reading DOC:  {doc_file}")

    # Set up performance metrics structure
    perf = {
        "doc": {"conversion_time": 0.0, "parse_time": 0.0, "peak_memory": 0},
        "epub": {"parse_time": 0.0, "peak_memory": 0}
    }

    temp_dir = tempfile.mkdtemp()
    try:
        # 1. Handle DOC file (if .doc, convert via LibreOffice to .docx first)
        docx_file = doc_file
        if doc_file.endswith(".doc"):
            print("Converting legacy .doc to modern .docx using headless LibreOffice...")
            start_time = time.time()
            subprocess.run([
                "libreoffice", "--headless", "--convert-to", "docx",
                "--outdir", temp_dir, doc_file
            ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
            doc_basename = os.path.splitext(os.path.basename(doc_file))[0]
            docx_file = os.path.join(temp_dir, f"{doc_basename}.docx")
            perf["doc"]["conversion_time"] = time.time() - start_time
            print(f"LibreOffice conversion took {perf['doc']['conversion_time']:.2f}s")
            
        # 2. Run Pandoc on DOCX to get JSON AST
        doc_ast_file = os.path.join(temp_dir, "doc_ast.json")
        print("Generating DOCX Pandoc JSON AST...")
        start_time = time.time()
        tracemalloc.start()
        subprocess.run([
            "pandoc", "-f", "docx", "-t", "json", "-o", doc_ast_file, docx_file
        ], check=True)
        perf["doc"]["parse_time"] = time.time() - start_time
        _, doc_peak = tracemalloc.get_traced_memory()
        perf["doc"]["peak_memory"] = doc_peak
        tracemalloc.stop()
        
        # Load DOC AST
        with open(doc_ast_file, 'r', encoding='utf-8') as f:
            doc_ast = json.load(f)
            
        # 3. Run Pandoc on EPUB to get JSON AST
        epub_ast_file = os.path.join(temp_dir, "epub_ast.json")
        print("Generating EPUB Pandoc JSON AST...")
        start_time = time.time()
        tracemalloc.start()
        subprocess.run([
            "pandoc", "-f", "epub", "-t", "json", "-o", epub_ast_file, epub_file
        ], check=True)
        perf["epub"]["parse_time"] = time.time() - start_time
        _, epub_peak = tracemalloc.get_traced_memory()
        perf["epub"]["peak_memory"] = epub_peak
        tracemalloc.stop()
        
        # Load EPUB AST
        with open(epub_ast_file, 'r', encoding='utf-8') as f:
            epub_ast = json.load(f)

        # 4. Parse both documents with the AST parser
        print("Parsing DOCX AST layout structure...")
        doc_parser = PandocASTParser(doc_ast)
        print("Parsing EPUB AST layout structure...")
        epub_parser = PandocASTParser(epub_ast)

        # 5. Run the Quality & Similarity Benchmark
        print("Running comparative benchmark metrics...")
        bench = DocumentBenchmark(doc_parser, epub_parser, perf)
        bench.run()

        # 6. Generate outputs
        json_report, md_report = bench.generate_report()
        
        # Write JSON report
        out_json_file = f"{out_path}.json"
        with open(out_json_file, 'w', encoding='utf-8') as f:
            json.dump(json_report, f, ensure_ascii=False, indent=2)
        print(f"JSON Benchmark results written to {out_json_file}")
        
        # Write Markdown report
        out_md_file = f"{out_path}.md"
        with open(out_md_file, 'w', encoding='utf-8') as f:
            f.write(md_report)
        print(f"Markdown Benchmark report written to {out_md_file}")

    finally:
        # Clean up temp folder
        try:
            import shutil
            shutil.rmtree(temp_dir)
        except Exception:
            pass

if __name__ == "__main__":
    main()
