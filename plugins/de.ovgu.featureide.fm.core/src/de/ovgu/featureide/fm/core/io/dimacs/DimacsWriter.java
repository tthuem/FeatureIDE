/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2019  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 *
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.core.io.dimacs;

import org.prop4j.Node;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;

/**
 * Transforms instances of {@link Node} into DIMACS CNF file format.
 *
 * @author Timo Günther
 * @author Sebastian Krieter
 */
public class DimacsWriter {

	/** Whether the writer should write a variable directory listing the names of the variables. */
	private boolean writingVariableDirectory = true;

	private final CNF cnf;

	private final LiteralSet projectionVariables;

	/**
	 * Constructs a new instance of this class with the given CNF.
	 *
	 * @param cnf the CNF to transform; not null
	 * @throws IllegalArgumentException if the input is null or not in CNF
	 */
	public DimacsWriter(CNF cnf) throws IllegalArgumentException {
		this(cnf, null);
	}

	/**
	 * Constructs a new instance of this class with the given CNF and variables to project on
	 *
	 * @param cnf the CNF to write into the file
	 * @param projectionVariables the variables that should be projected on
	 */
	public DimacsWriter(CNF cnf, LiteralSet projectionVariables) {
		if (cnf == null) {
			throw new IllegalArgumentException();
		}
		this.cnf = cnf;
		this.projectionVariables = projectionVariables.clone();
	}

	/**
	 * <p> Sets the writing variable directory flag. If true, the writer will write a variable directory at the start of the output. This is a set of comments
	 * naming the variables. This can later be used during reading so the variables are not just numbers. </p>
	 *
	 * <p> Defaults to false. </p>
	 *
	 * @param writingVariableDirectory whether to write the variable directory
	 */
	public void setWritingVariableDirectory(boolean writingVariableDirectory) {
		this.writingVariableDirectory = writingVariableDirectory;
	}

	public boolean isWritingVariableDirectory() {
		return writingVariableDirectory;
	}

	/**
	 * Writes the DIMACS CNF file format.
	 *
	 * @return the transformed CNF; not null
	 */
	public String write() {
		final StringBuilder sb = new StringBuilder();
		if (writingVariableDirectory) {
			writeVariableDirectory(sb);
		}
		writeProjectionLine(sb);
		writeProblem(sb);
		writeClauses(sb);
		return sb.toString();
	}

	/**
	 * Writes the variable directory.
	 *
	 * @param sb the string builder that builds the document
	 */
	private void writeVariableDirectory(StringBuilder sb) {
		final String[] names = cnf.getVariables().getNames();
		for (int i = 1; i < names.length; i++) {
			writeVariableDirectoryEntry(sb, i, names[i]);
		}
	}

	/**
	 * Writes an entry of the variable directory.
	 *
	 * @param sb the string builder that builds the document
	 * @param variable variable to list in the entry
	 * @param index index of the variable
	 */
	private void writeVariableDirectoryEntry(StringBuilder sb, int index, String name) {
		sb.append(DIMACSConstants.COMMENT_START);
		sb.append(index);
		sb.append(' ');
		sb.append(String.valueOf(name));
		sb.append(System.lineSeparator());
	}

	/**
	 * Writes the problem description.
	 *
	 * @param sb the string builder that builds the document
	 */
	private void writeProblem(StringBuilder sb) {
		sb.append(DIMACSConstants.PROBLEM);
		sb.append(' ');
		sb.append(DIMACSConstants.CNF);
		sb.append(' ');
		sb.append(cnf.getVariables().size());
		sb.append(' ');
		sb.append(cnf.getClauses().size());
		sb.append(System.lineSeparator());
	}

	/**
	 * Writes the projection line in the format c p show v1 v2 ... The listed variables are kept when performing operations based on projection on the DIMACS,
	 * such as projected model counting or projected compilation.
	 *
	 * @param sb string builder that builds the document
	 */
	private void writeProjectionLine(StringBuilder sb) {
		if (projectionVariables == null) {
			return;
		}
		sb.append(DIMACSConstants.COMMENT_START);
		sb.append(' ');
		sb.append(DIMACSConstants.PROJECTION);
		sb.append(' ');
		for (final int variable : projectionVariables.getLiterals()) {
			sb.append(' ');
			sb.append(variable);
		}
		sb.append(System.lineSeparator());
	}

	/**
	 * Writes the given clause.
	 *
	 * @param sb the string builder that builds the document
	 * @param clause clause to transform; not null
	 */
	private void writeClause(StringBuilder sb, LiteralSet clause) {
		for (final int l : clause.getLiterals()) {
			sb.append(l);
			sb.append(' ');
		}
		sb.append(DIMACSConstants.CLAUSE_END);
		sb.append(System.lineSeparator());
	}

	/**
	 * Writes all clauses.
	 *
	 * @param sb the string builder that builds the document
	 */
	private void writeClauses(StringBuilder sb) {
		for (final LiteralSet clause : cnf.getClauses()) {
			writeClause(sb, cnf.getInternalVariables().convertToInternal(clause));
		}
	}

}
