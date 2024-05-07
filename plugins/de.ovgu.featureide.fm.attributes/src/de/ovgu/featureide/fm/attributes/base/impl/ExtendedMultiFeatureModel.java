/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2017  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.fm.attributes.base.impl;

import de.ovgu.featureide.fm.attributes.base.IExtendedFeatureModel;
import de.ovgu.featureide.fm.core.ExtensionManager.NoSuchExtensionException;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.MultiFeatureModel;

/**
 * This class extends {@link MultiFeatureModel} to support usage of attributes. Instances of this class can be created by
 * {@link ExtendedMultiFeatureModelFactory}.
 * 
 * @author Rahel Arens
 * @author Johannes Herschel
 */
public class ExtendedMultiFeatureModel extends MultiFeatureModel implements IExtendedFeatureModel {

	private IFeatureModelFactory factory;

	public ExtendedMultiFeatureModel(ExtendedMultiFeatureModel copyFeatureModel, ExtendedMultiFeature newRoot, boolean copyId) {
		super(copyFeatureModel, newRoot, copyId);

		try {
			factory = FMFactoryManager.getInstance().getFactory(factoryID);
		} catch (NoSuchExtensionException e) {
			factory = new ExtendedMultiFeatureModelFactory();
		}
	}

	public ExtendedMultiFeatureModel(String factoryID) {
		super(factoryID);

		try {
			factory = FMFactoryManager.getInstance().getFactory(factoryID);
		} catch (NoSuchExtensionException e) {
			factory = new ExtendedMultiFeatureModelFactory();
		}
	}

	@Override
	public void createDefaultValues(CharSequence projectName) {
		String rootName = getValidJavaIdentifier(projectName);
		if (rootName.isEmpty()) {
			rootName = "Root";
		}
		if (featureTable.isEmpty()) {
			final IFeature root = factory.createFeature(this, rootName);
			structure.setRoot(root.getStructure());
			addFeature(root);
		}
		final IFeature feature = factory.createFeature(this, "Base");
		addFeature(feature);

		structure.getRoot().addChild(feature.getStructure());
		structure.getRoot().setAbstract(true);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("ExtendedMultiFeatureModel(");
		if (getStructure().getRoot() != null) {
			sb.append("Structure=[");
			FeatureUtils.print(getStructure().getRoot().getFeature(), sb);
			sb.append("], Constraints=[");
			print(getConstraints(), sb);
			sb.append("], ");
		} else {
			sb.append("Feature model without root feature.");
		}
		final StringBuilder features = new StringBuilder();
		final String[] feat = featureTable.keySet().toArray(new String[featureTable.keySet().size()]);
		for (int i = 0; i < feat.length; i++) {
			features.append(feat[i]);
			if ((i + 1) < feat.length) {
				features.append(", ");
			}
		}
		sb.append("Features=[" + (features.length() > 0 ? features.toString() : ""));
		sb.append("])");
		return sb.toString();
	}

	@Override
	public ExtendedMultiFeatureModel clone() {
		return new ExtendedMultiFeatureModel(this, null, true);
	}

	@Override
	public ExtendedMultiFeatureModel clone(IFeature newRoot, boolean copyId) {
		return new ExtendedMultiFeatureModel(this, (ExtendedMultiFeature) newRoot, copyId);
	}
}
