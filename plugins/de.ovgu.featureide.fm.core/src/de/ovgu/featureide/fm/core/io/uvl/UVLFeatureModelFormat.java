/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2020  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.fm.core.io.uvl;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.prop4j.Equals;
import org.prop4j.Implies;
import org.prop4j.Literal;
import org.prop4j.Node;

import de.ovgu.featureide.fm.core.PluginID;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.MultiConstraint;
import de.ovgu.featureide.fm.core.base.impl.MultiFeature;
import de.ovgu.featureide.fm.core.base.impl.MultiFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.MultiFeatureModelFactory;
import de.ovgu.featureide.fm.core.constraint.FeatureAttribute;
import de.ovgu.featureide.fm.core.io.AFeatureModelFormat;
import de.ovgu.featureide.fm.core.io.APersistentFormat;
import de.ovgu.featureide.fm.core.io.LazyReader;
import de.ovgu.featureide.fm.core.io.Problem;
import de.ovgu.featureide.fm.core.io.Problem.Severity;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.vill.config.Configuration;
import de.vill.exception.ParseError;
import de.vill.exception.ParseErrorList;
import de.vill.main.UVLModelFactory;
import de.vill.model.Attribute;
import de.vill.model.Feature;
import de.vill.model.FeatureModel;
import de.vill.model.Group;
import de.vill.model.Group.GroupType;
import de.vill.model.Import;
import de.vill.model.LanguageLevel;
import de.vill.model.constraint.AndConstraint;
import de.vill.model.constraint.Constraint;
import de.vill.model.constraint.EquivalenceConstraint;
import de.vill.model.constraint.ImplicationConstraint;
import de.vill.model.constraint.LiteralConstraint;
import de.vill.model.constraint.NotConstraint;
import de.vill.model.constraint.OrConstraint;
import de.vill.model.constraint.ParenthesisConstraint;

/**
 * Reads / writes feature models in the UVL format.
 *
 * @author Dominik Engelhardt
 * @author Johannes Herschel
 */
public class UVLFeatureModelFormat extends AFeatureModelFormat {

	public static final String ID = PluginID.PLUGIN_ID + ".format.fm." + UVLFeatureModelFormat.class.getSimpleName();
	public static final String FILE_EXTENSION = "uvl";

	private static final String NS_ATTRIBUTE_NAME = "namespace";
	private static final String NS_ATTRIBUTE_FEATURE = "synthetic_ns_feature";

	protected static final String EXTENDED_ATTRIBUTE_NAME = "extended__";

	private static final String FEATURE_DESCRIPTION_ATTRIBUTE_NAME = "featureDescription__";

	// Patterns for import validation. The same as in the BNF of the UVL parser.
	private static final Pattern ID_PATTERN = Pattern.compile("(?!true|false)[a-zA-Z][a-zA-Z_0-9]*");
	private static final Pattern STRICT_ID_RESTRICTIVE_PATTERN =
		Pattern.compile("(?!alternative|or|features|constraints|true|false|as|refer)[a-zA-Z][a-zA-Z_0-9]*");

	private static Map<Class<? extends Constraint>, Integer> prioritiy = new HashMap<>();
	static {
		final Map<Class<? extends Constraint>, Integer> tempMap = new HashMap<>();
		tempMap.put(LiteralConstraint.class, 0);
		tempMap.put(NotConstraint.class, 1);
		tempMap.put(AndConstraint.class, 2);
		tempMap.put(OrConstraint.class, 3);
		tempMap.put(ImplicationConstraint.class, 4);
		tempMap.put(EquivalenceConstraint.class, 5);
		prioritiy = Collections.unmodifiableMap(tempMap);
	}

	private FeatureModel rootModel;
	protected ProblemList pl;
	protected MultiFeatureModelFactory factory;

	@Override
	public String getName() {
		return "UVL";
	}

	@Override
	public String getSuffix() {
		return FILE_EXTENSION;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public APersistentFormat<IFeatureModel> getInstance() {
		return new UVLFeatureModelFormat();
	}

	@Override
	public ProblemList read(IFeatureModel fm, CharSequence source) {
		if (fm.getSourceFile() != null) {
			return read(fm, source, fm.getSourceFile().toAbsolutePath());
		}
		System.err.println("No path set for model. Can't load imported models.");
		return read(fm, source, new File("./.").toPath());
	}

	@Override
	public ProblemList read(IFeatureModel fm, CharSequence source, Path path) {
		fm.setSourceFile(path);
		pl = new ProblemList();
		final UVLModelFactory uvlModelFactory = new UVLModelFactory();
		try {
			rootModel = uvlModelFactory.parse(source.toString(), path.resolve("..").normalize().toString());
			uvlModelFactory.convertAllMoreComplexLanguageLevels(rootModel, LanguageLevel.BOOLEAN_LEVEL);
			constructFeatureModel((MultiFeatureModel) fm);
		} catch (final ParseError e) {
			if (e instanceof ParseErrorList) {
				final List<ParseError> errorList = ((ParseErrorList) e).getErrorList();
				for (final ParseError error : errorList) {
					pl.add(toProblem(error));
				}
			} else {
				pl.add(toProblem(e));
			}
		}
		return pl;
	}

	private void constructFeatureModel(MultiFeatureModel fm) {
		factory = (MultiFeatureModelFactory) FMFactoryManager.getInstance().getFactory(fm);
		fm.reset();
		parseImports(rootModel, fm);
		final IFeature rootFeature;
		final Feature uvlRootFeature = rootModel.getRootFeature();
		rootFeature = parseFeature(fm, uvlRootFeature, null);
		fm.getStructure().setRoot(rootFeature.getStructure());
		fm.addAttribute(rootFeature.getName(), NS_ATTRIBUTE_FEATURE, rootModel.getNamespace());
		parseConstraints(fm);

		// final Map<String, UsedModel> externalModels = fm.getExternalModels();
	}

	private IFeature parseFeature(MultiFeatureModel fm, Feature uvlFeature, IFeature parentFeature) {
		final MultiFeature feature = factory.createFeature(fm, uvlFeature.getReferenceFromSpecificSubmodel(""));
		fm.addFeature(feature);

		final Attribute<?> featureDescription = uvlFeature.getAttributes().get(FEATURE_DESCRIPTION_ATTRIBUTE_NAME);
		if ((featureDescription != null) && (featureDescription.getValue() instanceof String)) {
			feature.getProperty().setDescription(((String) featureDescription.getValue()).replace("\\n", "\n"));
		}

		if (parentFeature != null) {
			parentFeature.getStructure().addChild(feature.getStructure());
		}
		if (uvlFeature.getAttributes().containsKey("abstract")) {
			feature.getStructure().setAbstract(true);
		}
		parseAttributes(fm, feature, uvlFeature);
		if (uvlFeature.getReferenceFromSpecificSubmodel("").contains(".")) {
			feature.setType(MultiFeature.TYPE_INTERFACE);
		}
		if (uvlFeature.isSubmodelRoot()) {
			fm.addAttribute(uvlFeature.getReferenceFromSpecificSubmodel(""), NS_ATTRIBUTE_FEATURE, uvlFeature.getRelatedImport().getNamespace());
		}

		if (uvlFeature.getChildren().size() == 1) {
			parseGroup(fm, uvlFeature.getChildren().get(0), feature);
		} else {
			feature.getStructure().setAnd();
			for (final Group group : uvlFeature.getChildren()) {
				final MultiFeature groupParent = factory.createFeature(fm, getGroupFeatureName(fm, feature, group));
				fm.addFeature(groupParent);
				groupParent.getStructure().setAbstract(true);
				groupParent.getStructure().setMandatory(true);
				feature.getStructure().addChild(groupParent.getStructure());
				parseGroup(fm, group, groupParent);
			}
		}

		return feature;
	}

	private String getGroupFeatureName(IFeatureModel featureModel, IFeature parentFeature, Group group) {
		int index = 0;
		String name = parentFeature.getName() + "_" + group.GROUPTYPE.toString() + "_" + index;
		while (featureModel.getFeature(name) != null) {
			name = group.GROUPTYPE.toString() + "_" + ++index;
		}
		return name;
	}

	private void parseGroup(MultiFeatureModel fm, Group uvlGroup, IFeature parentFeature) {
		final List<IFeature> children = new LinkedList<>();
		for (final Feature feature : uvlGroup.getFeatures()) {
			children.add(parseFeature(fm, feature, parentFeature));
		}

		if (uvlGroup.GROUPTYPE.equals(GroupType.GROUP_CARDINALITY)) {
			if ((uvlGroup.getLowerBound().equals("1")) && (uvlGroup.getUpperBound().equals("*"))) {
				parentFeature.getStructure().setOr();
			} else if ((uvlGroup.getLowerBound().equals("1")) && (uvlGroup.getUpperBound().equals("1"))) {
				parentFeature.getStructure().setAlternative();
			} else if ((uvlGroup.getLowerBound().equals("0")) && (uvlGroup.getUpperBound().equals("*"))) {
				// optional is true if nothing else is set
			} else if ((uvlGroup.getLowerBound().equals(uvlGroup.getUpperBound()))
				&& (uvlGroup.getUpperBound().equals(Integer.toString(uvlGroup.getFeatures().size())))) {
					children.forEach(f -> f.getStructure().setMandatory(true));
				} else {
					pl.add(new Problem(String.format("Failed to convert cardinality [%s..%s] to known group type at feature %s.", uvlGroup.getLowerBound(),
							uvlGroup.getUpperBound(), parentFeature.getName()), 0, Severity.WARNING));
				}
		}

		switch (uvlGroup.GROUPTYPE) {
		case OR:
			parentFeature.getStructure().setOr();
			break;
		case ALTERNATIVE:
			parentFeature.getStructure().setAlternative();
			break;
		case OPTIONAL:
			break;
		case MANDATORY:
			children.forEach(f -> f.getStructure().setMandatory(true));
			break;
		case GROUP_CARDINALITY:
			break;
		default:
			break;
		}
	}

	private void parseAttributes(MultiFeatureModel fm, MultiFeature feature, Feature uvlFeature) {
		uvlFeature.getAttributes().entrySet().stream().forEachOrdered(e -> parseAttribute(fm, feature, e.getKey(), e.getValue().getValue()));
	}

	protected void parseAttribute(MultiFeatureModel fm, MultiFeature feature, String attributeKey, Object attributeValue) {
		if (attributeValue instanceof Constraint) {
			parseConstraint(fm, (Constraint) attributeValue);
		}

	}

	private void parseConstraints(MultiFeatureModel fm) {
		final List<Constraint> ownConstraints = rootModel.getOwnConstraints();
		final List<Constraint> allConstraints = new LinkedList<>();
		for (final Import importLine : rootModel.getImports()) {
			// only consider submodels from imports that are actually used
			if (importLine.isReferenced()) {
				allConstraints.addAll(importLine.getFeatureModel().getConstraints());
			}
		}

		for (final Constraint constraint : ownConstraints) {
			parseOwnConstraint(fm, constraint);
		}

		for (final Constraint constraint : allConstraints) {
			parseConstraint(fm, constraint);
		}

	}

	private void parseConstraint(MultiFeatureModel fm, Constraint c) {
		parseConstraint(fm, c, false);
	}

	private void parseOwnConstraint(MultiFeatureModel fm, Constraint c) {
		parseConstraint(fm, c, true);
	}

	private void parseConstraint(MultiFeatureModel fm, Constraint c, boolean own) {
		try {
			final Node constraint = parseConstraint(c);
			if (constraint != null) {
				final MultiConstraint newConstraint = factory.createConstraint(fm, constraint);
				if (own) {
					fm.addOwnConstraint(newConstraint);
				} else {
					newConstraint.setType(MultiFeature.TYPE_INTERFACE);
					fm.addConstraint(newConstraint);
				}
			}
		} catch (final RuntimeException e) {
			// Contained invalid reference. Already added to problem list
		}
	}

	private Node parseConstraint(Constraint constraint) {
		if (constraint instanceof AndConstraint) {
			return new org.prop4j.And(parseConstraint(((AndConstraint) constraint).getLeft()), parseConstraint(((AndConstraint) constraint).getRight()));
		} else if (constraint instanceof EquivalenceConstraint) {
			return new org.prop4j.Equals(parseConstraint(((EquivalenceConstraint) constraint).getLeft()),
					parseConstraint(((EquivalenceConstraint) constraint).getRight()));
		} else if (constraint instanceof ImplicationConstraint) {
			return new org.prop4j.Implies(parseConstraint(((ImplicationConstraint) constraint).getLeft()),
					parseConstraint(((ImplicationConstraint) constraint).getRight()));
		} else if (constraint instanceof NotConstraint) {
			return new org.prop4j.Not(parseConstraint(((NotConstraint) constraint).getContent()));
		} else if (constraint instanceof OrConstraint) {
			return new org.prop4j.Or(parseConstraint(((OrConstraint) constraint).getLeft()), parseConstraint(((OrConstraint) constraint).getRight()));
		} else if (constraint instanceof ParenthesisConstraint) {
			return parseConstraint(((ParenthesisConstraint) constraint).getContent());
		} else if (constraint instanceof LiteralConstraint) {
			return new org.prop4j.Literal(((LiteralConstraint) constraint).toString(false, "").replace("\"", ""));
		} else {
			return null;
		}
	}

	private void parseImports(FeatureModel uvlModel, MultiFeatureModel fm) {
		final List<Import> imports = uvlModel.getImports();
		for (final Import importLine : imports) {
			// only consider submodels that are actually referenced somewhere
			if (importLine.isReferenced()) {
				parseImports(importLine.getFeatureModel(), fm);
				parseImport(fm, importLine);
			}
		}
	}

	private void parseImport(MultiFeatureModel fm, Import i) {
		final Path path = fm.getSourceFile().resolveSibling(i.getNamespace().replace(".", "/") + "." + FILE_EXTENSION);
		fm.addInstance(i.getNamespace(), i.getAlias(), path);
	}

	/**
	 * @param error a {@link ParseError}
	 * @return a {@link Problem}
	 */
	private Problem toProblem(ParseError error) {
		return new Problem(error, error.getLine());
	}

	@Override
	public String write(IFeatureModel fm) {
		Configuration.setTabulatorSymbol("\t");
		final String uvlModel = featureIDEModelToUVLFeatureModel(fm).toString();
		return uvlModel;
	}

	private FeatureModel featureIDEModelToUVLFeatureModel(IFeatureModel fm) {
		final FeatureModel uvlModel = new FeatureModel();
		uvlModel.setNamespace(fm.getStructure().getRoot().getFeature().getName());

		if (fm instanceof MultiFeatureModel) {
			final MultiFeatureModel mfm = (MultiFeatureModel) fm;
			final FeatureAttribute<String> nsAttribute = mfm.getStringAttributes().getAttribute(NS_ATTRIBUTE_FEATURE, NS_ATTRIBUTE_NAME);
			if (nsAttribute != null) {
				uvlModel.setNamespace(nsAttribute.getValue());
			}

			uvlModel.getImports()
					.addAll(mfm.getExternalModels().values().stream().map(um -> new Import(um.getModelName(), um.getVarName())).collect(Collectors.toList()));
		}

		final Feature rootFeature = featureIDEFeatureToUVLFeature(fm.getStructure().getRoot().getFeature());
		uvlModel.setRootFeature(rootFeature);
		uvlModel.getOwnConstraints().addAll(featureIDEConstraintsToUVLConstraints(fm));
		return uvlModel;
	}

	private Feature featureIDEFeatureToUVLFeature(IFeature feature) {
		final String featureReference = feature.getName();
		final String[] featureReferenceParts = featureReference.split("\\.");
		String featureName;
		String featureNamespace;
		if (featureReferenceParts.length > 1) {
			featureName = featureReferenceParts[featureReferenceParts.length - 1];
			featureNamespace = featureReference.substring(0, featureReference.length() - featureName.length() - 1);
		} else {
			featureName = featureReferenceParts[0];
			featureNamespace = null;
		}

		final Feature uvlFeature = new Feature(featureName);
		if (featureNamespace != null) {
			uvlFeature.setNameSpace(featureNamespace);
			uvlFeature.setSubmodelRoot(true);
			return uvlFeature;
		}

		uvlFeature.getAttributes().putAll(printAttributes(feature));

		final String featureDescription = feature.getProperty().getDescription();
		if ((featureDescription != null) && !featureDescription.equals("")) {
			final Attribute<String> featureDescriptionAttribute =
				new Attribute<String>(FEATURE_DESCRIPTION_ATTRIBUTE_NAME, featureDescription.replace("\n", "\\n"));
			uvlFeature.getAttributes().put(FEATURE_DESCRIPTION_ATTRIBUTE_NAME, featureDescriptionAttribute);
		}

		if (feature.getStructure().isAlternative()) {
			final List<IFeature> alternativeChildren = feature.getStructure().getChildren().stream().map(x -> x.getFeature()).collect(Collectors.toList());
			final Group group = new Group(GroupType.ALTERNATIVE);
			for (final IFeature childFeature : alternativeChildren) {
				group.getFeatures().add(featureIDEFeatureToUVLFeature(childFeature));
			}
			uvlFeature.getChildren().add(group);
		} else if (feature.getStructure().isOr()) {
			final List<IFeature> orChildren = feature.getStructure().getChildren().stream().map(x -> x.getFeature()).collect(Collectors.toList());
			final Group group = new Group(GroupType.OR);
			for (final IFeature childFeature : orChildren) {
				group.getFeatures().add(featureIDEFeatureToUVLFeature(childFeature));
			}
			uvlFeature.getChildren().add(group);
		} else {
			Group group = new Group(GroupType.OPTIONAL);
			for (final IFeatureStructure fs : feature.getStructure().getChildren()) {
				if (fs.isMandatory()) {
					if (group.GROUPTYPE == GroupType.MANDATORY) {
						group.getFeatures().add(featureIDEFeatureToUVLFeature(fs.getFeature()));
					} else {
						if (group.getFeatures().size() > 0) {
							uvlFeature.getChildren().add(group);
						}
						group = new Group(GroupType.MANDATORY);
						group.getFeatures().add(featureIDEFeatureToUVLFeature(fs.getFeature()));
					}
				} else {
					if (group.GROUPTYPE == GroupType.OPTIONAL) {
						group.getFeatures().add(featureIDEFeatureToUVLFeature(fs.getFeature()));
					} else {
						if (group.getFeatures().size() > 0) {
							uvlFeature.getChildren().add(group);
						}
						group = new Group(GroupType.OPTIONAL);
						group.getFeatures().add(featureIDEFeatureToUVLFeature(fs.getFeature()));
					}
				}
			}
			if (group.getFeatures().size() > 0) {
				uvlFeature.getChildren().add(group);
			}
		}
		return uvlFeature;

	}

	protected Map<String, Attribute<?>> printAttributes(IFeature feature) {
		final Map<String, Attribute<?>> properties = new HashMap<>();
		if (feature.getStructure().isAbstract()) {
			properties.put("abstract", new Attribute<>("abstract", Boolean.TRUE));
		}
		return properties;
	}

	private List<Constraint> featureIDEConstraintsToUVLConstraints(IFeatureModel fm) {
		final List<Constraint> result = new LinkedList<>();
		for (final IConstraint constraint : fm.getConstraints()) {
			if ((constraint instanceof MultiConstraint) && (((MultiConstraint) constraint).getType() == MultiFeature.TYPE_INTERFACE)) {
				continue;
			}

			result.add(featureIDEConstraintToUVLConstraint(constraint.getNode()));
		}
		return result;
	}

	private Constraint featureIDEConstraintToUVLConstraint(Node n) {
		if (n instanceof Literal) {
			return new LiteralConstraint(((Literal) n).var.toString());
		} else if (n instanceof org.prop4j.Not) {
			Constraint child = featureIDEConstraintToUVLConstraint(n.getChildren()[0]);
			if (prioritiy.get(child.getClass()) > prioritiy.get(NotConstraint.class)) {
				child = new ParenthesisConstraint(child);
			}
			return new NotConstraint(child);
		} else if (n instanceof org.prop4j.And) {
			return printMultiArity(AndConstraint::new, n.getChildren(), AndConstraint.class);
		} else if (n instanceof org.prop4j.Or) {
			return printMultiArity(OrConstraint::new, n.getChildren(), OrConstraint.class);
		} else if (n instanceof Implies) {
			Constraint child1 = featureIDEConstraintToUVLConstraint(n.getChildren()[0]);
			Constraint child2 = featureIDEConstraintToUVLConstraint(n.getChildren()[1]);
			if (prioritiy.get(child1.getClass()) > prioritiy.get(ImplicationConstraint.class)) {
				child1 = new ParenthesisConstraint(child1);
			}
			if (prioritiy.get(child2.getClass()) > prioritiy.get(ImplicationConstraint.class)) {
				child2 = new ParenthesisConstraint(child2);
			}
			return new ImplicationConstraint(child1, child2);
		} else if (n instanceof Equals) {
			Constraint child1 = featureIDEConstraintToUVLConstraint(n.getChildren()[0]);
			Constraint child2 = featureIDEConstraintToUVLConstraint(n.getChildren()[1]);
			if (prioritiy.get(child1.getClass()) > prioritiy.get(EquivalenceConstraint.class)) {
				child1 = new ParenthesisConstraint(child1);
			}
			if (prioritiy.get(child2.getClass()) > prioritiy.get(EquivalenceConstraint.class)) {
				child2 = new ParenthesisConstraint(child2);
			}
			return new EquivalenceConstraint(child1, child2);
		}
		return null;
	}

	private Constraint printMultiArity(BiFunction<Constraint, Constraint, Constraint> constructor, Node[] args, Class<? extends Constraint> constraintType) {
		switch (args.length) {
		case 0:
			return null;
		case 1:
			Constraint child = featureIDEConstraintToUVLConstraint(args[0]);
			if (prioritiy.get(child.getClass()) > prioritiy.get(constraintType)) {
				child = new ParenthesisConstraint(child);
			}
			return child;
		case 2:
			Constraint child1 = featureIDEConstraintToUVLConstraint(args[0]);
			Constraint child2 = featureIDEConstraintToUVLConstraint(args[1]);
			if (prioritiy.get(child1.getClass()) > prioritiy.get(constraintType)) {
				child1 = new ParenthesisConstraint(child1);
			}
			if (prioritiy.get(child2.getClass()) > prioritiy.get(constraintType)) {
				child2 = new ParenthesisConstraint(child2);
			}
			return constructor.apply(child1, child2);
		default:
			Constraint childLeft = featureIDEConstraintToUVLConstraint(args[0]);
			Constraint childRight = printMultiArity(constructor, Arrays.copyOfRange(args, 1, args.length), constraintType);
			if (prioritiy.get(childLeft.getClass()) > prioritiy.get(constraintType)) {
				childLeft = new ParenthesisConstraint(childLeft);
			}
			if (prioritiy.get(childRight.getClass()) > prioritiy.get(constraintType)) {
				childRight = new ParenthesisConstraint(childRight);
			}
			return constructor.apply(childLeft, childRight);
		}
	}

	@Override
	public boolean supportsRead() {
		return true;
	}

	@Override
	public boolean supportsWrite() {
		return true;
	}

	@Override
	public boolean supportsContent(CharSequence content) {
		return !content.toString().contains(EXTENDED_ATTRIBUTE_NAME);
	}

	@Override
	public boolean supportsContent(LazyReader reader) {
		return supportsContent((CharSequence) reader);
	}

	@Override
	public boolean isValidFeatureName(String featureName) {
		return featureName.matches("[^\\\"\\.\\n\\r]*");
	}

	@Override
	public boolean isValidImportName(String name) {
		final String[] splitName = name.split("\\.", -1);
		for (int i = 0; i < (splitName.length - 1); i++) {
			if (!ID_PATTERN.matcher(splitName[i]).matches()) {
				return false;
			}
		}
		return STRICT_ID_RESTRICTIVE_PATTERN.matcher(splitName[splitName.length - 1]).matches();
	}

	@Override
	public boolean isValidImportAlias(String alias) {
		return alias.isEmpty() || ID_PATTERN.matcher(alias).matches();
	}

	@Override
	public String getErrorMessage() {
		return "The characters  \" and . are not allowed and the feature name has to be non-empty.";
	}

	@Override
	public boolean initExtension() {
		FMFactoryManager.getInstance().getDefaultFactoryWorkspace().assignID(getId(), MultiFeatureModelFactory.ID);
		return super.initExtension();
	}

}
