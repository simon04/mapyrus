/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 Simon Chenery.
 *
 * Mapyrus is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mapyrus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mapyrus; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * @(#) $Id$
 */
package org.mapyrus;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Globally useful constants including fixed distance measurements.
 */
public class Constants
{
	public static final String PROGRAM_NAME = "Mapyrus";
	
	/**
	 * Return version number of software.
	 * @return version number.
	 */
	public static String getVersion()
	{
		/*
		 * Current software version number set by ant Replace task during build.
		 */
		return("@software_version_token@");
	}

	/**
	 * Return release date of software.
	 * @return release date.
	 */
	public static String getReleaseDate()
	{
		/*
		 * Release date set by ant Replace task during build.
		 */
		return("@release_date_token@");
	}

	/*
	 * American number formatting used for all output, with '.'
	 * as the decimal separator.
	 */
	public static final DecimalFormatSymbols US_DECIMAL_FORMAT_SYMBOLS =
		new DecimalFormatSymbols(Locale.US); 

	/*
	 * Maximum number of threads to run simultaneously handling HTTP requests.
	 */
	public static final int MAX_HTTP_THREADS = 8;

	/*
	 * Time in milliseconds to wait to begin handling an HTTP request.  If
	 * HTTP server is too busy to begin handling request within this time
	 * then request is cancelled and an error is returned to HTTP client.
	 */
	public static final int HTTP_TIMEOUT = 30 * 1000;

	/*
	 * Length of time in milliseconds that temporary files are retained
	 * when running as an HTTP server.
	 */
	public static final int HTTP_TEMPFILE_LIFESPAN = 300 * 1000;

	/*
	 * Timeout in milliseconds for socket communication between Mapyrus
	 * and HTTP client.  A read/write takes longer than this time fails. 
	 */
	public static final int HTTP_SOCKET_TIMEOUT = 30 * 1000;

	/*
	 * Timeout in seconds for connecting to RDBMS and for executing
	 * SQL queries.
	 */
	public static final int DB_CONNECTION_TIMEOUT = 30;

	/*
	 * Maximum number of icons to cache in memory.
	 */
	public static final int ICON_CACHE_SIZE = 64;

	/*
	 * Number of points and millimetres per inch.
	 */
	public static final int POINTS_PER_INCH = 72;
	public static final double MM_PER_INCH = 25.4;

	/*
	 * Screen resolution, in dots per inch.
	 * Most displays will be a bit different but it is not easy
	 * to obtain this information.
	 */
	public static final int SCREEN_RESOLUTION = 96;

	/*
	 * Line separator in text files.
	 */
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
