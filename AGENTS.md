# THSR_APP_Android Rules

## Primary goal
- Android 以對齊 iOS 功能與設計為第一原則。
- iOS 是 authority；Android 不是獨立重新發明一套產品邏輯的地方。
- 任何 train results、search、discount、seat、notification、timeline 改動，都先讀 iOS 對應實作，再決定 Android 要怎麼對齊。

## Working mode
- 先確認這次要對齊的是哪一段 iOS 行為：資料模型、互動、畫面層級、文案、狀態、動畫、fallback 邊界。
- 若是大型改動、跨 feature 重構、資料流搬移，先規劃再實作。
- 回答時要明確說出：原本 Android 問題點、對應的 iOS 目標行為、這次修正內容、驗證方式。

## Build and verification
- 進入本機環境前先 `source ~/.zshrc`。
- 預設只做 compile 驗證，不主動跑測試；優先使用 `./gradlew assembleDebug`。
- 真的遇到無法判斷的邏輯問題、解析邊界、回歸風險時，再跑對應最小測試範圍。
- 若命令因權限或沙盒限制失敗，直接申請提權，不要繞路。

## iOS-first parity rules
- 先讀 iOS，再改 Android；不要在沒看 iOS 最新實作前直接提出 Android-only 解法。
- 對齊順序：
  1. 行為與資料語意
  2. 畫面資訊架構與狀態呈現
  3. 互動細節
  4. 視覺 token 與文案
- 不可只改顏色、間距、字重來假裝完成 parity；狀態語意、資料來源、互動規則才是主體。
- 若 iOS 已有明確 phase/state 模型，Android 應優先鏡射相同語意，而不是只做近似視覺效果。
- 非必要不要加入 Android-only 文案、badge、狀態詞；若 iOS 沒有，先假設不應新增。

## Source mapping
- Android 搜尋主流程優先檢查：
  - `app/src/main/java/com/chiiii5640/thsrapp/features/searchDashboard/SearchDashboardScreen.kt`
  - `app/src/main/java/com/chiiii5640/thsrapp/features/searchDashboard/SearchDashboardViewModel.kt`
  - `app/src/main/java/com/chiiii5640/thsrapp/features/searchDashboard/SearchDashboardService.kt`
  - `app/src/main/java/com/chiiii5640/thsrapp/features/searchDashboard/ResultFilter.kt`
- Android train results / timeline 主流程優先檢查：
  - `app/src/main/java/com/chiiii5640/thsrapp/features/trainResults/TrainOptionCard.kt`
  - `app/src/main/java/com/chiiii5640/thsrapp/features/trainResults/StopTimeline.kt`
- Android discounts / notifications 主流程優先檢查：
  - `app/src/main/java/com/chiiii5640/thsrapp/features/discounts/FeedDiscountService.kt`
  - `app/src/main/java/com/chiiii5640/thsrapp/features/bookingNotifications/`
- Android 核心模型優先檢查：
  - `app/src/main/java/com/chiiii5640/thsrapp/core/model/TrainOption.kt`
- iOS 對照入口優先檢查：
  - `/Users/shaoqi/THSR_APP/THSR_APP/App/AppDependencies.swift`
  - `/Users/shaoqi/THSR_APP/THSR_APP/App/RootView.swift`
  - `/Users/shaoqi/THSR_APP/THSR_APP/Features/SearchDashboard/ViewModel/SearchDashboardViewModel.swift`
  - `/Users/shaoqi/THSR_APP/THSR_APP/Features/SearchDashboard/Service/SearchDashboardService.swift`
  - `/Users/shaoqi/THSR_APP/THSR_APP/Features/TrainResults/View/TrainOptionRow.swift`
  - `/Users/shaoqi/THSR_APP/THSR_APP/Features/TrainResults/View/StopTimeline.swift`

## Project structure rules
- 維持單模組 `:app` 結構，不主動拆多模組。
- 依 feature 分資料夾，不以技術類型把整個 app 橫切成一堆分散目錄。
- 目前主結構以這些位置為準：
  - `app/src/main/java/com/chiiii5640/thsrapp/features/<feature>/`
  - `app/src/main/java/com/chiiii5640/thsrapp/core/model/`
  - `app/src/test/java/com/chiiii5640/thsrapp/features/<feature>/`
- 新檔案優先放進既有 feature 內，除非它已經是跨 feature 的穩定共用能力。

## Naming and file placement
- Compose 畫面入口用 `Screen` 結尾，例如 `SearchDashboardScreen.kt`。
- 單張列車卡片、sheet、dialog、cell 類型的 UI 元件，用清楚的責任命名，例如 `TrainOptionCard.kt`、`BookingNotificationSheet.kt`，不要用模糊的 `CustomCard.kt`、`CommonView.kt`。
- 與列車時間軸直接相關的 UI 與 phase 計算，集中在 `features/trainResults/`，不要分散到無關 feature。
- 管理頁面狀態、查詢條件、使用者操作協調的類別，用 `ViewModel` 結尾並放在對應 feature。
- 純篩選、排序、查詢條件語意型別，優先放 feature 內，例如 `ResultFilter.kt`。
- 核心領域模型如 `TrainOption` 放 `core/model/`；若只是某一 feature 的暫時 UI state，不要硬塞進 core。
- 串接外部資料、做 fallback、整併 feed、查 seat/timetable 的邏輯放 `Service` 類別，並放在對應 feature。
- 通知排程、receiver、store、sheet 保持在 `features/bookingNotifications/`，不要拆到隨意 util 資料夾。
- 測試檔命名與正式檔一一對應，使用 `<Subject>NameTest.kt`，例如 `SearchDashboardServiceTest.kt`。
- 不要使用 `New`、`Temp`、`Util2`、`FinalVersion` 這類暫時命名。

## Behavior rules to preserve
- 搜尋結果必須保留完整結果集，不可默默裁掉最後班次。
- 最快車篩選、跨午夜時間計算、方向判斷、折扣套用，需對齊 iOS 語意，不做 Android 自行簡化版。
- seat / timetable fallback 可以補位，但不能掩蓋官方資料其實成功或失敗的邊界。
- force refresh 必須是真的 refresh，不可被過度快取吃掉。
- timeline 應以 phase-driven state 為主，不只是一條線性進度條。
- station 應大多維持靜態，train marker 才是主要動態；避免過度特效、閃爍、粒子感。
- 若 iOS 使用實際 scroll / phase 模型，Android 不要退回手勢模擬假滾動或只用 offset 偽裝。

## Design parity rules
- 優先對齊資訊層級、區塊結構、狀態呈現與文案，再處理 Android 視覺微差。
- Android 可以保留 Compose / Material 實作方式，但最終使用者感知應接近 iOS。
- 若 iOS 某段設計是為了解釋狀態，Android 應保留同一個語意目的，不可只留下表面外觀。
- 沒有明確理由，不新增 Android 專屬裝飾、提示詞、次要 badge。

## Commit rule
- 完成且驗證後優先協助 commit。
- commit message 要清楚寫出這次對齊了哪個 iOS 行為或修掉哪個 Android 偏差。
- 若 repo 內已有使用者未完成修改，不可回退它們；只 stage / commit 本次相關檔案。
