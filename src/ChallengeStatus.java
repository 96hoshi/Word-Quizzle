/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

// Class that maintains the status of a challenger during a translation game.
// Contains the number of word translated correctly, wrongly, the score and the
// index to track the word to translate
public class ChallengeStatus {

	private int wordIndex;
	private int score;
	private int corrects;
	private int errors;

	public ChallengeStatus() {
		this.wordIndex = 0;
		this.score = 0;
		this.corrects = 0;
		this.errors = 0;
	}

	public int getScore() {
		return score;
	}
	
	public void addScore(int score) {
		this.score += score;
	}

	public int getWordIndex() {
		return wordIndex;
	}
	
	public void incrementWordIndex() {
		this.wordIndex++;
	}

	public int getErrors() {
		return errors;
	}

	public void incrementErrors() {
		this.errors++;
	}

	public int getCorrects() {
		return corrects;
	}

	public void incrementCorrects() {
		this.corrects++;
	}
}
