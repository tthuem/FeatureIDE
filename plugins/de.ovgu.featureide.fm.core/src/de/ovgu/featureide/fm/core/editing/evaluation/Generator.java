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
package de.ovgu.featureide.fm.core.editing.evaluation;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.prop4j.And;
import org.prop4j.AtMost;
import org.prop4j.Equals;
import org.prop4j.Implies;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Not;
import org.prop4j.Or;

import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.impl.Constraint;
import de.ovgu.featureide.fm.core.base.impl.DefaultFeatureModelFactory;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.functional.Functional;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;

/**
 * A generator for feature models.
 *
 * @author Thomas Thüm
 * @author Marcus Pinnecke (Feature Interface)
 */
public abstract class Generator {

	public static final int TIMEOUT = 60;

	public final static int maxChildren = 10;

	public final static int minLiterals = 2;
	public final static int maxLiterals = 5;

	public static IFeatureModel generateFeatureModel(long id, int numberOfFeatures) {
		final Random random = new Random(id);
		final IFeatureModel fm = generateFeatureDiagram(random, numberOfFeatures);
		generateConstraints(fm, random, numberOfFeatures / 10);
		return fm;
	}

	public static IFeatureModel generateFeatureDiagram(Random random, int numberOfFeatures) {
		final IFeatureModelFactory factory = DefaultFeatureModelFactory.getInstance();
		final IFeatureModel fm = factory.create();
		// Create root feature
		final IFeature rootFeature = factory.createFeature(fm, "C1");
		fm.addFeature(rootFeature);
		fm.getStructure().setRoot(rootFeature.getStructure());
		final List<IFeature> leaves = new LinkedList<>();
		leaves.add(rootFeature);
		int count = 1;
		while (count < numberOfFeatures) {
			final int parentIndex = random.nextInt(leaves.size());
			final IFeature parent = leaves.remove(parentIndex);
			fm.getRenamingsManager().renameFeature(parent.getName(), "A" + parent.getName().substring(1));
			int childrenCount = random.nextInt(maxChildren) + 1;
			childrenCount = Math.min(childrenCount, numberOfFeatures - count);
			for (int i = 1; i <= childrenCount; i++) {
				final IFeature child = factory.createFeature(fm, "C" + (count + i));
				fm.addFeature(child);
				parent.getStructure().addChild(child.getStructure());
				leaves.add(child);
			}
			if (random.nextBoolean()) {
				parent.getStructure().changeToAnd();
				for (final IFeatureStructure child : parent.getStructure().getChildren()) {
					child.setMandatory(random.nextBoolean());
				}
			} else if (random.nextBoolean()) {
				parent.getStructure().changeToOr();
			}
			count += childrenCount;
		}
		return fm;
	}

	public static void generateConstraints(IFeatureModel fm, Random random, int numberOfConstraints) {
		final boolean valid = FeatureModelManager.getAnalyzer(fm).isValid(null);
		if (!valid) {
			Logger.logInfo("Feature model not valid!");
		}
		final Object[] names = FeatureUtils.getFeatureNamesList(fm).toArray(new Object[FeatureUtils.getFeatureNamesList(fm).size()]);
		int k = 0;
		for (int i = 0; i < numberOfConstraints;) {
			Node node = getRandomLiteral(names, random);
			for (int j = random.nextInt((maxLiterals - minLiterals) + 1) + minLiterals; j > 1; j--) {
				if (random.nextBoolean()) {
					if (random.nextBoolean()) {
						node = new And(node, getRandomLiteral(names, random));
					} else {
						node = new Or(node, getRandomLiteral(names, random));
					}
				} else {
					if (random.nextBoolean()) {
						node = new Implies(node, getRandomLiteral(names, random));
					} else {
						node = new Equals(node, getRandomLiteral(names, random));
					}
				}
				if (random.nextBoolean() && random.nextBoolean()) {
					node = new Not(node);
				}
			}
			final IConstraint constraint = DefaultFeatureModelFactory.getInstance().createConstraint(fm, node);
			fm.addConstraint(constraint);
			if (!valid || FeatureModelManager.getAnalyzer(fm).isValid(null)) {
				i++;
				System.out.println("E\t" + i + "\t" + node);
			} else {
				fm.removeConstraint(constraint);
				Logger.logInfo("F\t" + ++k + "\t" + node);
			}
		}
	}

	public static IFeatureModel refactoring(IFeatureModel originalFM, long id, int numberOfEdits) {
		final IFeatureModel fm = originalFM.clone();
		final Random random = new Random(id);

		for (int i = 0; i < numberOfEdits; i++) {
			final List<IFeature> list = new LinkedList<>(Functional.toList(fm.getFeatures()));
			final List<IFeature> randomizedList = new LinkedList<>();
			while (!list.isEmpty()) {
				randomizedList.add(list.remove(random.nextInt(list.size())));
			}
			final int r = random.nextInt(3);
			if (r == 0) {
				// Alternative to Or+Constraint
				for (final IFeature feature : randomizedList) {
					if ((feature.getStructure().getChildrenCount() > 1) && feature.getStructure().isAlternative()) {
						feature.getStructure().changeToOr();
						final LinkedList<Node> nodes = new LinkedList<>();
						for (final IFeatureStructure child : feature.getStructure().getChildren()) {
							nodes.add(new Literal(child.getFeature().getName()));
						}
						fm.addConstraint(new Constraint(fm, new AtMost(1, nodes).toCNF()));
						break;
					}
				}
			} else if (r == 1) {
				// Mandatory to Optional+Constraint
				for (final IFeature feature : randomizedList) {
					final IFeatureStructure parent = feature.getStructure().getParent();
					if ((parent != null) && parent.isAnd() && !parent.isFirstChild(feature.getStructure()) && feature.getStructure().isMandatory()) {
						feature.getStructure().setMandatory(false);
						fm.addConstraint(new Constraint(fm, new Implies(new Literal(parent.getFeature().getName()), new Literal(feature.getName()))));
						break;
					}
				}
			} else {
				// move feature to parent's parent
				for (final IFeature child : randomizedList) {
					final IFeatureStructure feature = child.getStructure().getParent();
					if ((feature != null) && feature.isMandatory() && feature.isAnd() && !feature.isFirstChild(child.getStructure())) {
						final IFeatureStructure parent = feature.getParent();
						if ((parent != null) && parent.isAnd()) {
							feature.removeChild(child.getStructure());
							parent.addChild(child.getStructure());
							break;
						}
					}
				}
			}
		}
		return fm;
	}

	public static IFeatureModel generalization(IFeatureModel originalFM, long id, int numberOfEdits) {
		final IFeatureModel fm = originalFM.clone();
		final IFeatureModelFactory factory = FMFactoryManager.getInstance().getFactory(fm);
		final Random random = new Random(id);

		for (int i = 0; i < numberOfEdits; i++) {
			final List<IFeature> list = new LinkedList<>(Functional.toList(fm.getFeatures()));
			final List<IFeature> randomizedList = new LinkedList<>();
			while (!list.isEmpty()) {
				randomizedList.add(list.remove(random.nextInt(list.size())));
			}
			final int r = 1 + random.nextInt(9);
			if (r == 1) {
				// Alternative to Or
				for (final IFeature feature : randomizedList) {
					if ((feature.getStructure().getChildrenCount() > 1) && feature.getStructure().isAlternative()) {
						feature.getStructure().changeToOr();
						break;
					}
				}
			} else if (r == 2) {
				// move Optional into Or
				r2: for (final IFeature feature : randomizedList) {
					final IFeatureStructure parent = feature.getStructure().getParent();
					if ((parent != null) && parent.isAnd() && feature.getStructure().isMandatory() && feature.getStructure().isOr()) {
						for (final IFeatureStructure child : parent.getChildren()) {
							if (!child.isMandatory()) {
								parent.removeChild(child);
								feature.getStructure().addChild(child);
								break r2;
							}
						}
					}
				}
			} else if (r == 3) {
				// And to Or
				for (final IFeature feature : randomizedList) {
					if ((feature.getStructure().getChildrenCount() > 1) && feature.getStructure().isAnd()) {
						feature.getStructure().changeToOr();
						break;
					}
				}
			} else if (r == 4) {
				// new feature in Alternative
				for (final IFeature feature : randomizedList) {
					if (feature.getStructure().hasChildren() && feature.getStructure().isAlternative()) {
						int j = 1;
						IFeature child;
						do {
							child = factory.createFeature(fm, "C" + j++);
						} while (!fm.addFeature(child));
						feature.getStructure().addChild(child.getStructure());
						break;
					}
				}
			} else if (r == 5) {
				// Or to And
				for (final IFeature feature : randomizedList) {
					if ((feature.getStructure().getChildrenCount() > 1) && feature.getStructure().isOr()) {
						final IFeatureStructure parent = feature.getStructure().getParent();
						if ((parent != null) && !parent.isFirstChild(feature.getStructure()) && parent.isAnd()) {
							parent.removeChild(feature.getStructure());
							for (final IFeatureStructure child : feature.getStructure().getChildren()) {
								parent.addChild(child);
								child.setMandatory(false);
							}
							break;
						}
					}
				}
			} else if (r == 6) {
				// Mandatory to Optional
				for (final IFeature feature : randomizedList) {
					final IFeatureStructure parent = feature.getStructure().getParent();
					if ((parent != null) && parent.isAnd() && !parent.isFirstChild(feature.getStructure()) && feature.getStructure().isMandatory()) {
						feature.getStructure().setMandatory(false);
						fm.addConstraint(new Constraint(fm, new Implies(new Literal(parent.getFeature().getName()), new Literal(feature.getName()))));
						break;
					}
				}
			} else if (r == 7) {
				// Alternative to And
				for (final IFeature feature : randomizedList) {
					if ((feature.getStructure().getChildrenCount() > 1) && feature.getStructure().isAlternative()) {
						final IFeatureStructure parent = feature.getStructure().getParent();
						if ((parent != null) && !parent.isFirstChild(feature.getStructure()) && parent.isAnd()) {
							parent.removeChild(feature.getStructure());
							for (final IFeatureStructure child : feature.getStructure().getChildren()) {
								parent.addChild(child);
								child.setMandatory(false);
							}
							break;
						}
					}
				}
			} else if (r == 8) {
				// new Optional in And
				for (final IFeature feature : randomizedList) {
					if (feature.getStructure().hasChildren() && feature.getStructure().isAnd()) {
						int j = 1;
						IFeature child;
						do {
							child = factory.createFeature(fm, "C" + j++);
						} while (!fm.addFeature(child));
						child.getStructure().setMandatory(false);
						feature.getStructure().addChild(child.getStructure());
						break;
					}
				}
			} else {
				// remove Constraint
				final List<Node> nodes = Functional.toList(FeatureUtils.getPropositionalNodes(fm.getConstraints()));
				if (!nodes.isEmpty()) {
					final int index = random.nextInt(nodes.size());
					fm.getConstraints().remove(new Constraint(fm, nodes.get(index)));
				}
			}
		}
		return fm;
	}

	public static IFeatureModel arbitraryEdits(IFeatureModel originalFM, long id, int numberOfEdits) {
		final boolean valid = FeatureModelManager.getAnalyzer(originalFM).isValid(null);
		IFeatureModel fm = originalFM.clone();
		final IFeatureModelFactory factory = FMFactoryManager.getInstance().getFactory(fm);
		final Random random = new Random(id);

		for (int i = 0; i < numberOfEdits; i++) {
			final IFeatureModel backup = valid ? fm.clone() : null;

			final List<IFeature> list = new LinkedList<>(Functional.toList(fm.getFeatures()));
			final List<IFeature> randomizedList = new LinkedList<>();
			while (!list.isEmpty()) {
				randomizedList.add(list.remove(random.nextInt(list.size())));
			}
			final int r = 1 + random.nextInt(5);
			if (r == 1) {
				// delete or add feature
				if (random.nextBoolean()) {
					for (final IFeature feature : randomizedList) {
						final IFeatureStructure parent = feature.getStructure().getParent();
						if (!feature.getStructure().hasChildren() && (parent != null) && !parent.isFirstChild(feature.getStructure())) {
							fm.deleteFeature(feature);
							break;
						}
					}
				} else {
					for (final IFeature feature : randomizedList) {
						if (feature.getStructure().hasChildren()) {
							int j = 1;
							IFeature child;
							do {
								child = factory.createFeature(fm, "C" + j++);
							} while (!fm.addFeature(child));
							feature.getStructure().addChild(child.getStructure());
							break;
						}
					}
				}
			} else if (r == 2) {
				// alter group type
				for (final IFeature feature : randomizedList) {
					if (feature.getStructure().hasChildren()) {
						if (feature.getStructure().isAlternative()) {
							if (random.nextBoolean()) {
								feature.getStructure().changeToAnd();
							} else {
								feature.getStructure().changeToOr();
							}
						} else if (feature.getStructure().isAnd()) {
							if (random.nextBoolean()) {
								feature.getStructure().changeToAlternative();
							} else {
								feature.getStructure().changeToOr();
							}
						} else if (random.nextBoolean()) {
							feature.getStructure().changeToAnd();
						} else {
							feature.getStructure().changeToAlternative();
						}
						break;
					}
				}
			} else if (r == 3) {
				// change mandatory/optional
				for (final IFeature feature : randomizedList) {
					final IFeatureStructure parent = feature.getStructure().getParent();
					if ((parent != null) && parent.isAnd() && !parent.isFirstChild(feature.getStructure())) {
						feature.getStructure().setMandatory(!feature.getStructure().isMandatory());
						break;
					}
				}
			} else if (r == 4) {
				// move a concrete feature to another branch
				for (final IFeature feature : randomizedList) {
					final IFeatureStructure parent = feature.getStructure().getParent();
					if (!feature.getStructure().hasChildren() && (parent != null) && !parent.isFirstChild(feature.getStructure())) {
						parent.removeChild(feature.getStructure());
						IFeatureStructure newParent = parent;
						for (final IFeature compound : randomizedList) {
							if (!compound.equals(parent.getFeature()) && compound.getStructure().hasChildren()) {
								newParent = compound.getStructure();
							}
						}
						newParent.addChild(feature.getStructure());
						break;
					}
				}
			} else {
				// delete or add constraint
				if ((fm.getConstraints().size() > 0) && random.nextBoolean()) {
					final int index = random.nextInt(fm.getConstraints().size());
					fm.removeConstraint(index);
				} else {
					generateConstraints(fm, random, 1);
				}
			}

			if (valid && !FeatureModelManager.getAnalyzer(fm).isValid(null)) {
				System.out.println("Void feature model by arbitrary edit	" + r);
				fm = backup;
				i--;
			}
		}
		return fm;
	}

	private static Literal getRandomLiteral(Object[] names, Random random) {
		final int index = random.nextInt(names.length);
		return new Literal(names[index], random.nextBoolean());
	}

	public static String getTimeString(long dur) {
		if (dur < 1000000) {
			return dur + "nsec";
		}
		dur /= 1000000;
		if (dur < 1000) {
			return dur + "msec";
		}
		dur /= 1000;
		if (dur < 60) {
			return dur + "sec";
		}
		dur /= 60;
		if (dur < 60) {
			return dur + "min";
		}
		dur /= 60;
		return dur + "h";
	}

}
