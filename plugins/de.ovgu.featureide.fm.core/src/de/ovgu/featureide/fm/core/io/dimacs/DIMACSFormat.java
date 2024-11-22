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
package de.ovgu.featureide.fm.core.io.dimacs;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.prop4j.And;
import org.prop4j.Node;

import de.ovgu.featureide.fm.core.PluginID;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.Nodes;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.editing.AdvancedNodeCreator;
import de.ovgu.featureide.fm.core.io.AFeatureModelFormat;
import de.ovgu.featureide.fm.core.io.Problem;
import de.ovgu.featureide.fm.core.io.ProblemList;

/**
 * Reads and writes feature models in the DIMACS CNF format.
 *
 * @author Sebastian Krieter
 * @author Timo G&uuml;nther
 */
public class DIMACSFormat extends AFeatureModelFormat {

	public static final String ID = PluginID.PLUGIN_ID + ".format.fm." + DIMACSFormat.class.getSimpleName();
	public static final String DUMMY_ROOT_NAME = "__Root__";

	private boolean flattenCNF = false;
	private boolean omitDummyRoot = true;

	@Override
	public ProblemList read(IFeatureModel featureModel, CharSequence source) {
		final ProblemList problemList = new ProblemList();
		setFactory(featureModel);

		// Transform the input into a propositional node.
		final DimacsReader r = new DimacsReader();
		r.setReadingVariableDirectory(true);
		r.setFlattenCNF(flattenCNF);
		try (StringReader reader = new StringReader(source.toString())) {
			final Node node = r.read(reader);
			final List<String> variables = r.getVariables();

			// Add the propositional node to the feature model.
			featureModel.reset();
			addNodeToFeatureModel(featureModel, node, variables);
		} catch (final ParseException e) {
			problemList.add(new Problem(e, e.getErrorOffset()));
		} catch (final IOException e) {
			problemList.add(new Problem(e));
		}

		return problemList;
	}

	/**
	 * Adds the given propositional node to the given feature model. The current implementation is naive in that it does not attempt to interpret any constraint
	 * as {@link IFeatureStructure structure}.
	 *
	 * @param featureModel feature model to edit
	 * @param node propositional node to add
	 * @param variables the variables of the propositional node
	 */
	private void addNodeToFeatureModel(IFeatureModel featureModel, Node node, List<String> variables) {
		// Add a dummy feature as root.
		final IFeature rootFeature = factory.createFeature(featureModel, DUMMY_ROOT_NAME);
		featureModel.addFeature(rootFeature);
		rootFeature.getStructure().setAbstract(true);
		featureModel.getStructure().setRoot(rootFeature.getStructure());

		// Add a feature for each variable.
		for (final String variable : variables) {
			if (!DUMMY_ROOT_NAME.equals(variable)) {
				final IFeature feature = factory.createFeature(featureModel, variable.toString());
				FeatureUtils.addFeature(featureModel, feature);
				FeatureUtils.addChild(rootFeature, feature);
			}
		}

		// Add a constraint for each conjunctive clause.
		final List<Node> clauses = node instanceof And ? Arrays.asList(node.getChildren()) : Collections.singletonList(node);
		for (final Node clause : clauses) {
			FeatureUtils.addConstraint(featureModel, factory.createConstraint(featureModel, clause));
		}
	}

	public void setFlattenCNF(boolean flattenCNF) {
		this.flattenCNF = flattenCNF;
	}

	public boolean isOmitDummyRoot() {
		return omitDummyRoot;
	}

	public void setOmitDummyRoot(boolean omitRoot) {
		omitDummyRoot = omitRoot;
	}

	@Override
	public String write(IFeatureModel featureModel) {
		final AdvancedNodeCreator nodeCreator = new AdvancedNodeCreator(featureModel);
		List<String> featureNamesList = FeatureUtils.getFeatureNamesList(featureModel);
		if (omitDummyRoot) {
			final IFeatureStructure root = featureModel.getStructure().getRoot();
			if ((root != null) && DUMMY_ROOT_NAME.equals(root.getFeature().getName())) {
				nodeCreator.setOmitRoot(omitDummyRoot);
				featureNamesList = new ArrayList<>(featureNamesList);
				featureNamesList.remove(DUMMY_ROOT_NAME);
			}
		}
		final Variables variables = new Variables(featureNamesList);
		final CNF cnf = new CNF(variables);
		cnf.addClauses(Nodes.convert(variables, nodeCreator.createNodes()));
		final DimacsWriter w = new DimacsWriter(cnf);
		w.setWritingVariableDirectory(true);
		return w.write();
	}

	@Override
	public String getSuffix() {
		return "dimacs";
	}

	@Override
	public DIMACSFormat getInstance() {
		return this;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean supportsRead() {
		return true;
	}

	@Override
	public boolean supportsWrite() {
		return true;
	}

	@Override
	public String getName() {
		return "DIMACS";
	}

}
