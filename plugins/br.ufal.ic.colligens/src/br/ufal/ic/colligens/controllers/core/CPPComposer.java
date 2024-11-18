package br.ufal.ic.colligens.controllers.core;

import static de.ovgu.featureide.fm.core.localization.StringTable.C_HEADER_FILE;
import static de.ovgu.featureide.fm.core.localization.StringTable.DISPLAY_IS_NULL;
import static de.ovgu.featureide.fm.core.localization.StringTable.IS_NOT_INSTALLED_;
import static de.ovgu.featureide.fm.core.localization.StringTable.PREPROCESSOR_ANNOTATION_CHECKING;
import static de.ovgu.featureide.fm.core.localization.StringTable.THE_REQUIRED_BUNDLE;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IIncludeReference;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.prop4j.Node;
import org.prop4j.Not;

import br.ufal.ic.colligens.activator.Colligens;
import br.ufal.ic.colligens.controllers.ProjectExplorerController;
import br.ufal.ic.colligens.controllers.invalidconfigurations.InvalidConfigurationsViewController;
import br.ufal.ic.colligens.controllers.invalidproducts.InvalidProductsViewController;
import br.ufal.ic.colligens.models.FileProxy;
import br.ufal.ic.colligens.models.TypeChef;
import br.ufal.ic.colligens.models.TypeChefException;
import br.ufal.ic.colligens.util.InvalidProductViewLog;
import br.ufal.ic.colligens.util.ProjectConfigurationErrorLogger;
import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.core.builder.preprocessor.PPComposerExtensionClass;
import de.ovgu.featureide.core.fstmodel.preprocessor.FSTDirective;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.configuration.Configuration;

/**
 * A featureIDE composer to compose C files.
 *
 * @author Dalton thanks to
 * @author Tom Brosch
 * @author Jens Meinicke
 * @author Christoph Giesel
 * @author Marcus Kamieth
 *
 */
public class CPPComposer extends PPComposerExtensionClass {

	private static final String PLUGIN_CDT_ID = "org.eclipse.cdt";
	private static final String PLUGIN_WARNING = THE_REQUIRED_BUNDLE + PLUGIN_CDT_ID + IS_NOT_INSTALLED_;
	public static final String COMPOSER_ID = Colligens.PLUGIN_ID + ".cppcomposer";
	public static final String C_NATURE = "org.eclipse.cdt.core.cnature";
	public static final String CC_NATURE = "org.eclipse.cdt.core.ccnature";

	/* pattern for replacing preprocessor commands like "//#if" */
	static final Pattern replaceCommandPattern = Pattern.compile("#(.+?)\\s");

	private CPPModelBuilder cppModelBuilder;

	/*
	 * If the user runs the compileAllProducs and the typechef warn about errors in the project the user needs to choose if want to continue with the
	 * compilation
	 */
	private static boolean continueCompilationFlag = true;
	/* manage the compilation execution */
	private static Set<Long> threadInExecId = new HashSet<Long>();

	public CPPComposer() {
		super("CppComposer");
	}

	@Override
	public boolean initialize(IFeatureProject project) {
		final boolean supSuccess = super.initialize(project);
		cppModelBuilder = new CPPModelBuilder(project);

		// Start typeChef
		// Setup the controller
		// typeChef = new TypeChef();
		prepareFullBuild(null);
		annotationChecking();

		if ((supSuccess == false) || (cppModelBuilder == null)) {
			return false;
		} else {
			return true;
		}
	}

	private static final LinkedHashSet<String> EXTENSIONS = createExtensions();

	private static LinkedHashSet<String> createExtensions() {
		final LinkedHashSet<String> extensions = new LinkedHashSet<String>();
		extensions.add("h");
		extensions.add("c");
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
			if (!project.isAccessible() || project.hasNature(C_NATURE)) {
				return;
			}

			final IProjectDescription description = project.getDescription();
			final String[] natures = description.getNatureIds();
			final String[] newNatures = new String[natures.length + 1];
			System.arraycopy(natures, 0, newNatures, 0, natures.length);
			newNatures[natures.length] = C_NATURE;
			description.setNatureIds(newNatures);
			project.setDescription(description, null);

		} catch (final CoreException e) {
			CorePlugin.getDefault().logError(e);
		}

	}

	@Override
	public void performFullBuild(Path config) {

		if (!isPluginInstalled(PLUGIN_CDT_ID)) {
			generateWarning(PLUGIN_WARNING);
		}

		if (!prepareFullBuild(config)) {
			return;
		}

		final Job job = new Job("Analyzing!") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				// this perfom a build using the Feature configuration selected
				IFolder buildFolder = featureProject.getBuildFolder();

				if (buildFolder.getName().equals("src")) {
					buildFolder = featureProject.getProject().getFolder(System.getProperty("file.separator") + "build");
				}
				runTypeChefAnalyzes(featureProject.getSourceFolder());

				if (continueCompilationFlag) {
					final LinkedList<String> lst_selectedFeatures = new LinkedList<String>();
					lst_selectedFeatures.addAll(activatedFeatures);
					runBuild(getActivatedFeatureArgs(lst_selectedFeatures), featureProject.getSourceFolder(), buildFolder);
				}
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.SHORT);
		job.schedule();

		if (cppModelBuilder != null) {
			cppModelBuilder.buildModel();
		}
		annotationChecking();
	}

	@Override
	public void postModelChanged() {
		prepareFullBuild(null);
		annotationChecking();
	}

	private void annotationChecking() {
		deleteAllPreprocessorAnotationMarkers();
		final Job job = new Job(PREPROCESSOR_ANNOTATION_CHECKING) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				annotationChecking(featureProject.getSourceFolder());
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
			Colligens.getDefault().logError(e);
		}
	}

	synchronized private void processLinesOfFile(Vector<String> lines, IFile res) {
		expressionStack = new ArrayDeque<>();

		// count of if, ifelse and else to remove after processing of else from
		// stack
		ifelseCountStack = new ArrayDeque<>();

		// go line for line
		for (int j = 0; j < lines.size(); ++j) {
			String line = lines.get(j);

			// if line is preprocessor directive
			if (line.contains("#")) {
				if (line.contains("#if ") || line.contains("#elif ") || line.contains("#ifdef ") || line.contains("#ifndef ") || line.contains("#else")) {

					// remove defined directive to proper work as normal
					// preprocessor.
					line = line.replaceAll("defined", "");

					// if e1, elseif e2, ..., elseif en == if -e1 && -e2 && ...
					// && en
					// if e1, elseif e2, ..., else == if -e1 && -e2 && ...
					if (line.contains("#elif ") || line.contains("#else")) {
						if (!expressionStack.isEmpty()) {
							final Node lastElement = new Not(expressionStack.pop().clone());
							expressionStack.push(lastElement);
						}
					} else if (line.contains("#if ") || line.contains("#ifdef ") || line.contains("#ifndef ")) {
						ifelseCountStack.push(0);
					}

					if (!ifelseCountStack.isEmpty() && !line.contains("#else")) {
						ifelseCountStack.push(ifelseCountStack.pop() + 1);
					}

					setMarkersContradictionalFeatures(line, res, j + 1);

					setMarkersNotConcreteFeatures(line, res, j + 1);
				} else if (line.contains("#endif")) {
					while (!ifelseCountStack.isEmpty()) {
						if (ifelseCountStack.peek() == 0) {
							break;
						}

						if (!expressionStack.isEmpty()) {
							expressionStack.pop();
						}

						ifelseCountStack.push(ifelseCountStack.pop() - 1);
					}

					if (!ifelseCountStack.isEmpty()) {
						ifelseCountStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Checks given line if it contains not existing or abstract features.
	 *
	 * @param line content of line
	 * @param res file containing given line
	 * @param lineNumber line number of given line
	 */
	private void setMarkersNotConcreteFeatures(String line, IFile res, int lineNumber) {
		final String[] splitted = line.split(CPPModelBuilder.OPERATORS, 0);

		for (int i = 0; i < splitted.length; ++i) {
			if (!splitted[i].equals("") && !splitted[i].contains("#")) {
				setMarkersOnNotExistingOrAbstractFeature(splitted[i], lineNumber, res);
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
		if (line.contains("#else")) {
			if (!expressionStack.isEmpty()) {
				checkContradictionOrTautology(lineNumber, res);
			}

			return;
		}

		final boolean negative = line.contains("#ifndef ");

		// remove "//#if ", "//ifdef", ...
		// TODO TIRAR O defined
		line = replaceCommandPattern.matcher(line).replaceAll("");

		// prepare expression for NodeReader()
		line = line.trim();
		line = line.replace("&&", "&");
		line = line.replace("||", "|");
		line = line.replace("!", "-");
		line = line.replace("&", " and ");
		line = line.replace("|", " or ");
		line = line.replace("-", " not ");

		// get all features and generate Node expression for given line
		Node ppExpression = nodereader.stringToNode(line, featureList);

		if (ppExpression != null) {
			if (negative) {
				ppExpression = new Not(ppExpression.clone());
			}

			expressionStack.push(ppExpression);

			checkContradictionOrTautology(lineNumber, res);

		}
	}

	/**
	 * Prepare the directives to C PreProcessor adding -D arg for each feature
	 *
	 * @param myActivatedFeatures list of all activated features for one build
	 * @return list in the form -D FEATUREA -D FEATUREB -D FEATUREC
	 */
	private LinkedList<String> getActivatedFeatureArgs(List<String> myActivatedFeatures) {
		final LinkedList<String> args = new LinkedList<String>();
		for (final String feature : myActivatedFeatures) {
			args.add("-D" + feature);
		}
		return args;

	}

	/**
	 * Execute preprocessment and compilation
	 *
	 * @param featureArgs list of features active for this build
	 * @param sourceFolder root folder from sources
	 * @param buildFolder folder that the result will be placed
	 */
	private void runBuild(LinkedList<String> featureArgs, IFolder sourceFolder, IFolder buildFolder) {
		final CPPWrapper cpp = new CPPWrapper();

		final LinkedList<String> compilerArgs = new LinkedList<String>(featureArgs);
		final LinkedList<String> fileList = new LinkedList<String>();

		// find includes
		final String projectName = sourceFolder.getProject().getName();

		final ICProject project = CoreModel.getDefault().getCModel().getCProject(projectName);
		try {
			final IIncludeReference includes[] = project.getIncludeReferences();
			for (int i = 0; i < includes.length; i++) {
				compilerArgs.add("-I" + includes[i].getElementName());
			}
			if (!Colligens.getDefault().getPreferenceStore().getString("LIBS").contentEquals("")) {
				compilerArgs.add(Colligens.getDefault().getPreferenceStore().getString("LIBS"));
			}
		} catch (final CModelException e) {
			e.printStackTrace();
		}

		try {
			createFolder(buildFolder);
			prepareFilesConfiguration(featureArgs, fileList, sourceFolder, buildFolder, cpp);

			// if the user don't want to continue the compilation
			// only the preprocessment occurs
			if (!continueCompilationFlag) {
				return;
			}
			compilerArgs.addAll(fileList);
			compilerArgs.add("-o");
			compilerArgs.add(buildFolder.getLocation().toOSString() + System.getProperty("file.separator") + buildFolder.getName());
			cpp.runCompiler(compilerArgs);
			buildFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (final CoreException e) {
			Colligens.getDefault().logError(e);
		}
	}

	/**
	 *
	 * Execute typeChef analyzes and in case of error, ask to user if he wants to continue the compilation.
	 *
	 * @param folder containing the sources
	 * @return true if the project can be compiled, false in otherwise
	 */
	private void runTypeChefAnalyzes(IFolder folder) {
		final ProjectExplorerController prjController = new ProjectExplorerController();

		prjController.addResource(folder);

		final TypeChef typeChef = new TypeChef();
		try {
			typeChef.run(prjController.getList());

			final Display display = Display.getDefault();
			if (display == null) {
				throw new NullPointerException(DISPLAY_IS_NULL);
			}

			display.syncExec(new Runnable() {

				@Override
				public void run() {
					final InvalidConfigurationsViewController viewController = InvalidConfigurationsViewController.getInstance();
					if (typeChef.isFinish()) {

						final List<FileProxy> logs = typeChef.getFilesLog();

						if (!logs.isEmpty()) {
							continueCompilationFlag = MessageDialog.openQuestion(display.getActiveShell(), "Error!",
									"This project contains errors in some feature combinations.\nDo you want to continue the compilation?");
							viewController.showView();
							viewController.setInput(logs);
						}
					} else {
						viewController.clear();
					}
				}
			});

		} catch (final TypeChefException e) {

			e.printStackTrace();
		}
	}

	/**
	 * In this method, all files in a given source folder are preprocessed by CPP
	 *
	 * @param featureArgs arguments to CPP preprocessor and compiler
	 * @param fileList list of all files found in folders and subfolders
	 * @param sourceFolder the origin of files
	 * @param buildFolder the destination of the compilation/preprocessment
	 * @param cpp that contains methods that compile/preprocess C files
	 * @throws CoreException
	 */
	@SuppressWarnings("unchecked")
	private void prepareFilesConfiguration(LinkedList<String> featureArgs, List<String> fileList, IFolder sourceFolder, IFolder buildFolder, CPPWrapper cpp)
			throws CoreException {

		String fullFilePath = null;
		List<String> preProcessorInput;
		String preProcessorOutput;
		for (final IResource res : sourceFolder.members()) {
			if (res instanceof IFolder) {
				buildFolder = featureProject.getProject().getFolder(buildFolder.getProjectRelativePath() + File.separator + res.getName());
				createFolder(buildFolder);
				prepareFilesConfiguration(featureArgs, fileList, (IFolder) res, buildFolder, cpp);
			} else if (res instanceof IFile) {
				if (!res.getFileExtension().equals("c") && !res.getFileExtension().equals("h")) {
					continue;
				}
				final String[] name = res.getName().split("\\.");
				fullFilePath = res.getLocation().toOSString();
				fileList.add(fullFilePath);
				preProcessorInput = (LinkedList<String>) featureArgs.clone();
				preProcessorInput.add(fullFilePath);
				preProcessorOutput =
					buildFolder.getLocation().toOSString() + System.getProperty("file.separator") + name[0] + "_preprocessed." + res.getFileExtension();

				// CommandLine syntax:
				// -DFEATURE1 -DFEATURE2 ... File1 outputDirectory/File1
				cpp.runPreProcessor(preProcessorInput, preProcessorOutput);
			}

		}
	}

	private void createFolder(IFolder folder) throws CoreException {
		if (!folder.exists()) {
			folder.create(true, true, null);
		}
		folder.refreshLocal(IResource.DEPTH_ZERO, null);
	}

	@Override
	public ArrayList<String[]> getTemplates() {
		return TEMPLATES;
	}

	private static final ArrayList<String[]> TEMPLATES = createTempltes();

	private static ArrayList<String[]> createTempltes() {
		final ArrayList<String[]> list = new ArrayList<String[]>();
		list.add(new String[] { "C Source File", "c", "\r\n" + "int main(int argc, char **argv)" + " {\r\n\r\n}" });
		list.add(new String[] { C_HEADER_FILE, "h",
			"#ifndef " + CLASS_NAME_PATTERN + "_H_\n" + "#define " + CLASS_NAME_PATTERN + "_H_\n\n\n" + "#endif /* " + CLASS_NAME_PATTERN + "_H_ */" });
		return list;
	}

	@Override
	public void postCompile(IResourceDelta delta, final IFile file) {

	}

	@Override
	public boolean hasFeatureFolder() {
		return false;
	}

	@Override
	public boolean createFolderForFeatures() {
		return false;
	}

	@Override
	public boolean clean() {
		return false;
	}

	@Override
	public void buildFSTModel() {
		cppModelBuilder.buildModel();
	}

	@Override
	public boolean postAddNature(IFolder source, IFolder destination) {
		return true;
	}

	/**
	 * Lock the execution of all threads until the user decide what to do if the project has errors
	 */
	private synchronized void syncTypeChefAnalyzes() {
		if (threadInExecId.isEmpty()) {
			ProjectConfigurationErrorLogger.getInstance().clearLogList();
			runTypeChefAnalyzes(featureProject.getSourceFolder());
		}
		threadInExecId.add(Thread.currentThread().getId());
	}

	// return logList.toArray(new Log[logList.size()]);

	/**
	 * Show variants with errors
	 */
	private synchronized void verifyVariantsWithProblems() {
		threadInExecId.remove(Thread.currentThread().getId());
		if (threadInExecId.isEmpty()) {
			final Display display = Display.getDefault();
			if (display == null) {
				throw new NullPointerException(DISPLAY_IS_NULL);
			}
			display.syncExec(new Runnable() {

				@Override
				public void run() {
					final InvalidProductsViewController invalidProductViewController = InvalidProductsViewController.getInstance();

					if (!ProjectConfigurationErrorLogger.getInstance().getProjectsList().isEmpty()) {
						invalidProductViewController.showView();

						final List<InvalidProductViewLog> logs = new LinkedList<InvalidProductViewLog>();
						for (final String s : ProjectConfigurationErrorLogger.getInstance().getProjectsList()) {
							logs.add(new InvalidProductViewLog(s));
						}
						invalidProductViewController.setInput(logs);
					} else {
						// Clear view
						// invalidProductViewController.clear();
						MessageDialog.openInformation(new Shell(), Colligens.PLUGIN_NAME, "The products generated successfully!");
					}

				}

			});

		}

	}

	@Override
	public void buildConfiguration(IFolder folder, Configuration configuration, String congurationName) {
		super.buildConfiguration(folder, configuration, congurationName);

		// do the typeChef analysis before the products compilation
		syncTypeChefAnalyzes();

		final List<String> myActivatedFeatures = new LinkedList<String>();

		for (final IFeature feature : configuration.getSelectedFeatures()) {
			myActivatedFeatures.add(feature.getName());
		}

		runBuild(getActivatedFeatureArgs(myActivatedFeatures), featureProject.getSourceFolder(), folder);

		// displays the products with errors
		verifyVariantsWithProblems();

	}

	@Override
	public boolean showContextFieldsAndMethods() {
		return false;
	}

	@Override
	public LinkedList<FSTDirective> buildModelDirectivesForFile(Vector<String> lines) {
		return cppModelBuilder.buildModelDirectivesForFile(lines);
	}

	@Override
	public boolean needColor() {
		return false;
	}

	@Override
	public boolean canGeneratInParallelJobs() {
		return true;
	}

	@Override
	public Mechanism getGenerationMechanism() {
		return IComposerExtensionClass.Mechanism.PREPROCESSOR;
	}

}
