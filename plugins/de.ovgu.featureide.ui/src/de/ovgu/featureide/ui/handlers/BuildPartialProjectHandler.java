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
package de.ovgu.featureide.ui.handlers;

import static de.ovgu.featureide.fm.core.localization.StringTable.CANT_DERIVE_PARTIAL_PROJECT_BECAUSE_IT_DOES_NOT_BELONG_TO_A_FEATURE_PROJECT;

import org.eclipse.core.resources.IFile;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.job.PartialFeatureProjectJob;
import de.ovgu.featureide.fm.core.job.LongRunningJob;
import de.ovgu.featureide.fm.ui.handlers.base.AFileHandler;
import de.ovgu.featureide.ui.UIPlugin;

/**
 * This class handles the event that is triggered when you build a partial configuration with a configuration file.
 *
 * @author Paul Westphal
 */
public class BuildPartialProjectHandler extends AFileHandler {

	@Override
	protected void singleAction(IFile file) {
		final IFeatureProject project = CorePlugin.getFeatureProject(file);
		if (project == null) {
			UIPlugin.getDefault().logWarning(CANT_DERIVE_PARTIAL_PROJECT_BECAUSE_IT_DOES_NOT_BELONG_TO_A_FEATURE_PROJECT);
		} else {
			new LongRunningJob<>("Creating Partial Feature Project", new PartialFeatureProjectJob(file)).schedule();
		}
	}
}
