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
package de.ovgu.featureide.fm.core.analysis.cnf.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sat4j.core.VecInt;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.manipulator.remove.CNFSlicer;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.EmptySatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISimpleSatSolver.SatResult;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ModifiableSatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.RuntimeContradictionException;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * Finds indetermined features.
 *
 * @author Sebastian Krieter
 */
public class IndeterminedAnalysis extends AVariableAnalysis<LiteralSet> {

	public IndeterminedAnalysis(CNF satInstance) {
		super(satInstance);
	}

	public IndeterminedAnalysis(ISatSolver solver) {
		super(solver);
	}

	@Override
	protected ISatSolver initSolver(CNF satInstance) {
		return new EmptySatSolver(satInstance);
	}

	@Override
	public LiteralSet analyze(IMonitor<LiteralSet> monitor) throws Exception {
		monitor.setRemainingWork(2 * variables.getLiterals().length);

		final VecInt potentialResultList = new VecInt();
		final List<LiteralSet> relevantClauses = new ArrayList<>();

		final ModifiableSatSolver modSolver = new ModifiableSatSolver(solver.getSatInstance());
		for (final int literal : variables.getLiterals()) {
			final List<LiteralSet> clauses = solver.getSatInstance().getClauses();
			for (final LiteralSet clause : clauses) {
				if (clause.containsVariable(literal)) {
					final LiteralSet newClause = clause.clean(literal);
					if (newClause != null) {
						relevantClauses.add(newClause);
					}
				}
			}
			try {
				modSolver.addClauses(relevantClauses);
			} catch (final RuntimeContradictionException e) {
				relevantClauses.clear();
				monitor.step();
				continue;
			}

			monitor.checkCancel();
			final SatResult hasSolution = modSolver.hasSolution();
			switch (hasSolution) {
			case FALSE:
				break;
			case TIMEOUT:
				reportTimeout();
				break;
			case TRUE:
				potentialResultList.push(literal);
				break;
			default:
				throw new AssertionError(hasSolution);
			}
			modSolver.removeLastClauses(relevantClauses.size());

			relevantClauses.clear();
			monitor.step();
		}

		final VecInt resultList = new VecInt();
		while (!potentialResultList.isEmpty()) {
			final int literal = potentialResultList.last();
			potentialResultList.pop();
			monitor.checkCancel();
			final CNF slicedCNF = LongRunningWrapper.runMethod(new CNFSlicer(solver.getSatInstance(), variables.removeAll(new LiteralSet(literal))));
			monitor.checkCancel();
			final List<LiteralSet> clauses = slicedCNF.getClauses();
			for (final LiteralSet clause : clauses) {
				if (clause.containsVariable(literal)) {
					final LiteralSet newClause = clause.clean(literal);
					if (newClause != null) {
						relevantClauses.add(newClause);
					}
				}
			}
			try {
				modSolver.addClauses(relevantClauses);
			} catch (final RuntimeContradictionException e) {
				relevantClauses.clear();
				monitor.step();
				continue;
			}

			monitor.checkCancel();
			final SatResult hasSolution = modSolver.hasSolution();
			switch (hasSolution) {
			case FALSE:
				break;
			case TIMEOUT:
				reportTimeout();
				break;
			case TRUE:
				resultList.push(literal);
				break;
			default:
				throw new AssertionError(hasSolution);
			}
			modSolver.removeLastClauses(relevantClauses.size());

			relevantClauses.clear();
			monitor.step();
		}

		return new LiteralSet(Arrays.copyOf(resultList.toArray(), resultList.size()));
	}

}
