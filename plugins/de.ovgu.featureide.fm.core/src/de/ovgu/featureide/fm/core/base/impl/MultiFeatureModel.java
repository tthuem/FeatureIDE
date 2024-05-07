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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.constraint.Equation;
import de.ovgu.featureide.fm.core.constraint.FeatureAttributeMap;

/**
 * Adds attributes and attribute constraints to a feature model.
 *
 * @author Sebastian Krieter
 * @author Matthias Strauss
 */
public class MultiFeatureModel extends FeatureModel {

	public static class UsedModel {

		private final String modelName;
		private String varName;
		/**
		 * The absolute path of the used model.
		 */
		private final Path path;
		private final int type;

		private String prefix;

		public UsedModel(UsedModel usedModel, String parentName) {
			modelName = usedModel.modelName;
			varName = parentName + usedModel.varName;
			path = null;
			type = usedModel.type;
		}

		public UsedModel(String modelName, String varName, Path path, int type) {
			this.modelName = modelName;
			this.varName = varName;
			this.path = path;
			this.type = type;
		}

		public String getModelName() {
			return modelName;
		}

		public String getVarName() {
			return varName;
		}

		/**
		 * Private setter for varName. Use {@link MultiFeatureModel#renameExternalModel(String, String)} to rename external models.
		 *
		 * @param varName The new varName
		 */
		private void setVarName(String varName) {
			this.varName = varName;
		}

		/**
		 * @return The absolute path of the used model
		 */
		public Path getPath() {
			return path;
		}

		public int getType() {
			return type;
		}

		public String getPrefix() {
			return prefix;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public String toString() {
			return modelName + " " + varName;
		}
	}

	protected final FeatureAttributeMap<Integer> integerAttributes;
	protected final FeatureAttributeMap<Boolean> booleanAttributes;
	protected final FeatureAttributeMap<String> stringAttributes;

	protected final Map<String, UsedModel> usedModels;

	protected final List<Equation> attributeConstraints;
	protected final List<String> imports;
	protected final List<IConstraint> ownConstraints;

	protected IFeatureModel mappingModel;

	private boolean isInterface;

	public MultiFeatureModel(String factoryID) {
		super(factoryID);

		integerAttributes = new FeatureAttributeMap<>();
		booleanAttributes = new FeatureAttributeMap<>();
		stringAttributes = new FeatureAttributeMap<>();

		usedModels = new HashMap<>();

		attributeConstraints = new LinkedList<>();
		imports = new LinkedList<>();
		ownConstraints = new LinkedList<>();

		mappingModel = null;
		isInterface = false;
	}

	protected MultiFeatureModel(MultiFeatureModel extendedFeatureModel, IFeature newRoot, boolean copyId) {
		super(extendedFeatureModel, newRoot, copyId);

		integerAttributes = new FeatureAttributeMap<>(extendedFeatureModel.integerAttributes);
		booleanAttributes = new FeatureAttributeMap<>(extendedFeatureModel.booleanAttributes);
		stringAttributes = new FeatureAttributeMap<>(extendedFeatureModel.stringAttributes);

		usedModels = new HashMap<>(extendedFeatureModel.usedModels);

		attributeConstraints = new LinkedList<>(extendedFeatureModel.attributeConstraints);
		imports = new LinkedList<>(extendedFeatureModel.imports);
		ownConstraints = new LinkedList<>(extendedFeatureModel.ownConstraints);

		mappingModel = extendedFeatureModel.mappingModel;

		isInterface = extendedFeatureModel.isInterface;
	}

	public List<String> getImports() {
		return imports;
	}

	public void addImport(String imp) {
		imports.add(imp.replace(".", "\\"));
	}

	public void addAttribute(final String featureName, final String attributeName, final Boolean value) {
		booleanAttributes.setAttribute(featureName, attributeName, value);
	}

	public void addAttribute(final String featureName, final String attributeName, final Integer value) {
		integerAttributes.setAttribute(featureName, attributeName, value);
	}

	public void addAttribute(final String featureName, final String attributeName, final String value) {
		stringAttributes.setAttribute(featureName, attributeName, value);
	}

	public void addAttributeConstraint(final Equation constraint) {
		attributeConstraints.add(constraint);
	}

	@Override
	public void addConstraint(IConstraint constraint) {
		if ((constraint instanceof MultiConstraint) && !(((MultiConstraint) constraint).getType() == MultiFeature.TYPE_INTERFACE)) {
			addOwnConstraint(constraint);
		} else {
			constraints.add(constraint);
		}
		elements.put(constraint.getInternalId(), constraint);
	}

	@Override
	public void addConstraint(IConstraint constraint, int index) {
		if ((constraint instanceof MultiConstraint) && !(((MultiConstraint) constraint).getType() == MultiFeature.TYPE_INTERFACE)) {
			addOwnConstraint(constraint);
		} else {
			constraints.add(constraint);
		}
		elements.put(constraint.getInternalId(), constraint);
	}

	public void addOwnConstraint(final IConstraint constraint) {
		ownConstraints.add(constraint);
		constraints.add(constraint);
	}

	/**
	 * Adds a parameter to the available parameters of the model
	 *
	 * @param varType the name of the interface that shall be bound to the variable
	 * @param varName the name of the variable an interface shall be bound to
	 * @param path
	 * @return true if the parameter could be added to the parameters. False if the variable name was already bound to another interface.
	 */
	public boolean addInterface(final String varType, final String varName, final Path path) {
		return addModel(varType, varName, path, MultiFeature.TYPE_INTERFACE);
	}

	public boolean addInstance(final String varType, final String varName, final Path path) {
		return addModel(varType, varName, path, MultiFeature.TYPE_INSTANCE);
	}

	public boolean addInheritance(final String varType, final String varName, final Path path) {
		return addModel(varType, varName, path, MultiFeature.TYPE_INHERITED);
	}

	public boolean addExternalModel(final UsedModel model) {
		if (usedModels.containsKey(model.varName)) {
			return false;
		} else {
			usedModels.put(model.varName, model);
			return true;
		}
	}

	private boolean addModel(final String varType, final String varName, final Path path, int modelType) {
		if (usedModels.containsKey(varName)) {
			return false;
		} else {
			usedModels.put(varName, new UsedModel(varType, varName, path, modelType));
			return true;
		}
	}

	/**
	 * Removes the external model with the given name from this feature model.
	 *
	 * @param varName The name of the model to be removed
	 */
	public void removeExternalModel(final String varName) {
		usedModels.remove(varName);
	}

	/**
	 * @param varName The name of an external model.
	 * @return This feature model's external model with the given name, or null if no such model exists.
	 */
	public UsedModel getExternalModel(String varName) {
		return usedModels.get(varName);
	}

	/**
	 * Renames an external model.
	 *
	 * @param oldVarName The old name of the model to rename
	 * @param newVarName The new name of the model to rename
	 */
	public void renameExternalModel(String oldVarName, String newVarName) {
		if (!usedModels.containsKey(oldVarName) || usedModels.containsKey(newVarName)) {
			return;
		}

		final UsedModel usedModel = usedModels.remove(oldVarName);
		usedModel.setVarName(newVarName);
		usedModels.put(newVarName, usedModel);
	}

	public List<Equation> getAttributConstraints() {
		return attributeConstraints;
	}

	public FeatureAttributeMap<Boolean> getBooleanAttributes() {
		return booleanAttributes;
	}

	public FeatureAttributeMap<Integer> getIntegerAttributes() {
		return integerAttributes;
	}

	public FeatureAttributeMap<String> getStringAttributes() {
		return stringAttributes;
	}

	public List<IConstraint> getOwnConstraints() {
		return Collections.unmodifiableList(ownConstraints);
	}

	public boolean isMultiProductLineModel() {
		return !usedModels.isEmpty();
	}

	/**
	 * Check if the feature model contains instance features.
	 *
	 * @return true if imported features exists
	 */
	public boolean hasInstance() {
		for (final IFeature feature : featureTable.values()) {
			if ((feature instanceof MultiFeature) && ((MultiFeature) feature).isInstance()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the feature model contains inherited features.
	 *
	 * @return true if inherited features exists
	 */
	public boolean hasInherited() {
		for (final IFeature feature : featureTable.values()) {
			if ((feature instanceof MultiFeature) && ((MultiFeature) feature).isInherited()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasInterface() {
		for (final IFeature feature : featureTable.values()) {
			if ((feature instanceof MultiFeature) && ((MultiFeature) feature).isInterface()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void removeConstraint(IConstraint constraint) {
		ownConstraints.remove(constraint);
		super.removeConstraint(constraint);
	}

	@Override
	public void removeConstraint(int index) {
		ownConstraints.remove(constraints.get(index));
		super.removeConstraint(index);
	}

	@Override
	public void replaceConstraint(IConstraint constraint, int index) {
		super.replaceConstraint(constraint, index);
	}

	@Override
	public void setConstraint(int index, IConstraint constraint) {
		ownConstraints.remove(constraints.get(index));
		ownConstraints.add(constraint);
		super.setConstraint(index, constraint);
	}

	@Override
	public void reset() {
		super.reset();
		usedModels.clear();
		imports.clear();
		ownConstraints.clear();
	}

	/**
	 * @return the mappingModel
	 */
	public IFeatureModel getMappingModel() {
		return mappingModel;
	}

	public Map<String, UsedModel> getExternalModels() {
		return Collections.unmodifiableMap(usedModels);
	}

	/**
	 * @param mappingModel the mappingModel to set
	 */
	public void setMappingModel(IFeatureModel mappingModel) {
		this.mappingModel = mappingModel;
	}

	@Override
	public IFeature getFeature(CharSequence name) {
		final IFeature feature = super.getFeature(name);
		if (feature != null) {
			return feature;
		}

		if (name.toString().contains(".")) {
			return super.getFeature(getStructure().getRoot().getFeature().getName() + "." + name);
		} else {
			return null;
		}
	}

	public boolean isInterface() {
		return isInterface;
	}

	public void setInterface(boolean isInterface) {
		this.isInterface = isInterface;
	}

	@Override
	public MultiFeatureModel clone() {
		return new MultiFeatureModel(this, null, true);
	}

	@Override
	public MultiFeatureModel clone(IFeature newRoot, boolean copyId) {
		return new MultiFeatureModel(this, (MultiFeature) newRoot, copyId);
	}

}
