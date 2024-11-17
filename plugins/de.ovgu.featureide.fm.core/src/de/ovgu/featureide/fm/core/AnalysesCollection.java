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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import de.ovgu.featureide.fm.core.analysis.ConstraintProperties;
import de.ovgu.featureide.fm.core.analysis.FeatureModelProperties;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.Nodes;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.AClauseAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.AbstractAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.AnalysisResult;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.AtomicSetAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CauseAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CauseAnalysis.Anomalies;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.ContradictionAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.HasSolutionAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.IndependentContradictionAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.IndependentRedundancyAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.IndeterminedAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.RemoveRedundancyAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.ACreator;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.EmptyCNFCreator;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureTreeCNFCreator;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.impl.FeatureModelProperty;
import de.ovgu.featureide.fm.core.explanations.Explanation;
import de.ovgu.featureide.fm.core.explanations.ExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.FeatureModelExplanationCreatorFactory;
import de.ovgu.featureide.fm.core.explanations.fm.MultipleAnomaliesExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.MultipleAnomaliesExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanationCreator;
import de.ovgu.featureide.fm.core.filter.HiddenFeatureFilter;
import de.ovgu.featureide.fm.core.filter.OptionalFeatureFilter;
import de.ovgu.featureide.fm.core.functional.Functional;
import de.ovgu.featureide.fm.core.job.IRunner;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor.MethodCancelException;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

/**
 * A collection of methods for working with {@link IFeatureModel} will replace the corresponding methods in {@link IFeatureModel}
 *
 * @author Sebastian Krieter
 */
public class AnalysesCollection {

	/**
	 * Remembers results for analyzed features.
	 */
	final Map<IFeature, FeatureProperties> featurePropertiesMap = new HashMap<>();

	/**
	 * Remembers results for analyzed constraints.
	 */
	final Map<IConstraint, ConstraintProperties> constraintPropertiesMap = new HashMap<>();
	/**
	 * Remembers results for analyzed constraints and features.
	 */
	final Map<IFeatureModelElement, Object> elementPropertiesMap = new HashMap<>();

	/**
	 * Remembers results for analyzed feature model.
	 */
	FeatureModelProperties featureModelProperties;

	/**
	 * Remembers explanations for dead features.
	 */
	final Map<IFeature, DeadFeatureExplanation> deadFeatureExplanations = new HashMap<>();
	/**
	 * Remembers explanations for false-optional features.
	 */
	final Map<IFeature, FalseOptionalFeatureExplanation> falseOptionalFeatureExplanations = new HashMap<>();
	/**
	 * Remembers explanations for redundant constraints.
	 */
	final Map<IConstraint, RedundantConstraintExplanation> redundantConstraintExplanations = new HashMap<>();
	/**
	 * Remembers the collected explanation for all anomalies.
	 */
	private MultipleAnomaliesExplanation multipleAnomaliesExplanation;

	/**
	 * Used for creating explanation creators.
	 */
	final FeatureModelExplanationCreatorFactory explanationCreatorFactory = FeatureModelExplanationCreatorFactory.getDefault();
	/**
	 * Creates explanations for dead features. Stored for performance so the underlying CNF is not recreated for every explanation.
	 */
	final DeadFeatureExplanationCreator deadFeatureExplanationCreator = explanationCreatorFactory.getDeadFeatureExplanationCreator();
	/**
	 * Creates explanations for false-optional features. Stored for performance so the underlying CNF is not recreated for every explanation.
	 */
	final FalseOptionalFeatureExplanationCreator falseOptionalFeatureExplanationCreator = explanationCreatorFactory.getFalseOptionalFeatureExplanationCreator();
	/**
	 * Creates explanations for redundant constraints. Stored for performance so the underlying CNF is not recreated for every explanation.
	 */
	final RedundantConstraintExplanationCreator redundantConstraintExplanationCreator = explanationCreatorFactory.getRedundantConstraintExplanationCreator();
	final MultipleAnomaliesExplanationCreator multipleAnomaliesExplanationCreator = explanationCreatorFactory.getMultipleAnomaliesExplanationCreator();

	static class StringToFeature implements Function<String, IFeature> {

		private final IFeatureModel featureModel;

		public StringToFeature(IFeatureModel featureModel) {
			this.featureModel = featureModel;
		}

		@Override
		public IFeature apply(String name) {
			return featureModel.getFeature(name);
		}
	};

	static class AnalysisWrapper<R, A extends AbstractAnalysis<R>> {

		protected FeatureModelFormula formula;

		private Object syncObject = new Object();
		private IMonitor<R> monitor = new NullMonitor<>();
		private boolean enabled = true;

		private AnalysisResult<R> analysisResult;

		private final Class<A> analysis;

		public AnalysisWrapper(Class<A> analysis) {
			this.analysis = analysis;
		}

		public Optional<R> getResult() {
			return getResult(null);
		}

		public Optional<R> getResult(IMonitor<R> monitor) {
			return getResult(monitor, -1);
		}

		public Optional<R> getResult(int timeout) {
			return getResult(null, timeout);
		}

		public Optional<R> getResult(IMonitor<R> monitor, int timeout) {
			if (!enabled) {
				return Optional.empty();
			}

			AnalysisResult<R> curAnalysisResult;
			Object curSyncObject;
			synchronized (this) {
				curAnalysisResult = analysisResult;
				curSyncObject = syncObject;
			}

			synchronized (curSyncObject) {
				this.monitor = monitor != null ? monitor : new NullMonitor<>();
				R result = null;
				if (curAnalysisResult == null) {
					final AbstractAnalysis<R> analysisInstance = createNewAnalysis();
					try {
						result = timeout > 0 //
							? compute(analysisInstance, timeout) //
							: compute(analysisInstance);
						curAnalysisResult = result == null ? null : analysisInstance.getResult();
					} catch (final MethodCancelException e) {

					} catch (final Exception e) {
						Logger.logError(e);
					}
					synchronized (this) {
						if (curSyncObject == syncObject) {
							analysisResult = curAnalysisResult;
						}
					}
				} else {
					result = curAnalysisResult.getResult();
					this.monitor.done();
				}
				return Optional.ofNullable(result);
			}
		}

		private R compute(final AbstractAnalysis<R> analysisInstance) {
			return LongRunningWrapper.runMethod(analysisInstance, monitor);
		}

		private R compute(final AbstractAnalysis<R> analysisInstance, int timeout) throws InterruptedException {
			final IRunner<R> thread = LongRunningWrapper.getThread(analysisInstance, monitor);
			thread.setStoppable(true);
			thread.setTimeout(timeout);
			thread.schedule();
			thread.join();
			return thread.getResults();
		}

		private A createNewAnalysis() {
			try {
				final CNF cnf = getCNF();
				final A newInstance = analysis.getConstructor(CNF.class).newInstance(cnf);
				configureAnalysis(cnf, newInstance);
				return newInstance;
			} catch (
					InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
					| SecurityException e) {
				Logger.logError(e);
				throw new RuntimeException(e);
			}
		}

		protected CNF getCNF() {
			return formula.getCNF();
		}

		protected void configureAnalysis(CNF cnf, A analysis) {}

		public void setFormula(FeatureModelFormula formula) {
			this.formula = formula;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public void reset() {
			synchronized (this) {
				analysisResult = null;
				monitor.cancel();
				monitor = new NullMonitor<>();
				syncObject = new Object();
			}
		}

	}

	static final class CauseAnalysisWrapper extends AConstraintAnalysisWrapper<List<Anomalies>, CauseAnalysis> {

		private final AnalysisWrapper<LiteralSet, CoreDeadAnalysis> coreDeadAnalysis;
		private final FalseOptionalAnalysisWrapper foAnalysis;
		protected boolean[] relevantConstraint;

		private CauseAnalysisWrapper(Class<CauseAnalysis> analysis, AnalysisWrapper<LiteralSet, CoreDeadAnalysis> coreDeadAnalysis,
				FalseOptionalAnalysisWrapper foAnalysis) {
			super(analysis, new FeatureTreeCNFCreator());
			this.coreDeadAnalysis = coreDeadAnalysis;
			this.foAnalysis = foAnalysis;
		}

		@Override
		public void setFormula(FeatureModelFormula formula) {
			super.setFormula(formula);
			setClauseGroups(formula.getFeatureModel().getConstraints());
		}

		@Override
		protected void configureAnalysis(CNF cnf, CauseAnalysis analysis) {
			final Anomalies initialAnomalies = new Anomalies();
			final Optional<LiteralSet> coreDeadResult = coreDeadAnalysis.getResult();
			initialAnomalies.setDeadVariables(coreDeadResult.orElse(new LiteralSet()));

			foAnalysis.setOptionalFeatures(Functional.filterToList(formula.getFeatureModel().getFeatures(), new OptionalFeatureFilter()));
			final Optional<List<LiteralSet>> foResult = foAnalysis.getResult();
			initialAnomalies.setRedundantClauses(Functional.removeNull(foResult.orElse(Collections.emptyList())));

			analysis.setAnomalies(initialAnomalies);
			analysis.setClauseList(constraintClauses);
			analysis.setClauseGroupSize(clauseGroupSize);
			analysis.setRelevantConstraint(relevantConstraint);
		}

		public void setRelevantConstraint(boolean[] relevantConstraint) {
			this.relevantConstraint = relevantConstraint;
		}

	}

	static final class IndeterminesAnalzsisWrapper extends AnalysisWrapper<LiteralSet, IndeterminedAnalysis> {

		private IndeterminesAnalzsisWrapper(Class<IndeterminedAnalysis> analysis) {
			super(analysis);
		}

		@Override
		protected void configureAnalysis(CNF cnf, IndeterminedAnalysis analysis) {
			final LiteralSet convertToVariables = cnf.getVariables()
					.convertToVariables(Functional.mapToList(formula.getFeatureModel().getFeatures(), new HiddenFeatureFilter(), IFeature::getName));
			analysis.setVariables(convertToVariables);
		}
	}

	static class ConstraintAnalysisWrapper<A extends AClauseAnalysis<List<LiteralSet>>> extends AConstraintAnalysisWrapper<List<LiteralSet>, A> {

		private List<IConstraint> constraints;

		private ConstraintAnalysisWrapper(Class<A> analysis, ACreator<CNF> cnfCreator) {
			super(analysis, cnfCreator);
		}

		public void setConstraints(List<IConstraint> constraints) {
			if (!constraints.equals(this.constraints)) {
				this.constraints = constraints;
				setClauseGroups(constraints);
				reset();
			}
		}

		@Override
		protected void configureAnalysis(CNF cnf, A analysis) {
			analysis.setClauseList(constraintClauses);
			analysis.setClauseGroupSize(clauseGroupSize);
		}

	}

	static class AConstraintAnalysisWrapper<R, A extends AbstractAnalysis<R>> extends AnalysisWrapper<R, A> {

		protected ArrayList<LiteralSet> constraintClauses;
		protected int[] clauseGroupSize;

		private final ACreator<CNF> cnfCreator;

		private AConstraintAnalysisWrapper(Class<A> analysis, ACreator<CNF> cnfCreator) {
			super(analysis);
			this.cnfCreator = cnfCreator;
		}

		public int[] getClauseGroupSize() {
			return clauseGroupSize;
		}

		@Override
		protected CNF getCNF() {
			return formula.getElement(cnfCreator);
		}

		protected final void setClauseGroups(List<IConstraint> constraints) {
			constraintClauses = new ArrayList<>();
			clauseGroupSize = new int[constraints.size()];
			final Variables variables = formula.getElement(new EmptyCNFCreator()).getVariables();
			int i = 0;
			for (final IConstraint constraint : constraints) {
				final List<LiteralSet> clauses = Nodes.convert(variables, constraint.getNode());
				constraintClauses.addAll(clauses);
				clauseGroupSize[i++] = clauses.size();
			}
		}

	}

	static final class FalseOptionalAnalysisWrapper extends AnalysisWrapper<List<LiteralSet>, IndependentRedundancyAnalysis> {

		private List<IFeature> optionalFeatures;

		private FalseOptionalAnalysisWrapper() {
			super(IndependentRedundancyAnalysis.class);
		}

		@Override
		protected void configureAnalysis(CNF cnf, IndependentRedundancyAnalysis analysis) {
			final List<LiteralSet> literalSetList = new ArrayList<>();
			final Variables variables = cnf.getVariables();
			for (final IFeature iFeature : optionalFeatures) {
				literalSetList.add(new LiteralSet(variables.getVariable(FeatureUtils.getParent(iFeature).getName(), false),
						variables.getVariable(iFeature.getName(), true)));
			}
			analysis.setClauseList(literalSetList);
		}

		public void setOptionalFeatures(List<IFeature> optionalFeatures) {
			if (!optionalFeatures.equals(this.optionalFeatures)) {
				this.optionalFeatures = optionalFeatures;
				reset();
			}
		}
	}

	private FeatureModelFormula formula;
	final AnalysisWrapper<Boolean, HasSolutionAnalysis> validAnalysis = new AnalysisWrapper<>(HasSolutionAnalysis.class);
	final AnalysisWrapper<List<LiteralSet>, AtomicSetAnalysis> atomicSetAnalysis = new AnalysisWrapper<>(AtomicSetAnalysis.class);
	final AnalysisWrapper<LiteralSet, CoreDeadAnalysis> coreDeadAnalysis = new AnalysisWrapper<>(CoreDeadAnalysis.class);
	final FalseOptionalAnalysisWrapper foAnalysis = new FalseOptionalAnalysisWrapper();
	final AnalysisWrapper<LiteralSet, IndeterminedAnalysis> determinedAnalysis = new IndeterminesAnalzsisWrapper(IndeterminedAnalysis.class);
	final ConstraintAnalysisWrapper<RemoveRedundancyAnalysis> constraintRedundancyAnalysis =
		new ConstraintAnalysisWrapper<>(RemoveRedundancyAnalysis.class, new FeatureTreeCNFCreator());
	final ConstraintAnalysisWrapper<IndependentRedundancyAnalysis> constraintTautologyAnalysis =
		new ConstraintAnalysisWrapper<>(IndependentRedundancyAnalysis.class, new EmptyCNFCreator());
	final ConstraintAnalysisWrapper<IndependentContradictionAnalysis> constraintContradictionAnalysis =
		new ConstraintAnalysisWrapper<>(IndependentContradictionAnalysis.class, new EmptyCNFCreator());
	final ConstraintAnalysisWrapper<ContradictionAnalysis> constraintVoidAnalysis =
		new ConstraintAnalysisWrapper<>(ContradictionAnalysis.class, new FeatureTreeCNFCreator());
	final CauseAnalysisWrapper constraintAnomaliesAnalysis = new CauseAnalysisWrapper(CauseAnalysis.class, coreDeadAnalysis, foAnalysis);

	private final List<AnalysisWrapper<?, ? extends AbstractAnalysis<? extends Object>>> list =
		Arrays.asList(validAnalysis, atomicSetAnalysis, coreDeadAnalysis, foAnalysis, determinedAnalysis, constraintContradictionAnalysis,
				constraintVoidAnalysis, constraintTautologyAnalysis, constraintRedundancyAnalysis, constraintAnomaliesAnalysis);

	<D extends IFeatureModelElement> Explanation<?> createExplanation(ExplanationCreator<D, ?> creator, D element, FeatureModelFormula formula) {
		creator.setSubject(element);
		return creator.getExplanation();
	}

	void reset(FeatureModelFormula formula) {
		this.formula = formula;
		for (final AnalysisWrapper<?, ?> analysisWrapper : list) {
			analysisWrapper.reset();
			analysisWrapper.setFormula(formula);
		}
		deadFeatureExplanations.clear();
		falseOptionalFeatureExplanations.clear();
		redundantConstraintExplanations.clear();
		multipleAnomaliesExplanation = null;

		featurePropertiesMap.clear();
		constraintPropertiesMap.clear();

		for (final IFeature feature : formula.getFeatureModel().getFeatures()) {
			final FeatureProperties featureProperties = new FeatureProperties(feature);
			featurePropertiesMap.put(feature, featureProperties);
			elementPropertiesMap.put(feature, featureProperties);
		}
		for (final IConstraint constraint : formula.getFeatureModel().getConstraints()) {
			final ConstraintProperties constraintProperties = new ConstraintProperties(constraint);
			constraintPropertiesMap.put(constraint, constraintProperties);
			elementPropertiesMap.put(constraint, constraintProperties);
		}
		featureModelProperties = new FeatureModelProperties(featurePropertiesMap.values(), constraintPropertiesMap.values());

	}

	void init(FeatureModelFormula formula) {
		this.formula = formula;
		for (final AnalysisWrapper<?, ?> analysisWrapper : list) {
			analysisWrapper.setFormula(formula);
		}

		deadFeatureExplanationCreator.setFeatureModel(formula.getFeatureModel());
		falseOptionalFeatureExplanationCreator.setFeatureModel(formula.getFeatureModel());
		redundantConstraintExplanationCreator.setFeatureModel(formula.getFeatureModel());
		multipleAnomaliesExplanationCreator.setFeatureModel(formula.getFeatureModel());
	}

	public void inheritSettings(AnalysesCollection otherCollection) {
		final Iterator<AnalysisWrapper<?, ? extends AbstractAnalysis<? extends Object>>> thisAnalysesIterator = list.iterator();
		final Iterator<AnalysisWrapper<?, ? extends AbstractAnalysis<? extends Object>>> otherAnalysesIterator = otherCollection.list.iterator();
		while (thisAnalysesIterator.hasNext()) {
			thisAnalysesIterator.next().setEnabled(otherAnalysesIterator.next().isEnabled());
		}
	}

	/**
	 * Defines whether features should be included into calculations. If features are not analyzed, then constraints a also NOT analyzed.
	 */
	public boolean isCalculateFeatures() {
		return FeatureModelProperty.isCalculateFeatures(formula.getFeatureModel());
	}

	/**
	 * Defines whether constraints should be included into calculations.
	 */
	public boolean isCalculateConstraints() {
		return FeatureModelProperty.isCalculateConstraints(formula.getFeatureModel());
	}

	public boolean isCalculateRedundantConstraints() {
		return constraintRedundancyAnalysis.isEnabled();
	}

	/**
	 * Defines whether redundant constraints should be calculated.
	 */
	public void setCalculateRedundantConstraints(boolean calculateRedundantConstraints) {
		constraintRedundancyAnalysis.setEnabled(calculateRedundantConstraints);
	}

	public boolean isCalculateTautologyConstraints() {
		return constraintTautologyAnalysis.isEnabled();
	}

	/**
	 * Defines whether constraints that are tautologies should be calculated.
	 */
	public void setCalculateTautologyConstraints(boolean calculateTautologyConstraints) {
		constraintTautologyAnalysis.setEnabled(calculateTautologyConstraints);
		if (calculateTautologyConstraints) {
			constraintRedundancyAnalysis.setEnabled(true);
		}
	}

	public boolean isCalculateFOConstraints() {
		return constraintAnomaliesAnalysis.isEnabled();
	}

	public void setCalculateFOConstraints(boolean calculateFOConstraints) {
		constraintAnomaliesAnalysis.setEnabled(calculateFOConstraints);
	}

	public boolean isCalculateDeadConstraints() {
		return constraintAnomaliesAnalysis.isEnabled();
	}

	public void setCalculateDeadConstraints(boolean calculateDeadConstraints) {
		constraintAnomaliesAnalysis.setEnabled(calculateDeadConstraints);
	}

	public boolean isRunCalculationAutomatically() {
		return FeatureModelProperty.isRunCalculationAutomatically(formula.getFeatureModel());
	}

	public FeatureModelProperties getFeatureModelProperties() {
		return featureModelProperties;
	}

	public Map<IFeatureModelElement, Object> getFeatureModelElementsProperties() {
		return elementPropertiesMap;
	}

	public FeatureProperties getFeatureProperty(IFeature feature) {
		final FeatureProperties featureProperties = featurePropertiesMap.get(feature);
		return featureProperties != null ? featureProperties : new FeatureProperties(feature);
	}

	public ConstraintProperties getConstraintProperty(IConstraint constraint) {
		final ConstraintProperties constraintProperties = constraintPropertiesMap.get(constraint);
		return constraintProperties != null ? constraintProperties : new ConstraintProperties(constraint);
	}

	MultipleAnomaliesExplanation getMultipleAnomaliesExplanation() {
		return multipleAnomaliesExplanation;
	}

	void setMultipleAnomaliesExplanation(MultipleAnomaliesExplanation multipleAnomaliesExplanation) {
		this.multipleAnomaliesExplanation = multipleAnomaliesExplanation;
	}
}
