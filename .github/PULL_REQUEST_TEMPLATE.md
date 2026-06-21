<div dir="rtl">

## ملخّص التغيير

<!-- ما الذي يضيفه أو يعدّله هذا الـ PR؟ -->

## نوع المحتوى

<!-- person · subject · topic · book · poem · series · lesson · question · benefit · article · audio · annotation · announcement -->

## قائمة المراجعة قبل النشر

- [ ] الـ frontmatter كامل وكل الحقول الإلزامية موجودة.
- [ ] المعرّف (اسم الملف) لاتيني وصفي ثابت ومطابق للنمط `^[a-z0-9]+(--?[a-z0-9]+)*$`.
- [ ] كل `(source_type, source_id)` و`target_id` يحلّ إلى كيان منشور.
- [ ] كل مرساة Annotation تطابق موضعاً موجوداً فعلاً في المصدر.
- [ ] الموضوعات 1–5، حقيقية لا وسوم.
- [ ] محتوى المتطوّع قُرئ كاملاً ونُقّي من HTML خام وروابط مشبوهة.
- [ ] الصحة العلمية: العزو والتخريج مدقَّقان.
- [ ] للدرس: التفريغ (transcript) موجود.
- [ ] `pnpm validate:content && pnpm build` يمرّان أخضر محلياً.

> ضبط الحالة إلى `published` والدمج في `main` من صلاحية الفريق فقط (CODEOWNERS + حماية الفرع).

تفاصيل: [CONTRIBUTING.md](../CONTRIBUTING.md) · [docs/governance.md](../docs/governance.md)

</div>
