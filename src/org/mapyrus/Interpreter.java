/*
 * $Id$
 */
package au.id.chenery.mapyrus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.ArrayList;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

/**
 * Language interpreter.  Parse and executes commands read from file, or
 * typed by user.
 * 
 * May be called repeatedly to interpret several files in the same context.
 */
public class Interpreter
{
	/*
	 * Character starting a comment on a line.
	 * Character separating arguments to a statement.
	 * Tokens around definition of a procedure.
	 */
	private static final char COMMENT_CHAR = '#';
	private static final char ARGUMENT_SEPARATOR = ',';
	private static final char PARAM_SEPARATOR = ',';
	private static final String BEGIN_KEYWORD = "begin";
	private static final String END_KEYWORD = "end";

	/*
	 * Keywords for if ... then ... else ... endif block.
	 */
	private static final String IF_KEYWORD = "if";
	private static final String THEN_KEYWORD = "then";
	private static final String ELSE_KEYWORD = "else";
	private static final String ELSIF_KEYWORD = "elsif";
	private static final String ENDIF_KEYWORD = "endif";
	
	/*
	 * Keywords for while ... do ... done block.
	 */
	private static final String WHILE_KEYWORD = "while";
	private static final String DO_KEYWORD = "do";
	private static final String DONE_KEYWORD = "done";

	/*
	 * States during parsing statements.
	 */
	private static final int AT_STATEMENT = 1;		/* at start of a statement */
	private static final int AT_ARG = 2;		/* at argument to a statement */
	private static final int AT_ARG_SEPARATOR = 3;	/* at separator between arguments */

	private static final int AT_PARAM = 4;	/* at parameter to a procedure block */
	private static final int AT_PARAM_SEPARATOR = 5;	/* at separator between parameters */


	private static final int AT_BLOCK_NAME = 6;	/* expecting procedure block name */
	private static final int AT_BLOCK_PARAM = 7;
	
	private static final int AT_IF_TEST = 8;	/* at expression to test in if ... then block */
	private static final int AT_THEN_KEYWORD = 9;	/* at "then" keyword in if ... then block */
	private static final int AT_ELSE_KEYWORD = 10;	/* at "else" keyword in if ... then block */
	private static final int AT_ENDIF_KEYWORD = 11;	/* at "endif" keyword in if ... then block */

	private static final int AT_WHILE_TEST = 8;	/* at expression to test in while loop block */
	private static final int AT_DO_KEYWORD = 9;	/* at "do" keyword in while loop block */
	private static final int AT_DONE_KEYWORD = 10;	/* at "done" keyword in while loop block */

	/*
	 * Literals for linestyles.
	 */
	public static final String CAP_BUTT_STRING = "butt";
	public static final String CAP_ROUND_STRING = "round";
	public static final String CAP_SQUARE_STRING = "square";
	public static final String JOIN_BEVEL_STRING = "bevel";
	public static final String JOIN_MITER_STRING = "miter";
	public static final String JOIN_ROUND_STRING = "round";
	
	private ContextStack mContext;
	
	/*
	 * Blocks of statements for each procedure defined in
	 * this interpreter.
	 */
	private Hashtable mStatementBlocks;

	/*
	 * Formats for printing numbers in statements.
	 */	
	private DecimalFormat mDoubleformat, mExponentialFormat;
	
	/*
	 * Static world coordinate system units lookup table.
	 */
	private static Hashtable mWorldUnitsLookup;

	static
	{
		mWorldUnitsLookup = new Hashtable();
		mWorldUnitsLookup.put("m", new Integer(Context.WORLD_UNITS_METRES));
		mWorldUnitsLookup.put("metres", new Integer(Context.WORLD_UNITS_METRES));
		mWorldUnitsLookup.put("meters", new Integer(Context.WORLD_UNITS_METRES));
		mWorldUnitsLookup.put("feet", new Integer(Context.WORLD_UNITS_FEET));
		mWorldUnitsLookup.put("foot", new Integer(Context.WORLD_UNITS_FEET));
		mWorldUnitsLookup.put("ft", new Integer(Context.WORLD_UNITS_FEET));
	}

	/**
	 * Parses all combinations of linestyle setting.  Sets values passed
	 * by user, with defaults for the values they did not give.
	 * @param context graphics context to set linestyle into.
	 * @param arguments to linestyle statement.
	 */	
	private void setLinestyle(ContextStack context, Argument []args)
		throws MapyrusException
	{
		int nExpressions;
		double width = 0.1, dashPhase = 0.0;
		float dashes[] = null;
		int cap = BasicStroke.CAP_SQUARE;
		int join = BasicStroke.JOIN_MITER;

		if (args != null)
			nExpressions = args.length;
		else
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINESTYLE));

		if (args[0].getType() != Argument.NUMERIC)
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_WIDTH));

		width = args[0].getNumericValue();
		if (width < 0)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_WIDTH) +
				": " + width);
		}

		if (nExpressions >= 2)
		{
			if (args[1].getType() != Argument.STRING)
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_END_CAP));

			String s = args[1].getStringValue().toLowerCase();
			if (s.equals(CAP_BUTT_STRING))
				cap = BasicStroke.CAP_BUTT;
			else if (s.equals(CAP_ROUND_STRING))
				cap = BasicStroke.CAP_ROUND;
			else if (s.equals(CAP_ROUND_STRING))
				cap = BasicStroke.CAP_SQUARE;
			else
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_END_CAP) + ": " + s);
		}

		if (nExpressions >= 3)
		{
			if (args[2].getType() != Argument.STRING)
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_LINE_JOIN));

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

		if (nExpressions >= 4)
		{
			if (args[3].getType() != Argument.NUMERIC)
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DASH_PHASE));

			dashPhase = args[3].getNumericValue();
			if (dashPhase < 0)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DASH_PHASE) +
					": " + dashPhase);
			}
		}

		if (nExpressions >= 5)
		{
			/*
			 * Build list of dash pattern values.
			 */
			dashes = new float[args.length - 4];
			for (int i = 4; i < args.length; i++)
			{
				if (args[i].getType() != Argument.NUMERIC)
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DASH_PATTERN));

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
	
	/*
	 * Execute a single statement, changing the path, context or generating
	 * some output.
	 */
	private void execute(Statement st, ContextStack context)
		throws MapyrusException, IOException
	{
		Expression []expr;
		int nExpressions;
		int type;
		Argument []args = null;
		double degrees;
		double x1, y1, x2, y2;
		int units;
		int i;

		expr = st.getExpressions();
		nExpressions = expr.length;
		
		/*
		 * Evaluate each of the expressions for this statement.
		 */
		if (nExpressions > 0)
		{
			args = new Argument[nExpressions];
			for (i = 0; i < nExpressions; i++)
			{
				args[i] = expr[i].evaluate(context);
			}
		}
		
		type = st.getType();
		switch (type)
		{
			case Statement.COLOR:
				if (nExpressions == 1 && args[0].getType() == Argument.STRING)
				{
					/*
					 * Find named color in color name database.
					 */
					Color c = ColorDatabase.getColor(args[0].getStringValue());
					if (c == null)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.COLOR_NOT_FOUND) +
							": " + args[0].getStringValue());
					}
					context.setColor(c); 
				}
				else if (nExpressions == 4 &&
					args[0].getType() == Argument.STRING &&
					args[1].getType() == Argument.NUMERIC &&
					args[2].getType() == Argument.NUMERIC &&
					args[3].getType() == Argument.NUMERIC)
				{
					String colorType = args[0].getStringValue();
					float c1 = (float)args[1].getNumericValue();
					float c2 = (float)args[2].getNumericValue();
					float c3 = (float)args[3].getNumericValue();
					
					if (colorType.equalsIgnoreCase("hsb"))
					{
						/*
						 * Set HSB color.
						 */
						int rgb = Color.HSBtoRGB(c1, c2, c3);
						context.setColor(new Color(rgb));
					}
					else if (colorType.equalsIgnoreCase("rgb"))
					{		
						/*
						 * Set RGB color.
						 */
						context.setColor(new Color(c1, c2, c3));
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
				break;

			case Statement.LINESTYLE:
				setLinestyle(context, args);
				break;
			
			case Statement.MOVE:
			case Statement.DRAW:
				if (nExpressions > 0 && nExpressions % 2 == 0)
				{
					/*
					 * Check that all coordindate values are numbers.
					 */
					for (i = 0; i < nExpressions; i++)
					{
						if (args[0].getType() != Argument.NUMERIC)
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COORDINATE));
						}
					}
					
					for (i = 0; i < nExpressions; i += 2)
					{
						/*
						 * Add point to path.
						 */
						if (type == Statement.MOVE)
						{
							context.moveTo(args[i].getNumericValue(),
								args[i + 1].getNumericValue());
						}
						else
						{
							context.lineTo(args[i].getNumericValue(),
								args[i + 1].getNumericValue());
						}
					}
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.WRONG_COORDINATE));
				}
				break;

			case Statement.ARC:
				if (nExpressions == 5 && args[0].getType() == Argument.NUMERIC &&
					args[1].getType() == Argument.NUMERIC &&
					args[2].getType() == Argument.NUMERIC &&
					args[3].getType() == Argument.NUMERIC &&
					args[4].getType() == Argument.NUMERIC)
				{
					int direction = (args[0].getNumericValue() > 0 ? 1 : -1);

					context.arcTo(direction,
						args[1].getNumericValue(),
						args[2].getNumericValue(),
						args[3].getNumericValue(),
						args[4].getNumericValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_ARC));
				}
				break;
				
			case Statement.CLEARPATH:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.clearPath();
				break;

			case Statement.SLICEPATH:
				if (nExpressions == 2 && args[0].getType() == Argument.NUMERIC &&
					args[1].getType() == Argument.NUMERIC)
				{
					context.slicePath(args[0].getNumericValue(), args[1].getNumericValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PATH_SLICE));
				}
				break;
				
			case Statement.STRIPEPATH:
				if (nExpressions == 2 && args[0].getType() == Argument.NUMERIC &&
					args[1].getType() == Argument.NUMERIC)
				{
					degrees = args[1].getNumericValue();
					context.stripePath(args[0].getNumericValue(), Math.toRadians(degrees));
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PATH_STRIPE));
				}
				break;

			case Statement.STROKE:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.stroke();
				break;
				
			case Statement.FILL:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.fill();
				break;
				
			case Statement.CLIP:
				if (nExpressions > 0)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_VALUES));
				}
				context.clip();
				break;	
							
			case Statement.SCALE:
				if (nExpressions == 2 &&
					args[0].getType() == Argument.NUMERIC &&
					args[1].getType() == Argument.NUMERIC)
				{
					context.setScaling(args[0].getNumericValue(),
						args[1].getNumericValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_SCALING));
				}
				break;

			case Statement.ROTATE:
				if (nExpressions == 1 && args[0].getType() == Argument.NUMERIC)
				{
					degrees = args[0].getNumericValue();
					context.setRotation(Math.toRadians(degrees));
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_ROTATION));
				}
				break;

			case Statement.WORLDS:
				if ((nExpressions == 4 || nExpressions == 5) &&
					args[0].getType() == Argument.NUMERIC &&
					args[1].getType() == Argument.NUMERIC &&
					args[2].getType() == Argument.NUMERIC &&
					args[3].getType() == Argument.NUMERIC)
				{
					x1 = args[0].getNumericValue();
					y1 = args[1].getNumericValue();
					x2 = args[2].getNumericValue();
					y2 = args[3].getNumericValue();
					if (nExpressions == 5)
					{
						Integer u;
						if (args[4].getType() == Argument.STRING)
						{
							u = (Integer)mWorldUnitsLookup.get(args[4].getStringValue());
							if (u == null)
							{
								throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLD_UNITS) +
									": " + args[4].getStringValue());
							}
							units = u.intValue();
						}
						else
						{
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLD_UNITS));
						}
					}
					else
					{
						units = Context.WORLD_UNITS_METRES;
					}
					
					if (x2 - x1 == 0.0 || y2 - y1 == 0.0)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.ZERO_WORLD_RANGE));
					}	
					context.setWorlds(x1, y1, x2, y2, units);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_WORLDS));
				}
				break;

			case Statement.PROJECT:
				if (nExpressions == 2 && args[0].getType() == Argument.STRING &&
					args[1].getType() == Argument.STRING)
				{
						context.setTransform(args[0].getStringValue(),
							args[1].getStringValue());
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_TRANSFORM));
				}
				break;
	
			case Statement.DATASET:
				if (nExpressions >= 3)
				{
					/*
					 * All arguments are strings.
					 */
					for (i = 0; i < nExpressions; i++)
					{
						if (args[i].getType() != Argument.STRING)
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_DATASET));
					}

					/*
					 * Build array of geometry field names.
					 */					
					String []geometryFieldNames = new String[nExpressions - 3];
					for (i = 0; i < geometryFieldNames.length; i++)
						geometryFieldNames[i] = args[i + 3].getStringValue();

					context.setDataset(args[0].getStringValue(),
						args[1].getStringValue(), args[2].getStringValue(),
						geometryFieldNames);
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_COORDINATE));
				}
				break;

			case Statement.IMPORT:
				context.queryDataset();
				break;

			case Statement.FETCH:
				/*
				 * Add next row from dataset to path.
				 */
				Row row = context.fetchRow();
				int index = 0;
				int []geometryFieldIndexes = context.getDatasetGeometryFieldIndexes();
				String []fieldNames = context.getDatasetFieldNames();
				double x = 0.0;
				for (i = 0; i < row.size(); i++)
				{
					Argument field = (Argument)row.get(i);
					if (index < geometryFieldIndexes.length &&
						i == geometryFieldIndexes[index])
					{
						if (field.getType() == Argument.GEOMETRY)
						{
							/*
							 * Define path from fetched geometry.
							 */
							double []coords = field.getGeometryValue();
							int j = 1;
							while (j < coords[0])
							{
								if (coords[j] == PathIterator.SEG_MOVETO)
									context.moveTo(coords[j + 1], coords[j + 2]);
								else
									context.lineTo(coords[j + 1], coords[j + 2]);
								j += 3;
							}
						}
						else
						{
							/*
							 * First pair of geometry fields contain moveTo coordinates.
							 * Successive pairs define lineTo coordinates.
							 */
							if (index == 1)
								context.moveTo(x, field.getNumericValue());
							else if (index % 2 == 0)
								x = field.getNumericValue();
							else
								context.lineTo(x, field.getNumericValue());
						}
						index++;
					}

					if (field.getType() != Argument.GEOMETRY)
					{
						/*
						 * Define attributes in non-geometry fields as variables.
						 */
						context.defineVariable(fieldNames[i], field);
					}
				}
				break;

			case Statement.NEWPAGE:
				if (nExpressions == 5 &&
					args[0].getType() == Argument.STRING &&
					args[1].getType() == Argument.STRING &&
					args[2].getType() == Argument.NUMERIC &&
					args[3].getType() == Argument.NUMERIC &&
					args[4].getType() == Argument.NUMERIC)
				{
					context.setOutputFormat(args[0].getStringValue(),
						args[1].getStringValue(),
						(int)args[2].getNumericValue(),
						(int)args[3].getNumericValue(),
						(int)args[4].getNumericValue(), "extras");
				}
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_PAGE));
				}
				break;	
							
			case Statement.PRINT:
				/*
				 * Print to stdout each of the expressions passed.
				 */
				for (i = 0; i <nExpressions; i++)
				{
					if (args[i].getType() == Argument.STRING)
					{
						System.out.print(args[i].getStringValue());
					}
					else
					{
						DecimalFormat format;
						double d = args[i].getNumericValue();
						double absoluteD = (d >= 0) ? d : -d;

						/*
						 * Print large or small numbers in scientific notation
						 * to give more significant digits.
						 */				
						if (absoluteD != 0 && (absoluteD < 0.01 || absoluteD > 10000000.0))
							format = mExponentialFormat;
						else
							format = mDoubleformat;

						System.out.print(format.format(d));
					}
				}
				System.out.println("");
				break;
				
			case Statement.ASSIGN:
				context.defineVariable(st.getAssignedVariable(), args[0]);
				break;
		}		
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
		StringBuffer word = new StringBuffer();

		/*
		 * A statement or procedure name begins with a keyword
		 * which must begin with a letter.
		 */
		if (!Character.isLetter((char)c))
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
		while (Character.isLetterOrDigit((char)c) || c == '.' || c == '_');

		/*
		 * Put back the character we read that is not part of the word.	
		 */	
		preprocessor.unread(c);
		return(word.toString());
	}

	/*
	 * Are we currently reading a comment?
	 */
	private boolean mInComment = false;

	/*
	 * Read next character, ignoring comments.
	 */
	private int readSkipComments(Preprocessor preprocessor)
		throws IOException, MapyrusException
	{
		int c;

		c = preprocessor.read();
		while (mInComment == true || c == COMMENT_CHAR)
		{
			if (c == COMMENT_CHAR)
			{
				/*
				 * Start of comment, skip characters until the end of the line.
				 */
				mInComment = true;
				c = preprocessor.read();
			}
			else if (c == '\n' || c == -1)
			{
				/*
				 * End of file or end of line is end of comment.
				 */
				mInComment = false;
			}
			else
			{
				/*
				 * Skip character in comment.
				 */
				c = preprocessor.read();
			}
		}
		return(c);
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
		ArrayList expressions = new ArrayList();
		Expression expr;
		Statement retval = null;
		boolean isAssignmentStatement = false;
		boolean finishedStatement = false;
		int c;

		state = AT_STATEMENT;
		c = readSkipComments(preprocessor);
		finishedStatement = false;
		while (!finishedStatement)
		{
			if (c == -1 || c == '\n')
			{
				/*
				 * End of line or end of file signifies end of statement.
				 */
				finishedStatement = true;
			}
			else if (Character.isWhitespace((char)c))
			{
				/*
				 * Ignore any whitespace.
				 */
				c = readSkipComments(preprocessor);
			}
			else if (state == AT_STATEMENT)
			{
				/*
				 * Is this an assignment statement of the form: var = value
				 */
				isAssignmentStatement = (c == '=');
				if (isAssignmentStatement)
					c = readSkipComments(preprocessor);
				state = AT_ARG;
			}
			else if (state == AT_ARG_SEPARATOR)
			{
				/*
				 * Expect a ',' between arguments and parameters to
				 * procedure block.
				 */ 
				if (c != ARGUMENT_SEPARATOR)
				{
					throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
						": " + MapyrusMessages.get(MapyrusMessages.EXPECTED) +
						": " + ARGUMENT_SEPARATOR);
				}
				c = readSkipComments(preprocessor);
				state = AT_ARG;
			}
			else if (state == AT_ARG)
			{
					/*
					 * Parse an expression.
					 */
					preprocessor.unread(c);
					expr = new Expression(preprocessor);
					expressions.add(expr);

					state = AT_ARG_SEPARATOR;
					c = readSkipComments(preprocessor);
			}
			else
			{
				/*
				 * Parsing is lost.  Don't know what is wrong.
				 */
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.PARSE_ERROR));
			}
		}

		/*
		 * Build a statement structure for what we just parsed.
		 */
		if (c == -1 && state == AT_STATEMENT)
		{
			/*
			 * Could not parse anything before we got EOF.
			 */
			retval = null;
		}
		else if (isAssignmentStatement)
		{
			/*
			 * Exactly one expression is assigned in a statement.
			 */
			if (expressions.size() > 1)
			{
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.TOO_MANY_EXPRESSIONS));
			}
			else if (expressions.size() == 0)
			{
				throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.NO_EXPRESSION));
			}

			retval = new Statement(keyword, (Expression)expressions.get(0));

			retval.setFilenameAndLineNumber(preprocessor.getCurrentFilename(),
					preprocessor.getCurrentLineNumber());
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
	private ArrayList parseParameters(Preprocessor preprocessor)
		throws IOException, MapyrusException
	{
		int c;
		ArrayList parameters = new ArrayList();
		int state;

		/*
		 * Read parameter names separated by ',' characters.
		 */
		state = AT_PARAM;
		c = readSkipComments(preprocessor);
		while (c != -1 && c != '\n')
		{
			if (Character.isWhitespace((char)c))
			{
				/*
				 * Ignore whitespace.
				 */
				c = readSkipComments(preprocessor);
			}
			else if (state == AT_PARAM)
			{
				/*
				 * Expect a parameter name.
				 */
				parameters.add(parseWord(c, preprocessor));
				state = AT_PARAM_SEPARATOR;
				c = readSkipComments(preprocessor);
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
						": " + PARAM_SEPARATOR);
				}
				state = AT_PARAM;
				c = readSkipComments(preprocessor);
			}
		}
		return(parameters);
	}

	/**
	 * Reads and parses a procedure block, several statements
	 * grouped together between "begin" and "end" keywords.
	 * @param preprocessor is source to read from.
	 * @retval parsed procedure block as single statement.
	 */
	private ParsedStatement parseProcedureBlock(Preprocessor preprocessor)
		throws IOException, MapyrusException
	{
		String blockName;
		ArrayList parameters;
		ArrayList procedureStatements = new ArrayList();
		ParsedStatement st;
		Statement retval;
		boolean parsedEndKeyword = false;
		int c;

		/*
		 * Skip whitespace between "begin" and block name.
		 */		
		c = readSkipComments(preprocessor);
		while (Character.isWhitespace((char)c))
			c = readSkipComments(preprocessor);
		
		blockName = parseWord(c, preprocessor);
		parameters = parseParameters(preprocessor);

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

		/*
		 * Return procedure block as a single statement.
		 */
		retval = new Statement(blockName, parameters, procedureStatements);
		return(new ParsedStatement(retval));
	}
	
	/**
	 * Reads and parses while loop statement.
	 * Parses test expression, "do" keyword, some
	 * statements, and then "done" keyword.
	 * @param preprocessor is source to read from.
	 * @return parsed loop as single statement.
	 */
	private ParsedStatement parseWhileStatement(Preprocessor preprocessor,
		boolean inProcedureDefn)
		throws IOException, MapyrusException
	{
		ParsedStatement st;
		Expression test;
		ArrayList loopStatements = new ArrayList();
		Statement statement;
		
		test = new Expression(preprocessor);
		
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

		statement = new Statement(test, loopStatements);
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
		Expression test;
		ArrayList thenStatements = new ArrayList();
		ArrayList elseStatements = new ArrayList();
		Statement statement;
		boolean checkForEndif = true;	/* do we need to check for "endif" keyword at end of statement? */

		test = new Expression(preprocessor);

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
		return(new ParsedStatement(statement));
	}

	/*
	 * Static keyword lookup table for fast keyword lookup.
	 */
	private static Hashtable mKeywordLookup;

	static
	{
		mKeywordLookup = new Hashtable();
		mKeywordLookup.put(END_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_END));
		mKeywordLookup.put(THEN_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_THEN));
		mKeywordLookup.put(ELSE_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_ELSE));
		mKeywordLookup.put(ELSIF_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_ELSIF));
		mKeywordLookup.put(ENDIF_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_ENDIF));
		mKeywordLookup.put(DO_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_DO));
		mKeywordLookup.put(DONE_KEYWORD,
			new ParsedStatement(ParsedStatement.PARSED_DONE));						
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
		Statement statement;
		ArrayList procedureStatements = null;
		int state;
		boolean finishedStatement = false;

		state = AT_STATEMENT;
		c = readSkipComments(preprocessor);
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
				c = readSkipComments(preprocessor);
			}
			else
			{
				String keyword = parseWord(c, preprocessor);
				String lower = keyword.toLowerCase();

				/*
				 * Is this the start or end of a procedure block definition?
				 */
				if (lower.equals(BEGIN_KEYWORD))
				{
					/*
					 * Nested procedure blocks not allowed.
					 */
					if (inProcedureDefn)
					{
						throw new MapyrusException(preprocessor.getCurrentFilenameAndLineNumber() +
							": " + MapyrusMessages.get(MapyrusMessages.NESTED_PROC));
					}
					retval = parseProcedureBlock(preprocessor);
				}
				else if (lower.equals(IF_KEYWORD))
				{
					retval = parseIfStatement(preprocessor, inProcedureDefn);
				}
				else if (lower.equals(WHILE_KEYWORD))
				{
					retval = parseWhileStatement(preprocessor, inProcedureDefn);
				}
				else
				{
					/*
					 * Does keyword match a control-flow keyword?
				 	 * like "then", or "else"?
					 */
					retval = (ParsedStatement)mKeywordLookup.get(lower);
					if (retval == null)
					{
						/*
						 * It must be a regular type of statement if we
						 * can't match any special words.
						 */
						Statement st = parseSimpleStatement(keyword, preprocessor);
						retval = new ParsedStatement(st);
					}
				}
				finishedStatement = true;
			}
		}

		return(retval);
	}

	/*
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
	 * @param f is open file or URL to read from.
	 * @param filename is name of file or URL (for use in error messages).
	 */
	public void interpret(Reader f, String filename)
		throws IOException, MapyrusException
	{
		Statement st;
		Preprocessor preprocessor = new Preprocessor(f, filename);
		mInComment = false;
		
		/*
		 * Keep parsing until we get EOF.
		 */
		while ((st = parseStatement(preprocessor)) != null)
		{
			executeStatement(st);
		}
	}

	private void makeCall(Statement block, ArrayList parameters, Argument []args)
		throws IOException, MapyrusException
	{
		Statement statement;

		for (int i = 0; i < args.length; i++)
		{
			mContext.defineVariable((String)parameters.get(i), args[i]);
		}

		/*
		 * Execute each of the statements in the procedure block.
		 */
		ArrayList v = block.getStatementBlock();
		for (int i = 0; i < v.size(); i++)
		{
			statement = (Statement)v.get(i);
			executeStatement(statement);
		}
	}

	/**
	 * Recursive function for executing statements.
	 * @param preprocessor is source to read statements from.
	 */
	private void executeStatement(Statement statement)
		throws IOException, MapyrusException
	{
		Argument []args;
		int statementType = statement.getType();

		/*
		 * Store procedure blocks away for later execution,
		 * execute any other statements immediately.
		 */
		if (statementType == Statement.BLOCK)
		{
			mStatementBlocks.put(statement.getBlockName(), statement);
		}
		else if (statementType == Statement.CONDITIONAL)
		{
			/*
			 * Execute correct part of if statement depending on value of expression.
			 */
			Expression []expr = statement.getExpressions();
			Argument test = expr[0].evaluate(mContext);
			ArrayList v;
			
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
					executeStatement(statement);
				}
			}
		}
		else if (statementType == Statement.LOOP)
		{
			/*
			 * Find expression to test and loop statements to execute.
			 */
			Expression []expr = statement.getExpressions();
			
			ArrayList v = statement.getLoopStatements();
			Argument test = expr[0].evaluate(mContext);
			
			if (test.getType() != Argument.NUMERIC)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.INVALID_EXPRESSION));
			}

			/*
			 * Execute loop while expression remains true (non-zero).
			 */			
			while (test.getNumericValue() != 0.0)
			{
				/*
				 * Execute each of the statements.
				 */	
				for (int i = 0; i < v.size(); i++)
				{
					statement = (Statement)v.get(i);
					executeStatement(statement);
				}
				
				test = expr[0].evaluate(mContext);
				if (test.getType() != Argument.NUMERIC)
				{
					throw new MapyrusException(statement.getFilenameAndLineNumber() +
						": " + MapyrusMessages.get(MapyrusMessages.INVALID_EXPRESSION));
				}
			}
		}
		else if (statementType == Statement.CALL)
		{
			/*
			 * Find the statements for the procedure block we are calling.
			 */
			Statement block =
				(Statement)mStatementBlocks.get(statement.getBlockName());
			if (block == null)
			{
				throw new MapyrusException(statement.getFilenameAndLineNumber() +
					": " + MapyrusMessages.get(MapyrusMessages.UNDEFINED_PROC) +
					": " + statement.getBlockName());
			}
			
			/*
			 * Check that correct number of parameters are being passed.
			 */
			ArrayList formalParameters = block.getBlockParameters();
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
					args[i] = actualParameters[i].evaluate(mContext);
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
			int moveToCount = mContext.getMoveToCount();
			int lineToCount = mContext.getLineToCount();
			if (moveToCount > 0 && lineToCount == 0)
			{
				/*
				 * Step through path, setting origin and rotation for each
				 * point and then calling procedure block.
				 */
				ArrayList moveTos = mContext.getMoveTos();
				ArrayList rotations = mContext.getMoveToRotations();

				for (int i = 0; i < moveToCount; i++)
				{
					mContext.saveState();
					Point2D.Float pt = (Point2D.Float)(moveTos.get(i));
					mContext.setTranslation(pt.x, pt.y);
					double rotation = ((Double)rotations.get(i)).doubleValue();
					mContext.setRotation(rotation);
					makeCall(block, formalParameters, args);
					mContext.restoreState();
				}
			}
			else
			{
				/*
				 * Execute statements in procedure block.  Surround statments
				 * with a save/restore so nothing can be changed by accident.
				 */
				mContext.saveState();
				makeCall(block, formalParameters, args);
				mContext.restoreState();
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
				execute(statement, mContext);
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
	}

	/**
	 * Create new language interpreter.
	 * @param context is the context to use during interpretation.
	 * This may be in a changed state by the time interpretation
	 * is finished.
	 */
	public Interpreter(ContextStack context)
	{
		mContext = context;
		mStatementBlocks = new Hashtable();

		mDoubleformat = new DecimalFormat("#.################");
		mExponentialFormat = new DecimalFormat("#.################E0");
	}
}
