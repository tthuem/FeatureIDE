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
import java.util.List;

import org.sat4j.minisat.core.Solver;
import org.sat4j.specs.IConstr;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;

/**
 * {@link AdvancedSatSolver} version that supports removing clauses.
 *
 * @author Sebastian Krieter
 */
public class ModifiableSatSolver extends AdvancedSatSolver {

	public ModifiableSatSolver(AdvancedSatSolver oldSolver) {
		super(oldSolver);
	}

	public ModifiableSatSolver(CNF satInstance) {
		super(satInstance);
	}

	@Override
	protected List<IConstr> addClauses(Solver<?> solver, Iterable<? extends LiteralSet> clauses, boolean internal) throws RuntimeContradictionException {
		final ArrayList<IConstr> newConstrs = new ArrayList<>();

		try {
			for (final LiteralSet clause : clauses) {
				newConstrs.add(addClause(solver, internal ? clause.getLiterals() : internalMapping.convertToInternal(clause.getLiterals())));
			}
		} catch (final RuntimeContradictionException e) {
			removeLastClauses(newConstrs.size());
			throw e;
		}

		return newConstrs;
	}

	@Override
	protected IConstr addClause(Solver<?> solver, int[] mainClause) throws RuntimeContradictionException {
		final IConstr constr = super.addClause(solver, mainClause);
		constrList.add(constr);
		return constr;
	}

	@Override
	public void removeClause(IConstr constr) {
		if (contradiction) {
			return;
		}
		if (constr != null) {
			try {
				solver.removeConstr(constr);
			} catch (final Exception e) {
				throw new RuntimeContradictionException(e);
			}
		}
	}

	@Override
	public void removeLastClauses(int numberOfClauses) {
		if (contradiction) {
			return;
		}
		try {
			for (int i = 0; i < numberOfClauses; i++) {
				final IConstr removeLast = constrList.remove(constrList.size() - 1);
				if (removeLast != null) {
					solver.removeSubsumedConstr(removeLast);
				}
			}
			solver.clearLearntClauses();
		} catch (final Exception e) {
			throw new RuntimeContradictionException(e);
		}
	}

	@Override
	public ModifiableSatSolver clone() {
		if (this.getClass() == ModifiableSatSolver.class) {
			return new ModifiableSatSolver(this);
		} else {
			throw new RuntimeException("Cloning not supported for " + this.getClass().toString());
		}
	}

}
