
import java.util.LinkedHashSet;

public class User {
	
	private final String username;
	private final String password;
	private LinkedHashSet<String> friendList;
	private int score;

	public User(String username, String password) {
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
		return(friendList.add(friendname));
	}

}
