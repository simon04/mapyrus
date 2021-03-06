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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.mapyrus.Argument;
import org.mapyrus.ContextStack;
import org.mapyrus.FileOrURL;
import org.mapyrus.MapyrusException;
import org.mapyrus.MapyrusMessages;

/**
 * Function returning contents of a text file.
 * For example, spool("/etc/motd") could return a string containing "Have a lot of fun...". 
 */
public class Spool implements Function
{
	@Override
	public Argument evaluate(ContextStack context, ArrayList<Argument> args)
		throws MapyrusException
	{
		Argument fileArg = args.get(0);
		String encoding = null;
		if (args.size() == 2)
		{
			String extras = args.get(1).getStringValue();
			StringTokenizer st = new StringTokenizer(extras);
			while (st.hasMoreTokens())
			{
				String token = st.nextToken();
				if (token.startsWith("encoding="))
					encoding = token.substring(9);
			}
		}
		String filename = fileArg.getStringValue();
		
		if (!context.getThrottle().isIOAllowed())
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_IO) +
				": " + filename);
		}

		ByteArrayOutputStream buf = new ByteArrayOutputStream(8 * 1024);
		InputStream stream = null;
		int nBytes;

		try
		{
			/*
			 * Flush any file we are writing to so that we get all
			 * of the file if it happens to be the same file that
			 * we are to read from.
			 */
			context.getStdout().flush();

			/*
			 * Read complete file into memory and return it as a single string.
			 */
			FileOrURL f = new FileOrURL(filename);
			stream = f.getInputStream();
			byte b[] = new byte[1024];
			while ((nBytes = stream.read(b)) > 0)
			{
				buf.write(b, 0, nBytes);
			}
		}
		catch (IOException e)
		{
			throw new MapyrusException(e.getMessage());
		}
		finally
		{
			try
			{
				if (stream != null)
					stream.close();
			}
			catch (IOException e)
			{
			}
		}

		Argument retval;
		try
		{
			if (encoding != null)
				retval = new Argument(Argument.STRING, new String(buf.toByteArray(), encoding));
			else
				retval = new Argument(Argument.STRING, new String(buf.toByteArray()));
		}
		catch (UnsupportedEncodingException e)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.INVALID_CHARSET) +
				": " + encoding + ": " + e.getMessage());
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
		return(1);
	}

	@Override
	public String getName()
	{
		return("spool");
	}
}

