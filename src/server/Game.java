package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class Game {
	public static final int MINE_SCORE = -1000; // your "score" for "exploring" a mine
	public static final int EXPLORE_SCORE = 25; // you get this for every explored non-mine
	public static final int DANGER_SCORE = 50; // you explore a field with number n -> you get n times this score

	int fieldsLeft;

	protected class Field {
		public int minesAround;
		public boolean isMine;
		public Object explorer;
		public Field() {
			minesAround = 0;
			isMine = false;
			explorer = null;
		}
		public Field(String str, Map<String,Object> explorers) {
			assert str.length() >= 1;
			char fst = str.charAt(0);
			switch (fst) {
			case '_':
				assert str.length() == 1;
				break;
			case 'X':
				isMine = true;
				break;
			default:
				assert fst >= '0' && fst <= '8';
				minesAround = fst - '0';
			}
			if (str.length() > 1) {
				synchronized (explorers) {
					explorer = explorers.get(str.substring(1));
				}
			}
		}
		@Override
		public String toString() {
			if (explorer == null)
				return "_";
			String ret = isMine ? "X" : String.valueOf(minesAround);
			return ret+explorer.toString();
		}
	}
	
	final Field[][] board;
	final int width, height, mineCount;
	Map<String,Object> explorers;
	
	public Game(int width, int height, int mineCount, Map<String,Object> explorers) {
		if (width * height < mineCount * 3) {
			throw new IllegalArgumentException("1/3 of fields can be mines at max");
		}
		if (width > 64 || height > 64) {
			throw new IllegalArgumentException("too big game field");
		}
		if (width < 1 || height < 1 || mineCount < 0)
			throw new IllegalArgumentException("min: 1x1 with 0 mines");
		this.board = new Field[width][height];
		this.width = width;
		this.height = height;
		this.mineCount = mineCount;
		this.explorers = explorers;
		this.fieldsLeft = width*height-mineCount;
		ArrayList<Field> fieldList = new ArrayList<Field>();
		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				board[col][row] = new Field ();
				fieldList.add(board[col][row]);
			}
		}
		Collections.shuffle(fieldList);
		int minesSet = 0;
		for (Field f : fieldList) {
			if (minesSet < mineCount) {
				f.isMine = true;
				minesSet++;
			} else {
				break;
			}
		}
		for (int row=0; row < height; row++) {
			for (int col=0; col < width; col++) {
				for (int offx = -1; offx <= 1; offx++) {
					for (int offy = -1; offy <=1; offy++) {
						if (isMine(col+offx,row+offy))
							board[col][row].minesAround++;
					}
				}
			}
		}
	}
	public static Game fromString (String string, Map<String,Object> explorers) {
		String[] infos = string.split(" ");
		if (infos.length != 3 && infos.length != 2)
			throw new IllegalArgumentException("NO: new game takes 2 or 3 parameters: width, height and optionally mine count");
		// NumberFormatException is an IllegalArgumentException
		int width = Integer.parseInt(infos[0]);
		int height = Integer.parseInt(infos[1]);
		int mineCount;
		if (infos.length > 2)
			mineCount = Integer.parseInt(infos[2]);
		else
			mineCount = width*height/5; // 20% mines
		return new Game (width, height, mineCount, explorers);
	}
	protected synchronized boolean isMine(int col, int row) {
		return col >= 0 && col < width && row >= 0 && row < height && board[col][row].isMine; 
	}
	boolean isExplored (int col, int row) {
		return col >= 0 && col < width && row >= 0 && row < height && board[col][row].explorer != null;
	}
	@Override
	public synchronized String toString() {
		StringBuilder sb = new StringBuilder(height*width);
		sb.append(width);
		sb.append(' ');
		sb.append(height);
		sb.append(' ');
		sb.append(mineCount);
		for (int col=0; col<width; col++) {
			for (int row=0; row<height; row++) {
				sb.append(' ');
				sb.append(board[col][row].toString());
			}
		}
		return sb.toString();
	}
	public synchronized String click(Object who, int col, int row) {
		try {
			int score = getScore(col,row);
			if (board[col][row].explorer == null) {
				if (!board[col][row].isMine) {
					fieldsLeft--;
				}
				board[col][row].explorer = who;
			}
			if (score != 0)
				((ClientInfo)who).score+=score;
			StringBuilder sb = new StringBuilder();
			sb.append('F');
			sb.append(col);
			sb.append(' ');
			sb.append(row);
			sb.append(' ');
			sb.append(score);
			sb.append(' ');
			sb.append(board[col][row].toString());
			return sb.toString();
		} catch (ArrayIndexOutOfBoundsException e) {
			return "NO: the given coordinate "+col+" "+row+" is not in bounds of the game.";
		}
	}
	boolean isUnseen (int col, int row) {
		for (int dx = -1; dx <= 1 ; dx++) {
			for (int dy = -1; dy <=1; dy++) {
				if (isExplored(col+dx, row+dy))
					return false;
			}
		}
		return true;
	}
	int getScore(int col, int row) {
		if (isExplored(col, row))
			return 0; // previously explored
		if (isMine(col, row))
			return MINE_SCORE;
		if (isUnseen(col,row)) {
			// so if you click everything unseen you have 0 points at the end
			return -MINE_SCORE * mineCount / (width*height-mineCount);
		} else {
			return EXPLORE_SCORE + board[col][row].minesAround * DANGER_SCORE;
		}
	}
	public boolean isGameOver () {
		return fieldsLeft == 0;
	}
}
