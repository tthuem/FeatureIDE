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
package de.ovgu.featureide.fm.ui.editors;

import static de.ovgu.featureide.fm.core.localization.StringTable.MODEL;
import static de.ovgu.featureide.fm.core.localization.StringTable.MODEL_SHOULD_BE_SAVED_AFTER_RENAMINGS_;
import static de.ovgu.featureide.fm.core.localization.StringTable.SAVE_FEATURE_MODEL;
import static de.ovgu.featureide.fm.core.localization.StringTable.SAVE_RESOURCES;
import static de.ovgu.featureide.fm.core.localization.StringTable.SOME_MODIFIED_RESOURCES_MUST_BE_SAVED_BEFORE_SAVING_THE_FEATUREMODEL_;
import static de.ovgu.featureide.fm.core.localization.StringTable.THE_FEATURE_MODEL_IS_VOID_COMMA__I_E__COMMA__IT_CONTAINS_NO_PRODUCTS;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.ObjectUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.gef.ui.actions.SelectAllAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.operations.RedoActionHandler;
import org.eclipse.ui.operations.UndoActionHandler;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import de.ovgu.featureide.fm.core.EmptyMarkerHandler;
import de.ovgu.featureide.fm.core.ExtensionManager.NoSuchExtensionException;
import de.ovgu.featureide.fm.core.FMComposerManager;
import de.ovgu.featureide.fm.core.FMCorePlugin;
import de.ovgu.featureide.fm.core.IModelMarkerHandler;
import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.ModelMarkerHandler;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.event.FeatureIDEEvent;
import de.ovgu.featureide.fm.core.base.event.IEventListener;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.base.impl.FeatureModelProperty;
import de.ovgu.featureide.fm.core.io.EclipseFileSystem;
import de.ovgu.featureide.fm.core.io.IPersistentFormat;
import de.ovgu.featureide.fm.core.io.Problem;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.GraphicsExporter;
import de.ovgu.featureide.fm.ui.editors.configuration.ConfigurationEditor;
import de.ovgu.featureide.fm.ui.editors.elements.GraphicalFeatureModel;
import de.ovgu.featureide.fm.ui.properties.FMPropertyManager;
import de.ovgu.featureide.fm.ui.views.outline.standard.FmOutlinePage;

/**
 * A multi page editor to edit feature models. If the model file contains errors, markers will be created on save.
 *
 * @author Thomas Thuem
 * @author Christian Becker
 */
public class FeatureModelEditor extends MultiPageEditorPart implements IEventListener, IResourceChangeListener {

	public static final String ID = FMUIPlugin.PLUGIN_ID + ".editors.FeatureModelEditor";

	public FeatureDiagramEditor diagramEditor;
	public FeatureOrderEditor featureOrderEditor;
	public FeatureModelTextEditorPage textEditor;

	public List<IFeatureModelEditorPage> extensionPages, pages = new LinkedList<>();

	private IModelMarkerHandler markerHandler;
	private IProject inputProject;
	private Path inputPath;

	boolean isPageModified = false;
	boolean isFirstSourceAccess = true;
	FeatureModelManager fmManager;
	IGraphicalFeatureModel gfm;

	private boolean closeEditor;

	private int currentPageIndex;
	private int operationCounter;

	private FmOutlinePage outlinePage;

	private final List<Action> actions = new ArrayList<>(4);

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		super.init(site, input);
	}

	@Override
	public void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		FMPropertyManager.unregisterEditor(this);
		if (diagramEditor != null) {
			diagramEditor.dispose();
			fmManager.removeListener(diagramEditor);
			fmManager.overwrite();
		}
		super.dispose();
	}

	@Override
	public void doSave(final IProgressMonitor monitor) {
		if (!saveEditors()) {
			return;
		}

		diagramEditor.doSave(monitor);
		featureOrderEditor.doSave(monitor);
		for (final IFeatureModelEditorPage page : extensionPages) {
			page.doSave(monitor);
		}

		gfm.writeValues();
		// write the model to the file
		if (getActivePage() == textEditor.getIndex()) {
			fmManager.externalSave(() -> textEditor.internalSave(monitor));
		} else {
			fmManager.save();
			textEditor.resetTextEditor();
			setPageModified(false);
		}
	}

	@Override
	public void doSaveAs() {
		GraphicsExporter.exportAs(diagramEditor.getViewer());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object getAdapter(Class adapter) {
		if (IContentOutlinePage.class.equals(adapter)) {
			if (outlinePage == null) {
				outlinePage = new FmOutlinePage(null, this);
				outlinePage.setInput(fmManager);
			}
			return outlinePage;
		}
		if (IGotoMarker.class.equals(adapter)) {
			if (getActivePage() != getDiagramEditorIndex()) {
				setActivePage(getDiagramEditorIndex());
			}
		}
		if (diagramEditor != null) {
			final Object o = diagramEditor.getAdapter(adapter);
			if (o != null) {
				return o;
			}
		}
		return super.getAdapter(adapter);
	}

	public IAction getDiagramAction(String workbenchActionID) {
		for (final Action action : actions) {
			if (action.getId().equals(workbenchActionID)) {
				return action;
			}
		}
		final IAction action = diagramEditor.getDiagramAction(workbenchActionID);
		if (action != null) {
			return action;
		}
		FMCorePlugin.getDefault().logInfo("The following workbench action is not registered at the feature diagram editor: " + workbenchActionID);
		return null;
	}

	public FeatureModelManager getFeatureModelManager() {
		return fmManager;
	}

	public IFeatureModel getOriginalFeatureModel() {
		return fmManager.getObject();
	}

	public ITextEditor getSourceEditor() {
		return textEditor;
	}

	@Override
	public boolean isDirty() {
		return isPageModified || super.isDirty();
	}

	@Override
	public boolean isSaveAsAllowed() {
		return true;
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		// Get editor input
		final IEditorInput input = getEditorInput();
		if (!(input instanceof IFileEditorInput)) {
			return;
		}
		final IFile inputFile = ((IFileEditorInput) input).getFile();

		// Check whether current editor input was deleted
		if (event.getType() == IResource.FILE) {
			IResourceDelta delta = event.getDelta();
			if (delta == null) {
				return;
			}
			while (delta.getAffectedChildren().length != 0) {
				delta = delta.getAffectedChildren()[0];
			}
			if ((delta.getKind() == IResourceDelta.REMOVED) && delta.getResource().equals(inputFile)) {
				Display.getDefault().asyncExec(new Runnable() {

					@Override
					public void run() {
						if ((getSite() == null) || (getSite().getWorkbenchWindow() == null)) {
							return;
						}
						final IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
						for (int i = 0; i < pages.length; i++) {
							final IEditorPart editorPart = pages[i].findEditor(input);
							pages[i].closeEditor(editorPart, true);
						}
					}
				});
			}
		}

		// Resource is only set when the project is affected. When only files are deleted the resource is null. Therefore, we start by checking the file before
		// checking the resource.
		if (event.getResource() == null) {
			return;
		}

		if (event.getResource().getType() == IResource.PROJECT) {
			closeEditor = true;
		}

		/*
		 * Closes editor if resource is deleted
		 */
		if ((event.getType() == IResourceChangeEvent.POST_CHANGE) && closeEditor) {
			final List<IResource> deletedlist = new ArrayList<>();
			final IResourceDelta inputFileDelta = event.getDelta().findMember(inputFile.getFullPath());
			if (inputFileDelta != null) {
				final IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {

					@Override
					public boolean visit(IResourceDelta delta) {
						// only interested in removal changes
						if (((delta.getFlags() & IResourceDelta.REMOVED) == 0) && closeEditor) {
							deletedlist.add(delta.getResource());
						}
						return true;
					}
				};
				try {
					inputFileDelta.accept(visitor);
				} catch (final CoreException e) {
					FMUIPlugin.getDefault().logError(e);
				}
			}
			if ((deletedlist.size() > 0) && deletedlist.contains(inputFile)) {
				Display.getDefault().asyncExec(new Runnable() {

					@Override
					public void run() {
						if ((getSite() == null) || (getSite().getWorkbenchWindow() == null)) {
							return;
						}
						final IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
						for (int i = 0; i < pages.length; i++) {
							final IEditorPart editorPart = pages[i].findEditor(input);
							pages[i].closeEditor(editorPart, true);
						}
					}
				});
			}
		}

		/*
		 * Closes all editors with this editor input on project close.
		 */
		final IResource res = event.getResource();
		if ((event.getType() == IResourceChangeEvent.PRE_CLOSE) || closeEditor) {
			Display.getDefault().asyncExec(new Runnable() {

				@Override
				public void run() {
					final IWorkbenchPartSite site = getSite();
					if (site == null) {
						return;
					}
					final IWorkbenchWindow workbenchWindow = site.getWorkbenchWindow();
					if (workbenchWindow == null) {
						return;
					}
					final IWorkbenchPage[] pages = workbenchWindow.getPages();
					for (int i = 0; i < pages.length; i++) {
						if (inputFile.getProject().equals(res)) {
							final IEditorPart editorPart = pages[i].findEditor(input);
							pages[i].closeEditor(editorPart, true);
						}
					}
				}
			});
		}
	}

	/**
	 * Open a dialog to save the model.
	 */
	public void saveModelForConsistentRenamings() {
		final LinkedList<String> editor = new LinkedList<>();
		editor.add(inputPath.getFileName().toString());

		final ListDialog dialog = new ListDialog(getSite().getWorkbenchWindow().getShell());
		dialog.setAddCancelButton(true);
		dialog.setContentProvider(new ArrayContentProvider());
		dialog.setLabelProvider(new LabelProvider());
		dialog.setInput(editor);
		dialog.setInitialElementSelections(editor);
		dialog.setTitle(SAVE_FEATURE_MODEL);
		dialog.setHelpAvailable(false);
		dialog.setMessage(MODEL_SHOULD_BE_SAVED_AFTER_RENAMINGS_);
		dialog.open();

		if (dialog.getResult() != null) {
			doSave(null);
		}
	}

	/**
	 * This is just a call to the private method {@link #setActivePage(int)}.
	 *
	 * @param index index to set
	 */
	public void setActiveEditorPage(int index) {
		setActivePage(index);
	}

	@Override
	public void setFocus() {
		if (getActivePage() == getDiagramEditorIndex()) {
			diagramEditor.getViewer().getControl().setFocus();
		} else {
			textEditor.setFocus();
		}
	}

	public void setPageModified(boolean modified) {
		if (!modified) {
			operationCounter = 0;
		}
		isPageModified = modified && (isPageModified || fmManager.hasChanged());
		firePropertyChange(PROP_DIRTY);
	}

	@Override
	protected void createPages() {
		if (fmManager == null) {
			addPage(new FeatureModelEditorErrorPage());
			setActivePage(0);
		} else {
			createActions();

			pages.clear();
			addPage(diagramEditor = new FeatureDiagramEditor(fmManager, gfm, true));
			addPage(featureOrderEditor = new FeatureOrderEditor(fmManager));
			createExtensionPages();
			addPage(textEditor = new FeatureModelTextEditorPage(this));

			extensionPages = pages.subList(2, pages.size() - 1);

			currentPageIndex = 0;
			// if there are errors in the model file, go to source page
			final ProblemList problems = checkModel(textEditor.getCurrentContent());
			if (problems.containsError()) {
				createModelFileMarkers(problems);
				setActivePage(textEditor.getIndex());
			} else {
				diagramEditor.getViewer().getControl().getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						if (!gfm.hasInitialLayout()) {
							diagramEditor.adjustModelToEditorSize();
						}
						pageChange(getDiagramEditorIndex());
						diagramEditor.getViewer().internRefresh(false);
						diagramEditor.analyzeFeatureModel();
					}
				});
			}
		}
	}

	private void addPage(IFeatureModelEditorPage page) {
		try {
			page.setIndex(addPage(page, getEditorInput()));
			setPageText(page.getIndex(), page.getPageText());
			page.initEditor();
		} catch (final PartInitException e) {
			FMUIPlugin.getDefault().logError(e);
		}
		pages.add(page);
	}

	private void createExtensionPages() {
		final IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(FMUIPlugin.PLUGIN_ID + ".FeatureModelEditor");
		for (final IConfigurationElement exec : config) {
			try {
				final Object o = exec.createExecutableExtension("class");
				if (o instanceof IFeatureModelEditorPage) {
					addPage(((IFeatureModelEditorPage) o));
				}
			} catch (final Exception e) {
				FMCorePlugin.getDefault().logError(e);
			}
		}
	}

	private void createActions() {
		final IOperationHistory history = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
		history.addOperationHistoryListener(new IOperationHistoryListener() {

			@Override
			public void historyNotification(OperationHistoryEvent event) {
				if (event.getOperation().hasContext((IUndoContext) fmManager.getUndoContext())) {
					if ((event.getEventType() == OperationHistoryEvent.DONE) || (event.getEventType() == OperationHistoryEvent.REDONE)) {
						operationCounter++;
					} else if (event.getEventType() == OperationHistoryEvent.UNDONE) {
						operationCounter--;
					} else if (operationCounter == 0) {
						setPageModified(false);
					}
				}
			}
		});
		final ObjectUndoContext undoContext = new ObjectUndoContext(this);
		fmManager.setUndoContext(undoContext);

		actions.add(new FMPrintAction(this));

		final SelectAllAction selectAllAction = new SelectAllAction(this);
		selectAllAction.setId(ActionFactory.SELECT_ALL.getId());
		actions.add(selectAllAction);

		final IWorkbenchPartSite site = getSite();

		final UndoActionHandler undoActionHandler = new UndoActionHandler(site, undoContext);
		undoActionHandler.setId(ActionFactory.UNDO.getId());
		actions.add(undoActionHandler);

		final RedoActionHandler redoActionHandler = new RedoActionHandler(site, undoContext);
		redoActionHandler.setId(ActionFactory.REDO.getId());
		actions.add(redoActionHandler);
	}

	@Override
	protected void handlePropertyChange(int propertyId) {
		if (propertyId == PROP_DIRTY) {
			isPageModified = fmManager.hasChanged();
			if (isPageModified) {
				gfm.writeValues();
			}
		}
		super.handlePropertyChange(propertyId);
	}

	@Override
	protected void pageChange(int newPageIndex) {
		final IFeatureModelEditorPage currentPage = getPage(currentPageIndex);
		if (currentPage.allowPageChange(newPageIndex)) {
			currentPage.pageChangeFrom(newPageIndex);
			getPage(newPageIndex).pageChangeTo(currentPageIndex);
			currentPageIndex = newPageIndex;
			super.pageChange(newPageIndex);
		} else {
			setActiveEditorPage(currentPageIndex);
		}
	}

	@Override
	protected void setInput(IEditorInput input) {
		super.setInput(input);

		final IFile modelFile;
		if (input instanceof FileStoreEditorInput) {
			modelFile = null;
			inputPath = Paths.get(((FileStoreEditorInput) input).getURI());
			inputProject = null;
			markerHandler = new EmptyMarkerHandler();
		} else {
			// Cast is necessary, don't remove
			modelFile = (IFile) input.getAdapter(IFile.class);
			if (modelFile != null) {
				inputPath = EclipseFileSystem.getPath(modelFile);
				inputProject = modelFile.getProject();
				markerHandler = new ModelMarkerHandler<>(modelFile);
			}
		}
		fmManager = FeatureModelManager.getInstance(inputPath);
		if (fmManager != null) {
			if (inputProject != null) {
				fmManager.getFormat().setFeatureNameValidator(FMComposerManager.getFMComposerExtension(inputProject));
			}

			createModelFileMarkers(fmManager.getLastProblems());
			gfm = new GraphicalFeatureModel(fmManager);
			gfm.init();
			FMPropertyManager.registerEditor(this);

			if (inputProject != null) {
				if (FMCorePlugin.findModelFile(inputProject).map(modelFile::equals).orElse(false)) {
					setPartName(inputProject.getName() + MODEL);
				} else {
					final String modelFileName = inputPath.getFileName().toString();
					setPartName(modelFileName + " (" + inputProject.getName() + ")");
				}
			}
		} else {
			setPartName(input.getName());
		}
		setTitleToolTip(input.getToolTipText());

		notifyFMEditorOpened();

		// TODO _Interfaces Removed Code
		// FeatureUIHelper.showHiddenFeatures(featureModel.getGraphicRepresenation().getLayout().showHiddenFeatures(), featureModel);
		// FeatureUIHelper.setVerticalLayoutBounds(featureModel.getGraphicRepresenation().getLayout().verticalLayout(), featureModel);

		// featureModel.getColorschemeTable().readColorsFromFile(file.getProject());
	}

	private void createModelFileMarkers(ProblemList warnings) {
		markerHandler.deleteAllModelMarkers();
		for (final Problem warning : warnings) {
			markerHandler.createModelMarker(warning.message, warning.severity.getLevel(), warning.line);
		}
		if (!warnings.containsError()) {

			// Check if automatic calculations are wanted
			if (!FeatureModelProperty.isRunCalculationAutomatically(fmManager.getVarObject())
				|| !FeatureModelProperty.isCalculateFeatures(fmManager.getVarObject())) {
				return;
			}
			if (!fmManager.getVariableFormula().getAnalyzer().isValid(null)) {
				markerHandler.createModelMarker(THE_FEATURE_MODEL_IS_VOID_COMMA__I_E__COMMA__IT_CONTAINS_NO_PRODUCTS, IMarker.SEVERITY_ERROR, 0);
			}
		}
	}

	/**
	 * Notify FeatureModelEditorListeners about the newly opened FeatureModelEditor.
	 */
	private void notifyFMEditorOpened() {
		final IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(FMUIPlugin.PLUGIN_ID + ".FMEditorListener");
		for (final IConfigurationElement c : config) {
			try {
				final Object o = c.createExecutableExtension("class");
				if (o instanceof IFeatureModelEditorListener) {
					((IFeatureModelEditorListener) o).editorOpened(this);
				}
			} catch (final Exception e) {
				FMCorePlugin.getDefault().logError(e);
			}
		}
	}

	private int getDiagramEditorIndex() {
		return diagramEditor != null ? diagramEditor.getIndex() : 0;
	}

	/**
	 * Gets the corresponding page for the given index.
	 *
	 * @param index The index of the page
	 * @return The page
	 */
	public IFeatureModelEditorPage getPage(int index) {
		for (final IFeatureModelEditorPage page : pages) {
			if (page.getIndex() == index) {
				return page;
			}
		}
		return null;
	}

	public void readModel(String newSource) {
		fmManager.readFromSource(newSource);
		gfm.readValues();
		createModelFileMarkers(fmManager.getLastProblems());
	}

	private boolean saveEditors() {
		if ((inputProject != null) && fmManager.getSnapshot().getRenamingsManager().isRenamed()) {
			final ArrayList<String> dirtyEditorFileNames = new ArrayList<>();
			final ArrayList<IEditorPart> dirtyEditors = new ArrayList<>();
			for (final IWorkbenchWindow window : getSite().getWorkbenchWindow().getWorkbench().getWorkbenchWindows()) {
				for (final IWorkbenchPage page : window.getPages()) {
					for (final IEditorReference editorRef : page.getEditorReferences()) {
						if (ConfigurationEditor.ID.equals(editorRef.getId())) {
							final IEditorPart editor = editorRef.getEditor(true);
							if (editor.isDirty()) {
								final IFile editorFile = Adapters.adapt(editor.getEditorInput(), IFile.class);
								if (editorFile.getProject().equals(inputProject)) {
									dirtyEditorFileNames.add(editorFile.getName());
									dirtyEditors.add(editor);
								}
							}
						}
					}
				}
			}

			if (!dirtyEditorFileNames.isEmpty()) {
				final ListDialog dialog = new ListDialog(getSite().getWorkbenchWindow().getShell());
				dialog.setAddCancelButton(true);
				dialog.setContentProvider(new ArrayContentProvider());
				dialog.setLabelProvider(new LabelProvider());
				dialog.setInput(dirtyEditorFileNames);
				dialog.setInitialElementSelections(dirtyEditorFileNames);
				dialog.setTitle(SAVE_RESOURCES);
				dialog.setHelpAvailable(false);
				dialog.setMessage(SOME_MODIFIED_RESOURCES_MUST_BE_SAVED_BEFORE_SAVING_THE_FEATUREMODEL_);
				dialog.open();

				if (dialog.getResult() != null) {
					for (final IEditorPart editor : dirtyEditors) {
						editor.doSave(null);
					}
				} else {
					// case: cancel pressed
					return false;
				}
			}
		}
		if (fmManager != null) {
			createModelFileMarkers(fmManager.getLastProblems());
		}

		return true;
	}

	@Override
	public void propertyChange(FeatureIDEEvent event) {
		if (getActivePage() == getDiagramEditorIndex()) {
			diagramEditor.propertyChange(event);
		}
	}

	public ProblemList checkModel(String source) {
		final ProblemList problemList = new ProblemList();
		final Path fileName = fmManager.getPath().getFileName();
		final IPersistentFormat<IFeatureModel> format = FMFormatManager.getInstance().getFormatByContent(source, fileName.toString());
		if (format != null) {
			try {
				problemList
						.addAll(format.getInstance().read(FMFactoryManager.getInstance().getFactory(fileName, format).create(), source, fmManager.getPath()));
			} catch (final NoSuchExtensionException e) {
				problemList.add(new Problem(e));
				Logger.logError(e);
			}
		}
		return problemList;
	}

	public void addEventListener(IEventListener listener) {
		if (fmManager == null) {
			return;
		}
		fmManager.addListener(listener);
	}

	public void removeEventListener(IEventListener listener) {
		if (fmManager == null) {
			return;
		}
		fmManager.removeListener(listener);
	}
}
