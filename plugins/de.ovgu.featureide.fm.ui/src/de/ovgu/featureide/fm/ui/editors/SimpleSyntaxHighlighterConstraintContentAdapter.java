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
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.fm.ui.editors;

import java.util.Arrays;

import org.eclipse.jface.fieldassist.IControlContentAdapter;
import org.eclipse.jface.fieldassist.IControlContentAdapter2;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.prop4j.NodeReader;

import de.ovgu.featureide.fm.core.Features;

/**
 * contentAdapter for content assist while typing constraints
 *
 * @author David Broneske
 * @author Fabian Benduhn
 * @author Rahel Arens
 * @author Johannes Herschel
 */
public class SimpleSyntaxHighlighterConstraintContentAdapter implements IControlContentAdapter, IControlContentAdapter2 {

	public enum TextChangeMode {
		INSERT_TEXT, REPLACE_TEXT, UNKNOWN
	}

	public static class InsertionResult {

		/**
		 * Selection after performing the insertion
		 */
		final Point selection;

		/**
		 * Resulting text after performing the insertion
		 */
		final String text;

		public InsertionResult(Point selection, String text) {
			this.selection = selection;
			this.text = text;
		}

		public Point getSelection() {
			return selection;
		}

		public String getText() {
			return text;
		}

	}

	@Override
	public void insertControlContents(Control control, String text, int cursorPosition) {

		final SimpleSyntaxHighlightEditor editor = (SimpleSyntaxHighlightEditor) control;
		final Point selection = editor.getSelection();
		final String currentText = editor.getText();

		final boolean isFeature = !Arrays.asList(NodeReader.textualSymbols).contains(" " + text + " ");
		final InsertionResult result = performInsertion(currentText, selection, text, isFeature);

		editor.setText(result.text);
		editor.setSelection(result.selection);
	}

	/**
	 * @param currentText complete constraint text
	 * @param selection caret position, beginning and end of selection if a substring is selected
	 * @param textToInsert text to be inserted, without quotation marks
	 * @param isFeature true iff the inserted text is a feature
	 * @return return
	 */
	public static InsertionResult performInsertion(final String currentText, final Point selection, final String textToInsert, final boolean isFeature) {
		String before = "", after = "";
		String text = textToInsert;

		if (text.contains(Features.FEATURE_SUFFIX)) {
			text = "\"" + text.replace(Features.FEATURE_SUFFIX, "").trim() + "\"";
		} else if (text.contains(" ")) {
			text = "\"" + text + "\"";
		}

		switch (getMode(selection)) {
		case INSERT_TEXT: {
			final int substringStartIndex = getSubStringStartIndex(currentText, selection.x);
			before = currentText.substring(0, substringStartIndex);
			after = currentText.substring(selection.x);
		}
			break;
		case REPLACE_TEXT:
			before = currentText.substring(0, Math.min(selection.x, getSubStringStartIndex(currentText, selection.x)));
			after = currentText.substring(selection.y, currentText.length());
			break;
		default:
			throw new UnsupportedOperationException();
		}

		if (!before.isEmpty() && !before.endsWith(" ") && !isFeature) {
			before += " ";
		}

		if (!before.isEmpty() && isFeature && !before.endsWith("(") && !before.endsWith(" ")) {
			before += " ";
		}

		if (!after.isEmpty() && !after.startsWith(" ")) {
			after = " " + after;
		}

		final String newText = before + text + after;
		final int pos = (before + text).length();
		return new InsertionResult(new Point(pos, pos), newText);
	}

	private static int getSubStringStartIndex(final String currentText, final int x) {
		int substringStartIndex = Math.max(0, x);

		// count number of quotation marks
		int quotMarkCounter = 0;
		for (int i = 0; i < substringStartIndex; i++) {
			if (currentText.charAt(i) == '\"') {
				quotMarkCounter++;
			}
		}

		final char separator = (quotMarkCounter % 2) != 0 ? '\"' : ' ';

		// compute start index
		for (; substringStartIndex > 0; substringStartIndex--) {
			final char ch = currentText.charAt(substringStartIndex - 1);
			if ((ch == separator) || (ch == '(') || (ch == ')')) {
				break;
			}
		}

		if (separator == '\"') {
			substringStartIndex--;
		}

		return substringStartIndex;
	}

	private static TextChangeMode getMode(final Point selection) {
		return selection.x == selection.y ? TextChangeMode.INSERT_TEXT : selection.x < selection.y ? TextChangeMode.REPLACE_TEXT : TextChangeMode.UNKNOWN;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.taskassistance.IControlContentAdapter# getControlContents(org.eclipse.swt.widgets.Control)
	 */
	@Override
	public String getControlContents(Control control) {
		return ((SimpleSyntaxHighlightEditor) control).getText();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.fieldassist.IControlContentAdapter#setControlContents (org.eclipse.swt.widgets.Control, java.lang.String, int)
	 */
	@Override
	public void setControlContents(Control control, String text, int cursorPosition) {
		((SimpleSyntaxHighlightEditor) control).setText(text);
		((SimpleSyntaxHighlightEditor) control).setSelection(cursorPosition, cursorPosition);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.fieldassist.IControlContentAdapter#getCursorPosition (org.eclipse.swt.widgets.Control)
	 */
	@Override
	public int getCursorPosition(Control control) {
		return ((SimpleSyntaxHighlightEditor) control).getSelectionRanges()[0];
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.fieldassist.IControlContentAdapter#getInsertionBounds (org.eclipse.swt.widgets.Control)
	 */
	@Override
	public Rectangle getInsertionBounds(Control control) {
		final SimpleSyntaxHighlightEditor text = (SimpleSyntaxHighlightEditor) control;
		final Point caretOrigin = text.getSelection();
		// We fudge the y pixels due to problems with getCaretLocation
		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=52520
		return new Rectangle(caretOrigin.x + text.getClientArea().x, caretOrigin.y + text.getClientArea().y + 3, 1, text.getLineHeight());
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.fieldassist.IControlContentAdapter#setCursorPosition (org.eclipse.swt.widgets.Control, int)
	 */
	@Override
	public void setCursorPosition(Control control, int position) {
		((SimpleSyntaxHighlightEditor) control).setSelection(new Point(position, position));
	}

	/**
	 * @see org.eclipse.jface.fieldassist.IControlContentAdapter2#getSelection(org.eclipse.swt.widgets.Control)
	 *
	 * @since 3.4
	 */
	@Override
	public Point getSelection(Control control) {
		return ((SimpleSyntaxHighlightEditor) control).getSelection();
	}

	/**
	 * @see org.eclipse.jface.fieldassist.IControlContentAdapter2#setSelection(org.eclipse.swt.widgets.Control, org.eclipse.swt.graphics.Point)
	 *
	 * @since 3.4
	 */
	@Override
	public void setSelection(Control control, Point range) {
		((SimpleSyntaxHighlightEditor) control).setSelection(range);
	}
}
