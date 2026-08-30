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
| 4 | YouTube | ⛔ kapsam dışı bırakıldı — [gerekçe](ARCHITECTURE-REVIEW.md#i-youtube-neden-kapsam-dışı) |
| 5 | Baseline Profile, EPG (XMLTV), Assistant ile sesli arama, kanal önizleme, altyazı/ses seçimi | ✅ |

Mimari incelemesi ve dokümandan sapmaların gerekçeleri:
[`ARCHITECTURE-REVIEW.md`](ARCHITECTURE-REVIEW.md).

## Kurulum

TV'ye kurmak için: **[INSTALL.md](INSTALL.md)**

Derleme:

```bash
./gradlew assembleDebug
./gradlew :data:health:test :core:model:test :data:sync:test   # 100 test, emülatör gerekmez
./gradlew :baselineprofile:generateBaselineProfile              # rootlu emülatör/userdebug cihaz gerekir
./gradlew :app:connectedAndroidTest            # D-pad odak regresyon testleri
```

Android SDK 36 ve JDK 17 gerekir.

CI (`.github/workflows/ci.yml`) iki iş çalıştırıyor: birim testleri ve
`assembleDebug` + lint. İkincisi, bu depo yazılırken doğrulanamayan tek şeyi —
Room/KSP, Hilt ve Compose kod üretimini — kapsıyor.

## Ekranlar

```
Home        Mod satırı · Devam et · Favoriler · Öne çıkan ülkeler
Browse      Ülke/kategori çekmecesi + kanal ızgarası (paging) + odak önizlemesi
Search      Son aramalar · sık izlenenler · sesli arama · alfabetik ızgara klavye
Player      Tam ekran · zap · kanal çekmecesi · altyazı/ses seçimi · şimdi/sıradaki
Favorites   Favori ızgarası, uzun basma ile çıkarma
Radio       Ülke çekmecesi + istasyon listesi, MediaSessionService ile arka planda çalma
Settings    Filtreler · sağlık yoğunluğu · önizleme · katalogu yenile
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

## EPG

`guides.json` hangi XMLTV kaynağının hangi kanalı kapsadığını söylüyor; senkronizasyon
kanal başına değil **kaynak başına** indiriyor — bir XMLTV dosyası genelde bütün bir
ülkeyi kapsadığı için bu, iki yüz indirmeyi bire düşürüyor.

Ayrıştırıcı SAX tabanlı ve akış halinde çalışıyor: ulusal bir rehber onlarca megabayt
ve yüz binlerce `<programme>` öğesi içerir, hepsini belleğe almak TV kutusunun sahip
olduğundan fazla heap ister. Bozuk bir girdi tüm belgeyi düşürmüyor — halka açık
rehberlerin kalitesi çok değişken ve tek bir hatalı zaman damgası yüzünden bir ülkenin
tüm yayın akışını kaybetmek kabul edilemez.

Zaman damgaları XMLTV'nin `YYYYMMDDHHMMSS +HHMM` biçiminde; offset'i yanlış işlemek tüm
akışı saatlerce kaydırır, bu yüzden `XmltvTime` elle yazıldı ve kapsamlı test edildi.

Saklama penceresi dar tutuldu (1 gün geriye, 2 gün ileriye) — `programmes` uygulamanın
en hızlı büyüyen tablosu ve dünün akışının hiçbir faydası yok.

## Altyazı ve ses parçası

Sistemin altyazı tercihi (`CaptioningManager`) uygulanıyor — TV'de erişilebilirlik
ayarları telefondakinden daha çok kullanılıyor, altyazı ortak oturma odasında yaygın
bir özellik. Dil eşleşmesi ana alt etikete göre: IPTV yayınları `tur`/`tr`/`tr-TR`
etiketlerini birbirinin yerine kullanıyor, tam eşleşme çoğu gerçek yayında hiçbir şey
seçmez.

## Kanal önizleme

Odaklanınca hemen değil, 1,2 sn bekledikten sonra. Beklemesiz haliyle bir sıra boyunca
yürümek kart başına bir bağlantı açıp terk eder. Önizleme yalnızca `OK` durumundaki
yayınları kullanıyor ve ses kod çözücü seviyesinde kapalı — sadece susturulmuş bir
oynatıcı yine ses odağı alır ve kullanıcının dinlediği radyoyu susturur.

## Metinler

Arayüz metinleri, onları çizen modülün kendi `res/values/strings.xml` dosyasında —
`nonTransitiveRClass` bunu gerektiriyor ve bir özellik modülünü kendi kendine yeterli
tutuyor. `:app` yalnızca framework'ün ada göre okuduklarını (`app_name`, arama ipucu)
barındırıyor.

`:core:model` ve `:data:health` saf Kotlin modülleri olduğu için hiç metin içermiyor:
dil etiketi çözümlenemediğinde `TrackPreferences.labelFor` null döndürüyor ve nasıl
adlandırılacağına arayüz karar veriyor.

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
:baselineprofile      Macrobenchmark ile baseline profile üretimi
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
