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
package de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise;

import java.util.ArrayList;
import java.util.List;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.ClauseList;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.iterator.ICombinationIterator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.iterator.LexicographicIterator;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.AdvancedSatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.RuntimeContradictionException;

/**
 * Tests whether a set of configurations achieves t-wise feature coverage.
 *
 * @author Sebastian Krieter
 */
public class TWiseCoverageCriterion implements CoverageCriterion {

	private final TWiseConfigurationUtil util;
	private PresenceConditionManager presenceConditionManager;
	private int t;

	public TWiseCoverageCriterion(CNF cnf, int t) {
		util = new TWiseConfigurationUtil(new AdvancedSatSolver(cnf));
		if (util.isUnsatisfiable()) {
			throw new RuntimeContradictionException("CNF is unsatisfiable");
		}
		util.computeRandomSample();

		presenceConditionManager = new PresenceConditionManager(util, TWiseConfigurationGenerator.convertLiterals(cnf.getVariables().getLiterals()));
		this.t = t;
	}

	public void setNodes(List<List<ClauseList>> expressions) {
		presenceConditionManager = new PresenceConditionManager(util, expressions);
	}

	public void setT(int t) {
		this.t = t;
	}

	@Override
	public double getCoverage(List<LiteralSet> sample) {
		final TWiseConfigurationStatistic statistic = getStatistics(sample);
		final long numberOfValidConditions = statistic.getNumberOfValidConditions();
		final long numberOfCoveredConditions = statistic.getNumberOfCoveredConditions();
		if (numberOfValidConditions == 0) {
			return 1;
		} else {
			return (double) numberOfCoveredConditions / numberOfValidConditions;
		}
	}

	/**
	 * Creates statistic values about covered combinations.<br> To get a percentage value of covered combinations use:<br> <pre>{@code
	 * 	TWiseConfigurationStatistic coverage = getCoverage();
	 * 	double covered = (double) coverage.getNumberOfCoveredConditions() / coverage.getNumberOfValidConditions();
	 * }</pre>
	 *
	 *
	 * @return a statistic object containing multiple values:<br> <ul> <li>number of valid combinations <li>number of invalid combinations <li>number of covered
	 *         combinations <li>number of uncovered combinations <li>value of each configuration </ul>
	 */
	public TWiseConfigurationStatistic getStatistics(List<LiteralSet> sample) {
		final TWiseConfigurationStatistic statistic = new TWiseConfigurationStatistic();
		statistic.setT(t);
		statistic.setOnlyCoverage(true);
		statistic.calculate(util, sample, presenceConditionManager.getGroupedPresenceConditions());
		return statistic;
	}

	public boolean hasUncoveredConditions(List<LiteralSet> sample) {
		final List<ClauseList> uncoveredConditions = getUncoveredConditions(true, sample);
		return !uncoveredConditions.isEmpty();
	}

	public ClauseList getFirstUncoveredCondition(List<LiteralSet> sample) {
		final List<ClauseList> uncoveredConditions = getUncoveredConditions(true, sample);
		return uncoveredConditions.isEmpty() ? null : uncoveredConditions.get(0);
	}

	public List<ClauseList> getUncoveredConditions(List<LiteralSet> sample) {
		return getUncoveredConditions(false, sample);
	}

	private List<ClauseList> getUncoveredConditions(boolean cancelAfterFirst, List<LiteralSet> sample) {
		final ArrayList<ClauseList> uncoveredConditions = new ArrayList<>();
		final TWiseCombiner combiner = new TWiseCombiner(util.getCnf().getVariables().size());
		ClauseList combinedCondition = new ClauseList();

		groupLoop: for (final List<PresenceCondition> expressions : presenceConditionManager.getGroupedPresenceConditions()) {
			for (final ICombinationIterator iterator = new LexicographicIterator(t, expressions); iterator.hasNext();) {
				final PresenceCondition[] clauseListArray = iterator.next();
				if (clauseListArray == null) {
					break;
				}

				combinedCondition.clear();
				combiner.combineConditions(clauseListArray, combinedCondition);
				if (!TWiseConfigurationGenerator.isCovered(combinedCondition, sample) && util.isCombinationValid(combinedCondition)) {
					uncoveredConditions.add(combinedCondition);
					combinedCondition = new ClauseList();
					if (cancelAfterFirst) {
						break groupLoop;
					}
				}

			}
		}
		return uncoveredConditions;
	}

}
