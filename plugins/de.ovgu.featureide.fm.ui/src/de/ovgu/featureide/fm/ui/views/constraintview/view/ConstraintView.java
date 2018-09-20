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
package de.ovgu.featureide.fm.ui.views.constraintview.view;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import de.ovgu.featureide.fm.core.base.IConstraint;

/**
 * TODO description
 *
 * @author "Rosiak Kamil"
 * @author "Domenik Eichhorn"
 */
public class ConstraintView {

	private final String CONSTRAINT_HEADER = "Constraint";
	private final String DESCRIPTION_HEADER = "Description";
	private TableViewer viewer;
	private Table table;

	public ConstraintView(Composite parent) {
		init(parent);
	}

	public void addItem(IConstraint element) {
		// add to table:
		viewer.add(element);
	}

	public void colorTable() {
		for (int i = 0; i < table.getItemCount(); i++) {
			if ((i % 2) == 1) {
				viewer.getTable().getItem(i).setBackground(new Color(table.getParent().getDisplay(), 235, 235, 235));
			}
		}

	}

	public void removeItem(IConstraint element) {
		viewer.remove(element);
	}

	public TableViewer getViewer() {
		return viewer;
	}

	public void removeAll() {
		viewer.getTable().removeAll();
	}

	private void init(Composite parent) {
		parent.setLayout(new FillLayout(SWT.HORIZONTAL));
		viewer = new TableViewer(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		table = viewer.getTable();
		table.setHeaderBackground(new Color(Display.getDefault(), 207, 207, 207));
		table.setHeaderForeground(new Color(Display.getDefault(), 0, 0, 0));
		addColumns(viewer);
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		addTableLayout(viewer);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

	}

	private void addTableLayout(TableViewer viewer) {
		final TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(60, true));
		layout.addColumnData(new ColumnWeightData(40, 800, true));
		viewer.getTable().setLayout(layout);
	}

	private void addColumns(TableViewer viewer) {
		final TableViewerColumn constraintViewerColumn = new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn constraintColumn = constraintViewerColumn.getColumn();
		constraintColumn.setText(CONSTRAINT_HEADER);
		addConstraintColumnProvider(constraintViewerColumn);

		final TableViewerColumn descriptionViewerColumn = new TableViewerColumn(viewer, SWT.NONE);

		final TableColumn descriptionColumn = descriptionViewerColumn.getColumn();
		descriptionColumn.setText(DESCRIPTION_HEADER);
		addDescriptionColumnProvider(descriptionViewerColumn);
	}

	private void addConstraintColumnProvider(TableViewerColumn viewerColumn) {
		viewerColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				// reformats the DisplayName with logical element from unicode
				String displayName = ((IConstraint) element).getDisplayName();
				displayName = displayName.replace("|", "\u2228");
				displayName = displayName.replace("<=>", "\u21D4");
				displayName = displayName.replace("=>", "\u21D2");
				displayName = displayName.replace("&", "\u2227");
				displayName = displayName.replace("-", "\u00AC");
				return super.getText(displayName);
			}
		});
	}

	private void addDescriptionColumnProvider(TableViewerColumn viewerColumn) {
		viewerColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return super.getText(((IConstraint) element).getDescription());
			}
		});
	}

}
