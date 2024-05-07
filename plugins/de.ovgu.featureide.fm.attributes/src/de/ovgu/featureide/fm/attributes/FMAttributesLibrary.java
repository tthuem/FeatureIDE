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
package de.ovgu.featureide.fm.attributes;

import de.ovgu.featureide.fm.attributes.base.impl.ExtendedConfigurationFactory;
import de.ovgu.featureide.fm.attributes.base.impl.ExtendedFeatureModelFactory;
import de.ovgu.featureide.fm.attributes.base.impl.ExtendedMultiFeatureModelFactory;
import de.ovgu.featureide.fm.attributes.format.UVLExtendedFeatureModelFormat;
import de.ovgu.featureide.fm.attributes.format.XmlExtendedConfFormat;
import de.ovgu.featureide.fm.attributes.format.XmlExtendedFeatureModelFormat;
import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.base.impl.ConfigFormatManager;
import de.ovgu.featureide.fm.core.base.impl.ConfigurationFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.init.ILibrary;

/**
 * The library object for the fm.attributes plug-in when using as a stand-alone library.
 *
 * @author Sebastian Krieter
 */
public class FMAttributesLibrary implements ILibrary {

	private static FMAttributesLibrary instance;

	public static FMAttributesLibrary getInstance() {
		if (instance == null) {
			instance = new FMAttributesLibrary();
		}
		return instance;
	}

	private FMAttributesLibrary() {}

	@Override
	public void install() {
		FMFormatManager.getInstance().addExtension(new XmlExtendedFeatureModelFormat());
		try {
			FMFormatManager.getInstance().addExtension(new UVLExtendedFeatureModelFormat());
		} catch (NoClassDefFoundError e) {
			Logger.logWarning("WARNING: Could not load Extended UVL format.");
		}
		FMFactoryManager.getInstance().addExtension(ExtendedFeatureModelFactory.getInstance());
		FMFactoryManager.getInstance().addExtension(ExtendedMultiFeatureModelFactory.getInstance());
		ConfigFormatManager.getInstance().addExtension(new XmlExtendedConfFormat());
		ConfigurationFactoryManager.getInstance().addExtension(ExtendedConfigurationFactory.getInstance());
	}

	@Override
	public void uninstall() {}

}
