# AutoCrafter Seçilmeyen Item Craftlama Hatası — Düzeltme Planı

## 1. Problem Özeti
`AutoCrafterHandler`, kullanıcının `Item Picker` ekranından seçmediği itemleri zaman zaman otomatik olarak craftlayabiliyor veya sonuç slotundan (Slot 0) envantere çekebiliyor.

---

## 2. Tespit Edilen Kök Nedenler (`AutoCrafterHandler.java`)

### Neden 1 — Slot 0 Kontrolsüz `QUICK_MOVE` (`AutoCrafterHandler.java:68-77`)
`tick()` başlangıcında `craftingMenu.getSlot(0).hasItem()` true ise, sonuç slotundaki eşyanın `autoCrafterTargets` içinde olup olmadığına bakılmaksızın doğrudan `QUICK_MOVE` (shift-click) yapılıyor.
- **Etki:** Oyuncu crafting masasına veya 2x2 envanter gridine manuel olarak seçili olmayan bir tarif koyduğunda (veya gridde kalan eski eşyalar geçerli bir sonuç oluşturduğunda), AutoCrafter bu eşyayı da otomatik olarak craftlayıp envantere çeker.

### Neden 2 — Gizli Legacy `TARGETS` Grup Anahtarları ve Çakışması (`AutoCrafterHandler.java:42-45, 160-176`)
`matchesTarget()` metodu, doğrudan seçili item ID kontrolünden sonra `TARGETS` listesini de tarıyor:
- `AutoCrafterItemPickerScreen` yalnızca gerçek Minecraft itemlerini (`BuiltInRegistries.ITEM`) listelediği için, eski sürümlerden veya `setAllAutoCraftTargets(true)` çağrısından config'de (`sestia.json`) kalmış **`"decraft"`**, **`"nuggets"`**, **`"raw_blocks"`**, **`"food_paper"`** gibi grup anahtarları **Item Picker arayüzünde görünmez**; ancak arka planda aktif kalmaya devam eder.
- Özellikle **`"decraft"`** anahtarı config'de kaldığında, AutoCrafter envanterdeki **`iron_block` eşyasını `9x iron_ingot` olarak geri çevirir** (kullanıcı yalnızca `Block of Iron` seçtiğini görse bile).
- Ayrıca `new CraftTarget("glowstone", "Glowstone", Set.of("glowstone", "quartz_block", "amethyst_block"))` tanımı yüzünden kullanıcı yalnızca **`glowstone`** seçtiğinde **`quartz_block`** ve **`amethyst_block`** tarifleri de onaylanır.

### Neden 3 — Tek Malzemeli Geri Dönüşüm (Decraft) Döngüsü (`AutoCrafterHandler.java:153-159, 180-192`)
Kullanıcı örneğin `iron_ingot` seçtiğinde, `iron_block -> 9x iron_ingot` tarifi de `resultId = iron_ingot` ürettiği için eşleşiyor. `wouldLoopSingleIngredient` yalnızca girdi itemi de (`iron_block`) seçiliyse engelliyor; `iron_block` seçili değilse oyuncunun envanterindeki blokları istemeden bozabiliyor.

### Neden 4 — Grid İçi Eşya / `handlePlaceRecipe` Senkronizasyonu (`AutoCrafterHandler.java:93-99, 125-131`)
`stackedContents` crafting gridindeki (`slots 1..gridSlots`) eşyaları da hesaba katıyor. Gridde başka bir eşya varken `handlePlaceRecipe` çağrıldığında, sunucu tarafında geçici veya kısmi yerleştirme sonucu Slot 0'da beklenmeyen bir çıktı oluşursa bir sonraki tick'te Neden 1 nedeniyle envantere çekilebiliyor.

---

## 3. Görev Dağılımı (Task Breakdown)

### Task 1 — Slot 0 Çıktı Doğrulaması (`backend-specialist` / `bug-fixer`)
- **Dosya:** `src/main/java/com/client/mod/AutoCrafterHandler.java`
- **Değişiklik:**
  - `tick()` başındaki `craftingMenu.getSlot(0).hasItem()` bloğunda (satır 68), `before` item'ının ID'sini (`BuiltInRegistries.ITEM.getKey(before.getItem()).getPath()`) kontrol et.
  - Eğer `before` item'ı aktif hedeflerle eşleşmiyorsa (`!isResultItemAllowed(itemId, config)`), `QUICK_MOVE` yapma; yalnızca AutoCrafter'ın kendi hedeflerini al.
- **Doğrulama:** Gridde seçili olmayan bir tarifin sonucu varken AutoCrafter açıkken shift-click atılmadığını test et.

### Task 2 — Legacy Grup Anahtarlarını Temizleme ve Sadece Seçili Item Eşleşmesi (`backend-specialist`)
- **Dosyalar:** `src/main/java/com/client/mod/AutoCrafterHandler.java`, `src/main/java/com/client/mod/ModConfig.java`
- **Değişiklik:**
  - `matchesTarget()` içinde `TARGETS` üzerinden otomatik genişleyen grup eşleşmesini (`decraft`, `nuggets`, `raw_blocks`, `food_paper`, `glowstone -> quartz_block/amethyst_block`) kaldır; yalnızca `config.isAutoCraftTargetEnabled(resultId)` (ve tek malzemeli döngü koruması) ile eşleşme yap.
  - `ModConfig.load()` / `copyFrom()` sırasında `autoCrafterTargets` içinde kalmış legacy grup ID'lerini (`decraft`, `nuggets`, `raw_blocks`, `food_paper`) temizle ki Item Picker sayacı ve seçili liste tutarlı olsun.
- **Doğrulama:** Config'de `"decraft"` kalsa bile `iron_block` seçiliyken `iron_ingot` craftlanmadığını; yalnızca `glowstone` seçiliyken `quartz_block`/`amethyst_block` craftlanmadığını doğrula.

### Task 3 — Birim Testleri (`test-engineer`)
- **Dosya:** `src/test/java/com/client/mod/AutoCrafterTargetMatchTest.java` (yeni test dosyası)
- **Değişiklik:**
  - `glowstone` seçiliyken `quartz_block` ve `amethyst_block` için `false` döndüğünü doğrulayan testler.
  - Seçili olmayan itemlerin `matchesTarget` / hedef doğrulama helper'ından geçmediğini doğrulayan testler.
- **Doğrulama:** `./gradlew test` (veya mevcut Gradle/Java test komutu) ile tüm testlerin geçtiğini doğrula.

### Task 4 — Dokümantasyon ve Sürüm Yayını (`documentation`)
- **Dosyalar:** `MODULLER.txt` (gerekirse AutoCrafter davranışı notu)
- **İşlem:** Derleme + `publish_update.ps1` + Git commit & push (implementasyon onaylandıktan sonra).

---

## 4. Doğrulama Kontrol Listesi (Phase X)
- [ ] Seçili olmayan bir item crafting gridine manuel konulduğunda AutoCrafter Slot 0'ı çekmiyor.
- [ ] `glowstone` seçiliyken `quartz_block` veya `amethyst_block` craftlanmıyor.
- [ ] Yalnızca `AutoCrafterItemPickerScreen` üzerinde işaretli itemler craftlanıyor.
- [ ] Tüm unit testler ve `publish_update.ps1` derlemesi hatasız geçiyor.
