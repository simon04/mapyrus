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
 * Function returning maximum of all values.
 * For example, max(1, 3, 2) = 3.
 */
public class Max implements Function
{
	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		double maxValue = -Double.MAX_VALUE;
		Argument maxArg = null;

		/*
		 * Find maximum of all numbers and arrays of numbers.
		 */
		for (int i = 0; i < args.size(); i++)
		{
			Argument arg = args.get(i);
			if (arg.getType() == Argument.HASHMAP)
			{
				Object []keys = arg.getHashMapKeys();
				for (int j = 0; j < keys.length; j++)
				{
					Argument entry = arg.getHashMapEntry(keys[j].toString());
					double d = entry.getNumericValue();
					if (d > maxValue)
					{
						maxArg = entry;
						maxValue = d;
					}
				}
			}
			else
			{
				double d = arg.getNumericValue();
				if (d > maxValue)
				{
					maxArg = arg;
					maxValue = d;
				}
			}
		}
		return(maxArg);
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
		return("max");
	}
}
