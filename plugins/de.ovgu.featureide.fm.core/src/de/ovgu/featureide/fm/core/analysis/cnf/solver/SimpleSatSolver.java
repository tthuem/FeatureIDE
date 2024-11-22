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
package de.ovgu.featureide.fm.core.analysis.cnf.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.minisat.core.Solver;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.IInternalVariables;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;

/**
 * Light version of a sat solver with reduced functionality.
 *
 * @author Sebastian Krieter
 */
public class SimpleSatSolver implements ISimpleSatSolver {

	// XXX: Must be initialized here (is used in ModifiableSatSolver)
	protected final ArrayList<IConstr> constrList = new ArrayList<>();

	protected final CNF satInstance;
	protected final IInternalVariables internalMapping;
	protected final Solver<?> solver;

	protected final boolean contradiction;

	public SimpleSatSolver(CNF satInstance) {
		this(satInstance, satInstance.getInternalVariables());
	}

	protected SimpleSatSolver(SimpleSatSolver oldSolver) {
		this(oldSolver.satInstance, oldSolver.internalMapping);
	}

	protected SimpleSatSolver(CNF satInstance, IInternalVariables variables) throws RuntimeContradictionException {
		this.satInstance = satInstance;
		internalMapping = variables;

		Solver<?> newSolver = null;
		boolean contradictionException = false;
		try {
			newSolver = newSolver();
		} catch (final RuntimeContradictionException e) {
			contradictionException = true;
		}
		solver = newSolver;
		contradiction = contradictionException;
	}

	@Override
	public IConstr addClause(LiteralSet mainClause) throws RuntimeContradictionException {
		return addClause(solver, internalMapping.convertToInternal(mainClause.getLiterals()));
	}

	@Override
	public IConstr addInternalClause(LiteralSet mainClause) throws RuntimeContradictionException {
		return addClause(solver, mainClause.getLiterals());
	}

	protected IConstr addClause(Solver<?> solver, int[] literals) throws RuntimeContradictionException {
		if ((literals.length == 1) && (literals[0] == 0)) {
			throw new RuntimeContradictionException();
		}
		try {
			assert checkClauseValidity(literals);
			return solver.addClause(new VecInt(Arrays.copyOfRange(literals, 0, literals.length)));
		} catch (final ContradictionException e) {
			throw new RuntimeContradictionException(e);
		}
	}

	@Override
	public List<IConstr> addClauses(Iterable<? extends LiteralSet> clauses) throws RuntimeContradictionException {
		return addClauses(solver, clauses, false);
	}

	@Override
	public List<IConstr> addInternalClauses(Iterable<? extends LiteralSet> clauses) throws RuntimeContradictionException {
		return addClauses(solver, clauses, true);
	}

	protected List<IConstr> addClauses(Solver<?> solver, Iterable<? extends LiteralSet> clauses, boolean internal) throws RuntimeContradictionException {
		final ArrayList<IConstr> constrList = new ArrayList<>();
		for (final LiteralSet clause : clauses) {
			constrList.add(addClause(solver, internal ? clause.getLiterals() : internalMapping.convertToInternal(clause.getLiterals())));
		}
		return constrList;
	}

	@Override
	public SimpleSatSolver clone() {
		if (this.getClass() == SimpleSatSolver.class) {
			return new SimpleSatSolver(this);
		} else {
			throw new RuntimeException("Cloning not supported for " + this.getClass().toString());
		}
	}

	@Override
	public CNF getSatInstance() {
		return satInstance;
	}

	@Override
	public int[] getSolution() {
		return contradiction ? null : internalMapping.convertToOriginal(solver.model());
	}

	@Override
	public int[] getInternalSolution() {
		return contradiction ? null : solver.model();
	}

	@Override
	public SatResult hasSolution() {
		if (contradiction) {
			return SatResult.FALSE;
		}
		try {
			if (solver.isSatisfiable(false)) {
				return SatResult.TRUE;
			} else {
				return SatResult.FALSE;
			}
		} catch (final TimeoutException e) {
			e.printStackTrace();
			return SatResult.TIMEOUT;
		}
	}

	@Override
	public SatResult hasSolution(int... assignment) {
		if (contradiction) {
			return SatResult.FALSE;
		}
		final int[] unitClauses = new int[assignment.length];
		System.arraycopy(internalMapping.convertToInternal(assignment), 0, unitClauses, 0, unitClauses.length);

		try {
			if (solver.isSatisfiable(new VecInt(unitClauses), false)) {
				return SatResult.TRUE;
			} else {
				return SatResult.FALSE;
			}
		} catch (final TimeoutException e) {
			e.printStackTrace();
			return SatResult.TIMEOUT;
		}
	}

	@Override
	public SatResult hasSolution(LiteralSet assignment) {
		return hasSolution(assignment.getLiterals());
	}

	@Override
	public int[] getPrimeImplicant() {
		if (contradiction) {
			return null;
		}
		try {
			if (solver.isSatisfiable()) {
				final int[] internalPrimeImplicant = solver.primeImplicant();
				return internalPrimeImplicant == null ? null : internalMapping.convertToOriginal(internalPrimeImplicant);
			} else {
				return null;
			}
		} catch (final Exception e) {
			return null;
		}
	}

	@Override
	public void removeClause(IConstr constr) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeLastClause() {
		removeLastClauses(1);
	}

	@Override
	public void removeLastClauses(int numberOfClauses) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void reset() {
		if (!contradiction) {
			solver.reset();
		}
	}

	private boolean checkClauseValidity(final int[] literals) {
		for (int i = 0; i < literals.length; i++) {
			final int l = literals[i];
			if ((l == 0) || (Math.abs(l) > satInstance.getVariables().maxVariableID())) {
				return false;
			}
		}
		return true;
	}

	protected final Solver<?> newSolver() throws RuntimeContradictionException {
		final Solver<?> solver = createSolver();
		configureSolver(solver);
		initSolver(solver);
		return solver;
	}

	/**
	 * Creates the Sat4J solver instance.
	 */
	protected Solver<?> createSolver() {
		return (Solver<?>) SolverFactory.newDefault();
	}

	/**
	 * Set several options for the Sat4J solver instance.
	 */
	protected void configureSolver(Solver<?> solver) {
		solver.setTimeoutMs(10_000);
		solver.setDBSimplificationAllowed(false);
		solver.setVerbose(false);
	}

	/**
	 * Add clauses to the solver. Initializes the order instance.
	 */
	protected void initSolver(Solver<?> solver) throws RuntimeContradictionException {
		final int size = satInstance.getVariables().size();
		final List<LiteralSet> clauses = satInstance.getClauses();
		if (!clauses.isEmpty()) {
			solver.setExpectedNumberOfClauses(clauses.size() + 1);
			addClauses(solver, clauses, false);
		}
		if (size > 0) {
			final VecInt pseudoClause = new VecInt(size + 1);
			for (int i = 1; i <= size; i++) {
				pseudoClause.push(i);
			}
			pseudoClause.push(-1);
			try {
				solver.addClause(pseudoClause);
			} catch (final ContradictionException e) {
				throw new RuntimeContradictionException(e);
			}
		}
		solver.getOrder().init();
	}

	@Override
	public void setTimeout(int timeout) {
		if (!contradiction) {
			solver.setTimeoutMs(timeout);
		}
	}

	@Override
	public IInternalVariables getInternalMapping() {
		return internalMapping;
	}

}
