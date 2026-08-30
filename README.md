# WorldTV

Android TV / Google TV için canlı TV (IPTV) ve online radyo uygulaması.

Ayırt edici özelliği kanal sayısı değil, **kanalların açılması**: uygulama her
yayının sağlığını kendi izler, ölü yayınları listeden gizler ve bir yayın
patladığında aynı kanalın alternatifine sessizce geçer.

## Durum

| Faz | Kapsam | Durum |
|---|---|---|
| 1 | Modül yapısı, Room şeması, iptv-org senkronizasyonu, katalog UI, ExoPlayer | ✅ |
| 2 | Sağlık motoru: Kademe 1+2, durum makinesi, sweep worker, tembel doğrulama, oynatma anında düşürme | ✅ |
| 3 | Radyo + MediaSessionService, favoriler, arama, son izlenenler, ayarlar | ✅ |
| 4 | YouTube (WebView + IFrame API) | ⏳ şema hazır, UI yok |
| 5 | Baseline Profile, EPG, sesli arama | ⏳ |

Mimari incelemesi ve dokümandan sapmaların gerekçeleri:
[`ARCHITECTURE-REVIEW.md`](ARCHITECTURE-REVIEW.md).

## Kurulum

```bash
./gradlew assembleDebug
./gradlew :data:health:test :core:model:test   # 63 test, emülatör gerekmez
./gradlew :app:connectedAndroidTest            # D-pad odak regresyon testleri
```

Android SDK 36 ve JDK 17 gerekir.

## Ekranlar

```
Home        Mod satırı · Devam et · Favoriler · Öne çıkan ülkeler
Browse      Kalıcı ülke çekmecesi + kanal ızgarası (paging)
Search      Sesli arama → kademeli filtreleme → alfabetik ızgara klavye
Player      Tam ekran · zap (yukarı/aşağı) · yan kanal çekmecesi (sağ/sol)
Favorites   Favori ızgarası, uzun basma ile çıkarma
Radio       İstasyon listesi, MediaSessionService ile arka planda çalma
Settings    Filtreler · sağlık kontrolü yoğunluğu · katalogu yenile
```

Gezinme derinliği en fazla iki: her ekran ana ekrandan bir sıçrama uzakta.
`BACK` yalnızca ana ekranda ve yalnızca ikinci basışta uygulamadan çıkar.

## Zap deneyimi

Kullanıcı bir kanalı hangi listeden açtıysa yukarı/aşağı o listede gezinir —
Türkiye'ye bakarken zap Türkiye'de kalır, tüm katalogda değil. `ChannelQueue`
uçlarda başa sarar; kumandada listenin sonunda hiçbir şey olmaması uygulamanın
donduğu izlenimi verir.

Ardışık basışlar 300 ms debounce edilir (beş kanal atlayan kullanıcı için
aradaki dördü açılmaz) ve komşu kanalların manifest'i sağlık sondajı üzerinden
önden çekilir — aynı havuzlanmış bağlantı kullanıldığı için zap gecikmesinden
yaklaşık bir saniye kazandırır.

## Modüller

```
:app                  Giriş noktası, Hilt, navigasyon
:core:model           Saf Kotlin veri modeli — Android bağımlılığı yok
:core:common          Dispatcher'lar, ağ izleme, cihaz yetenekleri
:core:database        Room şeması, DAO'lar
:core:network         Retrofit servisleri, OkHttp istemcileri, streaming JSON
:core:designsystem    Tema, odaklanabilir bileşenler, kumanda tuş haritası
:data:health          ★ Sağlık kontrol motoru — saf Kotlin/JVM, hızlı testler
:data:repository      Repository implementasyonları, Room ↔ sağlık motoru köprüsü
:data:sync            Katalog senkronizasyonu, WorkManager işleri
:feature:*            catalog, player, radio, favorites, settings
```

`:core:model` ve `:data:health` bilinçli olarak Android'e bağımlı değil. Bu,
motorun testlerinin emülatörsüz milisaniyelerde koşmasını sağlar ve ileride
KMP'ye geçiş yolunu açık tutar.

## Sağlık motoru nasıl çalışır

Üç kademe, ucuzdan pahalıya:

1. **Manifest kontrolü** (~200 ms – 3 sn) — `Range: bytes=0-8191` ile GET,
   `HEAD` değil (IPTV CDN'lerinin çoğu HEAD'e 405 döner). Yanıt, yayının
   taşıma türüne göre değerlendirilir: HLS için `#EXTM3U`, DASH için `<MPD`,
   düz TS için 0x47 senkron baytı. RTSP/RTMP HTTP ile ölçülemez, dokunulmaz.
2. **Segment kontrolü** (~1–4 sn) — master playlist'ten en düşük bit hızlı
   variant, oradan ilk segment. 200 dönüp içi boş manifest yaygındır.
3. **Gerçek oynatma** — yalnızca kullanıcı gerçekten izlemeye çalıştığında.
   En güvenilir sinyal budur ve ağırlığı iki kat sayılır.

Eleme kuralı: **üç ardışık başarısızlık**, üstel aralıklarla (1 sa → 6 sa →
24 sa) yayıldığı için ~31 saatlik bir gözlem penceresi. Hiçbir kayıt silinmez;
`DEAD` olan 7 gün sonra yeniden denenir.

Ağ hatası (`Inconclusive`) sayaç artırmaz — kullanıcının interneti kesikken
tüm katalog ölü işaretlenmez.

## İçerik bildirimi

WorldTV hiçbir içerik barındırmaz veya yayınlamaz. Tüm yayın adresleri herkese
açık [iptv-org](https://github.com/iptv-org/iptv) ve
[Radio Browser](https://www.radio-browser.info/) dizinlerinden gelir.
iptv-org `blocklist.json` (DMCA ile kaldırılanlar) filtresi zorunlu olarak
uygulanır.
