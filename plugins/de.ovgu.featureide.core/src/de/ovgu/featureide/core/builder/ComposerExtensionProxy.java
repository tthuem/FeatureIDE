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
package de.ovgu.featureide.core.builder;

import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;

/**
 * Handles a composer extension.
 *
 * @author Tom Brosch
 */
public class ComposerExtensionProxy implements IComposerExtension {

	private final IConfigurationElement configElement;
	private final String name;
	private final String id;
	private final String description;
	private final Map<IFeatureProject, IComposerExtensionClass> projectComposerMap;
	private IComposerExtensionClass defaultComposerExtensionClass;

	public ComposerExtensionProxy(IConfigurationElement configurationElement) throws Exception {
		configElement = configurationElement;
		name = configElement.getAttribute("name");
		id = configElement.getAttribute("id");
		description = configElement.getAttribute("description");
		projectComposerMap = new WeakHashMap<IFeatureProject, IComposerExtensionClass>();
		try {
			defaultComposerExtensionClass = (IComposerExtensionClass) configElement.createExecutableExtension("class");
		} catch (final CoreException e) {
			CorePlugin.getDefault().logError(e);
			throw e;
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public boolean initExtension() {
		return true;
	}

	@Override
	public String toString() {
		return "Name: " + name + "; ID: " + id;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public IComposerExtensionClass getComposerByProject(IFeatureProject featureProject) {
		IComposerExtensionClass composer = projectComposerMap.get(featureProject);
		if (composer == null) {
			try {
				final ComposerExtensionClass tmpComposer = (ComposerExtensionClass) configElement.createExecutableExtension("class");
				tmpComposer.setComposerExtension(this);
				composer = tmpComposer;
				projectComposerMap.put(featureProject, composer);
			} catch (final CoreException e) {
				CorePlugin.getDefault().logError(e);
			}
		}
		return composer;
	}

	@Override
	public boolean hasFeatureFolder() {
		return defaultComposerExtensionClass.hasFeatureFolder();
	}

	@Override
	public boolean hasSourceFolder() {
		return defaultComposerExtensionClass.hasSourceFolder();
	}

	@Override
	public boolean hasSource() {
		return defaultComposerExtensionClass.hasSource();
	}

	@Override
	public boolean hasContractComposition() {
		return defaultComposerExtensionClass.hasContractComposition();
	}

	@Override
	public boolean hasMetaProductGeneration() {
		return defaultComposerExtensionClass.hasMetaProductGeneration();
	}

	@Override
	public String[] getCompositionMechanisms() {
		return defaultComposerExtensionClass.getCompositionMechanisms();
	}

	@Override
	public boolean createFolderForFeatures() {
		return defaultComposerExtensionClass.createFolderForFeatures();
	}

	@Override
	public boolean supportsAndroid() {
		return defaultComposerExtensionClass.supportsAndroid();
	}

	@Override
	public boolean supportsMigration() {
		return defaultComposerExtensionClass.supportsMigration();
	}

	@Override
	public IStatus isComposable() {
		return defaultComposerExtensionClass.isComposable();
	}

	@Override
	public <T extends IComposerObject> T getComposerObjectInstance(Class<T> c) {
		return defaultComposerExtensionClass.getComposerObjectInstance(c);
	}

	@Override
	public boolean hasBuildFolder() {
		return defaultComposerExtensionClass.hasBuildFolder();
	}

}
