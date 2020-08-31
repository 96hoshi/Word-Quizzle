import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class TranslationHandler {

	private static final String WORDS_PATH = "Italian_dictionary.txt";

	private static final String TRANSLATION_URL = "https://api.mymemory.translated.net/get?q=";
	private static final String LANGPAIR = "!&langpair=it|en";

	private static final int NUM_WORDS = 8;

	private ArrayList<String> dictionary;

	public TranslationHandler() {
		dictionary = new ArrayList<String>();

		try (BufferedReader wordReader = new BufferedReader(new FileReader(WORDS_PATH));) {
			String word;
			while ((word = wordReader.readLine()) != null) {
				dictionary.add(word);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private ArrayList<String> selectWords() {
		ArrayList<String> selectedWords = new ArrayList<String>();

		for (int i = 0; i < NUM_WORDS; i++) {
			selectedWords.add(dictionary.get(ThreadLocalRandom.current().nextInt(0, dictionary.size())));
		}

		return selectedWords;
	}

	public LinkedList<String>[] getWords() throws IOException {
		ArrayList<String> italianWords = selectWords();
		LinkedList<String>[] translations = new LinkedList[NUM_WORDS];

		for (int i = 0; i < italianWords.size(); i++) {
			URL url = new URL(TRANSLATION_URL + URLEncoder.encode(italianWords.get(i), "UTF-8") + LANGPAIR);

			System.out.println(url.toExternalForm());

			URLConnection urlConn = url.openConnection();

			StringBuilder jsonResponse = new StringBuilder();
			String line;

			BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
			while ((line = br.readLine()) != null)
				jsonResponse.append(line);

			translations[i] = new LinkedList<String>();
//			Put the original Italian word at first position
			translations[i].add(italianWords.get(i));

			JsonObject jobj = new Gson().fromJson(jsonResponse.toString(), JsonObject.class);
			JsonArray jsonArr = jobj.get("matches").getAsJsonArray();
//			Retrieve all matched translations
			for (int j = 0; j < jsonArr.size(); j++) {
				JsonObject tmpJobj = jsonArr.get(j).getAsJsonObject();
				translations[i].add(tmpJobj.get("translation").getAsString().toLowerCase().replaceAll("[^a-zA-Z0\\u0020]", ""));
			}
		}
		return translations;
	}
}
