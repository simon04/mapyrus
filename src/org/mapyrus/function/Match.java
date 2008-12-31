/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 - 2009 Simon Chenery.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * @(#) $Id$
 */
package org.mapyrus.function;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.mapyrus.Argument;
import org.mapyrus.ContextStack;
import org.mapyrus.LRUCache;
import org.mapyrus.MapyrusException;
import org.mapyrus.MapyrusMessages;

/**
 * Function returning index in string where a regular expression first matches.
 * For example, match('foobar', 'ob') = 3.
 */
public class Match implements Function
{
	/*
	 * Maximum number of compiled regular expressions we'll cache.
	 */
	private static final int MAX_COMPILED_REGEX = 100;
	
	/*
	 * Static table of frequently used regular expressions.
	 */
	private static LRUCache<String, Pattern> mRegexCache =
		new LRUCache<String, Pattern>(MAX_COMPILED_REGEX);

	/**
	 * Compile a regular expression string into a Pattern that can be used for matching.
	 * Patterns are cached to avoid recomputing them again and again.
	 * Synchronized because LRUCache is not thread-safe.
	 * @param regex is regular expression to compile.
	 * @return compiled pattern
	 */
	public static synchronized Pattern compileRegex(String regex) throws MapyrusException
	{
		Pattern retval = mRegexCache.get(regex);

		if (retval == null)
		{
			try
			{
				retval = Pattern.compile(regex);
			}
			catch (PatternSyntaxException e)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_REGEX) +
					": " + e.getMessage());
			}

			/*
			 * Cache newly compiled regular expression.
			 */
			mRegexCache.put(regex, retval);
		}
		return(retval);
	}

	/**
	 * @see org.mapyrus.function.Function#evaluate(org.mapyrus.ContextStack, ArrayList)
	 */
	public Argument evaluate(ContextStack context, ArrayList args)
		throws MapyrusException
	{
		/*
		 * Find index of start of regular expression in string.
		 */
		Argument retval;
		Argument arg1 = (Argument)args.get(0);
		Argument arg2 = (Argument)args.get(1);
		Pattern pattern = compileRegex(arg2.toString());
		Matcher matcher = pattern.matcher(arg1.toString());
		if (matcher.find())
		{
			int index = matcher.start() + 1;
			if (index == 1)
				retval = Argument.numericOne;
			else
				retval = new Argument(index);
		}
		else
		{
			retval = Argument.numericZero;
		}
		return(retval);
	}

	/**
	 * @see org.mapyrus.function.Function#getMaxArgumentCount()
	 */
	public int getMaxArgumentCount()
	{
		return(2);
	}

	/**
	 * @see org.mapyrus.function.Function#getMinArgumentCount()
	 */
	public int getMinArgumentCount()
	{
		return(2);
	}

	/**
	 * @see org.mapyrus.function.Function#getName()
	 */
	public String getName()
	{
		return("match");
	}
}
