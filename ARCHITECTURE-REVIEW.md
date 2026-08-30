# Mimari İnceleme — WorldTV

Bu belge, `mimari.md` dokümanının kodlanmadan önceki incelemesidir. Genel yapı
sağlam ve doğrudan uygulanabilir; aşağıdaki maddeler uygulanırken **düzeltilmesi
gereken** noktalardır. Her maddede kodda ne yapıldığı belirtilmiştir.

Özet karar: **mimaride yapıyı bozan bir sorun yok, kodlandı.** Ancak sağlık
kontrol motorunun örnek kodunda katalogu ciddi biçimde budayacak dört hata
vardı; bunlar düzeltilerek uygulandı.

---

## A. Sağlık motorundaki kritik hatalar

### A1. Sadece HLS varsayımı — kataloğun önemli bir kısmını öldürür

Dokümandaki Kademe 1:

```kotlin
if (body.contains("#EXTM3U")) CheckResult.Alive(...)
else CheckResult.Dead(res.code, "not a playlist")
```

iptv-org kataloğu yalnızca HLS içermez: düz **MPEG-TS over HTTP**, **DASH
(.mpd)** ve **RTSP/RTMP/UDP** girdileri de vardır. Bu kontrol hepsini ilk
taramada `DEAD` işaretler. Üstelik `DEAD` gizlendiği için kullanıcı bunu bir
hata olarak bile göremez — kanallar sessizce yok olur.

**Yapılan:** `StreamKindDetector` URL'den taşıma türünü belirliyor; her tür
kendi kuralıyla değerlendiriliyor (HLS → `#EXTM3U`, DASH → `<MPD`, progressive
→ 0x47 TS senkron baytı veya `Content-Type`, RTSP/RTMP → HTTP ile
değerlendirilemez, `Inconclusive`). `StreamKindDetectorTest` bunu doğruluyor.

### A2. `readUtf8()` sınırsız okuma — takılma ve bellek şişmesi

```kotlin
val body = res.body.source().readUtf8()
```

`Range` başlığı gönderilse de **sunucuların önemli kısmı bunu yok sayar** ve
200 ile tüm gövdeyi döner. Canlı bir yayında bu gövdenin sonu yoktur:
`readUtf8()` EOF'a kadar okur, yani hiç dönmez ve bu arada bellek şişer. 8
saniyelik `callTimeout` sonunda iptal eder ama o süre boşa gider ve düşük
RAM'li kutularda OOM riski kalır.

**Yapılan:** Tüm okumalar sınırlı — `source.request(n)` + `buffer.readUtf8(min(n, size))`.
Manifest için 8 KB, segment sondajı için 2 KB.

### A3. `Alive` içinde manifest yok ama Kademe 2 onu kullanıyor

```kotlin
data class Alive(val latencyMs: Int, val variantCount: Int)   // 6.1
...
val result = if (r1 is CheckResult.Alive) checkSegment(stream, r1.body) else r1   // 6.3
```

`r1.body` diye bir alan yok — bu kod derlenmez. Kademe 2'nin manifesti tekrar
indirmesi de gereksiz bir tur demek.

**Yapılan:** `Alive.manifest: String?` eklendi, Kademe 1'in indirdiği gövde
Kademe 2'ye taşınıyor.

### A4. VOD ≠ ölü yayın

```kotlin
if (variantBody.contains("#EXT-X-ENDLIST")) return CheckResult.Dead(-1, "vod not live")
```

`#EXT-X-ENDLIST` içeren bir playlist **çalışır durumdadır**, sadece canlı
değildir. `Dead` demek onu üç denemede listeden silmek anlamına gelir.

**Yapılan:** `Alive(isLive = false)` olarak işaretleniyor; `HealthInfo.isVod`
alanında saklanıyor, kontrol aralığı 2 güne uzatılıyor, ama gizlenmiyor.

### A5. `GEO_BLOCKED` bir ölümsüzlük rozetine dönüşüyor

Dokümanda 403 ve 451 doğrudan `GEO_BLOCKED`, ve `GEO_BLOCKED` hiçbir zaman
`DEAD` olmuyor. Pratikte **403'ün büyük kısmı bölgesel engel değildir** —
süresi dolmuş token, eksik `Referer`, kapanmış origin. Sonuç: kalıcı olarak
bozuk her 403 yayını listede sonsuza kadar kalır ve kullanıcı ona tıklamaya
devam eder. Bu, uygulamanın ana vaadine (%95+ açılma) doğrudan zarar verir.

**Yapılan:**
- 451 → her zaman `GeoBlocked` (hukuki, net).
- 403 → yalnızca katalog `label` alanı bölgesel engel ima ediyorsa
  `GeoBlocked`; aksi halde normal `Dead`.
- `GEO_BLOCKED` de artık sayaç artırıyor; `GEO_TOLERANCE = 4` denemeden sonra
  `DEAD` oluyor. Test: `a permanently forbidden stream eventually stops
  claiming to be geo blocked`.

---

## B. Eşzamanlılık ve veri bütünlüğü

### B1. `hostThrottle.acquire(...)` — hem derlenmez hem sızdırır

```kotlin
hostThrottle.acquire(stream.url.host)   // String'in .host özelliği yok
```

Ayrıca `acquire` var, `release` yok. İlk timeout'ta permit sızar ve tarama
zamanla tıkanır.

**Yapılan:** `HostThrottle.withHostPermit(url) { }` — `Semaphore.withPermit`
üzerine kurulu, istisna ve iptalde de permit'i bırakıyor. `hostOf` OkHttp'nin
`HttpUrl` ayrıştırıcısını kullanıyor, RTSP gibi şemalar için elle parse'a
düşüyor. Test: `releases the permit when the body throws`.

### B2. `dao.upsertAll(updated)` — eşzamanlı yazmaları eziyor

Tarama sırasında katalog senkronizasyonu veya oynatma raporu araya girerse,
tüm satırı geri yazmak o değişiklikleri kaybettirir. Oynatma raporu
uygulamanın **en değerli sinyali** — onu kaybetmek pahalı.

**Yapılan:** `StreamDao.updateHealth(...)` yalnızca sağlık kolonlarını
güncelleyen hedefli bir `UPDATE`. Katalog senkronizasyonu da simetrik olarak
`upsertPreservingHealth` ile yalnızca katalog kolonlarını tazeliyor — resync
sağlık geçmişini **sıfırlamıyor**.

### B3. `HealthChecker`, `StreamDao`'ya doğrudan bağlı

Doküman "`:data:health` Android'e bağımlı olmayan saf Kotlin arayüzü ile"
diyor, ama örnek kod Room DAO'sunu ve `SystemClock`'u enjekte ediyor. İkisi de
modülü Android'e bağlar ve hızlı JVM testini imkânsız kılar.

**Yapılan:** `HealthStore` portu ve `TimeProvider` seam'i tanımlandı;
`:data:health` gerçekten saf Kotlin/JVM modülü. Room implementasyonu
(`RoomHealthStore`) `:data:repository`'de. Bu sayede motorun 29 birim testi
emülatörsüz, milisaniyelerde koşuyor.

---

## C. Veri modeli ve sorgular

### C1. `state` enum'u ordinal olarak saklanırsa 6.5'teki SQL sessizce boş döner

`s.state IN ('OK','UNKNOWN','GEO_BLOCKED')` yalnızca kolon TEXT ise çalışır.
Room varsayılan `TypeConverter`'ı yazılmazsa enum ordinal (INTEGER) saklanır ve
sorgu hiçbir satır döndürmez — **hata vermeden**.

**Yapılan:** `HealthColumns.state: String`, `StreamState.name` ile yazılıyor.
Bilinmeyen bir değer istisna atmak yerine `UNKNOWN`'a düşüyor.

### C2. `MIN(s.lastLatencyMs)` — kontrol edilmemiş yayınlar hep "en hızlı"

`lastLatencyMs = 0` "ölçüm yok" demek, "0 ms" değil. Ham `MIN()` bu yüzden her
kontrol edilmemiş kanalı listenin başına taşır.

**Yapılan:** `MIN(NULLIF(s.lastLatencyMs, 0))`.

### C3. `blocklist` tablosu Bölüm 5'te tanımlı değil ama 6.5'te sorgulanıyor

DMCA filtresi zorunlu; tablo eksikse sorgu derlenmez.

**Yapılan:** `BlocklistEntity` eklendi. Ayrı tablo olması önemli — katalog
senkronizasyonu kanalı yeniden eklese bile filtre ayakta kalır.

### C4. `hash(url)` birincil anahtar olarak çakışıyor

Aynı URL katalogda birden fazla kanal altında listelenebiliyor. Yalnızca URL
hash'lenirse bu satırlar tek satıra çöker ve ilki dışındaki kanallar yayınsız
kalır.

**Yapılan:** `StreamIdFactory.idFor(url, channelId)` — `sha1(channelId|url)`.

---

## D. UI katmanı

### D1. 8.6'daki "görünürlüğü dondur" kodu dondurmuyor

```kotlin
repo.channelsFor(countryCode)
    .distinctUntilChangedBy { list -> list.map { it.id } }
```

Bu, **yalnızca id listesi değişmediğinde** yeniden yayını engeller. Üyelik
değişimi zaten id listesini değiştirir, yani tam da engellemek istenen durum
geçer. Ayrıca `remember { flow }.collectAsStateWithLifecycle()` başlangıç
değeri olmadan derlenmez.

**Yapılan:** Amaç, kodun anlatmak istediği yolla — ama gerçekten çalışan
biçimde — sağlandı: `items(key = { it.id })` ile kararlı anahtar,
`Modifier.focusRestorer()` ile odak geri kazanımı, ve ölen öğeyi listeden
çıkarmak yerine `alpha = 0.35f` ile soluklaştırma (`HealthBadge.UNAVAILABLE`).
Öğe kaybolmadığı için odak sıçraması oluşmuyor.

### D2. `BringIntoViewSpec` sürüm kırılganlığı

`LocalBringIntoViewSpec` / `BringIntoViewSpec` hâlâ `@ExperimentalFoundationApi`
ve Compose Foundation sürümleri arasında imzası değişti. Doküman bunu
`TvLazyRow(pivotOffsets = ...)` yerine önerirken bu riski belirtmiyor.

**Yapılan:** Tek bir yerde (`rememberTvPivotSpec`) izole edildi; sürüm
değişirse düzeltilecek tek dosya orası.

### D3. `minSdk 23` ile `androidx.tv:tv-material` uyumlu, sorun yok

Kontrol edildi: tv-material ve Media3 `minSdk 21` istiyor. 23 güvenli.

---

## E. Güvenlik ve uyumluluk

### E1. "Cleartext yalnızca medya, API'ler HTTPS" tek başına manifestte ifade edilemez

IPTV origin'leri keyfi host'lar olduğu için `usesCleartextTraffic` uygulama
genelinde açık olmak zorunda; `domain-config` "bunlar hariç her şey"
diyemez.

**Yapılan:** Manifest genelinde cleartext açık, ama API OkHttp istemcisine
`HttpsOnlyInterceptor` eklendi — katalog çağrıları kodda HTTPS'e zorlanıyor.
Bilinen API host'ları ayrıca `network_security_config.xml`'de cleartext'e
kapatıldı (kuşak + kemer).

### E2. `blocklist.json` filtresi ve içerik bildirimi

Doküman doğru vurguluyor. Uygulandı: anti-join sorguda, bildirim ayarlar
ekranında.

---

## F. Kabul edilen ama değiştirilen tasarım kararları

| Doküman | Uygulanan | Gerekçe |
|---|---|---|
| `HealthSweepWorker` 10 dk bütçe | 8 dk | WorkManager 10 dk'da işi öldürür; öldürülen worker o anki batch'i kaybeder. 8 dk güvenli pay bırakır. |
| Batch içinde `while` döngüsü koşulsuz | Öncelik kovaları sırayla | Favoriler → son izlenenler → ülke → diğerleri; bütçe biterse sonraki periyot kaldığı kovadan sürer. |
| `MAX_PARALLEL` sabit 8/16 | `ActivityManager.MemoryInfo` + kullanıcı tercihi | Doküman zaten öneriyordu; ayarlar ekranına da bağlandı. |
| Oynatma hatası sayacı +2 | Aynı, ama sınıflandırmalı | `DECODING_*` cihaza özgü → global sayaç artmıyor, cihaz-yerel kara listeye giriyor. `NETWORK_*` → hiç sayılmıyor. |

---

## G. Uygulanmayanlar (bilinçli)

- **YouTube (Faz 4).** Dokümanın kendi tavsiyesi. Room tablosu
  (`youtube_streams`) v1 şemasında yer alıyor ki sonradan migration
  yazılmasın.
- **EPG, PiP, çoklu profil.** Bölüm 14'te zaten kapsam dışı.
- **Baseline Profile.** Gerçek cihazda üretilmesi gerekiyor.

---

## H. Doğrulama durumu

Bu depo yazılırken kullanılan ortamda **Android SDK ve Google Maven
(`dl.google.com`) ağ politikası tarafından engelli**. Bu nedenle:

- ✅ `:core:model` ve `:data:health` — kotlinc 2.x ile derlendi, **29 birim
  testin tamamı geçiyor** (durum makinesi, HLS ayrıştırma, URL sınıflandırma,
  host kısıtlama).
- ⚠️ Android modülleri (Room/KSP/Hilt/Compose) — bu ortamda derlenemedi.
  Android SDK'sı olan bir makinede `./gradlew assembleDebug` ilk çalıştırmada
  sürüm uyuşmazlıkları için `gradle/libs.versions.toml` içindeki `androidx.*`
  ve `agp` sürümlerinin doğrulanması gerekebilir; bu sürümler Maven
  Central'dan doğrulanamayan tek gruptur ve dosyada bu not düşülmüştür.
