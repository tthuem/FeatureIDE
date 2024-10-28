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
package de.ovgu.featureide.core.job;

import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.PartialFeatureProjectBuilder;
import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.io.manager.FileHandler;
import de.ovgu.featureide.fm.core.job.LongRunningMethod;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * This job copies a feature project, waits for the copy to be registered by the Core Plugin, and then runs the Partial Feature Project builder to build a
 * partial feature project.
 *
 * @author Paul Westphal
 */
public class PartialFeatureProjectJob implements LongRunningMethod<Boolean> {

	private final IFile file;

	public PartialFeatureProjectJob(IFile file) {
		this.file = file;
	}

	@Override
	public Boolean execute(IMonitor<Boolean> monitor) throws Exception {
		final IFeatureProject baseProject = CorePlugin.getFeatureProject(file);
		final String newProjectName = baseProject.getProjectName() + "_" + FileHandler.getFileName(file.getName()) + "_" + System.currentTimeMillis();

		try {
			baseProject.getProject().copy(baseProject.getProject().getFullPath().removeLastSegments(1).append(newProjectName), true, null);
		} catch (final CoreException e) {
			Logger.logError(e);
		}

		final IProject newProject = ResourcesPlugin.getWorkspace().getRoot().getProject(newProjectName);
		CorePlugin.getDefault().addProject(newProject);

		IFeatureProject newFeatureProject = CorePlugin.getFeatureProject(newProject);
		while ((newFeatureProject = CorePlugin.getFeatureProject(newProject)) == null) {}
		final Configuration config =
			baseProject.loadConfiguration(Paths.get(file.getLocation().toOSString()));

		LongRunningWrapper.runMethod(new PartialFeatureProjectBuilder(newFeatureProject, config));
		return null;
	}

}
