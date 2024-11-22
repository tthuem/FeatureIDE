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
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.fm.core.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CountSolutionsAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.NoAbstractCNFCreator;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.NoAbstractNoHiddenCNFCreator;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.NoHiddenCNFCreator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.AllConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.OneWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.OneWiseConfigurationGenerator.CoverStrategy;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.AdvancedSatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver.SelectionStrategy;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISimpleSatSolver.SatResult;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.RuntimeContradictionException;
import de.ovgu.featureide.fm.core.job.LongRunningMethod;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * Updates a configuration.
 *
 * @author Sebastian Krieter
 */
public class ConfigurationPropagator implements IConfigurationPropagator {

	public class IsValidMethod implements LongRunningMethod<Boolean> {

		private final boolean deselectUndefinedFeatures;
		private final boolean includeHiddenFeatures;

		public IsValidMethod(boolean includeUndefinedFeatures, boolean includeHiddenFeatures) {
			deselectUndefinedFeatures = includeUndefinedFeatures;
			this.includeHiddenFeatures = includeHiddenFeatures;
		}

		@Override
		public Boolean execute(IMonitor<Boolean> monitor) {
			if (formula == null) {
				return false;
			}
			final AdvancedSatSolver solver = getSolverForCurrentConfiguration(deselectUndefinedFeatures, includeHiddenFeatures);
			if (solver == null) {
				return false;
			}

			final SatResult satResult = solver.hasSolution();
			switch (satResult) {
			case FALSE:
			case TIMEOUT:
				return false;
			case TRUE:
				return true;
			default:
				throw new AssertionError(satResult);
			}
		}
	}

	public class Resolve implements LongRunningMethod<Collection<SelectableFeature>> {

		@Override
		public Collection<SelectableFeature> execute(IMonitor<Collection<SelectableFeature>> workMonitor) throws Exception {
			if (formula == null) {
				return null;
			}

			// Reset all automatic values
			configuration.resetAutomaticValues();

			final AdvancedSatSolver solver = getSolverForCurrentConfiguration(false, true);
			if (solver == null) {
				return null;
			}

			final SatResult satResult = solver.hasSolution();
			switch (satResult) {
			case FALSE:
			case TIMEOUT:
				final int[] contradictoryAssignment = solver.getContradictoryAssignment();
				for (final int i : contradictoryAssignment) {
					configuration.setManual(solver.getSatInstance().getVariables().getName(i), Selection.UNDEFINED);
				}
			case TRUE:
				return null;
			default:
				throw new AssertionError(satResult);
			}
		}
	}

	public class CompleteRandomlyMethod implements LongRunningMethod<Boolean> {

		private final SelectionStrategy selectionStrategy;

		public CompleteRandomlyMethod(SelectionStrategy selectionStrategy) {
			this.selectionStrategy = selectionStrategy;
		}

		@Override
		public Boolean execute(IMonitor<Boolean> workMonitor) throws Exception {
			if (formula == null) {
				return false;
			}

			// Reset all automatic values
			configuration.resetAutomaticValues();

			final AdvancedSatSolver solver = getSolverForCurrentConfiguration(false, true);
			if (solver == null) {
				return false;
			}

			solver.setSelectionStrategy(selectionStrategy);
			final int[] solution = solver.findSolution();
			if (solution != null) {
				for (final int i : solution) {
					configuration.setManual(solver.getSatInstance().getVariables().getName(i), i > 0 ? Selection.SELECTED : Selection.UNSELECTED);
				}
				return true;
			} else {
				return false;
			}
		}
	}

	public class CountSolutionsMethod implements LongRunningMethod<Long> {

		private final int timeout;

		public CountSolutionsMethod(int timeout) {
			this.timeout = timeout;
		}

		@Override
		public Long execute(IMonitor<Long> monitor) throws Exception {
			if (formula == null) {
				return 0L;
			}
			final AdvancedSatSolver solver = getSolverForCurrentConfiguration(false, false);
			if (solver == null) {
				return 0L;
			}
			solver.setTimeout(timeout);
			return new CountSolutionsAnalysis(solver).analyze(monitor);
		}

	}

	public class FindOpenClauses implements LongRunningMethod<Collection<SelectableFeature>> {

		@Override
		public Collection<SelectableFeature> execute(IMonitor<Collection<SelectableFeature>> workMonitor) {
			if (formula == null) {
				return Collections.emptyList();
			}
			final CNF clausesWithoutHidden = formula.getElement(new NoHiddenCNFCreator());
			final boolean[] results = new boolean[clausesWithoutHidden.getVariables().maxVariableID() + 1];
			final List<LiteralSet> openClauses = new ArrayList<>();

			final ArrayList<SelectableFeature> previouslyRecommendedFeatures = new ArrayList<>();
			for (final SelectableFeature selectableFeature : configuration.getFeatures()) {
				if (selectableFeature.getRecommended() != Selection.UNDEFINED) {
					previouslyRecommendedFeatures.add(selectableFeature);
					selectableFeature.setRecommended(Selection.UNDEFINED);
					selectableFeature.clearOpenClauses();
				}
			}
			workMonitor.invoke(previouslyRecommendedFeatures);

			final List<LiteralSet> clauses = clausesWithoutHidden.getClauses();
			workMonitor.setRemainingWork(clauses.size());
			final Collection<SelectableFeature> result = new ArrayList<>();

			loop: for (final LiteralSet clause : clauses) {
				workMonitor.worked();
				final int[] orLiterals = clause.getLiterals();
				for (int j = 0; j < orLiterals.length; j++) {
					final int literal = orLiterals[j];
					final SelectableFeature feature = configuration.getSelectableFeature(clausesWithoutHidden.getVariables().getName(literal));
					if (feature != null) {
						final Selection selection = feature.getSelection();
						switch (selection) {
						case SELECTED:
							if (literal > 0) {
								continue loop;
							}
							break;
						case UNDEFINED:
						case UNSELECTED:
							if (literal < 0) {
								continue loop;
							}
							break;
						default:
							throw new AssertionError(selection);
						}
					}
				}

				final ArrayList<SelectableFeature> updateFeatures = new ArrayList<>();
				boolean newLiterals = false;
				for (int j = 0; j < orLiterals.length; j++) {
					final int literal = orLiterals[j];
					if (!results[Math.abs(literal)]) {
						results[Math.abs(literal)] = true;
						newLiterals = true;

						final SelectableFeature feature = configuration.getSelectableFeature(clausesWithoutHidden.getVariables().getName(literal));
						if (feature != null) {
							final Selection selection = feature.getSelection();
							updateFeatures.add(feature);
							switch (selection) {
							case SELECTED:
								feature.setRecommended(Selection.UNSELECTED);
								feature.addOpenClause(openClauses.size(), clause);
								feature.setVariables(clausesWithoutHidden.getVariables());
								break;
							case UNDEFINED:
							case UNSELECTED:
								feature.setRecommended(Selection.SELECTED);
								feature.addOpenClause(openClauses.size(), clause);
								feature.setVariables(clausesWithoutHidden.getVariables());
								break;
							default:
								throw new AssertionError(selection);
							}
						}
					}
				}

				if (newLiterals) {
					workMonitor.invoke(updateFeatures);
					result.addAll(updateFeatures);
					openClauses.add(clause);
				}
			}
			return result;
		}
	}

	public class GetSolutionsMethod implements LongRunningMethod<List<List<String>>> {

		private final int max;

		public GetSolutionsMethod(int max) {
			this.max = max;
		}

		@Override
		public List<List<String>> execute(IMonitor<List<List<String>>> monitor) throws Exception {
			if (formula == null) {
				return null;
			}
			final ArrayList<List<String>> resultList = new ArrayList<>();

			final AdvancedSatSolver solver = getSolverForCurrentConfiguration(false, false);
			if (solver == null) {
				return resultList;
			}
			final List<LiteralSet> result = new AllConfigurationGenerator(solver, max).analyze(monitor.subTask(1));
			for (final LiteralSet is : result) {
				resultList.add(solver.getSatInstance().getVariables().convertToString(is));
			}

			return resultList;
		}
	}

	/**
	 * Creates solutions to cover the given features.
	 */
	public class CoverFeatures implements LongRunningMethod<List<List<String>>> {

		private final Collection<String> features;
		private final boolean selection;

		public CoverFeatures(Collection<String> features, boolean selection) {
			this.features = features;
			this.selection = selection;
		}

		@Override
		public List<List<String>> execute(IMonitor<List<List<String>>> workMonitor) throws Exception {
			if (formula == null) {
				return null;
			}
			final CNF clausesWithoutHidden;
			if (includeAbstractFeatures) {
				clausesWithoutHidden = formula.getElement(new NoHiddenCNFCreator());
			} else {
				clausesWithoutHidden = formula.getElement(new NoAbstractNoHiddenCNFCreator());
			}
			final OneWiseConfigurationGenerator oneWiseConfigurationGenerator =
				new OneWiseConfigurationGenerator(getSolverForCurrentConfiguration(false, false));
			oneWiseConfigurationGenerator.setCoverMode(selection ? CoverStrategy.POSITIVE : CoverStrategy.NEGATIVE);
			final int[] featureArray = new int[features.size()];
			int index = 0;
			for (final String feature : features) {
				featureArray[index++] = clausesWithoutHidden.getVariables().getVariable(feature);
			}
			oneWiseConfigurationGenerator.setFeatures(featureArray);

			final List<List<String>> solutionList = new ArrayList<>();
			final List<LiteralSet> solutions = LongRunningWrapper.runMethod(oneWiseConfigurationGenerator, workMonitor.subTask(1));
			if (solutions == null) {
				return solutionList;
			}
			for (final LiteralSet is : solutions) {
				solutionList.add(clausesWithoutHidden.getVariables().convertToString(is, true, false));
			}

			return solutionList;
		}

	}

	public class UpdateMethod implements LongRunningMethod<Collection<SelectableFeature>> {

		protected final boolean redundantManual;
		protected final List<SelectableFeature> featureOrder;

		public UpdateMethod(boolean redundantManual) {
			this(redundantManual, null);
		}

		public UpdateMethod(boolean redundantManual, List<SelectableFeature> featureOrder) {
			this.redundantManual = redundantManual;
			this.featureOrder = featureOrder != null ? featureOrder : Collections.<SelectableFeature> emptyList();
		}

		@Override
		public Collection<SelectableFeature> execute(IMonitor<Collection<SelectableFeature>> workMonitor) {
			if (formula == null) {
				return Collections.emptyList();
			}
			configuration.resetAutomaticValues();

			final CNF rootNode = formula.getCNF();
			final ArrayList<Integer> manualLiterals = new ArrayList<>();
			for (final SelectableFeature feature : featureOrder) {
				if ((feature.getManual() != Selection.UNDEFINED) && (includeAbstractFeatures || feature.getFeature().getStructure().isConcrete())) {
					manualLiterals.add(rootNode.getVariables().getVariable(feature.getFeature().getName(), feature.getManual() == Selection.SELECTED));
				}
			}
			final HashSet<Integer> manualLiteralSet = new HashSet<>(manualLiterals);
			for (final SelectableFeature feature : configuration.getFeatures()) {
				if ((feature.getManual() != Selection.UNDEFINED) && (includeAbstractFeatures || feature.getFeature().getStructure().isConcrete())) {
					final Integer l = rootNode.getVariables().getVariable(feature.getFeature().getName(), feature.getManual() == Selection.SELECTED);
					if (manualLiteralSet.add(l)) {
						manualLiterals.add(l);
					}
				}
			}

			workMonitor.setRemainingWork(manualLiterals.size() + 1);
			Collections.reverse(manualLiterals);

			final CoreDeadAnalysis analysis = new CoreDeadAnalysis(rootNode);
			final int[] intLiterals = new int[manualLiterals.size()];
			for (int i = 0; i < intLiterals.length; i++) {
				intLiterals[i] = manualLiterals.get(i);
			}
			analysis.setAssumptions(new LiteralSet(intLiterals));
			final LiteralSet impliedFeatures = LongRunningWrapper.runMethod(analysis, workMonitor.subTask(1));

			// if there is a contradiction within the configuration
			if (impliedFeatures == null) {
				return Collections.emptyList();
			}

			final Collection<SelectableFeature> result = new HashSet<>(rootNode.getVariables().size());

			for (final int i : impliedFeatures.getLiterals()) {
				final SelectableFeature feature = configuration.getSelectableFeature(rootNode.getVariables().getName(i));
				if (feature != null) {
					configuration.setAutomatic(feature, i > 0 ? Selection.SELECTED : Selection.UNSELECTED);
					result.add(feature);
					manualLiteralSet.add(feature.getManual() == Selection.SELECTED ? i : -i);
				}
			}
			workMonitor.invoke(result);

			// only for update of configuration editor
			final ArrayList<SelectableFeature> updateFeatures = new ArrayList<>();
			for (final SelectableFeature feature : configuration.getFeatures()) {
				if (!manualLiteralSet
						.contains(rootNode.getVariables().getVariable(feature.getFeature().getName(), feature.getManual() == Selection.SELECTED))) {
					updateFeatures.add(feature);
					result.add(feature);
				}
			}
			workMonitor.invoke(updateFeatures);

			if (redundantManual) {
				final AdvancedSatSolver solver = getSolver(true);
				if (solver == null) {
					return result;
				}
				for (final int feature : intLiterals) {
					solver.assignmentPush(feature);
				}

				int literalCount = intLiterals.length;
				for (int i = 0; i < solver.getAssignmentSize(); i++) {
					final int oLiteral = intLiterals[i];
					final SelectableFeature feature = configuration.getSelectableFeature(rootNode.getVariables().getName(oLiteral));
					if (feature != null) {
						solver.assignmentSet(i, -oLiteral);
						final SatResult satResult = solver.hasSolution();
						switch (satResult) {
						case FALSE:
							configuration.setAutomatic(feature, oLiteral > 0 ? Selection.SELECTED : Selection.UNSELECTED);
							result.add(feature);
							workMonitor.invoke(Arrays.asList(feature));
							intLiterals[i] = intLiterals[--literalCount];
							solver.assignmentDelete(i--);
							break;
						case TIMEOUT:
						case TRUE:
							solver.assignmentSet(i, oLiteral);
							result.add(feature);
							workMonitor.invoke(Arrays.asList(feature));
							break;
						default:
							throw new AssertionError(satResult);
						}
					}
					workMonitor.worked();
				}
			}
			return result;
		}

	}

	public class ResetAutomaticMethod implements LongRunningMethod<Collection<SelectableFeature>> {

		@Override
		public Collection<SelectableFeature> execute(IMonitor<Collection<SelectableFeature>> workMonitor) {
			if (formula == null) {
				return Collections.emptyList();
			}
			workMonitor.setRemainingWork(2);

			final List<SelectableFeature> result = configuration.getAutomaticFeatures();
			configuration.resetAutomaticValues();

			final CNF rootNode = formula.getCNF();

			workMonitor.checkCancel();
			final LiteralSet impliedFeatures = LongRunningWrapper.runMethod(new CoreDeadAnalysis(rootNode), workMonitor.subTask(1));
			if (impliedFeatures == null) {
				return Collections.emptyList();
			}

			for (final int i : impliedFeatures.getLiterals()) {
				final SelectableFeature feature = configuration.getSelectableFeature(rootNode.getVariables().getName(i));
				if (feature != null) {
					final Selection automatic = i > 0 ? Selection.SELECTED : Selection.UNSELECTED;
					final Selection manual = feature.getManual();
					if ((manual == Selection.UNDEFINED) || (manual == automatic)) {
						feature.setAutomatic(automatic);
					}
				}
			}
			workMonitor.step(result);
			return result;
		}

	}

	// TODO fix monitor values
	protected final FeatureModelFormula formula;
	protected final Configuration configuration;

	protected boolean includeAbstractFeatures = true;

	/**
	 * This method creates a clone of the given {@link ConfigurationPropagator}
	 *
	 * @param configuration The new configuration object
	 */
	protected ConfigurationPropagator(ConfigurationPropagator oldPropagator, Configuration configuration) {
		formula = oldPropagator.formula;
		this.configuration = configuration;
		includeAbstractFeatures = oldPropagator.includeAbstractFeatures;
	}

	public ConfigurationPropagator(FeatureModelFormula formula, Configuration configuration) {
		this.formula = formula;
		this.configuration = configuration;
	}

	@Override
	public boolean isIncludeAbstractFeatures() {
		return includeAbstractFeatures;
	}

	@Override
	public void setIncludeAbstractFeatures(boolean includeAbstractFeatures) {
		this.includeAbstractFeatures = includeAbstractFeatures;
	}

	protected AdvancedSatSolver getSolverForCurrentConfiguration(boolean deselectUndefinedFeatures, boolean includeHiddenFeatures) {
		final AdvancedSatSolver solver = getSolver(includeHiddenFeatures);
		if (solver == null) {
			return null;
		}
		for (final SelectableFeature feature : configuration.getFeatures()) {
			if ((deselectUndefinedFeatures || (feature.getSelection() != Selection.UNDEFINED))
				&& (includeAbstractFeatures || feature.getFeature().getStructure().isConcrete())
				&& (includeHiddenFeatures || !feature.getFeature().getStructure().hasHiddenParent())) {
				solver.assignmentPush(
						solver.getSatInstance().getVariables().getVariable(feature.getFeature().getName(), feature.getSelection() == Selection.SELECTED));
			}
		}
		return solver;
	}

	protected AdvancedSatSolver getSolver(boolean includeHiddenFeatures) {
		final CNF satInstance;
		if (includeAbstractFeatures) {
			if (includeHiddenFeatures) {
				satInstance = formula.getCNF();
			} else {
				satInstance = formula.getElement(new NoHiddenCNFCreator());
			}
		} else {
			if (includeHiddenFeatures) {
				satInstance = formula.getElement(new NoAbstractCNFCreator());
			} else {
				satInstance = formula.getElement(new NoAbstractNoHiddenCNFCreator());
			}
		}
		try {
			if (satInstance != null) {
				return new AdvancedSatSolver(satInstance);
			}
		} catch (final RuntimeContradictionException e) {
			Logger.logError(e);
		}
		return null;
	}

	@Override
	public LongRunningMethod<Boolean> canBeValid() {
		return new IsValidMethod(false, true);
	}

	@Override
	public Resolve resolve() {
		return new Resolve();
	}

	/**
	 * Creates solutions to cover the given features.
	 *
	 * @param features The features that should be covered.
	 * @param selection true is the features should be selected, false otherwise.
	 */
	@Override
	public CoverFeatures coverFeatures(final Collection<String> features, final boolean selection) {
		return new CoverFeatures(features, selection);
	}

	@Override
	public FindOpenClauses findOpenClauses() {
		return new FindOpenClauses();
	}

	@Override
	public GetSolutionsMethod getSolutions(int max) throws TimeoutException {
		return new GetSolutionsMethod(max);
	}

	@Override
	public IsValidMethod isValid() {
		return new IsValidMethod(true, true);
	}

	/**
	 * Ignores hidden features. Use this, when propgate is disabled (hidden features are not updated).
	 */
	@Override
	public IsValidMethod isValidNoHidden() {
		return new IsValidMethod(true, false);
	}

	/**
	 * Counts the number of possible solutions.
	 *
	 * @param timeout The timeout in milliseconds.
	 * @return A positive value equal to the number of solutions (if the method terminated in time)<br> or a negative value (if a timeout occurred) that
	 *         indicates that there are more solutions than the absolute value
	 */
	@Override
	public CountSolutionsMethod number(int timeout) {
		return new CountSolutionsMethod(timeout);
	}

	@Override
	public UpdateMethod update(boolean redundantManual, List<SelectableFeature> featureOrder) {
		return new UpdateMethod(redundantManual, featureOrder);
	}

	@Override
	public UpdateMethod update(boolean redundantManual) {
		return update(redundantManual, null);
	}

	@Override
	public UpdateMethod update() {
		return update(false, null);
	}

	@Override
	public ResetAutomaticMethod resetAutomatic() {
		return new ResetAutomaticMethod();
	}

	protected ConfigurationPropagator clone(Configuration configuration) {
		return new ConfigurationPropagator(this, configuration);
	}

	@Override
	public CompleteRandomlyMethod completeRandomly() {
		return new CompleteRandomlyMethod(SelectionStrategy.RANDOM);
	}

	@Override
	public CompleteRandomlyMethod completeMin() {
		return new CompleteRandomlyMethod(SelectionStrategy.NEGATIVE);
	}

	@Override
	public CompleteRandomlyMethod completeMax() {
		return new CompleteRandomlyMethod(SelectionStrategy.POSITIVE);
	}

}
