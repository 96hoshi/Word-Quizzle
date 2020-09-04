
/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.util.LinkedHashSet;

// Class that represent a Word Quizzle user.
// It maintains all informations needed to identify a user and to save their data.
public class User {

	private final String username;
	private final String password;
	private LinkedHashSet<String> friendList;
	private int score;

	public User(String username, String password) {
		if (username == null || password == null)
			throw new NullPointerException();

		this.username = username;
		this.password = password;
		friendList = new LinkedHashSet<String>();
		score = 0;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public int getScore() {
		return score;
	}

	public void addScore(int score) {
		this.score += score;
	}

	public LinkedHashSet<String> getFriendlist() {
		return friendList;
	}

	public boolean addFriend(String friendname) {
		if (friendname == null)
			throw new NullPointerException();

		return friendList.add(friendname);
	}
}
