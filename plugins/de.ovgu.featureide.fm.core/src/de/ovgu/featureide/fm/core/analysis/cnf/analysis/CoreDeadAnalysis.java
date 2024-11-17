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

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet.Order;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver.SelectionStrategy;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ModifiableSatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.RuntimeContradictionException;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * Finds core and dead features.
 *
 * @author Sebastian Krieter
 */
public class CoreDeadAnalysis extends AVariableAnalysis<LiteralSet> {

	public CoreDeadAnalysis(ISatSolver solver) {
		this(solver, null);
	}

	public CoreDeadAnalysis(CNF satInstance) {
		this(satInstance, null);
	}

	public CoreDeadAnalysis(CNF satInstance, LiteralSet variables) {
		super(satInstance);
		this.variables = variables;
	}

	public CoreDeadAnalysis(ISatSolver solver, LiteralSet variables) {
		super(solver);
		this.variables = variables;
	}

	@Override
	protected ISatSolver initSolver(CNF satInstance) {
		try {
			return new ModifiableSatSolver(satInstance);
		} catch (final RuntimeContradictionException e) {
			return null;
		}
	}

	@Override
	public LiteralSet analyze(IMonitor<LiteralSet> monitor) throws Exception {
		final int initialAssignmentLength = solver.getAssignmentSize();
		solver.setSelectionStrategy(SelectionStrategy.POSITIVE);
		monitor.checkCancel();
		int[] model1 = solver.findSolution();

		if (model1 != null) {
			solver.setSelectionStrategy(SelectionStrategy.NEGATIVE);
			monitor.checkCancel();
			final int[] model2 = solver.findSolution();

			monitor.checkCancel();

			if (variables != null) {
				final int[] model3 = new int[model1.length];
				for (int i = 0; i < variables.getLiterals().length; i++) {
					final int index = variables.getLiterals()[i] - 1;
					if (index >= 0) {
						model3[index] = model1[index];
					}
				}
				model1 = model3;
			}

			for (int i = 0; i < initialAssignmentLength; i++) {
				model1[Math.abs(solver.assignmentGet(i)) - 1] = 0;
			}

			LiteralSet.resetConflicts(model1, model2);
			solver.setSelectionStrategy(model1,
					model1.length > (new LiteralSet(model2, Order.INDEX, false).countNegative() + new LiteralSet(model1, Order.INDEX, false).countNegative()));

			for (int i = 0; i < model1.length; i++) {
				monitor.checkCancel();
				final int varX = model1[i];
				if (varX != 0) {
					solver.assignmentPush(-varX);
					switch (solver.hasSolution()) {
					case FALSE:
						solver.assignmentReplaceLast(varX);
						monitor.invoke(new LiteralSet(varX));
						break;
					case TIMEOUT:
						solver.assignmentPop();
						reportTimeout();
						break;
					case TRUE:
						solver.assignmentPop();
						LiteralSet.resetConflicts(model1, solver.getSolution());
						solver.shuffleOrder(getRandom());
						break;
					}
				}
			}
		}

		return new LiteralSet(solver.getAssignmentArray(initialAssignmentLength, solver.getAssignmentSize()));
	}

}
