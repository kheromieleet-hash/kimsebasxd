# 📐 Tasarım Dokümanı: Modern Single-Panel ClickGUI & Gelişmiş ESP Sistemi

**Tarih:** 10 Kasım 2026  
**Durum:** Taslak (Kullanıcı Onayına Sunuldu)  
**Kapsam:** Orta/Büyük Ölçekli Görsel & Modül Geliştirmesi  

---

## 1. Amaç ve Neden (Intent & Why)

İstemcinin mevcut çoklu pencereli (LiquidBounce tarzı) ClickGUI'sini ve kaldırılan ESP modülünü; modern, derli toplu, kullanımı son derece akıcı ve görsel olarak zengin **Tek Parça Modern Panel (Rise / Lunar / Modern Dashboard tarzı)** mimarisiyle yeniden inşa etmek. 

Kullanıcı, ekranın ortasında açılan sabit bir ana panel üzerinden tüm kategorileri sol sekmeden hızlıca yönetebilecek, özellikle **ESP** kategorisinde 2D Box, Can Barı, İskelet, İsimlik ve Renk seçimlerini canlı ve detaylı bir kontrolle ayarlayabilecektir.

---

## 2. Değerlendirilen Yaklaşımlar (Approaches)

### Yaklaşım 1 (Seçilen): Tek Parça Modern Dashboard (Single-Panel with Sidebar)
- **Açıklama:** Ekranın merkezinde açılan (860x540 px ölçeklenebilir) koyu cam efektli ana pencere. Sol tarafta kategori sekmeleri (`Combat`, `Movement`, `Render`, `⭐ ESP Settings`, `World`, `Player`, `Exploit`, `Settings`), sağ tarafta modül kartları ve ESP için özel önizleme/ayar alanı.
- **Avantajlar:** Çok temiz, modern, kafa karıştırmayan yapı; arama çubuğu ve önizleme desteği; ayarlar tek ekranda organize.
- **Dezavantajlar:** Pencereler ayrı ayrı ekrana dağıtılamaz (ancak ekranı kirletmediği için bu tercih edilmektedir).

### Yaklaşım 2 (Elenen): Çoklu Sürüklenebilir Pencereler (LiquidBounce Multi-Window)
- **Açıklama:** Her kategorinin ekranda bağımsız pencereler olarak uçuştuğu mevcut yapı.
- **Elenme Nedeni:** Ekranı kalabalıklaştırıyor ve ESP gibi çok sayıda alt parametresi olan modüllerin ayarlanmasını zorlaştırıyor.

---

## 3. Hedefler, Kapsam Dışı ve Karşı-Hedefler

### Hedefler (Goals)
1. **Modern ClickGUI (`ModernClickGuiScreen.java`):**
   - Sol kenar çubuğu (Sidebar) ile kategoriler arası anında geçiş.
   - Modül arama çubuğu (Search bar) ile hızlı filtreleme.
   - Akıcı renk geçişleri, modern anahtar (switch) animasyonları ve yumuşak sürgüler (sliders).
2. **Gelişmiş ESP Modülü (`EspRenderHandler.java`):**
   - **2D Box ESP:** Oyuncuların etrafına mesafe ölçekli parlak köşe/kutu çizgileri.
   - **Can Barı (Health Bar):** Kutu yanında yeşil/sarı/kırmızı dinamik can doluluğu.
   - **İskelet (Skeleton ESP):** Oyuncuların kafa, kol ve bacak eklem açılarını gösteren çizgiler.
   - **Gelişmiş İsimlikler (Nametags):** Duvar arkasından net okunan, can ve mesafe belirten etiketler.
   - **Renk Paleti Seçimi:** Kırmızı, Camgöbeği (Cyan), Yeşil, Mor (Purple) ve Altın Sarısı.
3. **Mevcut Modüllerin Korunması:**
   - Vulcan uyumlu FastBreak, AbortBreaking, NoPitchLimit, Version Switcher, Keybinds HUD ve Dulkir animasyonları eksiksiz çalışmaya devam eder.

### Kapsam Dışı (Non-Goals)
- Ağır harici tarayıcı motorları (CEF/Chromium) eklemek (Minecraft native GUI ve render motoru kullanılarak sıfır FPS kaybı hedeflenir).

### Karşı-Hedefler (Anti-Goals)
- Performansı düşürmemek, FPS drop yaratmamak ve mevcut tuş atamalarını (Keybinds) sıfırlamamak.

---

## 4. Teknik Mimari ve Dosya Değişiklikleri

1. **`src/main/java/com/client/mod/ModernClickGuiScreen.java`:**
   - Yeni nesil dashboard arayüzü, sekmeler, arama motoru, animasyonlu switch ve renk seçiciler.
2. **`src/main/java/com/client/mod/EspRenderHandler.java`:**
   - Oyun içi 3D dünyada varlıkların pozisyonlarını ekran koordinatlarına projekte eden ve 2D Box / Can Barı / İskelet çizen yüksek performanslı render sistemi.
3. **`src/main/java/com/client/mod/ModConfig.java`:**
   - ESP ayar bayrakları (`espEnabled`, `espBox`, `espHealth`, `espSkeleton`, `espNametags`, `espColor`) ve persistans desteği.
4. **`src/main/java/com/client/mod/mixin/KeyboardHandlerMixin.java` & `MinecraftMixin.java`:**
   - Sağ Shift (Right Shift) tuşunun yeni Modern ClickGUI'yi açmasını sağlamak.

---

## 5. Sıradaki Adım (Next Step)
Kullanıcı onayından sonra `superpowers:writing-plans` devreye alınarak görev adım adım parçalara ayrılacak ve kodlama aşamasına geçilecektir.
