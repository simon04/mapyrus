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
package org.mapyrus;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Connects Mapyrus to Tomcat web server, enabling Mapyrus to run as a servlet in
 * a web application.
 */
public class MapyrusServlet extends HttpServlet
{
	private static final String COMMANDS_INIT_PARAM_NAME = "c1";

	/**
	 * Handle HTTP GET request from web browser.
	 * @param request HTTP request
	 * @param response HTTP response
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException
	{
		/*
		 * Mapyrus commands read from servlet initialisation parameter 'c1' unless
		 * over-ridden.
		 */
		String paramName = COMMANDS_INIT_PARAM_NAME;

		/*
		 * Generate Mapyrus commands to set variables from HTTP request parameters,
		 * using uppercase for all variable names.
		 */
		StringBuffer variables = new StringBuffer(512);
		Map parameters = request.getParameterMap();
		Iterator it = parameters.keySet().iterator();

		while (it.hasNext())
		{
			String var = it.next().toString();
			String []value = ((String [])parameters.get(var));
			if (!HTTPRequest.isLegalVariable(var))
			{
				throw new ServletException(MapyrusMessages.get(MapyrusMessages.VARIABLE_EXPECTED) +
					": " + var);
			}
			variables = HTTPRequest.addVariable(variables, var.toUpperCase(), value[0]);

			/*
			 * Special HTTP request parameter giving name of servlet initialisation
			 * parameter containing commands for this HTTP request.
			 */
			if (var.equals("ipn"))
			{
				/*
				 * Ensure that value will be valid as a servlet
				 * initialisation parameter name.
				 */
				if (!HTTPRequest.isLegalVariable(value[0]))
				{
					throw new ServletException(MapyrusMessages.get(MapyrusMessages.INVALID_VARIABLE) +
						": " + value[0]);
				}
				paramName = value[0];
			}
		}

		/*
		 * Create array containing HTTP request header information.
		 */
		Enumeration headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements())
		{
			String var = (String)headerNames.nextElement();
			String value = request.getHeader(var);
			if (HTTPRequest.isLegalVariable(var))
				HTTPRequest.addVariable(variables, HTTPRequest.HTTP_HEADER_ARRAY + "['" + var + "']", value);
		}
	
		/*
		 * Get Mapyrus commands to execute for this request from servlet
		 * configuration file.
		 */
		String paramValue = getInitParameter(paramName);
		if (paramValue == null || paramValue.length() == 0)
		{
			throw new ServletException(MapyrusMessages.get(MapyrusMessages.SERVLET_INIT_PARAM) +
				": " + paramName);
		}

		ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
		PrintStream printStream = new PrintStream(byteArrayStream);

		String servletPath = request.getServletPath();
		FileOrURL f1 = new FileOrURL(new StringReader(variables.toString()), servletPath);
		FileOrURL f2 = new FileOrURL(new StringReader(paramValue), servletPath);
		ContextStack context = new ContextStack();
		byte []emptyBuffer = new byte[0];
		ByteArrayInputStream emptyStdin = new ByteArrayInputStream(emptyBuffer);

		try
		{
			Interpreter interpreter = new Interpreter();
			
			/*
			 * Run commands to set variables, then run commands to generate output.
			 */
			interpreter.interpret(context, f1, emptyStdin, null);
			interpreter.interpret(context, f2, emptyStdin, printStream);
			String responseHeader = context.getHTTPResponse().trim();
			context.closeContextStack();
			context = null;

			/*
			 * Send HTTP response header back to client, followed by content.
			 */
			String contentType = null;
			BufferedReader reader = new BufferedReader(new StringReader(responseHeader));
			String nextLine;
			while ((nextLine = reader.readLine()) != null)
			{
				int index = 0;
				while (index < nextLine.length() && !Character.isWhitespace(nextLine.charAt(index)))
					index++;
				if (index < nextLine.length())
				{
					String var = nextLine.substring(0, index);
					String value = nextLine.substring(index).trim();

					if (var.endsWith(":"))
						var = var.substring(0, var.length() - 1);
					if (var.equals(HTTPRequest.CONTENT_TYPE_KEYWORD))
					{
						/*
						 * A special method exists for setting content type. 
						 */
						contentType = value;
					}
					else if (!var.startsWith(HTTPRequest.HTTP_KEYWORD))
					{
						/*
						 * Do not set "HTTP/1.0 OK" line.  Tomcat will set this itself.
						 */
						response.setHeader(var, value);
					}
				}
			}
			if (contentType != null)
				response.setContentType(contentType);
			byteArrayStream.writeTo(response.getOutputStream());
		}
		catch (MapyrusException e)
		{
			throw new ServletException(e.getMessage());
		}
		finally
		{
			/*
			 * Ensure that context is always closed.
			 */
			try
			{
				if (context != null)
					context.closeContextStack();
			}
			catch (IOException e)
			{
			}
			catch (MapyrusException e)
			{
			}
		}
	}

	/**
	 * Handle HTTP POST request from web browser.
	 * @param request HTTP request
	 * @param response HTTP response
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException
	{
		doGet(request, response);
	}	
}