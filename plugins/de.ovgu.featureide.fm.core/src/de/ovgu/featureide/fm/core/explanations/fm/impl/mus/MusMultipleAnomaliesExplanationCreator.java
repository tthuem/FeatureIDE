/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2020  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.fm.core.explanations.fm.impl.mus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.prop4j.explain.solvers.SatSolverFactory;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.FeatureModelExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.FeatureModelExplanationCreatorFactory;
import de.ovgu.featureide.fm.core.explanations.fm.MultipleAnomaliesExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.MultipleAnomaliesExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanationCreator;

/**
 * {@link MusMultipleAnomaliesExplanationCreator} creates combined {@link MultipleAnomaliesExplanation}s with all anomaly types.
 *
 * @author Benedikt Jutz
 */
public class MusMultipleAnomaliesExplanationCreator extends MusFeatureModelExplanationCreator<IFeatureModel, MultipleAnomaliesExplanation>
		implements MultipleAnomaliesExplanationCreator {

	/**
	 * Creates a new {@link MusMultipleAnomaliesExplanationCreator} with the given <code>satSolverFactory</code>.
	 *
	 * @param solverFactory - {@link SatSolverFactory}
	 */
	protected MusMultipleAnomaliesExplanationCreator(SatSolverFactory solverFactory) {
		super(solverFactory);
	}

	/**
	 * Creates dead, false-optional and redundancy explanations, then combines them to a single one and returns it.
	 *
	 * @see de de.ovgu.featureide.fm.core.explanations.impl.AbstractExplanationCreator#getExplanation()
	 */
	@Override
	public MultipleAnomaliesExplanation getExplanation() throws IllegalStateException {
		final IFeatureModel featureModel = getFeatureModel();
		final Collection<IFeature> features = featureModel.getFeatures();
		final List<FeatureModelExplanation<? extends IFeatureModelElement>> exps = new ArrayList<>((2 * features.size()) + featureModel.getConstraintCount());
		final FeatureModelExplanationCreatorFactory creatorFactory = MusFeatureModelExplanationCreatorFactory.getDefault();

		// Get all Dead Feature explanations.
		final DeadFeatureExplanationCreator deadFeatExpCreator = creatorFactory.getDeadFeatureExplanationCreator();
		deadFeatExpCreator.setFeatureModel(featureModel);
		// Get all False-Optional Feature explanations.
		final FalseOptionalFeatureExplanationCreator falseOptFeatExpCreator = creatorFactory.getFalseOptionalFeatureExplanationCreator();
		falseOptFeatExpCreator.setFeatureModel(featureModel);
		// Get all Redundant-Constraint explanations.
		final RedundantConstraintExplanationCreator redundantConsExpCreator = creatorFactory.getRedundantConstraintExplanationCreator();
		redundantConsExpCreator.setFeatureModel(featureModel);
		for (final IFeature feature : features) {
			exps.add(deadFeatExpCreator.getExplanationFor(feature));
			exps.add(falseOptFeatExpCreator.getExplanationFor(feature));
		}
		for (final IConstraint constraint : featureModel.getConstraints()) {
			exps.add(redundantConsExpCreator.getExplanationFor(constraint));
		}

		return new MultipleAnomaliesExplanation(featureModel, exps);
	}

	/*
	 * (non-Javadoc)
	 * @see de.ovgu.featureide.fm.core.explanations.impl.AbstractExplanationCreator#getConcreteExplanation()
	 */
	@Override
	protected MultipleAnomaliesExplanation getConcreteExplanation() {
		return getExplanation();
	}

}
