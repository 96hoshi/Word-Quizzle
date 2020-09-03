/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class WQDatabase {

	private static final String DB_PATH = "WQ-Database.json";
	public ConcurrentHashMap<String, User> database;

	public WQDatabase() {
		File databaseFile = new File(DB_PATH);
		if (databaseFile.exists()) {
			restoreDB();
		} else {
			database = new ConcurrentHashMap<>();
		}
	}

	public boolean addUser(String username, String password) {
		if (username == null || password == null)
			throw new NullPointerException();

		if (database.containsKey(username)) {
			return false;
		}
		database.put(username, new User(username, password));
		updateDB();
		return true;
	}

	public void updateScore(String username, int score) {
		if (username == null)
			throw new NullPointerException();

		User usr = (User) database.get(username);
		usr.addScore(score);
		updateDB();
	}

	public int getScore(String username) {
		if (username == null)
			throw new NullPointerException();

		User usr = (User) database.get(username);
		return usr.getScore();
	}

	public boolean updateFriendList(String username, String friendname) {
		if (username == null || friendname == null)
			throw new NullPointerException();

		User usr = (User) database.get(username);
		User friend = (User) database.get(friendname);

		if (usr != null && friend != null) {
			if (usr.addFriend(friendname)) {
				friend.addFriend(username);
				updateDB();
				return true;
			}
		}
		return false;
	}

	public LinkedHashSet<String> getFriendList(String username) {
		if (username == null)
			throw new NullPointerException();

		User usr = (User) database.get(username);
		return usr.getFriendlist();
	}

	public boolean findUser(String username) {
		if (username == null)
			throw new NullPointerException();

		return database.containsKey(username);
	}

	public boolean matchPassword(String username, String password) {
		if (username == null || password == null)
			throw new NullPointerException();

		User usr = (User) database.get(username);
		if (usr != null) {
			return usr.getPassword().equals(password);
		}
		return false;
	}

	public SortedSet<Entry<String, Integer>> getRanking(String username) {
		if (username == null)
			throw new NullPointerException();

		Map<String, Integer> usrRanking = new TreeMap<>();
		LinkedHashSet<String> friends = getFriendList(username);

		User mainUsr = (User) database.get(username);
		usrRanking.put(username, mainUsr.getScore());

		for (String name : friends) {
			User usr = (User) database.get(name);
			usrRanking.put(name, usr.getScore());
		}
		return entriesSortedByValues(usrRanking);
	}

//	Support function to order rankings in descending way
	private static <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>> entriesSortedByValues(
			Map<K, V> map) {
		if (map == null)
			throw new NullPointerException();

		SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<Map.Entry<K, V>>(new Comparator<Map.Entry<K, V>>() {
			@Override
			public int compare(Map.Entry<K, V> e1, Map.Entry<K, V> e2) {
				int res = -e1.getValue().compareTo(e2.getValue());
				return res != 0 ? res : 1;
			}
		});
		sortedEntries.addAll(map.entrySet());
		return sortedEntries;
	}

//	Read from JSON file and restore Quizzle database 
	private void restoreDB() {
		try {
			Gson gson = new Gson();
			Reader reader = Files.newBufferedReader(Paths.get(DB_PATH));
			// Specify the correct parameterized type for database
			Type mapType = new TypeToken<ConcurrentHashMap<String, User>>() {
			}.getType();
			// Convert JSON file to map
			ConcurrentHashMap<String, User> map = gson.fromJson(reader, mapType);
			this.database = map;
			reader.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

//	Need to be synchronized since more than one thread can call this function
	private synchronized void updateDB() {
		try {
			Writer writer = new FileWriter(DB_PATH);
			// Convert map to JSON File
			new Gson().toJson(this.database, writer);
			// Close the writer
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
