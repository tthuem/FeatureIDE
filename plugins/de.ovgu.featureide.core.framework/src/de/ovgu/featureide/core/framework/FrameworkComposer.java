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
package de.ovgu.featureide.core.framework;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.ComposerExtensionClass;
import de.ovgu.featureide.core.framework.activator.FrameworkCorePlugin;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.configuration.Configuration;

/**
 * Framework composer updating .classpath file of eclipse
 *
 * @author Daniel Hohmann
 * @author Sebastian Krieter
 *
 */
public class FrameworkComposer extends ComposerExtensionClass {

	private FrameworkModelBuilder modelBuilder = null;

	private Set<String> selectedFeatures;

	@Override
	public Mechanism getGenerationMechanism() {
		return null;
	}

	@Override
	public void copyNotComposedFiles(Configuration c, IFolder destination) {
		/** Should do nothing, otherwise will copy jar files to src folder **/
	}

	private boolean isProject(IResource res) {
		return res.isAccessible() && (res instanceof IFolder) && ((IFolder) res).getFile(".project").isAccessible();
	}

	private void createJARs() {
		final IFolder jarFolder = getJarFolder();
		final IFolder sourceFolder = featureProject.getSourceFolder();
		try {
			Arrays.stream(sourceFolder.members()).filter(this::isProject).map(res -> (IFolder) res).forEach(project -> {
				final String projectName = project.getName();

				/** clean jar **/
				final String jarName = projectName + ".jar";
				final IFile oldJar = jarFolder.getFile(jarName);
				if (oldJar.exists()) {
					try {
						oldJar.delete(false, null);
					} catch (final CoreException e) {
						createMarker(jarFolder, "Old JAR " + jarName + " could not be deleted.");
						return;
					}
				}

				/** build jar **/
				final IFile infoFile = project.getFile("info.xml");
				if (infoFile.isAccessible()) {
					final IFolder bin = project.getFolder("bin");
					final String path = jarFolder.getLocation().append(FileSystems.getDefault().getSeparator() + jarName).toString();
					try (JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(path))) {
						FrameworkJarCreator.addToJar(jarStream, bin);
						FrameworkJarCreator.addFileToJar(jarStream, infoFile, "");
					} catch (final IOException | CoreException e) {
						createMarker(project, "JAR " + jarName + " could not be built.");
					}
				} else {
					createMarker(project, "No valid info.xml found for feature " + projectName);
				}
			});
			jarFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (final CoreException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
	}

	private static void createMarker(final IFolder sourceFolder, String message) {
		try {
			final IMarker marker = sourceFolder.createMarker("de.ovgu.featureide.core.featureModuleMarker");
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.LOCATION, "1");
			marker.setAttribute(IMarker.MESSAGE, message);
		} catch (final CoreException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public void buildFSTModel() {
		try {
			if (modelBuilder == null) {
				modelBuilder = new FrameworkModelBuilder(featureProject);
			}
			modelBuilder.buildModel();
		} catch (final CoreException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public void performFullBuild(Path configPath) {
		final Configuration configuration = featureProject.loadConfiguration(configPath);
		if (configuration == null) {
			return;
		}

		selectedFeatures = configuration.getSelectedFeatures().stream() //
				.filter(f -> f.getStructure().isConcrete()) //
				.map(IFeature::getName) //
				.collect(Collectors.toCollection(HashSet::new));

		final IProject project = featureProject.getProject();
		try {
			project.deleteMarkers("de.ovgu.featureide.core.featureModuleMarker", true, IResource.DEPTH_INFINITE);
			featureProject.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, null);
		} catch (final CoreException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
		createSubprojects(featureProject.getProjectName());
		createJARs();

		setBuildpaths(project);

		buildFSTModel();
		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (final CoreException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}

		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (final CoreException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * Creates sub-projects inside feature folder depending on selected features
	 */
	private void createSubprojects(String parentProjectName) {
		for (final String featureName : selectedFeatures) {
			final IFolder features = featureProject.getSourceFolder();
			final IFolder subproject = features.getFolder(featureName);
			if (!subproject.exists()) {
				try {
					FrameworkProjectCreator.createSubprojectFolder(parentProjectName + "-" + featureName, subproject);
				} catch (final CoreException e) {
					FrameworkCorePlugin.getDefault().logError(e);
				}
			} else {
				if (!subproject.getFile(".project").exists()) {
					try {
						FrameworkProjectCreator.createSubprojectFolder(parentProjectName + "-" + featureName, subproject);
					} catch (final CoreException e) {
						FrameworkCorePlugin.getDefault().logError(e);
					}
				}
			}
			try {
				features.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (final CoreException e) {
				FrameworkCorePlugin.getDefault().logError(e);
			}
		}
	}

	private boolean isFeatureLib(IPath path) {
		final IPath pluginFolder = getJarFolder().getFullPath();
		return pluginFolder.isPrefixOf(path);
	}

	private IFolder getJarFolder() {
		return featureProject.getProject().getFolder("lib");
	}

	/**
	 * Update .classpath file
	 *
	 * @param project the project
	 */
	private void setBuildpaths(IProject project) {

		try {
			final IJavaProject javaProject = JavaCore.create(project);
			final IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
			final List<IClasspathEntry> entries = new ArrayList<>();
			/** copy existing non-feature entries **/
			for (int i = 0; i < oldEntries.length; i++) {
				if (oldEntries[i].getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
					final IPath path = oldEntries[i].getPath();
					if (!isFeatureLib(path)) {
						entries.add(oldEntries[i]);
					}
				} else {
					entries.add(oldEntries[i]);
				}
			}

			/** add selected features **/
			try {
				for (final IResource res : getJarFolder().members()) {
					final String featureName = res.getName().substring(0, res.getName().indexOf("."));
					if (selectedFeatures.contains(featureName)) {
						final IClasspathEntry newLibraryEntry = JavaCore.newLibraryEntry(res.getFullPath(), null, null);
						entries.add(newLibraryEntry);
					}
				}
			} catch (final CoreException e) {
				FrameworkCorePlugin.getDefault().logError(e);
			}

			final IClasspathEntry[] result = entries.toArray(new IClasspathEntry[0]);
			javaProject.setRawClasspath(result, null);
		} catch (final JavaModelException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public boolean initialize(IFeatureProject project) {
		if (super.initialize(project)) {
			return copyRequiredFiles(project);
		}
		return false;
	}

	/**
	 * Copies needed files to project folder<br> <ul> <li>Called every time when a framework project does not contain pluginLoader or config file</li> </ul>
	 *
	 * @param project
	 *
	 * @return <code>true</code> if files are created without a problem
	 */
	private boolean copyRequiredFiles(IFeatureProject project) {

		/** Copy plugin loader **/
		InputStream inputStream = null;
		try {
			inputStream = FileLocator.openStream(FrameworkCorePlugin.getDefault().getBundle(),
					new org.eclipse.core.runtime.Path("resources" + FileSystems.getDefault().getSeparator() + "PluginLoader.java"), false);
		} catch (final IOException e) {
			FrameworkCorePlugin.getDefault().logError(e);
		}
		final IFolder folder = featureProject.getBuildFolder();
		final IFolder loaderFolder = folder.getFolder("loader");
		if (!loaderFolder.exists()) {
			try {
				loaderFolder.create(true, true, null);
				loaderFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (final CoreException e) {
				FrameworkCorePlugin.getDefault().logError(e);
			}
		}
		final IFile pluginLoader = loaderFolder.getFile("PluginLoader.java");
		if (!pluginLoader.exists()) {
			try {
				pluginLoader.create(inputStream, true, null);
			} catch (final CoreException e) {
				FrameworkCorePlugin.getDefault().logError(e);
			}
		}
		/** Create jar folder **/
		final IFolder jarFolder = project.getProject().getFolder("lib");
		if (!jarFolder.exists()) {
			try {
				jarFolder.create(true, true, null);
				jarFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (final CoreException e) {
				FrameworkCorePlugin.getDefault().logError(e);
			}
		}
		return true;

	}

	@Override
	public void postCompile(IResourceDelta delta, IFile buildFile) {}

	@Override
	public boolean clean() {
		return false;
	}

}
