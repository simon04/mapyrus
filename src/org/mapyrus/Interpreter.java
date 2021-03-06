/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 - 2013 Simon Chenery.
 *
 * Mapyrus is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mapyrus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mapyrus; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.mapyrus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.mapyrus.function.UserFunction;

/**
 * Language interpreter.  Parse and executes commands read from file, or
 * typed by user.
 *
 * May be called repeatedly to interpret several files in the same context.
 */
public class Interpreter implements Cloneable
{
	/*
	 * Character separating arguments to a statement.
	 * Tokens around definition of a procedure.
	 */
	private static final char ARGUMENT_SEPARATOR = ',';
	private static final char PARAM_SEPARATOR = ',';
	private static final String BEGIN_KEYWORD = "begin";
	private static final String FUNCTION_KEYWORD = "function";
	private static final String END_KEYWORD = "end";

	/*
	 * Keywords for if ... then ... else ... endif block.
	 */
	private static final String IF_KEYWORD = "if";
	private static final String THEN_KEYWORD = "then";
	private static final String ELSE_KEYWORD = "else";
	private static final String ELIF_KEYWORD = "elif";
	private static final String ENDIF_KEYWORD = "endif";

	/*
	 * Keywords for while ... do ... done and
	 * repeat ... do ... doneblocks.
	 */
	private static final String WHILE_KEYWORD = "while";
	private static final String REPEAT_KEYWORD = "repeat";
	private static final String DO_KEYWORD = "do";
	private static final String DONE_KEYWORD = "done";

	/*
	 * Keywords for for ... in ... do ... done block.
	 */
	private static final String FOR_KEYWORD = "for";
	private static final String IN_KEYWORD = "in";

	/*
	 * States during parsing statements.
	 */
	private static final int AT_ARGUMENT = 1;		/* at start of arguments to a statement */
	private static final int AT_SEPARATOR = 2;	/* at separator between arguments */

	private static final int AT_PARAM = 3;	/* at parameter to a procedure block */
	private static final int AT_PARAM_SEPARATOR = 4;	/* at separator between parameters */

	/*
	 * Literals for linestyles.
	 */
	public static final String CAP_BUTT_STRING = "butt";
	public static final String CAP_ROUND_STRING = "round";
	public static final String CAP_SQUARE_STRING = "square";
	public static final String JOIN_BEVEL_STRING = "bevel";
	public static final String JOIN_MITER_STRING = "miter";
	public static final String JOIN_ROUND_STRING = "round";

	private ContextStack m_context;
	private InputStream m_stdinStream;
	private PrintStream m_stdoutStream;

	private Throttle m_throttle;

	/*
	 * Evaluted arguments for statement currently being executed.
	 * A large number of statements will be executed (but only one at a
	 * time) so reusing buffer saves continually allocating a new buffers.
	 */
	Argument []m_executeArgs;

	/*
	 * Blocks of statements for each procedure defined in
	 * this interpreter.
	 */
	private HashMap<String, Statement> m_statementBlocks;

	/*
	 * Functions that user has defined.
	 */
	private HashMap<String, UserFunction> m_userFunctions;
	
	/*
	 * Static world coordinate system units lookup table.
	 */
	private static HashMap<String, Integer> m_worldUnitsLookup;

	static
	{
		m_worldUnitsLookup = new HashMap<String, Integer>();
		m_worldUnitsLookup.put("m", Integer.valueOf(Context.WORLD_UNITS_METRES));
		m_worldUnitsLookup.put("metres", Integer.valueOf(Context.WORLD_UNITS_METRES));
		m_worldUnitsLookup.put("meters", Integer.valueOf(Context.WORLD_UNITS_METRES));
		m_worldUnitsLookup.put("feet", Integer.valueOf(Context.WORLD_UNITS_FEET));
		m_worldUnitsLookup.put("foot", Integer.valueOf(Context.WORLD_UNITS_FEET));
		m_worldUnitsLookup.put("ft", Integer.valueOf(Context.WORLD_UNITS_FEET));
		m_worldUnitsLookup.put("degrees", Integer.valueOf(Context.WORLD_UNITS_DEGREES));
		m_worldUnitsLookup.put("degree", Integer.valueOf(Context.WORLD_UNITS_DEGREES));
		m_worldUnitsLookup.put("deg", Integer.valueOf(Context.WORLD_UNITS_DEGREES));
	}

	/**
	 * Parses all combinations of color setting.  Sets values passed
	 * by user in graphics context.
	 * @param context graphics context to set linestyle into.
	 * @param arguments to color statement.
	 * @param nArgs number of arguments to color statement.
	 */	
	private void setColor(ContextStack context, Argument []args, int nArgs)
		throws MapyrusException
	{
		int alpha = 255;
		float decimalAlpha = 1.0f;

		if (nArgs == 1 || nArgs == 2)
		{
			String color = args[0].getStringValue();
			Color c;

			if (nArgs == 2)
			{
				/*
				 * Parse transparency value.
				 */
				decimalAlpha = (float)args[1].getNumericValue();
				if (decimalAlpha < 0.0f)
					decimalAlpha = 0.0f;
				else if (decimalAlpha > 1.0f)
					decimalAlpha = 1.0f;

				alpha = (int)Math.round(decimalAlpha * 255.0);
			}

			/*
			 * Find named color or hex value in color database.
			 */
			c = ColorDatabase.getColor(color, alpha, context.getColor());
			if (c == null)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.COLOR_NOT_FOUND) +
					": " + color);
			}

			context.setColor(c);
		}
		else if (nArgs == 4 || nArgs == 5 || nArgs == 6)
		{
			String colorType = args[0].getStringValue();
			float c1 = (float)args[1].getNumericValue();
			float c2 = (float)args[2].getNumericValue();
			float c3 = (float)args[3].getNumericValue();
			float c4 = 0;
			int alphaIndex = 4;

			if (colorType.equalsIgnoreCase("cmyk"))
			{
				if (nArgs < 5)
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR));
				c4 = (float)args[4].getNumericValue();
				alphaIndex = 5;
			}
			else if (nArgs > 5)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR));
			}

			if (alphaIndex < nArgs)
			{
				/*
				 * Parse transparency value.
				 */
				decimalAlpha = (float)args[alphaIndex].getNumericValue();
				if (decimalAlpha < 0.0f)
					decimalAlpha = 0.0f;
				else if (decimalAlpha > 1.0f)
					decimalAlpha = 1.0f;

				alpha = (int)Math.round(decimalAlpha * 255.0);
			}

			/*
			 * Constrain color to valid range.
			 */
			if (c2 < 0.0f)
				c2 = 0.0f;
			else if (c2 > 1.0f)
				c2 = 1.0f;

			if (c3 < 0.0f)
				c3 = 0.0f;
			else if (c3 > 1.0f)
				c3 = 1.0f;

			if (c4 < 0.0f)
				c4 = 0.0f;
			else if (c4 > 1.0f)
				c4 = 1.0f;

			if (colorType.equalsIgnoreCase("hsb"))
			{
				/*
				 * Set HSB color.
				 */
				int rgb = Color.HSBtoRGB(c1, c2, c3);
				rgb = (rgb & 0xffffff);
				context.setColor(new Color(rgb | (alpha << 24), true));
			}
			else if (colorType.equalsIgnoreCase("rgb"))
			{
				if (c1 < 0.0f)
					c1 = 0.0f;
				else if (c1 > 1.0f)
					c1 = 1.0f;

				/*
				 * Set RGB color.
				 */
				context.setColor(new Color(c1, c2, c3, decimalAlpha));
			}
			else if (colorType.equalsIgnoreCase("cmyk"))
			{
				if (c1 < 0.0f)
					c1 = 0.0f;
				else if (c1 > 1.0f)
					c1 = 1.0f;
				
				/*
				 * Set color with ColorSpace to identify it as CMYK.
				 */
				float []components = new float[]{c1, c2, c3, c4};
				Color cmykColor = new Color(new CMYKColorSpace(), components, decimalAlpha);
				context.setColor(cmykColor);
			}
			else
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR_TYPE) +
					": " + colorType);
			}
		}
		else
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR));
		}
	}

	/**
	 * Parses all combinations of linestyle setting.  Sets values passed
	 * by user, with defaults for the values they did not give.
	 * @param context graphics context to set linestyle into.
	 * @param arguments to linestyle statement.
	 * @param nArgs number of arguments to linestyle statement.
	 */	
	private void setLinestyle(ContextStack context, Argument []args, int nArgs)
		throws MapyrusException
	{
		double width = 0.1, dashPhase = 0.0;
		float dashes[] = null;
		int cap = BasicStroke.CAP_SQUARE;
		int join = BasicStroke.JOIN_MITER;

		if (nArgs == 0)
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINESTYLE));

		width = args[0].getNumericValue();
		if (width < 0)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_WIDTH) +
				": " + width);
		}

		if (nArgs >= 2)
		{
			String s = args[1].getStringValue().toLowerCase();
			if (s.equals(CAP_BUTT_STRING))
				cap = BasicStroke.CAP_BUTT;
			else if (s.equals(CAP_ROUND_STRING))
				cap = BasicStroke.CAP_ROUND;
			else if (s.equals(CAP_SQUARE_STRING))
				cap = BasicStroke.CAP_SQUARE;
			else
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_END_CAP) + ": " + s);
		}

		if (nArgs >= 3)
		{
			String s = args[2].getStringValue().toLowerCase();
			if (s.equals(JOIN_BEVEL_STRING))
				join = BasicStroke.JOIN_BEVEL;
			else if (s.equals(JOIN_MITER_STRING))
				join = BasicStroke.JOIN_MITER;
			else if (s.equals(JOIN_ROUND_STRING))
				join = BasicStroke.JOIN_ROUND;
			else
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_JOIN) + ": " + s);
				
		}

		if (nArgs >= 4)
		{
			dashPhase = args[3].getNumericValue();
			if (dashPhase < 0)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DASH_PHASE) +
					": " + dashPhase);
			}
		}

		if (nArgs >= 5)
		{
			/*
			 * Build list of dash pattern values.
			 */
			dashes = new float[nArgs - 4];
			for (int i = 4; i < nArgs; i++)
			{
				dashes[i - 4] = (float)(args[i].getNumericValue());
				if (dashes[i - 4] <= 0.0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DASH_PATTERN) +
						": " + dashes[i - 4]);
				}
			}
		}

		context.setLinestyle(width, cap, join, dashPhase, dashes);	
	}
	
	/**
	 * Parse justification for labels and then set it.
	 * @param context graphics context to set justification value into.
	 * @param justify value given in statement.
	 */
	private void setJustify(ContextStack context, String justify)
	{
		int justifyCode;
		justify = justify.toLowerCase();

		if (justify.indexOf("center") >= 0 ||
			justify.indexOf("centre") >= 0)
		{
			justifyCode = OutputFormat.JUSTIFY_CENTER;
		}
		else if (justify.indexOf("right") >= 0)
		{
			justifyCode = OutputFormat.JUSTIFY_RIGHT;
		}
		else
		{
			justifyCode = OutputFormat.JUSTIFY_LEFT;
		}
	
		if (justify.indexOf("top") >= 0)
		{
			justifyCode |= OutputFormat.JUSTIFY_TOP;
		}
		else if (justify.indexOf("middle") >= 0)
		{
			justifyCode |= OutputFormat.JUSTIFY_MIDDLE;
		}
		else
		{
			justifyCode |= OutputFormat.JUSTIFY_BOTTOM;
		}
		context.setJustify(justifyCode);
	}

	/**
	 * Parse font for labels and then set it.
	 * @param context graphics context to set font into.
	 * @param args arguments to font statement.
	 * @param nArgs number of arguments to font statement.
	 */
	private void setFont(ContextStack context, Argument []args, int nArgs)
		throws MapyrusException
	{
		double size, outlineWidth = 0.0, lineSpacing = 1;

		if (nArgs == 2 || nArgs == 3)
		{
			size = args[1].getNumericValue();
			if (size <= 0.0)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SIZE) +
					": " + size);
			}

			if (nArgs == 3)
			{
				String extras = args[2].getStringValue();
				StringTokenizer st = new StringTokenizer(extras);
				while (st.hasMoreTokens())
				{
					String token = st.nextToken();
					if (token.startsWith("outlinewidth="))
					{
						/*
						 * Parse line width to use for drawing outline of each
						 * letter in a label.
						 */
						String width = token.substring(13);
						try
						{
							outlineWidth = Double.parseDouble(width);
						}
						catch (NumberFormatException e)
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_WIDTH) +
								": " + width);
						}
						if (outlineWidth < 0)
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_WIDTH) +
								": " + outlineWidth);
						}
					}
					else if (token.startsWith("linespacing="))
					{
						/*
						 * Parse line spacing for multi-line labels.
						 */
						String spacing = token.substring(12);
						try
						{
							lineSpacing = Double.parseDouble(spacing);
						}
						catch (NumberFormatException e)
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SPACING) +
								": " + spacing);
						}
					}
				}
			}

			context.setFont(args[0].getStringValue(), size,
				outlineWidth, lineSpacing);
		}
		else
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_FONT));
		}
	}

	/**
	 * Draw legend for procedures used at moveto points in current path.
	 * @param st legend statement being executed.
	 * @param context contains path and output page.
	 * @param legendEntrySize size of entry in legend.
	 */
	private void displayLegend(Statement st, ContextStack context, double legendEntrySize)
		throws MapyrusException, IOException, InterruptedException
	{
		LegendEntryList legendList = context.getLegendEntries();
		ArrayList<Point2D> moveTos = context.getMoveTos();

		/*
		 * Drawing legend will itself generate new legend entries.
		 * Ignore any new legend entries while the legend is being drawn.
		 */
		legendList.ignoreAdditions();

		/*
		 * Draw only as many legend entries as there are moveto points.
		 */
		long nEntries = Math.min(legendList.size(), moveTos.size());
		for (int i = 0; i < nEntries; i++)
		{
			LegendEntry entry = legendList.pop();
			String blockName = entry.getBlockName();

			Statement block = (Statement)m_statementBlocks.get(blockName);
			if (block == null)
			{
				throw new MapyrusException(st.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNDEFINED_PROC) +
					": " + blockName);
			}

			/*
			 * Check that correct number of parameters are being passed.
			 */
			ArrayList<String> formalParameters = block.getBlockParameters();
			if (entry.getBlockArgs().length != formalParameters.size())
			{
				throw new MapyrusException(st.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.WRONG_PARAMETERS));
			}

			context.saveState(blockName);
			Point2D.Float pt = (Point2D.Float)(moveTos.get(i));
			context.setTranslation(pt.x, pt.y);

			/*
			 * Draw description label for legend entry just to the right of
			 * the symbol, line or box.
			 */
			context.clearPath();
			context.moveTo(legendEntrySize * 1.1 + 2, legendEntrySize / 2);
			String description = entry.getDescription();
			int index;
			while ((index = description.indexOf("(#)")) >= 0)
			{
				/*
				 * Replace part of legend description with number of times this
				 * entry was used.
				 */
				description = description.substring(0, index + 1) +
					entry.getReferenceCount() + description.substring(index + 2);
			}
			context.label(description);

			/*
			 * Set path to a point, line or box and then call procedure block
			 * to draw the symbol for the legend.
			 */
			context.clearPath();
			if (entry.getType() == LegendEntry.POINT_ENTRY)
			{
				/*
				 * Set path to a single point.
				 */
				context.setTranslation(legendEntrySize / 2, legendEntrySize / 2);
				context.moveTo(0.0, 0.0);
			}
			else if (entry.getType() == LegendEntry.LINE_ENTRY)
			{
				/*
				 * Set path to a horizontal line.
				 */
				context.moveTo(0.0, legendEntrySize / 2);
				context.lineTo(legendEntrySize, legendEntrySize / 2);
			}
			else if (entry.getType() == LegendEntry.ZIGZAG_ENTRY)
			{
				/*
				 * Set path to a zigzag line /\/\.
				 */
				context.moveTo(0.0, legendEntrySize / 2);
				context.lineTo(legendEntrySize / 3, legendEntrySize);
				context.lineTo(legendEntrySize * 2 / 3, 0.0);
				context.lineTo(legendEntrySize, legendEntrySize / 2);
			}
			else if (entry.getType() == LegendEntry.BOX_ENTRY)
			{
				/*
				 * Set path to a square.
				 */
				context.moveTo(0.0, 0.0);
				context.lineTo(0.0, legendEntrySize);
				context.lineTo(legendEntrySize, legendEntrySize);
				context.lineTo(legendEntrySize, 0.0);
				context.lineTo(0.0, 0.0);
			}

			/*
			 * Save additional state for boxes so that any clip region set
			 * by the procedure block is cleared before drawing outline box.
			 */
			if (entry.getType() == LegendEntry.BOX_ENTRY)
				context.saveState(blockName);
				
			makeCall(block, formalParameters, entry.getBlockArgs());

			if (entry.getType() == LegendEntry.BOX_ENTRY)
			{
				/*
				 * Draw black outline around box.
				 */
				context.restoreState();
				context.setColor(Color.BLACK);
				context.setLinestyle(0.1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0.0, null);
				context.stroke(null);
			}
			context.restoreState();
		}
		legendList.acceptAdditions();
	}

	/**
	 * Walk through a geometry, adding it to current path.
	 * @param context context containing path to add geometry to.
	 * @param coords geometry array to add to path.
	 * @param index index in geometry array at which to start walking.
	 * @return index one greater than the last element in the geometry array. 
	 */
	private int addGeometryToPath(ContextStack context, double []coords, int index)
		throws MapyrusException
	{
		int i;
		int geometryType = (int)(coords[index]);
		int nCoords = (int)(coords[index + 1]);
		index += 2;

		/*
		 * Add geometry to path.  Complex, nested geometries must be
		 * added recursively.
		 */
		switch (geometryType)
		{
			case Argument.GEOMETRY_POINT:
			case Argument.GEOMETRY_LINESTRING:
			case Argument.GEOMETRY_POLYGON:
				for (i = 0; i < nCoords; i++)
				{
					double x = coords[index + 1];
					double y = coords[index + 2];
					if (coords[index] == Argument.MOVETO)
						context.moveTo(x, y);
					else
						context.lineTo(x, y);
					index += 3;
				}
				break;
			case Argument.GEOMETRY_MULTIPOINT:
			case Argument.GEOMETRY_MULTILINESTRING:
			case Argument.GEOMETRY_MULTIPOLYGON:
			case Argument.GEOMETRY_COLLECTION:
				for (i = 0; i < nCoords; i++)
				{
					index = addGeometryToPath(context, coords, index);
				}
				break;
		}
		return(index);
	}

	/*
	 * Execute a single statement, changing the path, context or generating
	 * some output.
	 */
	private void execute(Statement st, ContextStack context)
		throws MapyrusException, IOException, InterruptedException
	{
		Expression []expr;
		int nExpressions;
		StatementType type;
		double degrees, radius = 0.0;
		double x1, y1, x2, y2;
		double px1, py1, px2, py2;
		boolean allowDistortion;
		int units;
		double legendSize;
		String extras;

		/*
		 * If this thread was interrupted by another thread then
		 * stop execution.
		 */
		if (Thread.interrupted())
			throw new InterruptedException(MapyrusMessages.get(MapyrusMessages.INTERRUPTED));
		m_throttle.sleep();

		expr = st.getExpressions();
		nExpressions = expr.length;

		/*
		 * Do not evaluate variables for local statement -- we want the
		 * original list of variable names instead.
		 */
		type = st.getType();
		if (type == StatementType.LOCAL)
		{
			for (int i = 0; i < nExpressions; i++)
			{
				String varName = expr[i].getVariableName();
				if (varName == null)
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.VARIABLE_EXPECTED));
				context.setLocalScope(varName);
			}
		}
		else
		{
			/*
			 * Make sure buffer we're keeping for command arguments is big
			 * enough for this command.
			 */
			if (m_executeArgs == null || nExpressions > m_executeArgs.length)
				m_executeArgs = new Argument[nExpressions];

			/*
			 * Evaluate each of the expressions for this statement.
			 */
			String interpreterFilename = st.getFilename();
			for (int i = 0; i < nExpressions; i++)
			{
				m_executeArgs[i] = expr[i].evaluate(context, interpreterFilename);
			}
		}

		switch (type)
		{
			case COLOR:
			case COLOUR:
				setColor(context, m_executeArgs, nExpressions);
				break;

			case BLEND:
				if (nExpressions == 1)
					context.setBlend(m_executeArgs[0].getStringValue());
				else
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_BLEND));
				break;

			case LINESTYLE:
				setLinestyle(context, m_executeArgs, nExpressions);
				break;

			case FONT:
				setFont(context, m_executeArgs, nExpressions);
				break;

			case JUSTIFY:
				if (nExpressions == 1)
					setJustify(context, m_executeArgs[0].getStringValue());
				else
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_JUSTIFY));
				break;
		
			case MOVE:
			case DRAW:
			case RDRAW:
				if (nExpressions > 0 && nExpressions % 2 == 0)
				{
					for (int i = 0; i < nExpressions; i += 2)
					{
						/*
						 * Add point to path.
						 */
						if (type == StatementType.MOVE)
						{
							context.moveTo(m_executeArgs[i].getNumericValue(),
								m_executeArgs[i + 1].getNumericValue());
						}
						else if (type == StatementType.DRAW)
						{
							context.lineTo(m_executeArgs[i].getNumericValue(),
								m_executeArgs[i + 1].getNumericValue());
						}
						else /* RDRAW */
						{
							context.rlineTo(m_executeArgs[i].getNumericValue(),
								m_executeArgs[i + 1].getNumericValue());
						}
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.WRONG_COORDINATE));
				}
				break;

			case ARC:
				if (nExpressions == 5)
				{
					int direction = (m_executeArgs[0].getNumericValue() > 0 ? 1 : -1);

					context.arcTo(direction,
						m_executeArgs[1].getNumericValue(),
						m_executeArgs[2].getNumericValue(),
						m_executeArgs[3].getNumericValue(),
						m_executeArgs[4].getNumericValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_ARC));
				}
				break;

			case CIRCLE:
				if (nExpressions == 3)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					if (radius > 0)
					{
							/*
							 * Add 360 degree arc making a full circle.
							 */
							context.moveTo(x1 - radius, y1);
							context.arcTo(1, x1, y1, x1 - radius, y1);
							context.closePath();
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_CIRCLE));
				}
				break;

			case ELLIPSE:
				if (nExpressions == 4)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					double xRadius = m_executeArgs[2].getNumericValue();
					double yRadius = m_executeArgs[3].getNumericValue();
					if (xRadius > 0 && yRadius > 0)
					{
						/*
						 * Add ellipse to path.
						 */
						context.ellipseTo(x1 - xRadius, y1 - yRadius,
							x1 + xRadius, y1 + yRadius);
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_ELLIPSE));
				}
				break;

			case CYLINDER:
				if (nExpressions == 4)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					double height = m_executeArgs[3].getNumericValue();

					if (radius > 0 && height > 0)
					{
						double xc = radius * 0.552285;
						double yc = xc / 2;

						context.moveTo(x1 - radius, y1);
						context.curveTo(x1 - radius, y1 - yc,
							x1 - xc, y1 - radius / 2,
							x1, y1 - radius / 2);
						context.curveTo(x1 + xc, y1 - radius / 2,
							x1 + radius, y1 - yc,
							x1 + radius, y1);
						context.lineTo(x1 + radius, y1 + height);
						context.curveTo(x1 + radius, y1 + height - yc,
							x1 + xc, y1 + height - radius / 2,
							x1, y1 + height - radius / 2);
						context.curveTo(x1 - xc, y1 + height - radius / 2,
							x1 - radius, y1 + height - yc,
							x1 - radius, y1 + height);
						context.closePath();

						context.ellipseTo(x1 - radius, y1 + height - radius / 2,
							x1 + radius, y1 + height + radius / 2);
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_CYLINDER));
				}
				break;

			case RAINDROP:
				if (nExpressions == 3)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					if (radius > 0)
					{
						double dist = radius;
						context.moveTo(x1 - radius, y1);
						context.arcTo(-1, x1, y1, x1 + radius, y1);
						context.curveTo(x1 + radius, y1 + dist,
								x1, y1 + radius * 3 - dist,
								x1, y1 + radius * 3);
						context.curveTo(x1, y1 + radius * 3 - dist,
								x1 - radius, y1 + dist,
								x1 - radius, y1);
						context.closePath();
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_RAINDROP));
				}
				break;

			case BEZIER:
				if (nExpressions == 6)
				{
					double xControl1 = m_executeArgs[0].getNumericValue();
					double yControl1 = m_executeArgs[1].getNumericValue();
					double xControl2 = m_executeArgs[2].getNumericValue();
					double yControl2 = m_executeArgs[3].getNumericValue();
					double xEnd = m_executeArgs[4].getNumericValue();
					double yEnd = m_executeArgs[5].getNumericValue();

					context.curveTo(xControl1, yControl1, xControl2, yControl2, xEnd, yEnd);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_BEZIER));
				}
				break;

			case SINEWAVE:
				if (nExpressions == 4)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					double nRepeats = m_executeArgs[2].getNumericValue();
					double amplitude = m_executeArgs[3].getNumericValue();
					context.sineWaveTo(x1, y1, nRepeats, amplitude);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SINEWAVE));
				}
				break;

			case WEDGE:
				if (nExpressions == 5 || nExpressions == 6)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					double startAngle = m_executeArgs[3].getNumericValue();
					double sweep = m_executeArgs[4].getNumericValue();
					double endAngle = startAngle + sweep;
					double height;
					if (nExpressions == 6)
						height = m_executeArgs[5].getNumericValue();
					else
						height = 0;
					int sign = sweep > 0 ? -1 : 1;
					startAngle = Math.toRadians(startAngle);
					endAngle = Math.toRadians(endAngle);
					if (radius > 0 && sweep != 0)
					{
						double cosStartAngle = Math.cos(startAngle);
						double sinStartAngle = Math.sin(startAngle);
						double cosEndAngle = Math.cos(endAngle);
						double sinEndAngle = Math.sin(endAngle);
						x2 = x1 + cosStartAngle * radius;
						y2 = y1 + sinStartAngle * radius;
						double x3 = x1 + cosEndAngle * radius;
						double y3 = y1 + sinEndAngle * radius;

						/*
						 * Add straight line segments and arc defining
						 * wedge (piece slice) shape.
						 */
						context.moveTo(x1, y1);
						context.lineTo(x2, y2);
						context.arcTo(sign, x1, y1, x3, y3);
						context.closePath();
						
						if (height > 0)
						{
							/*
							 * Add sections below the wedge to give it a 3D effect
							 * and make it look like a slice of cake.
							 * 
							 * Draw this first so that wedge overwrites it correctly.
							 */
							if ((sign == 1 && cosStartAngle < 0) ||
								(sign == -1 && cosStartAngle > 0))
							{
								/*
								 * Add rectangular section visible at
								 * start of wedge.
								 */
								context.moveTo(x2, y2);
								context.lineTo(x2, y2 - height);
								context.lineTo(x1, y1 - height);
								context.lineTo(x1, y1);
								context.closePath();
							}
							if ((sign == 1 && cosEndAngle > 0) ||
								(sign == -1 && cosEndAngle < 0))
							{
								/*
								 * Add rectangular section visible at
								 * end of wedge.
								 */
								context.moveTo(x3, y3);
								context.lineTo(x3, y3 - height);
								context.lineTo(x1, y1 - height);
								context.lineTo(x1, y1);
								context.closePath();
							}
							if (sinStartAngle < 0 || sinEndAngle < 0 || Math.abs(sweep) > 180)
							{
								/*
								 * Draw curved part of edge of wedge that is visible.
								 */
								double x4 = x2, y4 = y2;
								if (sinStartAngle > 0)
								{
									x4 = x1 + radius * sign;
									y4 = y1;
								}
								context.moveTo(x4, y4);
								context.lineTo(x4, y4 - height);
								double x5 = x3, y5 = y3;
								if (sinEndAngle > 0)
								{
									x5 = x1 - radius * sign;
									y5 = y1;
								}
								context.arcTo(sign, x1, y1 - height, x5, y5 - height);
								context.lineTo(x5, y5);
								context.arcTo(-sign, x1, y1, x4, y4);
								context.closePath();
							}
						}
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WEDGE));
				}
				break;

			case SPIRAL:
				if (nExpressions == 5)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					double revs = m_executeArgs[3].getNumericValue();
					double angle = m_executeArgs[4].getNumericValue();
					angle = Math.toRadians(angle);
					if (radius > 0 && revs != 0)
					{
						double resolution = context.getResolution();
						double angleStep = Math.acos((radius - resolution) / radius);
						double tStep = angleStep / (Math.PI * 2);
						tStep /= Math.abs(revs);
	
						double t = 1;
						int i = 0;
						while (t > 0)
						{
							x2 = radius * t * Math.cos(Math.PI * 2 * revs * t + angle) + x1;
							y2 = radius * t * Math.sin(Math.PI * 2 * revs * t + angle) + y1;
	
							if (i == 0)
								context.moveTo(x2, y2);
							else
								context.lineTo(x2, y2);
	
							i++;
							t = 1 - (i * tStep);
						}
						context.lineTo(x1, y1);
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SPIRAL));
				}
				break;

			case LOGSPIRAL:
				if (nExpressions == 6)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					double a = m_executeArgs[2].getNumericValue();
					double b = m_executeArgs[3].getNumericValue();
					double revs = m_executeArgs[4].getNumericValue();
					double startAngle = m_executeArgs[5].getNumericValue();
					startAngle = Math.toRadians(startAngle);
					if (revs != 0 && b > 0 && a > 0)
					{
						context.moveTo(x1, y1);

						double resolution = context.getResolution();

						double t = 0;
						double maxT = Math.abs(revs) * Math.PI * 2;
						while (t < maxT)
						{
							radius = a * Math.exp(b * t);
							
							if (radius >= resolution)
							{
								double currentAngle;
								if (revs > 0)
									currentAngle = startAngle + t;
								else
									currentAngle = startAngle - t;

								x2 = radius * Math.cos(currentAngle) + x1;
								y2 = radius * Math.sin(currentAngle) + y1;

								context.lineTo(x2, y2);

								t += Math.asin(resolution / radius);
							}
							else
							{
								t += 2 * Math.PI / 180.0; /* 2 degrees */
							}
						}
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LOGSPIRAL));
				}
				break;

			case HEXAGON:
				if (nExpressions == 3)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					double sin60radius = 0.8660254 * radius;
					double cos60radius = 0.5 * radius;
					if (radius > 0)
					{
							/*
							 * Add six points defining hexagon.
							 */
							context.moveTo(x1 - cos60radius, y1 - sin60radius);
							context.lineTo(x1 + cos60radius, y1 - sin60radius);
							context.lineTo(x1 + radius, y1);
							context.lineTo(x1 + cos60radius, y1 + sin60radius);
							context.lineTo(x1 - cos60radius, y1 + sin60radius);
							context.lineTo(x1 - radius, y1);
							context.closePath();
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_HEXAGON));
				}
				break;

			case PENTAGON:
				if (nExpressions == 3)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					double sin54radius = 0.809017 * radius;
					double cos54radius = 0.59778525 * radius;
					double sin18radius = 0.309017 * radius;
					double cos18radius = 0.95105652 * radius;
					if (radius > 0)
					{
						/*
						 * Add five points defining pentagon.
						 */
						context.moveTo(x1 - cos54radius, y1 - sin54radius);
						context.lineTo(x1 + cos54radius, y1 - sin54radius);
						context.lineTo(x1 + cos18radius, y1 + sin18radius);
						context.lineTo(x1, y1 + radius);
						context.lineTo(x1 - cos18radius, y1 + sin18radius);
						context.closePath();
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PENTAGON));
				}
				break;

			case TRIANGLE:
				if (nExpressions == 4)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					double rotation = m_executeArgs[3].getNumericValue();
					rotation = Math.toRadians(rotation);
					
					/*
					 * Precalculate triangle corner points.
					 */
					double sin30radius = 0.5 * radius;
					double cos30radius = 0.8660254 * radius;

					AffineTransform affine = AffineTransform.getTranslateInstance(x1, y1);
					affine.rotate(rotation);

					/*
					 * Add coordinates for equilateral triangle to path,
					 * rotated to desired angle.
					 */
					Point2D.Double pt = new Point2D.Double(0, radius);
					affine.transform(pt, pt);
					context.moveTo(pt.x, pt.y);

					pt.x = cos30radius;
					pt.y = -sin30radius;
					affine.transform(pt, pt);
					context.lineTo(pt.x, pt.y);

					pt.x = -cos30radius;
					pt.y = -sin30radius;
					affine.transform(pt, pt);
					context.lineTo(pt.x, pt.y);

					context.closePath();
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_TRIANGLE));
				}
				break;

			case STAR:
				if (nExpressions == 4)
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					radius = m_executeArgs[2].getNumericValue();
					int nPoints = (int)m_executeArgs[3].getNumericValue();

					if (radius > 0 && nPoints > 0)
					{
						double angleBetweenPoints = (Math.PI * 2) / nPoints;
						double angle = Math.PI / 2;

						/*
						 * Angle of each point of the star.
						 */
						double starPointAngle = angleBetweenPoints / 3;

						/*
						 * Calculate distance from center of star to inner
						 * coordinates of star using rule:
						 * a / sin(A) = b / sin(B).
						 */
						double dist = radius * Math.sin(starPointAngle / 2) /
							Math.sin(Math.PI - starPointAngle / 2 - angleBetweenPoints / 2);

						/*
						 * Add each point of the star, starting at the top
						 * and proceeding clockwise.
						 */
						for (int i = 0; i < nPoints; i++)
						{
							x2 = x1 + Math.cos(angle) * radius;
							y2 = y1 + Math.sin(angle) * radius;

							if (i == 0)
								context.moveTo(x2, y2);
							else
								context.lineTo(x2, y2);

							x2 = x1 + Math.cos(angle - angleBetweenPoints / 2) * dist;
							y2 = y1 + Math.sin(angle - angleBetweenPoints / 2) * dist;
							context.lineTo(x2, y2);

							angle -= angleBetweenPoints;
						}
						context.closePath();
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_STAR));
				}
				break;

			case BOX:
			case ROUNDEDBOX:
			case BOX3D:
			case CHESSBOARD:
			case GUILLOTINE:
			case PROTECT:
			case UNPROTECT:
				if (nExpressions == 4 || ((type == StatementType.ROUNDEDBOX ||
					type == StatementType.BOX3D ||
					type == StatementType.CHESSBOARD) && nExpressions == 5))
				{
					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					x2 = m_executeArgs[2].getNumericValue();
					y2 = m_executeArgs[3].getNumericValue();

					double xMin, xMax, yMin, yMax, tileSize = 1, depth = 0;
 
					if (x1 < x2)
					{
						xMin = x1;
						xMax = x2;
					}
					else
					{
						xMin = x2;
						xMax = x1;
					}
					if (y1 < y2)
					{
						yMin = y1;
						yMax = y2;
					}
					else
					{
						yMin = y2;
						yMax = y1;
					}

					if (type == StatementType.ROUNDEDBOX)
					{
						double yRange = yMax - yMin;
						double xRange = xMax - xMin;

						if (nExpressions == 5)
						{
							radius = m_executeArgs[4].getNumericValue();
							if (radius > xRange / 2)
								radius = xRange / 2;
							if (radius > yRange / 2)
								radius = yRange / 2;
						}
						else
						{
							radius = Math.min(xRange / 10, yRange / 10);
						}

						/*
						 * If there is no radius then draw a plain box without
						 * rounded corners instead.
						 */
						if (radius <= 0)
							type = StatementType.BOX;
					}
					else if (type == StatementType.BOX3D)
					{
						depth = Math.min(xMax - xMin, yMax - yMin);
						if (nExpressions == 5)
						{
							depth = m_executeArgs[4].getNumericValue();
						}
					}
					else if (type == StatementType.CHESSBOARD)
					{
						if (nExpressions == 5)
						{
							tileSize = m_executeArgs[4].getNumericValue();
						}
						if (tileSize <= 0)
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SIZE) +
								": " + tileSize);
					}

					if (type == StatementType.BOX || type == StatementType.BOX3D)
					{
						context.moveTo(xMin, yMin);
						context.lineTo(xMin, yMax);
						context.lineTo(xMax, yMax);
						context.lineTo(xMax, yMin);
						context.closePath();

						if (type == StatementType.BOX3D)
						{

							double cos30 = Math.cos(Math.toRadians(30));
							double sin30 = Math.sin(Math.toRadians(30));

							/*
							 * Draw slanted right side of 3D box.
							 */
							x1 = xMax + depth * cos30;
							y1 = yMax + depth * sin30;
							context.moveTo(xMax, yMax);
							context.lineTo(x1, y1);
							context.lineTo(x1, y1 - (yMax - yMin));
							context.lineTo(xMax, yMin);
							context.closePath();

							/*
							 * Draw slanted top side of 3D box.
							 */
							x1 = xMin + depth * cos30;
							y1 = yMax + depth * sin30;
							context.moveTo(xMin, yMax);
							context.lineTo(x1, y1);
							context.lineTo(x1 + (xMax - xMin), y1);
							context.lineTo(xMax, yMax);
							context.closePath();
						}
					}
					else if (type == StatementType.ROUNDEDBOX)
					{
						context.moveTo(xMin, yMax - radius);
						context.arcTo(1, xMin + radius, yMax - radius, xMin + radius, yMax);
						context.lineTo(xMax - radius, yMax);
						context.arcTo(1, xMax - radius, yMax - radius, xMax, yMax - radius);
						context.lineTo(xMax, yMin + radius);
						context.arcTo(1, xMax - radius, yMin + radius, xMax - radius, yMin);
						context.lineTo(xMin + radius, yMin);
						context.arcTo(1, xMin + radius, yMin + radius, xMin, yMin + radius);
						context.closePath();
					}
					else if (type == StatementType.CHESSBOARD)
					{
						int i = 0, j;
						y1 = yMin;
						while (y1 < yMax)
						{
							y2 = y1 + tileSize;
							if (y2 > yMax)
								y2 = yMax;

							j = 0;
							x1 = xMin;
							while (x1 < xMax)
							{
								/*
							 	 * Only draw half the squares,
								 * like on a chessboard.
							 	 */
								if ((i + j) % 2 == 0)
								{
									x2 = x1 + tileSize;
									if (x2 > xMax)
										x2 = xMax;
									context.moveTo(x1, y1);
									context.lineTo(x1, y2);
									context.lineTo(x2, y2);
									context.lineTo(x2, y1);
									context.closePath();
								}

								j++;
								x1 = xMin + j * tileSize;
							}
							i++;
							y1 = yMin + i * tileSize;
						}
					}
					else if (type == StatementType.GUILLOTINE)
					{
						context.guillotine(xMin, yMin, xMax, yMax);
					}
					else if (type == StatementType.PROTECT)
					{
						context.protect(xMin, yMin, xMax, yMax);
					}
					else /* UNPROTECT */
					{
						context.unprotect(xMin, yMin, xMax, yMax);
					}
				}
				else if (nExpressions == 1 &&
					(type == StatementType.PROTECT || type == StatementType.UNPROTECT))
				{
					Argument geometry = m_executeArgs[0];
					if (type == StatementType.PROTECT)
					{
						context.protect(geometry);
					}
					else
					{
						context.unprotect(geometry);
					}
				}
				else if (nExpressions == 0 && type == StatementType.PROTECT)
				{
					context.protect();
				}
				else if (nExpressions == 0 && type == StatementType.UNPROTECT)
				{
					context.unprotect();
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_BOX));
				}
				break;

			case ADDPATH:
				for (int i = 0; i < nExpressions; i++)
				{
					double coords[] = m_executeArgs[i].getGeometryValue();
					addGeometryToPath(context, coords, 0);
				}
				break;
				
			case CLEARPATH:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.clearPath();
				break;

			case CLOSEPATH:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.closePath();
				break;

			case SAMPLEPATH:
				if (nExpressions == 2)
				{
					context.samplePath(m_executeArgs[0].getNumericValue(), m_executeArgs[1].getNumericValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PATH_SAMPLE));
				}
				break;
				
			case STRIPEPATH:
				if (nExpressions == 2)
				{
					degrees = m_executeArgs[1].getNumericValue();
					context.stripePath(m_executeArgs[0].getNumericValue(), Math.toRadians(degrees));
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PATH_STRIPE));
				}
				break;

			case SHIFTPATH:
				if (nExpressions == 2)
				{
					context.translatePath(m_executeArgs[0].getNumericValue(),
						m_executeArgs[1].getNumericValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PATH_SHIFT));
				}
				break;

			case PARALLELPATH:
				if (nExpressions > 0)
				{
					double []distances = new double[nExpressions];
					for (int i = 0; i < nExpressions; i++)
						distances[i] = m_executeArgs[i].getNumericValue();
					context.parallelPath(distances);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DISTANCE));
				}
				break;

			case SELECTPATH:
				if (nExpressions > 0 && nExpressions % 2 == 0)
				{
					double []offsets = new double[nExpressions / 2];
					double []lengths = new double[nExpressions / 2];
					for (int i = 0; i < nExpressions / 2; i++)
					{
						offsets[i] = m_executeArgs[i * 2].getNumericValue();
						lengths[i] = m_executeArgs[i * 2 + 1].getNumericValue();
					}
					context.selectPath(offsets, lengths);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				break;

			case REVERSEPATH:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.reversePath();
				break;

			case SINKHOLE:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.createSinkhole();
				break;

			case STROKE:
				if (nExpressions > 0)
				{
					StringBuilder sb = new StringBuilder(m_executeArgs[0].getStringValue());
					for (int i = 1; i < nExpressions; i++)
						sb.append(m_executeArgs[i].getStringValue()).append(Constants.LINE_SEPARATOR);
					context.stroke(sb.toString());
				}
				else
				{
					context.stroke(null);
				}
				break;
				
			case FILL:
				if (nExpressions > 0)
				{
					StringBuilder sb = new StringBuilder(m_executeArgs[0].getStringValue());
					for (int i = 1; i < nExpressions; i++)
						sb.append(m_executeArgs[i].getStringValue()).append(Constants.LINE_SEPARATOR);
					context.fill(sb.toString());
				}
				else
				{
					context.fill(null);
				}
				break;

			case GRADIENTFILL:
				if (nExpressions == 4 || nExpressions == 5)
				{
					Color current = context.getColor();
					Color c1 = ColorDatabase.getColor(m_executeArgs[0].toString(), 255, current);
					if (c1 == null)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR) +
							": " + m_executeArgs[0].toString());
					}
					Color c2 = ColorDatabase.getColor(m_executeArgs[1].toString(), 255, current);
					if (c2 == null)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR) +
							": " + m_executeArgs[1].toString());
					}
					Color c3 = ColorDatabase.getColor(m_executeArgs[2].toString(), 255, current);
					if (c3 == null)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR) +
							": " + m_executeArgs[2].toString());
					}
					Color c4 = ColorDatabase.getColor(m_executeArgs[3].toString(), 255, current);
					if (c4 == null)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR) +
							": " + m_executeArgs[3].toString());
					}
					Color c5 = null;
					if (nExpressions == 5)
					{
						c5 = ColorDatabase.getColor(m_executeArgs[4].toString(), 255, current);
						if (c5 == null)
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COLOR) +
								": " + m_executeArgs[4].toString());
					}
					context.gradientFill(c1, c2, c3, c4, c5);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_GRADIENT_FILL));
				}
				break;

			case EVENTSCRIPT:
				if (nExpressions >= 1)
				{
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < nExpressions; i++)
					{
							if (i > 0)
								sb.append(Constants.LINE_SEPARATOR);
							sb.append(m_executeArgs[i].toString());
					}
					context.setEventScript(sb.toString());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SCRIPT));
				}
				break;

			case CLIP:
				if (nExpressions != 1)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_CLIP_SIDE));
				}
				String side = m_executeArgs[0].toString();
				if (side.startsWith("in") || side.startsWith("IN"))
					context.clipInside();
				else
					context.clipOutside();
				break;	

			case SETOUTPUT:
				if (nExpressions == 1)
				{
					try
					{
						PrintStream stdout;
						String filename = m_executeArgs[0].getStringValue();
						if (filename.equals("-"))
						{
							/*
							 * Return output to original standard output.
							 */
							stdout = m_stdoutStream;
						}
						else
						{
							if (!context.getThrottle().isIOAllowed())
							{
								throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_IO) +
									": " + filename);
							}
							
							if (filename.startsWith("|"))
							{
								/*
								 * Pipe standard output through an external command.
								 */
								String pipeCommand = filename.substring(1).trim();
								String []cmdArray;
								if (Constants.getOSName().indexOf("WIN") >= 0)
									cmdArray = new String[]{pipeCommand};
								else
									cmdArray = new String[]{"sh", "-c", pipeCommand};
								Process p = Runtime.getRuntime().exec(cmdArray);
								OutputStream stream = p.getOutputStream();
								stdout = new PrintStream(stream);
							}
							else
							{
								OutputStream stream = new FileOutputStream(filename);
								stdout = new PrintStream(stream);
							}
						}
						context.setStdout(stdout);
					}
					catch (SecurityException e)
					{
						throw new IOException(e.getClass().getName() + ": " + e.getMessage());
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SETOUTPUT));
				}
				break;

			case LABEL:
			case PRINT:
			case FLOWLABEL:
				String label = "";
				int nChars = 0;
				int labelIndex;
				double offset = 0.0;
				double spacing = 0.0;
				boolean rotateInvertedLabels = true;

				if (type == StatementType.FLOWLABEL)
				{
					if (nExpressions < 2)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PATH_OFFSET));
					}
					spacing = m_executeArgs[0].getNumericValue();
					offset = m_executeArgs[1].getNumericValue();

					labelIndex = 2;
					if (nExpressions > 2)
					{
						extras = m_executeArgs[2].getStringValue();
						int index = extras.indexOf("rotate=");
						if (index >= 0)
						{
							rotateInvertedLabels = extras.substring(index + 7).equalsIgnoreCase("true");
							labelIndex = 3;
						}
					}
				}
				else
				{
					labelIndex = 0;
				}

				/*
				 * Label/print a single argument, or several separated by spaces.
				 */
				if (labelIndex >= nExpressions)
				{
					/*
					 * Nothing to label.
					 */
				}
				else if (nExpressions == labelIndex + 1)
				{
					label = m_executeArgs[labelIndex].toString();
					nChars += label.length();
				}
				else
				{
					StringBuilder sb = new StringBuilder();
					for (int i = labelIndex; i < nExpressions; i++)
					{
						if (i > labelIndex)
							sb.append(' ');

						String nextLine = m_executeArgs[i].toString();
						sb.append(nextLine);
						nChars += nextLine.length();
					}
					label = sb.toString();
				}

				if (type == StatementType.PRINT)
				{
					PrintStream p = context.getStdout();
					p.println(label);
				}
				else if (nChars > 0)
				{
					if (type == StatementType.FLOWLABEL)
						context.flowLabel(spacing, offset, rotateInvertedLabels, label);
					else
						context.label(label);
				}
				break;

			case TABLE:
			case TREE:
				if ((type == StatementType.TREE && nExpressions == 2) ||
					(type == StatementType.TABLE && nExpressions >= 2))
				{
					extras = m_executeArgs[0].getStringValue();
					ArrayList<Argument> columns = new ArrayList<Argument>(nExpressions - 1);
					for (int i = 1; i < nExpressions; i++)
					{
						Argument arg;
						if (m_executeArgs[i].getType() == Argument.HASHMAP)
						{
							arg = m_executeArgs[i];
						}
						else
						{
							arg = new Argument();
							arg.addHashMapEntry("1", m_executeArgs[i]);
						}
						columns.add(arg);
					}
					if (type == StatementType.TABLE)
						context.drawTable(extras, columns);
					else
						context.drawTree(extras, (Argument)columns.get(0));
				}
				else
				{
					if (type == StatementType.TABLE)
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_TABLE));
					else
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_TREE));
				}
				break;

			case ICON:
				if (nExpressions == 1 || nExpressions == 2)
				{
					double size;
					if (nExpressions == 2)
						size = m_executeArgs[1].getNumericValue();
					else
						size = 0;

					context.drawIcon(m_executeArgs[0].getStringValue(), size);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_ICON));
				}
				break;

			case GEOIMAGE:
				if (nExpressions == 1 || nExpressions == 2)
				{
					String filename = m_executeArgs[0].getStringValue();
					if (nExpressions == 2)
						extras = m_executeArgs[1].getStringValue();
					else
						extras = "";
					context.drawGeoImage(filename, extras, m_throttle);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_GEOIMAGE));
				}
				break;

			case EPS:
				if (nExpressions == 1 || nExpressions == 2)
				{
					double size;
					if (nExpressions == 2)
					{
						size = m_executeArgs[1].getNumericValue();
					}
					else
					{
						size = 0;
					}

					context.drawEPS(m_executeArgs[0].getStringValue(), size);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_EPS));
				}
				break;
				
			case SVG:
				if (nExpressions == 1 || nExpressions == 2)
				{
					double size;
					if (nExpressions == 2)
					{
						size = m_executeArgs[1].getNumericValue();
					}
					else
					{
						size = 0;
					}

					context.drawSVG(m_executeArgs[0].getStringValue(), size);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SVG));
				}
				break;

			case SVGCODE:
				for (int i = 0; i < nExpressions; i++)
				{
					context.addSVGCode(m_executeArgs[i].getStringValue());
				}
				break;

			case PDF:
				if (nExpressions == 2 || nExpressions == 3)
				{
					double size;
					if (nExpressions == 3)
					{
						size = m_executeArgs[2].getNumericValue();
					}
					else
					{
						size = 0;
					}
					long pageNumber = Math.round(m_executeArgs[1].getNumericValue());

					context.drawPDF(m_executeArgs[0].getStringValue(),
						(int)pageNumber, size);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PDF));
				}
				break;

			case PDFGROUP:
				if (nExpressions > 0)
				{
					String groupAction = m_executeArgs[0].getStringValue();
					if (groupAction.equalsIgnoreCase("begin"))
					{
						if (nExpressions == 2)
						{
							String groupName = m_executeArgs[1].getStringValue();
							context.beginPDFGroup(groupName);
						}
						else
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PDF_GROUP));
						}
					}
					else if (groupAction.equalsIgnoreCase("end") && nExpressions == 1)
					{
						context.endPDFGroup();
					}
					else
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PDF_GROUP));
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PDF_GROUP));
				}
				break;
				
			case SCALE:
				if (nExpressions == 1)
				{
					double s = m_executeArgs[0].getNumericValue();
					context.setScaling(s);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SCALING));
				}
				break;

			case ROTATE:
				if (nExpressions == 1)
				{
					degrees = m_executeArgs[0].getNumericValue();
					context.setRotation(Math.toRadians(degrees));
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_ROTATION));
				}
				break;

			case WORLDS:

				if (nExpressions == 1 || nExpressions == 2)
				{
					/*
					 * Parse BBOX from string in WMS format: 10.2,48.2,11,49
					 */
					px1 = py1 = px2 = py2 = 0;
					StringTokenizer st2 = new StringTokenizer(m_executeArgs[0].getStringValue(), ",");
					if (st2.countTokens() == 4)
					{
						String token = "";
						try
						{
							token = st2.nextToken();
							x1 = Double.parseDouble(token);
							token = st2.nextToken();
							y1 = Double.parseDouble(token);
							token = st2.nextToken();
							x2 = Double.parseDouble(token);
							token = st2.nextToken();
							y2 = Double.parseDouble(token);
						}
						catch (NumberFormatException e)
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLDS) +
								": " + token);
						}
					}
					else
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLDS));
					}
				}
				else
				{
					if (nExpressions == 4 || nExpressions == 5)
					{
						/*
						 * Add world coordinates over whole page.
						 */
						px1 = py1 = px2 = py2 = 0;
					}
					else if (nExpressions == 8 || nExpressions == 9)
					{
						/*
						 * Set world coordinates over part of the page.
						 */
						px1 = m_executeArgs[4].getNumericValue();
						py1 = m_executeArgs[5].getNumericValue();
						px2 = m_executeArgs[6].getNumericValue();
						py2 = m_executeArgs[7].getNumericValue();
					}
					else
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLDS));
					}

					x1 = m_executeArgs[0].getNumericValue();
					y1 = m_executeArgs[1].getNumericValue();
					x2 = m_executeArgs[2].getNumericValue();
					y2 = m_executeArgs[3].getNumericValue();
				}

				units = Context.WORLD_UNITS_METRES;
				allowDistortion = false;

				if (nExpressions == 2)
					extras = m_executeArgs[1].getStringValue();
				else if (nExpressions == 5)
					extras = m_executeArgs[4].getStringValue();
				else if (nExpressions == 9)
					extras = m_executeArgs[8].getStringValue();
				else
					extras = "";

				/*
				 * Parse additional options.
				 */
				StringTokenizer st2 = new StringTokenizer(extras);
				while (st2.hasMoreTokens())
				{
					String token = st2.nextToken();
					if (token.startsWith("units="))
					{
						String s = token.substring(6);
						Integer u = m_worldUnitsLookup.get(s);
						if (u == null)
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLD_UNITS) +
								": " + s);
						}
						units = u.intValue();
					}
					else if (token.startsWith("distortion="))
					{
						String flag = token.substring(11);
						allowDistortion = flag.equalsIgnoreCase("true");
					}
				}

				context.setWorlds(x1, y1, x2, y2, px1, py1, px2, py2,
					units, allowDistortion);
				break;

			case DATASET:
				if (nExpressions == 2 || nExpressions == 3)
				{
					extras = "";
					if (nExpressions > 2)
						extras = m_executeArgs[2].getStringValue();
					String name = m_executeArgs[1].getStringValue();
					context.setDataset(m_executeArgs[0].getStringValue(), name, extras, m_stdinStream);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DATASET));
				}
				break;

			case FETCH:
				/*
				 * Fetch next row from dataset.
				 */
				Row row = context.fetchRow();

				String []fieldNames = context.getDatasetFieldNames();
				String fieldName;

				for (int i = 0; i < row.size(); i++)
				{
						/*
						 * Define all fields as variables.
						 */
						if (fieldNames != null)
							fieldName = fieldNames[i];
						else
							fieldName = DefaultFieldNames.get(i);
						context.defineVariable(fieldName, (Argument)(row.get(i)));
				}
				break;

			case NEWPAGE:
				if (nExpressions >= 3 && nExpressions <= 5)
				{
					String format = m_executeArgs[0].getStringValue();
					String filename = m_executeArgs[1].getStringValue();
					double width, height;
					int extrasIndex;

					if (nExpressions == 3)
					{
						String paperName = m_executeArgs[2].getStringValue();
						PageSize p = new PageSize(paperName);
						width = p.getDimension().getX();
						height = p.getDimension().getY();
						extrasIndex = 3;
					}
					else if (m_executeArgs[2].getType() == Argument.STRING)
					{
						try
						{
							String paperName = m_executeArgs[2].getStringValue();
							PageSize p = new PageSize(paperName);
							width = p.getDimension().getX();
							height = p.getDimension().getY();
							extrasIndex = 3;
						}
						catch (MapyrusException e)
						{
							width = parseDimension(m_executeArgs[2]);
							height = parseDimension(m_executeArgs[3]);
							extrasIndex = 4;
						}
					}
					else
					{
						width = parseDimension(m_executeArgs[2]);
						height = parseDimension(m_executeArgs[3]);
						extrasIndex = 4;
					}

					if (extrasIndex < nExpressions)
						extras = m_executeArgs[extrasIndex].getStringValue();
					else
						extras = "";

					if (width <= 1)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PAGE_SIZE) +
							": " + width);
					}
					if (height <= 1)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PAGE_SIZE) +
							": " + height);
					}

					context.setOutputFormat(format, filename, width, height,
						extras, context.getStdout(), m_throttle);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PAGE));
				}
				break;	

			case ENDPAGE:
				context.closeOutputFormat();
				break;

			case LOCAL:
				break;

			case EVAL:
				if (nExpressions == 1)
				{
					/*
					 * Run evaluated argument as a command.  This allows
					 * commands to be built and executed on-the-fly.
					 */
					String command = m_executeArgs[0].getStringValue();
					StringReader stringReader = new StringReader(command);
					String filename = st.getFilename();
					FileOrURL f = new FileOrURL(stringReader, filename);
					byte []emptyBuffer = new byte[0];
					interpret(context, f, new ByteArrayInputStream(emptyBuffer),
						context.getStdout());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_EVAL));
				}
				break;

			case LET:
				/*
				 * Nothing to do -- any variables were assigned during expression
				 * evaluation above.
				 */
				break;

			case KEY:
				if (nExpressions >= 2)
				{
					String entryType = m_executeArgs[0].getStringValue();
					String description = m_executeArgs[1].getStringValue();
					int eType = LegendEntry.parseTypeString(entryType);
					if (eType < 0)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LEGEND_TYPE) +
							": " + entryType);
					}
					m_context.addLegendEntry(description, eType, m_executeArgs, 2, nExpressions - 2);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LEGEND_ENTRY));
				}
				break;

			case LEGEND:
				if (nExpressions == 1)
				{
					legendSize = m_executeArgs[0].getNumericValue();
					displayLegend(st, context, legendSize);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_LEGEND_SIZE));
				}
				break;

			case MIMETYPE:
				if (nExpressions == 1)
				{
					String mimeType = m_executeArgs[0].getStringValue();
					String response = HTTPRequest.HTTP_OK_KEYWORD + Constants.LINE_SEPARATOR +
						HTTPRequest.CONTENT_TYPE_KEYWORD + ": " + mimeType +
						Constants.LINE_SEPARATOR;
					context.setHTTPReponse(response);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_MIME_TYPE));
				}
				break;
				
			case HTTPRESPONSE:
				StringBuilder sb = new StringBuilder(128);
				for (int i = 0; i < nExpressions; i++)
				{
					sb.append(m_executeArgs[i].getStringValue());
					sb.append(Constants.LINE_SEPARATOR);
				}
				context.setHTTPReponse(sb.toString());
				break;
		}		
	}

	/**
	 * Parse a size, with or without units
	 * @param dim size which may be a number in millimeters or a string like "15px".
	 * @return parse size in millimeters.
	 */
	private double parseDimension(Argument dim) throws MapyrusException
	{
		double retval;
		double scaling = 1;

		if (dim.getType() != Argument.NUMERIC)
		{
			/*
			 * Check if value ends with units.  For example, "100px".
			 */
			String s = dim.getStringValue();
			int sLength = s.length();
			if (sLength >= 2)
			{
				String suffix = s.substring(sLength - 2, sLength);
				if (suffix.equals("px"))
				{
					scaling = Constants.MM_PER_INCH / Constants.getScreenResolution();
					dim = new Argument(Argument.STRING, s.substring(0, sLength - 2));
				}
				else if (suffix.equals("pt"))
				{
					scaling = Constants.MM_PER_INCH / Constants.POINTS_PER_INCH;
					dim = new Argument(Argument.STRING, s.substring(0, sLength - 2));
				}
				else if (suffix.equals("mm"))
				{
					scaling = 1;
					dim = new Argument(Argument.STRING, s.substring(0, sLength - 2));
				}
				else if (suffix.equals("cm"))
				{
					scaling = 10;
					dim = new Argument(Argument.STRING, s.substring(0, sLength - 2));
				}
				else if (suffix.equals("in"))
				{
					scaling = Constants.MM_PER_INCH;
					dim = new Argument(Argument.STRING, s.substring(0, sLength - 2));
				}
			}
		}
		retval = dim.getNumericValue();
		retval *= scaling;
		return retval;
	}

	/**
	 * Parse a statement name or variable name.
	 * @param c is first character of name.
	 * @param preprocessor is source to continue reading from.
	 * @return word parsed from preprocessor.
	 */
	private String parseWord(int c, Preprocessor preprocessor)
		throws IOException, MapyrusException
	{
		StringBuilder word = new StringBuilder();

		/*
		 * A statement or procedure name begins with a keyword
		 * which must begin with a letter.
		 */
		if (!(Character.isLetter((char)c) || c == '$'))
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.INVALID_KEYWORD));
		}
		
		/*
		 * Read in whole word.
		 */
		do
		{
			word.append((char)c);
			c = preprocessor.read();
		}
		while (Character.isLetterOrDigit((char)c) || c == '.' || c == '_' || c == ':');

		/*
		 * Put back the character we read that is not part of the word.	
		 */	
		preprocessor.unread(c);
		return(word.toString());
	}

	/**
	 * Reads, parses and returns next statement.
	 * @param preprocessor is source to read statement from.
	 * @param keyword is first token that has already been read.
	 * @return next statement read from file, or null if EOF was reached
	 * before a statement could be read.
	 */
	private Statement parseSimpleStatement(String keyword, Preprocessor preprocessor)
		throws MapyrusException, IOException
	{
		int state;
		ArrayList<Expression> expressions = new ArrayList<Expression>();
		Expression expr;
		Statement retval = null;
		boolean finishedStatement = false;
		int c;

		state = AT_ARGUMENT;
		c = preprocessor.readNonSpace();

		/*
		 * Keep parsing statement until we get to the end of the
		 * line, semi-colon statement separator or end of file.
		 */
		while (!finishedStatement)
		{
			if (c == -1 || c == '\n' || c == ';')
			{
				finishedStatement = true;
			}
			else if (Character.isWhitespace((char)c))
			{
				/*
				 * Ignore any whitespace.
				 */
				c = preprocessor.readNonSpace();
			}
			else if (state == AT_SEPARATOR)
			{
				/*
				 * Expect a ',' between arguments to a
				 * statement or procedure block.
				 */
				if (c != ARGUMENT_SEPARATOR)
				{
					throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
						": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
						": '" + ARGUMENT_SEPARATOR + "'");
				}
				c = preprocessor.readNonSpace();
				state = AT_ARGUMENT;
			}
			else
			{
				/*
				 * Parse an expression.
				 */
				preprocessor.unread(c);
				expr = new Expression(preprocessor, m_userFunctions);
				expressions.add(expr);

				c = preprocessor.readNonSpace();
				state = AT_SEPARATOR;
			}
		}

		/*
		 * Build a statement structure for what we just parsed.
		 */
		if (c == -1 && expressions.size() == 0)
		{
			/*
			 * Could not parse anything before we got EOF.
			 */
			retval = null;
		}
		else
		{
			Expression []a = new Expression[expressions.size()];

			for (int i = 0; i < a.length; i++)
			{
				a[i] = (Expression)expressions.get(i);
			}
			retval = new Statement(keyword, a);

			retval.setFilenameAndLineNumber(preprocessor.getCurrentFilename(),
					preprocessor.getCurrentLineNumber());
		}
		return(retval);
	}

	/**
	 * Parse paramters in a procedure block definition.
	 * Reads comma separated list of parameters
	 * @param preprocessor is source to read from.
	 * @return list of parameter names.
	 */
	private ArrayList<String> parseParameters(Preprocessor preprocessor)
		throws IOException, MapyrusException
	{
		int c;
		ArrayList<String> parameters = new ArrayList<String>();
		int state;

		/*
		 * Read parameter names separated by ',' characters.
		 */
		state = AT_PARAM;
		c = preprocessor.readNonSpace();
		while (c != -1 && c != '\n' && c != ';')
		{
			if (Character.isWhitespace((char)c))
			{
				/*
				 * Ignore whitespace.
				 */
				c = preprocessor.readNonSpace();
			}
			else if (state == AT_PARAM)
			{
				/*
				 * Expect a parameter name.
				 */
				parameters.add(parseWord(c, preprocessor));
				state = AT_PARAM_SEPARATOR;
				c = preprocessor.readNonSpace();
			}
			else
			{
				/*
				 * Expect a ',' between parameter names.
				 */
				if (c != PARAM_SEPARATOR)
				{
					throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
						": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
						": '" + PARAM_SEPARATOR + "'");
				}
				state = AT_PARAM;
				c = preprocessor.readNonSpace();
			}
		}
		return(parameters);
	}

	/**
	 * Reads and parses several statements
	 * grouped together between "begin"/"function" and "end" keywords.
	 * @param preprocessor is source to read from.
	 * @param isFunction true if function is being parsed.
	 * @return parsed procedure block as single statement.
	 */
	private ParsedStatement parseProcedureBlock(Preprocessor preprocessor,
		boolean isFunction) throws IOException, MapyrusException
	{
		String blockName;
		ArrayList<String> parameters;
		ArrayList<Statement> procedureStatements = new ArrayList<Statement>();
		ParsedStatement st;
		Statement retval;
		boolean parsedEndKeyword = false;
		UserFunction function = null;
		int c;

		/*
		 * Skip whitespace between "begin" and block name.
		 */		
		c = preprocessor.readNonSpace();
		while (Character.isWhitespace((char)c))
			c = preprocessor.readNonSpace();

		blockName = parseWord(c, preprocessor);
		parameters = parseParameters(preprocessor);

		/*
		 * Define function now so that it can be called recursively
		 * inside the function definition.
		 */
		if (isFunction)
		{
			function = new UserFunction(blockName, parameters, null, this);
			m_userFunctions.put(blockName, function);
		}

		/*
		 * Keep reading statements until we get matching "end"
		 * keyword.
		 */
		do
		{
			st = parseStatementOrKeyword(preprocessor, true);
			if (st == null)
			{
				/*
				 * Should not reach end of file inside a procedure block.
				 */
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
			}

			if (st.isStatement())
			{
				/*
				 * Accumulate statements for this procedure block.
				 */
				procedureStatements.add(st.getStatement());
			}
			else if (st.getKeywordType() == ParsedStatement.PARSED_END)
			{
				/*
				 * Found matching "end" keyword for this procedure block.
				 */
				parsedEndKeyword = true;
			}
			else
			{
				/*
				 * Found some other sort of control-flow keyword
				 * that we did not expect.
				 */
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
					": " + END_KEYWORD);
			}
		}
		while(!parsedEndKeyword);

		if (isFunction)
			function.setStatements(procedureStatements);

		/*
		 * Return procedure block as a single statement.
		 */
		retval = new Statement(blockName, parameters, procedureStatements);
		return(new ParsedStatement(retval));
	}

	/**
	 * Reads and parses repeat or while loop statement.
	 * Parses test expression, "do" keyword, some
	 * statements, and then "done" keyword.
	 * @param preprocessor is source to read from.
	 * @param isWhileLoop true if parsing a while loop, false for a repeat loop.
	 * @param inProcedureDefn true if already parsing inside a procedure defn.
	 * @return parsed loop as single statement.
	 */
	private ParsedStatement parseLoopStatement(Preprocessor preprocessor,
		boolean isWhileLoop, boolean inProcedureDefn)
		throws IOException, MapyrusException
	{
		ParsedStatement st;
		Expression test;
		ArrayList<Statement> loopStatements = new ArrayList<Statement>();
		Statement statement;
		String currentFilename = preprocessor.getCurrentFilename();
		int currentLineNumber = preprocessor.getCurrentLineNumber();
		
		test = new Expression(preprocessor, m_userFunctions);
		
		/*
		 * Expect to parse "do" keyword.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside while loop.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		else if (st.isStatement() ||
			st.getKeywordType() != ParsedStatement.PARSED_DO)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + DO_KEYWORD);
		}
		
		/*
		 * Now we want some statements to execute each time through the loop.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside loop.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		while (st.isStatement())
		{
			loopStatements.add(st.getStatement());
			st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
			if (st == null)
			{
				/*
			 	* Should not reach end of file inside loop
			 	*/
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
			}
		}
		
		/*
		 * Expect "done" after statements.
		 */
		if (st.getKeywordType() != ParsedStatement.PARSED_DONE)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + DONE_KEYWORD);
		}

		statement = new Statement(test, loopStatements, isWhileLoop);
		statement.setFilenameAndLineNumber(currentFilename, currentLineNumber);
		return(new ParsedStatement(statement));		
	}

	/**
	 * Reads and parses "for" loop statement.
	 * Parses variable name, "in" keyword, arrayname, "do" keyword,
	 * some statements, and then "done" keyword.
	 * @param preprocessor is source to read from.
	 * @return parsed loop as single statement.
	 */
	private ParsedStatement parseForStatement(Preprocessor preprocessor,
		boolean inProcedureDefn)
		throws IOException, MapyrusException
	{
		ParsedStatement st;
		Expression var, arrayExpr;
		ArrayList<Statement> loopStatements = new ArrayList<Statement>();
		Statement statement;
		String currentFilename = preprocessor.getCurrentFilename();
		int currentLineNumber = preprocessor.getCurrentLineNumber();

		var = new Expression(preprocessor, m_userFunctions);

		/*
		 * Expect to parse "in" keyword.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside for loop.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		else if (st.isStatement() ||
			st.getKeywordType() != ParsedStatement.PARSED_IN)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + IN_KEYWORD);
		}

		arrayExpr = new Expression(preprocessor, m_userFunctions);

		/*
		 * Expect to parse "do" keyword.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside while loop.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		else if (st.isStatement() ||
			st.getKeywordType() != ParsedStatement.PARSED_DO)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + DO_KEYWORD);
		}
		
		/*
		 * Now we want some statements to execute each time through the loop.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside loop.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		while (st.isStatement())
		{
			loopStatements.add(st.getStatement());
			st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
			if (st == null)
			{
				/*
				* Should not reach end of file inside loop
				*/
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
			}
		}
		
		/*
		 * Expect "done" after statements.
		 */
		if (st.getKeywordType() != ParsedStatement.PARSED_DONE)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + DONE_KEYWORD);
		}

		statement = new Statement(var, arrayExpr, loopStatements);
		statement.setFilenameAndLineNumber(currentFilename, currentLineNumber);
		return(new ParsedStatement(statement));		
	}
	
	/**
	 * Reads and parses conditional statement.
	 * Parses test expression, "then" keyword, some
	 * statements, an "else" keyword, some statements and
	 * "endif" keyword.
	 * @param preprocessor is source to read from.
	 * @return parsed if block as single statement.
	 */
	private ParsedStatement parseIfStatement(Preprocessor preprocessor,
		boolean inProcedureDefn)
		throws IOException, MapyrusException
	{
		ParsedStatement st;
		String currentFilename = preprocessor.getCurrentFilename();
		int currentLineNumber = preprocessor.getCurrentLineNumber();
		Expression test;
		ArrayList<Statement> thenStatements = new ArrayList<Statement>();
		ArrayList<Statement> elseStatements = new ArrayList<Statement>();
		Statement statement;
		boolean checkForEndif = true;	/* do we need to check for "endif" keyword at end of statement? */

		test = new Expression(preprocessor, m_userFunctions);

		/*
		 * Expect to parse "then" keyword.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside if statement.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		else if (st.isStatement() ||
			st.getKeywordType() != ParsedStatement.PARSED_THEN)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + THEN_KEYWORD);
		}

		/*
		 * Now we want some statements for when the expression is true.
		 */
		st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
		if (st == null)
		{
			/*
			 * Should not reach end of file inside if statement.
			 */
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
		}
		while (st.isStatement())
		{
			thenStatements.add(st.getStatement());
			st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
			if (st == null)
			{
				/*
			 	* Should not reach end of file inside if statement.
			 	*/
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
			}
		}

		/*
		 * There may be an "else" part to the statement too.
		 */
		if (st.getKeywordType() == ParsedStatement.PARSED_ELSE)
		{
			/*
			 * Now get the statements for when the expression is false.
			 */
			st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
			if (st == null)
			{
				/*
			 	* Should not reach end of file inside if statement.
			 	*/
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
			}
			while (st.isStatement())
			{
				elseStatements.add(st.getStatement());
				st = parseStatementOrKeyword(preprocessor, inProcedureDefn);
				if (st == null)
				{
					/*
			 		 * Should not reach end of file inside if statement.
			 		 */
					throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
						": " + MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF));
				}
			}
		}
		else if (st.getKeywordType() == ParsedStatement.PARSED_ELSIF)
		{
			/*
			 * Parse "elsif" block as a single, separate "if" statement
			 * that is part of the "else" case.
			 */
			st = parseIfStatement(preprocessor, inProcedureDefn);
			if (!st.isStatement())
			{
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
					": " + ENDIF_KEYWORD);
			}
			elseStatements.add(st.getStatement());
			checkForEndif = false;
		}

		/*
		 * Expect "endif" after statements.
		 */
		if (checkForEndif && st.getKeywordType() != ParsedStatement.PARSED_ENDIF)
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
				": " + ENDIF_KEYWORD);
		}

		statement = new Statement(test, thenStatements, elseStatements);
		statement.setFilenameAndLineNumber(currentFilename, currentLineNumber);
		return(new ParsedStatement(statement));
	}

	/*
	 * Static keyword lookup table for fast keyword lookup.
	 */
	private static HashMap<String, ParsedStatement> m_keywordLookup;

	static
	{
		m_keywordLookup = new HashMap<String, ParsedStatement>();
		m_keywordLookup.put(END_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_END));
		m_keywordLookup.put(THEN_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_THEN));
		m_keywordLookup.put(ELSE_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_ELSE));
		m_keywordLookup.put(ELIF_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_ELSIF));
		m_keywordLookup.put(ENDIF_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_ENDIF));
		m_keywordLookup.put(DO_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_DO));
		m_keywordLookup.put(DONE_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_DONE));
		m_keywordLookup.put(IN_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_IN));
	}

	/**
	 * Reads, parses and returns next statement, or block of statements.
	 * @param preprocessor source to read from.
	 * @param inProcedureDefn true if currently parsing inside an
	 * procedure block.
	 * @return next statement read from file, or null if EOF was reached
	 * before a statement could be read.
	 */
	private ParsedStatement parseStatementOrKeyword(Preprocessor preprocessor,
		boolean inProcedureDefn)
		throws MapyrusException, IOException
	{
		int c;
		ParsedStatement retval = null;
		boolean finishedStatement = false;

		c = preprocessor.readNonSpace();
		finishedStatement = false;
		while (!finishedStatement)
		{
			if (c == -1)
			{
				/*
				 * Reached EOF.
				 */
				finishedStatement = true;
				break;
			}
			else if (Character.isWhitespace((char)c))
			{
				/*
				 * Skip whitespace
				 */
				c = preprocessor.readNonSpace();
			}
			else
			{
				String keyword = parseWord(c, preprocessor);
				String lower = keyword.toLowerCase();

				/*
				 * Is this the start or end of a procedure block or function definition?
				 */
				if (lower.equals(BEGIN_KEYWORD) || lower.equals(FUNCTION_KEYWORD))
				{
					/*
					 * Nested procedure blocks and functions not allowed.
					 */
					if (inProcedureDefn)
					{
						throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
							": " + MapyrusMessages.get(MapyrusMessages.NESTED_PROC));
					}
					retval = parseProcedureBlock(preprocessor, lower.equals(FUNCTION_KEYWORD));
				}
				else if (lower.equals(IF_KEYWORD))
				{
					retval = parseIfStatement(preprocessor, inProcedureDefn);
				}
				else if (lower.equals(WHILE_KEYWORD))
				{
					retval = parseLoopStatement(preprocessor, true, inProcedureDefn);
				}
				else if (lower.equals(REPEAT_KEYWORD))
				{
					retval = parseLoopStatement(preprocessor, false, inProcedureDefn);
				}
				else if (lower.equals(FOR_KEYWORD))
				{
					retval = parseForStatement(preprocessor, inProcedureDefn);
				}
				else
				{
					/*
					 * Does keyword match a control-flow keyword?
				 	 * like "then", or "else"?
					 */
					retval = (ParsedStatement)m_keywordLookup.get(lower);
					if (retval == null)
					{
						/*
						 * It must be a regular type of statement if we
						 * can't match any special words.
						 */
						Statement st = parseSimpleStatement(keyword, preprocessor);
						retval = new ParsedStatement(st);
					}
					else
					{
						/*
						 * Parse any semi-colon following the keyword.
						 */
						c = preprocessor.readNonSpace();
						if (c != -1 && c != ';')
							preprocessor.unread(c);
					}
				}
				finishedStatement = true;
			}
		}

		return(retval);
	}

	/**
	 * Reads and parses a single statement.
	 * @param preprocessor is source to read statement from.
	 * @return next statement read and parsed.
	 */
	private Statement parseStatement(Preprocessor preprocessor)
		throws IOException, MapyrusException
	{
		ParsedStatement st = parseStatementOrKeyword(preprocessor, false);
		if (st == null)
		{
			return(null);
		}
		else if (!st.isStatement())
		{
			throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
				": " + MapyrusMessages.get(MapyrusMessages.INVALID_KEYWORD));
		}
		return(st.getStatement());
	}

	/**
	 * Reads and parses commands from file and executes them.
	 * @param context is the context to use during interpretation.
	 * @param f is open file or URL to read from.
	 * @param stdin is stream to use for standard input by this intepreter.
	 * @param stdout is stream to use for standard output by this intepreter.
	 * File f is closed by this method when reading is completed.
	 */
	public void interpret(ContextStack context, FileOrURL f,
		InputStream stdin, PrintStream stdout)
		throws IOException, InterruptedException, MapyrusException
	{
		Statement st;
		boolean isIncludeAllowed = m_throttle.isIOAllowed();
		Preprocessor preprocessor = new Preprocessor(f, isIncludeAllowed);
		m_stdinStream = stdin;
		m_stdoutStream = stdout;
		m_context = context;
		context.setStdout(stdout);
		context.setThrottle(m_throttle);

		try
		{
			/*
			 * Keep parsing until we get EOF or reach a 'RETURN' command.
			 */
			while ((st = parseStatement(preprocessor)) != null)
			{
				Argument returnValue = executeStatement(st);
				if (returnValue != null)
					break;
			}
		}
		finally
		{
			/*
			 * Ensure that all files the preprocessor opened are always closed.
			 */
			preprocessor.close();
		}
	}

	private void makeCall(Statement block, ArrayList<String> parameters, Argument []args)
		throws IOException, InterruptedException, MapyrusException
	{
		Statement statement;
		String parameterName;

		for (int i = 0; i < args.length; i++)
		{
			parameterName = parameters.get(i);
			m_context.setLocalScope(parameterName);
			m_context.defineVariable(parameterName, args[i]);
		}

		/*
		 * Execute each of the statements in the procedure block.
		 */
		ArrayList<Statement> v = block.getStatementBlock();
		for (int i = 0; i < v.size(); i++)
		{
			statement = v.get(i);

			/*
			 * Found return statement so stop executing.
			 */
			if (executeStatement(statement) != null)
				break;
		}
	}

	/**
	 * Recursive function for executing single statements and
	 * blocks of statements.
	 * @param statement is statement to execute.
	 * @return type of last statement executed.
	 */
	public Argument executeStatement(Statement statement)
		throws IOException, MapyrusException, InterruptedException
	{
		Argument []args;
		StatementType statementType = statement.getType();
		Argument returnValue = null;

		/*
		 * Store procedure blocks away for later execution,
		 * execute any other statements immediately.
		 */
		if (statementType == StatementType.BLOCK)
		{
			m_statementBlocks.put(statement.getBlockName(), statement);
		}
		else if (statementType == StatementType.RETURN)
		{
			/*
			 * Evaluate any value to return.
			 */
			Expression []expr = statement.getExpressions();
			if (expr.length > 1)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_EXPRESSION));
			}
			if (expr.length == 0)
				returnValue = Argument.emptyString;
			else
				returnValue = expr[0].evaluate(m_context, statement.getFilename());
		}
		else if (statementType == StatementType.CONDITIONAL)
		{
			/*
			 * Execute correct part of if statement depending on value of expression.
			 */
			Expression []expr = statement.getExpressions();
			ArrayList<Statement> v;
			Argument test;
			
			try
			{
				test = expr[0].evaluate(m_context, statement.getFilename());
			}
			catch (MapyrusException e)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + e.getMessage());
			}
			
			if (test.getType() != Argument.NUMERIC)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.INVALID_EXPRESSION));
			}
			
			if (test.getNumericValue() != 0.0)
				v = statement.getThenStatements();
			else
				v = statement.getElseStatements();

			if (v != null)
			{			
				/*
				 * Execute each of the statements.
				 */	
				for (int i = 0; i < v.size(); i++)
				{
					statement = (Statement)v.get(i);
					returnValue = executeStatement(statement);

					/*
					 * Found return statement so stop executing.
					 */
					if (returnValue != null)
						break;
				}
			}
		}
		else if (statementType == StatementType.WHILE_LOOP ||
			statementType == StatementType.REPEAT_LOOP)
		{
			/*
			 * Find expression to test and loop statements to execute.
			 */
			Expression []expr = statement.getExpressions();
			
			ArrayList<Statement> v = statement.getLoopStatements();
			Argument test;
	
			try
			{
				test = expr[0].evaluate(m_context, statement.getFilename());
			}
			catch (MapyrusException e)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + e.getMessage());
			}

			if (test.getType() != Argument.NUMERIC)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.INVALID_EXPRESSION));
			}

			int nIterations = 0;
			if (statementType == StatementType.REPEAT_LOOP)
			{
				/*
				 * Protect against round-off.
				 */
				double d = test.getNumericValue();
				if (NumericalAnalysis.equals(d, (int)d))
					nIterations = (int)Math.round(d);
				else
					nIterations = (int)test.getNumericValue();
			}

			/*
			 * Execute loop while expression remains true (non-zero).
			 */
			int iter = 0;
			StatementType loopStatementType = statementType;
			while
			(
				(returnValue == null) &&
			 	((loopStatementType == StatementType.WHILE_LOOP &&
					test.getNumericValue() != 0.0) ||
			 	(loopStatementType == StatementType.REPEAT_LOOP &&
				 	iter < nIterations))
			)
			{
				/*
				 * Execute each of the statements.
				 */
				for (int i = 0; i < v.size(); i++)
				{
					Statement st = (Statement)v.get(i);
					returnValue = executeStatement(st);
					
					/*
					 * Found return statement so stop executing.
					 */
					if (returnValue != null)
						break;
				}

				/*
				 * Ensure we can break out of an empty loop.
				 */
				if (v.isEmpty())
					m_throttle.sleep();

				if (loopStatementType == StatementType.WHILE_LOOP && (returnValue == null))
				{
					test = expr[0].evaluate(m_context, statement.getFilename());
					if (test.getType() != Argument.NUMERIC)
					{
						throw new MapyrusException(statement.getFilenameAndLineNumber() +
							": " + MapyrusMessages.get(MapyrusMessages.INVALID_EXPRESSION));
					}
				}
				iter++;
			}
		}
		else if (statementType == StatementType.FOR_LOOP)
		{
			/*
			 * Find hashmap to loop through and the variable to assign
			 * each hashmap key into.
			 */
			Expression []varExpr = statement.getExpressions();
			Expression hashMapExpr = statement.getForHashMap();

			ArrayList<Statement> v = statement.getLoopStatements();
			Argument hashMapVar;
			String varName = varExpr[0].getVariableName();

			try
			{
				hashMapVar = hashMapExpr.evaluate(m_context, statement.getFilename());
			}
			catch (MapyrusException e)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + e.getMessage());
			}

			if (varName == null)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.VARIABLE_EXPECTED));
			}
			if (hashMapVar.getType() == Argument.HASHMAP)
			{
				/*
				 * Take a copy of current keys in hashmap so that changes to the hashmap
				 * during the loop have no effect.
				 */
				Object []keys = hashMapVar.getHashMapKeys();
				boolean gotReturn = false;
				for (int i = 0; i < keys.length && !gotReturn; i++)
				{
					String currentKey = (String)keys[i];
					m_context.defineVariable(varName,
						new Argument(Argument.STRING, currentKey));

					/*
					 * Execute each of the statements.
					 */	
					for (int j = 0; j < v.size(); j++)
					{
						Statement st = (Statement)v.get(j);
						returnValue = executeStatement(st);
						
						/*
						 * Found return statement so stop executing.
						 */
						if (returnValue != null)
							break;
					}
				}
			}
		}
		else if (statementType == StatementType.CALL)
		{
			/*
			 * Find the statements for the procedure block we are calling.
			 */
			String blockName = statement.getBlockName();
			Statement block = (Statement)m_statementBlocks.get(blockName);
			if (block == null)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNDEFINED_PROC) +
					": " + blockName);
			}

			/*
			 * Check that correct number of parameters are being passed.
			 */
			ArrayList<String> formalParameters = block.getBlockParameters();
			Expression []actualParameters = statement.getExpressions();
			if (actualParameters.length != formalParameters.size())
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.WRONG_PARAMETERS));
			}

			try
			{
				/*
				 * Save state and set parameters passed to the procedure.
				 */
				args = new Argument[actualParameters.length];
				for (int i = 0; i < args.length; i++)
				{
					args[i] = actualParameters[i].evaluate(m_context, statement.getFilename());
				}
			}
			catch (MapyrusException e)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + e.getMessage());
			}

			/*
			 * If one or more "move" points are defined without
			 * any lines then call the procedure block repeatedly
			 * with the origin transformed to each of move points
			 * in turn.
			 */
			int moveToCount = m_context.getMoveToCount();
			int lineToCount = m_context.getLineToCount();
			if (moveToCount > 0 && lineToCount == 0)
			{
				/*
				 * Step through path, setting origin and rotation for each
				 * point and then calling procedure block.
				 */
				ArrayList<Point2D> moveTos = m_context.getMoveTos();
				ArrayList<Double> rotations = m_context.getMoveToRotations();

				for (int i = 0; i < moveToCount; i++)
				{
					m_context.saveState(blockName);
					Point2D.Float pt = (Point2D.Float)(moveTos.get(i));
					m_context.setTranslation(pt.x, pt.y);
					m_context.clearPath();

					double rotation = ((Double)rotations.get(i)).doubleValue();
					m_context.setRotation(rotation);
					makeCall(block, formalParameters, args);
					m_context.restoreState();
				}
			}
			else
			{
				/*
				 * Execute statements in procedure block.  Surround statments
				 * with a save/restore so nothing can be changed by accident.
				 */
				m_context.saveState(blockName);
				makeCall(block, formalParameters, args);
				m_context.restoreState();
			}
		}
		else
		{
			/*
			 * Execute single statement.  If error occurs then add filename and
			 * line number to message so user knows exactly where to look.
			 */
			try
			{
				execute(statement, m_context);
			}
			catch (MapyrusException e)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + e.getMessage());
			}
			catch (IOException e)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + e.getMessage());
			}
		}
		return(returnValue);
	}

	/**
	 * Create new language interpreter.
	 */
	public Interpreter()
	{
		m_statementBlocks = new HashMap<String, Statement>();
		m_userFunctions = new HashMap<String, UserFunction>();
		m_executeArgs = null;
		m_throttle = new Throttle();
	}

	/**
	 * Set throttle limiting CPU usage.
	 * @param throttle throttle to set.
	 */
	public void setThrottle(Throttle throttle)
	{
		m_throttle = throttle;
	}

	/**
	 * Get throttle limiting CPU usage.
	 * @return throttle.
	 */
	public Throttle getThrottle()
	{
		return(m_throttle);
	}

	/**
	 * Return a clone of this interpreter.
	 * @return cloned interpreter.
	 */
	public Object clone()
	{
		Interpreter retval = new Interpreter();
		retval.m_executeArgs = null;
		retval.m_context = null;
		retval.m_throttle = m_throttle.clone();
		retval.m_statementBlocks = new HashMap<String, Statement>(this.m_statementBlocks.size());
		retval.m_statementBlocks.putAll(this.m_statementBlocks);

		/*
		 * Copy all the user functions for use in new interpreter.
		 */
		retval.m_userFunctions = new HashMap<String, UserFunction>(this.m_userFunctions.size());
		for (String key : this.m_userFunctions.keySet())
		{
			UserFunction userFunction = this.m_userFunctions.get(key);
			retval.m_userFunctions.put(key, userFunction.clone(retval));
		}
		retval.m_stdinStream = null;
		retval.m_stdoutStream = null;
		return((Object)retval);
	}
}
