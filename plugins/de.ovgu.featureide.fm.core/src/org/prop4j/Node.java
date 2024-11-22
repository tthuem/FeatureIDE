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
package org.prop4j;

import static de.ovgu.featureide.fm.core.localization.StringTable.IS_NOT_SUPPORTING_THIS_METHOD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

/**
 * A propositional node that can be transformed into conjunctive normal form (cnf).
 *
 * @author Thomas Thuem
 * @author Marcus Pinnecke (Feature Interface)
 */
public abstract class Node {

	protected Node[] children;

	@SuppressWarnings("unchecked")
	public void setChildren(Object... newChildren) {
		// allow collections as parameters
		if ((newChildren.length == 1) && (newChildren[0] instanceof Collection)) {
			newChildren = ((Collection<Object>) newChildren[0]).toArray();
		}
		// copy children and create literals
		children = new Node[newChildren.length];
		for (int i = 0; i < children.length; i++) {
			children[i] = getNode(newChildren[i]);
		}
	}

	public void setChildren(Object leftChild, Object rightChild) {
		children = new Node[] { getNode(leftChild), getNode(rightChild) };
	}

	public void setChildren(Node[] newChildren) {
		children = newChildren;
	}

	public Node[] getChildren() {
		return children;
	}

	/**
	 * <p> Returns true iff this node evaluates to true under the given truth value assignment. The result of the evaluation is the same as if each positive
	 * literal in the expression were replaced by the corresponding boolean value in the given map. </p>
	 *
	 * For example, for the {@link And conjunction} operation, this operations returns true iff the following formula is satisfied: <pre><i>c<sub>1</sub></i>
	 * &and; &hellip; &and; <i>c<sub>n</sub></i></pre> Where <i>c<sub>i</sub></i> is the <i>i</i>-th of the <i>n</i> children of the node.
	 *
	 * @param assignment truth value assignment from variable to true or false
	 * @return the result of evaluation of this node
	 */
	public abstract boolean getValue(Map<Object, Boolean> assignment);

	/**
	 * Returns true iff this is in conjunctive normal form. This is the case iff this is a conjunction of disjunctions of literals. Note that redundant nodes
	 * may be omitted. This means that instead of one-literal conjunctions and disjunctions, the literal alone may be stored.
	 *
	 * @return true iff this is in conjunctive normal form.
	 */
	public boolean isConjunctiveNormalForm() {
		return false;
	}

	public boolean isDisjunctiveNormalForm() {
		return false;
	}

	/**
	 * Returns true iff this is in clausal normal form. This is a more narrow case of conjunctive normal form. Specifically, redundant nodes may not be omitted.
	 * In other words, this must be a conjunction of clauses. Each clause must in turn contain nothing but a positive amount of literals.
	 *
	 * @return true iff this is in clausal normal form
	 */
	public boolean isRegularConjunctiveNormalForm() {
		return false;
	}

	public boolean isRegularDisjunctiveNormalForm() {
		return false;
	}

	public Node toCNF() {
		return toCNF(false, false);
	}

	public Node toDNF() {
		return toDNF(false);
	}

	public Node toCNF(boolean propagateUnitClauses, boolean removeSubsumed) {
		if (isConjunctiveNormalForm()) {
			return clone();
		} else {
			return transformToCNF(propagateUnitClauses, removeSubsumed, null);
		}
	}

	public Optional<Node> toCNF(boolean propagateUnitClauses, boolean removeSubsumed, long timeout) {
		if (isConjunctiveNormalForm()) {
			return Optional.of(clone());
		} else {
			final NullMonitor<Object> monitor = new NullMonitor<>();
			try {
				return Optional.ofNullable(Executors.newSingleThreadExecutor() //
						.submit(() -> transformToCNF(propagateUnitClauses, removeSubsumed, monitor)) //
						.get(timeout, TimeUnit.MILLISECONDS));
			} catch (final Exception e) {
				monitor.cancel();
			}
		}
		return Optional.empty();
	}

	private Node transformToCNF(boolean propagateUnitClauses, boolean removeSubsumed, final NullMonitor<?> monitor) {
		final Node prepareNF = prepareNF();
		final CNFDistributiveLawTransformer transformer = new CNFDistributiveLawTransformer();
		transformer.setPropagateUnitClauses(propagateUnitClauses);
		transformer.setRemoveSubsumed(removeSubsumed);
		transformer.setMonitor(monitor);
		return transformer.transform(prepareNF);
	}

	public Node toDNF(boolean simplify) {
		if (isDisjunctiveNormalForm()) {
			return clone();
		} else {
			final Node prepareNF = prepareNF();
			return ((prepareNF instanceof Or) ? prepareNF : new Or(prepareNF)).clausifyDNF(simplify);
		}
	}

	private Node prepareNF() {
		Node cnf = this.eliminateNonCNFOperators();
		cnf = cnf.deMorgan();
		cnf = cnf.simplifyTree();
		cnf.removeDuplicates();
		cnf = cnf.simplifyTree();
		return cnf;
	}

	public Node toRegularCNF() {
		return toRegularCNF(false, false);
	}

	public Node toRegularCNF(boolean propagateUnitClauses, boolean removeSubsumed) {
		Node regularCNFNode = toCNF(propagateUnitClauses, removeSubsumed);
		if (regularCNFNode instanceof And) {
			final Node[] children = regularCNFNode.getChildren();
			for (int i = 0; i < children.length; i++) {
				final Node child = children[i];
				if (child instanceof Literal) {
					children[i] = new Or(child);
				}
			}
		} else if (regularCNFNode instanceof Or) {
			regularCNFNode = new And(regularCNFNode);
		} else if (regularCNFNode instanceof Literal) {
			regularCNFNode = new And(new Or(regularCNFNode));
		}
		return regularCNFNode;
	}

	public Node toRegularDNF() {
		return toRegularDNF(false);
	}

	public Node toRegularDNF(boolean simplify) {
		Node regularDNFNode = toDNF(simplify);
		if (regularDNFNode instanceof Or) {
			final Node[] children = regularDNFNode.getChildren();
			for (int i = 0; i < children.length; i++) {
				final Node child = children[i];
				if (child instanceof Literal) {
					children[i] = new And(child);
				}
			}
		} else if (regularDNFNode instanceof And) {
			regularDNFNode = new Or(regularDNFNode);
		} else if (regularDNFNode instanceof Literal) {
			regularDNFNode = new Or(new And(regularDNFNode));
		}
		return regularDNFNode;
	}

	protected final Node eliminateNonCNFOperators() {
		if (children != null) {
			final Node[] newChildren = new Node[children.length];
			for (int i = 0; i < children.length; i++) {
				newChildren[i] = children[i].eliminateNonCNFOperators();
			}
			return eliminateNonCNFOperators(newChildren);
		} else {
			return eliminateNonCNFOperators(null);
		}
	}

	protected abstract Node eliminateNonCNFOperators(Node[] newChildren);

	public Node deMorgan() {
		for (int i = 0; i < children.length; i++) {
			children[i] = children[i].deMorgan();
		}
		return this;
	}

	@SuppressWarnings("unchecked")
	public Node eliminateNotSupportedSymbols(final String[] symbols) {
		Node node = this;
		for (int i = 0; i < symbols.length; i++) {
			if (symbols[i].equals(NodeWriter.noSymbol)) {
				switch (i) {
				case 0:
					node = node.eliminate(Not.class);
					break;
				case 1:
					node = node.eliminate(And.class);
					break;
				case 2:
					node = node.eliminate(Or.class);
					break;
				case 3:
					node = node.eliminate(Implies.class);
					break;
				case 4:
					node = node.eliminate(Equals.class);
					break;
				case 6:
					node = node.eliminate(Choose.class);
					break;
				case 7:
					node = node.eliminate(AtLeast.class);
					break;
				case 8:
					node = node.eliminate(AtMost.class);
					break;
				default:
					break;
				}
			}
		}
		return node;
	}

	public Node simplifyTree() {
		return flatten().simplify();
	}

	protected Node simplify() {
		for (int i = 0; i < children.length; i++) {
			children[i].simplify();
		}
		return this;
	}

	protected Node flatten() {
		if (children == null) {
			return this;
		} else if (children.length == 1) {
			return children[0].flatten();
		} else {
			for (int i = 0; i < children.length; i++) {
				children[i] = children[i].flatten();
			}
			return this;
		}
	}

	public void removeDuplicates() {
		if ((children != null) && (children.length > 1)) {
			final LinkedHashSet<Node> childrenSet = new LinkedHashSet<>();
			for (final Node child : children) {
				child.removeDuplicates();
				childrenSet.add(child);
			}
			if (childrenSet.size() < children.length) {
				children = childrenSet.toArray(new Node[0]);
			}
		}
	}

	@Override
	abstract public Node clone();

	@Override
	public int hashCode() {
		int hashCode = children.length * 37;
		for (int i = 0; i < children.length; i++) {
			hashCode += children[i].hashCode();
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!getClass().isInstance(object)) {
			return false;
		}
		final Node otherNode = (Node) object;
		if (children.length != otherNode.children.length) {
			return false;
		}
		final List<Node> thisChildrenList = Arrays.asList(children);
		final List<Node> otherChildrenList = Arrays.asList(otherNode.children);
		return thisChildrenList.containsAll(otherChildrenList) && otherChildrenList.containsAll(thisChildrenList);
	}

	@Override
	public String toString() {
		return toString(NodeWriter.shortSymbols);
	}

	/**
	 * Returns a string representation of this node. The symbols for logical connectors, e.g. And, are given as a parameter.
	 *
	 * @see org.prop4j.NodeWriter#shortSymbols
	 * @see org.prop4j.NodeWriter#logicalSymbols
	 * @see org.prop4j.NodeWriter#textualSymbols
	 *
	 * @param symbols the symbols for logical connectors
	 *
	 * @return a string representing this node
	 */
	public String toString(String[] symbols) {
		return toString(symbols, false);
	}

	public String toString(String[] symbols, boolean enquoteAlways) {
		final NodeWriter nw = new NodeWriter(this);
		nw.setSymbols(symbols);
		nw.setEnquoteWhitespace(true);
		nw.setEnquoteAlways(enquoteAlways);
		return nw.nodeToString();
	}

	public static Node[] clone(Node[] array) {
		final Node[] newArray = new Node[array.length];
		for (int i = 0; i < newArray.length; i++) {
			newArray[i] = array[i].clone();
		}
		return newArray;
	}

	public static List<Node> clone(List<Node> list) {
		final List<Node> newList = new ArrayList<>(list.size());
		for (final Node node : list) {
			newList.add(node.clone());
		}
		return newList;
	}

	@SuppressWarnings("unchecked")
	protected Node eliminate(Class<? extends Node>... array) {
		return eliminate(Arrays.asList(array));
	}

	protected Node eliminate(List<Class<? extends Node>> list) {
		for (int i = 0; i < children.length; i++) {
			children[i] = children[i].eliminate(list);
		}
		return this;
	}

	protected Node clausifyDNF(boolean simplify) {
		throw new RuntimeException(getClass().getName() + IS_NOT_SUPPORTING_THIS_METHOD);
	}

	public List<Node> replaceFeature(String feature, String replaceWithFeature) {
		return replaceFeature(feature, replaceWithFeature, new LinkedList<Node>());
	}

	protected List<Node> replaceFeature(String feature, String replaceWithFeature, List<Node> list) {
		for (final Node child : children) {
			child.replaceFeature(feature, replaceWithFeature, list);
		}
		return list;
	}

	/**
	 * Applies the given function to the name of each contained feature.
	 *
	 * @param f The function to apply to each feature name
	 */
	public void modifyFeatureNames(UnaryOperator<String> f) {
		for (final Node child : children) {
			child.modifyFeatureNames(f);
		}
	}

	protected static Node getNode(Object object) {
		return object instanceof Node ? (Node) object : new Literal(object);
	}

	protected void createNF(Node parent, LinkedList<LinkedHashSet<Literal>> newClauseList, boolean simplify) {
		final Node[] children = (parent instanceof Literal) ? new Node[] { parent } : parent.children;
		final ArrayList<List<Literal[]>> oldClauses = collectClauses(children);
		buildClauses(oldClauses, newClauseList, new LinkedHashSet<>(), 0, simplify);
	}

	private ArrayList<List<Literal[]>> collectClauses(Node[] children) {
		final ArrayList<List<Literal[]>> oldClauses = new ArrayList<>(children.length);
		for (final Node child : children) {
			final ArrayList<Literal[]> oldClause = new ArrayList<>();
			if (child instanceof Literal) {
				oldClause.add(new Literal[] { (Literal) child });
			} else {
				for (final Node grandchild : child.children) {
					if (grandchild instanceof Literal) {
						oldClause.add(new Literal[] { (Literal) grandchild });
					} else {
						oldClause.add(Arrays.copyOf(grandchild.children, grandchild.children.length, Literal[].class));
					}
				}
			}
			oldClauses.add(oldClause);
		}
		for (final List<Literal[]> clause : oldClauses) {
			Collections.sort(clause, (a, b) -> b.length - a.length);
		}
		Collections.sort(oldClauses, (a, b) -> a.size() - b.size());
		return oldClauses;
	}

	@SuppressWarnings("unchecked")
	private void buildClauses(ArrayList<List<Literal[]>> clauseList, LinkedList<LinkedHashSet<Literal>> newClauseList, LinkedHashSet<Literal> literals,
			int depth, boolean simplify) {
		for (final LinkedHashSet<Literal> hashSet : newClauseList) {
			if (literals.containsAll(hashSet)) {
				// is subsumed
				return;
			}
		}
		if (depth == clauseList.size()) {
			for (final Iterator<LinkedHashSet<Literal>> iterator = newClauseList.iterator(); iterator.hasNext();) {
				final LinkedHashSet<Literal> otherLiterals = iterator.next();
				if (otherLiterals.size() > literals.size()) {
					if (otherLiterals.containsAll(literals)) {
						// subsumes prior clause
						iterator.remove();
					}
				}
			}
			newClauseList.add((LinkedHashSet<Literal>) literals.clone());
		} else {
			final List<Literal[]> clause = clauseList.get(depth);
			final ArrayList<Literal> addedLiterals = new ArrayList<>();
			clauseLoop: for (int j = 0; j < clause.size(); j++) {
				for (final Literal l : clause.get(j)) {
					if (simplify && literals.contains(new Literal(l.var, !l.positive))) {
						literals.removeAll(addedLiterals);
						addedLiterals.clear();
						continue clauseLoop;
					} else {
						if (literals.add(l)) {
							addedLiterals.add(l);
						}
					}
				}
				buildClauses(clauseList, newClauseList, literals, depth + 1, simplify);
				literals.removeAll(addedLiterals);
				addedLiterals.clear();
			}
		}
	}

	protected boolean unitPropagation(LinkedList<LinkedHashSet<Literal>> newClauseList) {
		boolean newUnitClauses = false;
		final HashSet<Literal> unitClauses = new HashSet<>();
		for (final LinkedHashSet<Literal> clause : newClauseList) {
			if (clause.isEmpty()) {
				return true;
			} else if (clause.size() == 1) {
				final Literal literal = clause.iterator().next();
				unitClauses.add(new Literal(literal.var, !literal.positive));
				newUnitClauses = true;
			}
		}
		while (newUnitClauses) {
			newUnitClauses = false;
			for (final LinkedHashSet<Literal> clause : newClauseList) {
				if (clause.removeAll(unitClauses)) {
					if (clause.isEmpty()) {
						return true;
					} else if (clause.size() == 1) {
						final Literal literal = clause.iterator().next();
						unitClauses.add(new Literal(literal.var, !literal.positive));
						newUnitClauses = true;
					}
				}
			}
		}
		return false;
	}

	protected Node[] chooseKofN(Node[] elements, int k, boolean negated) {
		final int n = elements.length;

		// return tautology
		if ((k == 0) || (k == (n + 1))) {
			return new Node[] { new Or(new Not(elements[0].clone()), elements[0].clone()) };
		}

		// return contradiction
		if ((k < 0) || (k > (n + 1))) {
			return new Node[] { new And(new Not(elements[0].clone()), elements[0].clone()) };
		}

		final Node[] newNodes = new Node[binom(n, k)];
		int j = 0;

		// negate all elements
		if (negated) {
			negateNodes(elements);
		}

		final Node[] clause = new Node[k];
		final int[] index = new int[k];

		// the position that is currently filled in clause
		int level = 0;
		index[level] = -1;

		while (level >= 0) {
			// fill this level with the next element
			index[level]++;
			// did we reach the maximum for this level
			if (index[level] >= (n - (k - 1 - level))) {
				// go to previous level
				level--;
			} else {
				clause[level] = elements[index[level]];
				if (level == (k - 1)) {
					newNodes[j++] = new Or(clone(clause));
				} else {
					// go to next level
					level++;
					// allow only ascending orders (to prevent from duplicates)
					index[level] = index[level - 1];
				}
			}
		}
		if (j != newNodes.length) {
			throw new RuntimeException("Pre-calculation of the number of elements failed!");
		}
		return newNodes;
	}

	public static int binom(int n, int k) {
		if (k > (n / 2)) {
			k = n - k;
		}
		if ((k < 0) || (n < 0)) {
			return 0;
		}
		if ((k == 0) || (n == 0)) {
			return 1;
		}
		return (binom(n - 1, k - 1) * n) / k;
	}

	protected static void negateNodes(Node[] nodes) {
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new Not(nodes[i]);
		}
	}

	/**
	 * Returns all features contained in this node and its children. Duplicates are kept.
	 *
	 * @return all features contained in this node and its children; not null
	 */
	public List<String> getContainedFeatures() {
		return getContainedFeatures(new ArrayList<String>());
	}

	/**
	 * Returns all features contained in this node and its children. Duplicates are removed.
	 *
	 * @return all features contained in this node and its children; not null
	 */
	public Set<String> getUniqueContainedFeatures() {
		return getContainedFeatures(new LinkedHashSet<String>());
	}

	/**
	 * Returns all features contained in this node and its children. Uses the given collection as out variable.
	 *
	 * @param containedFeatures collection of previously found features to add to; not null
	 * @return all features contained in this node and its children; not null
	 */
	protected <T extends Collection<String>> T getContainedFeatures(T containedFeatures) {
		for (final Node child : children) {
			child.getContainedFeatures(containedFeatures);
		}
		return containedFeatures;
	}

	public int getMaxDepth() {
		int count = 1;
		for (final Node child : children) {
			final int childCount = child.getMaxDepth() + 1;
			if (childCount > count) {
				count = childCount;
			}
		}
		return count;
	}

	/**
	 * Returns all literals contained in this node and its children. Duplicates are kept.
	 *
	 * @return all literals contained in this node and its children; not null
	 */
	public List<Literal> getLiterals() {
		return getLiterals(new LinkedList<Literal>());
	}

	/**
	 * Returns all literals contained in this node and its children. Duplicates are removed.
	 *
	 * @return all literals contained in this node and its children; not null
	 */
	public Set<Literal> getUniqueLiterals() {
		return getLiterals(new LinkedHashSet<Literal>());
	}

	/**
	 * Returns all literals contained in this node and its children. Duplicates are kept. Uses the given collection as out variable.
	 *
	 * @param literals collection of previously found literals to add to; not null
	 * @return all literals contained in this node and its children; not null
	 */
	protected <T extends Collection<Literal>> T getLiterals(T literals) {
		for (final Node child : children) {
			child.getLiterals(literals);
		}
		return literals;
	}

	/**
	 * Returns all variables contained in this node and its children. Duplicates are kept.
	 *
	 * @return all variables contained in this node and its children; not null
	 */
	public List<Object> getVariables() {
		return getVariables(new LinkedList<Object>());
	}

	/**
	 * Returns all variables contained in this node and its children. Duplicates are removed.
	 *
	 * @return all variables contained in this node and its children; not null
	 */
	public Set<Object> getUniqueVariables() {
		return getVariables(new LinkedHashSet<Object>());
	}

	/**
	 * Returns all variables contained in this node and its children. Uses the given collection as out variable.
	 *
	 * @param variables collection of previously found variables to add to; not null
	 * @return all variables contained in this node and its children; not null
	 */
	protected <T extends Collection<Object>> T getVariables(T variables) {
		for (final Node child : children) {
			child.getVariables(variables);
		}
		return variables;
	}

	/**
	 * <p> Returns all possible truth value assignments for {@link #getVariables() all variables} in this node. </p>
	 *
	 * <p> Let <i>n</i> denote the amount of literals in this node. Then this method will return exactly <i>2<sup>n</sup></i> assignments. Each assignment in
	 * turn contains exactly <i>n</i> entries (1 for each variable). </p>
	 *
	 * @return all possible truth value assignments; not null
	 */
	public Set<Map<Object, Boolean>> getAssignments() {
		return getAssignments(null);
	}

	/**
	 * Returns all truth value assignments for which this node {@link #getValue(Map) evaluates} to true.
	 *
	 * @return all satisfying truth value assignments; not null
	 */
	public Set<Map<Object, Boolean>> getSatisfyingAssignments() {
		return getAssignments(true);
	}

	/**
	 * Returns all truth value assignments for which this node {@link #getValue(Map) evaluates} to false.
	 *
	 * @return all contradicting truth value assignments; not null
	 */
	public Set<Map<Object, Boolean>> getContradictingAssignments() {
		return getAssignments(false);
	}

	/**
	 * Returns all truth value assignments for which this node {@link #getValue(Map) evaluates} to the given boolean value. Accepts all assignments if the given
	 * boolean value is null.
	 *
	 * @param result the expected evaluation result; null to accept every assignment
	 * @return all accepted truth value assignments; not null
	 */
	private Set<Map<Object, Boolean>> getAssignments(Boolean result) {
		final Set<Object> keys = getUniqueVariables();
		final Set<Map<Object, Boolean>> assignments = new LinkedHashSet<>();
		for (int assignment = 0; assignment < (1 << keys.size()); assignment++) {
			final Map<Object, Boolean> map = new LinkedHashMap<>();
			int i = 0;
			for (final Object key : keys) {
				map.put(key, (assignment & (1 << i)) != 0);
				i++;
			}
			if ((result == null) || (getValue(map) == result)) {
				assignments.add(map);
			}
		}
		return assignments;
	}

	/**
	 * Replaces all instances of the passed feature names with False. Depending on the value of resolveq, this method will either try to resolve the entire
	 * formula down to a True/False or leave it as is.
	 *
	 * @param node
	 * @param features
	 * @param resolve
	 * @return
	 */
	public static Node replaceLiterals(Node node, List<String> features, boolean resolve) {
		if (node instanceof And) {
			final List<Node> children = new ArrayList<>();
			for (final Node child : node.getChildren()) {
				final Node newchild = replaceLiterals(child, features, resolve);

				// check if children already contains negation of child, which makes it a contradiction
				if (resolve) {
					if (children.contains(new Not(newchild))) {
						return new False();
					}
					if (newchild instanceof Not) {
						if (children.contains(((Not) newchild).getChildren()[0])) {
							return new False();
						}
					}
				}

				if (newchild instanceof False) {
					return newchild;
				} else if (newchild instanceof True) {
					continue;
				} else {
					children.add(newchild);
				}
			}

			if (children.size() > 1) {
				return new And(children);
			} else if (children.size() == 1) {
				return children.get(0);
			} else if (children.size() == 0) {
				return new True();
			}

		} else if (node instanceof Or) {
			final List<Node> children = new ArrayList<Node>();
			for (final Node child : node.getChildren()) {
				final Node newchild = replaceLiterals(child, features, resolve);

				// check if children already contains negation of child, which makes it a tautology
				if (resolve) {
					if (children.contains(new Not(newchild))) {
						return new True();
					}
					if (newchild instanceof Not) {
						if (children.contains(((Not) newchild).getChildren()[0])) {
							return new True();
						}
					}
				}

				if (newchild instanceof False) {
					continue;
				} else if (newchild instanceof True) {
					return newchild;
				} else {
					children.add(newchild);
				}
			}
			if (children.size() > 1) {
				return new Or(children);
			} else if (children.size() == 1) {
				return children.get(0);
			} else if (children.size() == 0) {
				return new False();
			}

		} else if (node instanceof Literal) {
			if (features.contains(((Literal) node).getContainedFeatures().get(0))) {
				if (((Literal) node).positive) {
					return new False();
				} else {
					return new True();
				}

			} else {
				return node;
			}
		} else if (node instanceof Not) {
			final Node child = replaceLiterals(node.getChildren()[0], features, resolve);
			if (child instanceof False) {
				return new True();
			} else if (child instanceof True) {
				return new False();
			} else {
				return new Not(child);
			}
		} else if (node instanceof Equals) {
			final Node leftChild = replaceLiterals(node.getChildren()[0], features, resolve);
			final Node rightChild = replaceLiterals(node.getChildren()[1], features, resolve);
			if (leftChild instanceof True) {
				return rightChild;
			} else if (rightChild instanceof True) {
				return leftChild;
			} else if (leftChild instanceof False) {
				return new Not(rightChild);
			} else if (rightChild instanceof False) {
				return new Not(leftChild);
			} else {
				return node;
			}
		}
		return null;
	}
}
