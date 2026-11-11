# Sestia Client Chat - Cloudflare Worker

Ücretsiz Cloudflare Workers + SQLite Durable Objects (WebSocket Hibernation API) tabanlı gerçek zamanlı client chat sunucusu.

## Kurulum ve Yayınlama (1 Dakika)

1. Terminalden bu klasöre geç:
   ```powershell
   cd cloudflare-chat
   npm install
   ```
2. Cloudflare hesabınla giriş yap:
   ```powershell
   npx wrangler login
   ```
3. *(İsteğe bağlı ama önerilen)* Yabancıların bağlanmasını önlemek için oda anahtarı/şifresi belirle:
   ```powershell
   npx wrangler secret put CHAT_ACCESS_KEY
   ```
4. Worker'ı yayınla:
   ```powershell
   npm run deploy
   ```
   Çıktıda şu tarz bir adres göreceksin: `https://sestia-client-chat.<kullanici>.workers.dev`

## Oyun İçinden Bağlanma

1. Sestia ClickGUI -> **`MISC`** -> **`Client Chat`** aç.
2. Oyun içi sohbette adresini kaydet (`https://` yazsan da otomatik `wss://` çevrilir):
   ```text
   .c url wss://sestia-client-chat.<kullanici>.workers.dev
   ```
3. Eğer `CHAT_ACCESS_KEY` koyduysan:
   ```text
   .c key <belirledigin_sifre>
   ```
4. Yazışmaya başla:
   - `.c selam millet` -> Odadaki herkese mesaj yollar
   - `.c msg <oyuncu> <mesaj>` -> Özel mesaj (DM) atar
   - `.c list` -> Odadaki çevrimiçi kullanıcıları listeler
   - `.c room <oda_adi>` -> Özel odaya geçer (varsayılan: `global`)
   - `.c status` -> Bağlantı durumunu gösterir
