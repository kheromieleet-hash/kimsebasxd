# Tasarım Dokümanı: Tek-JAR Bootloader & GitHub Releases Otomatik Güncelleyici

**Tarih:** 10 Kasım 2026  
**Durum:** Onaylandı (`kheromieallah/sestia`)  
**Hedef Sürüm:** Minecraft 26.3 • Fabric Loader `>=0.19.3` • Java `>=25`

---

## 1. Amaç (Intent & Why)

Kullanıcı client'ı arkadaşlarına **tek bir `.jar` dosyası** (`SCTClient.jar`) olarak verecek. Arkadaşları dosyayı yalnızca **ilk kurulumda 1 kez** `mods/` klasörüne koyacak. Bundan sonraki tüm güncellemelerde geliştirici buradan tek komutla yeni sürüm yayınlayacak; karşı taraf oyunu her başlattığında (PrismLauncher, Resmi Launcher vb. fark etmeksizin) client açılış sırasında güncellemeyi otomatik indirip doğrulayacak ve **oyunu kapatıp açmaya gerek kalmadan** aynı başlatmada güncel sürümle oyuna girecektir.

İnternet kesintisi veya sunucu erişim sorunu halinde client çökmeden önbellekteki son doğrulanmış sürümle güvenli şekilde açılacaktır. Tüm kullanıcı arayüzü metinleri (açılış indirme penceresi ve oyun içi bildirim) **İngilizce** olacaktır.

---

## 2. Değerlendirilen Yaklaşımlar (Approaches)

### Yaklaşım 1 (Seçilen): `LanguageAdapter` Bootloader + Dinamik Çekirdek JAR
- **Mekanizma:**
  - `mods/SCTClient.jar` yalnızca küçük bir önyükleyici (`com.client.loader.*`), `IAS` JiJ bağımlılığı (`META-INF/jars/IAS-9.0.8+26.3-fabric.jar`) ve çevrimdışı ilk açılış için gömülü `assets/sctclient/fallback-core.jar` dosyasını taşır.
  - `fabric.mod.json` üzerinde tanımlanan `SctBootstrapAdapter` (`net.fabricmc.loader.api.LanguageAdapter`), `FabricLoaderImpl.setupLanguageAdapters()` aşamasında — **yani `FabricMixinBootstrap.init()` ve Minecraft penceresi başlamadan hemen önce** — çalışır.
  - Güncel sürümü `.minecraft/sctclient/core/SCTClient-Core-<version>.jar` içine indirir, SHA-256 doğrulamasından geçirir ve `FabricLauncherBase.getLauncher().addToClassPath(coreJarPath)` ile sınıf yoluna (KnotClassLoader) ekler.
  - Ardından `FabricMixinBootstrap` tüm mixin JSON'larını (`customclient.mixins.json`, `inventoryhud.mixins.json`) ve sınıfları doğrudan güncel çekirdekten yükler; `CoreAssetsPackMixin` ise güncel `assets/` kaynaklarını yüksek öncelikli yerleşik kaynak paketi (`FilePackResources`) olarak oyuna bağlar.
- **Avantajlar:** Windows `.jar` dosya kilidi hatası olmaz, oyunu kapatıp yeniden başlatmak gerekmez, PrismLauncher `stdin` oturum protokolünü bozmaz, tüm launcher'larda tek `.jar` ile sorunsuz çalışır.
- **Takaslar (Trade-offs):** İlk dağıtımda `SCTClient.jar`'ın 1 kez `mods/` klasörüne konulması gerekir; `LanguageAdapter` katmanının kendisinde değişiklik gerekirse nadiren yeni `SCTClient.jar` dağıtımı gerekir (normal kod/UI/modül/doku güncellemeleri tamamen otomatiktir).

### Yaklaşım 2 (Elenen): `preLaunch` İçinden Kendini Güncelleyip Yeniden Başlatma
- **Elenme Nedeni:** Windows JVM açık `mods/*.jar` dosyasını kilitler. Ayrıca `PrismLauncher` (`NewLaunch.jar` / `org.prismlauncher.EntryPoint`) oturum ve kullanıcı argümanlarını `System.in` üzerinden aldığı için yeniden başlatılan süreç oturum parametrelerini kaybeder.

### Yaklaşım 3 (Elenen): Harici Mini Launcher (`.exe` / `.bat`)
- **Elenme Nedeni:** Ek program kullanımı gerektirir, imzasız `.exe` Windows SmartScreen uyarısı tetikler ve kullanıcı doğrudan kendi launcher'ından girerse güncelleme çalışmaz.

---

## 3. Kapsam, Kapsam Dışı ve Kısıtlar

- **Yayınlama Deposu:** `https://github.com/kheromieallah/sestia` (**Public** release-only repo; kaynak kod yüklenmez, yalnızca GitHub Releases varlıkları olarak `SCTClient-Core.jar`, `version.json` ve `SCTClient.jar` paylaşılır).
- **Kapsam (Goals):**
  1. Tek dosya dağıtımı (`SCTClient.jar`), karşı tarafta sıfır yeniden yapılandırma.
  2. `https://github.com/kheromieallah/sestia/releases/latest/download/...` yönlendirmesiyle GitHub REST API rate-limit'e (`60/saat`) takılmadan `version.json` ve `SCTClient-Core.jar` indirme.
  3. Rise 6 Luxury temalı **İngilizce** Java2D/Swing açılış indirme penceresi (`Checking`, `Downloading` hız/MB/%, `Ready`, `Offline`).
  4. Güncelleme sonrası oyun içinde (`TitleScreen` / `HUD` toast + ilk dünyaya girişte sohbet mesajı) **İngilizce** `"✦ SCT Client Updated to vX.X.X"` bildirimi.
  5. Çevrimdışı / zaman aşımı durumunda son doğrulanmış önbellek veya gömülü `fallback-core.jar` ile güvenli başlatma.
  6. `minLoaderCode > LOADER_CODE` uyumsuzluğunda **Engellemeyen Uyarı (Non-blocking warning)**: Client mevcut önbellekteki/gömülü çekirdekle oyuna girişe izin verir, ancak kullanıcıya yeni `SCTClient.jar` yüklemesi gerektiğini belirten bir İngilizce toast + sohbet uyarısı (`https://github.com/kheromieallah/sestia/releases/latest`) gösterir.
  7. Tek komutla paketleyen ve GitHub Releases'a yüklemeye hazır hale getiren `publish_update.ps1`.
- **Kapsam Dışı (Non-Goals):**
  - Kaynak kodunun karşı tarafa açılması.
  - İstemci içine gömülü GitHub PAT token'ı saklamak (token sadece geliştiricinin kendi ortamında `$env:GITHUB_TOKEN` veya `gh` CLI oturumu üzerinden kullanılır).
- **Karşı-Hedefler (Anti-Goals):**
  - İnternet yavaşken veya kesikken oyunun açılmasını engellememek (kısa timeout: 2.5 sn bağlanma, 4 sn okuma).
  - `SCTClient.jar` köküne `com/client/mod/*.class` veya `dlovin/**` koymamak (eski sınıfların yeni çekirdeği gölgelemesini önlemek için).

---

## 4. Teknik Tasarım ve Bileşenler

### 4.1. Dosya ve Dizin Yerleşimi (Kullanıcı Bilgisayarı)
- `mods/SCTClient.jar` — Kalıcı bootloader mod dosyası.
- `.minecraft/sctclient/` (Fabric `getGameDir().resolve("sctclient")`):
  - `core/SCTClient-Core-<version>.jar` — Aktif doğrulanmış çekirdek sürümü.
  - `state.json` — Aktif kurulu sürüm (`versionCode`, `version`, `sha256`, `coreFile`).
  - `pending-notice.json` — Bir sonraki açılışta oyun içi toast/mesaj göstermek için yazılan tek seferlik bildirim kaydı (`type`: `"UPDATED"` veya `"LOADER_OUTDATED"`, `version`, `previousVersion`, `changelog`, `releaseUrl`).
  - `updater.properties` — İsteğe bağlı URL override (yerel test veya ayna için: `manifestUrl=...`).

### 4.2. Uzak Manifest Formatı (`version.json`)
```json
{
  "versionCode": 2,
  "version": "1.0.2",
  "changelog": "New ClickGUI & HUD improvements",
  "downloadUrl": "https://github.com/kheromieallah/sestia/releases/latest/download/SCTClient-Core.jar",
  "sha256": "<64-char lowercase hex>",
  "sizeBytes": 6812340,
  "minLoaderCode": 1,
  "loaderUrl": "https://github.com/kheromieallah/sestia/releases/latest"
}
```
- **Güncelleme Kararı:**
  - `remote.minLoaderCode > SctUpdateConfig.LOADER_CODE` ise yeni çekirdek indirilmez; önbellekteki/gömülü çekirdek yüklenir ve `pending-notice.json` dosyasına `type = "LOADER_OUTDATED"` yazılır (Engellemeyen Uyarı).
  - Aksi halde `remote.versionCode > local.versionCode` ise (veya `localCoreJar` eksik/bozuksa) indirme başlar. `remote.versionCode == local.versionCode` ve SHA-256 eşleşiyorsa ek dosya indirilmez, oyun ~0.3 sn içinde açılır.
- **Bütünlük Doğrulama:** İndirme `.part` geçici dosyasına yapılır; SHA-256 eşleşmezse `.part` silinir ve güvenli şekilde mevcut önbelleğe düşülür.
- **Temizlik:** Başarılı yeni çekirdek kurulumundan sonra önceki `SCTClient-Core-*.jar` dosyaları temizlenir.

### 4.3. Java Sınıf Mimarisi
1. **Bootloader (`com.client.loader` — yalnızca `SCTClient.jar` içinde):**
   - `SctUpdateConfig`: `LOADER_CODE = 1`, `DEFAULT_MANIFEST_URL = "https://github.com/kheromieallah/sestia/releases/latest/download/version.json"`, `DEFAULT_RELEASES_URL = "https://github.com/kheromieallah/sestia/releases/latest"`, `-Dsct.manifestUrl` JVM argümanı ve `sctclient/updater.properties` okuma desteği.
   - `SctBootstrapAdapter` (`implements LanguageAdapter`): `setupLanguageAdapters()` içinde tek seferlik başlatılır; çekirdeği hazırlar, `FabricLauncherBase.getLauncher().addToClassPath(corePath)` ile yükler ve aktif core yolunu `System.setProperty("sct.core.jar.path", ...)` üzerinden çekirdeğe aktarır; `create(...)` çağrılarını standart reflection (`DefaultLanguageAdapter` mantığı) ile sınıfa yönlendirir.
   - `SctUpdater`: HTTP istemcisi (`java.net.http.HttpClient`, redirect `NORMAL`, timeout kontrollü), JSON ayrıştırıcı (`com.google.gson.Gson` — Launch classpath'te mevcut), SHA-256 doğrulama (`MessageDigest`) ve önbellek/fallback yönetimi.
   - `SctUpdateWindow`: İndirme gerekiyorsa (veya kontrol >350 ms sürerse) EDT üzerinde açılan, `RoundRectangle2D` kenarlı, `#0B0D19` arka planlı, `#C084FC -> #F472B6` degrade şeritli Rise 6 Luxury Swing penceresi (tüm etiketler İngilizce). `GraphicsEnvironment.isHeadless()` kontrolü ile güvenli kapatma (`dispose()`).
2. **Çekirdek (`com.client.mod` — yalnızca `SCTClient-Core.jar` içinde):**
   - `ClientBuildInfo`: Derleme sırasında `sct-core-meta.properties` kaynağından aktif sürümü (`version`, `versionCode`) okur; `ModernClickGuiScreen` ve `StatsHudRenderer` üzerindeki sürüm rozetini dinamik gösterir.
   - `UpdateNoticeManager`: Başlangıçta `.minecraft/sctclient/pending-notice.json` kaydını okuyup tüketir; hem ekranlarda (`ScreenMixin` `extractRenderState` TAIL) hem oyun içinde (`HudOverlayMixin` `extractRenderState` TAIL) 7 saniyelik Rise 6 Toast (`✦ SCT Client Updated to vX.X.X` veya `⚠ New SCTClient.jar Loader Required`) gösterir, ayrıca dünyaya ilk girişte (`ClientMod` tick içinde `client.gui.hud.getChat().addClientSystemMessage(...)` ile) tek satırlık İngilizce bilgilendirme yazar.
   - `CoreAssetsPackMixin`: `ModResourcePackCreator.loadPacks` içerisine `SCTClient-Core-<ver>.jar` dosyasını `FilePackResources.FileResourcesSupplier` + `PackSelectionConfig(true, Position.TOP, true)` ile ekleyerek ileride değişecek doku/font/dil dosyalarının da otomatik yüklenmesini sağlar.

### 4.4. Geliştirici Yayınlama Pipeline'ı (`publish_update.ps1`)
- Tek komutla (`-Version`, `-VersionCode`, `-Changelog`, `-Upload`):
  1. `com.client.loader` ve `com.client.mod` (+ `dlovin`) sınıflarını ayrıştırılmış staging klasörlerinde derler.
  2. `sct-core-meta.properties` dosyasını `SCTClient-Core.jar` içine gömer.
  3. `dist/SCTClient-Core.jar`, SHA-256 hesaplaması ile `dist/version.json` ve içinde güncel `assets/sctclient/fallback-core.jar` barındıran `dist/SCTClient.jar` çıktılarını üretir.
  4. `SCTClient.jar` dosyasını yerel `Desktop`, `Downloads` ve `PrismLauncher\instances\26.3\minecraft\mods\` içine kopyalar.
  5. `-Upload` verilirse `$env:GITHUB_TOKEN` (veya `gh release create`) kullanarak `kheromieallah/sestia` deposuna yeni release tag'i oluşturup `SCTClient-Core.jar`, `version.json` ve `SCTClient.jar` varlıklarını yükler.
