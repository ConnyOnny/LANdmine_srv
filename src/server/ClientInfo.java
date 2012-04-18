package server;

import java.nio.channels.NotYetConnectedException;

import org.java_websocket.WebSocket;

public class ClientInfo {
	protected final WebSocket sock;
	protected String nick;
	public final long player_id;
	private int errorCount;
	public int score;
	final static int maxErrors = 5;
	public ClientInfo(WebSocket sock, long player_id) {
		this.sock = sock;
		this.player_id = player_id;
	}
	public String getNick() {
		return nick;
	}
	public void send (String msg) throws NotYetConnectedException, IllegalArgumentException, InterruptedException {
		sock.send(msg);
		errorCount = 0;
	}
	@Override
	public String toString() {
		return String.valueOf(player_id);
	}
	public void sendUnsafe(String msg) {
		if (errorCount >= maxErrors) {
			System.out.println("Client "+this+" aka "+getNick()+" had too many errors");
			sock.close(42);
			return;
		}
		try {
			send (msg);
			errorCount = 0;
		} catch (Exception e) {
			System.out.println("Exception while unsafe sending \""+msg+"\"to client "+this+" aka "+getNick());
			e.printStackTrace();
			errorCount++;
		}
	}
}
