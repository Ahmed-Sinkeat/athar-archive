// أذكار الصباح والمساء — the standard Hisn al-Muslim set. Fixed, closed content
// that essentially never changes, so it lives here as data rather than as a
// content collection.
// ponytail: hardcoded list, not a CMS collection — move to a content
// collection if per-occasion adhkar (beyond morning/evening) get added later.
//
// `source` is intentionally left blank on several items: this site is a
// hadith/scholarship archive, and a wrong takhrij is worse than a missing
// one — only filled in where the attribution is unambiguous and extremely
// widely agreed on. Verify against the actual Hisn al-Muslim text (or the
// site's own gen-takhrij pipeline) before treating the rest as settled.

export interface AdhkarItem {
  id: string;
  text: string;
  count: number;
  source?: string;
}

const AYAH_KURSI =
  "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ";
const SURAH_IKHLAS = "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ";
const SURAH_FALAQ = "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِنْ شَرِّ مَا خَلَقَ، وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ، وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ، وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ";
const SURAH_NAS =
  "قُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَٰهِ النَّاسِ، مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ";
const SAYYID_ISTIGHFAR =
  "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ";
const RADHITU = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا";
const YA_HAYYU = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ";
const BISMILLAHI_LA_YADUR = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ، وَهُوَ السَّمِيعُ الْعَلِيمُ";
const ALLAHUMMA_AAFINI = "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَٰهَ إِلَّا أَنْتَ";
const HASBIYA_ALLAH = "حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ";
const AUZU_KALIMAT = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ";
const SUBHAN_ALLAH_BIHAMDIHI = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ";
const ASTAGHFIRULLAH = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ";

export const MORNING: AdhkarItem[] = [
  { id: "m1", text: AYAH_KURSI, count: 1 },
  { id: "m2", text: SURAH_IKHLAS, count: 3 },
  { id: "m3", text: SURAH_FALAQ, count: 3 },
  { id: "m4", text: SURAH_NAS, count: 3 },
  { id: "m5", text: "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", count: 1, source: "رواه مسلم" },
  { id: "m6", text: "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ", count: 1 },
  { id: "m7", text: SAYYID_ISTIGHFAR, count: 1, source: "رواه البخاري" },
  { id: "m8", text: "اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ، فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ، فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ", count: 1 },
  // كان مفقودًا من القائمة السابقة — أضيف بعد المراجعة
  { id: "m8b", text: "اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلَائِكَتَكَ، وَجَمِيعَ خَلْقِكَ، أَنَّكَ أَنْتَ اللَّهُ لَا إِلَٰهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ، وَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ", count: 4, source: "رواه أبو داود" },
  { id: "m9", text: RADHITU, count: 3 },
  { id: "m10", text: YA_HAYYU, count: 1 },
  { id: "m11", text: BISMILLAHI_LA_YADUR, count: 3 },
  { id: "m12", text: ALLAHUMMA_AAFINI, count: 3 },
  { id: "m13", text: HASBIYA_ALLAH, count: 7 },
  { id: "m14", text: AUZU_KALIMAT, count: 3, source: "رواه مسلم" },
  { id: "m15", text: SUBHAN_ALLAH_BIHAMDIHI, count: 100, source: "رواه مسلم" },
  { id: "m16", text: ASTAGHFIRULLAH, count: 100, source: "رواه مسلم" },
];

export const EVENING: AdhkarItem[] = [
  { id: "e1", text: AYAH_KURSI, count: 1 },
  { id: "e2", text: SURAH_IKHLAS, count: 3 },
  { id: "e3", text: SURAH_FALAQ, count: 3 },
  { id: "e4", text: SURAH_NAS, count: 3 },
  { id: "e5", text: "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", count: 1, source: "رواه مسلم" },
  { id: "e6", text: "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ", count: 1 },
  { id: "e7", text: SAYYID_ISTIGHFAR, count: 1, source: "رواه البخاري" },
  { id: "e8", text: "اللَّهُمَّ مَا أَمْسَى بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ، فَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ، فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ", count: 1 },
  { id: "e8b", text: "اللَّهُمَّ إِنِّي أَمْسَيْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلَائِكَتَكَ، وَجَمِيعَ خَلْقِكَ، أَنَّكَ أَنْتَ اللَّهُ لَا إِلَٰهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ، وَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ", count: 4, source: "رواه أبو داود" },
  { id: "e9", text: RADHITU, count: 3 },
  { id: "e10", text: YA_HAYYU, count: 1 },
  { id: "e11", text: BISMILLAHI_LA_YADUR, count: 3 },
  { id: "e12", text: ALLAHUMMA_AAFINI, count: 3 },
  { id: "e13", text: HASBIYA_ALLAH, count: 7 },
  { id: "e14", text: AUZU_KALIMAT, count: 3, source: "رواه مسلم" },
  { id: "e15", text: SUBHAN_ALLAH_BIHAMDIHI, count: 100, source: "رواه مسلم" },
  { id: "e16", text: ASTAGHFIRULLAH, count: 100, source: "رواه مسلم" },
];
