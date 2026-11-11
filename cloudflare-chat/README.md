# Sestia Client Chat - Cloudflare Worker

Ücretsiz Cloudflare Workers + SQLite Durable Objects (WebSocket Hibernation API) tabanlı gerçek zamanlı tek ortak global client chat sunucusu. Oda veya şifre yoktur; Sestia Client ile oyuna giren herkes doğrudan aynı sohbete bağlanır.

## Kurulum ve Yayınlama (1 Dakika)

1. Terminalden bu klasöre geçip bağımlılıkları yükle:
   ```powershell
   cd c:\Users\PC\Desktop\client\cloudflare-chat
   npm install
   ```
2. Cloudflare hesabınla giriş yap:
   ```powershell
   npx wrangler login
   ```
3. Worker'ı yayınla:
   ```powershell
   npm run deploy
   ```
   Çıktıda şu tarz bir adres göreceksin: `https://sestia-client-chat.<kullanici>.workers.dev`

Bu adresi Sestia istemcisine varsayılan URL olarak eklediğinde oyuna giren herkes hiçbir ayar yapmadan otomatik bağlanır.

## Oyun İçi Komutlar

- `.c <mesaj>` -> Tüm Sestia kullanıcılarına mesaj gönderir
- `.c msg <oyuncu> <mesaj>` -> Özel mesaj (DM) atar
- `.c list` -> Çevrimiçi Sestia kullanıcılarını listeler
- `.c mute <oyuncu>` / `.c unmute <oyuncu>` -> Kullanıcı susturma
- `.c status` / `.c help` -> Bağlantı durumu ve komut listesi
- `.c url <wss://...>` -> İsteğe bağlı özel sunucu adresi ayarlar (`.c url default` varsayılana döner)
