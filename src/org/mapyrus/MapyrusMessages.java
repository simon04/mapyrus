/*
 * $Id$
 */

package au.id.chenery.mapyrus;

import java.util.ResourceBundle;

/**
 * Wrapper around a java resource file containing messages for various
 * locales.  Provides single function to get a message for the current
 * locale, given the message key identifier.  
 */
public class MapyrusMessages
{
	public static final String COLOR_NOT_FOUND = "colornotfound";
	public static final String DEFINE_TRANSFORM = "definetransform";
	public static final String ERROR_PS = "errorps";
	public static final String EXPECTED = "expected";
	public static final String FIELD_MISMATCH = "fieldmismatch";
	public static final String FIELD_NOT_FOUND = "fieldnotfound";
	public static final String FIELD_NOT_GEOMETRY = "fieldnotgeometry";
	public static final String INVALID_ARC = "invalidarc";
	public static final String INVALID_COLOR = "invalidcolor";
	public static final String INVALID_COLOR_TYPE = "invalidcolortype";
	public static final String INVALID_COORDINATE = "invalidcoordinate";
	public static final String INVALID_DASH_PHASE = "invaliddashphase";
	public static final String INVALID_DASH_PATTERN = "invaliddashpattern";
	public static final String INVALID_DATASET = "invaliddataset";
	public static final String INVALID_DATASET_TYPE = "invaliddatasettype";
	public static final String INVALID_END_CAP = "invalidendcap";
	public static final String INVALID_EXPRESSION = "invalidexpression";
	public static final String INVALID_KEYWORD = "invalidkeyword";
	public static final String INVALID_LINE_JOIN = "invalidlinejoin";
	public static final String INVALID_LINE_WIDTH = "invalidlinewidth";
	public static final String INVALID_LINESTYLE = "invalidlinestyle";
	public static final String INVALID_NUMBER = "invalidnumber";
	public static final String INVALID_OUTPUT = "invalidoutput";
	public static final String INVALID_PAGE = "invalidpage";
	public static final String INVALID_PATH_SAMPLE = "invalidpathsample";
	public static final String INVALID_PATH_STRIPE = "invalidpathstripe";
	public static final String INVALID_ROTATION = "invalidrotation";
	public static final String INVALID_SCALING = "invalidscaling";
	public static final String INVALID_TRANSFORM = "invalidtransform";
	public static final String INVALID_WORLDS = "invalidworlds";
	public static final String INVALID_WORLD_UNITS = "invalidworldunits";
	public static final String MISSING_FIELD = "missingfield";
	public static final String MISSING_FILENAME = "missingfilename";
	public static final String NESTED_PROC = "nestedproc";
	public static final String NOT_NUMERIC_OPERATION = "notnumericoperation";
	public static final String NOT_SHAPE_FILE = "notshapefile";
	public static final String NOT_STRING_OPERATION = "notstringoperation";
	public static final String NOT_TEXT_FILE = "nottextfile";
	public static final String NO_DATASET = "nodataset";
	public static final String NO_EXPRESSION = "noexpression";
	public static final String NO_GEOMETRY_FIELD = "nogeometryfield";
	public static final String NO_ROWS = "norows";
	public static final String NUMERIC_OVERFLOW = "numericoverflow";
	public static final String OPEN_DATASET_ERROR = "opendataseterror";
	public static final String PARSE_ERROR = "parseerror";
	public static final String PROCESS_ERROR = "processerror";
	public static final String RECURSION = "recursion";
	public static final String TOO_MANY_EXPRESSIONS = "toomanyexpressions";
	public static final String TRANSFORM_ERROR = "transformerror";
	public static final String UNDEFINED_PROC = "undefinedproc";
	public static final String UNEXPECTED_EOF = "unexpectedeof";
	public static final String UNEXPECTED_VALUES = "unexpectedvalues";
	public static final String UNMATCHED_BRACKET = "unmatchedbracket";
	public static final String VARIABLE_UNDEFINED = "variableundefined";
	public static final String WRONG_COORDINATE = "wrongcoordinate";
	public static final String WRONG_PARAMETERS = "wrongparameters";
	public static final String WRONG_TYPES = "wrongtypes";
	public static final String ZERO_WORLD_RANGE = "zeroworldrange";

	private static ResourceBundle messages;

	static
	{
		messages = ResourceBundle.getBundle("MapyrusMessages");
	}

	/**
	 * Returns message for current locale for message a key.
	 * @return full message
	 */
	public static String get(String key)
	{
		return(messages.getString(key));
	}
}
