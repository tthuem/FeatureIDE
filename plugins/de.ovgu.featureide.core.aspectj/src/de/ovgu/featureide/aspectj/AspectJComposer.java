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
package de.ovgu.featureide.aspectj;

import static de.ovgu.featureide.fm.core.localization.StringTable.EMPTY___;
import static de.ovgu.featureide.fm.core.localization.StringTable.ERROR_WHILE_CREATING_FEATURE_MODEL;
import static de.ovgu.featureide.fm.core.localization.StringTable.IS_NOT_INSTALLED_;
import static de.ovgu.featureide.fm.core.localization.StringTable.RESTRICTION;
import static de.ovgu.featureide.fm.core.localization.StringTable.THE_REQUIRED_BUNDLE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaProject;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.ComposerExtensionClass;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.io.EclipseFileSystem;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;

/**
 * Excludes unselected aspects form buildpath.
 *
 * @author Jens Meinicke
 * @author Marcus Pinnecke (Feature Interface)
 */
// implement buildconfiguration
@SuppressWarnings(RESTRICTION)
public class AspectJComposer extends ComposerExtensionClass {

	private static final String PLUGIN_ID = "org.eclipse.ajdt";
	private static final String PLUGIN_WARNING = THE_REQUIRED_BUNDLE + PLUGIN_ID + IS_NOT_INSTALLED_;
	private static final String ASPECTJ_NATURE = "org.eclipse.ajdt.ui.ajnature";

	private static final String NEW_ASPECT = "\t// TODO Auto-generated aspect" + NEWLINE;

	public static final IPath ASPECTJRT_CONTAINER = new Path("org.eclipse.ajdt.core.ASPECTJRT_CONTAINER");

	private static final String BUILDER_AJ = "core.eclipse.ajdt.core.ajbuilder";

	private static final Object BUILDER_JAVA = "org.eclipse.jdt.core.javabuilder";

	private LinkedList<String> unSelectedFeatures;
	private IFeatureModel featureModel;
	private IFeatureModelFactory fmFactory;
	private boolean hadAspectJNature;

	private static final LinkedHashSet<String> EXTENSIONS = createExtensions();

	private static LinkedHashSet<String> createExtensions() {
		final LinkedHashSet<String> extensions = new LinkedHashSet<String>();
		extensions.add("java");
		return extensions;
	}

	@Override
	public LinkedHashSet<String> extensions() {
		return EXTENSIONS;
	}

	@Override
	public void performFullBuild(java.nio.file.Path config) {
		if (config == null) {
			return;
		}
		assert (featureProject != null) : "Invalid project given";
		IStatus stat;
		if ((stat = isComposable()) != Status.OK_STATUS) {
			for (final IStatus child : stat.getChildren()) {
				featureProject.createBuilderMarker(featureProject.getProject(), child.getMessage(), -1, IMarker.SEVERITY_ERROR);
			}
			featureProject.createBuilderMarker(featureProject.getProject(), stat.getMessage(), -1, IMarker.SEVERITY_ERROR);
		}

		final String outputPath = featureProject.getBuildPath();

		if (outputPath == null) {
			return;
		}

		final Configuration configuration = featureProject.loadConfiguration(config);
		if (configuration == null) {
			return;
		}

		final LinkedList<String> selectedFeatures = new LinkedList<>();
		unSelectedFeatures = new LinkedList<>();
		for (final IFeature feature : configuration.getSelectedFeatures()) {
			selectedFeatures.add(feature.getName());
		}
		for (final IFeature feature : FeatureUtils.extractConcreteFeatures(featureProject.getFeatureModel())) {
			if (!selectedFeatures.contains(feature.getName())) {
				unSelectedFeatures.add(feature.getName());
			}
		}

		final IProject project = featureProject.getProject();
		setBuildpaths(project);
		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
			featureProject.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, null);
		} catch (final CoreException e) {
			AspectJCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * Set the unselected aspects to be excluded from build
	 *
	 * @param project
	 */
	private void setBuildpaths(IProject project) {
		String buildPath = null;
		if ((featureProject == null) || (featureProject.getBuildPath() == null)) {
			buildPath = IFeatureProject.DEFAULT_SOURCE_PATH;
		} else {
			buildPath = featureProject.getBuildPath();
		}

		try {
			final JavaProject javaProject = new JavaProject(project, null);
			final IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
			/** check if entries already exist **/
			for (int i = 0; i < oldEntries.length; i++) {
				if (oldEntries[i].getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					/** correct the source entry **/
					oldEntries[i] = setSourceEntry(oldEntries[i]);
					javaProject.setRawClasspath(oldEntries, null);
					return;
				}
			}

			/** add the new entry **/
			final IClasspathEntry[] entries = new IClasspathEntry[oldEntries.length + 1];
			System.arraycopy(oldEntries, 0, entries, 0, oldEntries.length);
			entries[oldEntries.length] = setSourceEntry(getSourceEntry(buildPath));
			javaProject.setRawClasspath(entries, null);
		} catch (final JavaModelException e) {
			CorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * Set the unselected aspect files to be excluded
	 *
	 * @param e The ClasspathEntry to set
	 * @return The set entry
	 */
	private IClasspathEntry setSourceEntry(IClasspathEntry e) {
		final IPath[] excludedAspects = new IPath[unSelectedFeatures.size()];
		int i = 0;
		for (final String f : unSelectedFeatures) {
			excludedAspects[i++] = new Path(f.replaceAll(EMPTY___, "/") + ".aj");
		}
		return new ClasspathEntry(e.getContentKind(), e.getEntryKind(), e.getPath(), e.getInclusionPatterns(), excludedAspects, e.getSourceAttachmentPath(),
				e.getSourceAttachmentRootPath(), null, e.isExported(), e.getAccessRules(), e.combineAccessRules(), e.getExtraAttributes());
	}

	@Override
	public boolean clean() {
		return false;
	}

	@Override
	public void copyNotComposedFiles(Configuration config, IFolder destination) {

	}

	/**
	 * Source files must not set derived.
	 */
	@Override
	public void postCompile(IResourceDelta delta, IFile buildFile) {

	}

	@Override
	public boolean postAddNature(IFolder source, IFolder destination) {
		final IProject project = source.getProject();
		CorePlugin.getDefault().addProject(project);
		addNatures(project);
		setClasspathFile(project);
		if (hadAspectJNature) {
			createFeatureModel(CorePlugin.getFeatureProject(source));
		}
		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (final CoreException e) {
			AspectJCorePlugin.getDefault().logError(e);
		}
		return true;
	}

	private void createFeatureModel(IFeatureProject project) {
		if (project == null) {
			return;
		}
		if (project.getFeatureModel() == null) {
			return;
		}
		featureModel = project.getFeatureModel();
		fmFactory = FMFactoryManager.getInstance().getFactory(featureModel);
		try {
			if (addAspects(project.getBuildFolder(), "")) {
				featureModel.getStructure().getRoot().removeChild(featureModel.getFeature("Base").getStructure());
				final IFeature root = featureModel.getStructure().getRoot().getFeature();
				root.setName("Base");
				featureModel.getStructure().setRoot(root.getStructure());
				featureModel.getStructure().getRoot().setAbstract(false);
				final ProblemList problems =
					SimpleFileHandler.save(EclipseFileSystem.getPath(project.getProject().getFile("model.xml")), featureModel, new XmlFeatureModelFormat());
				if (problems.containsError()) {
					CorePlugin.getDefault().logError(ERROR_WHILE_CREATING_FEATURE_MODEL + "\n" + problems.getErrors().toString(), new Exception());
				}
			}
		} catch (final CoreException e) {
			AspectJCorePlugin.getDefault().logError(e);
		}
	}

	private boolean addAspects(IFolder folder, String folders) throws CoreException {
		boolean hasAspects = false;
		for (final IResource res : folder.members()) {
			if (res instanceof IFolder) {
				hasAspects = addAspects((IFolder) res, folders + res.getName() + EMPTY___);
			} else if (res instanceof IFile) {
				final String name = res.getName();
				if (name.endsWith(".aj")) {
					final IFeature feature = fmFactory.createFeature(featureModel, folders + name.split("[.]")[0]);
					featureModel.getStructure().getRoot().addChild(feature.getStructure());
					hasAspects = true;
				}
			}
		}
		return hasAspects;
	}

	private void setClasspathFile(IProject project) {
		addClasspathFile(project, null);
	}

	@Override
	public void addCompiler(IProject project, String sourcePath, String configPath, String buildPath) {
		addNatures(project);
		addClasspathFile(project, buildPath);
	}

	private void addClasspathFile(IProject project, String buildPath) {
		if (buildPath == null) {
			if ((featureProject == null) || (featureProject.getBuildPath() == null)) {
				buildPath = IFeatureProject.DEFAULT_SOURCE_PATH;
			} else {
				buildPath = featureProject.getBuildPath();
			}
		}

		try {
			final JavaProject javaProject = new JavaProject(project, null);
			final IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
			boolean sourceAdded = false;
			boolean containerAdded = false;
			boolean ajContainerAdded = false;
			/** check if entries already exist **/
			for (int i = 0; i < oldEntries.length; i++) {
				if (!sourceAdded && (oldEntries[i].getEntryKind() == IClasspathEntry.CPE_SOURCE)) {
					/** correct the source entry **/
					oldEntries[i] = getSourceEntry(buildPath);
					sourceAdded = true;
				} else if ((!containerAdded || !ajContainerAdded) && (oldEntries[i].getEntryKind() == IClasspathEntry.CPE_CONTAINER)) {
					/** check the container entries **/
					if (oldEntries[i].getPath().equals(JRE_CONTAINER)) {
						containerAdded = true;
					}
					if (oldEntries[i].getPath().equals(ASPECTJRT_CONTAINER)) {
						ajContainerAdded = true;
					}

				}
			}
			/** case: no new entries **/
			if (sourceAdded && containerAdded && ajContainerAdded) {
				javaProject.setRawClasspath(oldEntries, null);
				return;
			}

			/** add the new entries **/
			final IClasspathEntry[] entries =
				new IClasspathEntry[(sourceAdded ? 0 : 1) + (containerAdded ? 0 : 1) + (ajContainerAdded ? 0 : 1) + oldEntries.length];
			System.arraycopy(oldEntries, 0, entries, 0, oldEntries.length);

			if (!sourceAdded) {
				entries[oldEntries.length] = getSourceEntry(buildPath);
			}
			if (!containerAdded) {
				final int position = (sourceAdded ? 0 : 1) + oldEntries.length;
				entries[position] = getContainerEntry();
			}
			if (!ajContainerAdded) {
				final int position = (sourceAdded ? 0 : 1) + (containerAdded ? 0 : 1) + oldEntries.length;
				entries[position] = getAJContainerEntry();
			}
			javaProject.setRawClasspath(entries, null);
		} catch (final JavaModelException e) {
			CorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * @return The ClasspathEnttry for the AspectJ container
	 */
	private IClasspathEntry getAJContainerEntry() {
		return new ClasspathEntry(IPackageFragmentRoot.K_SOURCE, IClasspathEntry.CPE_CONTAINER, ASPECTJRT_CONTAINER, new IPath[0], new IPath[0], null, null,
				null, false, null, false, new IClasspathAttribute[0]);
	}

	private void addNatures(IProject project) {
		try {
			if (!project.isAccessible()) {
				return;
			}

			int i = 2;
			if (project.hasNature(JAVA_NATURE)) {
				i--;
			}
			hadAspectJNature = project.hasNature(ASPECTJ_NATURE);
			if (hadAspectJNature) {
				i--;
			}
			if (i == 0) {
				return;
			}
			final IProjectDescription description = project.getDescription();
			final String[] natures = description.getNatureIds();
			final String[] newNatures = new String[natures.length + i];
			int j = 2;
			newNatures[0] = ASPECTJ_NATURE;
			newNatures[1] = JAVA_NATURE;
			for (final String nature : natures) {
				if (!(nature.equals(ASPECTJ_NATURE) || nature.equals(JAVA_NATURE))) {
					newNatures[j] = nature;
					j++;
				}
			}
			description.setNatureIds(newNatures);

			/** the java builder has to be replaced with the AspectJ builder **/
			final ICommand[] buildSpec = description.getBuildSpec();
			if (buildSpec.length > 0) {
				final ICommand[] newBuildSpec = new ICommand[buildSpec.length];
				int k = 0;
				for (final ICommand c : buildSpec) {
					if (!c.getBuilderName().equals(BUILDER_JAVA)) {
						newBuildSpec[k] = c;
						k++;
					} else {
						c.setBuilderName(BUILDER_AJ);
						newBuildSpec[k] = c;
					}
				}
				description.setBuildSpec(newBuildSpec);
			}

			project.setDescription(description, null);
		} catch (final CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public ArrayList<String[]> getTemplates() {
		return TEMPLATES;
	}

	private static final ArrayList<String[]> TEMPLATES = createTemplates();

	// TODO add aspect template
	private static ArrayList<String[]> createTemplates() {
		final ArrayList<String[]> list = new ArrayList<String[]>(1);
		list.add(JAVA_TEMPLATE);
		return list;
	}

	private String rootName = "";

	@Override
	public void postModelChanged() {
		try {
			featureProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (final CoreException e) {
			AspectJCorePlugin.getDefault().logError(e);
		} catch (final NullPointerException e) {
			AspectJCorePlugin.getDefault().reportBug(321);
		}
		final IFeature root = featureProject.getFeatureModel().getStructure().getRoot().getFeature();
		if (root == null) {
			return;
		}
		rootName = root.getName();
		if (!"".equals(rootName) && root.getStructure().hasChildren()) {
			checkAspect(root);
		}
	}

	private void checkAspect(IFeature feature) {
		if (feature.getStructure().hasChildren()) {
			for (final IFeatureStructure child : feature.getStructure().getChildren()) {
				if (child.isConcrete() && !child.getFeature().getName().equals(rootName)) {
					createAspect(child.getFeature().getName(), featureProject.getBuildFolder(), null);
				}
				checkAspect(child.getFeature());
			}
		}
	}

	public static IFile getAspectFile(String aspect, String aspectPackage, IFolder folder) {
		final String text = aspect.split("[_]")[0];
		if (aspect.contains(EMPTY___)) {
			if (aspectPackage == null) {
				aspectPackage = text;
			} else {
				aspectPackage = aspectPackage + "." + text;
			}
			return getAspectFile(aspect.substring(text.length() + 1), aspectPackage, folder.getFolder(text));
		}
		try {
			createFolder(folder);
		} catch (final CoreException e) {
			AspectJCorePlugin.getDefault().logError(e);
		}
		return folder.getFile(text + ".aj");
	}

	private void createAspect(String aspect, IFolder folder, String aspectPackage) {
		final IFile aspectFile = getAspectFile(aspect, aspectPackage, folder);
		if ((aspectPackage == null) && aspect.contains(EMPTY___)) {
			aspectPackage = aspect.substring(0, aspect.lastIndexOf('_')).replaceAll(EMPTY___, ".");
			aspect = aspect.substring(aspect.lastIndexOf('_') + 1);
		}
		if (!aspectFile.exists()) {
			String fileText;
			if (aspectPackage != null) {
				fileText = NEWLINE + "package " + aspectPackage + ";" + NEWLINE + NEWLINE + "public aspect " + aspect + " {" + NEWLINE + NEW_ASPECT + "}";
			} else {
				fileText = NEWLINE + "public aspect " + aspect + " {" + NEWLINE + NEW_ASPECT + "}";
			}
			final InputStream source = new ByteArrayInputStream(fileText.getBytes(Charset.availableCharsets().get("UTF-8")));
			try {
				aspectFile.create(source, true, null);
				aspectFile.refreshLocal(IResource.DEPTH_ZERO, null);
			} catch (final CoreException e) {
				// avoid resource already exists error
				// has no negative effect
			}

		}
	}

	public static void createFolder(IFolder folder) throws CoreException {
		if (!folder.exists()) {
			createFolder((IFolder) folder.getParent());
			folder.create(true, true, null);
		}
	}

	@Override
	public boolean createFolderForFeatures() {
		return false;
	}

	@Override
	public boolean hasFeatureFolder() {
		return false;
	}

	@Override
	public Mechanism getGenerationMechanism() {
		return IComposerExtensionClass.Mechanism.ASPECT_ORIENTED_PROGRAMMING;
	}

	@Override
	public boolean supportsMigration() {
		return false;
	}

	@Override
	public IStatus isComposable() {
		if (!isPluginInstalled(PLUGIN_ID)) {
			return new Status(IStatus.ERROR, AspectJCorePlugin.PLUGIN_ID, IStatus.WARNING, PLUGIN_WARNING, null);
		}
		return super.isComposable();
	}

}
