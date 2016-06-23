package org.codedefenders.singleplayer;

import difflib.Patch;
import difflib.PatchFailedException;
import org.codedefenders.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.regex.Pattern;

import static org.codedefenders.Constants.*;

/**
 * @author Ben Clegg
 * An AI attacker, which chooses mutants generated by Major when the class is uploaded.
 */
public class AiAttacker extends AiPlayer {

	private enum GenerationMethod {
		RANDOM, //Randomly select mutant.
		COVERAGE, //Select random mutant by least covered lines.
		FIRST //Choose the first mutant, for debugging.
	}

	public AiAttacker(Game g) {
		super(g);
		role = Game.Role.ATTACKER;
	}

	private int totalMutants() {
		return getMutantList().size();
	}

	private List<String> getMutantList() {
		String loc = AI_DIR + F_SEP + "mutants" + F_SEP + game.getClassName() + ".log";
		File f = new File(loc);
		List<String> l = FileManager.readLines(f.toPath());
		//TODO: Handle errors.
		return l;
	}

	/**
	 * Get information for a given mutant.
	 * @param mutantNum The mutant to use.
	 * @return The class after the mutant has been applied.
	 */
	private MutantPatch getMutantPatch(int mutantNum) {
		//Get the mutant information from the log.
		String mInfo = getMutantList().get(mutantNum);
		//Split into each component.
		String[] splitInfo = mInfo.split(":");
		/*
		0 = Mutant's id
		1 = Name of mutation operator
		2 = Original operator symbol
		3 = New operator symbol
		4 = Full name of mutated method
		5 = Line number of CUT
		6 = 'from' |==> 'to'  (<NO-OP> means empty string)
		 */
		//Only really need values 5 and 6.
		//Use replace option?

		int lineNum = Integer.parseInt(splitInfo[5]);
		String[] beforeAfter = splitInfo[6].split(Pattern.quote(" |==> ")); //Before = 0, After = 1

		return new MutantPatch(lineNum, beforeAfter);
	}

	/**
	 * Modify a list of strings from a class to have a mutant.
	 * @param lines original class's lines.
	 * @param patch the mutant's patch information.
	 * @return the new list of lines.
	 */
	private List<String> doPatch(List<String> lines, MutantPatch patch) {
		//Copy contents of original lines.
		List<String> newLines = new ArrayList<String>(lines);
		String l = newLines.get(patch.getLineNum() - 1);
		String newLine = l.replaceFirst(patch.getOriginal(), patch.getReplacement().replace("QE", ""));
		newLines.set(patch.getLineNum() - 1, newLine);
		return newLines;
	}

	/**
	 * Get the text of the full mutated class.
	 * @param strategy which mutant selection strategy to use.
	 * @return the mutant's text, or nothing if too many mutants which already exist have been made.
	 */
	private String createMutantString(GenerationMethod strategy) {
		for (int i = 0; i < 10; i++) {
			int mId = 1;
			if(strategy.equals(GenerationMethod.RANDOM)) {
				mId = (int)Math.floor(Math.random() * totalMutants()); //Choose a mutant.
			}
			else if (strategy.equals(GenerationMethod.COVERAGE)) {
				//Coverage method.
			}
			//Get original lines.
			File cutFile = new File(game.getCUT().javaFile);
			List<String> cutLines = FileManager.readLines(cutFile.toPath());
			//TODO: Don't reuse mutant.
			//Patch lines with selected mutant.
			MutantPatch p = getMutantPatch(mId);
			List<String> newLines = doPatch(cutLines, p);
			String mText = "";
			if(isUnique(newLines, cutLines)) {
				for (String l : newLines) {
					mText += l + "\n";
				}
				return mText;
			}
		}
		return ""; //Return empty string if all attempted mutants already exist.
	}

	private boolean isUnique(List<String> patched, List<String> cut) {
		ArrayList<Mutant> existingMuts = game.getAliveMutants();
		for (Mutant m : existingMuts) {
			List<String> original = new ArrayList<String>(cut);
			Patch p = m.getDifferences();
			try {
				if(patched.equals(p.applyTo(original))) {
					return false;
				}
			} catch (PatchFailedException e) {
				e.printStackTrace();
			}
		}
		return true;
	}

	private boolean createMutant(String mutantText) {
		GameManager gm = new GameManager();
		try {
			//Create mutant and insert it into the database.
			Mutant m = gm.createMutant(game.getId(), game.getClassId(), mutantText, 1);
			//TODO: More error checking.
			ArrayList<String> messages = new ArrayList<String>();
			MutationTester.runAllTestsOnMutant(game, m, messages);
		} catch (IOException e) {
			//Try again if an exception.
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Hard difficulty attacker turn.
	 * @return true if mutant generation succeeds, or if no non-existing mutants have been found to prevent infinite loop.
	 */
	public boolean turnHard() {
		//Use only one mutant per round.
		//Perhaps modify the line with the least test coverage?

		//TODO: Determine by lowest test coverage. Using medium behaviour for now.
		return turnMedium();
	}

	/**
	 * Medium difficulty attacker turn.
	 * @return true if mutant generation succeeds, or if no non-existing mutants have been found to prevent infinite loop.
	 */
	public boolean turnMedium() {
		//Use one randomly selected mutant per round, without reusing an old one.
		String mText = createMutantString(GenerationMethod.RANDOM); //Create mutant string.
		if(mText.isEmpty()) {
			//End the game if all empty strings exist.
			System.out.println("Attempted mutants exist.");
			//TODO: Check that this works.
			game.setState(Game.State.FINISHED);
			game.update();
			return true;
		}

		return createMutant(mText);
	}

	/**
	 * Easy difficulty attacker turn.
	 * @return true if mutant generation succeeds, or if no non-existing mutants have been found to prevent infinite loop.
	 */
	public boolean turnEasy() {
		//Use "multiple" mutants per round, up to maximum amount.
		//Mutants are combined to make a single mutant, which would therefore be easier to detect and kill.
		//Will have to check line number is different if using multiple mutants.
		double maxNumMutants = Math.floor(totalMutants() / game.getFinalRound());

		return true;
	}
}

class MutantPatch {
	private int line;
	private String original;
	private String replacement;

	/**
	 * A datatype to represent key mutant information
	 * @param lineNumber The linenumber which the mutant is applied to
	 * @param beforeAfter Sub-strings of the mutated line, before and after mutation.
	 */
	protected MutantPatch(int lineNumber, String[] beforeAfter) {
		line = lineNumber;
		original = beforeAfter[0];
		replacement = beforeAfter[1];
		if (replacement.matches("<NO-OP>")) {
			replacement = "";
			original += ";";
		}
	}

	protected int getLineNum() {
		return line;
	}
	protected String getOriginal() {
		return original;
	}
	protected String getReplacement() {
		return replacement;
	}

}