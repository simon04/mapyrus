/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 Simon Chenery.
 *k
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * @(#) $Id$
 */

package org.mapyrus;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * An argument is a literal value, array of literal values, or a variable name.
 * Each field read from a dataset is an argument.
 * Several arguments can be combined with operators to make an expression
 * For example, '2 * a + 7' and 'prefix["DE"] . "11823"' both contain three arguments.
 */
public class Argument
{
	public static final int NUMERIC = 0;
	public static final int STRING = 1;
	public static final int HASHMAP = 2;
	public static final int VARIABLE = 3;
	public static final int GEOMETRY = 4;

	public static final int GEOMETRY_POINT = 100;
	public static final int GEOMETRY_LINESTRING = 101;
	public static final int GEOMETRY_POLYGON = 102;
	public static final int GEOMETRY_MULTIPOINT = 103;
	public static final int GEOMETRY_MULTILINESTRING = 104;
	public static final int GEOMETRY_MULTIPOLYGON = 105;
	public static final int GEOMETRY_COLLECTION = 106;

	/*
	 * Markers in geometry array marking each segment of a geometry.
	 */
	public static final int MOVETO = 0;
	public static final int LINETO = 1;

	/**
	 * Constant for numeric value zero.
	 * Avoids allocating many arguments for this commonly used value.
	 */
	public static final Argument numericZero = new Argument(0.0);

	/**
	 * Constant for numeric value one.
	 * Avoids allocating many arguments for this commonly used value.
	 */
	public static final Argument numericOne = new Argument(1.0);

	/**
	 * Constant for numeric value minus one.
	 * Avoids allocating many arguments for this commonly used value.
	 */
	public static final Argument numericMinusOne = new Argument(-1.0);

	/**
	 * Constant for empty string.
	 * Avoids allocating many arguments for this commonly used value.
	 */
	public static final Argument emptyString = new Argument(Argument.STRING, "");

	private int mType;
	private double mNumericValue;
	private String mStringValue;
	private String mVarname;
	private double []mGeometryValue;
	private HashMap mHashMap;

	/**
	 * Create a new numeric argument.
	 * @param d is value for this argument.
	 */
	public Argument(double d)
	{
		mType = NUMERIC;
		mNumericValue = d;
	}

	/**
	 * Create a new string argument, or variable name.
	 * @param type is STRING to create a string argument, or a VARIABLE to create
	 * a reference to a variable.
	 * @param s is string, or variable name.
	 */
	public Argument(int type, String s)
	{
		mType = type;

		if (type == STRING)
			mStringValue = s;
		else
			mVarname = s;

		/*
		 * We don't know the numeric value of this argument.
		 */
		mNumericValue = Double.NaN;
	}

	/**
	 * Create new, empty hashmap argument.
	 * Use addHashMapEntry() method to add values to the hash map.
	 */
	public Argument()
	{
		mType = HASHMAP;
		mHashMap = new HashMap();	
	}

	/**
	 * Create a new geometry argument for all types of geometry.
	 * @param geometryType OGC geometry type.
	 * @param coords is array containing (X, Y) coordinates of point(s),
	 * line(s), polygon(s).
	 *
	 * For simple geometry, array elements are of the form:
	 * <pre>
	 * GEOMETRY_POINT, 1, MOVE, x, y
	 * 
	 * GEOMETRY_LINESTRING, 2, MOVE, x0, y0, DRAW, x1, y1
	 * 
	 * GEOMETRY_POLYGON, 4, MOVE, x0, y0, DRAW, x1, y1, DRAW, x2, y2, DRAW, x3, y3
	 * </pre>
	 * 
	 * where second element gives number of coordinates in geometry.
	 * 
	 * For multiple geometry types, array elements are of the form:
	 * <pre>
	 * GEOMETRY_MULTIPOINT, 2, GEOMETRY_POINT 1, MOVE, x, y, GEOMETRY_POINT, 1, MOVE, x, y
	 * 
	 * GEOMETRY_MULTILINESTRING, 2, GEOMETRY_LINESTRING, 2, MOVE, x0, y0, DRAW x1, y1,
	 * GEOMETRY_LINESTRING, 3, MOVE, x0, y0, DRAW x1, y1, DRAW x2, y2
	 * 
	 * GEOMETRY_MULTIPOLYGON, 2, GEOMETRY_POLYGON, 4, MOVE, x0, y0, DRAW, x1, y1,
	 * DRAW, x2, y2, DRAW, x3, y3, GEOMETRY_POLYGON, 4, MOVE, x0, y0, DRAW, x1, y1,
	 * DRAW, x2, y2, DRAW, x3, y3
	 * 
	 * GEOMETRY_COLLECTION, 2, GEOMETRY_POINT, 1, MOVE, x, y,
	 * GEOMETRY_LINESTRING, 2, MOVE, x0, y0, DRAW, x1, y1
	 * </pre>
	 * 
	 * where the second element gives the number of geometries included in the
	 * multi-geometry.
	 * 
	 */
	public Argument(int geometryType, double []coords)
	{
		mType = geometryType;
		mGeometryValue = coords;
	}

	/**
	 * Parse parenthesised coordinate list from OGC geometry string into array.
	 * @param wktGeometry original geometry string.
	 * @param st OGC geometry string as tokens.
	 * @param geometryIndex index into geometry array to add coordinates.
	 * @param isMultiPoint flag true when all coordinates to be added as MULTIPOINT geometry.
	 * @return number of coordinate pairs added to geometry array.
	 */
	private int parseCoordinateList(String wktGeometry, StringTokenizer st,
		int geometryIndex, boolean isMultiPoint)
		throws MapyrusException
	{
		String token;
		boolean foundOpenParen, foundCloseParen, foundEmpty;
		boolean foundX, foundY;
		int index = geometryIndex;
		int counter = 0;

		/*
		 * First expect a '(' or the keyword 'EMPTY'.
		 */
		foundOpenParen = foundCloseParen = foundEmpty = false;
		foundX = foundY = false;
		while (foundOpenParen == false && foundEmpty == false && st.hasMoreTokens())
		{
			token = st.nextToken();
			foundOpenParen = token.equals("(");
			foundEmpty = token.equals("EMPTY");
		}

		/*
		 * Then expect a series of X and Y coordinate value separated by commas,
		 * and then a ')' to finish the coordinate string.
		 */
		if (foundOpenParen)
		{
			while (foundCloseParen == false && st.hasMoreTokens())
			{
				token = st.nextToken();
				char c = token.charAt(0);
				if (Character.isDigit(c) || c == '-' || c == '.')
				{
					try
					{
						if (foundX == false)
						{
							if (isMultiPoint)
							{
								/*
								 * Set each point in multipoint geometry as
								 * a separate point geometry.
								 */
								mGeometryValue[index] = GEOMETRY_POINT;
								mGeometryValue[index + 1] = 1;
								mGeometryValue[index + 2] = MOVETO;
								index += 3;
							}
							else if (counter == 0)
							{
								/*
								 * Move to first coordinate pair, then add a
								 * line to each subsequent pair.
								 */
								mGeometryValue[index] = MOVETO;
								index++;
							}
							else
							{
								mGeometryValue[index] = LINETO;
								index++;
							}

							mGeometryValue[index] = Double.parseDouble(token);
							index++;
							foundX = true;
						}
						else if (foundY == false)
						{
							mGeometryValue[index] = Double.parseDouble(token);
							index++;
							foundY = true;
							counter++;
						}
						else
						{
							/*
							 * Not expecting an X or Y coordinate.  The OGC string must be invalid.
							 */
							throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
								": " + wktGeometry);
						}
					}
					catch (NumberFormatException e)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
							": " + wktGeometry);
					}
				}
				else if (c == ')' && foundY)
				{
					/*
					 * Found closing parenthesis marking end of coordinates.
					 */
					foundCloseParen = true;
				}
				else if (c == ',' && foundY)
				{
					/*
					 * Found comma separating coordinate pairs
					 */
					foundX = foundY = false;
				}
				else if (c != ' ')
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
						": " + wktGeometry);
				}
			}
		}

		/*
		 * Did we successively parse something?
		 */
		if ((foundOpenParen && foundCloseParen == false) || (foundX && (!foundY)))
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
				": " + wktGeometry);
		}
		return(counter);
	}

	/**
	 * Parse polygon from OGC geometry string into array.
	 * @param wktGeometry original geometry string.
	 * @param st OGC geometry coordinates as tokens.
	 * @param geometryIndex index into geometry array to add coordinates.
	 * @param isMultiLinestring flag true when all coordinates to be added
	 * as MULTILINESTRING geometries
	 * @return index of next free position in geometry array after adding coordinates.
	 */
	private int parseRing(String wktGeometry, StringTokenizer st,
		int geometryIndex, boolean isMultiLinestring)
		throws MapyrusException
	{
		String token;
		int counter = 0;
		int nCoords;
		int index = geometryIndex + 1;

		/*
		 * Parse opening '('.  Then one or more lists of coordinates surrounded
		 * by parentheses and separated by commas.  Then parse closing ')'.
		 */
		boolean foundCloseParen = false;
		boolean foundCoordList = false;
		boolean foundEmptyList = false;

		foundCoordList = foundEmptyList = false;
		while (foundCoordList == false && foundEmptyList == false && st.hasMoreTokens())
		{
			token = st.nextToken();
			foundCoordList = token.equals("(");
			foundEmptyList = token.equals("EMPTY");
		}

		if (foundEmptyList == false)
		{
			while (foundCoordList && foundCloseParen == false && st.hasMoreTokens())
			{
				/*
				 * Parse coordinate list like '(10 20, 30 50)'.
				 */
				if (isMultiLinestring)
				{
					mGeometryValue[index] = GEOMETRY_LINESTRING;
					index++;
					nCoords = parseCoordinateList(wktGeometry, st, index + 1, false);
					mGeometryValue[index] = nCoords;
					index++;
					counter++;
				}
				else
				{
					nCoords = parseCoordinateList(wktGeometry, st, index, false);
					counter += nCoords;
				}

				index += nCoords * 3;

				/*
				 * Is there another coordinate list following this one?
				 */
				foundCoordList = false;
				while (foundCoordList == false && foundCloseParen == false && st.hasMoreTokens())
				{
					token = st.nextToken();
					foundCoordList = token.equals(",");
					foundCloseParen = token.equals(")");
				}
			}

			/*
			 * If we did not parse right through to the ')' then there is
			 * something wrong with the geometry string.
			 */
			if (!foundCloseParen)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
					": " + wktGeometry);
			}
		}

		/*
		 * Set number of coordinate pairs for POLYGON, number of sub-geometries for MULTILINESTRING. 
		 */
		mGeometryValue[geometryIndex] = counter;  
		return(index);
	}

	/**
	 * Parse OGC geometry string.
	 * @param wktGeometry original geometry string.
	 * @param st OGC geometry coordinates as tokens.
	 * @param index index into geometry array to add geometry type and coordinates.
	 * @return index of next free position in geometry array after adding coordinates.
	 */
	private int parseGeometry(String wktGeometry, StringTokenizer st, int index)
		throws MapyrusException
	{
		String token;
		String ogcType;
		int nCoords;
		boolean foundOpenParen, foundCloseParen, foundComma, foundEmptyList;

		if (st.hasMoreTokens())
		{
			/*
			 * Find geometry type, skipping any leading spaces.
			 */
			ogcType = st.nextToken();
			while (ogcType.equals(" ") && st.hasMoreTokens())
				ogcType = st.nextToken();

			/*
			 * What type of geometry is this?
			 */
			if (ogcType.equals("POINT"))
			{
				mGeometryValue[index] = GEOMETRY_POINT;
				index++;
				nCoords = parseCoordinateList(wktGeometry, st, index + 1, false);
				mGeometryValue[index] = nCoords;
				index++;
				index += nCoords * 3;
			}
			else if (ogcType.equals("LINESTRING"))
			{
				mGeometryValue[index] = GEOMETRY_LINESTRING;
				index++;
				nCoords = parseCoordinateList(wktGeometry, st, index + 1, false);
				mGeometryValue[index] = nCoords;
				index++;
				index += nCoords * 3;
			}
			else if (ogcType.equals("MULTIPOINT"))
			{
				mGeometryValue[index] = GEOMETRY_MULTIPOINT;
				index++;
				nCoords = parseCoordinateList(wktGeometry, st, index + 1, true);
				mGeometryValue[index] = nCoords;
				index++;
				index += nCoords * 5; /* 5 values for each POINT sub-geometry */
			}
			else
			{
				if (ogcType.equals("POLYGON"))
					mGeometryValue[index] = GEOMETRY_POLYGON;
				else if (ogcType.equals("MULTILINESTRING"))
					mGeometryValue[index] = GEOMETRY_MULTILINESTRING;
				else if (ogcType.equals("MULTIPOLYGON"))
					mGeometryValue[index] = GEOMETRY_MULTIPOLYGON;
				else if (ogcType.equals("GEOMETRYCOLLECTION"))
					mGeometryValue[index] = GEOMETRY_COLLECTION;
				else
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
						": " + wktGeometry);
				}

				if (mGeometryValue[index] == GEOMETRY_POLYGON)
				{
					index = parseRing(wktGeometry, st, index + 1, false);
				}
				else if (mGeometryValue[index] == GEOMETRY_MULTILINESTRING)
				{
					index = parseRing(wktGeometry, st, index + 1, true);
				}
				else
				{
					/*
					 * Parse any number of polygons or geometries surrounded
					 * by parentheses.
					 */
					foundOpenParen = foundCloseParen = foundEmptyList = false;
					while (foundOpenParen == false && foundEmptyList == false && st.hasMoreTokens())
					{
						token = st.nextToken();
						foundOpenParen = token.equals("(");
						foundEmptyList = token.equals("EMPTY");
					}

					int geometryType = (int)mGeometryValue[index];
					int counter = 0;
					int counterIndex = index + 1;
					index += 2;

					while (foundOpenParen && foundCloseParen == false && st.hasMoreTokens())
					{
						if (geometryType == GEOMETRY_MULTIPOLYGON)
						{
							mGeometryValue[index] = GEOMETRY_POLYGON;
							index = parseRing(wktGeometry, st, index + 1, false);
						}
						else
						{
							index = parseGeometry(wktGeometry, st, index);
						}

						counter++;

						foundComma = false;
						while (foundComma == false &&
							foundCloseParen == false && st.hasMoreTokens())
						{
							token = st.nextToken();
							if (token.equals(","))
								foundComma = true;
							else if (token.equals(")"))
								foundCloseParen = true;
						}
					}

					/*
					 * Set number of sub-geometries in MULTIPOLYGON or GEOMETRYCOLLECTION. 
					 */
					mGeometryValue[counterIndex] = counter;

					if (foundOpenParen && foundCloseParen == false)
					{
						throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
							": " + wktGeometry);
					}
				}
			}
		}
		else
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_OGC_WKT) +
				": " + wktGeometry);
		}
		return(index);
	}

	/**
	 * Create new geometry argument from OGC well known geometry string.
	 * @param wktGeometry OGC geometry string.
	 */
	public Argument(String wktGeometry) throws MapyrusException
	{
		StringTokenizer st = new StringTokenizer(wktGeometry, ",() ", true);
		mGeometryValue = new double[st.countTokens() + 1];
		parseGeometry(wktGeometry, st, 0);
		mType = (int)mGeometryValue[0];
	}

	/**
	 * Returns type of argument.
	 * @return either NUMERIC, STRING, VARIABLE, or GEOMETRY.
	 */	
	public int getType()
	{
		if (mType == NUMERIC || mType == STRING || mType == VARIABLE || mType == HASHMAP)
			return(mType);
		else
			return(GEOMETRY);
	}

	/**
	 * Returns numeric value of argument.
	 * @return numeric argument value,
	 * or zero if it cannot be converted to a number.
	 */
	public double getNumericValue() throws MapyrusException
	{
		if (mType == STRING)
		{
			if (Double.isNaN(mNumericValue))
			{
				/*
				 * Argument is a string that we've not tried
				 * converting to a number before.  Find it's numeric
				 * value now.
				 */
				try
				{
					mNumericValue = Double.parseDouble(mStringValue);
				}
				catch (NumberFormatException e)
				{
					mNumericValue = 0;
				}
			}
		}
		else if (mType == HASHMAP)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.HASHMAP_NOT_NUMERIC));
		}
		else if (mType != NUMERIC)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.GEOMETRY_NOT_NUMERIC));
		}
		return(mNumericValue);
	}

	/**
	 * Returns value of argument as a string.
	 * @return argument value as a string.
	 */
	public String getStringValue()
	{
		return(toString());
	}

	/**
	 * Returns variable name reference of argument.
	 * @return name of variable containing value for this argument.
	 */	
	public String getVariableName()
	{
		return(mVarname);
	}

	/**
	 * Returns value of geometry argument.
	 * @return geometry argument value.
	 */	
	public double []getGeometryValue()
	{
		return(mGeometryValue);
	}
	
	/**
	 * Returns value of one entry in a hashmap.
	 * @param key is key to lookup.
	 * @return value associated with this key, or empty string argument
	 * if this key is not in hashmap.
	 */	
	public Argument getHashMapEntry(String key)
	{
		Argument retval = (Argument)mHashMap.get(key);
		if (retval == null)
			retval = emptyString;
		return(retval);
	}
	
	/**
	 * Add new key-value pair to a hash map.
	 * @param key key to add to hash map.
	 * @param value value to add for this key.
	 */
	public void addHashMapEntry(String key, Argument value)
	{
		mHashMap.put(key, value);
	}

	/**
	 * Comparator for string lists.
	 * Orders list elements that contain numbers numerically, other
	 * elements alphabetically.
	 */
	private class NumericAndStringComparator implements Comparator
	{
		public int compare(Object o1, Object o2)
		{
			int retval;
			String s1 = (String)o1;
			String s2 = (String)o2;

			try
			{
				/*
				 * If both strings contain numbers then compare them
				 * numerically.
				 */
				int i1 = Integer.parseInt(s1);
				int i2 = Integer.parseInt(s2);
				retval = i1 - i2;
			}  
			catch (NumberFormatException e)
			{
				retval = s1.compareTo(s2);
			}
			return(retval);
		}
	}

	/**
	 * Get array of keys in hash map.
	 * @return keys in this hash map, each object being a string.
	 */
	public Object[] getHashMapKeys()
	{
		Object []keys = mHashMap.keySet().toArray();
		
		/*
		 * Return keys in sorted order, either numerically or alphabetically.
		 */
		Arrays.sort(keys, new NumericAndStringComparator());
		return(keys);
	}

	/**
	 * Get number of elements in hash map.
	 * @return size of hashmap.
	 */
	public int getHashMapSize()
	{
		return(mHashMap.size());
	}

	/**
	 * Creates OGC WKT geometry string from geometry array.
	 * @param coords geometry type, count and move/draw coordinates.
	 * @param startIndex index in coords array to start converting.
	 * @param s buffer to append geometry to.
	 * @param addGeometryType if true then geometry type added to geometry string.
	 * @return index of next element in coords array to be parsed.
	 */
	private int createOGCWKT(double []coords, int startIndex, StringBuffer s, boolean addGeometryType)
	{
		int geometryType = (int)coords[startIndex];
		int nElements = (int)coords[startIndex + 1];
		int nextIndex = startIndex + 2;

		if (addGeometryType)
		{
			if (geometryType == GEOMETRY_POINT)
				s.append("POINT ");
			else if (geometryType == GEOMETRY_LINESTRING)
				s.append("LINESTRING ");
			else if (geometryType == GEOMETRY_POLYGON)
				s.append("POLYGON ");
			else if (geometryType == GEOMETRY_MULTIPOINT)
				s.append("MULTIPOINT ");
			else if (geometryType == GEOMETRY_MULTILINESTRING)
				s.append("MULTILINESTRING ");
			else if (geometryType == GEOMETRY_MULTIPOLYGON)
				s.append("MULTIPOLYGON ");
			else
				s.append("GEOMETRYCOLLECTION ");
		}

		if (nElements == 0)
		{
			/*
			 * Geometry is empty.
			 */
			s.append("EMPTY ");
		}
		else if (geometryType == GEOMETRY_POINT)
		{
			/*
			 * Convert point to OGC Well Known Text representation.
			 */
			if (addGeometryType)
				s.append("(");
			s.append(coords[nextIndex + 1]);
			s.append(" ");
			s.append(coords[nextIndex + 2]);
			if (addGeometryType)
				s.append(")");
			nextIndex += 3;
		}
		else if (geometryType == GEOMETRY_LINESTRING || geometryType == GEOMETRY_POLYGON)
		{
			/*
			 * Convert line or polygon to OGC Well Known Text representation.
			 */
			if (geometryType == GEOMETRY_POLYGON)
				s.append("(");
			for (int i = 0; i < nElements; i++)
			{
				if (coords[nextIndex] == MOVETO)
				{
					/*
					 * End last polygon ring and begin next ring.
					 */
					if (i > 0)
						s.append("), ");
					s.append("(");
				}
				else if (i > 0)
				{
					s.append(", ");
				}
				s.append(mGeometryValue[nextIndex + 1]);
				s.append(" ");
				s.append(mGeometryValue[nextIndex + 2]);
				nextIndex += 3;
			}

			if (geometryType == GEOMETRY_LINESTRING)
				s.append(")");
			else
				s.append("))");
		}
		else /* GEOMETRY_MULTIPOINT, GEOMETRY_MULTILINESTRING, GEOMETRY_MULTIPOLYGON, GEOMETRYCOLLECTION */
		{
			/*
			 * Expand each geometry in the multiple geometry.
			 */
			s.append("( ");
			boolean isGeometryCollection = (geometryType == GEOMETRY_COLLECTION);
			for (int i = 0; i < nElements; i++)
			{
				if (i > 0)
					s.append(", ");
				nextIndex = createOGCWKT(coords, nextIndex, s, isGeometryCollection);
			}
			s.append(")");
		}
		return(nextIndex);
	}
	
	/**
	 * Return string representation of this argument.
	 * @return string representation of argument value.
	 */
	public String toString()
	{
		String retval = null;
		DecimalFormat format;
		StringBuffer sb;

		if (mType == STRING)
			retval = mStringValue;
		else if (mType == VARIABLE)
			retval = mVarname;
		else if (mType == NUMERIC)
		{
			double absValue = (mNumericValue >= 0) ? mNumericValue : -mNumericValue;

			/*
			 * Print large or small numbers in scientific notation
			 * to give more significant digits.
			 */				
			if (absValue != 0 && (absValue < 0.01 || absValue > 10000000.0))
				format = new DecimalFormat("#.################E0", Constants.US_DECIMAL_FORMAT_SYMBOLS);
			else
				format = new DecimalFormat("#.################", Constants.US_DECIMAL_FORMAT_SYMBOLS);

			retval = format.format(mNumericValue);
		}
		else if (mType == HASHMAP)
		{
			/*
			 * Build string of all key, value pairs in the hash map.
			 */
			sb = new StringBuffer();
			Object []keys = getHashMapKeys();
			for (int i = 0; i < keys.length; i++)
			{
				sb.append(keys[i]);
				sb.append(' ');
				Argument value = (Argument)mHashMap.get(keys[i]);
				sb.append(value.getStringValue());
				sb.append(Constants.LINE_SEPARATOR);
			}
			retval = sb.toString();
		}
		else
		{
			sb = new StringBuffer();
			createOGCWKT(mGeometryValue, 0, sb, true);
			retval = sb.toString().trim();
		}
		return(retval);
	}

	/**
	 * Clones an Argument object.
	 * @return clone of this argument.
	 */	
	public Object clone()
	{
		Argument retval;
		
		/*
		 * Create new argument that is a copy of existing one.
		 */
		if (mType == STRING || mType == VARIABLE)
			retval = new Argument(mType, mStringValue);
		else if (mType == NUMERIC)
			retval = new Argument(mNumericValue);
		else if (mType == HASHMAP)
		{
			retval = new Argument();
			
			/*
			 * Make a copy of the list of entries in the hash map --
			 * we do not want changes in the hashmap copy appearing
			 * in the original hashmap.
			 */
			retval.mHashMap = (HashMap)mHashMap.clone();
		}
		else
		{
			retval = new Argument(mType, mGeometryValue);
		}
		return((Object)retval);
	}
}
