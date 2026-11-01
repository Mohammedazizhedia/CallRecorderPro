# DESIGN.md — Call Recorder Pro

## وضع خدمات جوجل (BFB §2 — إلزامي)
- **المعتمد: A) بدون جوجل (No-GMS / No-HMS)**
- السبب: التطبيق لا يستخدم خرائط ولا Push ولا Billing أصلاً.
- البدائل المعتمدة (BFB §2): Backup = ZIP محلي + مشاركة نظامية · Update = GitHub Releases · Analytics = سجل تشخيص محلي فقط.
- التحويل مستقبلاً لوضع B (Hybrid) يتم عبر Provider Interfaces دون تغيير الواجهات.

## التعريب (BFB §3)
- عربي RTL كامل + إنجليزي LTR، تبديل فوري من الإعدادات، أيقونات مترابطة (AutoMirrored).

## التصميم (BFB §4)
- الخطوط: Cairo (مضمّن) + IBM Plex Arabic (fallback عربي) — ممنوع DejaVu Sans.
- الثيمات: 5 سمات (ديناميكي/أزرق/أخضر/بنفسجي/AMOLED).
- حجم الخط: 90–130% + Bold حقيقي.
