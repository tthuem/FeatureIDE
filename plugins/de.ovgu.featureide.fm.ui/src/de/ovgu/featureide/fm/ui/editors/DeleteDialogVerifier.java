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
package de.ovgu.featureide.fm.ui.editors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jface.dialogs.MessageDialog;

import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.impl.MultiFeature;
import de.ovgu.featureide.fm.core.base.impl.MultiFeatureModel;
import de.ovgu.featureide.fm.core.localization.StringTable;

/**
 * Provides methods to verify, whether a {@link DeleteDialog} is needed
 *
 * @author Rahel Arens
 */
public final class DeleteDialogVerifier {

	public static final String DELETE_WITH_SLICING = StringTable.DELETE_WITH_SLICING;
	public static final String DELETE_WITHOUT_SLICING = StringTable.DELETE_WITHOUT_SLICING;
	public static final String CANCEL = StringTable.CANCEL;

	private DeleteDialogVerifier() {}

	/**
	 * checks, whether a dialog is needed.
	 *
	 * @return Returns null if there is no dialog needed at all, otherwise it returns the button that was pressed in the dialog
	 */
	public static Optional<String> checkForDialog(List<IFeature> featuresToDelete) {
		boolean featureInConstraint = false;
		boolean featureHasGroupDifference = false;
		boolean featureIsRoot = false;
		for (final IFeature feature : featuresToDelete) {
			final List<IConstraint> constraintsToConsider = new LinkedList<>();
			if (feature.getFeatureModel() instanceof MultiFeatureModel) {
				constraintsToConsider.addAll(((MultiFeatureModel) feature.getFeatureModel()).getOwnConstraints());
			} else {
				constraintsToConsider.addAll(feature.getFeatureModel().getConstraints());
			}

			final Collection<IConstraint> relevantConstraints = new LinkedList<>();

			// set relevant constraints
			for (final IConstraint constraint : constraintsToConsider) {
				for (final IFeature f : constraint.getContainedFeatures()) {
					if (f.getName().equals(feature.getName())) {
						relevantConstraints.add(constraint);
						break;
					}
				}
			}

			if (!relevantConstraints.isEmpty()) {
				featureInConstraint = true;
			}
			if (hasGroupDifference(feature)) {
				featureHasGroupDifference = true;
			}
			if (isRootRequiringSlicing(feature, featuresToDelete)) {
				featureIsRoot = true;
			}
		}

		if (featureInConstraint || featureHasGroupDifference || featureIsRoot) {
			// the delete dialog needs to be shown
			final boolean isMultiFeature = isAnyFeatureMultiFeature(featuresToDelete);
			final List<String> dialogReasons = getDialogReasons(featureInConstraint, featureHasGroupDifference, featureIsRoot);
			final String[] dialogButtonLabels = getDialogButtonLabels(featureInConstraint, featureHasGroupDifference, featureIsRoot, isMultiFeature);
			return openDeleteDialog(featuresToDelete.size() > 1, dialogReasons, dialogButtonLabels);
		} else {
			return Optional.of(DeleteDialogVerifier.DELETE_WITHOUT_SLICING);
		}
	}

	/**
	 * Checks if the group of the feature and the group of its parent are of the same type
	 *
	 * @param feature The feature of which the group is compared to the group of its parent
	 * @return <code>true</code> if the group of the feature is different from the group of the parent. <code>false</code> if the group is the same or the
	 *         feature has no parent or no children.
	 */
	private static boolean hasGroupDifference(IFeature feature) {
		final IFeature parent = FeatureUtils.getParent(feature);
		if ((parent == null) || !FeatureUtils.hasChildren(feature)) {
			return false;
		}
		return (FeatureUtils.isOr(feature) && !FeatureUtils.isOr(parent)) || (FeatureUtils.isAlternative(feature) && !FeatureUtils.isAlternative(parent))
			|| (FeatureUtils.isAnd(feature) && !FeatureUtils.isAnd(parent));
	}

	/**
	 * Checks whether the given feature is the root feature, and deleting it requires slicing because there is no unambiguous new root.
	 *
	 * @param feature The feature to check
	 * @param featuresToDelete The list of features to be deleted
	 * @return <code>true</code> iff the given feature is the root, and deleting it requires slicing.
	 */
	private static boolean isRootRequiringSlicing(IFeature feature, List<IFeature> featuresToDelete) {
		if (!FeatureUtils.isRoot(feature)) {
			return false;
		}
		IFeatureStructure fs = feature.getStructure();
		while (fs.getChildrenCount() == 1) {
			fs = fs.getFirstChild();
			if (!featuresToDelete.contains(fs.getFeature())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Gets the reasons for the DeleteDialog
	 *
	 * @param featureInConstraint <code>true</code> if any of the selected features is contained in a constraint, <code>false</code> if not
	 * @param featureHasGroupDifference <code>true</code> if any of the selected features has a different group than its parent, <code>false</code> if not
	 * @param featureIsRoot <code>true</code> if any of the selected features is the root and has multiple children, <code>false</code> if not
	 * @return A List of Strings with reasons for the dialog. These are being displayed in the DeleteDialog
	 */
	private static List<String> getDialogReasons(boolean featureInConstraint, boolean featureHasGroupDifference, boolean featureIsRoot) {
		final List<String> dialogReasons = new ArrayList<>();
		if (featureInConstraint) {
			dialogReasons.add(StringTable.DELETE_FEATURE_REASON_CONSTRAINTS);
		}
		if (featureHasGroupDifference) {
			dialogReasons.add(StringTable.DELETE_FEATURE_REASON_GROUP_DIFFERENCE);
		}
		if (featureIsRoot) {
			dialogReasons.add(StringTable.DELETE_FEATURE_REASON_ROOT);
		}
		return dialogReasons;
	}

	/**
	 * Gets the button labels for the DeleteDialog
	 *
	 * @param featureInConstraint <code>true</code> if any of the selected features is contained in a constraint, <code>false</code> if not
	 * @param featureHasGroupDifference <code>true</code> if any of the selected features has a different group than its parent, <code>false</code> if not
	 * @param featureIsRoot <code>true</code> if any of the selected features is the root and has multiple children, <code>false</code> if not
	 * @return A String array with labels for the buttons of the DeleteDialog
	 */
	private static String[] getDialogButtonLabels(boolean featureInConstraint, boolean featureHasGroupDifference, boolean featureIsRoot,
			boolean isMultiFeature) {
		final List<String> buttonLabels = new ArrayList<>();
		if ((featureInConstraint || featureHasGroupDifference || featureIsRoot) && !isMultiFeature) {
			buttonLabels.add(DELETE_WITH_SLICING);
		}

		if (!featureInConstraint && !featureIsRoot) {
			buttonLabels.add(DELETE_WITHOUT_SLICING);
		}

		buttonLabels.add(CANCEL);
		return buttonLabels.toArray(new String[0]);
	}

	private static boolean isAnyFeatureMultiFeature(List<IFeature> featuresToDelete) {
		for (final IFeature feat : featuresToDelete) {
			if (feat instanceof MultiFeature) {
				return ((MultiFeature) feat).isFromExtern();
			}
		}
		return false;
	}

	/**
	 * Opens the DeleteDialog with the given options
	 *
	 * @param multiple <code>true</code> if multiple features are being deleted, <code>false</code> if not
	 * @param dialogReasons A List of Strings with reasons for the DeleteDialog. These are being displayed in the dialog
	 * @param dialogButtonLabels A String array with labels for the buttons of the DeleteDialog
	 * @return A String containing the label of the button that was pressed in the DeleteDialog or <code>null</code> if the dialog was closed differently
	 */
	protected static Optional<String> openDeleteDialog(boolean multiple, List<String> dialogReasons, String[] dialogButtonLabels) {
		final MessageDialog dialog = new DeleteDialog(null, multiple, dialogReasons, dialogButtonLabels, dialogButtonLabels.length - 1);
		dialog.open();
		final int dialogReturn = dialog.getReturnCode();

		if ((dialogReturn >= 0) && (dialogReturn < dialogButtonLabels.length)) {
			return Optional.of(dialogButtonLabels[dialogReturn]);
		} else {
			return Optional.empty();
		}
	}

}
