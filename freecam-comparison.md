# Meteor Client vs LiquidBounce Nextgen — Freecam Modülü İnceleme ve Karşılaştırma Planı

## 1. Amaç
Meteor Client (`Freecam.java`) ve LiquidBounce Nextgen (`ModuleFreeCam.kt`) içindeki Freecam mimarilerini inceleyip karşılaştırmak; ardından **Sestia Client** için en temiz, anticheat-güvenli ve akıcı Freecam tasarımını belirlemek.

---

## 2. Mimari Karşılaştırma

| Özellik / Boyut | Meteor Client (`Freecam`) | LiquidBounce Nextgen (`FreeCam`) | Sestia Client İçin Değerlendirme |
| :--- | :--- | :--- | :--- |
| **Kamera Ayrıştırma Modeli** | Saf **sanal kamera koordinatı** (`pos`, `prevPos`, `yaw`, `pitch` + tick interpolasyonu). Fake entity spawn etmez. | **Kamera/pozisyon soyutlaması** (Nextgen'de kamera konumu ve rotasyonu oyuncu entity'sinden bağımsız güncellenir; legacy'de fake player kullanılırdı). | **Sanal kamera koordinatı** (`CameraMixin` + `prevPos`/`pos` lerp) en hafif ve side-effect'siz yöntemdir. |
| **Oyuncu Hareketini Durdurma** | `LocalPlayer` inputlarını sıfırlar; hareket tuşları yalnızca sanal kamera vektörünü günceller. Oyuncu sunucuda sabit kalır. | Oyuncu inputlarını/hareket paketlerini kilitler veya yönlendirir (`AllowRotation` / `AllowInteract` seçeneklerine göre). | `LocalPlayerMixin` / `EntityMixin` üzerinden oyuncu hareketini kilitleyip kamera vektörünü sürmek en temizidir. |
| **Rotasyon & Etkileşim (Interact)** | `rotate` ayarı ile bakışın oyuncu entity'sine yansıyıp yansımayacağı seçilir; blok/varlık etkileşimi genelde kamera veya oyuncu pozisyonundan yönetilir. | **`AllowRotation`** ve **`AllowInteract`** (veya raycast kaynağı) seçenekleriyle kamera gezerken karakterin dönmesi/etkileşimi ayrı kontrol edilir. | Varsayılan olarak sunucuya rotasyon/hareket paketi göndermemek (Grim/NCP uyumu için) en güvenlisidir. |
| **Hız Kontrolü** | `speed` + `speedScrollSensitivity` (fare tekerleği ile anlık hız ayarı). | `Speed` ayarı (yatay/dikey hız). | `speed` slider + opsiyonel scroll hızı çok pratik olur. |
| **Render & Cave Culling** | `LevelRenderer` chunk/cave culling'i devre dışı bırakarak yeraltını/duvar arkasını görmeyi sağlar; oyuncunun kendi modeli 3. şahıs gibi görünür. | Kamera ayrıldığında oyuncunun kendi modelini renderlar ve chunk culling'i yönetir (`FirstPerson` eli gizlenir). | `LevelRendererMixin` (culling bypass) + `FirstPersonHandsMixin` / `FirstPersonRendererMixin` zaten mevcut. |
| **Güvenlik / Oto-Kapatma** | `toggleOnDamage`, `toggleOnDeath`, `toggleOnLog` ve `reloadChunks` seçenekleri vardır. | Dünya değişimi / ölüm / hasar durumlarında takılı kalmayı önleyen state reset mekanizmaları içerir. | Hasar alınca veya dünya/sunucu değişince otomatik kapanma mutlaka olmalı. |

### Özet Farklar
1. **Meteor Client yaklaşımı** daha **render/utility odaklıdır**: fake entity oluşturmaz, `prevPos -> pos` interpolasyonu ile 144Hz+ ekranlarda bile yağ gibi akıcıdır; fare tekerleğiyle hız ayarı (`speedScrollSensitivity`) ve `toggleOnDamage`/`toggleOnLog` gibi yaşam kalitesi ayarları çok iyidir.
2. **LiquidBounce Nextgen yaklaşımı** daha **modüler ve anticheat/combat farkındalıklıdır**: kamera ile oyuncu rotasyonu/etkileşimi (`AllowRotation`, `AllowInteract`) birbirinden net ayrılır; GUI/ekran geçişlerinde ve dünya değişimlerinde kamera state'inin takılı kalmaması üzerine ekstra korumalar barındırır.

---

## 3. Sestia Client Mevcut Altyapısı (İnceleme)
Sestia Client'ta Freecam için gereken temel mixin kancalarının çoğu zaten hazır durumda:
- `src/main/java/com/client/mod/mixin/CameraMixin.java` — Kamera pozisyonu ve açısı override
- `src/main/java/com/client/mod/mixin/EntityMixin.java` & `LocalPlayerMixin.java` — Oyuncu hareket/input kontrolü
- `src/main/java/com/client/mod/mixin/MouseHandlerMixin.java` & `KeyboardHandlerMixin.java` — Bakış ve scroll/tuş yakalama
- `src/main/java/com/client/mod/mixin/LevelRendererMixin.java` — Cave/occlusion culling kontrolü
- `src/main/java/com/client/mod/mixin/FirstPersonRendererMixin.java` / `FirstPersonHandsMixin.java` — Birinci şahıs el/eşya gizleme

---

## 4. Önerilen Sestia Freecam Tasarımı (Implementasyon Planı)

### Task 1 — Çekirdek `FreecamHandler` (Meteor + LiquidBounce Hibriti)
- **Dosya:** `src/main/java/com/client/mod/FreecamHandler.java` (yeni)
- **Sorumluluk:**
  - Meteor tarzı `pos` / `prevPos` / `yaw` / `pitch` sanal kamera state'i ve `tickDelta` interpolasyonu.
  - LiquidBounce tarzı temiz state reset (`onDisable`, dünya/sunucu değişimi, ölüm/hasar kontrolü).
  - Sunucuya ekstra hareket/rotasyon paketi göndermeden yalnızca kamera yönünde 3D uçuş vektörü hesaplama.

### Task 2 — Mixin Entegrasyonları
- **Dosyalar:** `CameraMixin.java`, `EntityMixin.java`, `LocalPlayerMixin.java`, `MouseHandlerMixin.java`, `LevelRendererMixin.java`, `FirstPersonHandsMixin.java`
- **Sorumluluk:**
  - `Camera`: Freecam aktifken `setPosition` / `setRotation` ile sanal kamerayı uygula, oyuncunun kendi bedenini görünür kıl (`detached = true`).
  - `MouseHandler` / `LocalPlayer`: Fare hareketini ve WASD/Space/Shift inputlarını oyuncudan alıp `FreecamHandler`'a yönlendir.
  - `LevelRenderer`: Freecam aktifken duvar/mağara culling'ini kapat (`smartCull = false`).

### Task 3 — ModConfig & UI Ayarları
- **Dosyalar:** `ModConfig.java`, `ModernModuleSettings.java`, `ClickGuiScreen.java`, `MODULLER.txt`
- **Ayarlar:**
  - `Speed` (0.2x – 5.0x)
  - `Toggle on Damage` (hasar alınca kapat)
  - `Allow Interact` (opsiyonel: kameradan vs oyuncudan etkileşim)

---

## 5. Doğrulama Kontrol Listesi (Phase X)
- [ ] Freecam açıldığında oyuncu sunucuda hareket etmiyor ve anticheat flag yemiyor.
- [ ] Kamera yüksek FPS'te titreme yapmadan (`prevPos` -> `pos` lerp) hareket ediyor.
- [ ] Blokların/mağaraların içi net görünüyor ve 1. şahıs eli gizleniyor.
- [ ] Hasar alınca veya sunucudan çıkınca kamera hatasız oyuncuya dönüyor.
