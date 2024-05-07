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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.ClauseList;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.AConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.ITWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.ICoverStrategy.CombinationStatus;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.iterator.ICombinationSupplier;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.iterator.MergeIterator3;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.iterator.SingleIterator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.util.Pair;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver.SelectionStrategy;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;
import de.ovgu.featureide.fm.core.job.monitor.MonitorThread;

/**
 * Generates configurations for a given propositional formula such that t-wise feature coverage is achieved.
 *
 * @author Sebastian Krieter
 */
public class TWiseConfigurationGenerator extends AConfigurationGenerator implements ITWiseConfigurationGenerator {

	private final class SamplingMonitor implements Runnable {

		@Override
		public void run() {
			if (VERBOSE) {
				final long uncoveredCount = (numberOfCombinations - coveredCount) - invalidCount;
				final double phaseProgress = ((int) Math.floor((1 - (((double) count) / numberOfCombinations)) * 1000)) / 10.0;
				final double coverProgress = ((int) Math.floor(((((double) coveredCount) / numberOfCombinations)) * 1000)) / 10.0;
				final double uncoverProgress = ((int) Math.floor(((((double) uncoveredCount) / numberOfCombinations)) * 1000)) / 10.0;
				final double invalidProgress = ((int) Math.floor(((((double) invalidCount) / numberOfCombinations)) * 1000)) / 10.0;
				final StringBuilder sb = new StringBuilder();

				sb.append(phaseCount);
				sb.append(" - ");
				sb.append(phaseProgress);
				sb.append(" (");
				sb.append(count);

				sb.append(") -- Configurations: ");
				sb.append(getIncompleteSolutionList().size() + getCompleteSolutionList().size());
				sb.append(" (");
				sb.append(getIncompleteSolutionList().size());
				sb.append(" | ");
				sb.append(getCompleteSolutionList().size());

				sb.append(") -- Covered: ");
				sb.append(coverProgress);
				sb.append(" (");
				sb.append(coveredCount);
				sb.append(")");

				sb.append(" -- Uncovered: ");
				sb.append(uncoverProgress);
				sb.append(" (");
				sb.append(uncoveredCount);
				sb.append(")");

				sb.append(" -- Invalid: ");
				sb.append(invalidProgress);
				sb.append(" (");
				sb.append(invalidCount);
				sb.append(")");
				System.out.println(sb.toString());
			}
		}
	}

	/**
	 * Converts a set of single literals into a grouped expression list.
	 *
	 * @param literalSet the literal set
	 * @return a grouped expression list (can be used as an input for the configuration generator).
	 */
	public static List<List<ClauseList>> convertLiterals(LiteralSet literalSet) {
		return TWiseCombiner.convertGroupedLiterals(Arrays.asList(literalSet));
	}

	/**
	 * Converts a grouped set of single literals into a grouped expression list.
	 *
	 * @param groupedLiterals the grouped literal sets
	 * @return a grouped expression list (can be used as an input for the configuration generator).
	 */
	public static List<List<ClauseList>> convertGroupedLiterals(List<LiteralSet> groupedLiterals) {
		return TWiseCombiner.convertGroupedLiterals(groupedLiterals);
	}

	/**
	 * Converts an expression list into a grouped expression set with a single group.
	 *
	 * @param expressions the expression list
	 * @return a grouped expression list (can be used as an input for the configuration generator).
	 */
	public static List<List<ClauseList>> convertExpressions(List<ClauseList> expressions) {
		return TWiseCombiner.convertExpressions(expressions);
	}

	// TODO Variation Point: Iterations of removing low-contributing Configurations
	private int iterations = 5;

	protected TWiseConfigurationUtil util;
	protected TWiseCombiner combiner;

	protected final int t;
	protected List<List<ClauseList>> nodes;
	protected PresenceConditionManager presenceConditionManager;

	protected long numberOfCombinations, count, coveredCount, invalidCount;
	protected int phaseCount;

	private List<TWiseConfiguration> curResult = null;
	private ArrayList<TWiseConfiguration> bestResult = null;

	private List<LiteralSet> initialSample = Collections.emptyList();
	private boolean allowInitialSolutionModify = false;
	private boolean allowInitialSolutionRemove = false;
	private boolean countInitialSolutionForLimit = false;

	protected MonitorThread samplingMonitor;

	final static Comparator<Pair<LiteralSet, TWiseConfiguration>> candidateLengthComparator = new CandidateLengthComparator();

	private final List<TWiseConfiguration> incompleteSolutionList = new LinkedList<>();
	private final List<TWiseConfiguration> completeSolutionList = new ArrayList<>();

	public TWiseConfigurationGenerator(CNF cnf, int t) {
		this(cnf, convertLiterals(cnf.getVariables().getLiterals()), t, Integer.MAX_VALUE);
	}

	public TWiseConfigurationGenerator(CNF cnf, int t, int maxSampleSize) {
		this(cnf, convertLiterals(cnf.getVariables().getLiterals()), t, maxSampleSize);
	}

	public TWiseConfigurationGenerator(CNF cnf, List<List<ClauseList>> nodes, int t) {
		this(cnf, nodes, t, Integer.MAX_VALUE);
	}

	public TWiseConfigurationGenerator(CNF cnf, List<List<ClauseList>> nodes, int t, int maxSampleSize) {
		super(cnf, maxSampleSize);
		this.t = t;
		this.nodes = nodes;
	}

	public List<LiteralSet> getInitialSample() {
		return Collections.unmodifiableList(initialSample);
	}

	public void setInitialSample(List<LiteralSet> initialSample) {
		this.initialSample = new ArrayList<>(initialSample);
	}

	@Override
	protected void generate(IMonitor<List<LiteralSet>> monitor) throws Exception {
		if (util == null) {
			util = new TWiseConfigurationUtil(solver);
			if (util.isUnsatisfiable()) {
				bestResult = new ArrayList<>(0);
				return;
			}
			util.setSolutionList(incompleteSolutionList);
			util.setRandom(getRandom());
			util.computeRandomSample();
			// TODO Variation Point: Building Combinations
			combiner = new TWiseCombiner(util.getCnf().getVariables().size());
		}
		if (presenceConditionManager == null) {
			// TODO Variation Point: Sorting Nodes
			presenceConditionManager = new PresenceConditionManager(util, nodes);
		}

		solver.useSolutionList(0);
		solver.setSelectionStrategy(SelectionStrategy.ORG);

		curResult = null;
		bestResult = null;

		incompleteSolutionList.clear();
		completeSolutionList.clear();

		initialSample.forEach(c1 -> newInitialConfiguration(c1));

		phaseCount = 0;

		for (int i = 0; i < iterations; i++) {
			trimConfigurations();
			buildCombinations(monitor);
		}

		if (allowInitialSolutionModify) {
			bestResult.stream() //
					.filter(c -> c.isInitial()) //
					.sorted(Comparator.comparing(TWiseConfiguration::getInitialIndex)) //
					.forEach(c -> addResult(c.getCompleteSolution()));
		} else {
			bestResult.stream() //
					.filter(c -> c.isInitial()) //
					.sorted(Comparator.comparing(TWiseConfiguration::getInitialIndex)) //
					.forEach(c -> addResult(c));
		}
		bestResult.stream() //
				.filter(c -> !c.isInitial()) //
				.forEach(c -> addResult(c.getCompleteSolution()));
	}

	private void trimConfigurations() {
		if (curResult != null) {
			final TWiseConfigurationStatistic statistic = new TWiseConfigurationStatistic();
			statistic.setT(t);
			statistic.setFastCalc(true);
			statistic.calculate(util, curResult, presenceConditionManager.getGroupedPresenceConditions());

			final double[] normConfigValues = statistic.getConfigValues2();
			double mean = 0;
			for (final double d : normConfigValues) {
				mean += d;
			}
			mean /= normConfigValues.length;

			final double reference = mean;

			int index = 0;
			index = removeSolutions(normConfigValues, reference, index, getIncompleteSolutionList());
			index = removeSolutions(normConfigValues, reference, index, getCompleteSolutionList());
		}
	}

	private int removeSolutions(double[] values, final double reference, int index, List<TWiseConfiguration> solutionList) {
		for (final Iterator<TWiseConfiguration> it = solutionList.iterator(); it.hasNext();) {
			final TWiseConfiguration solution = it.next();
			if (values[index++] < reference) {
				if (allowInitialSolutionRemove || !solution.isInitial()) {
					it.remove();
				}
			}
		}
		return index;
	}

	private void buildCombinations(IMonitor<List<LiteralSet>> monitor) {
		// TODO Variation Point: Cover Strategies
		final List<? extends ICoverStrategy> phaseList = Arrays.asList(//
				new CoverAll(this) //
		);

		// TODO Variation Point: Combination order
		final ICombinationSupplier<ClauseList> it;
		presenceConditionManager.shuffleSort(getRandom());
		final List<List<PresenceCondition>> groupedPresenceConditions = presenceConditionManager.getGroupedPresenceConditions();
		if (groupedPresenceConditions.size() == 1) {
			it = new SingleIterator(t, util.getCnf().getVariables().size(), groupedPresenceConditions.get(0));
		} else {
			it = new MergeIterator3(t, util.getCnf().getVariables().size(), groupedPresenceConditions);
		}
		numberOfCombinations = it.size();
		if (numberOfCombinations == 0) {
			final LiteralSet[] solverSolutions = util.getSolverSolutions();
			if ((solverSolutions.length > 0) && (solverSolutions[0] != null)) {
				newConfiguration(solverSolutions[0]);
			}
		} else {
			coveredCount = 0;
			invalidCount = 0;

			samplingMonitor = new MonitorThread(new SamplingMonitor(), 60_000);
			try {
				samplingMonitor.start();
				final List<ClauseList> combinationListUncovered = new ArrayList<>();
				count = coveredCount;
				phaseCount++;
				ICoverStrategy phase = phaseList.get(0);
				while (true) {
					monitor.checkCancel();
					final ClauseList combinedCondition = it.get();
					if (combinedCondition == null) {
						break;
					}
					if (combinedCondition.isEmpty()) {
						invalidCount++;
					} else {
						final CombinationStatus covered = phase.cover(combinedCondition);
						switch (covered) {
						case NOT_COVERED:
							combinationListUncovered.add(combinedCondition);
							break;
						case COVERED:
							coveredCount++;
							combinedCondition.clear();
							break;
						case INVALID:
							invalidCount++;
							combinedCondition.clear();
							break;
						default:
							combinedCondition.clear();
							break;
						}
					}
					count++;
				}

				int coveredIndex = -1;
				for (int j = 1; j < phaseList.size(); j++) {
					phaseCount++;
					phase = phaseList.get(j);
					count = coveredCount + invalidCount;
					for (int i = coveredIndex + 1; i < combinationListUncovered.size(); i++) {
						final ClauseList combination = combinationListUncovered.get(i);
						final CombinationStatus covered = phase.cover(combination);
						switch (covered) {
						case COVERED:
							Collections.swap(combinationListUncovered, i, ++coveredIndex);
							coveredCount++;
							break;
						case NOT_COVERED:
							break;
						case INVALID:
							Collections.swap(combinationListUncovered, i, ++coveredIndex);
							invalidCount++;
							break;
						default:
							break;
						}
						count++;
					}
				}
			} finally {
				samplingMonitor.finish();
			}
		}

		curResult = getResultList();
		if ((bestResult == null) || (bestResult.size() > curResult.size())) {
			bestResult = new ArrayList<>(curResult.size());
			curResult.stream().map(TWiseConfiguration::clone).forEach(bestResult::add);
		}
	}

	public int getIterations() {
		return iterations;
	}

	public void setIterations(int iterations) {
		this.iterations = iterations;
	}

	public boolean isAllowInitialSolutionModify() {
		return allowInitialSolutionModify;
	}

	public void setAllowInitialSolutionModify(boolean allowInitialSolutionModify) {
		this.allowInitialSolutionModify = allowInitialSolutionModify;
	}

	public boolean isAllowInitialSolutionRemove() {
		return allowInitialSolutionRemove;
	}

	public void setAllowInitialSolutionRemove(boolean allowInitialSolutionRemove) {
		this.allowInitialSolutionRemove = allowInitialSolutionRemove;
	}

	public boolean isCountInitialSolutionForLimit() {
		return countInitialSolutionForLimit;
	}

	public void setCountInitialSolutionForLimit(boolean countInitialSolutionForLimit) {
		this.countInitialSolutionForLimit = countInitialSolutionForLimit;
	}

	public boolean isCombinationValid(LiteralSet literals) {
		return util.isCombinationValid(literals);
	}

	public List<List<ClauseList>> getNodes() {
		return nodes;
	}

	public void setNodes(List<List<ClauseList>> nodes) {
		this.nodes = nodes;
		presenceConditionManager = null;
	}

	public boolean removeInvalidClauses(ClauseList nextCondition, List<Pair<LiteralSet, TWiseConfiguration>> candidatesList) {
		int validCount = nextCondition.size();
		for (final LiteralSet literals : nextCondition) {
			if (!util.isCombinationValid(literals)) {
				validCount--;
				for (final Iterator<Pair<LiteralSet, TWiseConfiguration>> iterator = candidatesList.iterator(); iterator.hasNext();) {
					final Pair<LiteralSet, TWiseConfiguration> pair = iterator.next();
					if (pair.getKey().equals(literals)) {
						iterator.remove();
					}
				}
			}
		}
		return validCount == 0;
	}

	public static boolean isCovered(ClauseList condition, Iterable<? extends LiteralSet> solutionList) {
		for (final LiteralSet configuration : solutionList) {
			for (final LiteralSet literals : condition) {
				if (configuration.containsAll(literals)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isCovered(ClauseList condition) {
		return isCovered(condition, completeSolutionList) || isCovered(condition, incompleteSolutionList);
	}

	public boolean select(TWiseConfiguration solution, Deduce deduce, LiteralSet literals) {
		solution.selectLiterals(deduce, literals.getLiterals());

		if (solution.isComplete()) {
			solution.clear();
			for (final Iterator<TWiseConfiguration> it = incompleteSolutionList.iterator(); it.hasNext();) {
				if (it.next() == solution) {
					it.remove();
					completeSolutionList.add(solution);
					break;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	public static boolean isCandidate(final LiteralSet literals, TWiseConfiguration solution) {
		return !solution.hasConflicts(literals);
	}

	public void addCandidates(final LiteralSet literals, List<Pair<LiteralSet, TWiseConfiguration>> candidatesList) {
		for (final TWiseConfiguration configuration : incompleteSolutionList) {
			if ((allowInitialSolutionModify || !configuration.isInitial()) && isCandidate(literals, configuration)) {
				candidatesList.add(new Pair<>(literals, configuration));
			}
		}
	}

	public void initCandidatesList(ClauseList nextCondition, List<Pair<LiteralSet, TWiseConfiguration>> candidatesList) {
		candidatesList.clear();
		for (final LiteralSet literals : nextCondition) {
			addCandidates(literals, candidatesList);
		}
		Collections.sort(candidatesList, candidateLengthComparator);
	}

	protected boolean cover(boolean useSolver, List<Pair<LiteralSet, TWiseConfiguration>> candidatesList) {
		for (final Pair<LiteralSet, TWiseConfiguration> pair : candidatesList) {
			if (useSolver) {
				if (util.isSelectionPossibleSAT(pair.getKey(), pair.getValue())) {
					select(pair.getValue(), Deduce.TraverseStrong, pair.getKey());
					return true;
				}
			} else {
				if (util.isSelectionPossibleHistory(pair.getKey(), pair.getValue())) {
					select(pair.getValue(), Deduce.TraverseStrong, pair.getKey());
					return true;
				}
			}
		}
		return false;
	}

	public void newInitialConfiguration(final LiteralSet literals) {
		final int initialIndex = completeSolutionList.size() + incompleteSolutionList.size();
		TWiseConfiguration configuration = null;
		if (allowInitialSolutionModify) {
			configuration = new TWiseConfiguration(util, initialIndex);
			configuration.setCoreLiterals();
			configuration.selectLiterals(Deduce.DecisionPropagation, literals.getLiterals());
			configuration.updateSolverSolutions();
		} else {
			configuration = new TWiseConfiguration(util, initialIndex);
			configuration.selectLiterals(Deduce.None, literals.getLiterals());
			configuration.setModifiable(false);
		}
		addToConfigurationToList(configuration);
	}

	public void newConfiguration(final LiteralSet literals) {
		TWiseConfiguration configuration = null;
		final int size = (completeSolutionList.size() + incompleteSolutionList.size()) - (countInitialSolutionForLimit ? 0 : initialSample.size());
		if (size < maxSampleSize) {
			configuration = new TWiseConfiguration(util);
			configuration.setCoreLiterals();
			configuration.selectLiterals(Deduce.DecisionPropagation, literals.getLiterals());
			configuration.updateSolverSolutions();
			addToConfigurationToList(configuration);
		}
	}

	private void addToConfigurationToList(TWiseConfiguration configuration) {
		if (configuration.isComplete()) {
			configuration.clear();
			completeSolutionList.add(configuration);
		} else {
			incompleteSolutionList.add(configuration);
			Collections.sort(incompleteSolutionList, (a, b) -> a.countLiterals() - b.countLiterals());
		}
	}

	public List<TWiseConfiguration> getIncompleteSolutionList() {
		return incompleteSolutionList;
	}

	public List<TWiseConfiguration> getCompleteSolutionList() {
		return completeSolutionList;
	}

	public List<TWiseConfiguration> getResultList() {
		final ArrayList<TWiseConfiguration> resultList = new ArrayList<>(completeSolutionList.size() + incompleteSolutionList.size());
		resultList.addAll(incompleteSolutionList);
		resultList.addAll(completeSolutionList);
		return resultList;
	}

	public Set<String> getCoveredInteractions(LiteralSet configuration) {
		final ICombinationSupplier<ClauseList> it;
		final List<List<PresenceCondition>> groupedPresenceConditions = presenceConditionManager.getGroupedPresenceConditions();
		if (groupedPresenceConditions.size() == 1) {
			it = new SingleIterator(t, util.getCnf().getVariables().size(), groupedPresenceConditions.get(0));
		} else {
			it = new MergeIterator3(t, util.getCnf().getVariables().size(), groupedPresenceConditions);
		}

		final Set<String> coveredInteractions = new HashSet<>();
		for (ClauseList combinedCondition = it.get(); combinedCondition != null; combinedCondition = it.get()) {
			if (combinedCondition.size() > 0) {
				final LiteralSet set = combinedCondition.get(0);
				if (configuration.containsAll(set)) {
					set.setOrder(LiteralSet.Order.NATURAL);
					coveredInteractions.add(set.toString());
				}
			}
		}
		return coveredInteractions;
	}

}
