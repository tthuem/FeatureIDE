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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.prop4j.NodeWriter;

import de.ovgu.featureide.fm.core.RenamingsManager;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.IFeatureModelProperty;
import de.ovgu.featureide.fm.core.base.IFeatureModelStructure;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.event.DefaultEventManager;
import de.ovgu.featureide.fm.core.base.event.FeatureIDEEvent;
import de.ovgu.featureide.fm.core.base.event.FeatureIDEEvent.EventType;
import de.ovgu.featureide.fm.core.base.event.IEventListener;
import de.ovgu.featureide.fm.core.base.event.IEventManager;
import de.ovgu.featureide.fm.core.filter.ConcreteFeatureFilter;
import de.ovgu.featureide.fm.core.functional.Functional;

/**
 * The model representation of the feature tree that notifies listeners of changes in the tree.
 *
 * @author Thomas Thuem
 * @author Florian Proksch
 * @author Stefan Krueger
 * @author Marcus Pinnecke
 */
public class FeatureModel implements IFeatureModel {

	private static long NEXT_ID = 0;

	protected static final synchronized long getNextId() {
		return NEXT_ID++;
	}

	protected long id;

	private long nextElementId = 0;

	@Override
	public final synchronized long getNextElementId() {
		return nextElementId++;
	}

	protected final String factoryID;

	protected final List<IConstraint> constraints = new ArrayList<>();

	/**
	 * A list containing the feature names in their specified order will be initialized in XmlFeatureModelReader.
	 */
	protected final List<String> featureOrderList;
	protected boolean featureOrderUserDefined;
	/**
	 * A {@link Map} containing all features.
	 */
	protected final Map<String, IFeature> featureTable = Collections.synchronizedMap(new LinkedHashMap<>());
	protected final Map<Long, IFeatureModelElement> elements = Collections.synchronizedMap(new LinkedHashMap<>());

	protected IEventManager eventManager = new DefaultEventManager();

	protected final IFeatureModelProperty property;

	protected final RenamingsManager renamingsManager;

	protected final IFeatureModelStructure structure;

	protected Path sourceFile;

	public FeatureModel(String factoryID) {
		this.factoryID = factoryID;

		id = getNextId();
		featureOrderList = new LinkedList<>();
		featureOrderUserDefined = false;

		property = createProperty();
		structure = createStructure();

		renamingsManager = new RenamingsManager(this);
	}

	protected FeatureModel(FeatureModel oldFeatureModel, IFeature newRoot, boolean copyId) {
		factoryID = oldFeatureModel.factoryID;
		id = copyId ? oldFeatureModel.id : getNextId();
		nextElementId = copyId ? oldFeatureModel.nextElementId : 0;
		featureOrderList = new LinkedList<>(oldFeatureModel.featureOrderList);
		featureOrderUserDefined = oldFeatureModel.featureOrderUserDefined;

		property = oldFeatureModel.getProperty().clone(this);
		structure = createStructure();

		renamingsManager = oldFeatureModel.renamingsManager.clone(this);

		sourceFile = oldFeatureModel.sourceFile;

		if (newRoot == null) {
			final IFeatureStructure root = oldFeatureModel.getStructure().getRoot();
			if (root != null) {
				structure.setRoot(root.cloneSubtree(this, copyId));// structure.getRoot().cloneSubtree(this));
				for (final IConstraint constraint : oldFeatureModel.constraints) {
					constraints.add(constraint.clone(this, copyId));
				}
			}
		} else {
			structure.setRoot(newRoot.getStructure().cloneSubtree(this, copyId));
			for (final IConstraint constraint : oldFeatureModel.constraints) {
				if (featureTable.keySet().containsAll(Functional.mapToStringList(constraint.getContainedFeatures()))) {
					constraints.add(constraint.clone(this, copyId));
				}
			}
		}
	}

	protected IFeatureModelProperty createProperty() {
		return new FeatureModelProperty(this);
	}

	protected IFeatureModelStructure createStructure() {
		return new FeatureModelStructure(this);
	}

	@Override
	public void addConstraint(IConstraint constraint) {
		constraints.add(constraint);
		elements.put(constraint.getInternalId(), constraint);
	}

	@Override
	public void addConstraint(IConstraint constraint, int index) {
		constraints.add(index, constraint);
		elements.put(constraint.getInternalId(), constraint);
	}

	@Override
	public boolean addFeature(IFeature feature) {
		final CharSequence name = feature.getName();
		if (featureTable.containsKey(name)) {
			return false;
		}
		featureTable.put(name.toString(), feature);
		elements.put(feature.getInternalId(), feature);
		return true;
	}

	@Override
	public List<IEventListener> getListenerList() {
		return eventManager.getListeners();
	}

	@Override
	public void createDefaultValues(CharSequence projectName) {
		String rootName = getValidJavaIdentifier(projectName);
		if (rootName.isEmpty()) {
			rootName = "Root";
		}
		if (featureTable.isEmpty()) {
			final IFeature root = new Feature(this, rootName);
			structure.setRoot(root.getStructure());
			addFeature(root);
		}
		final IFeature feature = new Feature(this, "Base");
		addFeature(feature);

		structure.getRoot().addChild(feature.getStructure());
		structure.getRoot().setAbstract(true);
	}

	@Override
	public boolean deleteFeature(IFeature feature) {
		// the root can not be deleted
		if (feature.equals(structure.getRoot().getFeature())) {
			return false;
		}

		// check if it exists
		final CharSequence name = feature.getName();
		if (!featureTable.containsKey(name)) {
			return false;
		}

		// use the group type of the feature to delete
		final IFeatureStructure parent = feature.getStructure().getParent();

		if (parent.getChildrenCount() == 1) {
			if (feature.getStructure().isAnd()) {
				parent.setAnd();
			} else if (feature.getStructure().isAlternative()) {
				parent.setAlternative();
			} else {
				parent.setOr();
			}
		}

		// add children to parent
		final int index = parent.getChildIndex(feature.getStructure());
		while (feature.getStructure().hasChildren()) {
			parent.addChildAtPosition(index, feature.getStructure().removeLastChild());
		}

		// delete feature
		parent.removeChild(feature.getStructure());
		featureTable.remove(name);
		elements.remove(feature.getInternalId());
		featureOrderList.remove(name);
		return true;
	}

	@Override
	public void deleteFeatureFromTable(IFeature feature) {
		featureTable.remove(feature.getName());
		elements.remove(feature.getInternalId());
	}

	@Override
	public final void addListener(IEventListener listener) {
		eventManager.addListener(listener);
	}

	@Override
	public List<IEventListener> getListeners() {
		return eventManager.getListeners();
	}

	@Override
	public final void removeListener(IEventListener listener) {
		eventManager.removeListener(listener);
	}

	@Override
	public final void fireEvent(FeatureIDEEvent event) {
		eventManager.fireEvent(event);
	}

	protected void fireEvent(final EventType action) {
		fireEvent(new FeatureIDEEvent(this, action, Boolean.FALSE, Boolean.TRUE));
	}

	@Override
	public int getConstraintCount() {
		return constraints.size();
	}

	@Override
	public int getConstraintIndex(IConstraint constraint) {
		return constraints.indexOf(constraint);
	}

	@Override
	public List<IConstraint> getConstraints() {
		return Collections.unmodifiableList(constraints);
	}

	@Override
	public IFeature getFeature(CharSequence name) {
		return featureTable.get(name);
	}

	@Override
	public List<String> getFeatureOrderList() {
		if (featureOrderList.isEmpty()) {
			return Functional.mapToStringList(Functional.filter(new FeaturePreOrderIterator(this), new ConcreteFeatureFilter()));
		}
		return Collections.unmodifiableList(featureOrderList);
	}

	@Override
	public Collection<IFeature> getFeatures() {
		return Collections.unmodifiableCollection(featureTable.values());
	}

	/**
	 * Returns a list of features, which are not collapsed
	 *
	 * @return
	 */
	@Override
	public Collection<IFeature> getVisibleFeatures() {
		final Collection<IFeature> features = new ArrayList<>();
		for (final IFeature f : getFeatures()) {
			features.add(f);
		}
		return features;
	}

	@Override
	public int getNumberOfFeatures() {
		return featureTable.size();
	}

	@Override
	public IFeatureModelProperty getProperty() {
		return property;
	}

	@Override
	public RenamingsManager getRenamingsManager() {
		return renamingsManager;
	}

	@Override
	public IFeatureModelStructure getStructure() {
		return structure;
	}

	/**
	 * Removes all invalid java identifiers form a given string.
	 */
	protected String getValidJavaIdentifier(CharSequence s) {
		final StringBuilder stringBuilder = new StringBuilder();
		int i = 0;
		for (; i < s.length(); i++) {
			if (Character.isJavaIdentifierStart(s.charAt(i))) {
				stringBuilder.append(s.charAt(i));
				i++;
				break;
			}
		}
		for (; i < s.length(); i++) {
			if (Character.isJavaIdentifierPart(s.charAt(i))) {
				stringBuilder.append(s.charAt(i));
			}
		}
		return stringBuilder.toString();
	}

	@Override
	public void handleModelDataChanged() {
		fireEvent(EventType.MODEL_DATA_CHANGED);
	}

	@Override
	public boolean isFeatureOrderUserDefined() {
		return featureOrderUserDefined;
	}

	@Override
	public void removeConstraint(IConstraint constraint) {
		constraints.remove(constraint);
		elements.remove(constraint.getInternalId());
	}

	@Override
	public void removeConstraint(int index) {
		final IConstraint constraint = constraints.remove(index);
		elements.remove(constraint.getInternalId());
	}

	@Override
	public void replaceConstraint(IConstraint constraint, int index) {
		if (constraint == null) {
			throw new NullPointerException();
		}
		elements.remove(constraints.get(index).getInternalId());
		constraints.set(index, constraint);
		elements.put(constraint.getInternalId(), constraint);
	}

	@Override
	public void reset() {
		structure.setRoot(null);

		featureTable.clear();
		renamingsManager.clear();
		constraints.clear();
		featureOrderList.clear();
		elements.clear();

		property.reset();
		nextElementId = 0;
	}

	@Override
	public void setConstraints(Iterable<IConstraint> constraints) {
		this.constraints.clear();
		for (final IConstraint constraint : constraints) {
			addConstraint(constraint);
		}
	}

	@Override
	public void setFeatureOrderList(List<String> featureOrderList) {
		final List<String> basicSet = Functional.mapToList(new FeaturePreOrder(this), new ConcreteFeatureFilter(), IFeature::getName);
		// TODO optimize performance
		basicSet.removeAll(featureOrderList);
		this.featureOrderList.clear();
		this.featureOrderList.addAll(featureOrderList);
		this.featureOrderList.addAll(basicSet);
	}

	@Override
	public void setFeatureOrderUserDefined(boolean featureOrderUserDefined) {
		this.featureOrderUserDefined = featureOrderUserDefined;
	}

	@Override
	public void setFeatureTable(Hashtable<String, IFeature> featureTable) {
		this.featureTable.clear();
		elements.clear();
		this.featureTable.putAll(featureTable);
		for (final IFeature feature : featureTable.values()) {
			elements.put(feature.getInternalId(), feature);
		}
		for (final IConstraint constraint : constraints) {
			elements.put(constraint.getInternalId(), constraint);
		}
	}

	@Override
	public Map<String, IFeature> getFeatureTable() {
		return Collections.unmodifiableMap(featureTable);
	}

	@Override
	public void setFeatureOrderListItem(int i, String newName) {
		if (!featureOrderList.isEmpty()) {
			featureOrderList.set(i, newName);
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("FeatureModel(");
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

	protected void print(List<IConstraint> constraints, StringBuilder sb) {
		for (int i = 0; i < constraints.size(); i++) {
			sb.append("[");
			sb.append(new NodeWriter(constraints.get(i).getNode()).nodeToString());
			sb.append("]");
			if ((i + 1) < constraints.size()) {
				sb.append(", ");
			}
		}
	}

	@Override
	public void setSourceFile(Path file) {
		sourceFile = file;
		if (file != null) {
			id = ModelFileIdMap.getModelId(this, file);
		}
	}

	@Override
	public Path getSourceFile() {
		return sourceFile;
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public int hashCode() {
		return (int) (37 * id);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		final FeatureModel other = (FeatureModel) obj;
		return id == other.id;
	}

	@Override
	public void setConstraint(int index, IConstraint constraint) {
		constraints.set(index, constraint);
	}

	@Override
	public FeatureModel clone() {
		return new FeatureModel(this, null, true);
	}

	@Override
	public IFeatureModel clone(IFeature newRoot, boolean copyId) {
		return new FeatureModel(this, newRoot, copyId);
	}

	@Override
	public String getFactoryID() {
		return factoryID;
	}

	@Override
	public void setEventManager(IEventManager eventManager) {
		this.eventManager = eventManager;
	}

	@Override
	public IFeatureModelElement getElement(long id) {
		return elements.get(id);
	}

	/**
	 * Sets the next element ID to the correct value for this feature model to avoid duplicate IDs.
	 */
	public void updateNextElementId() {
		long max = 0;
		final List<IFeatureModelElement> elements = new ArrayList<>();
		elements.addAll(getFeatures());
		elements.addAll(getConstraints());

		for (final IFeatureModelElement element : elements) {
			if (element.getInternalId() > max) {
				max = element.getInternalId();
			}
		}
		nextElementId = max + 1;
	}

	public void setNextElementId(long nextElementId) {
		this.nextElementId = nextElementId;
	}
}
