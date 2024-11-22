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
package de.ovgu.featureide.fm.ui.editors.elements;

import java.util.HashSet;
import java.util.List;

import org.prop4j.NodeWriter;

import de.ovgu.featureide.fm.core.PluginID;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.color.ColorScheme;
import de.ovgu.featureide.fm.core.color.FeatureColor;
import de.ovgu.featureide.fm.core.color.FeatureColorManager;
import de.ovgu.featureide.fm.core.io.APersistentFormat;
import de.ovgu.featureide.fm.ui.editors.IGraphicalConstraint;
import de.ovgu.featureide.fm.ui.editors.IGraphicalFeatureModel;

/**
 * This class implements a LaTeX converter for the feature diagram (using TikZ). <br> The main class uses one String for the latex code; the subclasses divides
 * the code into three different Strings (each subclass for one String).
 *
 * @author Simon Wenk
 * @author Yang Liu
 */
public class TikzGraphicalFeatureModelFormat extends APersistentFormat<IGraphicalFeatureModel> {

	private static final String lnSep = System.lineSeparator();

	public static final String ID = PluginID.PLUGIN_ID + ".format.fm." + TikzGraphicalFeatureModelFormat.class.getSimpleName();

	/**
	 * Processes a String to make special symbols LaTeX compatible.
	 *
	 * @param str a StringBuilder which content should be LaTeX code
	 * @return a StringBuilder which contend has compatible LaTeX code
	 */
	public static void postProcessing(StringBuilder str) {
		str.replace(0, str.length(), str.toString().replace("_", "\\_"));
	}

	/**
	 * Creates the styles and packages that are required for the converted Feature Model Diagram. <br> <br> <b>Note:</b> For exporting purposes the file name
	 * must be <i> head.tex </i> and exported in the same folder as the main file.
	 */
	public static class TikZHeadFormat extends APersistentFormat<IGraphicalFeatureModel> {

		/**
		 * Writes the required styles and packages in a String.
		 *
		 * @param object The graphic feature model
		 * @return LaTeX Code as a String
		 */
		@Override
		public String write(IGraphicalFeatureModel object) {
			final StringBuilder str = new StringBuilder();
			printHead(str, object);
			return str.toString();
		}

		private static void printHead(StringBuilder str, IGraphicalFeatureModel object) {
			final boolean hasVerticalLayout = object.getLayout().hasVerticalLayout();

			str.append("%---required packages & variable definitions------------------------------------" + lnSep //
				+ "\\usepackage{forest}" + lnSep //
				+ "\\usepackage{amsmath}" + lnSep //
				+ "\\usepackage{xcolor}" + lnSep //
				+ "\\usetikzlibrary{angles}" + lnSep //
				+ "\\usetikzlibrary{positioning}" + lnSep //
				+ "\\definecolor{drawColor}{RGB}{128 128 128}" + lnSep //

				+ "\\newcommand{\\circleSize}{0.25em}" + lnSep //
				+ "\\newcommand{\\angleSize}{0.8em}" + lnSep //

				+ "%-------------------------------------------------------------------------------" + lnSep //

				+ "%---Define the style of the tree------------------------------------------------" + lnSep //
				+ "\\forestset{" + lnSep //

				+ "	/tikz/mandatory/.style={" + lnSep //
				+ "		circle,fill=drawColor," + lnSep //
				+ "		draw=drawColor," + lnSep //

				+ "		inner sep=\\circleSize" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/optional/.style={" + lnSep //
				+ "		circle," + lnSep //
				+ "		fill=white," + lnSep //
				+ "		draw=drawColor," + lnSep //
				+ "		inner sep=\\circleSize" + lnSep //
				+ "	}," + lnSep //
				+ "	featureDiagram/.style={" + lnSep //
				+ "		for tree={" + lnSep //
				+ "			minimum height = 0.6cm," + lnSep //
				+ "			text depth = 0," + lnSep //
				+ "			draw = drawColor," + lnSep //
				+ "			edge = {draw=drawColor}," + lnSep //
				+ String.format( //
						"			parent anchor = %s," + lnSep //
							+ "			child anchor = %s," + lnSep //
							+ "%s" //
							+ "			l sep = 2em," + lnSep //
							+ "			s sep = 1em," + lnSep //
							+ "%s", //
						hasVerticalLayout ? "east" : "south", //
						hasVerticalLayout ? "west" : "north", //
						hasVerticalLayout ? "			grow' = east," + lnSep : "", //
						hasVerticalLayout ? "			tier/.pgfmath=level()," + lnSep : "")
				+ "		}" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/redColor/.style={" + lnSep //
				+ "		fill = red!60," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/orangeColor/.style={" + lnSep //
				+ "		fill = orange!50," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/yellowColor/.style={" + lnSep //
				+ "		fill = yellow!50," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/darkGreenColor/.style={" + lnSep //
				+ "		fill = black!30!green," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/lightGreenColor/.style={" + lnSep //
				+ "		fill = green!30," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/cyanColor/.style={" + lnSep //
				+ "		fill = cyan!30," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/lightGrayColor/.style={" + lnSep //
				+ "		fill = black!10," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/blueColor/.style={" + lnSep //
				+ "		fill = blue!50," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/magentaColor/.style={" + lnSep //
				+ "		fill = magenta," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/pinkColor/.style={" + lnSep //
				+ "		fill = pink!90," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/abstract/.style={" + lnSep //
				+ "		fill = blue!85!cyan!5," + lnSep //
				+ "		draw = drawColor" + lnSep //

				+ "	}," + lnSep //
				+ "	/tikz/concrete/.style={" + lnSep //
				+ "		fill = blue!85!cyan!20," + lnSep //
				+ "		draw = drawColor" + lnSep //
				+ "	}," + lnSep //
				+ "	mandatory/.style={" + lnSep //
				+ "		edge label={node [mandatory] {} }" + lnSep //
				+ "	}," + lnSep //
				+ "	optional/.style={" + lnSep //

				+ "		edge label={node [optional] {} }" + lnSep //
				+ "	}," + lnSep //
				+ "	or/.style={" + lnSep //
				+ "		tikz+={" + lnSep //

				+ "			\\path (.parent) coordinate (A) -- (!u.children) coordinate (B) -- (!ul.parent) coordinate (C) pic[fill=drawColor, angle radius=\\angleSize]{angle};"
				+ lnSep //
				+ "		}	" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/or/.style={" + lnSep //
				+ "	}," + lnSep //
				+ "	alternative/.style={" + lnSep //
				+ "		tikz+={" + lnSep //
				+ "			\\path (.parent) coordinate (A) -- (!u.children) coordinate (B) -- (!ul.parent) coordinate (C) pic[draw=drawColor, angle radius=\\angleSize]{angle};"
				+ lnSep //
				+ "		}	" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/alternative/.style={" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/placeholder/.style={" + lnSep //

				+ "	}," + lnSep //
				+ "	collapsed/.style={" + lnSep //
				+ "		rounded corners," + lnSep //
				+ "		no edge," + lnSep //
				+ "		for tree={" + lnSep //

				+ "			fill opacity=0," + lnSep //
				+ "			draw opacity=0," + lnSep //
				+ "			l = 0em," + lnSep //
				+ "		}" + lnSep //
				+ "	}," + lnSep //
				+ "	/tikz/hiddenNodes/.style={" + lnSep //
				+ "		midway," + lnSep //
				+ "		rounded corners," + lnSep //
				+ "		draw=drawColor," + lnSep //

				+ "		fill=white," + lnSep //
				+ "		minimum size = 1.2em," + lnSep //
				+ "		minimum width = 0.8em," + lnSep //
				+ "		scale=0.9" + lnSep //

				+ "	}," + lnSep //
				+ "}" + lnSep //
				+ "%-------------------------------------------------------------------------------" + lnSep //
			);
		}

		@Override
		public boolean supportsRead() {
			return false;

		}

		@Override
		public boolean supportsWrite() {
			return true;

		}

		@Override
		public String getSuffix() {
			return ".tex";
		}

		@Override
		public String getName() {
			return "LaTeX-Document with TikZ";
		}

		@Override
		public String getId() {
			return ID;
		}
	}

	/**
	 * Creates the body for the LaTeX export. <br> <br> <b>Note:</b> For exporting purposes the file name must be <i> body.tex </i> and exported in the same
	 * folder as the main file. This file runs only with the head file and the main file.
	 */
	public static class TikZBodyFormat extends APersistentFormat<IGraphicalFeatureModel> {

		private final String fileName;

		/**
		 * @param FileName The file name of the main file
		 */
		public TikZBodyFormat(String FileName) {
			fileName = FileName;
		}

		/**
		 * Constructs the body of the LaTeX file, includes the head file and the main file and writes it in a String.
		 *
		 * @param object The graphic feature model
		 * @return LaTeX Code as a String
		 */
		@Override
		public String write(IGraphicalFeatureModel object) {
			final StringBuilder str = new StringBuilder();
			printBody(str, fileName);
			return str.toString();
		}

		private static void printBody(StringBuilder str, String FileName) {
			str.append("\\documentclass[border=5pt]{standalone}" + lnSep);
			str.append("\\input{head.tex}" + lnSep); // Include head
			str.append("\\begin{document}" + lnSep + "	");
			str.append("\\sffamily" + lnSep);
			str.append("	\\input{" + FileName + "}" + lnSep); // Include main
			str.append("\\end{document}");
		}

		@Override
		public boolean supportsRead() {
			return false;

		}

		@Override
		public boolean supportsWrite() {
			return true;

		}

		@Override
		public String getSuffix() {
			return ".tex";
		}

		@Override
		public String getName() {
			return "LaTeX-Document with TikZ";
		}

		@Override
		public String getId() {
			return ID;
		}

	}

	/**
	 * Creates the main file. This contains the converted Feature Diagram. <br>
	 *
	 * <b> Note: </b> The class <i> TikZHead </i> creates the styles and packages that are required to execute the resulting LaTeX Code correctly.
	 *
	 * @see TikZHeadFormat
	 */
	public static class TikZMainFormat extends APersistentFormat<IGraphicalFeatureModel> {

		private final boolean[] legend = new boolean[7];

		private IFeatureModel featureModel;

		/**
		 * Writes the tree of the Feature Diagram in tex-format.
		 *
		 * @param object The graphic feature model
		 * @return LaTeX Code as a String
		 */
		@Override
		public String write(IGraphicalFeatureModel object) {
			featureModel = object.getFeatureModelManager().getSnapshot();
			final StringBuilder str = new StringBuilder();
			printForest(object, str);
			return str.toString();
		}

		@Override
		public boolean supportsWrite() {
			return true;
		}

		@Override
		public String getSuffix() {
			return ".tex";
		}

		@Override
		public String getName() {
			return "LaTeX-Document with TikZ";
		}

		@Override
		public String getId() {
			return ID;
		}

		private void insertNodeHead(String node, IGraphicalFeatureModel object, StringBuilder str) {
			str.append("[" + node);
			if (object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().isAbstract()) {
				str.append(",abstract");
				legend[0] = true;
			}
			if (object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().isConcrete()) {
				str.append(",concrete");
				legend[1] = true;
			}
			if (!object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().isRoot()
				&& object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getParent().isAnd()) {
				if (object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().isMandatory()) {
					str.append(",mandatory");
					legend[2] = true;
				} else {
					str.append(",optional");
					legend[3] = true;
				}
			}
			if (!object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().isRoot()) {
				if (object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getParent().isOr()
					&& object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getParent().getFirstChild()
							.equals(object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure())) {
					str.append(",or");
					legend[4] = true;
				}
			}
			if (!object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().isRoot()) {
				if (object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getParent().isAlternative()
					&& object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getParent().getFirstChild()
							.equals(object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure())) {
					str.append(",alternative");
					legend[5] = true;

				}
			}
			final FeatureColor fc = FeatureColorManager.getColor(featureModel.getFeature(node));
			if (fc != FeatureColor.NO_COLOR) {
				str.append("," + featureColorToTikzStyle(fc));
			}
		}

		private static String featureColorToTikzStyle(FeatureColor featureColor) {
			switch (featureColor) {
			case Red:
				return "redColor";
			case Orange:
				return "orangeColor";
			case Yellow:
				return "yellowColor";
			case Dark_Green:
				return "darkGreenColor";
			case Light_Green:
				return "lightGreenColor";
			case Cyan:
				return "cyanColor";
			case Light_Gray:
				return "lightGrayColor";
			case Blue:
				return "blueColor";
			case Magenta:
				return "magentaColor";
			case Pink:
				return "pinkColor";
			case NO_COLOR:
				return "";
			default:
				throw new IllegalStateException(String.valueOf(featureColor));
			}
		}

		private static void insertNodeTail(StringBuilder str) {
			str.append("]");
		}

		private void printTree(String node, IGraphicalFeatureModel object, StringBuilder str) {
			// PRE-OREDER TRAVERSEL
			final int numberOfChildren = object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getChildrenCount();
			if ((numberOfChildren == 0)) {
				insertNodeHead(node, object, str);
				insertNodeTail(str);
			} else if (object.getGraphicalFeature(featureModel.getFeature(node)).isCollapsed()) {
				legend[6] = true;
				insertNodeHead(node, object, str);
				str.append("[,collapsed,edge label={node[hiddenNodes]{" + countNodes(node, object, -1) + "}}]");
				insertNodeTail(str);
			} else {
				insertNodeHead(node, object, str);
				final List<IFeatureStructure> nodesChildren =
					object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getChildren();
				final Iterable<IFeatureStructure> myChildren = nodesChildren;
				for (final IFeatureStructure child : myChildren) {
					printTree(child.getFeature().getName(), object, str);
				}
				insertNodeTail(str);
			}
		}

		private int countNodes(String node, IGraphicalFeatureModel object, int counter) {
			final int numberOfChildren = object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getChildrenCount();
			if (numberOfChildren == 0) {
				return ++counter;
			} else {
				final List<IFeatureStructure> nodesChildren =
					object.getGraphicalFeature(featureModel.getFeature(node)).getObject().getStructure().getChildren();
				final Iterable<IFeatureStructure> myChildren = nodesChildren;
				for (final IFeatureStructure child : myChildren) {
					counter = countNodes(child.getFeature().getName(), object, counter);

				}
				return ++counter;
			}
		}

		private String getRoot(IGraphicalFeatureModel object) {
			final Iterable<IFeature> myList = featureModel.getFeatures();
			String myRoot = null;

			for (final IFeature feature : myList) {
				if (object.getGraphicalFeature(featureModel.getFeature(feature.getName())).getObject().getStructure().isRoot()) {
					myRoot = feature.getName();
					break;
				}
			}

			return myRoot;
		}

		private void printForest(IGraphicalFeatureModel object, final StringBuilder str) {
			str.append("\\begin{forest}" + lnSep + "\tfeatureDiagram" + lnSep + "\t");
			final StringBuilder treeStringBuilder = new StringBuilder();
			printTree(getRoot(object), object, treeStringBuilder);
			postProcessing(treeStringBuilder);
			str.append(treeStringBuilder);
			str.append("\t" + lnSep);
			if (!object.isLegendHidden()) {
				printLegend(str, object);
			}
			printConstraints(str, object);
			str.append("\\end{forest}");
		}

		private void printLegend(StringBuilder str, IGraphicalFeatureModel graphicalFeatureModel) {
			boolean check = false;
			final StringBuilder sb = new StringBuilder();
			if (legend[0] && legend[1]) {
				check = true;
				sb.append("		\\node [abstract,label=right:Abstract Feature] {}; \\\\" + lnSep);
				legend[0] = false;
				sb.append("		\\node [concrete,label=right:Concrete Feature] {}; \\\\" + lnSep);
				legend[1] = false;
			}
			if (legend[0]) {
				check = true;
				sb.append("		\\node [abstract,label=right:Feature] {}; \\\\" + lnSep);
				legend[0] = false;
			}
			if (legend[1]) {
				check = true;
				sb.append("		\\node [concrete,label=right:Feature] {}; \\\\" + lnSep);
				legend[1] = false;
			}
			if (legend[2]) {
				check = true;
				sb.append("		\\node [mandatory,label=right:Mandatory] {}; \\\\" + lnSep);
				legend[2] = false;
			}
			if (legend[3]) {
				check = true;
				sb.append("		\\node [optional,label=right:Optional] {}; \\\\" + lnSep);
				legend[3] = false;
			}
			if (legend[4]) {
				check = true;
				// myString.append(" \\filldraw[drawColor] (0.45,0.15) ++ (225:0.3) arc[start angle=315,end angle=225,radius=0.2]; " + lnSep
				// + " \\node [or,label=right:Or] {}; \\\\" + lnSep);
				sb.append("			\\filldraw[drawColor] (0.1,0) - +(-0,-0.2) - +(0.2,-0.2)- +(0.1,0);" + lnSep
					+ "			\\draw[drawColor] (0.1,0) -- +(-0.2, -0.4);" + lnSep + "			\\draw[drawColor] (0.1,0) -- +(0.2,-0.4);" + lnSep
					+ "			\\fill[drawColor] (0,-0.2) arc (240:300:0.2);" + lnSep + "		\\node [or,label=right:Or Group] {}; \\\\");
				legend[4] = false;
			}
			if (legend[5]) {
				check = true;
				// myString.append(" \\draw[drawColor] (0.45,0.15) ++ (225:0.3) arc[start angle=315,end angle=225,radius=0.2] -- cycle; " + lnSep
				// + " \\node [alternative,label=right:Alternative] {}; \\\\" + lnSep);
				sb.append("			\\draw[drawColor] (0.1,0) -- +(-0.2, -0.4);" + lnSep + "			\\draw[drawColor] (0.1,0) -- +(0.2,-0.4);" + lnSep
					+ "			\\draw[drawColor] (0,-0.2) arc (240:300:0.2);" + lnSep + "		\\node [alternative,label=right:Alternative Group] {}; \\\\");
				legend[5] = false;
			}
			if (legend[6]) {
				check = true;
				sb.append("		\\node [hiddenNodes,label=center:1,label=right:Collapsed Nodes] {}; \\\\" + lnSep);
				legend[6] = false;
			}

			final ColorScheme colorScheme = FeatureColorManager.getCurrentColorScheme(graphicalFeatureModel.getFeatureModelManager().getSnapshot());
			int colorIndex = 1;

			for (final FeatureColor currentColor : new HashSet<>(colorScheme.getColors().values())) {
				if (currentColor != FeatureColor.NO_COLOR) {
					String meaning = currentColor.getMeaning();
					if (meaning.isEmpty()) {
						meaning = "Custom Color " + String.format("%02d", colorIndex);
						colorIndex++;
					}
					sb.append("		\\node [" + featureColorToTikzStyle(currentColor) + ",label=right:" + meaning + "] {}; \\\\" + lnSep);
				}
			}

			if (check) {
				str.append("	\\matrix [anchor=north west] at (current bounding box.north east) {" + lnSep + "		\\node [placeholder] {}; \\\\" + lnSep
					+ "	};" + lnSep + "	\\matrix [draw=drawColor,anchor=north west] at (current bounding box.north east) {" + lnSep
					+ "		\\node [label=center:\\underline{Legend:}] {}; \\\\" + lnSep);
				str.append(sb);
				str.append("	};" + lnSep);
				check = false;
			} else {
				for (int i = 0; i < legend.length; ++i) {
					legend[i] = false;
				}
				check = false;
			}
		}

		private void printConstraints(StringBuilder str, IGraphicalFeatureModel graphicalFeatureModel) {
			str.append("	\\matrix [below=1mm of current bounding box] {" + lnSep);
			for (final IGraphicalConstraint constraint : graphicalFeatureModel.getConstraints()) {
				String text = constraint.getObject().getNode().toString(NodeWriter.latexSymbols, true);
				text = text.replaceAll("\"([\\w\" ]+)\"", " \\\\text\\{$1\\} "); // wrap all words in \text{} // replace with $2
				text = text.replaceAll("\\s+", " "); // remove unnecessary whitespace characters
				str.append("	\\node {\\(" + text + "\\)}; \\\\" + lnSep);
			}
			str.append("	};" + lnSep);
		}
	}

	/**
	 * Writes the converted feature diagram with all required packages and styles (for the tex format) in a String.
	 *
	 * @param object The graphic feature model
	 * @return LaTeX Code as String
	 */
	@Override
	public String write(IGraphicalFeatureModel object) {
		final StringBuilder str = new StringBuilder();
		str.append("\\documentclass[border=5pt]{standalone}");
		str.append(lnSep);
		TikZHeadFormat.printHead(str, object);
		str.append("\\begin{document}" + lnSep + "	%---The Feature Diagram-----------------------------------------------------" + lnSep);
		new TikZMainFormat().printForest(object, str);
		str.append(lnSep);
		str.append("\t%---------------------------------------------------------------------------" + lnSep + "\\end{document}");
		return str.toString();
	}

	@Override
	public boolean supportsWrite() {
		return true;
	}

	@Override
	public APersistentFormat<IGraphicalFeatureModel> getInstance() {
		return new TikzGraphicalFeatureModelFormat();
	}

	/**
	 * Return the suffix of the extension.
	 *
	 * @return the suffix of the extension
	 */
	@Override
	public String getSuffix() {
		return ".tex";
	}

	/**
	 * Returns the name/description of the extension as a string.
	 *
	 * @return the name/description of the extension
	 */
	@Override
	public String getName() {
		return "LaTeX-Document with TikZ";
	}

	@Override
	public String getId() {
		return ID;
	}

}
