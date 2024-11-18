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
package de.ovgu.featureide.ahead;

import static de.ovgu.featureide.fm.core.localization.StringTable.AHEAD_INSTANCE_NOT_INITIALIZED;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Scanner;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;

import de.ovgu.featureide.ahead.wrapper.AheadBuildErrorEvent;
import de.ovgu.featureide.ahead.wrapper.AheadBuildErrorListener;
import de.ovgu.featureide.ahead.wrapper.AheadWrapper;
import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.ComposerExtensionClass;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.io.EclipseFileSystem;

/**
 * Composes source jak files into merged jak files.
 *
 * @author Tom Brosch
 */
public class AheadComposer extends ComposerExtensionClass {

	public static final String COMPOSER_ID = "de.ovgu.featureide.composer.ahead";

	public static final String OLD_BUILD_COMMAND = "FeatureIDE_Core.jakBuilder";

	private static final String LAYER_REPLACING = "LAYER_REPLACING";

	private AheadWrapper ahead;

	private class BuilderErrorListener implements AheadBuildErrorListener {

		@Override
		public void parseErrorFound(AheadBuildErrorEvent event) {
			if (featureProject != null) {
				featureProject.createBuilderMarker(event.getResource(), event.getMessage(), event.getLine(), IMarker.SEVERITY_ERROR);
			}
		}
	}

	@Override
	public boolean initialize(IFeatureProject project) {
		super.initialize(project);
		ahead = new AheadWrapper(project);
		ahead.addBuildErrorListener(new BuilderErrorListener());

		return true;
	}

	@Override
	public void performFullBuild(java.nio.file.Path config) {
		assert (ahead != null) : AHEAD_INSTANCE_NOT_INITIALIZED;
		try {
			correctSourceFiles(featureProject.getSourceFolder());
			ahead.setConfiguration((IFile) EclipseFileSystem.getResource(config));
			ahead.buildAll();
		} catch (final Exception e) {
			AheadCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * Iterated through all jak files of the source folder.<br><br>
	 *
	 * The first line of a jak file must not start with imports.<br> Removes derived layer declarations.
	 *
	 * @param folder
	 * @throws CoreException
	 */
	private void correctSourceFiles(IFolder folder) throws CoreException {
		for (final IResource res : folder.members()) {
			if (res instanceof IFolder) {
				correctSourceFiles((IFolder) res);
			} else if (res instanceof IFile) {
				if (extensions().contains(res.getFileExtension())) {
					correctSourceFile((IFile) res);
				}
			}
		}
	}

	/**
	 * Corrects the given source jak file.<br><br>
	 *
	 * The first line of a jak file must not start with imports.<br> Removes derived layer declarations.
	 *
	 * @param file
	 */
	private void correctSourceFile(IFile file) {
		String text = getFileText(file);
		if (text != null) {
			text = correctFileText(text);
			if (text != null) {
				setFileText(file, text);
			}
		}
	}

	/**
	 * Corrects the given file content of the source jak file.<br><br>
	 *
	 * The first line of a jak file must not start with imports.<br> Removes derived layer declarations.
	 *
	 * @param fileContent The file content.
	 * @return return
	 */
	public static String correctFileText(String fileContent) {
		boolean changed = false;
		if (fileContent.startsWith("import ")) {
			changed = true;
			fileContent = NEWLINE + fileContent;
		}
		if (!fileContent.equals(fileContent.replaceFirst("layer\\s+\\w+\\s*;", ""))
			&& (fileContent.replaceFirst("layer\\s+\\w+\\s*;", LAYER_REPLACING).indexOf(LAYER_REPLACING) < fileContent.indexOf('{'))) {
			return fileContent.replaceFirst("layer\\s+\\w+\\s*;", "");
		} else if (changed) {
			return fileContent;
		}
		return null;
	}

	/**
	 * Returns the content of a file.
	 *
	 * @param file
	 * @return the file content
	 */
	private String getFileText(IFile file) {
		Scanner scanner = null;
		try {
			final StringBuffer fileText = new StringBuffer();
			scanner = new Scanner(file.getRawLocation().toFile(), "UTF-8");
			while (scanner.hasNext()) {
				fileText.append(scanner.nextLine());
				fileText.append(NEWLINE);
			}
			return fileText.toString();
		} catch (final FileNotFoundException e) {
			AheadCorePlugin.getDefault().logError(e);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
		}
		return null;
	}

	/**
	 * Sets the content of a file.
	 *
	 * @param file
	 * @param content
	 */
	private void setFileText(IFile file, String content) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(file.getRawLocation().toFile());
			fw.write(content);
			try {
				file.refreshLocal(IResource.DEPTH_ZERO, null);
			} catch (final CoreException e) {
				AheadCorePlugin.getDefault().logError(e);
			}
		} catch (final IOException e) {
			AheadCorePlugin.getDefault().logError(e);
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (final IOException e) {
					AheadCorePlugin.getDefault().logError(e);
				}
			}
		}
	}

	private static final LinkedHashSet<String> EXTENSIONS = createExtensions();

	private static LinkedHashSet<String> createExtensions() {
		final LinkedHashSet<String> extensions = new LinkedHashSet<String>();
		extensions.add("jak");
		return extensions;
	}

	@Override
	public LinkedHashSet<String> extensions() {
		return EXTENSIONS;
	}

	/**
	 * Renames all java-files into jak-files and replaces "package" by "layer"
	 */
	@Override
	public boolean postAddNature(IFolder source, IFolder destination) {
		try {
			for (final IResource res : source.members()) {
				if (res instanceof IFolder) {
					performRenamings(source);
				} else {
					if (res instanceof IFile) {
						final IFile file = (IFile) res;
						if (file.getName().endsWith(".java")) {
							res.move(source.getFile(file.getName().replaceFirst(".java", ".jak")).getFullPath(), true, null);
						}
					}
				}
			}

		} catch (final CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
		return false;
	}

	private void performRenamings(IFolder folder) throws CoreException {
		for (final IResource res : folder.members()) {
			if (res instanceof IFolder) {
				performRenamings((IFolder) res);
			} else if (res instanceof IFile) {
				final IFile file = (IFile) res;
				if (file.getName().endsWith(".java")) {
					performRenamings(file);
					res.move(folder.getFile(file.getName().replaceFirst(".java", ".jak")).getFullPath(), true, null);
				}
			}

		}
	}

	private void performRenamings(IFile iFile) {
		FileWriter fw = null;
		try {
			final File file = iFile.getRawLocation().toFile();
			final StringBuilder fileTextBuffer = new StringBuilder();
			final Scanner scanner = new Scanner(file, "UTF-8");
			while (scanner.hasNext()) {
				fileTextBuffer.append(scanner.nextLine() + NEWLINE);
			}
			scanner.close();

			final String fileText = fileTextBuffer.toString().replaceFirst("package", "layer");
			fw = new FileWriter(file);
			fw.write(fileText);
		} catch (final FileNotFoundException e) {
			AheadCorePlugin.getDefault().logError(e);
		} catch (final IOException e) {
			AheadCorePlugin.getDefault().logError(e);
		} finally {
			try {
				if (fw != null) {
					fw.close();
				}
			} catch (final IOException e) {
				AheadCorePlugin.getDefault().logError(e);
			}
		}
	}

	@Override
	public void buildFSTModel() {
		performFullBuild(null);
	}

	@Override
	public ArrayList<String[]> getTemplates() {
		return TEMPLATES;
	}

	private static final ArrayList<String[]> TEMPLATES = createTempltes();

	private static ArrayList<String[]> createTempltes() {
		final ArrayList<String[]> list = new ArrayList<String[]>(1);
		list.add(new String[] { "Jak", "jak", "/**" + NEWLINE + " * TODO description" + NEWLINE + " */" + NEWLINE + "public " + REFINES_PATTERN + " class "
			+ CLASS_NAME_PATTERN + " {" + NEWLINE + NEWLINE + "}" });
		return list;
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

	@Override
	public boolean refines() {
		return true;
	}

	@Override
	public void postCompile(IResourceDelta delta, IFile file) {
		super.postCompile(delta, file);
		if ((ahead != null) && file.getName().endsWith(".java")) {
			ahead.postCompile(file);
		}
	}

	@Override
	public void addCompiler(IProject project, String sourcePath, String configPath, String buildPath) {
		super.addCompiler(project, sourcePath, configPath, buildPath);
		addSettings(project);
		removeOldBuildCommand(project);
	}

	/**
	 * Removes the old build command from project setup. "FeatureIDE_Core.jakBuilder"
	 *
	 * @param project
	 */
	private void removeOldBuildCommand(IProject project) {
		try {
			final IProjectDescription description = project.getDescription();
			final LinkedList<ICommand> newCommandList = new LinkedList<ICommand>();
			for (final ICommand command : description.getBuildSpec()) {
				if (command.getBuilderName().equals(COMPOSER_ID)) {
					newCommandList.addFirst(command);
				}
				if (!command.getBuilderName().equals(OLD_BUILD_COMMAND)) {
					newCommandList.add(command);
				}
			}
			final ICommand[] newCommandArray = new ICommand[newCommandList.size()];
			int i = 0;
			for (final ICommand c : newCommandList) {
				newCommandArray[i] = c;
				i++;
			}
			description.setBuildSpec(newCommandArray);
			project.setDescription(description, null);
		} catch (final CoreException ex) {}
	}

	// TODO this should be done with external classes
	private void addSettings(IProject project) {
		final IFolder settingsFolder = project.getFolder(".settings");
		if (!settingsFolder.exists()) {
			try {
				settingsFolder.create(true, true, null);
			} catch (final CoreException e) {
				AheadCorePlugin.getDefault().logError(e);
			}
		}
		final IFile settingsFile = settingsFolder.getFile("org.eclipse.jdt.core.prefs");
		if (!settingsFile.exists()) {
			final String text = "eclipse.preferences.version=1" + NEWLINE + "org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled" + NEWLINE
				+ "org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.6" + NEWLINE + "org.eclipse.jdt.core.compiler.codegen.unusedLocal=preserve" + NEWLINE
				+ "org.eclipse.jdt.core.compiler.compliance=1.6" + NEWLINE + "org.eclipse.jdt.core.compiler.debug.lineNumber=generate" + NEWLINE
				+ "org.eclipse.jdt.core.compiler.debug.localVariable=generate" + NEWLINE + "org.eclipse.jdt.core.compiler.debug.sourceFile=generate" + NEWLINE
				+ "org.eclipse.jdt.core.compiler.problem.assertIdentifier=error" + NEWLINE + "org.eclipse.jdt.core.compiler.problem.enumIdentifier=error"
				+ NEWLINE + "org.eclipse.jdt.core.compiler.source=1.6" + NEWLINE + "org.eclipse.jdt.core.builder.resourceCopyExclusionFilter=*.jak";
			final InputStream source = new ByteArrayInputStream(text.getBytes(Charset.availableCharsets().get("UTF-8")));
			try {
				settingsFile.create(source, true, null);
			} catch (final CoreException e) {
				AheadCorePlugin.getDefault().logError(e);
			}
		}
	}

	@Override
	public void buildConfiguration(IFolder folder, Configuration configuration, String configurationName) {
		super.buildConfiguration(folder, configuration, configurationName);
		ahead.setCompositionFolder(folder);
		try {
			ahead.setConfiguration(folder.getFile(configurationName + "." + getConfigurationFormat().getSuffix()));
			ahead.buildAll();
		} catch (final Exception e) {
			AheadCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * AHEAD causes some errors if it is called parallel
	 */
	@Override
	public boolean canGeneratInParallelJobs() {
		return false;
	}

	@Override
	public String[] getCompositionMechanisms() {
		return new String[] { "Mixin", "Jampack" };
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
