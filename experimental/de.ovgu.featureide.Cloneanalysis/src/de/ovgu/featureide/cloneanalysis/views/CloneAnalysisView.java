package de.ovgu.featureide.cloneanalysis.views;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import de.ovgu.featureide.cloneanalysis.results.CloneAnalysisResults;
import de.ovgu.featureide.cloneanalysis.results.FeatureRootLocation;
import de.ovgu.featureide.cloneanalysis.results.VariantAwareClone;

public class CloneAnalysisView extends ViewPart
{

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "de.ovgu.featureide.code.cloneanalysis.views.CloneAnalysisView";

	public static int hiddenEntries = 0, totalEntries = 0; 

	private Tree cloneTree;
	private TreeViewer cloneViewer;

	private Text text;
	private String fileName;
	IPath path;
	IFile ifile;
	Path filePath;
	File file;
	private HashMap<String,String> fileLocations;

	CloneAnalysisResults<VariantAwareClone> results = null;

	private Action action1;
	private Action action2;
	private Action doubleClickAction;

	private HashSet<Action> filterActions;

	class NameSorter extends ViewerSorter
	{}

	/**
	 * The constructor.
	 */
	public CloneAnalysisView()
	{
	}

	public void updateAnalysis(IStructuredSelection selection)
	{}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent)
	{
		cloneTree = new Tree(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		cloneViewer = new TreeViewer(cloneTree);
		cloneViewer.setContentProvider(new CloneAnalysisContentProvider(this));
		cloneViewer.setLabelProvider(new CloneAnalysisLabelProvider());
		cloneViewer.setSorter(new NameSorter());
		cloneViewer.setInput(getViewSite());
		cloneViewer.setComparator(new SizeComparator());

		PlatformUI.getWorkbench().getHelpSystem()
		.setHelp(cloneViewer.getControl(), "de.ovgu.featureide.code.Cloneanalysis.viewer");

		addColumns();
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
	}

	private void addColumns()
	{
		cloneTree.setLinesVisible(true);
		cloneTree.setHeaderVisible(true);

		TreeColumn column1 = new TreeColumn(cloneTree, SWT.LEFT);
		column1.setAlignment(SWT.LEFT);
		column1.setText(CloneAnalysisTreeColumn.CLONE_OR_OCCURENCE.toString());
		column1.setWidth(250);

		TreeColumn column15 = new TreeColumn(cloneTree, SWT.LEFT);
		column15.setAlignment(SWT.LEFT);
		column15.setText(CloneAnalysisTreeColumn.SIZE.toString());
		column15.setWidth(50);
		cloneTree.setSortColumn(column15);
		cloneTree.setSortDirection(SWT.UP);
		column15.addSelectionListener(new SortTreeListener());

		TreeColumn column2 = new TreeColumn(cloneTree, SWT.RIGHT);
		column2.setAlignment(SWT.LEFT);
		column2.setText(CloneAnalysisTreeColumn.LENGTH.toString());
		column2.setWidth(50);
		column2.addSelectionListener(new SortTreeListener());

		TreeColumn column3 = new TreeColumn(cloneTree, SWT.RIGHT);
		column3.setAlignment(SWT.LEFT);
		column3.setText(CloneAnalysisTreeColumn.TOKEN_COUNT.toString());
		column3.setWidth(50);
		column3.addSelectionListener(new SortTreeListener());

		TreeColumn column4 = new TreeColumn(cloneTree, SWT.RIGHT);
		column4.setAlignment(SWT.LEFT);
		column4.setText(CloneAnalysisTreeColumn.FILES_AFFECTED_COUNT.toString());
		column4.setWidth(50);
		column4.addSelectionListener(new SortTreeListener());

		TreeColumn column5 = new TreeColumn(cloneTree, SWT.RIGHT);
		column5.setAlignment(SWT.LEFT);
		column5.setText(CloneAnalysisTreeColumn.VARIANT_TYPE.toString());
		column5.setWidth(150);

		TreeColumn column6 = new TreeColumn(cloneTree, SWT.RIGHT);
		column6.setAlignment(SWT.LEFT);
		column6.setText(CloneAnalysisTreeColumn.PROJECT_FEATURE.toString());
		column6.setWidth(150);

	}

	private void hookContextMenu()
	{
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener()
		{
			public void menuAboutToShow(IMenuManager manager)
			{
				CloneAnalysisView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(cloneViewer.getControl());
		cloneViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, cloneViewer);
	}

	private void contributeToActionBars()
	{
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager)
	{
		manager.add(action1);
		manager.add(action2);
		manager.add(new Separator());
	}

	private void fillContextMenu(IMenuManager manager)
	{
		manager.add(action1);
		manager.add(action2);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager)
	{
		manager.add(action1);
		manager.add(action2);
	}

	private void makeActions()
	{
		action1 = new Action()
		{
			public void run()
			{
				showMessage("Action 1 executed");
			}
		};
		action1.setText("Action 1");
		action1.setToolTipText("Action 1 tooltip");
		action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));

		action2 = new Action()
		{
			public void run()
			{
				showMessage("Action 2 executed");
			}
		};
		action2.setText("Action 2");
		action2.setToolTipText("Action 2 tooltip");
		action2.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		
//		doubleClickAction = new Action()
//		{
//			public void run()
//			{
//				// TreeItem<VariantAwareClone> selectedItem =
//				// matchViewer.getSelectionModel().getSelectedItem();
//				// showMessage("Double-click detected on " +
//				// selectedItem.toString());
//			}
//	};
	}

	private void hookDoubleClickAction()
	{
		cloneViewer.addDoubleClickListener(new IDoubleClickListener()
		{
			public void doubleClick(DoubleClickEvent event)
			{
//				doubleClickAction.run();
				String selectedItem = null,selected = null;
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				selectedItem = selection.toString();
				selectedItem = selectedItem.replaceAll("[\\[\\]]", "");
				selected = selectedItem.substring(selectedItem.lastIndexOf(":"));
				selectedItem = selectedItem.replaceAll(selected,"");
				System.out.println(selectedItem);
				fileName = selectedItem;
				if(fileLocations.containsKey(fileName)){
					String mapValue = fileLocations.get(fileName);
					File fileToOpen = new File(mapValue);
					if (fileToOpen.exists() && fileToOpen.isFile()) {
						IFileStore fileStore = EFS.getLocalFileSystem().getStore(fileToOpen.toURI());
						IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

						try {
							IDE.openEditorOnFileStore( page, fileStore );
						} catch ( PartInitException exception ) {
						}
					} else {
						showMessage("File does not exist");
					}
				}
				
			}
		});
	}

	private void showMessage(String message)
	{
		MessageDialog.openInformation(cloneViewer.getControl().getShell(), "Sample View", message);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus()
	{
		cloneViewer.getControl().setFocus();
	}
	//	public void showResults(CloneAnalysisResults<VariantAwareClone> formattedResults)
	public void showResults(CloneAnalysisResults<VariantAwareClone> formattedResults, HashMap<String,String> fileLocations)
	{
		this.fileLocations = fileLocations;
		
		results = formattedResults;
		createFilterActions();
		cloneViewer.setInput(results);
		cloneViewer.expandToLevel(1);
		cloneViewer.refresh();
	}

	private void createFilterActions()
	{
		clearPreviousFilters();
		filterActions = new HashSet<Action>();

		for (FeatureRootLocation feature : results.getRelevantFeatures())
		{
			Action filterByFeatureAction = createFilterAction(feature);
			filterActions.add(filterByFeatureAction);
		}

		updateActionBars();
	}

	private void clearPreviousFilters()
	{
		for(ViewerFilter filter : cloneViewer.getFilters()) 
		{
			if(filter instanceof FeatureFilter)
			{
				cloneViewer.removeFilter(filter);
			}
		}

		if(filterActions!= null && !filterActions.isEmpty())
		{
			IActionBars bars = getViewSite().getActionBars();
			for(Action filterAction : filterActions)
			{
				bars.getMenuManager().remove(filterAction.getId());
				bars.getToolBarManager().remove(filterAction.getId());
			}
		}
	}

	/**
	 * 
	 */
	private Action createFilterAction(final FeatureRootLocation feature)
	{
		final String name = feature.getLocation().lastSegment();
		final String tooltipText = "Filter by Feature: " + name;
		Action newAction = new Action()
		{
			public void run()
			{
				hiddenEntries = 0; totalEntries = 0;
				if(toggleFeatureFilter(feature))
					showMessage("Only showing clones including feature " + name + " now");
				else
					showMessage("No longer filtering by feature " + name);
				System.out.println("total: " + totalEntries + " hidden: " + hiddenEntries);
			}
		};
		newAction.setText(name);
		newAction.setToolTipText(tooltipText);
		newAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));

		return newAction;
	}

	protected boolean toggleFeatureFilter(FeatureRootLocation feature)
	{
		for(ViewerFilter filter : cloneViewer.getFilters()) 
		{
			if(filter instanceof FeatureFilter)
			{
				if(((FeatureFilter)filter).getFeature().equals(feature))
				{
					cloneViewer.removeFilter(filter);
					return false;
				}
			}
		}

		applyFeatureFilter(feature);
		return true;
	}

	protected void applyFeatureFilter(FeatureRootLocation feature)
	{
		ViewerFilter[] filters = new ViewerFilter[cloneViewer.getFilters().length+1];
		for(int i = 0; i<cloneViewer.getFilters().length; i++)
			filters[i] = cloneViewer.getFilters()[i];

		filters[cloneViewer.getFilters().length] = new FeatureFilter(feature);
		cloneViewer.setFilters(filters);
	}

	private void updateActionBars()
	{
		if (filterActions == null)
			return;

		IActionBars bars = getViewSite().getActionBars();

		for (Action filterAction : filterActions)
		{
			bars.getMenuManager().add(filterAction);
			bars.getToolBarManager().add(filterAction);
		}
	}
}