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
package org.prop4j.explain.solvers.impl.sat4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.prop4j.Node;
import org.prop4j.explain.solvers.MusExtractor;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVecInt;
import org.sat4j.tools.AllMUSes;

/**
 * A MUS extractor using a Sat4J oracle for all MUSes.
 *
 * @author Timo G&uuml;nther
 */
public class Sat4jAllMusExtractor extends Sat4jMutableSatSolver implements MusExtractor {

	/** The oracle used to extract all MUSes. */
	private AllMUSes allMusExtractor;

	@Override
	protected ISolver createOracle() {
		allMusExtractor = new AllMUSes(true, SolverFactory.instance());
		return allMusExtractor.getSolverInstance();
	}

	@Override
	protected Node removeClause() throws UnsupportedOperationException {
		/*
		 * AllMUSes throws exceptions when clauses are removed. Specifically, when calling ISolver#removeSubsumedConstraint(IConstr), it throws an
		 * IllegalArgumentException (with the message "Can only remove latest added constraint!!!"). When calling ISolver#removeConstraint(IConstr) instead, an
		 * ArrayIndexOutOfBoundsException is thrown later while extracting the MUSes because it assumes the clause exists even though it was already removed.
		 * Tested on Sat4J version 2.3.5.v20130525.
		 */
		throw new UnsupportedOperationException("Sat4J's AllMUSes does not support clause removal");
	}

	@Override
	public Set<Node> getMinimalUnsatisfiableSubset() throws IllegalStateException {
		return getClauseSetFromIndexSet(getMinimalUnsatisfiableSubsetIndexes());
	}

	@Override
	public Set<Integer> getMinimalUnsatisfiableSubsetIndexes() throws IllegalStateException {
		return getAllMinimalUnsatisfiableSubsetIndexes().get(0);
	}

	@Override
	public List<Set<Node>> getAllMinimalUnsatisfiableSubsets() throws IllegalStateException {
		return getClauseSetsFromIndexSets(getAllMinimalUnsatisfiableSubsetIndexes());
	}

	@Override
	public List<Set<Integer>> getAllMinimalUnsatisfiableSubsetIndexes() throws IllegalStateException {
		if (isSatisfiable()) {
			throw new IllegalStateException("Problem is satisfiable");
		}
		final List<IVecInt> allIndexes = allMusExtractor.computeAllMUSes();
		final List<Set<Integer>> allMuses = new ArrayList<>(allIndexes.size());
		for (final IVecInt indexes : allIndexes) {
			final Set<Integer> mus = new TreeSet<>();
			for (int i = 0; i < indexes.size(); i++) {
				mus.add(getClauseIndexFromIndex(indexes.get(i)));
			}
			allMuses.add(mus);
		}
		return allMuses;
	}
}
