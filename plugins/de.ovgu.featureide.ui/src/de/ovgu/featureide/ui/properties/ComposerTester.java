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
package de.ovgu.featureide.ui.properties;

import java.util.Objects;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IResource;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.fm.ui.handlers.base.SelectionWrapper;

public class ComposerTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		final IResource res = SelectionWrapper.checkClass(receiver, IResource.class);
		final IFeatureProject featureProject = CorePlugin.getFeatureProject(res);
		if (featureProject != null) {
			switch (property) {
			case "composer":
				return Objects.equals(expectedValue, featureProject.getComposerID());
			case "partialFeatureProjectBuild":
				final IComposerExtensionClass composer = featureProject.getComposer();
				return (composer != null) && composer.supportsPartialFeatureProject();
			default:
				throw new IllegalStateException();
			}
		}
		return false;
	}

}
