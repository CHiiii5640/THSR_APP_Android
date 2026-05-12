# THSR_APP Android 版架構設計 README

## Summary

Android 版目標是基於現有 iOS 架構與畫面行為重建同等功能，不重新設計資料流程。核心方向是：Kotlin + Jetpack Compose + single app module + feature-folder 架構，沿用目前穩定的 TDX 查詢、GitHub Pages 優惠快取、GeneralTimetable fallback、路線偏好保存、開票通知與完整班次列表行為。

Android 版第一版不拆多 module，先以單一 `:app` module 建立清楚的 feature/package boundary，降低與 iOS 同步成本。

## Current iOS Behavior To Preserve

- 入口由 dependency graph 建立 live services，再注入 search dashboard view model。
- 查詢表單包含起站、迄站、交換起迄站、搭乘日期、出發時間、強制更新。
- 表單變更自動查詢，普通查詢需要 debounce / dedupe，強制更新必須立即執行並繞過快取。
- 結果列表不可截斷，要能一次顯示到最後班次。
- 結果支援篩選：全部、可訂位、有優惠、有座位、最快。
- 每筆班次顯示車次、開票狀態、起迄時間、優惠、座位狀態、資料來源、官方訂票連結。
- 班次可展開停靠時間軸。
- 只有尚未開放訂票的班次可設定開票通知。
- 資料來源區要清楚顯示 timetable、seat availability、discount feed 來源狀態。

## Android Project Structure

```text
app/src/main/java/com/chiiii5640/thsrapp/
  app/
    MainActivity.kt
    AppGraph.kt

  core/
    model/
      Station.kt
      TripQuery.kt
      TrainOption.kt
      DiscountFeed.kt
      TdxModels.kt
    network/
      HttpClient.kt
      TdxApiClient.kt
      TdxAuthInterceptor.kt
    persistence/
      RoutePreferencesStore.kt
      PersistedGeneralTimetableStore.kt
    time/
      ThsrClock.kt
      ThsrFormatters.kt

  features/
    searchDashboard/
      SearchDashboardScreen.kt
      SearchDashboardViewModel.kt
      SearchCoordinator.kt
      SearchDashboardService.kt
      ResultFilter.kt
    timetable/
      TimetableProvider.kt
      TdxTimetableProvider.kt
    seatAvailability/
      SeatAvailabilityProvider.kt
      TdxSeatAvailabilityProvider.kt
    discounts/
      DiscountProvider.kt
      FeedDiscountService.kt
    trainResults/
      TrainOptionCard.kt
      StopTimeline.kt
      SeatAvailabilityLabel.kt
    bookingNotifications/
      BookingNotificationScheduler.kt
      BookingNotificationSheet.kt
      ScheduledNotificationsScreen.kt
```

## Data Flow

```text
SearchDashboardScreen
  -> SearchDashboardViewModel
  -> SearchCoordinator
  -> SearchDashboardService
      -> TdxTimetableProvider
          -> TdxApiClient DailyTimetable
          -> TdxApiClient GeneralTimetable fallback
          -> PersistedGeneralTimetableStore
      -> TdxSeatAvailabilityProvider
          -> AvailableSeatStatus/Train/OD
          -> AvailableSeatStatusList for today only
      -> FeedDiscountService
          -> GitHub Pages discounts.json
      -> merged TrainOption list
```

## API Integration

TDX auth:

```http
POST https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id={TDX_CLIENT_ID}&client_secret={TDX_CLIENT_SECRET}
```

TDX timetable:

```http
GET https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/DailyTimetable/TrainDate/{yyyy-MM-dd}?$format=JSON
```

TDX GeneralTimetable fallback:

```http
GET https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/GeneralTimetable?$top=300&$format=JSON
```

TDX OD seat status:

```http
GET https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/AvailableSeatStatus/Train/OD/TrainDate/{yyyy-MM-dd}?$top=500&$filter=OriginStationID eq '{originID}' and DestinationStationID eq '{destinationID}'&$format=JSON
```

TDX today seat board:

```http
GET https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/AvailableSeatStatusList/{originID}?$top=100&$format=JSON
```

Discount feed:

```http
GET https://chiiii5640.github.io/THSR_APP/api/discounts.json
```

## Cache And Fallback Rules

- Token cache：token 到期前 60 秒視為失效。
- DailyTimetable cache：依 `yyyy-MM-dd` 快取；`forceRefresh=true` 必須跳過。
- GeneralTimetable cache：記憶體快取 + 本機持久化 JSON。
- Persisted GeneralTimetable：存 app private storage，用於 TDX supply date 過期或網路 fallback。
- Seat OD cache：今天 TTL 90 秒，未來日期 TTL 600 秒。
- Seat board：只查今天；非今天直接回空資料。
- Rate limit：遇到 429 後該 request 進入 30 秒 cooldown；seat API cooldown 不應阻擋 timetable API。
- TDX DailyTimetable 無資料時使用 GeneralTimetable；供應日期外 fallback 時跳過 seat APIs。
- TDX 完全不可用時，改用 GitHub Pages discount feed 內的 schedule rules 作為 fallback timetable。

## UI Design Mapping

- `SearchDashboardScreen` 使用 Compose `Scaffold` + `LazyColumn`。
- 查詢表單使用 `ExposedDropdownMenuBox` 或 `AlertDialog` 選站，日期時間用 Material DatePicker / TimePicker。
- 資料來源區使用 status rows，顯示時刻表、座位狀態、優惠快取。
- 結果篩選使用 horizontal chips。
- `TrainOptionCard` 顯示車次、開票狀態、起迄時間、優惠 badge、座位狀態。
- 停靠時間軸用 horizontal row，自訂 line + node。
- 通知操作可用 card action 或 swipe action；只對 `notYetOpen` 顯示。
- 強制更新按鈕要保持明確，並在 loading 時避免重複觸發同一 request。

## Notifications

Android 使用 `NotificationManager` + `AlarmManager`。  
通知 identifier 規則沿用：

```text
booking-open-{trainNo}-{travelDate}-{originCode}-{destinationCode}
```

預設提醒時間：

```text
estimated opening date 前一天 23:55
```

權限需求：

- Android 13+ 需要 `POST_NOTIFICATIONS`。
- 若採用精準提醒，需處理 exact alarm 權限或 fallback 成非精準提醒。
- 已設定通知頁要能列出、取消 pending notifications。

## Testing Plan

- Model decoding：TDX DailyTimetable、GeneralTimetable、AvailableSeatStatus、discounts.json。
- Request URL：確認 OAuth、DailyTimetable、GeneralTimetable、OD seat、seat board URL 正確。
- Cache behavior：普通查詢命中 cache，`forceRefresh=true` 繞過 cache。
- Search behavior：起迄站相同回錯誤；查詢結果依出發時間過濾並排序。
- Fallback behavior：DailyTimetable 無資料時走 GeneralTimetable；supply date 外跳過 seat APIs。
- Result count：45 筆以上結果不可被截斷。
- Discount merge：早鳥 / 大學生優惠可依 train number、direction、departure time、date 合併。
- Notification：只有未開放班次可設定；同一班次重設會覆蓋舊通知。
- UI tests：表單查詢、篩選 chip、展開時間軸、通知 sheet、已設定通知列表。

## Implementation Order

1. 建立 Android 專案與基本 Compose theme。
2. 建立 core models、formatter、station mapping。
3. 建立 TDX API client、OAuth、cache、rate limit handling。
4. 建立 discount feed parser 與 lookup service。
5. 建立 `SearchDashboardService` 合併 timetable、seat、discount。
6. 建立 `SearchDashboardViewModel` 與 `SearchCoordinator`。
7. 建立 Compose UI：表單、資料來源、結果列表、篩選、時間軸。
8. 建立 booking notification flow。
9. 補完整 unit tests 與基本 UI tests。
10. 用真實 TDX credentials 驗證查詢、fallback、強制更新、通知流程。

## Assumptions

- Android 第一版使用 single `:app` module，不先拆 feature modules。
- UI 行為以目前 iOS 版為準，不新增額外產品功能。
- App 內不爬 THSRC HTML；優惠與 fallback schedule 使用已生成的 GitHub Pages JSON。
- TDX timetable 是主要來源；GitHub Pages feed 是 fallback 與優惠來源。
- 所有日期與時間以 `Asia/Taipei` 為唯一基準。

