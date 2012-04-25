package server;

import java.net.InetSocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;

public class Server extends WebSocketServer {
	final Map<String,Object> clients;
	final Map<WebSocket,ClientInfo> sock2client;
	Game game;
	ClientInfo admin;
	
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Please specify exactly one parameter: the port where to listen.");
			System.exit(1);
		}
		int port = Integer.parseInt(args[0]);
		Server srv = new Server (new InetSocketAddress(port));
		srv.start();
	}
	
	public Server(InetSocketAddress address) {
		super(address);
		currentID = new AtomicLong();
		clients = new HashMap<String,Object> ();
		sock2client = new HashMap<WebSocket, ClientInfo> ();
		System.out.println("Listening on: "+address);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		ClientInfo newClient = new ClientInfo(conn, getNewID());
		try {
			newClient.send("YOU"+newClient.toString());
			if (game != null)
				newClient.send('G'+game.toString());
			synchronized (clients) {
				if (clients.isEmpty()) {
					admin = newClient;
					newClient.send("A"+admin);
					System.out.println("New admin: "+admin);
				} else {
					for (Object o : clients.values()) {
						ClientInfo c = (ClientInfo) o;
						newClient.send("PL"+c.toString()+" "+c.score);
						if (c.nick != null)
							newClient.send("PN"+c.toString()+" "+c.nick);
					}
					newClient.send("A"+admin);
				}
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
			System.out.println("some client wanted to connect, but then some error happened.");
			conn.close(500);
			return;
		}
		broadcast ("PL"+newClient.toString()+" 0");
		synchronized (clients) {
			clients.put(newClient.toString(), newClient);
			sock2client.put(conn, newClient);
		}
		System.out.println("Client "+newClient+" successfully connected");
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		ClientInfo leaver = sock2client.get(conn);
		System.out.println("Client "+leaver+" aka "+leaver.getNick()+ " is leaving.");
		synchronized (clients) {
			clients.remove(leaver.toString());
			sock2client.remove(conn);
			if (leaver == admin) {
				if (clients.isEmpty()) {
					admin = null;
					System.out.println("nobody is left, so admin is now null");
				} else {
					admin = (ClientInfo) clients.values().iterator().next();
					System.out.println("The leaver was admin. The new admin is "+admin+" aka "+admin.getNick());
					broadcast ("A"+admin);
				}
			}
		}
		broadcast("PO"+leaver);
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		try {
			onMessageUnsafe(conn, message);
		} catch (Exception e) {
			System.out.println("EXCEPTION in onMessageUnsafe!");
			e.printStackTrace(System.out);
			synchronized (clients) {
				if (sock2client.containsKey(conn)) {
					ClientInfo cl = sock2client.get(conn);
					sock2client.remove(conn);
					clients.remove(cl.toString());
				}
			}
			conn.close(500);
		}
	}
	
	public void onMessageUnsafe(WebSocket conn, String what) {
		ClientInfo who = sock2client.get(conn);
		if (what.isEmpty()) {
			System.err.println("Warning: Ignoring empty command");
			return;
		}
		switch (what.charAt(0)) {
		case 'M': // chat message
			// security check
			if (what.contains("\n")) {
				who.sendUnsafe("NO: no newline allowed in message");
				return;
			}
			if (what.length() > 160) {
				who.sendUnsafe("NO: only 160 characters allowed in chat message");
				return;
			}
			broadcast ('M'+(who.nick==null ? "player"+who.player_id : who.nick) + ": " + what.substring(1));
			break;
		case 'G': // new game cols rows minecount
			synchronized (this) {
				if (game == null || game.isGameOver() || who==admin) {
					System.out.println("Starting new game: "+what);
					try {
						game = Game.fromString(what.substring(1),clients);
						broadcast ('G'+game.toString());
					} catch (IllegalArgumentException e) {
						who.sendUnsafe("NO: "+e.getMessage());
						System.out.println("Didn't start game because: "+e.getMessage());
					}
				} else {
					System.out.println(who+" tried creating a game while a game was already there.");
					who.sendUnsafe("NO: There is already a game running");
				}
			}
			break;
		case 'C': // click col row
			if (game == null) {
				System.out.println(who+" tried to click while there was no game existing");
				who.sendUnsafe("NO: There is currently no board to click on");
			} else {
				if (what.length() < 4) {
					// C[col] [row]
					who.sendUnsafe("NO: invalid click message: "+what);
					return;
				}
				String[] nums = what.substring(1).split(" ");
				if (nums.length != 2) {
					who.sendUnsafe("NO: A click needs exactly 2 arguments: column and row.");
					return;
				}
				String result = game.click (who, Integer.parseInt(nums[0]), Integer.parseInt(nums[1]));
				if (result.startsWith("NO")) {
					who.sendUnsafe(result);
				} else {
					broadcast(result);
					if (game.isGameOver()) {
						broadcast ("OVER");
					}
				}
				// print scores
				/*System.out.println("Scores:");
				synchronized (clients) {
					for (Object o : clients.values()) {
						ClientInfo cl = (ClientInfo)o;
						System.out.println(cl.getNick()+" has "+cl.score+" points");
					}
				}
				System.out.println();*/
			}
			break;
		case 'N': // nickname
			if (who.nick == null) {
				if (what.length() <= 1) {
					who.sendUnsafe("NO: Nickname cannot be empty.");
					return;
				}
				String newnick = what.substring(1);
				// security check
				if (newnick.contains("\n") || newnick.contains(": ")) {
					who.sendUnsafe("NO: no newline or \": \" allowed in nickname");
					return;
				}
				synchronized (clients) {
					for (Object o : clients.values()) {
						ClientInfo c = (ClientInfo) o;
						if (newnick.equalsIgnoreCase(c.nick)) {
							who.sendUnsafe("NO: this nick was already taken by player "+c.toString());
							return;
						}
					}
				}
				who.nick = newnick;
				broadcast ("PN"+who.toString()+' '+who.nick);
			} else {
				who.sendUnsafe("NO: You already have a nickname");
			}
			break;
		default:
			System.out.println("Client "+who+" unknown command: "+what);
			who.sendUnsafe("NO: unknown command: "+what);
		}
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		System.out.println("ERROR!!!");
	}

	void broadcast (String msg) {
		synchronized (clients) {
			for (Object clo : clients.values()) {
				ClientInfo cl = (ClientInfo) clo;
				try {
					cl.send(msg);
				} catch (NotYetConnectedException e) {
					e.printStackTrace();
					System.out.println("client "+cl+" aka "+cl.getNick()+" was not connected yet (wtf?!)");
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
					System.out.println("somehow the string \""+msg+"\" was not okay for client "+cl+" aka "+cl.getNick());
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.out.println("interrupted, so let's suicide");
					System.exit(1);
				}
			}
		}
	}
	
	private AtomicLong currentID;
	public long getNewID () {
		return currentID.incrementAndGet();
	}
}
