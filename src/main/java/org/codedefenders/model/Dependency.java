/*
 * Copyright (C) 2016-2019 Code Defenders contributors
 *
 * This file is part of Code Defenders.
 *
 * Code Defenders is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Code Defenders is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.
 */
package org.codedefenders.model;

import org.codedefenders.game.GameClass;

/**
 * This class represents a dependency which is uploaded together
 * with a {@link GameClass}. The class under test its uploaded with
 * is referenced by the {@link #classId} field.
 *
 * @author <a href="https://github.com/werli">Phil Werli<a/>
 */
public class Dependency {
    private int id;
    private int classId;
    private String javaFile;
    private String classFile;

    public Dependency(int id, int classId, String javaFile, String classFile) {
        this.id = id;
        this.classId = classId;
        this.javaFile = javaFile;
        this.classFile = classFile;
    }

    public Dependency(int classId, String javaFile, String classFile) {
        this.classId = classId;
        this.javaFile = javaFile;
        this.classFile = classFile;
    }

    public int getId() {
        return id;
    }

    public int getClassId() {
        return classId;
    }

    public String getJavaFile() {
        return javaFile;
    }

    public String getClassFile() {
        return classFile;
    }
}
