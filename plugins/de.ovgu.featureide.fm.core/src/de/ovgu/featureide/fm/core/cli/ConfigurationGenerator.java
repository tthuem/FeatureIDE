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
package de.ovgu.featureide.fm.core.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.ClauseList;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.AllConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.IConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.PairWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.RandomConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.SPLCAToolConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.csv.ConfigurationListFormat;
import de.ovgu.featureide.fm.core.io.expression.ExpressionGroupFormat;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.manager.FileHandler;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.ConsoleMonitor;

/**
 * Command line interface for sampling algorithms.
 *
 * @author Sebastian Krieter
 */
public class ConfigurationGenerator extends ACLIFunction {

	private String algorithm;
	private Path outputFile;
	private Path fmFile;
	private Path expressionFile;
	private Path initialSampleFile;
	private boolean allowInitialSolutionModify = false;
	private boolean allowInitialSolutionRemove = false;
	private boolean countInitialSolutionForLimit = false;
	private int t;
	private int m;
	private int limit;

	@Override
	public String getId() {
		return "genconfig";
	}

	@Override
	public void run(List<String> args) {
		parseArguments(args);

		if (fmFile == null) {
			throw new IllegalArgumentException("No feature model specified!");
		}
		if (outputFile == null) {
			throw new IllegalArgumentException("No output file specified!");
		}
		if (algorithm == null) {
			throw new IllegalArgumentException("No algorithm specified!");
		}

		final FileHandler<IFeatureModel> fileHandler = FeatureModelManager.getFileHandler(fmFile);
		if (fileHandler.getLastProblems().containsError()) {
			throw new IllegalArgumentException(fileHandler.getLastProblems().getErrors().get(0).error);
		}
		final CNF cnf = new FeatureModelFormula(fileHandler.getObject()).getCNF();

		final ArrayList<List<ClauseList>> expressionGroups;
		if (expressionFile != null) {
			expressionGroups = new ArrayList<>();
			final ProblemList lastProblems = SimpleFileHandler.load(expressionFile, expressionGroups, new ExpressionGroupFormat());
			if (lastProblems.containsError()) {
				throw new IllegalArgumentException(lastProblems.getErrors().get(0).error);
			}
		} else {
			expressionGroups = null;
		}

		final SolutionList initialSample;
		if (initialSampleFile != null) {
			initialSample = new SolutionList();
			final ProblemList lastProblems = SimpleFileHandler.load(initialSampleFile, initialSample, new ConfigurationListFormat());
			if (lastProblems.containsError()) {
				throw new IllegalArgumentException(lastProblems.getErrors().get(0).error);
			}
		} else {
			initialSample = null;
		}

		IConfigurationGenerator generator = null;
		switch (algorithm.toLowerCase()) {
		case "icpl": {
			generator = new SPLCAToolConfigurationGenerator(cnf, "ICPL", t, limit);
			break;
		}
		case "chvatal": {
			generator = new SPLCAToolConfigurationGenerator(cnf, "Chvatal", t, limit);
			break;
		}
		case "incling": {
			generator = new PairWiseConfigurationGenerator(cnf, limit);
			break;
		}
		case "yasa": {
			if (expressionGroups == null) {
				generator = new TWiseConfigurationGenerator(cnf, t, limit);
			} else {
				generator = new TWiseConfigurationGenerator(cnf, expressionGroups, t, limit);
			}
			final TWiseConfigurationGenerator yasa = (TWiseConfigurationGenerator) generator;
			yasa.setIterations(m);
			if (initialSampleFile != null) {
				yasa.setInitialSample(initialSample.getSolutions());
				yasa.setAllowInitialSolutionModify(allowInitialSolutionModify);
				yasa.setAllowInitialSolutionRemove(allowInitialSolutionRemove);
				yasa.setCountInitialSolutionForLimit(countInitialSolutionForLimit);
			}
			break;
		}
		case "random": {
			generator = new RandomConfigurationGenerator(cnf, limit);
			break;
		}
		case "all": {
			generator = new AllConfigurationGenerator(cnf, limit);
			break;
		}
		default:
			throw new IllegalArgumentException("No algorithm specified!");
		}
		final List<LiteralSet> result = LongRunningWrapper.runMethod(generator, new ConsoleMonitor<>());
		if (result != null) {
			SimpleFileHandler.save(outputFile, new SolutionList(cnf.getVariables(), result), new ConfigurationListFormat());
		}
	}

	private void resetArguments() {
		algorithm = null;
		outputFile = null;
		fmFile = null;
		expressionFile = null;
		initialSampleFile = null;
		allowInitialSolutionModify = false;
		allowInitialSolutionRemove = false;
		countInitialSolutionForLimit = false;
		t = 1;
		m = 1;
		limit = Integer.MAX_VALUE;
	}

	private void parseArguments(List<String> args) {
		resetArguments();
		for (final Iterator<String> iterator = args.iterator(); iterator.hasNext();) {
			final String arg = iterator.next();
			if (arg.startsWith("-")) {
				switch (arg.substring(1)) {
				case "a": {
					algorithm = getArgValue(iterator, arg);
					break;
				}
				case "e": {
					expressionFile = Paths.get(getArgValue(iterator, arg));
					break;
				}
				case "fm": {
					fmFile = Paths.get(getArgValue(iterator, arg));
					break;
				}
				case "i": {
					initialSampleFile = Paths.get(getArgValue(iterator, arg));
					break;
				}
				case "im": {
					allowInitialSolutionModify = true;
					break;
				}
				case "ir": {
					allowInitialSolutionRemove = true;
					break;
				}
				case "il": {
					countInitialSolutionForLimit = true;
					break;
				}
				case "l": {
					limit = Integer.parseInt(getArgValue(iterator, arg));
					break;
				}
				case "m": {
					m = Integer.parseInt(getArgValue(iterator, arg));
					break;
				}
				case "o": {
					outputFile = Paths.get(getArgValue(iterator, arg));
					break;
				}
				case "t": {
					t = Integer.parseInt(getArgValue(iterator, arg));
					break;
				}
				default: {
					throw new IllegalArgumentException(arg);
				}
				}
			} else {
				throw new IllegalArgumentException(arg);
			}
		}
	}

	private String getArgValue(final Iterator<String> iterator, final String arg) {
		if (iterator.hasNext()) {
			return iterator.next();
		} else {
			throw new IllegalArgumentException("No value specified for " + arg);
		}
	}

}
