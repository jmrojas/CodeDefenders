package org.codedefenders;

import org.apache.commons.lang.math.IntRange;
import org.codedefenders.util.AdminDAO;
import org.codedefenders.util.DatabaseAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AdminUserMgmt extends HttpServlet {

	private static final char[] LOWER = "abcdefghijklmnopqrstuvwxyz".toCharArray();
	private static final char[] DIGITS = "0123456789".toCharArray();
	private static final char[] PUNCTUATION = "!@#$%&*()_+-=[]|,./?><".toCharArray();
	static final String USER_INFO_TOKENS_DELIMITER = "[ ,;]+";
	private static final Logger logger = LoggerFactory.getLogger(AdminUserMgmt.class);

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		response.sendRedirect(request.getContextPath() + "/" + Constants.ADMIN_USER_JSP);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		HttpSession session = request.getSession();
		// Get their user id from the session.
		int currentUserID = (Integer) session.getAttribute("uid");
		ArrayList<String> messages = new ArrayList<String>();
		session.setAttribute("messages", messages);
		String responsePath = request.getContextPath() + "/admin/users";

		switch (request.getParameter("formType")) {
			case "manageUsers":
				String userToResetIdString = request.getParameter("resetPasswordButton");
				String userToDeleteIdString = request.getParameter("deleteUserButton");
				String userToEditIdString = request.getParameter("editUserInfo");
				if (userToResetIdString != null) {
					messages.add(resetUserPW(Integer.parseInt(userToResetIdString)));
				} else if (userToDeleteIdString != null) {
					messages.add(deleteUser(Integer.parseInt(userToDeleteIdString)));
				} else if (userToEditIdString != null) {
					responsePath = request.getContextPath() + "/" + Constants.ADMIN_USER_JSP + "?editUser=" + userToEditIdString;
				}
				break;
			case "createUsers":
				createUserAccounts(request.getParameter("user_name_list"), messages);
				break;
			case "editUser":
				String uidString = request.getParameter("uid");
				String successMsg = "Successfully updated info for User " + uidString;
				String msg = editUser(uidString, request, successMsg);
				messages.add(msg);
				if (!msg.equals(successMsg))
					responsePath = request.getContextPath() + "/" + Constants.ADMIN_USER_JSP + "?editUser=" + uidString;
				break;
			default:
				System.err.println("Action not recognised");
				break;
		}

		response.sendRedirect(responsePath);
	}

	private String editUser(String uid, HttpServletRequest request, String successMsg) {
		User u = DatabaseAccess.getUser(Integer.parseInt(uid));
		if (u == null)
			return "Error. User " + uid + " cannot be retrieved from database.";

		String name = request.getParameter("name");
		String email = request.getParameter("email");
		String password = request.getParameter("password");
		String confirm_password = request.getParameter("confirm_password");

		if (!password.equals(confirm_password))
			return "Error! Passwords don't match!";

		if (!name.equals(u.getUsername()) && DatabaseAccess.getUserForNameOrEmail(name) != null)
			return "Username " + name + " is already taken";

		if(!email.equals(u.getEmail()) && DatabaseAccess.getUserForNameOrEmail(email) != null)
			return "Email " + email + " is already in use!";

		if(!LoginManager.validEmailAddress(email))
			return "Email Address is not valid";

		if (!password.equals("")) {
			// we don't want to encode the already encoded password from the DB
			if(!LoginManager.validPassword(password))
				return "Password is not valid";
			BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
			password = passwordEncoder.encode(password);
		}
		u.setUsername(name);
		u.setEmail(email);

		if (!u.update(password))
			return "Error trying to update info for user " + uid + "!";
		return successMsg;
	}

	private void createUserAccounts(String userNameListString, ArrayList<String> messages) {
		if (userNameListString != null) {
			for (String nameOrEmail : userNameListString.split(AdminGamesMgmt.USER_NAME_LIST_DELIMITER)) {
				nameOrEmail = nameOrEmail.trim();
				String name, email, password;

				if (nameOrEmail.length() > 0) {
					if (nameOrEmail.split(USER_INFO_TOKENS_DELIMITER).length > 1) {
						String[] tokens = nameOrEmail.split(USER_INFO_TOKENS_DELIMITER);
						email = tokens[0].contains("@") ? tokens[0] : tokens[1];
						name = tokens[0].contains("@") ? tokens[1] : tokens[0];
						password = tokens.length > 2 ? tokens[2] : generatePW();
					} else {
						email = nameOrEmail.contains("@") ? nameOrEmail : nameOrEmail + "@NOT.SPECIFIED";
						name = nameOrEmail.contains("@") ? nameOrEmail.split("@")[0] : nameOrEmail;
						password = generatePW();
					}
					User u = new User(name, password, email);
					if (DatabaseAccess.getUserForNameOrEmail(name) == null && DatabaseAccess.getUserForNameOrEmail(email) == null) {
						if (u.insert()) {
							messages.add(name + "'s password is: " + password);
							logger.info("Generated password " + password + " for user " + name);
						} else {
							messages.add("Error trying to create an account for " + name + " (email: " + email + ")!");
						}
					} else {
						messages.add(name + " (email: " + email + ") already has an account!");
					}
				}
			}
		}
	}

	private String deleteUser(int uid) {
		return (AdminDAO.deleteUser(uid) ? "Successfully deleted user " : "Error trying to delete user ") + uid + "!";
	}

	private String resetUserPW(int uid) {

		String newPassword = generatePW();
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		boolean success = AdminDAO.setUserPassword(uid, passwordEncoder.encode(newPassword));

		return success ? "User " + uid + "'s password set to: " + newPassword : "Could not reset password for user " + uid;
	}

	private static String generatePW() {
		int length = AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.MIN_PASSWORD_LENGTH).getIntValue();

		StringBuilder sb = new StringBuilder();
		char[] initialSet = LOWER;

		Random random = new Random();
		for (int i = 0; i < length; i++) {
			sb.append(initialSet[random.nextInt(initialSet.length)]);
		}
		char[] resultChars = sb.toString().toCharArray();

		List<Integer> randomInts = Arrays.stream(new IntRange(0, length - 1).toArray()).boxed().collect(Collectors.toList());
		Collections.shuffle(randomInts);

		int c = 0;
		resultChars[randomInts.get(c)] = Character.toUpperCase(resultChars[randomInts.get(c)]);
		resultChars[randomInts.get(++c)] = PUNCTUATION[random.nextInt(PUNCTUATION.length)];
		resultChars[randomInts.get(++c)] = DIGITS[random.nextInt(DIGITS.length)];

		return new String(resultChars);
	}

}