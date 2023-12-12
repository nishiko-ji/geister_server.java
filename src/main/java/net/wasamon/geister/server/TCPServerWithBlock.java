package net.wasamon.geister.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Calendar;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.glassfish.tyrus.server.Server;

import net.wasamon.geister.utils.Constant;
import net.wasamon.mjlib.util.GetOpt;

public class TCPServerWithBlock {

	private final GameServer server;
	Server webSocketServer = new Server("localhost", 8080, "/ws", null, UIWebSocketServer.class);

	private ServerThread[] players = new ServerThread[2];
	
	private Object lock = new Object();

	private int waitTime = 10;
	private static int waitViewerTime = 0;
	
	private final int budget;

  private static int battle_times = 0; // multi_battle

	public TCPServerWithBlock(GameServer server, int budget){
		this.server = server;
		this.budget = budget;
	}

	public void setWaitTime(int v){
		waitTime = v;
	}
	
	public int getWaitTime(){
		return waitTime;
	}

	public void start() throws IOException{
		players[0] = new ServerThread(server, Constant.PLAYER_1st_PORT, budget);
		players[1] = new ServerThread(server, Constant.PLAYER_2nd_PORT, budget);
		players[0].start();
		players[1].start();
		try{
			players[0].join();
			players[1].join();
		}catch(InterruptedException e){
			e.printStackTrace();
		}
	}

	PlayerTimer timer;

	class ServerThread extends Thread{

		final int port;
		final ServerSocketChannel server;
		SocketChannel ch;
		final GameServer game;
		int budget;
		
		public ServerThread(GameServer game, int port, int budget) throws IOException{
			this.budget = budget;
			this.game = game;
			this.port = port;
			this.server = ServerSocketChannel.open();
			this.server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			this.server.configureBlocking(true);
			this.server.bind(new InetSocketAddress(port));
		}

		private void doAccept(ServerSocketChannel server) throws IOException {
			SocketChannel c = server.accept();
			c.setOption(StandardSocketOptions.SO_LINGER, 1000);
			if (this.port == Constant.PLAYER_1st_PORT && ch == null){
				System.out.println("1st Payer:" + c.socket().getRemoteSocketAddress());
				ch = c;
				send(c, "SET?\r\n");
			} else if (this.port == Constant.PLAYER_2nd_PORT && ch == null) {
				System.out.println("2nd Payer:" + c.socket().getRemoteSocketAddress());
				ch = c;
				send(c, "SET?\r\n");
			} else {
				c.close();
			}
		}

		synchronized private void send(SocketChannel ch, String msg) throws IOException {
			if(!ch.isConnected()) return;
			ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
			int len = 0;
			do {
				len += ch.write(bb);
			} while (len < msg.length());
		}

		private ByteBuffer bb = ByteBuffer.allocate(2048);
		private String restMesg = "";
		/**
		 * This method does action and returns a flag whether game should be continued or not.
		 */
		private boolean doRead(SocketChannel ch) throws IOException {
			if(ch == null || ch.isConnected() == false) return false;
			int pid = port == Constant.PLAYER_1st_PORT ? 0 : 1;
			bb.clear();
			int len = ch.read(bb);
			if (len < 0) { // channel has been closed
				System.out.println("connection closed: " + pid);
				if(game.getState() != GameServer.STATE.GAME_END){
					doIrregularJudgement(pid);
				}
				return false; // game end
			}
			bb.flip();
			String msg = Charset.defaultCharset().decode(bb).toString();
			restMesg += msg;
			boolean result = false;
			if (restMesg.indexOf("\r\n") > 0) { // at least, there is a message
				synchronized(lock){
					result = action(ch, restMesg, pid);
				}
			}
			if(result && game.getState() == GameServer.STATE.GAME_END){
				return false; // game end
			}else{
				return true;
			}
		}
		
		public void run(){
			irregularFlag = false;
			try{
				body();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		
		private void body() throws IOException{
			doAccept(server);
			boolean flag = true;
			while(flag){
				try{
					flag = doRead(ch);
				}catch(IOException e){ // At least, a connection is disconnected during read.
					flag = false;
					if(port == Constant.PLAYER_1st_PORT){
						if(players[1] != null && players[1].ch != null && players[1].ch.isConnected()){
							try{
								send(players[1].ch, "WON:" + game.getEncodedBoard(1, true) + "\r\n");
							}catch(IOException ee){
								// internal error
							}
						}
					}else{
						if(players[0] != null && players[0].ch != null && players[0].ch.isConnected()){
							try{
								send(players[0].ch, "WON:" + game.getEncodedBoard(0, true) + "\r\n");
							}catch(IOException ee){
								// internal error
							}
						}
					}
				}
				if(flag == false){ // not should be continued.
					break;
				}
			}
			//close();
		}

		private void close() throws IOException {
			if(timer != null){
				timer.terminate();
			}
			if(ch != null && ch.isConnected()){
				ch.shutdownOutput();
				ch.close();
			}
			if(server != null){
				server.close();
			}
		}
				
		long turnStart = -1;
		
		private boolean action(SocketChannel chan, String str, int pid) throws IOException {
			boolean result = true;
			String lastTakenItemColor = "";

			int i = str.indexOf("\r\n");
			if (i == -1){
				// internal error; str should be a message. 
				return false;
			}
			String cmd = str.substring(0, i);
			str = str.substring(i + 2);
			result = game.parse(cmd, pid);
			lastTakenItemColor = game.getLastTakenItemColor();
			game.pp();
		
			restMesg = str;
		
			String stateLabel = "MOV:";
			
			if (result) {
				System.out.println("pid["+pid+"]send: OK");
				if(turnStart > 0){
					this.budget -= (Calendar.getInstance().getTimeInMillis() - turnStart) / 1000;
				}
				System.out.println("pid["+pid+"]Budget: " + this.budget);
				send(chan, String.format("OK%s\r\n", lastTakenItemColor));
				if(timer != null){
					timer.terminate();
				}
			}else{
				System.out.println("send: NG");
				send(chan, "NG \r\n");
			}

			//sleep for view. (without using budget time.) // by kawakami-san
			try {
				Thread.sleep(waitViewerTime);
			} catch (InterruptedException e) {
			}
			
			if (result && game.getState() == GameServer.STATE.WAIT_FOR_PLAYER_0) {
				System.out.println("MOV?" + game.getEncodedBoard(0));
				send(players[0].ch, "MOV?" + game.getEncodedBoard(0) + "\r\n");
				players[0].turnStart = Calendar.getInstance().getTimeInMillis();
				int w = players[0].budget <= 0 ? waitTime : players[0].budget + waitTime;
				timer = new PlayerTimer(game, players, w, waitTime, 0);
				timer.start();
				stateLabel = "MV0:";
			}
			if (result && game.getState() == GameServer.STATE.WAIT_FOR_PLAYER_1) {
				System.out.println("MOV?" + game.getEncodedBoard(1));
				send(players[1].ch, "MOV?" + game.getEncodedBoard(1) + "\r\n");
				players[1].turnStart = Calendar.getInstance().getTimeInMillis();
				int w = players[1].budget <= 0 ? waitTime : players[1].budget + waitTime;
				timer = new PlayerTimer(game, players, w, waitTime, 1);
				timer.start();
				stateLabel = "MV1:";
			}
			if (game.getState() == GameServer.STATE.GAME_END) {
				int winner = game.getWinner();
				System.out.println("game end: winner=" + winner);
				if (winner == Constant.DRAW_MARK) {
					System.out.println("DRW:" + game.getEncodedBoard(0, true));
					send(players[0].ch, "DRW:" + game.getEncodedBoard(0, true) + "\r\n");
					System.out.println("DRW:" + game.getEncodedBoard(1, true));
					send(players[1].ch, "DRW:" + game.getEncodedBoard(1, true) + "\r\n");
					stateLabel = "DRW:";
				} else {
					int loser = winner == 0 ? 1 : 0;
					System.out.println("WON:" + game.getEncodedBoard(winner, true));
					send(players[winner].ch, "WON:" + game.getEncodedBoard(winner, true) + "\r\n");
					System.out.println("LST:" + game.getEncodedBoard(loser, true));
					send(players[loser].ch, "LST:" + game.getEncodedBoard(loser, true) + "\r\n");
					stateLabel = winner == 0 ? "WI0:" : "WI1:";
				}
			}
		  UIWebSocketServer.setName("Name0:" + game.getName(0) + ",Name1:" + game.getName(1)); // as global viewer mode
			UIWebSocketServer.setMesg(stateLabel + game.getEncodedBoard(1, true)); // as global viewer mode
      UIWebSocketServer.setTurn("Turn:" + game.getTurn());
		
			return result;
		}

		private void doIrregularJudgement(int loser) throws IOException {
			int winner = loser == 0 ? 1 : 0;
			System.out.println("Connection closed by " + loser);
			if (players[winner].ch != null && irregularFlag == false) {
				irregularFlag = true;
				System.out.println("send a message for [" + winner + "]");
				send(players[winner].ch, "WON:" + game.getEncodedBoard(winner, true) + "\r\n");
			}
		}

	}

	private boolean irregularFlag = false;
	
	class PlayerTimer extends Thread{
		private final GameServer game;
		private final ServerThread[] players;
		private final int waitTime;
    private final int byo_yomi;
		private final int player;
		
		private boolean runFlag = false;
		private boolean timeout = false;

		public PlayerTimer(GameServer game, ServerThread[] players, int waitTime, int byo_yomi, int player){
			this.game = game;
			this.waitTime = waitTime;
      this.byo_yomi = byo_yomi;
			this.players = players;
			this.player = player;
			this.runFlag = false;
			this.timeout = false;
		}

		public void terminate(){
			runFlag = false;
		}

		public void run(){
			System.out.println("PlayerTimer(" + player + "): timeout=" + waitTime);
			runFlag = true;
			timeout = false;
			int t = 0;
			while(runFlag){
				try{
          if(player == 0) {
            int enemy_budget = players[1].budget >= 0 ? players[1].budget : 0;
            if(waitTime-byo_yomi <= t) {
			        UIWebSocketServer.setTime("MJ0:" + 0 + ",MJ1:" + enemy_budget + ",BY0:" + (waitTime-t-1) + ",BY1:" + byo_yomi); // as global viewer mode
            } else {
			        UIWebSocketServer.setTime("MJ0:" + (waitTime-byo_yomi-t-1) + ",MJ1:" + enemy_budget + ",BY0:" + byo_yomi + ",BY1:" + byo_yomi); // as global viewer mode
            }
          } else {
            int enemy_budget = players[0].budget >= 0 ? players[0].budget : 0;
            if(waitTime-byo_yomi <= t) {
			        UIWebSocketServer.setTime("MJ0:" + enemy_budget + ",MJ1:" + 0 + ",BY0:" + byo_yomi + ",BY1:" + (waitTime-t-1)); // as global viewer mode
            } else {
			        UIWebSocketServer.setTime("MJ0:" + enemy_budget + ",MJ1:" + (waitTime-byo_yomi-t-1) + ",BY0:" + byo_yomi + ",BY1:" + byo_yomi); // as global viewer mode
            }

          }
					Thread.sleep(1000);
					if(t >= waitTime){ // timeout
						runFlag = false;
						timeout = true;
					}
					t++;
				}catch(InterruptedException e){
					e.printStackTrace();
				}
			}
			if(timeout){
				int loser = player;
				int winner = player == 0 ? 1 : 0;
				System.out.println("Timeout: winner=" + winner + ", loser=" + loser);
				try{
					System.out.println("WON:" + game.getEncodedBoard(winner, true));
					players[winner].send(players[winner].ch, "WON:" + game.getEncodedBoard(winner, true) + "\r\n");
					System.out.println("LST:" + game.getEncodedBoard(loser, true));
					players[loser].send(players[loser].ch, "LST:" + game.getEncodedBoard(loser, true) + "\r\n");
					players[0].close(); // force stop
					players[1].close(); // force stop
				}catch(IOException e){
				}
			}
		}
			
	}

	
	private void close() throws IOException {
		players[0].close();
		players[1].close();
	}

	public static void main(String[] args) throws Exception {
		System.out.println("TCPSrverWithBlock");
		GetOpt opt = new GetOpt("", "no_ng_terminate,set_player_name_server,set_player_name_client,timeout:,budget:,wait:,max-turn:,battle-times:", args);
		boolean ng_terminate = !opt.flag("no_ng_terminate");

    boolean set_player_name_server = opt.flag("set_player_name_server");
    boolean set_player_name_client = opt.flag("set_player_name_client");
    Scanner scanner = new Scanner(System.in);
    String n;
    Pattern illegalFileNamePattern = Pattern.compile("\\\\|/|:|\\*|\\?|\"|<|>|\\|");

		int budget = 10*60; // 10min.
		if(opt.flag("budget")){
			budget = Integer.parseInt(opt.getValue("budget"));
		}
		int max_turn = Constant.MAX_TURN_COUNT;
		if(opt.flag("maxturn")){
			max_turn = Integer.parseInt(opt.getValue("max-turn"));
		}
		TCPServerWithBlock s = new TCPServerWithBlock(new GameServer(ng_terminate, set_player_name_client, max_turn), budget);
		if(opt.flag("timeout")){
			s.setWaitTime(Integer.parseInt(opt.getValue("timeout")));
		}
		if(opt.flag("wait")){
			waitViewerTime = Integer.parseInt(opt.getValue("wait"));
		}
    if(opt.flag("battle-times")){
      battle_times = Integer.parseInt(opt.getValue("battle-times"));
    }
		System.out.println("set_player_name_server = " + set_player_name_server);
		System.out.println("set_player_name_client = " + set_player_name_client);
		System.out.println("Budget = " + budget + " sec.");
		System.out.println("Timeout(after consuming budget) = " + s.getWaitTime() + " sec.");
		s.webSocketServer.start();
		while(true){
      if(set_player_name_server) {
        System.out.print("SET PLAYER_0 NAME > ");
        n = scanner.nextLine();
        n = illegalFileNamePattern.matcher(n).replaceAll("-");
        s.server.setName(n, 0);
        System.out.print("SET PLAYER_1 NAME > ");
        n = scanner.nextLine();
        n = illegalFileNamePattern.matcher(n).replaceAll("-");
        s.server.setName(n, 1);
      }

		  UIWebSocketServer.setResult("win:" + 0 + ",lose:" + 0 + ",draw:" + 0); // as global viewer mode
      // --multi_battle用追加--
      if(battle_times==0){
        s.server.init();
			  s.start();
			  s.close();
			  s.server.close();
      }
      else{
      // int num = 3;  // とりあえず5戦
      // 対戦結果保持
        int win = 0;
        int lose = 0;
        int draw = 0;
        for(int i = 0; i < battle_times; i++) {
          s.server.init();
          s.start();
          switch(s.server.getWinner()){
            case 0:
              win++;
              break;
            case 1:
              lose++;
              break;
            case 2:
              draw++;
              break;
          }
          System.out.println("------ RESULT (win, lose, draw) = ("+win+", "+lose+", "+draw+")------");
          s.close();
          s.server.close();
		      UIWebSocketServer.setResult("win:" + win + ",lose:" + lose + ",draw:" + draw); // as global viewer mode
        }
      }
		}
	}

}
