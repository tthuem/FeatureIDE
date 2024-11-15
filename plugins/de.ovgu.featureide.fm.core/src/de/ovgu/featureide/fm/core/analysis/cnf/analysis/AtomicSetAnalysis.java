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

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver.SelectionStrategy;
import de.ovgu.featureide.fm.core.base.util.RingList;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * Finds atomic sets.
 *
 * @author Sebastian Krieter
 */
public class AtomicSetAnalysis extends AVariableAnalysis<List<LiteralSet>> {

	public AtomicSetAnalysis(ISatSolver solver) {
		super(solver);
	}

	public AtomicSetAnalysis(CNF satInstance) {
		super(satInstance);
	}

	@Override
	public List<LiteralSet> analyze(IMonitor<List<LiteralSet>> monitor) throws Exception {
		final List<LiteralSet> result = new ArrayList<>();

		solver.setSelectionStrategy(SelectionStrategy.POSITIVE);
		monitor.checkCancel();
		final int[] model1 = solver.findSolution();
		solver.useSolutionList(1000);

		if (model1 != null) {
			solver.setSelectionStrategy(SelectionStrategy.NEGATIVE);
			monitor.checkCancel();
			final int[] model2 = solver.findSolution();
			solver.setSelectionStrategy(SelectionStrategy.POSITIVE);

			final byte[] done = new byte[model1.length];

			final int[] model1Copy = Arrays.copyOf(model1, model1.length);

			LiteralSet.resetConflicts(model1Copy, model2);
			for (int i = 0; i < model1Copy.length; i++) {
				monitor.checkCancel();
				final int varX = model1Copy[i];
				if (varX != 0) {
					solver.assignmentPush(-varX);
					switch (solver.hasSolution()) {
					case FALSE:
						done[i] = 2;
						solver.assignmentReplaceLast(varX);
						break;
					case TIMEOUT:
						solver.assignmentPop();
						reportTimeout();
						break;
					case TRUE:
						solver.assignmentPop();
						LiteralSet.resetConflicts(model1Copy, solver.getSolution());
						solver.shuffleOrder(getRandom());
						break;
					}
				}
			}
			final int fixedSize = solver.getAssignmentSize();
			result.add(new LiteralSet(solver.getAssignmentArray(0, fixedSize)));

			solver.setSelectionStrategy(SelectionStrategy.RANDOM);

			for (int i = 0; i < model1.length; i++) {
				if (done[i] == 0) {
					done[i] = 2;

					// int c = 0;
					int[] xModel0 = Arrays.copyOf(model1, model1.length);

					final int mx0 = xModel0[i];
					solver.assignmentPush(mx0);
					final RingList<int[]> solutions = solver.getSolutionList();

					inner: for (int j = i + 1; j < xModel0.length; j++) {
						final int my0 = xModel0[j];
						if ((my0 != 0) && (done[j] == 0)) {
							for (int k = 1; k < solutions.size(); k++) {
								final int[] solution = solutions.get(k);
								final int mxI = solution[i];
								final int myI = solution[j];
								if ((mx0 == mxI) != (my0 == myI)) {
									continue inner;
								}
							}

							solver.assignmentPush(-my0);

							monitor.checkCancel();
							switch (solver.hasSolution()) {
							case FALSE:
								done[j] = 1;
								break;
							case TIMEOUT:
								reportTimeout();
								break;
							case TRUE:
								LiteralSet.resetConflicts(xModel0, solver.getSolution());
								solver.shuffleOrder(getRandom());
								break;
							}
							solver.assignmentPop();
						}
					}

					solver.assignmentPop();
					solver.assignmentPush(-mx0);

					monitor.checkCancel();
					switch (solver.hasSolution()) {
					case FALSE:
						break;
					case TIMEOUT:
						for (int j = i + 1; j < xModel0.length; j++) {
							done[j] = 0;
						}
						reportTimeout();
						break;
					case TRUE:
						xModel0 = solver.getSolution();
						break;
					}

					for (int j = i + 1; j < xModel0.length; j++) {
						if (done[j] == 1) {
							final int my0 = xModel0[j];
							if (my0 != 0) {
								solver.assignmentPush(-my0);

								monitor.checkCancel();
								switch (solver.hasSolution()) {
								case FALSE:
									done[j] = 2;
									solver.assignmentReplaceLast(my0);
									break;
								case TIMEOUT:
									done[j] = 0;
									solver.assignmentPop();
									reportTimeout();
									break;
								case TRUE:
									done[j] = 0;
									LiteralSet.resetConflicts(xModel0, solver.getSolution());
									solver.shuffleOrder(getRandom());
									solver.assignmentPop();
									break;
								}
							} else {
								done[j] = 0;
							}
						}
					}

					result.add(new LiteralSet(solver.getAssignmentArray(fixedSize, solver.getAssignmentSize())));
					solver.assignmentClear(fixedSize);
				}
			}
		}
		return result;
	}

	// private void updateSolver(int c) {
	// if ((c % 2 == 0)) {
	// solver.setSelectionStrategy(SelectionStrategy.RANDOM);
	// } else {
	// solver.setSelectionStrategy(SelectionStrategy.POSITIVE);
	// solver.shuffleOrder();
	// }
	// }

}
