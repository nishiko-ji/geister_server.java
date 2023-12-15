package net.wasamon.geister.server;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/geister")
public class UIWebSocketServer {

	private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();
	
	public static String mesg = "null";
	public static String time = "null,null,null,null";
	public static String name = "null,null";
  public static String result = "null,null,null,null,null,null";
  public static String turn = "null";
  public static String ch = "null";
	
	synchronized static void setMesg(String mesg){
		UIWebSocketServer.mesg = mesg;
	}

	synchronized static void setTime(String time){
		UIWebSocketServer.time = time;
	}

	synchronized static void setName(String name){
		UIWebSocketServer.name = name;
	}

  synchronized static void setResult(String result){
    UIWebSocketServer.result = result;
  }

  synchronized static void setTurn(String turn){
    UIWebSocketServer.turn = turn;
  }

  synchronized static void setCh(String ch){
    UIWebSocketServer.ch = ch;
  }

	synchronized static String getMesg(){
		return UIWebSocketServer.mesg;
	}

	synchronized static String getTime(){
		return UIWebSocketServer.time;
	}

	synchronized static String getName(){
		return UIWebSocketServer.name;
	}

  synchronized static String getResult(){
    return UIWebSocketServer.result;
  }

  synchronized static String getTurn(){
    return UIWebSocketServer.turn;
  }

  synchronized static String getCh(){
    return UIWebSocketServer.ch;
  }

	static {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleWithFixedDelay(UIWebSocketServer::broadcast, 500, 500, TimeUnit.MILLISECONDS);
	}

	@OnOpen
	public void connect(Session session) {
		sessions.add(session);
	}

	@OnClose
	public void remove(Session session) {
		sessions.remove(session);
	}

	public static void broadcast() {
		String mesg = getMesg();
		String time = getTime();
		String name = getName();
		sessions.forEach(session -> {
            session.getAsyncRemote().sendText(mesg + ',' + time + ',' + name + ',' + result + ',' + turn + ',' + ch);
		});
	}
}
