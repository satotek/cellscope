# CellScope

Pixel 11（Tensor G6）向けの **セル情報モニタ**。NetMonster 系の情報を「この端末で取れる限界」まで出すことを目的にした
Kotlin + Jetpack Compose（Material 3 Expressive）のネイティブアプリ。root / priv-app の 3 段階で取得範囲が広がる。

## 機能

- **ホーム**: serving セル（RSRP / RSRQ / SINR / CQI / TA、PCI / TAC / ECI / NCI、バンド・周波数）、CA 構成（LTE nCC + NR mCC、帯域幅）、近隣セル、ネットワーク状態（NR state / EN-DC / IMS / VoNR）、スループット・RTT
- **セル一覧**: 見えているセル全部を RAT ごとに
- **電波**: RSRP / RSRQ / SINR / スループット / RTT / 近隣セルの時系列（2m〜60m）、セル変更イベント、タップでその時点の詳細
- **統計**: 在圏バンド比率、品質分布、CA、スループット / RTT の集計
- **ログ**: CSV + JSONL 記録、後から **再生**（ピンチでズーム）、他端末のログのインポート
- **スナップショット**: 現在地の地図（OpenStreetMap）付きカード画像を保存・共有（Google フォトにも出る）
- **PiP / オーバーレイ HUD**: 他アプリを使いながら serving / スループットを常時表示
- **AI に聞く**: 計測要約を ChatGPT / Gemini / Claude に共有、または API キーを入れてアプリ内で回答表示
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
./install.sh          # debug APK を adb install（通常 / root モード）
./make-module.sh      # release APK + KSU priv-app モジュール zip を dist/ に生成
```

priv-app 化の手順（どちらか）:

- アプリ内: Setup → priv-app モード → 有効化 → 再起動。アプリ自身が KSU モジュール
  `/data/adb/modules/cellscope_privapp` を書き出す。解除も同じ行から。
- 手動: `dist/cellscope-privapp-*.zip` を KernelSU Next Manager から flash して再起動。

root モードは KernelSU Manager でこのアプリに su を許可するだけ。
priv-app モジュールの仕組みは [docs/DESIGN.md](docs/DESIGN.md#priv-app-モジュール) を参照。

## ドキュメント

- [docs/DESIGN.md](docs/DESIGN.md) — 構成、各画面の実装メモ、Android telephony の落とし穴

## ライセンス

MIT — [LICENSE](LICENSE)。第三者ソフトウェアは [THIRD_PARTY.md](THIRD_PARTY.md)。
地図タイルは © OpenStreetMap contributors（ODbL）。
