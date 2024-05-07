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
package de.ovgu.featureide.fm.core.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import de.ovgu.featureide.Commons;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.RandomConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.SampleTester;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseCoverageCriterion;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.csv.ConfigurationListFormat;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.manager.FileHandler;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.ConsoleMonitor;

/**
 * Tests sampling algorithms.
 *
 * @author Sebastian Krieter
 */
public class ConfigurationGeneratorTest {

	private final static Path modelDirectory = Commons.getRemoteOrLocalFolder(Commons.TEST_FEATURE_MODEL_PATH).toPath();

	private final List<String> modelNames = Arrays.asList( //
			"basic", //
			"simple", //
			"car", //
			"gpl_medium_model");

	@Test
	public void AllCoverage() {
		testSize("basic", "all", 1);
		testSize("simple", "all", 2);
		testSize("car", "all", 7);
		testSize("gpl_medium_model", "all", 960);
	}

	@Test
	public void YASAHas0ConfigurationsForUnsatisfiableModel() {
		testSize("unsatisfiable", "yasa", 0);
	}

	@Test
	public void AllLimit() {
		testLimitedSize("basic", "all", 1, 0);
		testLimitedSize("basic", "all", 1, 1);
		testLimitedSize("basic", "all", 1, 2);
		testLimitedSize("simple", "all", 2, 1);
		testLimitedSize("simple", "all", 2, 2);
		testLimitedSize("simple", "all", 2, 3);
		testLimitedSize("car", "all", 7, 0);
		testLimitedSize("car", "all", 7, 1);
		testLimitedSize("car", "all", 7, 5);
		testLimitedSize("car", "all", 7, 7);
		testLimitedSize("car", "all", 7, 10);
		testLimitedSize("car", "all", 7, Integer.MAX_VALUE);
		testLimitedSize("gpl_medium_model", "all", 960, 10);
		testLimitedSize("gpl_medium_model", "all", 960, 960);
		testLimitedSize("gpl_medium_model", "all", 960, Integer.MAX_VALUE);
	}

	@Test
	public void RandomLimit() {
		testLimitedSize("basic", "random", 1, 0);
		testLimitedSize("basic", "random", 1, 1);
		testLimitedSize("basic", "random", 1, 2);
		testLimitedSize("simple", "random", 2, 1);
		testLimitedSize("simple", "random", 2, 2);
		testLimitedSize("simple", "random", 2, 3);
		testLimitedSize("car", "random", 7, 0);
		testLimitedSize("car", "random", 7, 1);
		testLimitedSize("car", "random", 7, 5);
		testLimitedSize("car", "random", 7, 7);
		testLimitedSize("car", "random", 7, 10);
		testLimitedSize("car", "random", 7, Integer.MAX_VALUE);
		testLimitedSize("gpl_medium_model", "random", 960, 10);
		testLimitedSize("gpl_medium_model", "random", 960, 960);
		testLimitedSize("gpl_medium_model", "random", 960, Integer.MAX_VALUE);
		testLimitedSize("apl_model", "random", 100, 100);
	}

	@Test
	public void ChvatalLimit() {
		testTWiseLimitedSize("gpl_medium_model", "chvatal", 1, 5);
		testTWiseLimitedSize("gpl_medium_model", "chvatal", 2, 5);
		testTWiseLimitedSize("gpl_medium_model", "chvatal", 3, 5);
	}

	@Test
	public void ICPLLimit() {
		testTWiseLimitedSize("gpl_medium_model", "icpl", 1, 5);
		testTWiseLimitedSize("gpl_medium_model", "icpl", 2, 5);
		testTWiseLimitedSize("gpl_medium_model", "icpl", 3, 5);
	}

	@Test
	public void InclingLimit() {
		testTWiseLimitedSize("gpl_medium_model", "incling", 1, 5);
		testTWiseLimitedSize("gpl_medium_model", "incling", 2, 5);
		testTWiseLimitedSize("gpl_medium_model", "incling", 3, 5);
	}

	@Test
	public void YASALimit() {
		testTWiseLimitedSize("gpl_medium_model", "yasa", 1, 5);
		testTWiseLimitedSize("gpl_medium_model", "yasa", 2, 5);
		testTWiseLimitedSize("gpl_medium_model", "yasa", 3, 5);
	}

	@Test
	public void YASAInitialSample() {
		testInitialTWise("apl_model", 1, 5);
		testInitialTWise("apl_model", 2, 5);
		testInitialTWise("apl_model", 3, 5);
		testInitialTWise("apl_model", 2, 0);
		testInitialTWise("apl_model", 2, 30);
		testInitialTWise("gpl_medium_model", 1, 5);
		testInitialTWise("gpl_medium_model", 2, 5);
		testInitialTWise("gpl_medium_model", 3, 5);
		testInitialTWise("gpl_medium_model", 2, 0);
		testInitialTWise("gpl_medium_model", 2, 30);
		testInitialTWise("berkeley_db_model", 2, 10);
	}

	@Test
	public void YASAInitialPartialSample() {
		testPartialInitialTWise("apl_model", 1, 5);
		testPartialInitialTWise("apl_model", 2, 0);
		testPartialInitialTWise("apl_model", 2, 5);
		testPartialInitialTWise("apl_model", 3, 5);
		testPartialInitialTWise("apl_model", 2, 30);
		testPartialInitialTWise("gpl_medium_model", 1, 5);
		testPartialInitialTWise("gpl_medium_model", 2, 5);
		testPartialInitialTWise("gpl_medium_model", 3, 5);
		testPartialInitialTWise("gpl_medium_model", 2, 0);
		testPartialInitialTWise("gpl_medium_model", 2, 30);
		testPartialInitialTWise("berkeley_db_model", 2, 10);
	}

	@Test
	public void YASAOneWiseCoverage() {
		testCoverageAndDeterminism("yasa", 1, modelNames);
	}

	@Test
	public void YASATwoWiseCoverage() {
		testCoverageAndDeterminism("yasa", 2, modelNames);
	}

	@Test
	public void YASAThreeWiseCoverage() {
		testCoverageAndDeterminism("yasa", 3, modelNames);
	}

	@Test
	public void InclingTwoWiseCoverage() {
		testCoverageAndDeterminism("incling", 2, modelNames);
	}

//	@Test
//	public void ICPLOneWiseCoverage() {
//		testCoverage("icpl", 1, modelNames);
//	}

	@Test
	public void ICPLTwoWiseCoverage() {
		testCoverage("icpl", 2, modelNames);
	}

	@Test
	public void ICPLThreeWiseCoverage() {
		testCoverage("icpl", 3, modelNames);
	}

//	@Test
//	public void ChvatalOneWiseCoverage() {
//		testCoverage("chvatal", 1, modelNames);
//	}

	@Test
	public void ChvatalTwoWiseCoverage() {
		testCoverage("chvatal", 2, modelNames);
	}

	@Test
	public void ChvatalThreeWiseCoverage() {
		testCoverage("chvatal", 3, modelNames);
	}

	private void testCoverage(final String algorithmName, final int t, final List<String> modelNameList) {
		for (final String modelName : modelNameList) {
			final Path modelFile = modelDirectory.resolve(modelName + ".xml");
			final SampleTester tester = sample(modelFile, algorithmName, Arrays.asList("-t", Integer.toString(t)));
			assertFalse("Invalid solutions for " + modelName, tester.hasInvalidSolutions());
			assertEquals("Wrong coverage for " + modelName, 1.0, tester.getCoverage(new TWiseCoverageCriterion(tester.getCnf(), t)), 0.0);
		}
	}

	private void testCoverageAndDeterminism(final String algorithmName, final int t, final List<String> modelNameList) {
		for (final String modelName : modelNameList) {
			final Path modelFile = modelDirectory.resolve(modelName + ".xml");
			final SampleTester tester = sample(modelFile, algorithmName, Arrays.asList("-t", Integer.toString(t)));
			assertFalse("Invalid solutions for " + modelName, tester.hasInvalidSolutions());
			assertEquals("Wrong coverage for " + modelName, 1.0, tester.getCoverage(new TWiseCoverageCriterion(tester.getCnf(), t)), 0.0);
			final SampleTester tester2 = sample(modelFile, algorithmName, Arrays.asList("-t", Integer.toString(t)));
			assertEquals("Wrong size for " + modelName, tester.getSize(), tester2.getSize());
		}
	}

	private static void testSize(String modelName, String algorithm, int numberOfConfigurations) {
		final Path modelFile = modelDirectory.resolve(modelName + ".xml");
		final SampleTester tester = sample(modelFile, algorithm, Collections.emptyList());
		assertFalse("Invalid solutions for " + modelName, tester.hasInvalidSolutions());
		assertEquals("Wrong number of configurations for " + modelName, numberOfConfigurations, tester.getSize());
	}

	private static void testLimitedSize(String modelName, String algorithm, int numberOfConfigurations, int limit) {
		final Path modelFile = modelDirectory.resolve(modelName + ".xml");
		final SampleTester tester = sample(modelFile, algorithm, Arrays.asList("-l", Integer.toString(limit)));
		assertFalse("Invalid solutions for " + modelName, tester.hasInvalidSolutions());
		assertTrue("Number of configurations larger than limit for " + modelName, limit >= tester.getSize());
	}

	private static void testTWiseLimitedSize(String modelName, String algorithm, int t, int limit) {
		final Path modelFile = modelDirectory.resolve(modelName + ".xml");
		final SampleTester tester = sample(modelFile, algorithm, Arrays.asList("-t", Integer.toString(t), "-l", Integer.toString(limit)));
		assertFalse("Invalid solutions for " + modelName, tester.hasInvalidSolutions());
		assertTrue("Number of configurations (" + tester.getSize() + ") larger than limit (" + limit + ") for " + modelName, limit >= tester.getSize());
	}

	private static void testInitialTWise(String modelName, int t, int initialSize) {
		final Path modelFile = modelDirectory.resolve(modelName + ".xml");
		try {
			final Path sampleFile = runSampleAlgorithm(modelFile, "random", Arrays.asList("-l", Integer.toString(initialSize)));
			final SolutionList sample = new SolutionList();
			FileHandler.load(sampleFile, sample, new ConfigurationListFormat());
			final SampleTester tester = sample(modelFile, "yasa", Arrays.asList("-t", Integer.toString(t), "-m", "5", "-i", sampleFile.toString()));
			assertFalse("Invalid solutions for " + modelName, tester.hasInvalidSolutions());
			for (int i = 0; i < initialSize; i++) {
				final LiteralSet initialConfig = sample.getSolutions().get(i);
				final LiteralSet newConfig = tester.getSample().get(i);
				assertEquals("Initial sample is changed for " + initialConfig, initialConfig, newConfig);
			}
			assertEquals("Initial sample is changed for " + sample.getSolutions(), sample.getSolutions(), tester.getSample().subList(0, initialSize));
		} catch (final IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private static void testPartialInitialTWise(String modelName, int t, int initialSize) {
		final Path modelFile = modelDirectory.resolve(modelName + ".xml");
		try {
			final CNF cnf = new FeatureModelFormula(FeatureModelManager.load(modelFile)).getCNF();

			final RandomConfigurationGenerator generator = new RandomConfigurationGenerator(cnf, initialSize);
			generator.setRandom(new Random(123));
			final List<LiteralSet> sample = LongRunningWrapper.runMethod(generator, new ConsoleMonitor<>());

			for (int repetition = 0; repetition < 5; repetition++) {
				final SolutionList partialSample = new SolutionList();
				partialSample.setVariables(cnf.getVariables());
				final Path partialSampleFile = Files.createTempFile("sample", "");
				final int[] unwantedVariables;
				if (sample.size() > 0) {
					final Random random = new Random(repetition);
					unwantedVariables = IntStream.range(1, sample.get(0).size()).filter(v -> random.nextBoolean()).toArray();
				} else {
					unwantedVariables = new int[0];
				}
				for (final LiteralSet config : sample) {
					partialSample.addSolution(config.removeAll(unwantedVariables));
				}
				FileHandler.save(partialSampleFile, partialSample, new ConfigurationListFormat());

				final SampleTester testerModified =
					sample(modelFile, "yasa", Arrays.asList("-t", Integer.toString(t), "-m", "5", "-im", "-il", "-i", partialSampleFile.toString()));

				assertFalse("Invalid solutions for " + modelName, testerModified.hasInvalidSolutions());
				for (int i = 0; i < initialSize; i++) {
					final LiteralSet initialConfig = partialSample.getSolutions().get(i);
					final LiteralSet newConfig = testerModified.getSample().get(i).removeAll(unwantedVariables);
					assertEquals("Initial sample is changed for " + modelName, initialConfig, newConfig);
				}

				final SampleTester testerUnmodified =
					sample(modelFile, "yasa", Arrays.asList("-t", Integer.toString(t), "-m", "5", "-il", "-i", partialSampleFile.toString()));

				for (int i = 0; i < initialSize; i++) {
					final LiteralSet initialConfig = partialSample.getSolutions().get(i);
					final LiteralSet newConfig = testerUnmodified.getSample().get(i);
					assertEquals("Initial sample is changed for " + modelName, initialConfig, newConfig);
				}
			}
		} catch (final IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private static SampleTester sample(final Path modelFile, String algorithm, List<String> additionalArgs) {
		try {
			final Path outFile = runSampleAlgorithm(modelFile, algorithm, additionalArgs);

			final SolutionList sample = new SolutionList();
			final ProblemList problemList = FileHandler.load(outFile, sample, new ConfigurationListFormat());
			ProblemList errors = problemList.getErrors();
			if (!errors.isEmpty()) {
				fail(errors.get(0).toString());
			}
			final FileHandler<IFeatureModel> fileHandler = FeatureModelManager.getFileHandler(modelFile);
			errors = fileHandler.getLastProblems().getErrors();
			if (!errors.isEmpty()) {
				fail(errors.get(0).toString());
			}
			final CNF cnf = new FeatureModelFormula(fileHandler.getObject()).getCNF();

			final SampleTester tester = new SampleTester(cnf);
			tester.setSample(sample.getSolutions());
			return tester;
		} catch (final IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
			return null;
		}
	}

	private static Path runSampleAlgorithm(final Path modelFile, String algorithm, List<String> additionalArgs) throws IOException {
		final Path inFile = Files.createTempFile("input", ".xml");
		Files.write(inFile, Files.readAllBytes(modelFile));

		final Path outFile = Files.createTempFile("output", "");

		final ArrayList<String> args = new ArrayList<>();
		args.add("-a");
		args.add(algorithm);
		args.add("-o");
		args.add(outFile.toString());
		args.add("-fm");
		args.add(inFile.toString());
		args.addAll(additionalArgs);
		new ConfigurationGenerator().run(args);
		return outFile;
	}
}
