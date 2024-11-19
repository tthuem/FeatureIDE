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
package de.ovgu.featureide.fm.ui.editors.configuration;

import static de.ovgu.featureide.fm.core.localization.StringTable.CONFIGURATION;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.color.ColorPalette;
import de.ovgu.featureide.fm.core.color.FeatureColor;
import de.ovgu.featureide.fm.core.color.FeatureColorManager;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.SelectableFeature;
import de.ovgu.featureide.fm.core.configuration.Selection;
import de.ovgu.featureide.fm.core.io.manager.ConfigurationManager;
import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.GraphicsExporter;
import de.ovgu.featureide.fm.ui.properties.FMPropertyManager;

/**
 * Displays the tree for common configuration selection at the configuration editor
 *
 * @author Jens Meinicke
 * @author Hannes Smurawsky
 * @author Marcus Pinnecke
 */
public class ConfigurationPage extends ConfigurationTreeEditorPage {

	private static final String ID = FMUIPlugin.PLUGIN_ID + "ConfigurationPage";
	private static final String PAGE_TEXT = CONFIGURATION;

	@Override
	protected void createUITree(Composite parent) {
		tree = new Tree(parent, SWT.CHECK);

		final MenuManager contextMenu = new MenuManager(null);
		contextMenu.setRemoveAllWhenShown(true);
		contextMenu.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager mgr) {
				contextMenu.add(new Action("Export As...", FMPropertyManager.IMAGE_EXPORT_AS) {

					@Override
					public void run() {
						GraphicsExporter.exportAs(configurationEditor.getConfigurationManager().getSnapshot());
					}
				});
			}
		});

		final Menu createContextMenu = contextMenu.createContextMenu(tree);

		tree.addMouseListener(new MouseListener() {

			@Override
			public void mouseDown(MouseEvent e) {
				if (tree.getItem(new Point(e.x, e.y)) != null) {
					tree.setMenu(null);
				} else {
					tree.setMenu(createContextMenu);
				}
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {}

			@Override
			public void mouseUp(MouseEvent e) {}
		});

		tree.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {}

			@Override
			public void widgetSelected(SelectionEvent event) {
				if (event.detail == SWT.CHECK) {
					final TreeItem item = (TreeItem) event.item;
					final Object data = item.getData();
					if (data instanceof SelectableFeature) {
						final SelectableFeature feature = (SelectableFeature) item.getData();
						if (updateFeatures.contains(feature)) {
							item.setChecked(true);
						} else {
							switch (feature.getAutomatic()) {
							case SELECTED:
								item.setChecked(true);
								break;
							case UNSELECTED:
								item.setChecked(false);
								break;
							case UNDEFINED:
								changeSelection(item, true);
								break;
							}
						}
					}
				}
			}
		});
	}

	@Override
	public String getID() {
		return ID;
	}

	@Override
	public String getPageText() {
		return PAGE_TEXT;
	}

	@Override
	public void pageChangeTo(int index) {
		final ConfigurationManager configurationManager = configurationEditor.getConfigurationManager();
		if (configurationManager != null) {
			configurationManager.editObject(this::resetAbstractDeselected, ConfigurationManager.CHANGE_ALL);
		}
		super.pageChangeTo(index);
	}

	private void resetAbstractDeselected(final Configuration configuration) {
		for (final SelectableFeature feature : configuration.getFeatures()) {
			if ((feature.getAutomatic() == Selection.UNDEFINED) && (feature.getManual() == Selection.UNSELECTED)) {
				configuration.setManual(feature, Selection.UNDEFINED);
			}
		}
	}

	@Override
	protected Image getImage(SelectableFeature selFeature, Selection selection) {
		final IFeature feature = selFeature.getFeature();

		final FeatureColor color = FeatureColorManager.getColor(feature);
		final String imageString = color != null ? color.getColorName() : "";
		Image combinedImage = combinedImages.get(imageString);

		if (combinedImage == null) {
			final int distance = 4;
			final int colorWidth = 24;
			final int colorHeight = 12;

			Image tempImage = null;
			GC gc = null;
			try {
				tempImage = new Image(Display.getCurrent(), distance + colorWidth + distance, colorHeight + 2);
				final ImageData id = tempImage.getImageData();
				id.alpha = 0;

				combinedImage = new Image(Display.getCurrent(), id);
				gc = new GC(combinedImage);

				if (color != FeatureColor.NO_COLOR) {
					final Color bgColor = new Color(null, ColorPalette.getRGB(color.getValue(), 0.5f));
					try {
						gc.setBackground(bgColor);
						gc.fillRoundRectangle(distance, 1, colorWidth, colorHeight, colorHeight, colorHeight);
					} finally {
						bgColor.dispose();
					}
				} else {
					gc.setForeground(FMPropertyManager.getLegendBorderColor());
					gc.drawRoundRectangle(distance, 1, colorWidth, colorHeight, colorHeight, colorHeight);
				}

				combinedImages.put(imageString, combinedImage);
			} finally {
				if (gc != null) {
					gc.dispose();
				}
				if (tempImage != null) {
					tempImage.dispose();
				}
			}
		}

		return combinedImage;
	}

}
