# TV'ye kurulum

## Önce: derlenip derlenmediğini doğrulayın

Bu depo, Android SDK'sının bulunmadığı bir ortamda yazıldı. Saf Kotlin modülleri
(`:core:model`, `:data:health`, XMLTV ayrıştırıcı) derlendi ve 101 testi geçiyor,
ancak **Android modülleri hiç derlenmedi**. Room/KSP, Hilt ve Compose kod üretimi
yalnızca gerçek bir derlemede ortaya çıkar.

İki yol var:

**A. GitHub Actions'ta derleyin (makinenize hiçbir şey kurmadan)**

`.github/workflows/ci.yml` her push'ta `assembleDebug` çalıştırıyor. Deponun
**Actions** sekmesinde `Assemble debug` işi yeşilse APK hazır demektir:
`worldtv-debug` artifact'ını indirin, içinden `app-debug.apk` çıkar.

**B. Yerelde derleyin**

Gerekenler: **JDK 17** ve **Android SDK 36** (Android Studio ile gelir).

```bash
git clone -b claude/proje-mimarisi-analizi-6upw0p https://github.com/saitguzel/world-tv
cd world-tv
./gradlew assembleDebug
```

Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

> Derleme hata verirse büyük olasılıkla eksik bir modül bağımlılığı veya bir Room/Hilt
> anotasyonudur. Hata mesajı hangi dosya ve hangi sembol olduğunu söyler; bu tür iki
> hata zaten bulunup düzeltildi (`0e40f09`), başkaları çıkabilir.

---

## TV'yi hazırlayın

Google TV / Android TV'de:

1. **Ayarlar → Sistem → Hakkında** → *Derleme* satırına **7 kez** basın.
   ("Artık geliştiricisiniz" mesajı çıkar.)
2. **Ayarlar → Sistem → Geliştirici seçenekleri** → **USB hata ayıklama**'yı açın.
   Ağ üzerinden kuracaksanız varsa **Kablosuz hata ayıklama**'yı da açın.
3. **Ayarlar → Uygulamalar → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar** →
   kullanacağınız uygulamaya (dosya yöneticisi, Downloader vb.) izin verin.
4. TV'nin IP adresini not edin:
   **Ayarlar → Ağ ve İnternet → (bağlı ağınız) → IP adresi**

---

## Kurulum yöntemleri

### 1. ADB ile ağ üzerinden (önerilen)

Bilgisayarınızda `adb` gerekir (Android SDK Platform Tools).

```bash
adb connect 192.168.1.42:5555        # TV'nin IP'si
# TV ekranında çıkan "USB hata ayıklamaya izin ver" uyarısını onaylayın
adb install -r app-debug.apk
```

Kurulduğunu doğrulayın ve başlatın:

```bash
adb shell pm list packages | grep worldtv
adb shell monkey -p com.worldtv.debug -c android.intent.category.LEANBACK_LAUNCHER 1
```

> Paket adı **`com.worldtv.debug`** — debug derlemesine `.debug` soneki ekleniyor,
> böylece bir release sürümüyle yan yana durabiliyor.

Kaldırmak için: `adb uninstall com.worldtv.debug`

### 2. USB bellek ile

1. `app-debug.apk` dosyasını bir USB belleğe kopyalayın.
2. TV'ye takın, bir dosya yöneticisi (örn. **X-plore**, **File Commander**) ile açın.
3. APK'ya tıklayın, "Bilinmeyen kaynaklar" iznini verin, kurun.

### 3. Downloader / Send files to TV

- **Send files to TV**: telefonunuza ve TV'nize kurun, APK'yı telefondan gönderin.
- **Downloader** (AFTV): APK'yı bir yere yükleyip (örn. GitHub release) TV'de doğrudan
  URL ile indirin.

---

## İlk açılış

Uygulama TV ana ekranında **Meridyen** olarak görünür (`LEANBACK_LAUNCHER` sayesinde).

İlk açılışta katalog boştur — arka planda iptv-org ve Radio Browser dizinleri
indirilir (~20 MB). Ana ekranda **"Katalog henüz indirilmedi"** görürseniz
**"Şimdi indir"**e basın. İndirme birkaç dakika sürebilir; bittiğinde kanallar görünür.

Kanalların yanındaki noktalar sağlık durumunu gösterir:

| İşaret | Anlamı |
|---|---|
| ● dolu yeşil | Doğrulandı, çalışıyor |
| ○ boş gri | Henüz kontrol edilmedi |
| ◎ sarı halka | Bölgesel kısıtlı olabilir |
| ● soluk | Şu an kullanılamıyor |

Sağlık motoru siz gezindikçe arka planda çalışır: baktığınız listedeki yayınlar
öncelikli kontrol edilir, çalışmayanlar birkaç saat içinde listeden gizlenir.

---

## Sistem gereksinimleri

- **Android 6.0 (API 23)** ve üzeri
- Android TV / Google TV arayüzü (telefon için tasarlanmadı — dokunmatik yok,
  her şey D-pad ile)
- ~150 MB boş alan (katalog veritabanı + logo önbelleği)

---

## Sorun giderme

**Uygulama ana ekranda görünmüyor**
Bazı launcher'lar sideload edilen uygulamaları ayrı bir "Uygulamalar" sekmesinde
gösterir. `adb shell monkey` komutuyla doğrudan başlatabilir veya
**Sideload Launcher** gibi bir uygulama kurabilirsiniz.

**`adb connect` bağlanmıyor**
TV ve bilgisayar aynı ağda olmalı. Kablosuz hata ayıklama kapalıysa
`adb tcpip 5555` için önce USB ile bağlanmanız gerekebilir.

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
Farklı bir imzayla derlenmiş eski bir sürüm kurulu. Önce
`adb uninstall com.worldtv.debug` çalıştırın.

**Kanallar açılmıyor**
Bazı yayınlar `Referer`/`User-Agent` gerektirir ve uygulama bunları katalogdan
gönderir; yine de açılmayanları sağlık motoru üç başarısız denemeden sonra gizler.
**Ayarlar → Tüm yayınları yeniden kontrol et** ile taramayı hızlandırabilirsiniz.

**Görüntü var ses yok / kod çözücü hatası**
Ucuz kutularda HEVC kod çözücü olmayabilir. Bu hatalar cihaza özgü kabul edilir:
yayın global olarak elenmez, yalnızca bu cihazda gizlenir.
