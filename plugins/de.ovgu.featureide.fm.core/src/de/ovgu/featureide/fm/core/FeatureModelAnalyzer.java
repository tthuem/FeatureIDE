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
package de.ovgu.featureide.fm.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.ovgu.featureide.fm.core.AnalysesCollection.StringToFeature;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties.ConstraintStatus;
import de.ovgu.featureide.fm.core.analysis.FeatureModelProperties;
import de.ovgu.featureide.fm.core.analysis.FeatureModelProperties.FeatureModelStatus;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties.FeatureStatus;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CauseAnalysis.Anomalies;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.HasSolutionAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.event.FeatureIDEEvent;
import de.ovgu.featureide.fm.core.base.event.IEventListener;
import de.ovgu.featureide.fm.core.explanations.Explanation;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.MultipleAnomaliesExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.MultipleAnomaliesExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanationCreator;
import de.ovgu.featureide.fm.core.filter.FeatureSetFilter;
import de.ovgu.featureide.fm.core.filter.OptionalFeatureFilter;
import de.ovgu.featureide.fm.core.functional.Functional;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

/**
 * A collection of methods for working with {@link IFeatureModel} will replace the corresponding methods in {@link IFeatureModel}
 *
 * @author Soenke Holthusen
 * @author Florian Proksch
 * @author Stefan Krueger
 * @author Sebastian Krieter
 * @author Marcus Pinnecke (Feature Interface)
 */
public class FeatureModelAnalyzer implements IEventListener {

	private final FeatureModelFormula formula;
	private final IFeatureModel featureModel;
	private final List<IConstraint> constraints;

	private final AnalysesCollection analysesCollection;

	public void reset() {
		analysesCollection.reset(formula);
	}

	public FeatureModelAnalyzer(IFeatureModel featureModel) {
		this(new FeatureModelFormula(featureModel));
	}

	public FeatureModelAnalyzer(FeatureModelFormula formula) {
		this.formula = formula;
		featureModel = formula.getFeatureModel();
		constraints = featureModel.getConstraints();
		analysesCollection = new AnalysesCollection();

		analysesCollection.init(formula);
		analysesCollection.reset(formula);
	}

	/**
	 * Tests if <code>featureModel</code> is valid.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return {@code true} if the feature model is void, {@code false} if the feature model is not void or an error occurred.
	 */
	public boolean isValid(IMonitor<Boolean> monitor) {
		return isValid(monitor, -1).orElse(Boolean.FALSE);
	}

	/**
	 * Tests if <code>featureModel</code> is valid.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<Boolean> isValid(IMonitor<Boolean> monitor, int timeout) {
		final Optional<Boolean> result = analysesCollection.validAnalysis.getResult(monitor, timeout);
		if (result.isPresent() && !result.get()) {
			getFeatureModelProperties().setStatus(FeatureModelStatus.VOID);
		}
		return result;
	}

	/**
	 * Computes a list of all core features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IFeature> getCoreFeatures(IMonitor<LiteralSet> monitor) {
		return getCoreFeatures(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all dead features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IFeature> getDeadFeatures(IMonitor<LiteralSet> monitor) {
		return getDeadFeatures(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Returns the list of features that occur in all variants, where one of the given features is selected. If the given list of features is empty, this method
	 * will calculate the features that are present in all variants specified by the feature model.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IFeature> getCommonFeatures(IMonitor<LiteralSet> monitor) {
		return getCommonFeatures(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all atomic sets.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<List<IFeature>> getAtomicSets(IMonitor<List<LiteralSet>> monitor) {
		return getAtomicSets(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all indeterminate hidden features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IFeature> getIndeterminedHiddenFeatures(IMonitor<LiteralSet> monitor) {
		return getIndeterminedHiddenFeatures(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all false-optional features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IFeature> getFalseOptionalFeatures(IMonitor<List<LiteralSet>> monitor) {
		return getFalseOptionalFeatures(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all void constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IConstraint> getVoidConstraints(IMonitor<List<LiteralSet>> monitor) {
		return getVoidConstraints(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all redundant constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IConstraint> getRedundantConstraints(IMonitor<List<LiteralSet>> monitor) {
		return getRedundantConstraints(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all contradictory constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IConstraint> getContradictoryConstraints(IMonitor<List<LiteralSet>> monitor) {
		return getContradictoryConstraints(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all tautology constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IConstraint> getTautologyConstraints(IMonitor<List<LiteralSet>> monitor) {
		return getTautologyConstraints(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all constraints with an anomaly (e.g. redundancy).
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IConstraint> getAnomalyConstraints(IMonitor<List<Anomalies>> monitor) {
		return getAnomalyConstraints(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all constraints with an anomaly (e.g. redundancy). Considers only constraints that are set to true in the given index.
	 *
	 * @param relevantConstraint an index indicating which constraints to check. Uses the constraint order of the feature model.
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<IConstraint> getAnomalyConstraints(boolean[] relevantConstraint, IMonitor<List<Anomalies>> monitor) {
		return getAnomalyConstraints(relevantConstraint, monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all atomic sets.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @return a non-null result list. Empty if an error occurred.
	 */
	public List<Map<IFeature, Boolean>> getAtomicSetsMap(IMonitor<List<LiteralSet>> monitor) {
		return getAtomicSetsMap(monitor, -1).orElse(Collections.emptyList());
	}

	/**
	 * Computes a list of all core features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IFeature>> getCoreFeatures(IMonitor<LiteralSet> monitor, int timeout) {
		return analysesCollection.coreDeadAnalysis.getResult(monitor, timeout).map(list -> convertToFeatureList(list, true, false));
	}

	/**
	 * Computes a list of all dead features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IFeature>> getDeadFeatures(IMonitor<LiteralSet> monitor, int timeout) {
		return analysesCollection.coreDeadAnalysis.getResult(monitor, timeout).map(list -> convertToFeatureList(list, false, true));
	}

	/**
	 * Returns the list of features that occur in all variants, where one of the given features is selected. If the given list of features is empty, this method
	 * will calculate the features that are present in all variants specified by the feature model.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IFeature>> getCommonFeatures(IMonitor<LiteralSet> monitor, int timeout) {
		return analysesCollection.coreDeadAnalysis.getResult(monitor, timeout).map(list -> featureModel.getFeatures().stream()
				.filter(new FeatureSetFilter(convertToFeatureList(list, true, true)).negate()).collect(Collectors.toList()));
	}

	/**
	 * Computes a list of all atomic sets.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<List<IFeature>>> getAtomicSets(IMonitor<List<LiteralSet>> monitor, int timeout) {
		return analysesCollection.atomicSetAnalysis.getResult(monitor, timeout).map(list -> {
			final CNF cnf = formula.getCNF();
			final ArrayList<List<IFeature>> resultList = new ArrayList<>();
			for (final LiteralSet literalList : list) {
				final List<IFeature> setList = new ArrayList<>();
				resultList.add(convertToFeatureList(literalList, true, true));

				for (final int literal : literalList.getLiterals()) {
					final IFeature feature = featureModel.getFeature(cnf.getVariables().getName(literal));
					if (feature != null) {
						setList.add(feature);
					}
				}
			}
			return resultList;
		});
	}

	/**
	 * Computes a list of all indeterminate hidden features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IFeature>> getIndeterminedHiddenFeatures(IMonitor<LiteralSet> monitor, int timeout) {
		return analysesCollection.determinedAnalysis.getResult(monitor, timeout).map(list -> convertToFeatureList(list, true, false));
	}

	/**
	 * Computes a list of all false-optional features.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IFeature>> getFalseOptionalFeatures(IMonitor<List<LiteralSet>> monitor, int timeout) {
		final List<IFeature> optionalFeatures = Functional.filterToList(featureModel.getFeatures(), new OptionalFeatureFilter());
		return getFalseOptionalFeatures(optionalFeatures, monitor, timeout).map(list -> selectPositiveElements(optionalFeatures, list));
	}

	/**
	 * Computes a list of all false-optional features, contained in the given list of features.
	 *
	 * @param optionalFeatures a list of optional features
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	private Optional<List<LiteralSet>> getFalseOptionalFeatures(final List<IFeature> optionalFeatures, IMonitor<List<LiteralSet>> monitor, int timeout) {
		analysesCollection.foAnalysis.setOptionalFeatures(optionalFeatures);
		return analysesCollection.foAnalysis.getResult(monitor, timeout);
	}

	private List<IFeature> convertToFeatureList(final LiteralSet result, boolean includePositive, boolean includeNegative) {
		return Functional.mapToList(formula.getCNF().getVariables().convertToString(result, includePositive, includeNegative, false),
				new StringToFeature(featureModel));
	}

	/**
	 * Computes a list of all void constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IConstraint>> getVoidConstraints(IMonitor<List<LiteralSet>> monitor, int timeout) {
		analysesCollection.constraintVoidAnalysis.setConstraints(constraints);
		return analysesCollection.constraintVoidAnalysis.getResult(monitor, timeout).map(list -> selectPositiveElements(constraints, list));
	}

	/**
	 * Computes a list of all redundant constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IConstraint>> getRedundantConstraints(IMonitor<List<LiteralSet>> monitor, int timeout) {
		analysesCollection.constraintRedundancyAnalysis.setConstraints(constraints);
		return analysesCollection.constraintRedundancyAnalysis.getResult(monitor, timeout).map(list -> selectPositiveElements(constraints, list));
	}

	/**
	 * Computes a list of all contradictory constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IConstraint>> getContradictoryConstraints(IMonitor<List<LiteralSet>> monitor, int timeout) {
		final Optional<List<IConstraint>> voidConstraints = getVoidConstraints(monitor != null ? monitor.subTask(1) : null, timeout);
		return voidConstraints.flatMap(constraints -> {
			analysesCollection.constraintContradictionAnalysis.setConstraints(constraints);
			return analysesCollection.constraintContradictionAnalysis.getResult(monitor, timeout).map(list -> selectPositiveElements(constraints, list));
		});
	}

	/**
	 * Computes a list of all tautology constraints.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IConstraint>> getTautologyConstraints(IMonitor<List<LiteralSet>> monitor, int timeout) {
		final Optional<List<IConstraint>> redundantConstraints = getRedundantConstraints(monitor != null ? monitor.subTask(1) : null, timeout);
		return redundantConstraints.flatMap(constraints -> {
			analysesCollection.constraintTautologyAnalysis.setConstraints(constraints);
			return analysesCollection.constraintTautologyAnalysis.getResult(monitor, timeout).map(list -> selectPositiveElements(constraints, list));
		});
	}

	/**
	 * Computes a list of all constraints with an anomaly (e.g. redundancy).
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IConstraint>> getAnomalyConstraints(IMonitor<List<Anomalies>> monitor, int timeout) {
		int i = 0;
		final boolean[] relevantConstraint = new boolean[constraints.size()];
		for (final IConstraint constraint : constraints) {
			final ConstraintProperties constraintProperties = getConstraintProperties(constraint);
			if (constraintProperties.hasStatus(ConstraintStatus.NECESSARY) && constraintProperties.hasStatus(ConstraintStatus.SATISFIABLE)) {
				relevantConstraint[i] = true;
			}
			i++;
		}
		return getAnomalyConstraints(relevantConstraint, monitor, timeout);
	}

	/**
	 * Computes a list of all constraints with an anomaly (e.g. redundancy). Considers only constraints that are set to true in the given index.
	 *
	 * @param relevantConstraint an index indicating which constraints to check. Uses the constraint order of the feature model.
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<IConstraint>> getAnomalyConstraints(boolean[] relevantConstraint, IMonitor<List<Anomalies>> monitor, int timeout) {
		analysesCollection.constraintAnomaliesAnalysis.setRelevantConstraint(relevantConstraint);
		return analysesCollection.constraintAnomaliesAnalysis.getResult(monitor, timeout).map(list -> {
			final List<IConstraint> resultList = new ArrayList<>();
			final Variables variables = formula.getCNF().getVariables();
			for (int i = 0; i < analysesCollection.constraintAnomaliesAnalysis.getClauseGroupSize().length; i++) {
				final Anomalies anomalies = list.get(i);
				if (anomalies != null) {
					if (anomalies.getRedundantClauses() != null) {
						final ArrayList<IFeature> falseOptionalFeatures = new ArrayList<>();
						for (final LiteralSet literalSet : anomalies.getRedundantClauses()) {
							if (literalSet != null) {
								falseOptionalFeatures.add(featureModel.getFeature(variables.getName(literalSet.getLiterals()[1])));
							}
						}
						final IConstraint constraint = constraints.get(i);
						getConstraintProperties(constraint).setFalseOptionalFeatures(falseOptionalFeatures);
						resultList.add(constraint);
					}
					if (anomalies.getDeadVariables() != null) {
						final IConstraint constraint = constraints.get(i);
						getConstraintProperties(constraint).setDeadFeatures(Functional
								.mapToList(variables.convertToString(anomalies.getDeadVariables(), false, true, false), new StringToFeature(featureModel)));
						resultList.add(constraint);
					}
				}
			}
			return resultList;
		});
	}

	/**
	 * Computes a list of all atomic sets.
	 *
	 * @param monitor a {@link IMonitor monitor} instance (can be null)
	 * @param timeout a timeout in ms
	 * @return an {@link Optional} containing the analysis result. Empty if a timeout or an error occurred.
	 */
	public Optional<List<Map<IFeature, Boolean>>> getAtomicSetsMap(IMonitor<List<LiteralSet>> monitor, int timeout) {
		return analysesCollection.atomicSetAnalysis.getResult(monitor, timeout).map(list -> {
			final List<Map<IFeature, Boolean>> resultList = new ArrayList<>();
			final Variables variables = formula.getCNF().getVariables();
			final Set<IFeature> coveredFeatures = new HashSet<>();
			for (final LiteralSet literalList : list) {
				final Map<IFeature, Boolean> setList = new HashMap<>();

				for (final int literal : literalList.getLiterals()) {
					final IFeature feature = featureModel.getFeature(variables.getName(literal));
					if ((feature != null) && coveredFeatures.add(feature)) {
						setList.put(feature, literal > 0);
					}
				}

				if (!setList.isEmpty()) {
					resultList.add(setList);
				}
			}
			return resultList;
		});
	}

	private static <T> List<T> selectPositiveElements(final List<T> elements, List<?> positiveElements) {
		final ArrayList<T> resultList = new ArrayList<>();
		final Iterator<?> positiveIt = positiveElements.iterator();
		for (final T element : elements) {
			if (positiveIt.next() != null) {
				resultList.add(element);
			}
		}
		resultList.trimToSize();
		return resultList;
	}

	public FeatureProperties getFeatureProperties(IFeature feature) {
		return analysesCollection.featurePropertiesMap.get(feature);
	}

	public boolean hasDeadFeatures(Collection<IFeature> features) {
		return analysesCollection.featurePropertiesMap.entrySet().stream()
				.filter(entry -> features.contains(entry.getKey()) && entry.getValue().hasStatus(FeatureStatus.DEAD)).count() > 0;
	}

	public ConstraintProperties getConstraintProperties(IConstraint constraint) {
		return analysesCollection.constraintPropertiesMap.get(constraint);
	}

	public FeatureModelProperties getFeatureModelProperties() {
		return analysesCollection.featureModelProperties;
	}

	/**
	 * Analyzes the feature model.
	 *
	 * @param monitor monitor
	 * @return Hashmap: key entry is Feature/Constraint, value usually indicating the kind of attribute
	 */
	/*
	 * check all changes of this method and called methods with the related tests and benchmarks, see fm.core-test plug-in think about performance (no
	 * unnecessary or redundant calculations) Hashing might be fast for locating features, but creating a HashSet is costly So LinkedLists are much faster
	 * because the number of feature in the set is usually small (e.g. dead features)
	 */
	public AnalysesCollection analyzeFeatureModel(IMonitor<Boolean> monitor) {
		if (monitor == null) {
			monitor = new NullMonitor<>();
		}

		try {
			int work = 1;
			if (analysesCollection.isCalculateFeatures() || analysesCollection.isCalculateConstraints()) {
				work += 1;
			}
			if (analysesCollection.isCalculateFeatures()) {
				work += 4;
			}
			if (analysesCollection.isCalculateConstraints()) {
				work += 19;
			}
			monitor.setRemainingWork(work);

			updateFeatureModel(monitor);

			updateFeatures(monitor);

			updateConstraints(monitor);
		} finally {
			monitor.done();
		}

		return analysesCollection;
	}

	public void updateConstraints() {
		updateConstraints(null);
	}

	protected void updateConstraints(IMonitor<Boolean> monitor) {
		if (analysesCollection.isCalculateConstraints()) {
			if (monitor == null) {
				monitor = new NullMonitor<>();
			}
			monitor.checkCancel();
			// set default values for constraint properties
			for (final IConstraint constraint : constraints) {
				final ConstraintProperties constraintProperties = getConstraintProperties(constraint);
				constraintProperties.resetStatus();
				constraintProperties.setStatus(ConstraintStatus.NECESSARY);
				constraintProperties.setStatus(ConstraintStatus.SATISFIABLE);
			}
			monitor.worked();

			monitor.checkCancel();
			final FeatureModelProperties properties = getFeatureModelProperties();
			if (properties.hasStatus(FeatureModelStatus.VOID)) {
				final List<IConstraint> voidConstraints = getVoidConstraints(monitor.subTask(2), -1).orElse(Collections.emptyList());
				for (final IConstraint constraint : voidConstraints) {
					getConstraintProperties(constraint).setStatus(ConstraintStatus.VOID);
				}
				monitor.checkCancel();
				final List<IConstraint> contradictoryConstraints = getContradictoryConstraints(monitor.subTask(2), -1).orElse(Collections.emptyList());
				for (final IConstraint constraint : contradictoryConstraints) {
					getConstraintProperties(constraint).setStatus(ConstraintStatus.UNSATISFIABLE);
				}
				monitor.worked(15);
			} else {
				// get constraint anomalies
				final Collection<IConstraint> redundantConstraints = annotateConstraints(ConstraintStatus.REDUNDANT, monitor);
				monitor.checkCancel();
				final Collection<IConstraint> tautologyConstraints = annotateConstraints(ConstraintStatus.TAUTOLOGY, monitor);

				if (!redundantConstraints.isEmpty() || !tautologyConstraints.isEmpty()) {
					properties.setStatus(FeatureModelStatus.ANOMALIES);
				}

				monitor.checkCancel();
				getAnomalyConstraints(monitor.subTask(10), -1);
			}
		}
	}

	/**
	 * Annotates the constraints in <code>constraints</code> with <code>status</code>, which might either {@link ConstraintStatus#REDUNDANT} or
	 * {@link ConstraintStatus#TAUTOLOGY} (as redundant constraints or tautologies). This triggers a constraint analysis using the given <code>monitor</code>.
	 * For any other status, this method returns an empty list.
	 *
	 * @param status - {@link ConstraintStatus}
	 * @param monitor - {@link IMonitor} <code>monitor</code> may also be null, in which case a {@link NullMonitor} is used.
	 * @return new {@link Collection}
	 */
	public Collection<IConstraint> annotateConstraints(ConstraintStatus status, IMonitor<Boolean> monitor) {
		if (monitor == null) {
			monitor = new NullMonitor<>();
		}
		final List<IConstraint> annotatedConstraints;

		switch (status) {
		case REDUNDANT:
			annotatedConstraints = getRedundantConstraints(monitor.subTask(2), -1).orElse(Collections.emptyList());
			break;
		case TAUTOLOGY:
			annotatedConstraints = getTautologyConstraints(monitor.subTask(2), -1).orElse(Collections.emptyList());
			break;
		default:
			annotatedConstraints = Collections.emptyList();
			break;
		}
		annotatedConstraints.forEach(constraint -> getConstraintProperties(constraint).setStatus(status));
		return annotatedConstraints;
	}

	public void updateFeatures() {
		updateFeatures(null);
	}

	protected void updateFeatureModel(IMonitor<Boolean> monitor) {
		if (analysesCollection.isCalculateFeatures() || analysesCollection.isCalculateConstraints()) {
			if (monitor == null) {
				monitor = new NullMonitor<>();
			}
			monitor.checkCancel();
			final FeatureModelProperties properties = getFeatureModelProperties();
			if (isValid(monitor.subTask(1))) {
				properties.setStatus(FeatureModelStatus.VALID);
			} else {
				properties.setStatus(FeatureModelStatus.VOID);
			}
		}
	}

	protected void updateFeatures(IMonitor<Boolean> monitor) {
		if (analysesCollection.isCalculateFeatures()) {
			if (monitor == null) {
				monitor = new NullMonitor<>();
			}
			featureModel.getFeatures().forEach(feature -> getFeatureProperties(feature).resetStatus());

			annotateFeatures(FeatureStatus.COMMON, monitor);
			annotateFeatures(FeatureStatus.MANDATORY, monitor);
			annotateFeatures(FeatureStatus.OPTIONAL, monitor);
			annotateFeatures(FeatureStatus.GROUP, monitor);

			monitor.worked();

			monitor.checkCancel();
			final FeatureModelProperties properties = getFeatureModelProperties();
			if (properties.hasStatus(FeatureModelStatus.VOID)) {
				for (final IFeature feature : featureModel.getFeatures()) {
					final FeatureProperties featureProperties = getFeatureProperties(feature);
					featureProperties.setStatus(FeatureStatus.DEAD);
				}
				monitor.worked(3);
			} else {
				// get feature anomalies
				final Collection<IFeature> deadFeatures = annotateFeatures(FeatureStatus.DEAD, monitor);
				monitor.checkCancel();
				final Collection<IFeature> falseOptionalFeatures = annotateFeatures(FeatureStatus.FALSE_OPTIONAL, monitor);
				monitor.checkCancel();
				final Collection<IFeature> indeterminedHiddenFeatures = annotateFeatures(FeatureStatus.INDETERMINATE_HIDDEN, monitor);
				if (!deadFeatures.isEmpty() || !falseOptionalFeatures.isEmpty() || !indeterminedHiddenFeatures.isEmpty()) {
					properties.setStatus(FeatureModelStatus.ANOMALIES);
				}
			}
		}
	}

	/**
	 * Annotates the features of <code>featureModelFormula</code> with the given status, i.e. checks if they are common, mandatory, optional features, belong to
	 * a group, or are dead, false optional or indeterminate. The latter three analyzes require the <code>monitor</code> object, though a monitor is not
	 * necessary. Afterwards returns the annotated features.
	 *
	 * @param status - {@link FeatureStatus}
	 * @param monitor - {@link IMonitor}
	 * @return new {@link Collection}
	 */
	public Collection<IFeature> annotateFeatures(FeatureStatus status, IMonitor<Boolean> monitor) {
		if (monitor == null) {
			monitor = new NullMonitor<>();
		}
		final Collection<IFeature> annotatedFeatures;

		switch (status) {
		case COMMON:
			annotatedFeatures = featureModel.getFeatures();
			break;
		case MANDATORY:
			annotatedFeatures = featureModel.getFeatures().stream().map(feature -> feature.getStructure())
					.filter(structure -> structure.isRoot() || (structure.isMandatorySet() && structure.getParent().isAnd())).map(s -> s.getFeature())
					.collect(Collectors.toList());
			break;
		case OPTIONAL:
			annotatedFeatures = featureModel.getFeatures().stream().map(feature -> feature.getStructure())
					.filter(structure -> !structure.isRoot() && !structure.isMandatorySet() && structure.getParent().isAnd())
					.map(structure -> structure.getFeature()).collect(Collectors.toList());
			break;
		case GROUP:
			annotatedFeatures = featureModel.getFeatures().stream().map(feature -> feature.getStructure())
					.filter(structure -> !structure.isRoot() && !structure.isAnd()).map(structure -> structure.getFeature()).collect(Collectors.toList());
			break;
		case DEAD:
			if (getFeatureModelProperties().hasStatus(FeatureModelStatus.VOID)) {
				annotatedFeatures = featureModel.getFeatures();
			} else {
				annotatedFeatures = getDeadFeatures(monitor.subTask(1), -1).orElse(Collections.emptyList());
			}
			break;
		case FALSE_OPTIONAL:
			annotatedFeatures = getFalseOptionalFeatures(monitor.subTask(1), -1).orElse(Collections.emptyList());
			break;
		case INDETERMINATE_HIDDEN:
			annotatedFeatures = getIndeterminedHiddenFeatures(monitor.subTask(1), -1).orElse(Collections.emptyList());
			break;
		default:
			annotatedFeatures = Collections.emptyList();
			break;
		}

		annotatedFeatures.forEach(feature -> getFeatureProperties(feature).setStatus(status));
		return new ArrayList<>(annotatedFeatures);
	}

	// TODO implement as analysis
	public int countConcreteFeatures() {
		int number = 0;
		for (final IFeature feature : featureModel.getFeatures()) {
			if (feature.getStructure().isConcrete()) {
				number++;
			}
		}
		return number;
	}

	// TODO implement as analysis
	public int countHiddenFeatures() {
		int number = 0;
		for (final IFeature feature : featureModel.getFeatures()) {
			final IFeatureStructure structure = feature.getStructure();
			if (structure.isHidden() || structure.hasHiddenParent()) {
				number++;
			}
		}
		return number;
	}

	// TODO implement as analysis
	public int countTerminalFeatures() {
		int number = 0;
		for (final IFeature feature : featureModel.getFeatures()) {
			if (!feature.getStructure().hasChildren()) {
				number++;
			}
		}
		return number;
	}

	/**
	 * Returns an explanation why the given feature model element is defect.
	 *
	 * @param modelElement potentially defect feature model element; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public Explanation<?> getExplanation(IFeatureModelElement modelElement) {
		return getExplanation(modelElement, formula);
	}

	/**
	 * Returns an explanation why the given feature model element is defect or null if it cannot be explained.
	 *
	 * @param modelElement potentially defect feature model element
	 * @param context another feature model that is used as reference for the explanations
	 * @return an explanation why the given feature model element is defect or null if it cannot be explained
	 */
	public Explanation<?> getExplanation(IFeatureModelElement modelElement, FeatureModelFormula context) {
		if (modelElement instanceof IFeature) {
			return getFeatureExplanation((IFeature) modelElement, context);
		} else if (modelElement instanceof IConstraint) {
			return getConstraintExplanation((IConstraint) modelElement, context);
		} else {
			return null;
		}
	}

	/**
	 * Returns an explanation why the given constraint is defect or null if it cannot be explained.
	 *
	 * @param constraint potentially defect constraint
	 * @return an explanation why the given constraint is defect or null if it cannot be explained
	 */
	public Explanation<?> getConstraintExplanation(IConstraint constraint, FeatureModelFormula context) {
		synchronized (constraint) {
			Explanation<?> explanation = null;
			final ConstraintProperties constraintProperties = getConstraintProperties(constraint);

			if (constraintProperties != null) {
				if (constraintProperties.hasStatus(ConstraintStatus.REDUNDANT)) {
					explanation = constraintProperties.getRedundantExplanation();
					if (explanation == null) {
						// TODO use context
						explanation = analysesCollection.createExplanation(analysesCollection.redundantConstraintExplanationCreator, constraint, context);
						constraintProperties.setRedundantExplanation(explanation);
					}
				}
			}
			return explanation;
		}
	}

	/**
	 * Returns an explanation why the given feature is defect or null if it cannot be explained.
	 *
	 * @param feature potentially defect feature
	 * @return an explanation why the given feature is defect or null if it cannot be explained
	 */
	public Explanation<?> getFeatureExplanation(IFeature feature, FeatureModelFormula context) {
		synchronized (feature) {
			Explanation<?> explanation = null;
			final FeatureProperties featureProperties = getFeatureProperties(feature);
			if (featureProperties != null) {
				// Test if feature is the root feature; in that case return the MultipleAnomaliesException.
				if (FeatureUtils.isRoot(feature) && (getMultipleAnomaliesExplanation() != null)) {
					explanation = getMultipleAnomaliesExplanation();
				}
				if (featureProperties.hasStatus(FeatureStatus.DEAD)) {
					explanation = featureProperties.getDeadExplanation();
					if (explanation == null) {
						explanation = analysesCollection.createExplanation(analysesCollection.deadFeatureExplanationCreator, feature, context);
						featureProperties.setDeadExplanation(explanation);
					}
				}
				if (featureProperties.hasStatus(FeatureStatus.FALSE_OPTIONAL)) {
					explanation = featureProperties.getFalseOptionalExplanation();
					if (explanation == null) {
						explanation = analysesCollection.createExplanation(analysesCollection.falseOptionalFeatureExplanationCreator, feature, context);
						featureProperties.setFalseOptionalExplanation(explanation);
					}
				}
			}
			return explanation;
		}
	}

	/**
	 * <p> Returns whether the conjunction of A always implies the disjunction of B in the current feature model. </p>
	 *
	 * <p> In other words, the following satisfiability query is checked: </p>
	 *
	 * <p> Note that this formula is always true if B is empty. </p>
	 *
	 * @param a set of features that form a conjunction
	 * @param b set of features that form a disjunction
	 * @return whether the conjunction of A always implies the disjunction of B in the current feature model
	 *
	 * @deprecated Use ConfigurationPropagator instead.
	 */
	@Deprecated
	public boolean checkImplies(Collection<IFeature> a, Collection<IFeature> b) {
		if (b.isEmpty()) {
			return true;
		}

		final CNF cnf = formula.getCNF();
		final Variables variables = cnf.getVariables();

		// (A1 and ... or An) => (B1 or ... or Bm)
		// |= -A1 or ... or -An or B1 or ... or Bm
		// |= -(A1 and ... and An and -B1 and ... and -Bm)
		final int[] literals = new int[a.size() + b.size()];
		int index = 0;
		for (final IFeature feature : b) {
			literals[index++] = -variables.getVariable(feature.getName());
		}
		for (final IFeature feature : a) {
			literals[index++] = variables.getVariable(feature.getName());
		}

		final HasSolutionAnalysis analysis = new HasSolutionAnalysis(cnf);
		analysis.setAssumptions(new LiteralSet(literals));

		return LongRunningWrapper.runMethod(analysis);
	}

	/**
	 * @deprecated Use ConfigurationPropagator instead.
	 */
	@Deprecated
	public boolean checkIfFeatureCombinationPossible(IFeature feature1, Collection<IFeature> dependingFeatures) {
		if (dependingFeatures.isEmpty()) {
			return true;
		}

		final CNF cnf = formula.getCNF();
		final Variables variables = cnf.getVariables();

		final CoreDeadAnalysis analysis = new CoreDeadAnalysis(cnf);
		analysis.setAssumptions(new LiteralSet(variables.getVariable(feature1.getName())));
		final LiteralSet result = LongRunningWrapper.runMethod(analysis);

		final LiteralSet dependingVariables = variables.convertToVariables(Functional.mapToList(dependingFeatures, IFeature::getName), false);
		final LiteralSet negativeVariables = result.retainAll(dependingVariables);
		return negativeVariables.isEmpty();
	}

	/**
	 * Returns an explanation why the feature model is void. That is the same explanation for why its root feature is dead.
	 *
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getVoidFeatureModelExplanation() {
		return getVoidFeatureModelExplanation(featureModel);
	}

	/**
	 * Returns an explanation why the given feature model is void. That is the same explanation for why its root feature is dead.
	 *
	 * @param fm potentially void feature model; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getVoidFeatureModelExplanation(IFeatureModel fm) {
		return getDeadFeatureExplanation(fm, FeatureUtils.getRoot(fm));
	}

	/**
	 * Returns an explanation why the given feature is dead.
	 *
	 * @param feature potentially dead feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getDeadFeatureExplanation(IFeature feature) {
		return getDeadFeatureExplanation(featureModel, feature);
	}

	/**
	 * Adds an explanation why the given feature is dead.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially dead feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getDeadFeatureExplanation(IFeatureModel fm, IFeature feature) {
		if (!analysesCollection.deadFeatureExplanations.containsKey(feature)) {
			addDeadFeatureExplanation(fm, feature);
		}
		return analysesCollection.deadFeatureExplanations.get(feature);
	}

	/**
	 * Adds an explanation why the given feature is dead.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially dead feature; not null
	 */
	private void addDeadFeatureExplanation(IFeatureModel fm, IFeature feature) {
		final DeadFeatureExplanationCreator creator;
		if (fm == featureModel) {
			creator = analysesCollection.deadFeatureExplanationCreator;
		} else {
			creator = analysesCollection.explanationCreatorFactory.getDeadFeatureExplanationCreator();
			creator.setFeatureModel(fm);
		}
		creator.setSubject(feature);
		analysesCollection.deadFeatureExplanations.put(feature, creator.getExplanation());
	}

	/**
	 * Returns an explanation why the given feature is false-optional.
	 *
	 * @param feature potentially false-optional feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public FalseOptionalFeatureExplanation getFalseOptionalFeatureExplanation(IFeature feature) {
		return getFalseOptionalFeatureExplanation(featureModel, feature);
	}

	/**
	 * Returns an explanation why the given feature is false-optional.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially false-optional feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public FalseOptionalFeatureExplanation getFalseOptionalFeatureExplanation(IFeatureModel fm, IFeature feature) {
		if (!analysesCollection.falseOptionalFeatureExplanations.containsKey(feature)) {
			addFalseOptionalFeatureExplanation(fm, feature);
		}
		return analysesCollection.falseOptionalFeatureExplanations.get(feature);
	}

	/**
	 * Adds an explanation why the given feature is false-optional.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially false-optional feature; not null
	 */
	private void addFalseOptionalFeatureExplanation(IFeatureModel fm, IFeature feature) {
		final FalseOptionalFeatureExplanationCreator creator;
		if (fm == featureModel) {
			creator = analysesCollection.falseOptionalFeatureExplanationCreator;
		} else {
			creator = analysesCollection.explanationCreatorFactory.getFalseOptionalFeatureExplanationCreator();
			creator.setFeatureModel(fm);
		}
		creator.setSubject(feature);
		analysesCollection.falseOptionalFeatureExplanations.put(feature, creator.getExplanation());
	}

	/**
	 * Returns an explanation why the given constraint is redundant.
	 *
	 * @param constraint potentially redundant constraint; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public RedundantConstraintExplanation getRedundantConstraintExplanation(IConstraint constraint) {
		return getRedundantConstraintExplanation(featureModel, constraint);
	}

	/**
	 * Returns an explanation why the given constraint is redundant.
	 *
	 * @param constraint potentially redundant constraint; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public RedundantConstraintExplanation getRedundantConstraintExplanation(IFeatureModel fm, IConstraint constraint) {
		if (!analysesCollection.redundantConstraintExplanations.containsKey(constraint)) {
			addRedundantConstraintExplanation(fm, constraint);
		}
		return analysesCollection.redundantConstraintExplanations.get(constraint);
	}

	/**
	 * <p> Adds an explanation why the given constraint is redundant. </p>
	 *
	 * <p> Uses the given feature model, which may differ from the default feature model stored in this instance. This is for example the case when explaining
	 * implicit constraints in subtree models. </p>
	 *
	 * @param fm feature model containing the constraint; not null
	 * @param constraint potentially redundant constraint; not null
	 */
	private void addRedundantConstraintExplanation(IFeatureModel fm, IConstraint constraint) {
		final RedundantConstraintExplanationCreator creator;
		if (fm == featureModel) {
			creator = analysesCollection.redundantConstraintExplanationCreator;
		} else {
			creator = analysesCollection.explanationCreatorFactory.getRedundantConstraintExplanationCreator();
			creator.setFeatureModel(fm);
		}
		creator.setSubject(constraint);
		analysesCollection.redundantConstraintExplanations.put(constraint, creator.getExplanation());
	}

	public void setMultipleAnomalyExplanationTypes(FeatureStatus[] featureStatuses, ConstraintStatus[] constraintStatuses) {
		analysesCollection.setMultipleAnomaliesExplanation(null);
		analysesCollection.multipleAnomaliesExplanationCreator.setAnomalyTypes(featureStatuses, constraintStatuses);
	}

	/**
	 * Returns an combined explanation for all feature model anomalies.
	 *
	 * @return a {@link MultipleAnomaliesExplanation} for <code>featureModel</code>, or null if none can be found.
	 */
	public MultipleAnomaliesExplanation getMultipleAnomaliesExplanation() {
		return addMultipleAnomaliesExplanation();
	}

	/**
	 * Creates the {@link MultipleAnomaliesExplanation} for <code>featureModel</code> at the user's request.
	 *
	 * @return new {@link MultipleAnomaliesExplanation}
	 */
	public MultipleAnomaliesExplanation addMultipleAnomaliesExplanation() {
		MultipleAnomaliesExplanation explanation = analysesCollection.getMultipleAnomaliesExplanation();
		if (explanation == null) {
			final MultipleAnomaliesExplanationCreator creator = analysesCollection.multipleAnomaliesExplanationCreator;
			explanation = creator.getExplanation();
			analysesCollection.setMultipleAnomaliesExplanation(explanation);
		}
		return explanation;
	}

	public AnalysesCollection getAnalysesCollection() {
		return analysesCollection;
	}

	@Override
	public void propertyChange(FeatureIDEEvent event) {}

}
