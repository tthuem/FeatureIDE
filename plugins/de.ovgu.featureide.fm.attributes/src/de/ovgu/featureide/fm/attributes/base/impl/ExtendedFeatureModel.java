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
package de.ovgu.featureide.fm.attributes.base.impl;

import de.ovgu.featureide.fm.attributes.base.IExtendedFeatureModel;
import de.ovgu.featureide.fm.core.ExtensionManager.NoSuchExtensionException;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FeatureModel;

/**
 * Represents extended feature models. Extends the {@link FeatureModel} provided by FeatureIDE. Provides copy constructors and copy methods.
 * 
 * @author Joshua Sprey
 * @author Chico SUndermann
 */
public class ExtendedFeatureModel extends FeatureModel implements IExtendedFeatureModel {

	IFeatureModelFactory factory;

	public ExtendedFeatureModel(ExtendedFeatureModel copyFeatureModel, ExtendedFeature newRoot, boolean copyId) {
		super(copyFeatureModel, newRoot, copyId);

		try {
			factory = FMFactoryManager.getInstance().getFactory(factoryID);
		} catch (NoSuchExtensionException e) {
			factory = new ExtendedFeatureModelFactory();
		}
	}

	public ExtendedFeatureModel(String factoryID) {
		super(factoryID);

		try {
			factory = FMFactoryManager.getInstance().getFactory(factoryID);
		} catch (NoSuchExtensionException e) {
			factory = new ExtendedFeatureModelFactory();
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
		StringBuilder builder = new StringBuilder();
		builder.append("ExtendedFeatureModel[");
		for (IFeature feature : getFeatures()) {
			builder.append(feature.toString() + ", ");
		}
		builder.append("]");
		return builder.toString();
	}

	@Override
	public ExtendedFeatureModel clone() {
		return new ExtendedFeatureModel(this, null, true);
	}

	@Override
	public ExtendedFeatureModel clone(IFeature newRoot, boolean copyId) {
		return new ExtendedFeatureModel(this, (ExtendedFeature) newRoot, copyId);
	}

}
