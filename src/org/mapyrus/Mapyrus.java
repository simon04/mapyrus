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
package au.id.chenery.mapyrus;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Main class for Mapyrus, a program for generating plots of points, lines and polygons
 * to various output formats.  Runs as either an interpreter for files
 * given on the command line or as an HTTP server.
 */
public class Mapyrus
{
	/**
	 * Show software version number and usage message, then exit.
	 */
	private static void printUsageAndExit()
	{		
		String []usage =
		{
			
			"Usage:",
			"java [-Dvariable=value] ... -jar " + Constants.PROGRAM_NAME.toLowerCase() + ".jar [-s port] filename ...",
			"",
			Constants.PROGRAM_NAME + " reads each file or URL in turn.",
			"If filename is '-' then standard input is read.",
			"",
			"Variables and configuration are passed to " + Constants.PROGRAM_NAME + " using the",
			"Java -D option.",
			"",
			"-s option starts " + Constants.PROGRAM_NAME + " as a self-contained HTTP server",
			"on the given port.  Refer to manual for detailed instructions."
		};

		String []license =
		{
			Constants.PROGRAM_NAME + " comes with ABSOLUTELY NO WARRANTY, not even for",
			"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.",
			"You may redistribute copies of " + Constants.PROGRAM_NAME + " under the terms",
			"of the GNU General Public License.  For more information",
			"about these matters, see the file named COPYING."
		};

		System.out.println(Constants.PROGRAM_NAME + " version " + Constants.getVersion() +
			" Copyright (C) 2003 Simon Chenery");
		System.out.println("");

		for (int i = 0; i < usage.length; i++)
		{
			System.out.println(usage[i]);
		}

		System.out.println("");
		for (int i = 0; i < license.length; i++)
		{
			System.out.println(license[i]);
		}
		
		System.out.println("");
		System.out.println("Report bugs to <" + Constants.PROGRAM_NAME.toLowerCase() +
			"@chenery.id.au>.");
		System.exit(1);
	}
	
	/**
	 * Parse and interpret commands from a file.  Trap any exceptions.
	 * @param context is context to use during interpretation.
	 * @param f open file or URL to read.
	 * @param interpreter interpreter in which to run commands.
	 * @param closeFile if set to true file is closed after we finish reading it.
	 * @return flag indicating whether interpretation succeeeded.
	 */
	private static boolean processFile(ContextStack context, FileOrURL f,
		Interpreter interpreter, boolean closeFile)
	{
		try
		{
			interpreter.interpret(context, f, System.out);
			if (closeFile)
				f.getReader().close();
		}
		catch (MapyrusException e)
		{
			System.err.println(e.getMessage());
			return(false);
		}
		catch (Exception e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
			return(false);
		}		
		return(true);
	}
	
	/*
	 * Initialise global settings, color name lookup tables.
	 */
	private static void initialise()
	{
		try
		{
			ColorDatabase.load();
		}
		catch (IOException e)
		{
			System.err.println(e.getMessage());
			System.exit(1);
		}
		catch (MapyrusException e)
		{
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Listen on a server socket, accepting and processing HTTP requests.
	 * @param interpreter interpreter to use for
	 * @param port port on which to create socket and listen on.
	 */
	private static void serveHttp(Interpreter interpreter, int port)
	{
		ServerSocket serverSocket = null;
		Pool interpreterPool;
		HashSet activeThreads;
		
		/*
		 * Make pool of interpreters available to threads that
		 * handle HTTP requests.
		 */
		interpreterPool = new Pool();
		interpreterPool.put(interpreter);
		for (int i = 1; i < Constants.MAX_HTTP_THREADS; i++)
			interpreterPool.put(interpreter.clone());

		/*
		 * Initialise set of threads that have been started.
		 */
		activeThreads = new HashSet();

		String packageName = Mapyrus.class.getPackage().getName();
		Logger logger = Logger.getLogger(packageName);

		try
		{
			/*
			 * Create socket on given port.  Check port so that
			 */
			serverSocket = new ServerSocket(port);
			port = serverSocket.getLocalPort();
		}
		catch (IOException e)
		{
			System.err.println(MapyrusMessages.get(MapyrusMessages.INIT_HTTP_FAILED) +
				": " + e.getMessage());
			System.exit(1);
		}

		logger.config(Constants.PROGRAM_NAME + " " + Constants.getVersion());
		logger.config(MapyrusMessages.get(MapyrusMessages.ACCEPTING_HTTP) + ": " + port);

		while (true)
		{
			try
			{
				/*
				 * Listen on socket for next client connection.
				 */
				Socket socket = serverSocket.accept();
				socket.setSoTimeout(Constants.HTTP_SOCKET_TIMEOUT);

				/*
				 * Take a intepreter to handle this request (waiting
				 * until one becomes available, if necessary).
				 * Then start new thread to handle this request.
				 */
				interpreter = (Interpreter)(interpreterPool.get(Constants.HTTP_TIMEOUT));
				if (interpreter == null)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.HTTP_TIMEOUT));
				}

				HTTPRequest request = new HTTPRequest(socket,
					interpreter, interpreterPool);
				activeThreads.add(request);
				logger.fine(MapyrusMessages.get(MapyrusMessages.STARTED_THREAD) +
					": " + request.getName());
				request.start();

				/*
				 * Join any threads we started that have now finished. 
				 */
				Iterator iterator = activeThreads.iterator();
				while (iterator.hasNext())
				{
					HTTPRequest active = (HTTPRequest)(iterator.next());
					if (!active.isAlive())
					{
						/*
						 * Wait for thread to complete, then check if it succeeded.
						 */
						active.join();
						logger.fine(MapyrusMessages.get(MapyrusMessages.JOINED_THREAD) +
							": " + active.getName());
						if (!active.getStatus())
							logger.severe(active.getName() + ": " + active.getErrorMessage());
						iterator.remove();
					}
				}
			}
			catch (IOException e)
			{
				logger.severe(e.getMessage());
			}
			catch (InterruptedException e)
			{
				logger.severe(e.getMessage());
			}
			catch (MapyrusException e)
			{
				logger.severe(e.getMessage());
			}
		}
	}

	/*
	 * Parse command line arguments and start processing.
	 */
	public static void main(String []args)
	{
		FileOrURL f = null;
		ContextStack context;
		int i;
		boolean readingStdin;
		boolean isHttpServer = false;
		int argStartIndex = 0;
		int port = 0;

		/*
		 * Parse command line arguments -- these are the files and URLs
		 * to read commands from.
		 */
		if (args.length == 0 || (args.length == 1 && (args[0].equals("-h") ||
			args[0].equals("--help") || args[0].equals("-?"))))
		{
			/*
			 * Show usage message and quit.
			 */
			printUsageAndExit();
		}
		else if (args.length == 1 && (args[0].equals("-v") || args[0].equals("--version")))
		{
			/*
			 * Show version number and quit.
			 */
			System.out.println(Constants.PROGRAM_NAME + " " + Constants.getVersion());
			System.exit(1);
		}
		else if (args[0].equals("-s"))
		{
			if (args.length < 2)
			{
				printUsageAndExit();
			}
			try
			{
				port = Integer.parseInt(args[1]);
			}
			catch (NumberFormatException e)
			{
				printUsageAndExit();
			}
			argStartIndex = 2;
			isHttpServer = true;
		}

		initialise();

		context = new ContextStack();
		Interpreter interpreter = new Interpreter();

		i = argStartIndex;
		while (i < args.length)
		{
			readingStdin = args[i].equals("-");
			if (readingStdin)
			{
				/*
				 * Read from standard input.
				 */
				f = new FileOrURL(new InputStreamReader(System.in), "standard input");
			}
			else
			{
				/*
				 * Read from a file or URL.
				 */
				try
				{
					f = new FileOrURL(args[i]);
				}
				catch (IOException e)
				{
					System.err.println(e.getMessage());
					System.exit(1);
				}
				catch (MapyrusException e)
				{
					System.err.println(e.getMessage());
					System.exit(1);
				}
			}

	
			if (!processFile(context, f, interpreter, !readingStdin))
				System.exit(1);

			i++;
		}

		/*
		 * Finished off anything being created in this context.
		 */
		try
		{
			context.closeContextStack();
		}
		catch (IOException e)
		{
			System.err.println(e.getMessage());
			System.exit(1);
		}
		catch (MapyrusException e)
		{
			System.err.println(e.getMessage());
			System.exit(1);
		}

		/*
		 * If we're running as an HTTP server we are now ready to
		 * accept connections and respond to requests from HTTP clients.
		 */
		if (isHttpServer)
		{
			serveHttp(interpreter, port);
		}
		System.exit(0);
	}
}
