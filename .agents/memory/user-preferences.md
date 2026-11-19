---
type: user
created: 2026-07-18
updated: 2026-11-19
---

# User Preferences

- Her yeni modül, ayar veya özellik eklendiğinde, çıkarıldığında veya değiştirildiğinde proje kökündeki [MODULLER.txt](file:///c:/Users/PC/Desktop/client/MODULLER.txt) dosyasını mutlaka güncelle (tüm modüllerin Türkçe açıklamaları, ayarları ve sayıları güncel kalsın).
- Yeni bir modül, ekran, Mixin, özellik veya dosya eklendiğinde / yüklendiğinde değişiklikleri mutlaka şu 4 başlıkta denetle ve bulguları kullanıcıya raporla:
  1. **Kritik Bug & Çökme (NPE / Mixin / Thread / Bellek) Riskleri** (yeni Mixin hedef imzalarını MC `26.3` / Sodium `minecraft-merged.jar` bytecode'u üzerinden `javap` ile doğrula)
  2. **Performans & FPS Düşürebilecek Yerler** (her frame/render/chunk adımında gereksiz nesne/liste/`String` tahsisi vb.)
  3. **UI / HUD / ClickGUI Tutarsızlıkları & Kullanılabilirlik Sorunları** (tema token uyumu, hitbox/scroll/odak, dar pencere taşması, HUD çakışması)
  4. **Ölü Kod & Gereksiz Tekrarlar (Temizlik)**
