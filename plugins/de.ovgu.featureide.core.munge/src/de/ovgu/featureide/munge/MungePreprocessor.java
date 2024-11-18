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
package de.ovgu.featureide.munge;

import static de.ovgu.featureide.fm.core.localization.StringTable.PREPROCESSOR_ANNOTATION_CHECKING;
import static de.ovgu.featureide.fm.core.localization.StringTable.PROPAGATE_PROBLEM_MARKERS_FOR;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.prop4j.Node;
import org.prop4j.Not;
import org.sonatype.plugins.munge.Munge;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.core.builder.IComposerObject;
import de.ovgu.featureide.core.builder.preprocessor.PPComposerExtensionClass;
import de.ovgu.featureide.core.fstmodel.preprocessor.FSTDirective;
import de.ovgu.featureide.core.signature.documentation.base.ADocumentationCommentParser;
import de.ovgu.featureide.fm.core.FMCorePlugin;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.munge.documentation.DocumentationCommentParser;
import de.ovgu.featureide.munge.model.MungeModelBuilder;

/**
 * Munge: a purposely-simple Java preprocessor.
 *
 * @author Jens Meinicke
 * @author Marcus Pinnecke (Feature Interface)
 */
public class MungePreprocessor extends PPComposerExtensionClass {

	protected MungeModelBuilder mungeModelBuilder;
	public static final String COMMENT_START = "/*";
	public static final String COMMENT_END = "*/";

	public static final String COMPOSER_ID = "de.ovgu.featureide.preprocessor.munge";

	private static final QualifiedName CREATE_SIGNATURE =
		new QualifiedName(MungePreprocessor.class.getName() + "#Signature", MungePreprocessor.class.getName() + "#Signature");
	private static final String TRUE = "true";
	private static final String FALSE = "false";

	/** all allowed instructions in munge as regular expression */
	public static final String OPERATORS = "(if(_not)?|else|end)\\[(.+?)\\]";

	/** all allowed instructions in munge as compiled regular expression */
	public static final Pattern OP_PATTERN = Pattern.compile(OPERATORS);

	/** compiled regular expression for instructions and comment symbols */
	public static final Pattern OP_COM_PATTERN = Pattern.compile("(" + OPERATORS + ")|/\\*|\\*/");

	/**
	 * is true if actual line is in comment section (between <code>&#47;*</code> and <code>*&#47;</code>)
	 */
	private boolean commentSection;

	public MungePreprocessor() {
		super("Munge");
	}

	@Override
	public boolean initialize(IFeatureProject project) {
		final boolean supSuccess = super.initialize(project);
		mungeModelBuilder = new MungeModelBuilder(project);

		prepareFullBuild(null);
		annotationChecking();

		return supSuccess && (mungeModelBuilder != null);
	}

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
	public void performFullBuild(Path config) {
		if (!prepareFullBuild(config)) {
			return;
		}
		try {
			preprocessSourceFiles(featureProject.getBuildFolder());
			annotationChecking();
		} catch (final CoreException e) {
			MungeCorePlugin.getDefault().logError(e);
		}

		if (mungeModelBuilder != null) {
			mungeModelBuilder.buildModel();
		}
	}

	@Override
	public void postModelChanged() {
		prepareFullBuild(null);
		annotationChecking();
	}

	protected void annotationChecking() {
		deleteAllPreprocessorAnotationMarkers();
		final Job job = new Job(PREPROCESSOR_ANNOTATION_CHECKING) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				annotationChecking(featureProject.getSourceFolder());
				setModelMarkers();
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.SHORT);
		job.schedule();
	}

	private void annotationChecking(IFolder folder) {
		try {
			for (final IResource res : folder.members()) {
				if (res instanceof IFolder) {
					annotationChecking((IFolder) res);
				} else if (res instanceof IFile) {
					final Vector<String> lines = loadStringsFromFile((IFile) res);
					// do checking and some stuff
					processLinesOfFile(lines, (IFile) res);
				}
			}
		} catch (final CoreException e) {
			MungeCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * preprocess all files in folder
	 *
	 * @param sourceFolder folder with files to preprocess
	 * @param buildFolder folder for preprocessed files
	 * @param annotationChecking <code>true</code> if preprocessor annotations should be checked
	 * @param performFullBuild <code>true</code> if the munge should be called
	 * @throws CoreException
	 */
	protected void preprocessSourceFiles(IFolder buildFolder) throws CoreException {
		final LinkedList<String> args = new LinkedList<String>();
		for (final String feature : activatedFeatures) {
			args.add("-D" + feature);
		}

		runMunge(args, featureProject.getSourceFolder(), buildFolder);
	}

	/**
	 * Calls munge for each package separate Creates all package folders at the build path
	 *
	 * @param featureArgs
	 * @param sourceFolder
	 * @param buildFolder
	 */
	protected void runMunge(LinkedList<String> featureArgs, IFolder sourceFolder, IFolder buildFolder) {
		final LinkedList<String> packageArgs = new LinkedList<String>(featureArgs);
		boolean added = false;
		try {
			createBuildFolder(buildFolder);
			for (final IResource res : sourceFolder.members()) {
				if (res instanceof IFolder) {
					runMunge(featureArgs, (IFolder) res, buildFolder.getFolder(res.getName()));
				} else if (res instanceof IFile) {
					added = true;
					packageArgs.add(res.getRawLocation().toOSString());
				}
			}
		} catch (final CoreException e) {
			MungeCorePlugin.getDefault().logError(e);
		}
		if (!added) {
			return;
		}
		// add output directory
		packageArgs.add(buildFolder.getRawLocation().toOSString());

		// CommandLine syntax:
		// -DFEATURE1 -DFEATURE2 ... File1 File2 ... outputDirectory
		runMunge(packageArgs);
	}

	/**
	 * Do checking for all lines of file.
	 *
	 * @param lines all lines of file
	 * @param res file
	 */
	synchronized private void processLinesOfFile(Vector<String> lines, IFile res) {
		expressionStack = new ArrayDeque<>();

		// count of if, ifelse and else to remove after processing of else from
		// stack
		ifelseCountStack = new ArrayDeque<>();
		ifelseCountStack.push(0);

		commentSection = false;

		// go line for line
		for (int j = 0; j < lines.size(); ++j) {
			final String line = lines.get(j);

			if (line.contains("/*") || line.contains("*/") || commentSection) {

				setMarkersContradictionalFeatures(line, res, j + 1);

				setMarkersNotConcreteFeatures(line, res, j + 1);
			}
		}
	}

	/**
	 * Checks given line if it contains expressions which are always <code>true</code> or <code>false</code>.<br /> <br />
	 *
	 * Check in three steps: <ol> <li>just the given line</li> <li>the given line and the feature model</li> <li>the given line, the surrounding lines and the
	 * feature model</li> </ol>
	 *
	 * @param line content of line
	 * @param res file containing given line
	 * @param lineNumber line number of given line
	 */
	private void setMarkersContradictionalFeatures(String line, IFile res, int lineNumber) {

		final Matcher m = OP_COM_PATTERN.matcher(line);

		while (m.find()) {
			final String completeElement = m.group(0);
			final String singleElement = m.group(2);

			if (singleElement == null) {
				if (completeElement.equals("/*")) {
					commentSection = true;
				} else if (completeElement.equals("*/")) {
					commentSection = false;
				}
			} else {
				if (singleElement.startsWith("if") || singleElement.equals("else")) {
					if (singleElement.equals("else")) {
						if (!expressionStack.isEmpty()) {
							final Node lastElement = new Not(expressionStack.pop().clone());
							expressionStack.push(lastElement);
						}

					} else {
						Node ppExpression = nodereader.stringToNode(m.group(4), featureList);

						if (singleElement.equals("if_not")) {
							ppExpression = new Not(ppExpression.clone());
						}

						if (singleElement.startsWith("if")) {
							ifelseCountStack.push(0);
						}

						ifelseCountStack.push(ifelseCountStack.pop() + 1);
						expressionStack.push(ppExpression);
					}
					checkContradictionOrTautology(lineNumber, res);

				} else if (singleElement.equals("end")) {
					for (; ifelseCountStack.peek() > 0; ifelseCountStack.push(ifelseCountStack.pop() - 1)) {
						if (!expressionStack.isEmpty()) {
							expressionStack.pop();
						}
					}

					ifelseCountStack.pop();
				}
			}
		}
	}

	private void setMarkersNotConcreteFeatures(String line, IFile res, int lineNumber) {
		final Matcher matcherIf = OP_PATTERN.matcher(line);

		if (matcherIf.find()) {
			setMarkersOnNotExistingOrAbstractFeature(matcherIf.group(3), lineNumber, res);
		}
	}

	protected void runMunge(LinkedList<String> args) {
		// run Munge
		final Munge m = new Munge();
		m.main(args.toArray(new String[0]), featureProject);
	}

	protected void createBuildFolder(IFolder buildFolder) throws CoreException {
		if (!buildFolder.exists()) {
			buildFolder.create(true, true, null);
		}
		buildFolder.refreshLocal(IResource.DEPTH_ZERO, null);
	}

	@Override
	public ArrayList<String[]> getTemplates() {
		return TEMPLATES;
	}

	private static final ArrayList<String[]> TEMPLATES = createTempltes();

	private static ArrayList<String[]> createTempltes() {
		final ArrayList<String[]> list = new ArrayList<String[]>();
		list.add(JAVA_TEMPLATE);
		return list;
	}

	@Override
	public void postCompile(IResourceDelta delta, final IFile file) {
		super.postCompile(delta, file);
		final Job job =
			new Job((PROPAGATE_PROBLEM_MARKERS_FOR + CorePlugin.getFeatureProject(file)) != null ? CorePlugin.getFeatureProject(file).toString() : "") {

				@Override
				public IStatus run(IProgressMonitor monitor) {
					try {
						final IMarker[] marker = file.findMarkers(null, false, IResource.DEPTH_ZERO);
						if (marker.length != 0) {
							for (final IMarker m : marker) {
								final IFile sourceFile = findSourceFile(file, featureProject.getSourceFolder());
								if (!hasMarker(m, sourceFile)) {
									final IMarker newMarker = sourceFile.createMarker(CorePlugin.PLUGIN_ID + ".builderProblemMarker");
									newMarker.setAttribute(IMarker.LINE_NUMBER, m.getAttribute(IMarker.LINE_NUMBER));
									newMarker.setAttribute(IMarker.MESSAGE, m.getAttribute(IMarker.MESSAGE));
									newMarker.setAttribute(IMarker.SEVERITY, m.getAttribute(IMarker.SEVERITY));
								}
							}
						}
					} catch (final CoreException e) {
						MungeCorePlugin.getDefault().logError(e);
					}
					return Status.OK_STATUS;
				}

				private boolean hasMarker(IMarker buildMarker, IFile sourceFile) {
					try {
						final IMarker[] marker = sourceFile.findMarkers(null, true, IResource.DEPTH_ZERO);
						final int LineNumber = buildMarker.getAttribute(IMarker.LINE_NUMBER, -1);
						final String Message = buildMarker.getAttribute(IMarker.MESSAGE, null);
						if (marker.length > 0) {
							for (final IMarker m : marker) {
								if (LineNumber == m.getAttribute(IMarker.LINE_NUMBER, -1)) {
									if (Message.equals(m.getAttribute(IMarker.MESSAGE, null))) {
										return true;
									}
								}
							}
						}
					} catch (final CoreException e) {
						MungeCorePlugin.getDefault().logError(e);
					}
					return false;
				}
			};
		job.setPriority(Job.DECORATE);
		job.schedule();
	}

	private IFile findSourceFile(IFile file, IFolder folder) throws CoreException {
		for (final IResource res : folder.members()) {
			if (res instanceof IFolder) {
				final IFile sourceFile = findSourceFile(file, (IFolder) res);
				if (sourceFile != null) {
					return sourceFile;
				}
			} else if (res instanceof IFile) {
				if (res.getName().equals(file.getName())) {
					return (IFile) res;
				}
			}
		}
		return null;
	}

	@Override
	public boolean hasFeatureFolder() {
		return true;
	}

	@Override
	public boolean createFolderForFeatures() {
		return false;
	}

	@Override
	public void buildFSTModel() {
		mungeModelBuilder.buildModel();
	}

	@Override
	public void buildConfiguration(IFolder folder, Configuration configuration, String congurationName) {
		super.buildConfiguration(folder, configuration, congurationName);
		prepareFullBuild(null);
		if (activatedFeatures == null) {
			activatedFeatures = new ArrayList<String>();
		} else {
			activatedFeatures.clear();
		}
		for (final IFeature feature : configuration.getSelectedFeatures()) {
			activatedFeatures.add(feature.getName());
		}
		try {
			preprocessSourceFiles(folder);
		} catch (final CoreException e) {
			MungeCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public boolean showContextFieldsAndMethods() {
		return false;
	}

	@Override
	public LinkedList<FSTDirective> buildModelDirectivesForFile(Vector<String> lines) {
		return mungeModelBuilder.buildModelDirectivesForFile(lines);
	}

	@Override
	public boolean needColor() {
		return true;
	}

	@Override
	public Mechanism getGenerationMechanism() {
		return IComposerExtensionClass.Mechanism.PREPROCESSOR;
	}

	@Override
	public boolean supportsMigration() {
		return false;
	}

	@Override
	public <T extends IComposerObject> T getComposerObjectInstance(Class<T> c) {
		if (c == ADocumentationCommentParser.class) {
			return c.cast(new DocumentationCommentParser());
		}
		return super.getComposerObjectInstance(c);
	}

	/**
	 * Removes duplicate empty lines.
	 */
	@Override
	public void postProcess(IFolder folder) {
		// TODO remove ALL lines that correspond to annotations
		try {
			folder.refreshLocal(IResource.DEPTH_INFINITE, null);
			for (final IResource res : folder.members()) {
				if (res instanceof IFolder) {
					postProcess((IFolder) res);
				} else if (res instanceof IFile) {
					if (res.getFileExtension().equals(getConfigurationFormat().getSuffix())) {
						continue;
					}
					try (final FileInputStream inputStream = new FileInputStream(new File(res.getLocationURI()));
							final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.availableCharsets().get("UTF-8")))) {
						String line = null;
						final StringBuilder content = new StringBuilder();
						boolean hasAnnotations = false;
						boolean lastLineEmpty = false;
						while ((line = reader.readLine()) != null) {
							if (line.trim().isEmpty()) {
								if (!lastLineEmpty) {
									content.append(line);
									content.append("\r\n");
								} else {
									lastLineEmpty = true;
									hasAnnotations = true;
								}
							} else {
								content.append(line);
								content.append("\r\n");
								lastLineEmpty = false;
							}
						}
						if (hasAnnotations) {
							setFileContent((IFile) res, content);
						}
					} catch (final IOException e) {
						MungeCorePlugin.getDefault().logError(e);
					}
				}
			}
		} catch (final CoreException e) {
			MungeCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * Sets the files new content.
	 *
	 * @param file The file
	 * @param content The new content to set
	 */
	private void setFileContent(IFile file, StringBuilder content) {
		final InputStream source = new ByteArrayInputStream(content.toString().getBytes(Charset.availableCharsets().get("UTF-8")));
		try {
			file.setContents(source, false, true, null);
		} catch (final CoreException e) {
			MungeCorePlugin.getDefault().logError(e);
		}
	}

	public void setCreateSignature(boolean signature) {
		setProperty(CREATE_SIGNATURE, signature);

		if (signature) {
			final Path currentConfiguration = featureProject.getCurrentConfiguration();
			if (currentConfiguration != null) {
				performFullBuild(currentConfiguration);
			}
		}
	}

	public boolean getCreateSignature() {
		return getPropertyBoolean(CREATE_SIGNATURE);
	}

	private boolean getPropertyBoolean(QualifiedName qname) {
		try {
			return TRUE.equals(featureProject.getProject().getPersistentProperty(qname));
		} catch (final CoreException e) {
			FMCorePlugin.getDefault().logError(e);
		}
		return false;
	}

	private void setProperty(QualifiedName qname, boolean value) {
		try {
			featureProject.getProject().setPersistentProperty(qname, value ? TRUE : FALSE);
		} catch (final CoreException e) {
			FMCorePlugin.getDefault().logError(e);
		}
	}

	@Override
	public boolean canGeneratInParallelJobs() {
		return false;// TODO munge seems to have parallelization problems
	}

}
