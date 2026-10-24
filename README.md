# CellScope

Pixel 11（Tensor G6）向けの **セル情報モニタ**。NetMonster 系の情報を「この端末で取れる限界」まで出すことを目的にした
Kotlin + Jetpack Compose（Material 3 Expressive）のネイティブアプリ。root / priv-app の 3 段階で取得範囲が広がる。

## 機能

- **ホーム**: serving セル（RSRP / RSRQ / SINR / CQI / TA、PCI / TAC / ECI / NCI、バンド・周波数）、CA 構成（LTE nCC + NR mCC、帯域幅）、ネットワーク状態（NR state / EN-DC / IMS / VoNR）
- **両SIMサマリー**: ホームで各SIMの通信方式・RSRP・5G状態を同時表示。タップで詳細の観測対象を切替
- **訪問セル**: 選択中SIMの接続セルを端末内に蓄積。初回・最終観測、最高RSRP、観測回数、検索・メモ、ZIPバックアップ
- **セル一覧**: 見えているセル全部を RAT ごとに
- **電波**: RSRP / RSRQ / SINR / 近隣セルの時系列（2m〜60m）、セル変更イベント、タップで拡大グラフと測定詳細、なぞって時刻選択・全画面表示（横向き対応）
- **通信**: モバイル通信速度・Pingの時系列、NDT7速度測定、測定履歴・CSV共有
- **統計**（その他）: 在圏バンド比率、品質分布、CA、スループット / RTT の集計
- **ログ**: CSV + JSONL 記録、後から **再生**（ピンチでズーム）、他端末のログのインポート
- **スナップショット**: 現在地の地図（OpenStreetMap）付きカード画像を保存・共有（Google フォトにも出る）
- **PiP / オーバーレイ HUD**: 他アプリを使いながら serving / スループットを常時表示
- **無線制御**（priv-app / root）: RAT 固定（5G+4G / 5G SA / 4G / 3G …）、Wi‑Fi・機内モード切替、再登録
- 英語 / 日本語、ライト / ダーク

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
# リポジトリ直下で
./install.sh          # debug 版（dev.satotek.cellscope.debug）を adb install。release 版と共存する
./install.sh release  # release 鍵の版を adb install。release の priv-app をそのまま更新できる
./make-module.sh      # release APK + KSU priv-app モジュール zip を dist/ に生成
```

priv-app 化の手順（どちらか）:

- アプリ内: Setup → priv-app モード → 有効化 → 再起動。アプリ自身が KSU モジュール
  `/data/adb/modules/cellscope_privapp` を書き出す。解除も同じ行から。
- 手動: `dist/cellscope-privapp-*.zip` を KernelSU Next Manager から flash して再起動。

root モードは KernelSU Manager でこのアプリに su を許可するだけ。
priv-app モジュールの仕組みは [docs/DESIGN.md](docs/DESIGN.md#priv-app-モジュール) を参照。

## 署名とリリース

release はリリース鍵、debug はマシンごとの debug 鍵で署名する。debug は
`dev.satotek.cellscope.debug`（ラベル「CellScope Debug」）という別アプリになり、
アプリ内の priv-app 化も `cellscope_privapp_debug` という別モジュールになる。
debuggable な APK をリリース鍵で署名すると、release 版を上書きして `run-as` で
データを読めてしまうため、鍵は分けている。

- 鍵の場所: `~/.config/cellscope/keystore.properties`（`storeFile` / `storePassword` /
  `keyAlias` / `keyPassword`）。別の場所なら `-Pcellscope.signing=<path>`。
  無い環境（CI の通常ビルドなど）はマシンごとの debug 鍵になる。
- 鍵を失うと同じアプリとして更新できなくなる。`release.jks` とパスワードは
  パスワードマネージャーなどにバックアップしておく。
- バージョンは `gradle.properties` の `cellscope.versionName` だけで決まり、
  versionCode は `major*10000 + minor*100 + patch`。

リリース手順:

```bash
# cellscope.versionName を上げてコミットしたあと
git tag v0.2.0 && git push origin v0.2.0
```

タグの push で `.github/workflows/release.yml` が、タグとバージョンの一致と署名証明書を
確かめてから、APK と priv-app モジュール zip を GitHub Release に添付する。
CI 側の鍵は GitHub Environment `release`（`v*` タグからのみ利用可）の Secrets
`CELLSCOPE_KEYSTORE_BASE64` / `CELLSCOPE_KEYSTORE_PASSWORD` / `CELLSCOPE_KEY_ALIAS` /
`CELLSCOPE_KEY_PASSWORD` に、証明書の SHA-256 は同じ Environment の Variables
`CELLSCOPE_CERT_SHA256` に置く。ワークフローの Actions はコミット SHA で固定している。

## ドキュメント

- [docs/DESIGN.md](docs/DESIGN.md) — 構成、各画面の実装メモ、Android telephony の落とし穴

## ライセンス

MIT — [LICENSE](LICENSE)。第三者ソフトウェアは [THIRD_PARTY.md](THIRD_PARTY.md)。
地図タイルは © OpenStreetMap contributors（ODbL）。
