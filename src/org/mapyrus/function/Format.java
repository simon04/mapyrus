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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import org.mapyrus.Argument;
import org.mapyrus.ContextStack;
import org.mapyrus.MapyrusException;

/**
 * Function returning a number formatted as a string.
 * For example, format("#.##", 1.2345) = "1.23".
 */
public class Format implements Function
{
	private static DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);

	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		Argument retval = null;
		Argument arg1 = args.get(0);
		Argument arg2 = args.get(1);
		String format = arg1.getStringValue();
		try
		{
			DecimalFormat df = new DecimalFormat(format, symbols);
			double d = arg2.getNumericValue();
			retval = new Argument(Argument.STRING, df.format(d));
		}
		catch (IllegalArgumentException e)
		{
			throw new MapyrusException(e.getMessage());
		}
		return(retval);
	}

	@Override
	public int getMaxArgumentCount()
	{
		return(2);
	}

	@Override
	public int getMinArgumentCount()
	{
		return(2);
	}

	@Override
	public String getName()
	{
		return("format");
	}
}
