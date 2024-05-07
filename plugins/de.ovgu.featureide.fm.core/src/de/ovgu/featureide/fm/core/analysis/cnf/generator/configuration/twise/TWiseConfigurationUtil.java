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
package de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.sat4j.core.VecInt;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.ClauseList;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet.Order;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.ITWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.UniformRandomConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISatSolver;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.ISimpleSatSolver.SatResult;
import de.ovgu.featureide.fm.core.analysis.cnf.solver.RuntimeTimeoutException;
import de.ovgu.featureide.fm.core.analysis.mig.CollectingStrongVisitor;
import de.ovgu.featureide.fm.core.analysis.mig.MIGBuilder;
import de.ovgu.featureide.fm.core.analysis.mig.ModalImplicationGraph;
import de.ovgu.featureide.fm.core.analysis.mig.Traverser;
import de.ovgu.featureide.fm.core.analysis.mig.Vertex;
import de.ovgu.featureide.fm.core.analysis.mig.Visitor;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;

/**
 * Contains several intermediate results and functions for generating a t-wise sample.
 *
 * @author Sebastian Krieter
 */
public class TWiseConfigurationUtil {

	public static final int GLOBAL_SOLUTION_LIMIT = 100_000;

	private final CNF cnf;
	private final ISatSolver solver;
	private ModalImplicationGraph mig;
	private int[] core;
	private LiteralSet[] strongHull;
	private boolean unsatisfiable;

	private Random random = new Random(42);

	private final LiteralSet[] solverSolutions = new LiteralSet[GLOBAL_SOLUTION_LIMIT];
	private final HashSet<LiteralSet> solutionSet = new HashSet<>();

	private int solverSolutionEndIndex = -1;

	private List<TWiseConfiguration> solutionList = Collections.emptyList();

	public TWiseConfigurationUtil(ISatSolver solver) {
		this.solver = solver;
		cnf = solver.getSatInstance();
		if (!cnf.getClauses().isEmpty()) {
			computeMIG();
		}
	}

	public void addSolverSolution(int[] literals) {
		final LiteralSet solution = new LiteralSet(literals, Order.INDEX, false);
		if (solutionSet.add(solution)) {
			solverSolutionEndIndex = (solverSolutionEndIndex + 1) % GLOBAL_SOLUTION_LIMIT;
			final LiteralSet oldSolution = solverSolutions[solverSolutionEndIndex];
			if (oldSolution != null) {
				solutionSet.remove(oldSolution);
			}
			solverSolutions[solverSolutionEndIndex] = solution;

			for (final TWiseConfiguration configuration : solutionList) {
				configuration.updateSolverSolutions(literals, solverSolutionEndIndex);
			}
		}
	}

	public LiteralSet getSolverSolution(int index) {
		return solverSolutions[index];
	}

	public LiteralSet[] getSolverSolutions() {
		return solverSolutions;
	}

	public void computeRandomSample() {
		final UniformRandomConfigurationGenerator randomGenerator = new UniformRandomConfigurationGenerator(cnf, 10000);
		randomGenerator.setAllowDuplicates(false);
		randomGenerator.setSampleSize(1000);
		randomGenerator.setRandom(random);
		LongRunningWrapper.runMethod(randomGenerator).forEach(s -> addSolverSolution(s.getLiterals()));
	}

	public void computeMIG() {
		if (ITWiseConfigurationGenerator.VERBOSE) {
			System.out.print("Init graph... ");
		}
		mig = LongRunningWrapper.runMethod(new MIGBuilder(solver.getSatInstance(), false));
		if (mig == null) {
			unsatisfiable = true;
			return;
		}
		core = mig.getAdjList().stream().filter(Vertex::isCore).mapToInt(Vertex::getVar).toArray();
		solver.assignmentPushAll(core);

		strongHull = new LiteralSet[mig.getAdjList().size()];
		for (final Vertex vertex : mig.getAdjList()) {
			final int literalSet = vertex.getVar();
			final Traverser traverser = new Traverser(mig);
			traverser.setModel(new int[mig.getAdjList().size()]);
			final Visitor<VecInt[]> visitor = new CollectingStrongVisitor();
			traverser.setVisitor(visitor);
			traverser.traverse(literalSet);
			final VecInt strong = visitor.getResult()[0];
			strongHull[vertex.getId()] = new LiteralSet(Arrays.copyOf(strong.toArray(), strong.size()));
		}
		if (ITWiseConfigurationGenerator.VERBOSE) {
			System.out.println("Done!");
		}
	}

	public int[] getDeadCoreFeatures() {
		return core;
	}

	public boolean hasNoConstraints() {
		return cnf.getClauses().isEmpty();
	}

	public CNF getCnf() {
		return cnf;
	}

	public ISatSolver getSolver() {
		return solver;
	}

	int[] sat(final int curLiteral) {
		solver.assignmentPush(curLiteral);
		final SatResult hasSolution = solver.hasSolution();
		switch (hasSolution) {
		case FALSE:
			solver.assignmentReplaceLast(-curLiteral);
			return null;
		case TIMEOUT:
			solver.assignmentPop();
			throw new RuntimeTimeoutException();
		case TRUE:
			solver.assignmentPop();
			final int[] solution = solver.getSolution();
			addSolverSolution(solution);
			solver.shuffleOrder(random);
			return solution;
		default:
			throw new IllegalStateException();
		}
	}

	public ModalImplicationGraph getMig() {
		return mig;
	}

	public boolean isCombinationValid(LiteralSet literals) {
		if (hasNoConstraints()) {
			return true;
		}
		if (isCombinationInvalidMIG(literals)) {
			return false;
		} else if (isCombinationValidHistory(literals)) {
			return true;
		} else {
			return isCombinationValidSAT(literals);
		}
	}

	public boolean isCombinationValid(ClauseList clauses) {
		if (hasNoConstraints()) {
			return !clauses.isEmpty();
		}
		for (final LiteralSet literalSet : clauses) {
			if (isCombinationInvalidMIG(literalSet)) {
				return false;
			}
		}
		for (final LiteralSet literalSet : clauses) {
			if (isCombinationValidHistory(literalSet)) {
				return true;
			}
		}
		for (final LiteralSet literalSet : clauses) {
			if (isCombinationValidSAT(literalSet)) {
				return true;
			}
		}
		return false;
	}

	public boolean isCombinationInvalidMIG(LiteralSet literals) {
		if (hasNoConstraints()) {
			return false;
		}
		for (final int literal : literals.getLiterals()) {
			if (strongHull[mig.getVertex(literal).getId()].hasConflicts(literals)) {
				return true;
			}
		}
		return false;
	}

	public boolean isCombinationValidHistory(LiteralSet literals) {
		for (final LiteralSet s : solverSolutions) {
			if (s == null) {
				break;
			}
			if (!s.hasConflicts(literals)) {
				return true;
			}
		}
		return false;
	}

	public boolean isCombinationValidSAT(LiteralSet literals) {
		if (hasNoConstraints()) {
			return true;
		}
		final int orgAssingmentLength = solver.getAssignmentSize();
		solver.assignmentPushAll(literals.getLiterals());
		try {
			final SatResult hasSolution = solver.hasSolution();
			switch (hasSolution) {
			case TRUE:
				addSolverSolution(solver.getSolution());
				return true;
			case FALSE:
			case TIMEOUT:
			default:
				return false;
			}
		} finally {
			solver.assignmentClear(orgAssingmentLength);
		}
	}

	public boolean isSelectionPossibleHistory(final LiteralSet literals, final TWiseConfiguration configuration) {
		if (hasNoConstraints()) {
			return true;
		}
		final VecInt solverSolutionIndex = configuration.getSolverSolutionIndex();
		for (int i = 0; i < solverSolutionIndex.size(); i++) {
			if (!getSolverSolution(solverSolutionIndex.get(i)).hasConflicts(literals)) {
				return true;
			}
		}
		return false;
	}

	public boolean isSelectionPossibleSAT(final LiteralSet literals, final TWiseConfiguration configuration) {
		if (hasNoConstraints()) {
			return true;
		}
		final int orgAssignmentSize = configuration.setUpSolver(solver);
		try {
			final int[] configurationLiterals = configuration.getLiterals();
			for (final int literal : literals.getLiterals()) {
				if (configurationLiterals[Math.abs(literal) - 1] == 0) {
					solver.assignmentPush(literal);
				}
			}
			if (orgAssignmentSize < solver.getAssignmentSize()) {
				if (solver.hasSolution() != SatResult.TRUE) {
					return false;
				}
			}
		} finally {
			solver.assignmentClear(orgAssignmentSize);
		}
		return true;
	}

	public List<TWiseConfiguration> getSolutionList() {
		return solutionList;
	}

	public void setSolutionList(List<TWiseConfiguration> solutionList) {
		this.solutionList = solutionList;
	}

	public void setRandom(Random random) {
		this.random = random;
	}

	public boolean isUnsatisfiable() {
		return unsatisfiable;
	}

}
