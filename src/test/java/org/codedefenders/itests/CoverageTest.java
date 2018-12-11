package org.codedefenders.itests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.codedefenders.database.DatabaseConnection;
import org.codedefenders.execution.MutationTester;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.GameLevel;
import org.codedefenders.game.GameState;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Role;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.model.User;
import org.codedefenders.rules.DatabaseRule;
import org.codedefenders.servlets.games.GameManager;
import org.codedefenders.util.Constants;
import org.codedefenders.validation.code.CodeValidatorException;
import org.codedefenders.validation.code.CodeValidatorLevel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@Category(IntegrationTest.class)
@RunWith(PowerMockRunner.class)
@PrepareForTest({ DatabaseConnection.class })
public class CoverageTest {

	@Rule // Look for the file on the classpath
	public DatabaseRule db = new DatabaseRule("defender", "db/emptydb.sql");

	// PROBLEM: @ClassRule cannot be used with PowerMock ...
	private static File codedefendersHome;

	@BeforeClass
	public static void setupEnvironment() throws IOException {
		codedefendersHome = Files.createTempDirectory("integration-tests").toFile();
		codedefendersHome.deleteOnExit();
	}

	// This factory enable to configure codedefenders properties
	public static class MyContextFactory implements InitialContextFactory {
		@Override
		public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
			System.out.println("ParallelizeAntRunnerTest.MyContextFactory.getInitialContext()");
			InitialContext mockedInitialContext = PowerMockito.mock(InitialContext.class);
			NamingEnumeration<NameClassPair> mockedEnumeration = PowerMockito.mock(NamingEnumeration.class);
			// Look at this again ...
			PowerMockito.mockStatic(NamingEnumeration.class);
			//
			PowerMockito.when(mockedEnumeration.hasMore()).thenReturn(true, true, true, true, false);
			PowerMockito.when(mockedEnumeration.next()).thenReturn(
					new NameClassPair("data.dir", String.class.getName()),
					new NameClassPair("parallelize", String.class.getName()),
					new NameClassPair("mutant.coverage", String.class.getName()),
					new NameClassPair("ant.home", String.class.getName())//
			);
			//
			PowerMockito.when(mockedInitialContext.toString()).thenReturn("Mocked Initial Context");
			PowerMockito.when(mockedInitialContext.list("java:/comp/env")).thenReturn(mockedEnumeration);
			//
			Context mockedEnvironmentContext = PowerMockito.mock(Context.class);
			PowerMockito.when(mockedInitialContext.lookup("java:/comp/env")).thenReturn(mockedEnvironmentContext);

			PowerMockito.when(mockedEnvironmentContext.lookup("mutant.coverage")).thenReturn("enabled");
			// FIXMED
			PowerMockito.when(mockedEnvironmentContext.lookup("parallelize")).thenReturn("disabled");
			//
			PowerMockito.when(mockedEnvironmentContext.lookup("data.dir"))
					.thenReturn(codedefendersHome.getAbsolutePath());

			PowerMockito.when(mockedEnvironmentContext.lookup("ant.home")).thenReturn("/usr/local");
			//
			return mockedInitialContext;
		}
	}

	@Before
	public void mockDBConnections() throws Exception {
		PowerMockito.mockStatic(DatabaseConnection.class);
		PowerMockito.when(DatabaseConnection.getConnection()).thenAnswer(new Answer<Connection>() {
			public Connection answer(InvocationOnMock invocation) throws SQLException {
				// Return a new connection from the rule instead
				return db.getConnection();
			}
		});
	}

	// TODO Maybe a rule here ?!
	@Before
	public void setupCodeDefendersEnvironment() throws IOException {
		// Initialize this as mock class
		MockitoAnnotations.initMocks(this);
		// Be sure to setup the "java.naming.factory.initial" to the inner
		// MyContextFactory class
		System.setProperty("java.naming.factory.initial", this.getClass().getCanonicalName() + "$MyContextFactory");
		//
		// Recreate codedefenders' folders
		boolean isCreated = false;
		isCreated = (new File(Constants.MUTANTS_DIR)).mkdirs() || (new File(Constants.MUTANTS_DIR)).exists();
		isCreated = (new File(Constants.CUTS_DIR)).mkdirs() || (new File(Constants.CUTS_DIR)).exists();
		isCreated = (new File(Constants.TESTS_DIR)).mkdirs() || (new File(Constants.TESTS_DIR)).exists();
		//
		// Setup the environment
		Files.createSymbolicLink(new File(Constants.DATA_DIR, "build.xml").toPath(),
				Paths.get(new File("src/test/resources/itests/build.xml").getAbsolutePath()));

		Files.createSymbolicLink(new File(Constants.DATA_DIR, "security.policy").toPath(),
				Paths.get(new File("src/test/resources/itests/relaxed.security.policy").getAbsolutePath()));

		Files.createSymbolicLink(new File(Constants.DATA_DIR, "lib").toPath(),
				Paths.get(new File("src/test/resources/itests/lib").getAbsolutePath()));

	}

	@Test
	public void testTestCoverInnerStaticClass() throws FileNotFoundException, IOException, CodeValidatorException {
		// MOVE THIS CODE TO BEFORE OF FACTORY METHOD
		ArrayList<String> messages = new ArrayList<String>();
		// Create the users
		User observer = new User("observer", User.encodePassword("password"), "demo@observer.com");
		observer.insert();
		User attacker = new User("demoattacker", User.encodePassword("password"), "demo@attacker.com");
		attacker.insert();
		User defender = new User("demodefender", User.encodePassword("password"), "demo@defender.com");
		defender.insert();
		// CUT with Inner Static Class
		File cutFolder = new File(Constants.CUTS_DIR, "ClassWithPrivateInnerClass");
		cutFolder.mkdirs();
		File jFile = new File(cutFolder, "ClassWithPrivateInnerClass.java");
		File cFile = new File(cutFolder, "ClassWithPrivateInnerClass.class");
		

		Files.copy(
				Paths.get(
						"src/test/resources/itests/sources/ClassWithPrivateInnerClass/ClassWithPrivateInnerClass.java"),
				new FileOutputStream(jFile));
		Files.copy(
				Paths.get(
						"src/test/resources/itests/sources/ClassWithPrivateInnerClass/ClassWithPrivateInnerClass.class"),
				new FileOutputStream(cFile));
		
		// Also "compile" and copy the inner class
		File innerClassFile = new File(cutFolder, "ClassWithPrivateInnerClass$InnerClass.class");
		Files.copy(
				Paths.get(
						"src/test/resources/itests/sources/ClassWithPrivateInnerClass/ClassWithPrivateInnerClass$InnerClass.class"),
				new FileOutputStream(innerClassFile));

		GameClass cut = new GameClass("ClassWithPrivateInnerClass", "ClassWithPrivateInnerClass", jFile.getAbsolutePath(), cFile.getAbsolutePath());
		cut.insert();
		long startDate= System.currentTimeMillis() - 1000*3600;
		long endDate = System.currentTimeMillis() + 1000*3600;
		// Observer creates a new MP game
		//
		MultiplayerGame multiplayerGame = new MultiplayerGame(cut.getId(), observer.getId(), GameLevel.HARD, 
				(float) 1,
				(float) 1,
				(float) 1, 10, 4, 4, 4, 
				0, 0, 
				startDate, endDate,
				GameState.ACTIVE.name(), false, 2, true,
				CodeValidatorLevel.MODERATE, false);
		multiplayerGame.insert();
		// Attacker and Defender join the game.
		multiplayerGame.addPlayer(defender.getId(), Role.DEFENDER);
		multiplayerGame.addPlayer(attacker.getId(), Role.ATTACKER);
		//  Submit Mutant
		String mutantText = new String(
				Files.readAllBytes(new File("src/test/resources/itests/mutants/ClassWithPrivateInnerClass/ClassWithPrivateInnerClass.java").toPath()),
				Charset.defaultCharset());
		// Do the mutant thingy
		Mutant mutant = GameManager.createMutant(multiplayerGame.getId(), multiplayerGame.getClassId(), mutantText,
				attacker.getId(), "mp");
		//
		MutationTester.runAllTestsOnMutant(multiplayerGame, mutant, messages);
		// Probably I need to store the mutant ?
		multiplayerGame.update();
		
		// Submit the Killing test
		//
		// Compile and test original
		String testText = new String(Files.readAllBytes(new File("src/test/resources/itests/tests/ClassWithPrivateInnerClass/TestClassWithPrivateInnerClass.java").toPath()), Charset.defaultCharset());
		org.codedefenders.game.Test newTest = GameManager.createTest(multiplayerGame.getId(), multiplayerGame.getClassId(),
				testText, defender.getId(), "mp");
		MutationTester.runTestOnAllMultiplayerMutants(multiplayerGame, newTest, messages);
		multiplayerGame.update();
		//
		System.out.println(new Date() + " MutationTesterTest.defend() " + defender.getId() + ": "
				+ messages.get(messages.size() - 1));
		Assert.assertEquals(Constants.TEST_KILLED_LAST_MESSAGE, messages.get(messages.size() - 1));

	}
}