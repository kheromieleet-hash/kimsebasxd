# ClickGUI Renk Canlılığı ve Kontrast İyileştirme Planı

## 1. Problem Özeti
`ModernClickGuiScreen` arayüzündeki ikincil metinler, pasif kart kenarlıkları, aktif kart dolguları ve üst rozetler (`v1.0.82`, `19 Active`) düşük opaklık ve solgun gri/pastel tonlar yüzünden mat ve soluk görünüyor.

---

## 2. Tespit Edilen Solgun Renk Kaynakları

### A. Tema Sabitleri (`JelloTheme.java`)
- `TEXT_MUTED = 0xFF94A3B8` → Yan menü sekmeleri, `[None]` tuş etiketleri, pasif kategori chip'leri ve `Search modules...` metni solgun gri-mavi görünüyor.
- `TEXT_DIM = 0xFFFF9EA6` → Pastel pembe-kırmızı ton (`Controls`, keybind vurguları, sürüm rozeti metni) canlı crimson temasından uzaklaşıp soluk kalıyor.
- `BORDER_SUBTLE = 0x20FFFFFF` → %12.5 opaklıkta beyaz çerçeve pasif modül kartlarını ve chip'leri arka plana gömüyor.
- `CARD_ACTIVE = 0xF222070B` / `CARD_ACTIVE_HOVER = 0xF82E090E` → Aktif modül kartlarının arka planı çok koyu bordo olduğu için aktif/pasif ayrımı yeterince parlak durmuyor.
- `ACCENT = 0xFFFF1E2D` / `ACCENT_HOVER = 0xFFFF424E` / `BORDER_ACTIVE_SOFT = 0xD8FF2838` → Aktif kenarlıklar ve toggle'lar tam opak, daha yüksek doygunluklu neon/crimson tonuna çekilebilir.

### B. Rozetler ve Pencere Kenarlığı (`ModernClickGuiScreen.java`)
- Pencere dış çerçevesi (`0x26FFFFFF`) ve ayırıcı çizgiler (`DIVIDER = 0x1AFFFFFF`) çok silik.
- `v1.0.82` rozeti (`0x36FF1E2D`, border `0xAAFF1E2D`) ve `19 Active` rozeti (`0x2410B981`, border `0x4410B981`, nokta `0xFF34D399`) düşük alfa yüzünden solgun görünüyor.

---

## 3. Görev Dağılımı (Task Breakdown)

### Task 1 — `JelloTheme` Paletini Canlandırma (`frontend-specialist`)
- **Dosya:** `src/main/java/com/client/mod/JelloTheme.java`
- **Değişiklik:**
  - `TEXT_MUTED`: `0xFF94A3B8` → Daha parlak, net okunur nötr gümüş (`0xFFC8D2E0`).
  - `TEXT_DIM`: `0xFFFF9EA6` → Pastel pembe yerine canlı mercan-kırmızı (`0xFFFF5A67`).
  - `ACCENT` / `ACCENT_HOVER`: Tam canlı saf crimson (`0xFFFF1A2B` / `0xFFFF4757`).
  - `CARD_ACTIVE` / `CARD_ACTIVE_HOVER`: Aktif kart iç dolgusu daha belirgin canlı koyu kırmızı (`0xF5340910` / `0xFA450D17`) ve `BORDER_ACTIVE_SOFT` tam opak (`0xFFFF2E3E`).
  - `BORDER_SUBTLE` & `DIVIDER`: `0x20FFFFFF` → `0x44FFFFFF`, `0x1AFFFFFF` → `0x2EFFFFFF` (kart ve chiplerin hatları keskinleşsin).
  - `SWITCH_OFF` / `SWITCH_OFF_HOVER`: Daha net kontrast (`0xFF1F2029` / `0xFF2D2E3B`).

### Task 2 — Üst Rozet ve Pencere Çerçevesi Kontrastı (`frontend-specialist`)
- **Dosya:** `src/main/java/com/client/mod/ModernClickGuiScreen.java`
- **Değişiklik:**
  - Dış pencere çerçevesini (`0x26FFFFFF` → `0x48FFFFFF`) güçlendir.
  - `v1.0.82` rozeti (`0x55FF1A2B` dolgu, `0xFFFF2E3E` kenarlık, `0xFFFFFFFF` veya canlı kırmızı metin) ve `Active` rozeti (`0x3A10B981` dolgu, `0xCC10B981` kenarlık, `0xFF4ADE80` nokta & yazı) daha canlı hale getir.
  - Sidebar aktif sekme dolgusunu ve `Controls` kutusu kenarlığını yeni `JelloTheme` değerleriyle hizala.

### Task 3 — Doğrulama ve Sürüm Yayını (`test-engineer`)
- **İşlem:** `publish_update.ps1` ile derleme ve mevcut UI layout testlerinin (`ModernInspectorLayoutTest`, `ModernModuleSettingsTest`) bozulmadığını doğrulama + GitHub Release & Git push.

---

## 4. Doğrulama Kontrol Listesi (Phase X)
- [ ] Pasif modül kartları, chip'ler ve yan menü yazıları arka planda kaybolmadan net okunuyor.
- [ ] Aktif modül kartları, toggle'lar, `v1.0.82` ve `Active` rozetleri canlı ve yüksek kontrastlı görünüyor.
- [ ] `publish_update.ps1` derlemesi hatasız tamamlanıyor.
