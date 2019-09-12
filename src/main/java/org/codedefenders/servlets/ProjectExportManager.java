package org.codedefenders.servlets;

import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.database.GameClassDAO;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.Role;
import org.codedefenders.model.Dependency;
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.servlets.util.ServletUtils;
import org.codedefenders.util.Constants;
import org.codedefenders.util.ZipFileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This {@link HttpServlet} handles requests for exporting a {@link GameClass}
 * as a Gradle project.
 * <p>
 * Serves on path: {@code /project-export}.
 *
 * @author <a href="https://github.com/werli">Phil Werli<a/>
 * @see org.codedefenders.util.Paths#PROJECT_EXPORT
 */
@WebServlet("/project-export")
public class ProjectExportManager extends HttpServlet {

    private static final Path mainDir = Paths.get("src/main/java");
    private static final Path testDir = Paths.get("src/test/java");
    private static final Path gradleDir = Paths.get(Constants.DATA_DIR).resolve("project-exporter");

    private static final String[] gradleFiles = {
        "build.gradle",
        "gradlew",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties"
    };

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final Optional<Integer> gameId = ServletUtils.gameId(request);
        if (!gameId.isPresent()) {
            Redirect.redirectBack(request, response);
            return;
        }

        final int userId = ServletUtils.userId(request);
        if (DatabaseAccess.getRole(userId, gameId.get()) == Role.NONE) {
            Redirect.redirectBack(request, response);
            return;
        }

        GameClass cut = GameClassDAO.getClassForGameId(gameId.get());
        Path packagePath = Paths.get(cut.getPackage().replace(".", "/"));
        List<Dependency> dependencies = GameClassDAO.getMappedDependenciesForClassId(cut.getId());

        final Set<Path> paths = dependencies
            .stream()
            .map(Dependency::getJavaFile)
            .map(Paths::get)
            .collect(Collectors.toSet());
        paths.add(Paths.get(cut.getJavaFile()));

        final Map<String, byte[]> files = new HashMap<>();
        {
            final String templateFileName = testDir.resolve(packagePath.resolve("Test" + Paths.get(cut.getJavaFile()).getFileName().toString())).toString();
            final byte[] templateFileContent = cut.getTestTemplate().getBytes();
            files.put(templateFileName, templateFileContent);
        }
        files.put("settings.gradle", ("rootProject.name = 'Code Defenders - " + cut.getBaseName() + "'").getBytes());

        for (Path path : paths) {
            String filePath = mainDir.resolve(packagePath.resolve(path.getFileName())).toString();
            byte[] fileContent = Files.readAllBytes(path);
            files.put(filePath, fileContent);
        }

        for (String gradleFilePath : gradleFiles) {
            byte[] fileContent = Files.readAllBytes(gradleDir.resolve(gradleFilePath));
            files.put(gradleFilePath, fileContent);
        }

        byte[] zipFileBytes = ZipFileUtils.zipFiles(files);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=CodeDefenders_" + cut.getBaseName() + ".zip");

        ServletOutputStream out = response.getOutputStream();
        out.write(zipFileBytes);
        out.flush();
        out.close();
    }
}
