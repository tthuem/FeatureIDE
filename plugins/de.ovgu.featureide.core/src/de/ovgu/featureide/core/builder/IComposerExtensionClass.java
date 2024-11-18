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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Vector;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;

import de.ovgu.featureide.core.fstmodel.preprocessor.FSTDirective;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.io.IConfigurationFormat;
import de.ovgu.featureide.fm.core.io.IFeatureModelFormat;

/**
 * Base interface of all composition tool classes.<br><br>
 *
 * Every plug-in which extends the de.ovgu.featureide.core.composers needs to provide a class, which implements this interface. Implementing this interface
 * ensures that a given composition tool meets the requirements for composition tools, which are used by the ExtensibleFeatureProjectBuilder. This requirements
 * are: - Specifying a path for the composed files (usually "./build") - Specifying a path for the source files (usually "./src") if hasFeatureFolder() -
 * Specifying a path to the current configuration file - Performing a full build for the current project with a given configuration file
 *
 * @author Tom Brosch
 */
public interface IComposerExtensionClass extends IComposerExtensionBase {

	/** Defines the product-line implementation-mechanism of the composition tool **/
	static enum Mechanism {
		FEATURE_ORIENTED_PROGRAMMING, ASPECT_ORIENTED_PROGRAMMING, DELTA_ORIENTED_PROGRAMMING, PREPROCESSOR
	};

	final static String PACKAGE_PATTERN = "#package#";
	final static String REFINES_PATTERN = "#refines#";
	final static String CLASS_NAME_PATTERN = "#classname#";
	final static String FEATUE_PATTER = "#featurename#";

	boolean isInitialized();

	void performFullBuild(Path config);

	/**
	 * Builds a configuration to the given folder<br> To call a method before the building process of all configurations is started see:
	 * {@link IComposerExtensionClass#preBuildConfiguration()}
	 *
	 * @param folder The destination
	 * @param configuration The configuration to build
	 * @param congurationName The name of the configuration
	 */
	void buildConfiguration(IFolder folder, Configuration configuration, String congurationName);

	/**
	 * Called before building all configurations is started.
	 *
	 * @return <code>false</code> if the building process should be aborted.
	 */
	boolean preBuildConfiguration();

	/**
	 *
	 * @return <code>true</code> if clean should be performed before every build
	 */
	boolean clean();

	/**
	 * Copies not composed files to the source folder
	 *
	 * @param config The configuration
	 * @param destination The destination to copy
	 */
	void copyNotComposedFiles(Configuration config, IFolder destination);

	/**
	 *
	 * @return a list of file extensions witch will be composed
	 */
	LinkedHashSet<String> extensions();

	/**
	 * Make some changes after adding the FeatureIDE nature.
	 *
	 * @return <code>true</code> if the source files not have to be moved to the feature folder anymore
	 *
	 * @param source source folder
	 * @param destination destination folder
	 */
	boolean postAddNature(IFolder source, IFolder destination);

	/**
	 *
	 * Adds the compiler to the project.
	 *
	 * @param project project to add the compiler
	 * @param sourcePath source path
	 * @param configPath config path
	 * @param buildPath build path
	 */
	void addCompiler(IProject project, String sourcePath, String configPath, String buildPath);

	/**
	 * Creates the FSTModel without building the project.
	 */
	void buildFSTModel();

	/**
	 * Returns the list of templates for the current composer. <br> Format: {FILE_FORMAT_NAME, EXTENSION, TEMPLATE}
	 *
	 * @return list of templates for the current composer
	 */
	ArrayList<String[]> getTemplates();

	/**
	 * Replaces all markers in the template.
	 *
	 * @param fileContent the file's content where markers shall be replaced
	 * @param refines defines whether the refines checkbox is selected.
	 * @param packageName The package name
	 * @return the new file content
	 */
	String replaceSourceContentMarker(String fileContent, boolean refines, String packageName);

	/**
	 * Defines if a refining class differs from a common one and <code>replaceMarker()</code> has to be called.
	 *
	 * @return <code>true</code> if the refining class differs.
	 */
	boolean refines();

	/**
	 * Is called after changes at composition folder.
	 *
	 * @param delta Changes of the resource as delta
	 * @param buildFile build file
	 */
	void postCompile(IResourceDelta delta, IFile buildFile);

	/**
	 *
	 * @return the index of the default template.
	 */
	int getDefaultTemplateIndex();

	/**
	 * Is called after changes at the feature model.
	 */
	void postModelChanged();

	/**
	 * @return returns <code>false</code> if filenames equal the corresponding feature name otherwise <code>true</code>.
	 */
	boolean hasCustomFilename();

	/**
	 *
	 * @return the default configuration format. Extension can be extracted via getSuffix().
	 */
	IConfigurationFormat getConfigurationFormat();

	/**
	 * @return <code>true</code> if the composition can be called parallel
	 */
	boolean canGeneratInParallelJobs();

	/**
	 * @return <code>true</code> if the fields and Methods can be shown on context Menu of Collaboration Diagram
	 */
	boolean showContextFieldsAndMethods();

	/**
	 * @return List of all FSTDirectives in the text with parent == null
	 *
	 * @param lines of file / document
	 */
	LinkedList<FSTDirective> buildModelDirectivesForFile(Vector<String> lines);

	/**
	 * @return <code>true</code> if a highlighting at the editor is available for the composition tool.
	 */
	boolean needColor();

	/**
	 * @return The generation {@link Mechanism} of the generation tool, or null.
	 */
	Mechanism getGenerationMechanism();

	/**
	 * @return The format that should be used to create the feature model
	 */
	IFeatureModelFormat getFeatureModelFormat();

	/**
	 * @param removedFeatureNameList
	 * @throws CoreException
	 * @throws IOException
	 */
	default void buildPartialFeatureProjectAssets(IFolder sourceFolder, ArrayList<String> removedFeatures, ArrayList<String> mandatoryFeatures)
			throws IOException, CoreException {
		throw new UnsupportedOperationException();
	}
}
