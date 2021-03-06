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

package org.mapyrus.function;

import java.util.ArrayList;

import org.mapyrus.Argument;
import org.mapyrus.ContextStack;
import org.mapyrus.MapyrusException;

/**
 * Function returning substring of a string.
 * For example, substr('foobar', 2, 3) = 'oob'
 */
public class Substr implements Function
{
	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		int startIndex, extractLen, len;
		Argument retval;

		Argument arg1 = args.get(0);
		Argument arg2 = args.get(1);
		Argument arg3;
		if (args.size() == 3)
		{
			arg3 = args.get(2);
		}
		else
		{
			/*
		 	 * Set extract length long enough for all remaining characters in string.
		 	 */
			String s = arg1.toString();
			arg3 = new Argument(s.length());
		}
		String s = arg1.toString();

		/*
		 * Convert to zero-based indexing used by java.
		 */
		startIndex = (int)(Math.floor(arg2.getNumericValue()));
		startIndex--;
		if (startIndex < 0)
			startIndex = 0;
		extractLen = (int)(Math.floor(arg3.getNumericValue()));

		len = s.length();
		if (extractLen < 1 || startIndex >= len)
		{
			/*
			 * Substring is totally to the left or right of
			 * the string.  So substring is empty.
			 */
			retval = Argument.emptyString;
		}
		else
		{
			if (startIndex + extractLen > len)
				extractLen = len - startIndex;
	
			retval = new Argument(Argument.STRING,
				s.substring(startIndex, startIndex + extractLen));
		}
		return(retval);
	}

	@Override
	public int getMaxArgumentCount()
	{
		return(3);
	}

	@Override
	public int getMinArgumentCount()
	{
		return(2);
	}

	@Override
	public String getName()
	{
		return("substr");
	}
}
