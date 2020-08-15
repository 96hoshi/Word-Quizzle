import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class WQDatabase {

	private final String pathDB = "WQ-Database.json";
	public ConcurrentHashMap<String, User> database;

	public WQDatabase() {
		File WQDatabase = new File(pathDB);
		if (WQDatabase.exists()) {
			restoreDB();
		} else {
			this.database = new ConcurrentHashMap<>();
		}
	}

	public boolean addUser(String username, String password) {
		if (database.containsKey(username)) {
			return false;
		}
		database.put(username, new User(username, password));
		updateDB();
		return true;
	}

	public void updateScore(String username, int score) {
		User usr = database.get(username);
		usr.addScore(score);
		updateDB();
	}

	public void updateFriendList(String username, String friendname) {
		User usr = database.get(username);
		usr.addFriend(friendname);
		updateDB();
	}

	public boolean findUser(String username) {
		return database.containsKey(username);
	}

	public boolean matchPassword(String username, String password) {
		User usr = database.get(username);
		if (usr != null) {
			return usr.getPassword().equals(password);
		}
		return false;
	}

	private void restoreDB() {
		try {
			// create Gson instance
			Gson gson = new Gson();
			// create a reader
			Reader reader = Files.newBufferedReader(Paths.get(pathDB));
			// specify the correct parameterized type for database
			Type mapType = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
			// convert JSON file to map
			ConcurrentHashMap<String, User> map = gson.fromJson(reader, mapType);
			this.database = map;
			// close reader
			reader.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

//	need to be synchronized since more than one thread can call this function
	private synchronized void updateDB() {
		try {
			Writer writer = new FileWriter(pathDB);
			// convert map to JSON File
			new Gson().toJson(this.database, writer);
			// close the writer
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}


}
