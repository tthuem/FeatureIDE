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
package de.ovgu.featureide.featurecpp;

import static de.ovgu.featureide.fm.core.localization.StringTable.IS_NOT_INSTALLED_;
import static de.ovgu.featureide.fm.core.localization.StringTable.THE_REQUIRED_BUNDLE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.ComposerExtensionClass;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.featurecpp.model.FeatureCppModelBuilder;
import de.ovgu.featureide.featurecpp.wrapper.FeatureCppWrapper;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.EquationFormat;
import de.ovgu.featureide.fm.core.io.EclipseFileSystem;
import de.ovgu.featureide.fm.core.io.IConfigurationFormat;

/**
 * A FeatureIDE extension to compose FeatureC++ files.
 *
 * @author Tom Brosch
 * @author Jens Meinicke
 */
public class FeatureCppComposer extends ComposerExtensionClass {

	private static final String PLUGIN_ID = "org.eclipse.cdt";
	private static final String PLUGIN_WARNING = THE_REQUIRED_BUNDLE + PLUGIN_ID + IS_NOT_INSTALLED_;
	public static final String COMPOSER_ID = "de.ovgu.featureide.composer.featurecpp";
	public static final String C_NATURE = "org.eclipse.cdt.core.cnature";
	public static final String CC_NATURE = "org.eclipse.cdt.core.ccnature";

	private static final String NEWLINE = System.getProperty("line.separator", "\n");
	private static final ArrayList<String[]> TEMPLATES = new ArrayList<String[]>(1);

	static {
		TEMPLATES.add(new String[] { "C++", "h", NEWLINE + REFINES_PATTERN + " class " + CLASS_NAME_PATTERN + " {" + NEWLINE + NEWLINE + "};" });
	}

	/**
	 * This wrapper builds the current configuration into the build folder.
	 */
	private final FeatureCppWrapper featureCpp = new FeatureCppWrapper();

	/**
	 * This wrapper builds a complete configuration into the temp folder to generate a full FST model.
	 */
	private final FeatureCppWrapper featureCppModelWrapper = new FeatureCppWrapper();

	/**
	 * This folder called FSTModel is the location where the model wrapper will build the full configuration.
	 */
	private IFolder tempFolder;

	/**
	 * This folder called .tmp contains the full configuration and the temp folder.
	 */
	private IFolder parentFolder;

	private FeatureCppModelBuilder featureCppModelBuilder;

	@Override
	public boolean initialize(IFeatureProject project) {
		super.initialize(project);
		featureCpp.initialize(project.getSourceFolder(), project.getBuildFolder());
		createTempFolder();

		featureCppModelWrapper.initialize(project.getSourceFolder(), tempFolder);

		featureCppModelBuilder = new FeatureCppModelBuilder(project, tempFolder);
		buildFSTModel();

		return true;
	}

	/**
	 * Creates the folders for building a full FST model.<br> {@link FeatureCppComposer#parentFolder}<br> {@link FeatureCppComposer#tempFolder}
	 */
	private void createTempFolder() {
		parentFolder = featureProject.getProject().getFolder(".tmp");
		if (!parentFolder.exists()) {
			try {
				parentFolder.create(true, true, null);
				parentFolder.refreshLocal(IResource.DEPTH_ZERO, null);
			} catch (final CoreException e) {
				FeatureCppCorePlugin.getDefault().logError(e);
			}
		}

		tempFolder = parentFolder.getFolder("FSTModel");
		if (!tempFolder.exists()) {
			try {
				tempFolder.create(true, true, null);
				tempFolder.refreshLocal(IResource.DEPTH_ZERO, null);
			} catch (final CoreException e) {
				FeatureCppCorePlugin.getDefault().logError(e);
			}
		}
	}

	@Override
	public void performFullBuild(java.nio.file.Path config) {
		if (!isPluginInstalled(PLUGIN_ID)) {
			featureProject.createBuilderMarker(featureProject.getProject(), PLUGIN_WARNING, -1, IMarker.SEVERITY_ERROR);
		}
		if (!isInitialized()) {
			final IFeatureProject configFeatureProject = CorePlugin.getFeatureProject(EclipseFileSystem.getResource(config));
			if (configFeatureProject == null) {
				return;
			}
			initialize(configFeatureProject);
		}
		featureCpp.compose(createTemporaryConfigrationFile(config));
		buildFSTModel();
	}

	private static final LinkedHashSet<String> EXTENSIONS = createExtensions();

	private static LinkedHashSet<String> createExtensions() {
		final LinkedHashSet<String> extensions = new LinkedHashSet<String>();
		extensions.add("h");
		return extensions;
	}

	@Override
	public LinkedHashSet<String> extensions() {
		return EXTENSIONS;
	}

	@Override
	public void addCompiler(IProject project, String sourcePath, String configPath, String buildPath) {
		addNature(project);
	}

	private void addNature(IProject project) {
		try {
			if (!project.isAccessible()) {
				return;
			}

			int i = 2;
			if (project.hasNature(C_NATURE)) {
				i--;
			}
			if (project.hasNature(CC_NATURE)) {
				i--;
			}
			if (i == 0) {
				return;
			}
			final IProjectDescription description = project.getDescription();
			final String[] natures = description.getNatureIds();
			final String[] newNatures = new String[natures.length + i];
			System.arraycopy(natures, 0, newNatures, 0, natures.length);
			if (project.hasNature(C_NATURE)) {
				newNatures[natures.length] = CC_NATURE;
			} else if (project.hasNature(CC_NATURE)) {
				newNatures[natures.length] = C_NATURE;
			} else {
				newNatures[natures.length] = C_NATURE;
				newNatures[natures.length + 1] = CC_NATURE;
			}
			description.setNatureIds(newNatures);
			project.setDescription(description, null);
		} catch (final CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public ArrayList<String[]> getTemplates() {
		return TEMPLATES;
	}

	@Override
	public String replaceSourceContentMarker(String text, boolean refines, String packageName) {
		if (refines) {
			text = text.replace(REFINES_PATTERN, "refines");
		} else {
			text = text.replace(REFINES_PATTERN + " ", "");
		}
		return super.replaceSourceContentMarker(text, refines, packageName);
	}

	/*
	 * (non-Javadoc)
	 * @see de.ovgu.featureide.core.builder.ComposerExtensionClass#refines()
	 */
	@Override
	public boolean refines() {
		return true;
	}

	@Override
	public void postCompile(IResourceDelta delta, IFile file) {
		super.postCompile(delta, file);
		try {
			file.refreshLocal(IResource.DEPTH_ZERO, null);
		} catch (final CoreException e) {
			FeatureCppCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public IConfigurationFormat getConfigurationFormat() {
		return new EquationFormat();
	}

	@Override
	public void buildFSTModel() {
		if (!tempFolder.exists()) {
			createTempFolder();
		} else {
			try {
				for (final IResource res : tempFolder.members()) {
					res.delete(true, null);
				}
				tempFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (final CoreException e) {
				FeatureCppCorePlugin.getDefault().logError(e);
			}
		}

		if ((featureProject != null) && (featureProject.getProject() != null)) {
			featureCppModelBuilder.resetModel();
			final StringBuilder stringBuilder = new StringBuilder();
			for (final String name : featureProject.getFeatureModel().getFeatureOrderList()) {
				stringBuilder.append(name);
				stringBuilder.append("\r\n");
			}

			final InputStream source = new ByteArrayInputStream(stringBuilder.toString().getBytes(Charset.availableCharsets().get("UTF-8")));

			final IFile file = parentFolder.getFile("temp." + getConfigurationFormat().getSuffix());
			try {
				if (file.exists()) {
					file.setContents(source, false, true, null);
				} else {
					file.create(source, true, null);
				}
			} catch (final CoreException e) {
				FeatureCppCorePlugin.getDefault().logError(e);
			}
			featureCppModelWrapper.compose(createTemporaryConfigrationFile(EclipseFileSystem.getPath(file)));
			try {
				tempFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (final CoreException e) {
				FeatureCppCorePlugin.getDefault().logError(e);
			}
			featureCppModelBuilder.buildModel();
		}
	}

	@Override
	public void buildConfiguration(IFolder folder, Configuration configuration, String configurationName) {
		super.buildConfiguration(folder, configuration, configurationName);
		featureCpp.initialize(null, folder);
		try {
			for (final IResource res : folder.members()) {
				if ((res instanceof IFile) && getConfigurationFormat().getSuffix().equals(res.getFileExtension())) {
					featureCpp.compose(createTemporaryConfigrationFile(EclipseFileSystem.getPath(res)));
				}
			}
		} catch (final CoreException e) {
			FeatureCppCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public Mechanism getGenerationMechanism() {
		return IComposerExtensionClass.Mechanism.FEATURE_ORIENTED_PROGRAMMING;
	}

	/*
	 * (non-Javadoc)
	 * @see de.ovgu.featureide.core.builder.IComposerExtensionBase#supportsMigration()
	 */
	@Override
	public boolean supportsMigration() {
		return false;
	}

}
