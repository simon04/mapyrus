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
 * Function returning string left padded to a given length.
 * For example, lpad('hi', 5, 'xy') = 'xyxhi'.
 */
public class Lpad implements Function
{
	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		Argument retval;
		Argument arg1 = args.get(0);
		Argument arg2 = args.get(1);

		String s = arg1.toString();
		int currentLength = s.length();
		int paddedLength = (int)arg2.getNumericValue();

		String padding;
		if (args.size() == 2)
			padding = " ";
		else
		{
			Argument arg3 = args.get(2);
			padding = arg3.toString();
			if (padding.length() == 0)
				padding = " ";
		}

		if (paddedLength <= 0)
		{
			/*
			 * Request length is zero so padded string will be empty.
			 */
			retval = Argument.emptyString;
		}
		else if (currentLength < paddedLength)
		{
			/*
			 * Add padding to string, then replace original string at end of string.
			 */
			StringBuilder sb = new StringBuilder(paddedLength + padding.length());
			while (sb.length() < paddedLength)
			{
				sb.append(padding);
			}
			sb.replace(paddedLength - currentLength, sb.length(), s);
			retval = new Argument(Argument.STRING, sb.toString());
		}
		else if (currentLength == paddedLength)
		{
			/*
			 * String is already exactly the right length.
			 * Just return original string.
			 */
			retval = arg1;
		}
		else
		{
			/*
			 * String is too long.  Chop it so only last part of string remains.
			 */
			retval = new Argument(Argument.STRING, s.substring(currentLength - paddedLength));
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
		return("lpad");
	}
}
