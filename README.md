# AzipediaSearcher

![スクリーンショット 2025-02-11 155417](https://github.com/user-attachments/assets/9aab2ec7-1384-48a3-bd3e-5dea35af9bf9)

## 概要
Azipedia内のページをマインクラフト内から検索するプラグイン

デフォルトではAzipediaが指定されていますが、MediaWikiであれば設定で変更可能です

## コマンド
### 全般
- `/azipedia help`: AzipediaSearcherのヘルプ

- `/azipedia config reload`: Configの再読み込み

- `/azipedia version`: バージョンの表示

- `/azipedia search <検索する単語> [検索オプション]`

エイリアス: `/wiki`,`azisabawiki`,`w`

#### 検索オプション

検索オプションは任意です

検索する単語は複数指定できます

- category:カテゴリ名 でカテゴリを指定して検索します

- limit:件数 で検索結果表示数を指定します

- searchrange:title でタイトルのみを検索します

- searchrange:text で本文を含め検索します

- searchtype:AND でキーワードが複数指定されたときにどちらも含めて検索します

- searchtype:OR でキーワードが複数指定されたときにどちらかに当てはまるページを検索します

<img width="1049" height="47" alt="image" src="https://github.com/user-attachments/assets/6d03a1d3-14ce-4ace-a02c-c7452032b185" />

## 権限
- `azipediasearcher.command.reload`: reloadコマンド権限

## ライセンス
[GNU General Public License v3.0](LICENSE)
