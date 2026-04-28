# EnglishTube

Android app (Java) giúp người Việt học tiếng Anh qua video YouTube với phụ đề song ngữ Anh–Việt đồng bộ theo thời gian phát, tra từ nhanh, lặp/ghép câu, bookmark phụ đề và lịch sử xem — không cần đăng nhập.

## Tài liệu yêu cầu

Toàn bộ phạm vi và hành vi đã chốt nằm trong file SRS riêng (gửi cho repo owner). Tóm tắt tính năng MVP:

- WebView YouTube (`m.youtube.com`) + thanh điều hướng (Back / Forward / Reload / Home).
- Player tự xây: phần trên là YouTube IFrame Player, phần dưới là danh sách phụ đề song ngữ tự cuộn theo thời gian.
- Lặp dòng phụ đề (vô hạn) và Ghép câu (ngắt ở `.?!`).
- Overlay song ngữ EN/VI khi xoay landscape + fullscreen.
- Từ điển popup (BottomSheet) tra từ qua Google Search.
- Lịch sử xem + Bookmark phụ đề (list phẳng group theo video).
- Settings: chọn engine dịch (Google online / ML Kit offline / Auto), cỡ chữ, hành vi auto-pause, xóa cache…
- Xử lý 3 trường hợp: video có phụ đề / không có / có-nhưng-fetch-fail (cho upload SRT).

## Tech stack

- Java, Android Gradle Plugin 8.4, Gradle 8.7.
- minSdk 24, targetSdk 34, compileSdk 34.
- AndroidX: AppCompat, ConstraintLayout, RecyclerView, Preference, Lifecycle, Activity, Fragment.
- Material Components 1.11 (theme Light mặc định).
- Room 2.6.1 (history / bookmark / subtitle cache / settings).
- OkHttp 4.12 + Gson 2.10.
- `com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0`.
- `com.google.mlkit:translate:17.0.2` (engine offline).
- Java 17 toolchain.

## Build

```bash
./gradlew assembleDebug
```

Mở project bằng **Android Studio Iguana (2023.2)** trở lên (cần JDK 17). Chạy app trên thiết bị/emulator Android 7.0+ (API 24+).

## Cấu trúc package

```
com.joy.englishtube
├── EnglishTubeApp           # Application, service locator
├── ui
│   ├── main                 # MainActivity (WebView host)
│   ├── player               # PlayerActivity (video + subtitle list + overlay)
│   ├── history              # HistoryActivity
│   ├── bookmark             # BookmarkActivity
│   └── settings             # SettingsActivity
├── service
│   ├── SubtitleService      # interface — implement Sprint 2
│   ├── TranslationService   # interface — implement Sprint 3 (Google + ML Kit)
│   └── PlayerSyncController # interface — implement Sprint 2
├── data
│   ├── AppDatabase          # Room
│   ├── HistoryEntity / Dao
│   ├── BookmarkEntity / Dao
│   ├── SubtitleCacheEntity / Dao
│   └── SettingEntity / Dao
└── model
    └── SubtitleLine
```

## Lộ trình

| Sprint | Nội dung |
|---|---|
| **0** (PR này) | Skeleton: Gradle, AndroidX, Room, OkHttp, lib YouTube, ML Kit, package layout. App build & launch một MainActivity placeholder. |
| 1 | MainActivity + WebView + nav bar + chuyển sang PlayerActivity. |
| 2 | YouTubePlayerView + lấy phụ đề EN + list + auto-scroll + xử lý case không có phụ đề. |
| 3 | TranslationService 2 engine + cache + list song ngữ + tap-to-seek. |
| 4 | Lặp dòng + Ghép câu + dictionary BottomSheet (long-press). |
| 5 | Overlay landscape fullscreen. |
| 6 | History + Bookmark + Settings + import SRT. |
| 7 | Polish, test trên Android 9/12/14, sửa bug, release APK debug. |
