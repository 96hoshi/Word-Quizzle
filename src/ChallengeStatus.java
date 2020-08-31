public class ChallengeStatus {

	private int wordIndex;
	private int score;

	public ChallengeStatus() {
		this.setWordIndex(0);
		this.setScore(0);
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}
	
	public void addScore(int score) {
		this.score += score;
	}

	public int getWordIndex() {
		return wordIndex;
	}

	public void setWordIndex(int wordIndex) {
		this.wordIndex = wordIndex;
	}
	
	public void incrementWordIndex() {
		this.wordIndex++;
	}
}
