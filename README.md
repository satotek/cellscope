# CellScope — Pixel 11 (Tensor G6) 向けセル情報モニタ

NetMonster 系の情報を「この端末で取れる限界」まで出すことを目的にした
Kotlin + Jetpack Compose のネイティブアプリ。root / priv-app の3段階で取得範囲が広がる。

## 取れるもの / 取れないもの

| 段階 | 権限 | 取れる情報 |
| --- | --- | --- |
| 通常 | `READ_PHONE_STATE` + 位置情報 | serving/neighbour cell（NR/LTE/WCDMA/GSM）、RSRP/RSRQ/SINR/CQI/TA、PCI/TAC/ECI/NCI、EARFCN/NR-ARFCN → band・周波数、`TelephonyDisplayInfo`（NR_NSA/LTE_CA）、`ServiceState.toString()` から nrState / EN-DC / DCNR restricted / CA有無 |
| root | 上 + `su` | `dumpsys telephony.registry` の `PhysicalChannelConfig`（PCC/SCC・帯域幅・band・PCI）、IMS登録状態、`logcat -b radio`、modem/RIL props |
| priv-app | 上 + `READ_PRIVILEGED_PHONE_STATE` / `READ_PRECISE_PHONE_STATE` / `MODIFY_PHONE_STATE` | `PhysicalChannelConfigListener` がpush型で動く（dumpsysポーリング不要）、VoNR状態、**RAT 固定** |

**取れないもの**: RRC/NAS のL3メッセージ。Network Signal Guru はQualcomm DIAG経由で
モデムから直接読むが、Tensor G6（Samsung Shannon系モデム）にDIAGは無い。
これはアプリ側でどうにもならない構造的な制約。

## ビルド / インストール

```bash
cd apps/cellscope
./install.sh          # debug APK を adb install（通常 / root モード）
./make-module.sh      # release APK + KSU priv-app モジュール zip を dist/ に生成
```

priv-app 化の手順（どちらか）:

- アプリ内: Setup → priv-app モード → 有効化 → 再起動。アプリ自身が KSU モジュール
  `/data/adb/modules/cellscope_privapp` を書き出す。解除も同じ行から。
- 手動: `dist/cellscope-privapp-*.zip` を KernelSU Next Manager から flash して再起動。

KernelSU Next 3.x は `system/` のマウントを「メタモジュール」に委譲しており、未導入だと
モジュールは何もマウントされない。そのためモジュールには `post-fs-data.sh` の自前マウント
（tmpfs にステージ → overlayfs で `/system/priv-app` と `/system/etc/permissions` に重ねる）
が入っている。メタモジュールが既に配置してくれていれば何もしない。`/data` は casefold f2fs
なので overlayfs の lowerdir に直接は使えない、というのが tmpfs を挟む理由。

root モードは KernelSU Manager でこのアプリに su を許可するだけ。

## 構成

```
app/src/main/kotlin/dev/satotek/cellscope/
├── MainActivity.kt / MainViewModel.kt   権限、ポーリング、履歴、root切替
├── data/model/Models.kt                 CellEntry / CarrierComponent / NetworkState / Snapshot
├── data/telephony/
│   ├── TelephonyRepository.kt           TelephonyCallback + requestCellInfoUpdate、ServiceState文字列パース
│   ├── CellMapper.kt                    CellInfo* → CellEntry（UNAVAILABLE→null 正規化、eNB/gNB分解）
│   └── BandTables.kt                    EARFCN / NR-ARFCN / UARFCN → band・MHz（3GPP 36.101 / 38.101-1,-2 / 25.101 全バンド）
├── data/root/
│   ├── RootShell.kt                     su -c
│   └── RootProbe.kt                     dumpsys telephony.registry / logcat -b radio / getprop パーサ
├── data/LogRecorder.kt                  CSV（GPS付き）
└── ui/                                  Compose: Home / Cells / Signal / Raw / Setup
ksu-module/                              /system/priv-app + privapp-permissions allowlist
```

## UI: Material 3 Expressive

- `compose-bom-alpha 2026.09.00`（material3 1.5.0-alpha28）。M3E の部品本体（`MaterialExpressiveTheme`,
  `MotionScheme.expressive()`, `ToggleButton`/`ButtonGroup`, `LoadingIndicator`）は stable 1.4.0 では
  `internal` で、alpha にしか無い。`compileSdk 37.1` が必要
- 配色は壁紙由来のダイナミックカラー（`dynamicDark/LightColorScheme`）。ライト/ダーク自動追従
- RAT色・good/fair/poor は意味色として固定。ライト時は暗いバリアント（`Palette` の composable getter）
- `ShortNavigationBar`、トーナルカード（`shapes.extraLarge`、ボーダー無し）、接続トグルグループ

## Signal タブ

- 時間軸固定ウィンドウ（2m/5m/15m/60m）、毎秒サンプリング、プロセス生存の履歴（1h）
- RSRP / RSRQ / SINR: serving（青）と NR SCC（緑、NSA時）を同一軸に重ね描き、good/fair/poor の背景帯
- 縦の破線 = セル変化（HO / BAND / RAT）。下に変化イベント一覧
- スループット: `TrafficStats`、0なら `/sys/class/net/wwan*` をフォールバック
- 近隣セル別 RSRP 時系列: ウィンドウ内で出現回数上位6セル（band/PCI）を serving と重ね描き
- グラフをタップ → その時点のサンプル詳細をボトムシートで表示（serving / NR SCC / RTT / スループット /
  ±30s のセル変化 / 当時の近隣セル）。シートのスライダーで全履歴をスクラブ、グラフに縦カーソル
- Ping: `/system/bin/ping -c1`（非rootで可）、失敗時 TCP:443。RTT推移 + min/avg/max/jitter/loss

## Stats タブ

`Snapshot.history` / `events` からの集計のみ（新しいデータ源は無い）。期間は 2m/5m/15m/60m。
サンプルは等間隔でないので「次サンプルまでの経過時間」を重みにした時間シェア。

- RAT / バンドの在圏時間シェア（横積みバー + 行ごとの滞在時間・%）、セル変更の種別別回数 + 直近 10 件
- RSRP / SINR のヒストグラムは Signal と同じ poor / fair / good の帯を背景に描き、下に **品質別の時間比率バー**。CA は LTE / NR 別
- スループット / RTT の max / avg / p95（重み付き）、RTT loss%

## スナップショット

Home ヘッダーの 📷 で「今この場所でこう見えた」を 1 枚のカード（1080×1760 PNG）+ 同名 JSON に保存。
画面のスクショではなく `GraphicsLayer.toImageBitmap()` で専用 Composable を offscreen 描画（常時ダーク、固定レイアウト）。

- 地図: OpenStreetMap タイル z16 を 5×3 枚合成して現在地中心に切り出し（3×3 だと ×1.4 拡大時に端が欠ける）。
  `User-Agent` 必須、`cacheDir/osm/` に 7 日キャッシュ、取得失敗時は座標のみ
- 内容: serving（ゲージ）/ CA（LTE nCC + NR mCC）/ ネットワーク / 近隣セル上位 6 / フッター
- 保存先 `files/snapshots/`（More → スナップショット で一覧・共有・削除）。同時に MediaStore 経由で
  `Pictures/CellScope/` にもコピーするので Google フォトに自動で出る
- 共有は `ACTION_SEND` に **Uri を 1 つだけ**（`ArrayList` を入れると Files/Photos が受け取れない）。JSON は別ボタン

## PiP / オーバーレイ HUD

- Home ヘッダーの PiP ボタン、または（Setup で自動 PiP を ON にすると）ホームに戻ったときに 4:3 の PiP へ。
  タップで serving ↔ スループットのページ切替、PiP のアクションボタンでも切替。
- オーバーレイ（`TYPE_APPLICATION_OVERLAY` + specialUse の FGS）はドラッグ移動・× で閉じる。「他のアプリの上に表示」の許可が要る。
- どちらも `PipHud` を描く。`MainViewModel` は `CellScopeApp` に process-wide で持たせ、Activity が消えてもポーリングが続く。
- 小さい窓では合計帯域だけ出す（`20+20+15+100` のような per-CC 列挙は入らない）。CC 表記は `3+1CC`（LTE+NR）。

## ログ再生（Replay）

記録は CSV（表計算用、serving のみ）と **JSONL**（1 行 = 1 `SignalSample`、近隣・NR SCC・スループット・RTT・位置込み）を並行して書く。
More → ログ → タップで Replay: Signal タブと同じパネル（`SignalPanels.kt` に共通化）を全区間で表示、
**ピンチで時間軸ズーム・ドラッグでパン**（最小 10 秒）、タップで `SampleSheet`。CSV しか無い古いログは serving 系列だけで開く。
`SignalChart` / `RatStrip` の `range` 引数がズーム用（null なら従来どおり最新から `windowMs`）。

## More タブ

右端は一覧メニュー。3 グループ + 終了:

- **設定**: 計測 / 表示 / 無線 / 権限 / 記録 / AI — 1 グループ = 1 サブ画面（`SettingsScreen(section = …)` で絞る）
- **データ**: ログ / スナップショット — **左スワイプで削除**（Gmail 式、スナックバーで元に戻す。実削除はスナックバーが消えてから）、
  見出しに件数・合計サイズと「すべて削除」。タップで共有 / 再生、長押しでも削除できる。
  ログは **インポート**（SAF、複数選択可。`.csv` / `.jsonl` を `logs/` にコピー、同名は `-1` 連番）で他端末のログも再生できる
- Raw / **アプリ情報**（バージョン・端末・権限、作者 / ソース / ライセンス / OSS ライセンス。`AppInfo` に URL 等をまとめてある。
  ライセンス本文は `assets/LICENSE`（ルートの `LICENSE` はそのシンボリックリンク）。OSS 一覧は `ossNotices`（`MoreScreen.kt`）— ルートの `THIRD_PARTY.md` と同期させること）
- **終了**: `vm.shutdown()` → `finishAndRemoveTask()` → プロセス kill。ホームに戻るだけでは計測が続く（オーバーレイ HUD のため）

サブ画面は `BackHandler` で戻る（Navigation ライブラリ無し）。`FileProvider` authority は `dev.satotek.cellscope.files`。

## 設定（More → 設定）

Pixel の設定アプリと同じ「グループ化リスト」形式。説明文は各行の ⓘ からボトムシートで開く。

- **表示**: テーマ（システム / ライト / ダーク、`MainViewModel.themeMode` を Activity とオーバーレイの両方が読む。
  強制テーマ時はステータスバーのアイコン色も `WindowInsetsController` で追従）、
  言語（`LocaleManager.applicationLocales`。OS の「アプリの言語」と同期し、Activity 再生成で即反映）
- **AI**: プロバイダ / API キー（`EncryptedSharedPreferences`）。キー無しでも Stats / Replay の ✨ から要約の共有・コピーはできる。
  要約シートの「ログファイルを添付」を入れると共有には CSV が `EXTRA_STREAM` で付き、API 直呼びにはCSV 末尾 150k 文字がプロンプトに入る

## Data calls（root）

`dumpsys telephony.registry` の `mPreciseDataConnectionStates` から PDN ごとに
APN / 種別 / 状態 / RAT / iface / IPアドレス / DNS / P-CSCF / MTU / QoS（5QI or QCI, maxDL）/ fail cause を表示。

## メモ

- `CellInfo` は位置情報権限が無いと空リストになる（Android 10+ の仕様）。
- `ServiceState.toString()` の文字列パースは @hide フィールドを非rootで読む唯一の手段。
  OSアップデートでキー名が変わる可能性あり（`nrState=`, `isEnDcAvailable=` など）。
- NR の gNB / cell 分解に使う gNB ID 長は Setup で選択（22〜32 bit、既定 24。事業者依存。NCI そのものも表示する）。
- バンド表は 3GPP の全バンド（LTE B1〜B88、NR n1〜n104 + FR2、UMTS I〜XXV）。モデムが `CellIdentity.bands` を返す場合はそちらを優先し、表は近隣セル等で空のときの補完。
- Setup「無線」: RAT 固定（自動 / 5G+4G / 5G(SA のみ) / 4G / 4G+3G / 3G、`setAllowedNetworkTypesForReason`、priv-app）、Wi‑Fi・機内モード切替と再登録（root）。
  バンド固定・セル固定は Android に API が無く Tensor では不可（NSG の band lock は Qualcomm DIAG 依存）。
- `PhysicalChannelConfigListener` の登録は **`CellInfoListener` と同じコールバックに同居**させる。位置権限の評価は
  位置系イベントを含むレコードでしか行われず、単独登録だと PCI が `PHYSICAL_CELL_ID_UNKNOWN` に伏せられて届く。
- `PhysicalChannelConfigListener` は TelephonyRegistry 上 `READ_PRECISE_PHONE_STATE` グループ。これが無いと登録は
  黙って落ち、`notifyNow` の初回値だけ残る（CA 表示が更新されない症状）。allowlist に 3 権限とも入れること。
- UI は英語（既定）と日本語。文言は `res/values{,-ja}/strings.xml`、システムのアプリ別言語設定にも対応（`localeConfig`）。
- CSV は `Android/data/dev.satotek.cellscope/files/logs/` に出る。
