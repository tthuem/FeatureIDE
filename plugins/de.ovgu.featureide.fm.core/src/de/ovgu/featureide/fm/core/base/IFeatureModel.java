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
package de.ovgu.featureide.fm.core.base;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import de.ovgu.featureide.fm.core.RenamingsManager;
import de.ovgu.featureide.fm.core.base.event.IEventListener;
import de.ovgu.featureide.fm.core.base.event.IEventManager;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.ModelFileIdMap;
import de.ovgu.featureide.fm.core.functional.Functional;

/**
 * The feature model interface represents any class that acts in the sense of a <i>feature model</i> in FeatureIDE. <br> <br> A feature model contains of a
 * modifiable collection of <ul> <li>{@link de.ovgu.featureide.fm.core.base.IFeature Features}, and</li> <li>{@link de.ovgu.featureide.fm.core.base.IConstraint
 * Constraints of features}</li> </ul> <br> Each <i>feature</i> in a feature model has a unique identifier and is related to other features over some
 * expressions and relations which forms the feature model (such as parent-children relation with implication expression from one feature to another).
 * Additional to the structure of features (see {@link de.ovgu.featureide.fm.core.base.IFeature} and {@link de.ovgu.featureide.fm.core.base.IFeatureStructure}
 * for more details) inside the feature models tree, features relationships can be further expressed using <i>constraints</i>. While the feature structure is
 * bound to the actual feature model tree, constraints can be state restrictions and relations to arbitrary features inside the feature model. <br> <br>
 * Features inside a feature model can by analyzed in order to determine feature properties which are implicated by the structure, the statements, and the
 * constraints. As a result of such an analysis, a set of <i>dead features</i> can be found for instance. For more information about analysis, see
 * {@link de.ovgu.featureide.fm.core.FeatureModelAnalyzer FeatureModelAnalyzer}. <br> <br> Additional to the collection mentioned above, the feature model
 * contains properties to express <ul> <li>Annotations</li> <li>Comments</li> <li>Feature Orders</li> </ul> <br> A feature model is moreover related to it's
 * project, such that the <i>project's name</i> can be received, the related composer extension can be received, as well as certain event handling logic (such
 * as model data change event handling) can be made. Furthermore, each feature model is <i>required to has an own unique system-wide identifier</i> (at least
 * during runtime). Any implementation of this interface has to provide the corresponding {@link #getId()} method and have to implement the management of
 * identifiers among a set of feature models. <br> <br> Any feature model is intended to be instantiated by a corresponding factory, the implementation-specific
 * {@link IFeatureModelFactory}. <br> <br> FeatureIDE provides a default implementation {@link de.ovgu.featureide.fm.core.base.impl.FeatureModel} which is used
 * for default use-cases and can be customized via inheritance of {@link de.ovgu.featureide.fm.core.base.impl.FeatureModel} and a user-defined
 * {@link IFeatureModelFactory feature model factory}. Internally, a feature model is represented by an unique numeric identifier, which should be considered in
 * the related {@link IFeatureModelFactory} in order to avoid confusion with other models. <br> <br> <b>Example</b><br> The following example demonstrate the
 * creation of a new feature model using FeatureIDE's default <code>FeatureModel</code> implementation {@link de.ovgu.featureide.fm.core.base.impl.FeatureModel
 * FeatureModel}, and the corresponding default factory {@link de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory DefaultFeatureModelFactory} over
 * the conviennent factory class {@link FMFactoryManager}: <code> IFeatureModel model = FMFactoryManager.getFactory().createFeatureModel(); </code> A unified
 * handling of certain <code>IFeature</code>, and <code>IFeatureModel</code> implementations (in terms of conviennent methods) can be achieved with the use of
 * {@link de.ovgu.featureide.fm.core.base.FeatureUtils FeatureUtils} helper class. <br> <br> <b>Caching notes</b>: A feature model implementation using the
 * <code>IFeatureModel</code> interface has to provide a map of feature names to the corresponding feature objects, the <i>feature table</i>. This data
 * structure is used in the {@link RenamingsManager} for instance. If the implementation utilizes this data structure for internal use, modifications to this
 * data structure must be protected against concurrent accesses. The default implementations {@link IFeatureModel} uses a <code>ConcurrentHashMap</code> for
 * this purpose. <br> <br> <b>API notes</b>: The classes internal structure has heavily changed compared to older FeatureIDE version. A bridge to the
 * old-fashioned handling is available in {@link de.ovgu.featureide.fm.core.base.FeatureUtils FeatureUtils} as static methods. <br> <br> <b>Notes on thread
 * safeness</b>: At least the management of <code>IFeature</code> and <code>IFeatureModel</code> identifiers (e.g., getting the next free id) have to be thread
 * safe. The reference default implementation for feature models is <code> private static long NEXT_ID = 0;
 *
 * protected static final synchronized long getNextId() { return NEXT_ID++; } </code> <br> <br> <b>Compatibility Notes</b>: To provide compatibility to earlier
 * versions of FeatureIDE, the <i>out-dated</i> class {@link IFeatureModel FeatureModel} is now a wrapper to an <code>IFeatureModel</code> instance (but
 * incompatible to it) and make use of convert-functionalities inside {@link de.ovgu.featureide.fm.core.base.FeatureUtils FeatureUtils}.
 *
 * @see de.ovgu.featureide.fm.core.base.impl.FeatureModel Default implementation of <code>IFeatureModel</code> (as starting point for custom implementations)
 *
 * @see IFeature Interface for features (<code>IFeature</code>)
 * @see IConstraint Interface for feature constraints (<code>IConstraint</code>)
 * @see IFeatureProperty Interface for feature properties (<code>IFeatureProperty</code>)
 * @see IFeatureStructure Interface for a feature's structure (<code>IFeatureStructure</code>)
 *
 * @see de.ovgu.featureide.fm.core.base.impl.Feature Default implementation for features (<code>Feature</code>)
 * @see de.ovgu.featureide.fm.core.base.impl.AConstraint Default implementation for feature constraints (<code>AConstraint</code>)
 * @see de.ovgu.featureide.fm.core.base.impl.FeatureProperty Default implementation for feature properties (<code>FeatureProperty</code>)
 * @see de.ovgu.featureide.fm.core.base.impl.FeatureStructure Default implementation for a feature's structure (<code>FeatureStructure</code>)
 *
 * @since 3.0
 *
 * @author Sebastian Krieter
 * @author Marcus Pinnecke
 */
public interface IFeatureModel extends Cloneable, IEventManager {

	/**
	 * Feature models are identified with their system-wide unique numeric identifier. This methods returns the identifier of the current feature model. Custom
	 * implementations might manage the feature model's identifier similar to the default implementation: <code> private static long NEXT_ID = 0;
	 *
	 * protected static final synchronized long getNextId() { return NEXT_ID++; } </code> The identifier has to be used for comparisons using
	 * {@link Object#equals(Object)}.
	 *
	 * @return unique identifier
	 *
	 * @see #getNextElementId()
	 *
	 * @since 3.0
	 */
	long getId();

	/**
	 * A feature model is created via a feature model {@link IFeatureModelFactory factory}. This methods returns the identifier of the factory used to create
	 * this feature model. The factory can be used to create more feature models, features, or constraint from the same type as this feature model.
	 *
	 * @return the feature model factory ID.
	 *
	 * @see FMFactoryManager#getFactory(String)
	 *
	 * @since 3.1
	 */
	String getFactoryID();

	/**
	 * A constraint is an additional restriction on features in the feature model.
	 *
	 * This methods adds the constraint <code>constraint</code> to the <i>end</i> of the existing collection. Please note that <ul> <li>the specification do not
	 * require a check if <code>constraint</code> is <i>null</i>. However, for regular use, <code>constraint</code> is assumed to be <i>non-null</i></li>
	 * <li>the specification do not require a check of duplicates. In FeatureIDE's default implementation, the collection is managed using a <code>List</code>.
	 * For regular use case, this collection is assumed to be duplicate-free. Therefore, duplicates should not be added.</li> </ul>
	 *
	 * To add a constraint at a specific position, use {@link #addConstraint(IConstraint, int)}
	 *
	 * @param constraint The constraint to be added at the end of the existing collection
	 *
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 */
	void addConstraint(IConstraint constraint);

	/**
	 * A constraint is an additional restriction on features in the feature model.
	 *
	 * This methods adds the constraint <code>constraint</code> at the given <i>index</i> of the existing collection. Please note that <ul> <li>the
	 * specification do not require a check if <code>constraint</code> is <i>null</i>. However, for regular use, <code>constraint</code> is assumed to be
	 * <i>non-null</i></li> <li>the specification do not require a check of duplicates. In FeatureIDE's default implementation, the collection is managed using
	 * a <code>List</code>. For regular use case, this collection is assumed to be duplicate-free. Therefore, duplicates should not be added.</li> </ul>
	 *
	 * To add a constraint at a specific position, use {@link #addConstraint(IConstraint, int)}
	 *
	 * @param constraint The constraint to be added at position <i>index</i> of the existing collection
	 * @param index The position. It is assumed, that the index is valid. Otherwise a exception have to be thrown by the implementation.
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 */
	void addConstraint(IConstraint constraint, int index);

	/**
	 * Add a new feature <code>feature</code> to this feature model. If the feature model not contains a feature with the name {@link IFeature#getName()} of
	 * <code>feature</code>, the <code>feature</code> will be added and the method returns <b>true</b>. Otherwise, the feature is not added and the method
	 * returns <b>false</b>. Classes implementing <code>IFeatureModel</code> must provide consistency with the underlying <i>feature table</i> which is
	 * accessible by {@link #getFeatureTable()}.
	 *
	 * @param feature the feature to be added. <code>feature</code> is assumed to be <i>non-null</i>
	 * @return <b>true</b> if the feature was added, otherwise <b>false</b>.
	 *
	 * @see #deleteFeature(IFeature)
	 * @see #getFeature(CharSequence)
	 * @see #getFeatures()
	 * @see #getNumberOfFeatures()
	 * @see #reset()
	 *
	 * @since 3.0
	 */
	boolean addFeature(IFeature feature);

	/**
	 * Clones this feature model <code>f</code>, such that a new instance <code>f'</code> is created. The cloned feature model <code>f'</code> must satisfy the
	 * following properties to contain the same information as <code>f</code>: <ul> <li>the identifiers of <code>f</code> and <code>f'</code> must be
	 * identical</li> <li>the feature order list of <code>f</code> and <code>f'</code> must be equal, but the references must be different</li> <li>the user
	 * defined feature order flag of <code>f</code> and <code>f'</code> must be identical</li> <li>the feature models properties must be equal but with
	 * different references in <code>f</code> and <code>f'</code></li> <li>the feature models constraints must be equal but with different references in
	 * <code>f</code> and <code>f'</code></li> <li>the cloned feature model <code>f'</code> must contain the structure behind <code>newRoot</code> but with
	 * different references</li> <li>the cloned feature model <code>f'</code>'s root feature must be <code>newRoot</code></li> </ul> Additionally, the following
	 * must hold <code>f != f'</code> and <code>f.equals(f')</code>.
	 *
	 * @param newRoot the new root feature including the entire structure of <code>newRoot</code> for the cloned model
	 * @return A new equal instance of this feature model with different references and <code>newRoot</code> as root feature
	 *
	 * @since 3.0
	 */
	IFeatureModel clone(IFeature newRoot);

	/**
	 * Clones this feature model <code>f</code>, such that a new instance <code>f'</code> is created. The cloned feature model <code>f'</code> must satisfy the
	 * following properties to contain the same information as <code>f</code>: <ul> <li>the identifiers of <code>f</code> and <code>f'</code> must be
	 * identical</li> <li>the feature order list of <code>f</code> and <code>f'</code> must be equal, but the references must be different</li> <li>the user
	 * defined feature order flag of <code>f</code> and <code>f'</code> must be identical</li> <li>the feature models properties must be equal but with
	 * different references in <code>f</code> and <code>f'</code></li> <li>the feature models constraints must be equal but with different references in
	 * <code>f</code> and <code>f'</code></li> <li>the cloned feature model <code>f'</code> must contain the structure behind <code>newRoot</code> but with
	 * different references</li> </ul> Additionally, the following must hold <code>f != f'</code> and <code>f.equals(f')</code>.
	 *
	 * @param copyId if {@code true} f' and its features and constraints will have the same internal IDs as f and its features and constraints, if {@code false}
	 *        f' will have a different ID than f, its features and constraints may share IDs with their original counter parts, but do not have to
	 * @return A new equal instance of this feature model with different references
	 *
	 * @see IFeatureModel#clone(IFeature)
	 */
	default IFeatureModel clone(boolean copyId) {
		return clone(null);
	}

	/**
	 * Clones this feature model <code>f</code>, such that a new instance <code>f'</code> is created. The cloned feature model <code>f'</code> must satisfy the
	 * following properties to contain the same information as <code>f</code>: <ul> <li>the identifiers of <code>f</code> and <code>f'</code> must be
	 * identical</li> <li>the feature order list of <code>f</code> and <code>f'</code> must be equal, but the references must be different</li> <li>the user
	 * defined feature order flag of <code>f</code> and <code>f'</code> must be identical</li> <li>the feature models properties must be equal but with
	 * different references in <code>f</code> and <code>f'</code></li> <li>the feature models constraints must be equal but with different references in
	 * <code>f</code> and <code>f'</code></li> <li>the cloned feature model <code>f'</code> must contain the structure behind <code>newRoot</code> but with
	 * different references</li> <li>the cloned feature model <code>f'</code>'s root feature must be <code>newRoot</code></li> </ul> Additionally, the following
	 * must hold <code>f != f'</code> and <code>f.equals(f')</code>.
	 *
	 * @param newRoot the new root feature including the entire structure of <code>newRoot</code> for the cloned model
	 * @param copyId if {@code true} f' and its features and constraints will have the same internal IDs as f and its features and constraints, if {@code false}
	 *        f' will have a different ID than f, its features and constraints may share IDs with their original counter parts, but do not have to
	 * @return A new equal instance of this feature model with different references
	 *
	 * @see IFeatureModel#clone(IFeature)
	 */
	default IFeatureModel clone(IFeature newRoot, boolean copyId) {
		return clone(newRoot);
	}

	/**
	 * Resets this feature model to the default values. The parameter <code>projectName</code> will be used as the new root features name if there are no
	 * features in this model (the <i>feature table</i> is empty). Independent of this, a new feature called <code>Base</code> will be added as child of the
	 * feature models root feature, and the feature models root feature will be set as <i>abstract feature</i>.
	 *
	 * @param projectName the name for the root feature, if this feature model does not contain any features. Otherwise this parameter will be ignored. If
	 *        <code>projectName</code> is an empty string, the string <code>"Root"</code> will be used for the potential new root feature. The parameter
	 *        <code>projectName</code> is assumed to be <i>non-null</i>
	 *
	 * @see #reset()
	 *
	 * @since 3.0
	 */
	void createDefaultValues(CharSequence projectName);

	/**
	 * Removes <code>feature</code> from this model. <code>feature</code> can not be removed, if it is the feature models <i>root</i> feature or if it is not
	 * contained in this model. In both cases, the method returns <b>false</b>. Otherwise the method returns <b>true</b>. <br> <br> Implementations of this
	 * method must ensure, that after removing <code>feature</code>, the feature's <i>parent feature</i> is changed to an <i>and</i> ( <i>or</i>,
	 * <i>alternative</i>) group if <code>feature</code> was an <i>and</i> (<i>or</i>, <i>alternative</i>) group. Additionally, removing <code>feature</code>
	 * has to add the children of <code>feature</code> as children to the <i>parent feature</i>. <br> <br> Removing a feature also removes this feature from the
	 * <i>feature table</i> and the <i>feature order list</i>. Both must be consistent with {@link #getFeatureOrderList()} and {@link #getFeatureOrderList()}
	 * <br> <br> <b>Note</b>If the structure should not be changed, use {@link #deleteFeatureFromTable(IFeature)}
	 *
	 * @param feature the feature that should be removed. It is assumed to be <i>non-null</i>
	 * @return <b>false</b> if <code>feature</code> is the models <i>root</i> feature, or if <code>feature</code> is not contained in this model. Otherwise
	 *         <b>true</b>.
	 *
	 * @see #addFeature(IFeature)
	 * @see #getFeature(CharSequence)
	 * @see #getFeatures()
	 * @see #getNumberOfFeatures()
	 * @see #reset()
	 *
	 * @since 3.0
	 */
	boolean deleteFeature(IFeature feature);

	/**
	 * Removes the feature <code>feature</code> from the <i>feature table</i> by <code>feature</code>'s name with {@link IFeature#getName()}. If the <i>feature
	 * table</i> does not contain a feature with such a name, there will be no changes. <br> <br> This method only affects the collection of features stored in
	 * the feature model, but do not change the <i>structure</i> neither of <code>feature</code> nor it's <i>parent</i> or <i>children</i>. <br> <br>
	 * <b>Note</b> There is no equality check over the identifiers between the feature to be deleted and the feature contained in the collection, expect for
	 * equality in their names. To avoid confusion, this check should be done before calling this method. <br> <b>Note</b> If the structure should be changed,
	 * use {@link #deleteFeature(IFeature)}
	 *
	 * @see #setFeatureTable(Hashtable)
	 * @see #getFeatureTable()
	 *
	 * @param feature the feature (the feature's name) which should be deleted from the <i>feature table</i>
	 *
	 * @since 3.0
	 */
	void deleteFeatureFromTable(IFeature feature);

	/**
	 * @return Returns the number of constraints contained in this feature model.
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 */
	int getConstraintCount();

	/**
	 * Returns the index of the first occurrence of <code>constraint</code> in the collection of constraints, or <b>-1</b> if <code>constraint</code> is not
	 * contained. <br> <br> <b>Note</b>:
	 *
	 * @param constraint the element to be removed. It is assumed that this parameter is <i>non-null</i>
	 * @throws NullPointerException - if <code>constraint</code> is null (optional)
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 *
	 * @return the index of the first occurrence of <code>constraint</code> in the collection of constraints, or <b>-1</b> otherwise.
	 */
	int getConstraintIndex(IConstraint constraint);

	/**
	 * Returns the list of constraints stored in this feature model. <br> <br> <b>Note</b>: The returned list should be <b>unmodifiable</b> to avoid external
	 * access to internal data
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 *
	 * @return All constraints stored in this feature model.
	 */
	List<IConstraint> getConstraints();

	/**
	 * Returns the feature with the given <code>name</code> stored in this feature model, or <code>null</code> if no features can be found. The given
	 * <code>name</code> is compared to the names of the contained features in a <i>case-sensitive</i> manner. Therefore <code>"FeatureA"</code> is unequal to
	 * <code>featureA</code>.
	 *
	 * @param name the name (case sensitive) of the feature which should be return. This parameter is assumed to be non-null.
	 *
	 * @see #addFeature(IFeature)
	 * @see #deleteFeature(IFeature)
	 * @see #getFeatures()
	 * @see #getNumberOfFeatures()
	 * @see #reset()
	 *
	 * @since 3.0
	 *
	 * @return the associated feature, if there is a match to <code>name</code>, or <b>null</b> otherwise.
	 */
	IFeature getFeature(CharSequence name);

	IFeatureModelElement getElement(long id);

	/**
	 * Returns the ordered collection of feature names according to the given feature order. If an order is given, the method returns the corresponding list of
	 * feature names according to their order. If no order is set, the method returns the names of features according to a pre-order traversation of the root
	 * feature's structure. In both cases, the resulting collection is <b>unmodifiable</b>.
	 *
	 * @see #setFeatureOrderList(List)
	 * @see #setFeatureOrderListItem(int, String)
	 * @see #setFeatureOrderUserDefined(boolean)
	 *
	 * @since 3.0
	 *
	 * @return an ordered list of feature names, either as a given order or in pre-order by traversing the root-feature.
	 */
	List<String> getFeatureOrderList();

	/**
	 * Returns the a read-only iterable collection of features stored in this feature model. This method is intend to provide the iteration-concept directly.
	 * <br> <br> <b>Example</b> <code> for (IFeature feature : featureModel.getFeatures()) { // ... } </code> If a list interface is required rather than the
	 * iterable counterpart, the utility class {@link Functional} provides a set of useful methods. To convert the iterator directly into a list, use
	 * {@link Functional#toList(Iterable)}. By using methods from the {@link Functional} utility class the advantages of a functional-like programming style can
	 * be directly used. For instance, to convert the collection of features inside a feature model into a set of feature names, the following code snippet can
	 * be used: <br><br><code> import static de.ovgu.featureide.fm.core.functional.Functional.*;
	 *
	 * <br>Set&lt;String&gt; featureNames = new HashSet&lt;&gt;(toList(mapToString(fm.getFeatures()))) </code> <br>If modification is required, use the related
	 * constructor for collection implementations, e.g., <br> <code>List&lt;IFeature&gt; list = new
	 * LinkedList&lt;IFeature&gt;(Functional.toList(fm.getFeatures()));</code> <br> <b>Note</b>: Many operations of features in feature models runs over
	 * iteration. This method returns an iterator rather than a collection for <i>lazy evaluation</i> purposes. <br>
	 *
	 * @see Functional FeatureIDE functional helper class
	 * @see #addFeature(IFeature)
	 * @see #deleteFeature(IFeature)
	 * @see #getFeature(CharSequence)
	 * @see #getNumberOfFeatures()
	 * @see #reset()
	 *
	 * @since 3.0
	 *
	 * @return features of the feature model.
	 */
	Collection<IFeature> getFeatures();

	/**
	 * Returns the a read-only iterable collection of features stored in this feature model, which are not collapsed. This method is intend to provide the
	 * iteration-concept directly. <br> <br> <b>Example</b> <code> for (IFeature feature : featureModel.getVisibleFeatures()) { // ... } </code> If a list
	 * interface is required rather than the iterable counterpart, the utility class {@link Functional} provides a set of useful methods. To convert the
	 * iterator directly into a list, use {@link Functional#toList(Iterable)}. By using methods from the {@link Functional} utility class the advantages of a
	 * functional-like programming style can be directly used. For instance, to convert the collection of features inside a feature model into a set of feature
	 * names, the following code snippet can be used: <code> import static de.ovgu.featureide.fm.core.functional.Functional.*;
	 *
	 * Set&lt;String&gt; featureNames = new HashSet&lt;&gt;(toList(mapToString(fm.getVisibleFeatures()))) </code> If modification is required, use the related
	 * constructor for collection implementations, e.g., <br> <code>List&lt;IFeature&gt; list = new
	 * LinkedList&lt;IFeature&gt;(Functional.toList(fm.getVisibleFeatures()));</code> <br> <b>Note</b>: Many operations of features in feature models runs over
	 * iteration. This method returns an iterator rather than a collection for <i>lazy evaluation</i> purposes. <br>
	 *
	 *
	 * @see Functional FeatureIDE functional helper class
	 * @see #addFeature(IFeature)
	 * @see #deleteFeature(IFeature)
	 * @see #getFeature(CharSequence)
	 * @see #getNumberOfFeatures()
	 * @see #reset()
	 *
	 * @since 3.3
	 *
	 * @return A iterable of IFeatures, which are not collapsed
	 */
	Iterable<IFeature> getVisibleFeatures();

	/**
	 * Returns the number of features stored in this feature model. This call must be constistent with {@link IFeatureModel#getFeatureTable()} size.
	 *
	 * @see #addFeature(IFeature)
	 * @see #deleteFeature(IFeature)
	 * @see #getFeature(CharSequence)
	 * @see #getFeatures()
	 * @see #reset()
	 *
	 * @since 3.0
	 *
	 * @return number of feature stored in this model
	 */
	int getNumberOfFeatures();

	/**
	 * Returns the model properties attached to this feature model. These properties contain at least <ul> <li>Annotations</li> <li>Comments</li> <li>The
	 * feature order specification</li> </ul> The properties returned by this model is implementation specific and might contain additional properties (see
	 * {@link IFeatureModelProperty}).
	 *
	 * @since 3.0
	 *
	 * @return feature model properties
	 */
	IFeatureModelProperty getProperty();

	/**
	 * @since 3.0
	 *
	 * @return Returns an instance of {@link RenamingsManager} which is bound to this feature model.
	 */
	RenamingsManager getRenamingsManager();

	/**
	 * Returns the feature models {@link IFeatureModelStructure} instance. In this features can be received in preorder, and further structural properties can
	 * be get. For instance, the structure holds information if alternative groups are contained, or the number of or-groups in total. For more information, see
	 * {@link IFeatureModelStructure}.
	 *
	 * @since 3.0
	 *
	 * @return This feature model's structure
	 */
	IFeatureModelStructure getStructure();

	/**
	 * Fires the the event {@link de.ovgu.featureide.fm.core.base.event.FeatureIDEEvent.EventType#MODEL_DATA_CHANGED} to listeners.
	 *
	 * @since 3.0
	 */
	void handleModelDataChanged();

	/**
	 * @since 3.0
	 *
	 * @see #setFeatureOrderUserDefined(boolean)
	 *
	 * @return Returns if a user defined order for features in this model is used.
	 */
	boolean isFeatureOrderUserDefined();

	/**
	 * Removes the first occurrence of <code>constraint</code> from the collection of constraints in this model, if it is present. Otherwise there is no effect
	 * to this model.
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 *
	 * @param constraint The constraint to be removed
	 */
	void removeConstraint(IConstraint constraint);

	/**
	 * Removes the constraint at the specified position <code>index</code> in this collection of constraints in this model. When a constraint was removed, the
	 * remaining constraints to the right are shifted one position to the left.
	 *
	 * @param index position of the constraint to be removed
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @throws IndexOutOfBoundsException If the index is out of range
	 * @since 3.0
	 */
	void removeConstraint(int index);

	/**
	 * Replaces the constraint <code>constraint</code> at the specified position <code>index</code> in the collection of constraints of this feature model.
	 *
	 * @param constraint constraint which should be stored at <code>index</code>
	 * @param index position for replacement
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #setConstraints(Iterable)
	 *
	 * @throws NullPointerException if <code>constraint</code> is <b>null</b>
	 * @throws IndexOutOfBoundsException if the index is out of range
	 *
	 * @since 3.0
	 *
	 */
	void replaceConstraint(IConstraint constraint, int index);

	/**
	 * Set the feature models structure root element to <b>null</b> and clears the collections of features and constraints. Moreover, the feature order list is
	 * cleared and all properties. The next unique element identifier is also reseted to <b>0</b>, such that {@link IFeatureModel#getNextElementId()} will
	 * return <b>0</b>.
	 *
	 * @see #deleteFeature(IFeature)
	 * @see #removeConstraint(int)
	 * @see #removeConstraint(IConstraint)
	 * @see #createDefaultValues(CharSequence)
	 *
	 * @since 3.0
	 */
	void reset();

	/**
	 * Sets the collections of constraints to the ones yielded by <code>constraints</code>. Existing constraint in the collection will be removed before this
	 * operation.
	 *
	 * @param constraints Source of constraints which should be copied into this feature model
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraint(int, IConstraint)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 */
	void setConstraints(final Iterable<IConstraint> constraints);

	/**
	 * Sets the list of feature names for ordering purposed to the content provided by <code>featureOrderList</code>. Existing ordering will be removed before
	 * this operation is executed. There is no check if the feature names provided by <code>featureOrderList</code> actually reflects names of features stored
	 * in this model. <br> <br> The order of strings provided in <code>featureOrderList</code> provide the order of feature names.
	 *
	 * @see #getFeatureOrderList()
	 * @see #setFeatureOrderListItem(int, String)
	 * @see #setFeatureOrderUserDefined(boolean)
	 *
	 * @param featureOrderList the orderd list of feature names which provides the feature order. This parameter is assumed to be <i>non-null</i>
	 *
	 * @since 3.0
	 */
	void setFeatureOrderList(final List<String> featureOrderList);

	/**
	 * Sets a flag that specificities if the feature order in this feature model user defined or not.
	 *
	 * @see #getFeatureOrderList()
	 * @see #setFeatureOrderList(List)
	 * @see #setFeatureOrderListItem(int, String)
	 *
	 * @param featureOrderUserDefined flag to indicate user defined ordering
	 *
	 * @since 3.0
	 */
	void setFeatureOrderUserDefined(boolean featureOrderUserDefined);

	/**
	 * Overwrites the contents of the <i>feature table</i> with the given <code>featureTable</code>. The existing feature table will be cleared and each element
	 * in <code>featureTable</code> will be inserted in the feature model's underlying feature table. There is no check, if the the mapping of features names to
	 * features in <code>featureTable</code> is consistent. Moreover, there is no check if the feature names in <code>featureTable</code> corresponds to the
	 * feature names in this feature model. Therefore, overwriting the contents of the feature table by this function might lead to unexpected behavior, when
	 * not used correctly.
	 *
	 * @see #deleteFeatureFromTable(IFeature)
	 * @see #getFeatureTable()
	 *
	 * @param featureTable New feature table for this feature model. This parameter is assumed to be <i>non-null</i>
	 *
	 * @since 3.0
	 */
	void setFeatureTable(final Hashtable<String, IFeature> featureTable);

	/**
	 * @see #setFeatureTable(Hashtable)
	 * @see #deleteFeatureFromTable(IFeature)
	 *
	 * @return Returns this feature model's underlying <i>feature table</i> as an <b>unmodifiable map</b>.
	 *
	 * @since 3.0
	 */
	Map<String, IFeature> getFeatureTable();

	/**
	 * Clones this feature model <code>f</code> to a new instance of feature model <code>f'</code>, such that <code>f != f'</code> and <code>f.equals(f')</code>
	 * holds. More in detail: <ul> <li>Both feature model's unique identifiers are equal</li> <li>Both feature order lists are equal but their references aren't
	 * identical</li> <li>Both feature order lists user defined order flag is equal</li> <li>Both feature order lists property and structure are equal, but
	 * their references aren't identical</li> <li>Both feature model's source files are equal but their references aren't identical</li> <li>Both feature
	 * model's feature structure (including their constraints) are equal but their references aren't identical</li> <li>The feature model <code>f'</code>'
	 * feature model analyzer instance is a <i>new</i> instance</li> </ul>
	 *
	 * @since 3.0
	 *
	 * @see #getId()
	 * @see #getFeatureOrderList()
	 * @see #isFeatureOrderUserDefined()
	 * @see #getStructure()
	 * @see #getProperty()
	 * @see #getSourceFile()
	 * @see #getStructure()
	 * @see #getConstraints()
	 *
	 * @return cloned instance of this model, such that the new instance is equal to this feature model but their references aren't identical
	 */
	IFeatureModel clone();

	/**
	 * Replaces the feature order item at the specified position <code>i</code> in this feature model's feature order list with the specified element
	 * <code>newName</code>.
	 *
	 * @param i index of the element to replace
	 * @param newName new name to be stored at the specified position
	 *
	 * @see #getFeatureOrderList()
	 * @see #setFeatureOrderList(List)
	 * @see #setFeatureOrderUserDefined(boolean)
	 *
	 * @since 3.0
	 *
	 * @throws IndexOutOfBoundsException if the index is out of range
	 */
	void setFeatureOrderListItem(int i, String newName);

	/**
	 * Set the feature models source file to <code>file</code>. By definition, the feature model's unique identifier is bidirectional mapped to the source
	 * files. Therefore, two feature model's based on the same file must have to same unique identifier. The feature model's identifier will not be changed, if
	 * <code>file</code> is <b>null</b>. <br><br> The default implementation provides this mechanism by using {@link ModelFileIdMap}, such that: <code>
	 * this.sourceFile = file; if (file != null) { id = ModelFileIdMap.getModelId(this, file); } </code> <b>Note</b>: The specification does not require to
	 * reload the content of this feature model, when the source file is changes. Hence, using this method only will affect the return value of
	 * {@link #getSourceFile()} and perhaps {@link #getId()}. However, it is not intended to notify listeners about this change.
	 *
	 * @see #getSourceFile()
	 * @see ModelFileIdMap#getModelId(IFeatureModel, Path)
	 *
	 * @since 3.0
	 *
	 * @param file the source file of this model (might be <b>null</b>.
	 */
	void setSourceFile(Path file);

	/**
	 * @see #setSourceFile(Path)
	 *
	 * @since 3.0
	 *
	 * @return Returns the feature models current source file, or <b>null</b> if no source file is specified.
	 */
	Path getSourceFile();

	/**
	 * Feature models are identified with their system-wide unique numeric identifier. This methods returns the <i>next</i> free identifier of the current
	 * feature model and is a <b>state-full</b> operation, such that invoking the method twice will result in two other numeric identifiers. <br> <br> The
	 * default implementations provides this by the following code snippet: <code> private static long NEXT_ID = 0;
	 *
	 * protected static final synchronized long getNextId() { return NEXT_ID++; } </code> <b>Notes to thread-safe execution</b>: The management of receiving the
	 * next free identifier must be thread-safe.
	 *
	 * @see #getId()
	 *
	 * @since 3.0
	 *
	 * @return the next free system-wide unique identifier for feature models
	 */
	long getNextElementId();

	/**
	 * Overwrites the constraint stored in this feature model at position <code>index</code> with the constraint provided by the parameter
	 * <code>constraint</code>.
	 *
	 * @param index index of the constraint to replace
	 * @param constraint constraint to be stored at the specified position
	 *
	 * @see #addConstraint(IConstraint)
	 * @see #addConstraint(IConstraint, int)
	 * @see #getConstraintCount()
	 * @see #getConstraintIndex(IConstraint)
	 * @see #getConstraints()
	 * @see #removeConstraint(IConstraint)
	 * @see #removeConstraint(int)
	 * @see #setConstraints(Iterable)
	 * @see #replaceConstraint(IConstraint, int)
	 *
	 * @since 3.0
	 *
	 * @throws IndexOutOfBoundsException if the index is out of range
	 */
	void setConstraint(int index, IConstraint constraint);

	List<IEventListener> getListenerList();

	void setEventManager(IEventManager eventManager);

}
