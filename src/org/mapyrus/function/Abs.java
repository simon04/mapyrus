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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.mapyrus.function;

import java.util.ArrayList;

import org.mapyrus.Argument;
import org.mapyrus.ContextStack;
import org.mapyrus.MapyrusException;

/**
 * Function returning largest integer not larger than given value.
 * For example, abs(-26) = 26.
 */
public class Abs implements Function
{
	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		Argument retval;
		Argument arg1 = args.get(0);
		double d = arg1.getNumericValue();
		if (arg1.getType() == Argument.NUMERIC)
			retval = (d >= 0) ? arg1 : new Argument(-d);
		else
			retval = Argument.numericZero;
		return(retval);
	}

	@Override
	public int getMaxArgumentCount()
	{
		return(1);
	}

	@Override
	public int getMinArgumentCount()
	{
		return(1);
	}

	@Override
	public String getName()
	{
		return("abs");
	}
}
