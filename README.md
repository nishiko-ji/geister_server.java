# GAT2024ガイスターAI大会用サーバー
ガイスターAI大会用にサーバーを改良しました．
大会と同じ設定でサーバーを起動するには...

    java -jar build/libs/geister.jar --set_player_name_server --budget=600 --timeout=10 --wait=1000

大会時は "--set_player_name_server" オプションを使ってプレイヤー名を設定するため，クライアント側が大会のために接続方法を変更する必要はありません．

サーバー側は，対戦ごとにプレイヤー名を設定する必要があります．

複数回対戦時は...

    java -jar build/libs/geister.jar --set_player_name_server --budget=600 --timeout=10 --wait=200 --battle-times=50

"--battle-times"で指定回数の対戦を行ったのち，先後切り替えを行い，指定回数の対戦を行います．

複数回対戦は"--set_player_name_server"オプションを使う前提で実装したため，"--set_player_name_server"なしでは正しく動作しない可能性があります．

複数回対戦のルールはまだ決まっていません．"--budget", "--timeout", "--wait", "--battle-times"は適当な数値になっています．

先後切り替えは，サーバー側のプレイヤー名や，対戦結果を自動で切り替えるもので，AIの接続を自動で切り替えるものではありません．

AIはサーバーの先後切り替えのタイミングで接続するポートを変える必要があります．

複数回対戦時の接続はmulti_battle.shを参考にしてください．自動で先後切り替えができるようになっています．

    bash multi_battle.sh 127.0.0.1 10000 10

## 元サーバーとの変更点
- サーバー側でプレイヤー名を設定できる
- クライアント側でプレイヤー名を設定できる
- ログ名にプレイヤー名を追加する
- プレイヤー名，残り時間，手数をviewer.htmlで見られる
- 対戦回数指定ができる．
- viewer_multi.htmlで対戦結果が表示される．

## 元サーバーとの変更点（詳細）
### サーバー側でプレイヤー名を設定できる
- サーバー起動時にオプション "--set_player_name_server" をつけるとサーバー側でプレイヤー名を設定できるようになる．
- クライアントの接続前にサーバー側で毎回，"SET PLAYER_0 NAME > ", "SET PLAYER_1 NAME > "と表示される.
- プレイヤー名を入力すると，プレイヤー名が設定され，クライアントが接続可能となる．
### クライアント側でプレイヤー名を設定できる
- サーバー起動時にオプション "--set_player_name_client" をつけるとクライアント側からプレイヤー名を設定できるようになる．
- 元のサーバーだと，"SET?"とクライアントが受信したとき，"SET:ABCD\r\n"のような文字列を送信することで，駒配置を設定できた．
- "--set_player_name_client" をつけた場合，送信文字列を"SET:ABCD,NAME:PLAYER_NAME\r\n"のようにすることでプレイヤー名を設定できる．
- "--set_player_name_client" をつけた場合，プレイヤー名を設定しないとサーバーが動かないので注意．
### ログ名にプレイヤー名を追加する
- ログ名にプレイヤー名がつくようになり，大会時の対戦管理が容易になる．
### プレイヤー名，残り時間をviewer.htmlで見られる
- viewer.htmlにプレイヤー名，持ち時間，秒読み，手数が表示されるようになった．

![viewer.htmlの時間，プレイヤー名表示](./misc/time_and_name2.png)

### 対戦回数指定ができる
- サーバー起動時にオプション "--battle-times=3" をつけるとサーバー側の対戦回数が指定できる．(この場合は3回対戦)
- 同じAI名での対戦が指定した回数分行える．
- "--set_player_name_server"を利用しているときに使う前提で作成したため，"--set_player_name_server"を指定していないときは正しく動作しない可能性があります．
### viewer_multi.htmlで対戦結果が表示される．
- viewer_multi.htmlに"--battle-times"で指定した対戦回数分の対戦結果が表示されるようになった．

![viewer_multi.htmlの時間，プレイヤー名表示](./misc/result.png)

# geister_server (Java version)

Geister用のゲームサーバ．
ランダムに打つコンピュータプレーヤ，人間が遊ぶ時用のGUIがあります．
また，勝負の様子をWebSocket経由でブラウザで観賞することができます．
...のはずです．

## 動作概要
- 二つのクライアント(プレーヤ)とTCPで通信し，Geisterを進行する
- ボードの管理，勝敗の管理をする
- WebSocketで観戦用のグローバルな盤情報をキャストする

## 動作環境

- Java 8, Java 11

## 盤面(仮)
        0 1 2 3 4 5
      0   h g f e
      1   d c b a
      2
      3
      4   A B C D
      5   E F G H
- サーバ側では表示のy=0の側を先手番の陣，y=5の側を後手番の陣とする
- プレーヤーは自分が先手なのか後手なのかによらず，y=5の側が自陣であるとする
- コマの名前は，ゲーム開始から終了まで変わらない
- コマが赤/青はゲーム開始前に，それぞれのクライアントに設定してもらう

## ゲームの進行
- サーバー: java net.wasamon.geister.TCPServerで2つのプレーヤインスタンス(それぞれ8個のコマを持つ)を生成してクライアントからの通信を待つ
 - 先手のクライアントは10000番，後手のクライアントは10001番で待ち受ける
- クライアント: クライアントは先手/後手に応じて10000番/10001番に接続．recvして `SET?` という文字列を受信する
- クライアント: クライアントは赤オバケを4つセットし，recvする
 - 赤オバケをセットするコマンドは，`SET:ABCD\r\n`
 - 正しく受理されると `OK \r\n` が，正しいコマンドでない場合は `NG \r\n` がサーバからクライアントに返される
- サーバー: 2つのクライアントが赤のコマをセットし終えると，サーバーは，先手番のクライアントに盤面情報を送ると共に手の入力を待ち受ける．
 - 盤面情報はコマの(x,y)と色(相手に非公開の赤/青=R/B，両者が見えている赤=r/青=b/不明=u)を ABCDEFGHabcdefgh (ここで小文字は相手のコマ)順に，並べたもの
 - たとえば初期状態は，`14R24R34R44R15B25B35B45B41u31u21u11u40u30u20u10u`
 - 取られたコマの座標は(9,9)
 - 盤外に逃げ出したコマンドの座標は(8,8)
 - 取った/取られたコマおよび盤外のコマ色は公開される．
 - 自分の手番によらずどちらのクラントも同じ方向からのビューを持つ(5の側が自陣，0の側が相手陣)
- クライアント: 先手番のクライアントは手を打ち，recvする．
 - 手を打つには， `MOV:A,NORTH\r\n` のように動かすコマ名と方角を送る
 - 方角は`NORTH`/`EAST`/`WEST`/`SOUTH`の4種類
  - それぞれ，`N`/`E`/`W`/`S`の1文字で指定することも可
 - 正しく受理されると `OK \r\n` が，正しいコマンドでない場合は `NG \r\n` がサーバからクライアントに返される
- サーバー: サーバーは手を受理した先手番にACKを送信．後手番のクライアントに更新後の盤面情報を送る
 - ACKは正しく受理されると `OK \r\n` が，正しいコマンドでない場合は `NG \r\n` がサーバからクライアントに返される
 - その手で相手のコマをとった場合，その色が赤なら`R`，青なら`B`がACKに付与される
  - 赤を取ったなら `OKR\r\n`，青を取ったなら`OKB\r\n`
- クライアント: 後手番のクライアントは手を打ち，recvする．
- サーバー: 先手/後手のどちらかで勝負がついたら，両方に結果と終了時点での盤面情報を送る．
 - 勝った方には`WON`，負けた方には`LST`を送る．
 
サーバがコマンドを受理するとOK/NGを返すようになったので，
対戦のプロトコルの詳細は，Pythonで書きかけていたバージョン https://github.com/miyo/geister_server と整合はとれていません(2017/01/08 現在)

## 実行例
### コンパイル

    gradle jar

gradle-6.9.2 または gradle-7.4.1 でビルドできることを確認しています．

### サーバー

    java -jar build/libs/geister.jar

でサーバーが起動します．

    java -jar build/libs/geister.jar --timeout=5 --budget=200

とすると，持ち時間200秒，タイムアウト5秒などと設定できます．
(デフォルトは持ち時間10分，タイムアウト10秒)

    java -jar build/libs/geister.jar --no_ng_terminate

と，--no_ng_terminateを着けて起動すると，不正な手を打った場合の負けを抑止することができます．
(デフォルトでは不正な手を打つと，即座に負けになります)


### テスト用クライアント
それぞれ別のターミナルなどで起動する．

    java -cp build/libs/geister.jar net.wasamon.geister.player.RandomPlayer localhost 10000 # 1st playerとして
    java -cp build/libs/geister.jar net.wasamon.geister.player.RandomPlayer localhost 10001 # 2nd playerとして

### テスト用クライアント(2)
HumanPlayerを使うと標準入力から手を入力できます．

    java -cp build/libs/geister.jar net.wasamon.geister.player.RandomPlayer localhost 10000 # 1st playerとして
    java -cp build/libs/geister.jar net.wasamon.geister.player.HumanPlayer localhost 10001 # 2nd playerとして

### テスト用クライアント(3)
HumanGUIPlayerを使うとGUIで遊ぶことができます．HumanGUIPlayer実行時の第3引数に，赤にセットするコマの名前を与えてください．A〜Hまでの4つから選択してください．

    java -cp build/libs/geister.jar net.wasamon.geister.player.RandomPlayer localhost 10000 # 1st playerとして
    java -cp build/libs/geister.jar net.wasamon.geister.player.HumanGUIPlayer localhost 10001 ABCD # 2nd playerとして

HumanGUIPlayerをRetinaディスプレイ搭載のMacBookなどで実行する場合には，次のように第4引数に `retina` というキーワードを付与して実行することで，期待通りの表示になります．

    java -cp build/libs/geister.jar net.wasamon.geister.player.HumanGUIPlayer localhost 10001 ABCD retina

![クライアントサンプル](./misc/ui_screenshot.png)

ドラッグアンドドロップでコマを動かして遊べます．

### 対戦の様子を観戦する
WebSocketで対戦中の盤情報が配信されます．

    resources/html/viewer.html

をWebSocket対応のWebブラウザで開いてください．

![ビューワサンプル](./misc/viewer_image.png)

## プレーヤを作る場合は
TCPで通信すればよいので，何で作っても構いません．
もしJavaで書くのであればsrc/wasamon/geister/player/BasePlayer.javaを継承すると楽かもしれません．
その場合は，src/wasamon/geister/player/RandomPlayer.javaが参考になるかもしれません．

