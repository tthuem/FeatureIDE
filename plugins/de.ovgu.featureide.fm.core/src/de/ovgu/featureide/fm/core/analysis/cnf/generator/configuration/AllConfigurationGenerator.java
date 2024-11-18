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
package de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration;

import java.util.Arrays;
import java.util.List;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet.Order;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.RuntimeContradictionException;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * Generates all configurations for a given propositional formula.
 *
 * @author Sebastian Krieter
 */
public class AllConfigurationGenerator extends AConfigurationGenerator {

	public AllConfigurationGenerator(CNF cnf) {
		super(cnf);
	}

	public AllConfigurationGenerator(ISatSolver solver) {
		super(solver);
	}

	public AllConfigurationGenerator(CNF cnf, int maxNumber) {
		super(cnf, maxNumber);
	}

	public AllConfigurationGenerator(ISatSolver solver, int maxNumber) {
		super(solver, maxNumber);
	}

	@Override
	protected void generate(IMonitor<List<LiteralSet>> monitor) throws Exception {
		for (int i = 0; i < maxSampleSize; i++) {
			final int[] solution = solver.findSolution();
			if (solution == null) {
				break;
			}
			final LiteralSet result = new LiteralSet(Arrays.copyOf(solution, solution.length), Order.INDEX, false);
			addResult(result);
			try {
				solver.addInternalClause(result.negate());
			} catch (final RuntimeContradictionException e) {
				break;
			}
		}
	}

}
