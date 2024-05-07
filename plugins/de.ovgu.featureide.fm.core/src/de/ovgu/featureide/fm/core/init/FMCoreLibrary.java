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
package de.ovgu.featureide.fm.core.init;

import de.ovgu.featureide.fm.core.JavaLogger;
import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.base.impl.ConfigFormatManager;
import de.ovgu.featureide.fm.core.base.impl.ConfigurationFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.CoreFactoryWorkspaceLoader;
import de.ovgu.featureide.fm.core.base.impl.DefaultConfigurationFactory;
import de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.base.impl.MultiFeatureModelFactory;
import de.ovgu.featureide.fm.core.cli.CLIFunctionManager;
import de.ovgu.featureide.fm.core.cli.ConfigurationGenerator;
import de.ovgu.featureide.fm.core.configuration.DefaultFormat;
import de.ovgu.featureide.fm.core.configuration.EquationFormat;
import de.ovgu.featureide.fm.core.configuration.ExpressionFormat;
import de.ovgu.featureide.fm.core.configuration.FeatureIDEFormat;
import de.ovgu.featureide.fm.core.configuration.XMLConfFormat;
import de.ovgu.featureide.fm.core.io.FileSystem;
import de.ovgu.featureide.fm.core.io.JavaFileSystem;
import de.ovgu.featureide.fm.core.io.cnf.CNFFormat;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormat;
import de.ovgu.featureide.fm.core.io.splconquerer.ConquererFMWriter;
import de.ovgu.featureide.fm.core.io.sxfm.SXFMFormat;
import de.ovgu.featureide.fm.core.io.uvl.UVLFeatureModelFormat;
import de.ovgu.featureide.fm.core.io.velvet.SimpleVelvetFeatureModelFormat;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import de.ovgu.featureide.fm.core.job.LongRunningCore;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;

/**
 * The library object for the fm.core plug-in when using as a stand-alone library.
 *
 * @author Sebastian Krieter
 */
public final class FMCoreLibrary implements ILibrary {

	private static FMCoreLibrary instance;

	public static FMCoreLibrary getInstance() {
		if (instance == null) {
			instance = new FMCoreLibrary();
		}
		return instance;
	}

	private FMCoreLibrary() {}

	@Override
	public void install() {
		FileSystem.INSTANCE = new JavaFileSystem();
		LongRunningWrapper.INSTANCE = new LongRunningCore();
		Logger.logger = new JavaLogger();

		FMFactoryManager.getInstance().addExtension(DefaultFeatureModelFactory.getInstance());
		FMFactoryManager.getInstance().addExtension(MultiFeatureModelFactory.getInstance());
		FMFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());

		FMFormatManager.getInstance().addExtension(new XmlFeatureModelFormat());
		try {
			FMFormatManager.getInstance().addExtension(new SimpleVelvetFeatureModelFormat());
		} catch (final NoClassDefFoundError e) {
			Logger.logWarning("Could not load Simple Velvet format.");
		}
		FMFormatManager.getInstance().addExtension(new DIMACSFormat());
		FMFormatManager.getInstance().addExtension(new SXFMFormat());
		FMFormatManager.getInstance().addExtension(new ConquererFMWriter());
		FMFormatManager.getInstance().addExtension(new CNFFormat());
		try {
			FMFormatManager.getInstance().addExtension(new UVLFeatureModelFormat());
		} catch (final NoClassDefFoundError e) {
			Logger.logWarning("Could not load UVL format.");
		}

		ConfigurationFactoryManager.getInstance().addExtension(DefaultConfigurationFactory.getInstance());
		ConfigurationFactoryManager.getInstance().setWorkspaceLoader(new CoreFactoryWorkspaceLoader());

		ConfigFormatManager.getInstance().addExtension(new XMLConfFormat());
		ConfigFormatManager.getInstance().addExtension(new DefaultFormat());
		ConfigFormatManager.getInstance().addExtension(new FeatureIDEFormat());
		ConfigFormatManager.getInstance().addExtension(new EquationFormat());
		ConfigFormatManager.getInstance().addExtension(new ExpressionFormat());

		CLIFunctionManager.getInstance().addExtension(new ConfigurationGenerator());
	}

	@Override
	public void uninstall() {
		FMFactoryManager.getInstance().save();
		ConfigurationFactoryManager.getInstance().save();
	}

}
