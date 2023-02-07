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
	public static String time = "null";
	public static String name = "null";
	
	synchronized static void setMesg(String mesg){
		UIWebSocketServer.mesg = mesg;
	}

	synchronized static void setTime(String time){
		UIWebSocketServer.time = time;
	}

	synchronized static void setName(String name){
		UIWebSocketServer.name = name;
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
            session.getAsyncRemote().sendText(mesg + ',' + time + ',' + name);
		});
	}
}
