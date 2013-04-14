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
 * Function returning sum of all values in one or more arrays.
 * For example, if a[1] = 10, a[2] = 20 then sum(a) = 30.
 */
public class Sum implements Function
{
	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		double total = 0;

		/*
		 * Add all numbers and arrays of numbers together.
		 */
		for (int i = 0; i < args.size(); i++)
		{
			Argument arg = args.get(i);
			if (arg.getType() == Argument.HASHMAP)
			{
				Object []keys = arg.getHashMapKeys();
				for (int j = 0; j < keys.length; j++)
					total += arg.getHashMapEntry(keys[j].toString()).getNumericValue();
			}
			else
			{
				total += arg.getNumericValue();
			}
		}
		return(new Argument(total));
	}

	@Override
	public int getMaxArgumentCount()
	{
		return(Integer.MAX_VALUE);
	}

	@Override
	public int getMinArgumentCount()
	{
		return(1);
	}

	@Override
	public String getName()
	{
		return("sum");
	}
}
