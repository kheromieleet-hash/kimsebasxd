# Settings Sekmesine Item Notifier ve Ayarlarını Ekleme Planı

## Amaç & Kapsam
`Settings` sekmesini (`NavTab.SETTINGS`) kaydırılabilir (scrollable) yapıya geçirerek, **Better Chat** tekil aç/kapa kartlarının altına **Item Notifier** modülünü ve ayarlarını inline kartlar + `- / +` stepper butonları ile eklemek; ayrıca **Item Notifier**'ı `Modules -> Render` listesinden kaldırıp yalnızca `Settings` ve `GUI` sekmelerinden (+ `Open HUD Editor`) yönetilebilir hale getirmek.

## Tasarım Kararları
1. **Inline Kart + `- / +` Stepper Tasarımı (`Settings` Sekmesi):**
   - `Settings` sekmesinde iki bölüm yer alacak:
     - **Better Chat** (5 adet `ON/OFF` kartı: `Infinite History`, `Anti Clear`, `Click to Copy`, `Timestamps`, `Compact Chat`).
     - **Item Notifier**:
       - **Item Notifier (Enable)** kartı: `ON / OFF` toggle + `Reset` (konumu sıfırla: `itemNotifierX = -1, itemNotifierY = -1`) butonu.
       - **Display Duration** kartı: `-` / `+` stepper butonları (`0.5s` adım, `1.0s` – `10.0s` arası) ve ortasında güncel değer (`3.0s`).
       - **Max Items** kartı: `-` / `+` stepper butonları (`1` adım, `1` – `10` arası) ve ortasında güncel değer (`5`).
   - Kartlar dikey toplam yüksekliği aşacağı için `renderSettingsView` içinde `graphics.enableScissor(...)`, `this.contentTotalHeight` hesabı ve `renderScrollbarAt(...)` ile dikey scroll desteği sağlanacak.
2. **Modül Listesinden Kaldırma (`Modules -> Render`):**
   - `ClickGuiScreen.java` içindeki `Category.RENDER` listesinden `"Item Notifier"` kaydı kaldırılacak.
   - `ModernModuleSettings.java` içindeki `case "Item Notifier"` ve `buildItemNotifierControls` temizlenecek (artık `Settings` sekmesinden yönetilecek).
   - `GUI` sekmesindeki (`renderGuiView` / `handleGuiClick`) **Item Notifier** kartı ve `HudLayoutEditorScreen` içindeki sürükle-bırak + sağ tık `ON/OFF` desteği korunacak.

## Görev Dağılımı & Değişecek Dosyalar

| # | Dosya | Yapılacak Değişiklik | Ajan |
|---|-------|----------------------|------|
| 1 | `src/main/java/com/client/mod/ModernClickGuiScreen.java` | `renderSettingsView` içine scroll (`enableScissor` + `contentTotalHeight` + `renderScrollbarAt`) ve **Item Notifier** bölümü (Toggle+Reset kartı, `Duration` `-/+` stepper kartı, `Max Items` `-/+` stepper kartı) ekleme; `handleSettingsClick` içinde scroll offset'i hesaba katarak tüm buton tıklamalarını (`config.save()` ve `ItemNotifierHudRenderer.reset()` dahil) bağlama; yardımcı `drawStepperCard` ve `handleStepperCardClick` metotlarını ekleme. | `frontend-specialist` |
| 2 | `src/main/java/com/client/mod/ClickGuiScreen.java` | `Category.RENDER` içindeki `"Item Notifier"` modül kaydını kaldırma. | `frontend-specialist` |
| 3 | `src/main/java/com/client/mod/ModernModuleSettings.java` | Kullanılmayan `case "Item Notifier"` ve `buildItemNotifierControls` metodunu kaldırma. | `frontend-specialist` |

## Doğrulama Planı (Phase X)
1. **Derleme & Paketleme:** `.\publish_update.ps1 -Version "1.0.63" -VersionCode 63 -Changelog "..." -Upload` çalıştırarak 0 derleme hatası ile `sestia.jar` ve `sestia-core.jar` üretildiğini doğrulama.
2. **Fonksiyonel Kontroller:**
   - `Modules -> Render` altında artık `Item Notifier` görünmediğini doğrulama.
   - `Settings` sekmesinde mouse wheel ile yukarı/aşağı kaydırılabildiğini, Better Chat kartlarının ve alttaki Item Notifier kartlarının (`ON/OFF`, `Reset`, `Duration -/+`, `Max Items -/+`) `ModConfig` değerlerini güncelleyip kaydettiğini doğrulama.
   - `GUI` sekmesi ve `Open HUD Editor` üzerinden konum taşıma / sağ tıkla aç-kapa işlemlerinin senkron çalıştığını doğrulama.
3. **Git Push:** Değişiklikleri `master` dalına commit + pushlama.
