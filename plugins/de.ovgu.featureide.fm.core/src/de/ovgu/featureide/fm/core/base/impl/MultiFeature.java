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
package de.ovgu.featureide.fm.core.base.impl;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureProperty;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;

/**
 * Feature for the {@link MultiFeatureModel}.
 *
 * @author Sebastian Krieter
 */
public class MultiFeature extends Feature {

	public static final int TYPE_INTERN = 0, TYPE_INHERITED = 1, TYPE_INTERFACE = 2, TYPE_INSTANCE = 3;

	private int type = TYPE_INTERN;
	private String externalModelName = null;
	private boolean newDefined = false;

	public MultiFeature(IFeatureModel featureModel, String name) {
		super(featureModel, name);
	}

	public MultiFeature(IFeature oldFeature, IFeatureModel featureModel, boolean copyId, IFeatureStructure newStructure) {
		super(oldFeature, featureModel, copyId, newStructure);

		if (oldFeature instanceof MultiFeature) {
			// Copy properties of MultiFeature if available
			final MultiFeature feature = (MultiFeature) oldFeature;
			type = feature.type;
			externalModelName = feature.externalModelName;
			newDefined = feature.newDefined;
		}
	}

	public MultiFeature(IFeature oldFeature, IFeatureModel featureModel, boolean copyId) {
		super(oldFeature, featureModel, copyId);

		if (oldFeature instanceof MultiFeature) {
			// Copy properties of MultiFeature if available
			final MultiFeature feature = (MultiFeature) oldFeature;
			type = feature.type;
			externalModelName = feature.externalModelName;
			newDefined = feature.newDefined;
		}
	}

	@Override
	protected IFeatureProperty createProperty() {
		return new MultiFeatureProperty(this);
	}

	public int getType() {
		return type;
	}

	public boolean isInherited() {
		return type == TYPE_INHERITED;
	}

	public boolean isInterface() {
		return type == TYPE_INTERFACE;
	}

	public boolean isInstance() {
		return type == TYPE_INSTANCE;
	}

	public boolean isIntern() {
		return type == TYPE_INTERN;
	}

	public boolean isFromExtern() {
		return type != TYPE_INTERN;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getExternalModelName() {
		return externalModelName;
	}

	public void setExternalModelName(String externalModelName) {
		this.externalModelName = externalModelName;
	}

	public boolean isNewDefined() {
		return newDefined;
	}

	public void setNewDefined(boolean newDefined) {
		this.newDefined = newDefined;
	}

	@Override
	public IFeature clone(IFeatureModel newFeatureModel, boolean copyId, IFeatureStructure newStructure) {
		return new MultiFeature(this, newFeatureModel, copyId, newStructure);
	}

}
