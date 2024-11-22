/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2024  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.fm.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import de.ovgu.featureide.Commons;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;

/**
 * Tests the whether timeouts work properly for the {@link FeatureModelAnalyzer}.
 *
 * @author Sebastrian Krieter
 */
public class FeatureModelAnalyzerTimeoutTest {

	@Test
	public final void timeoutOccursForCore() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getCoreFeatures(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getCoreFeatures(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getCoreFeatures(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForAnomalyConstraints() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getAnomalyConstraints(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getAnomalyConstraints(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getAnomalyConstraints(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForAntomicSets() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getAtomicSets(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getAtomicSets(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getAtomicSets(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForFalseOptionalFeatures() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getFalseOptionalFeatures(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getFalseOptionalFeatures(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getFalseOptionalFeatures(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForIndeterminedHiddenFeatures() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(201)).getIndeterminedHiddenFeatures(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(201)).getIndeterminedHiddenFeatures(null, 10000).isPresent());
	}

	@Test
	public final void timeoutOccursForContradictoryConstraints() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getContradictoryConstraints(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getContradictoryConstraints(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getContradictoryConstraints(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForTautologyConstraints() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getTautologyConstraints(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getTautologyConstraints(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getTautologyConstraints(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForRedundantConstraints() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getRedundantConstraints(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getRedundantConstraints(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getRedundantConstraints(null, 10).isPresent());
	}

	@Test
	public final void timeoutOccursForVoidConstraints() {
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1)).getVoidConstraints(null, 1).isPresent());
		assertTrue(FeatureModelManager.getAnalyzer(getFM(1)).getVoidConstraints(null, 10000).isPresent());
		assertFalse(FeatureModelManager.getAnalyzer(getFM(1000)).getVoidConstraints(null, 10).isPresent());
	}

	private static IFeatureModel getFM(final int i) {
		switch (i) {
		case 1:
			return Commons.loadBenchmarkFeatureModelFromFile("berkeley_db_model.xml");
		case 1000:
			return Commons.loadBenchmarkFeatureModelFromFile("1000-100.xml");
		case 201:
			return Commons.loadBenchmarkFeatureModelFromFile("200-100-hidden.xml");
		default:
			fail("NO FM");
			return null;
		}
	}

}
